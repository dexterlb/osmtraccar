package org.qtrp.osmtraccar

import android.content.Context
import android.util.Log
import com.franmontiel.persistentcookiejar.cache.SetCookieCache
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class TraccarApi(context: Context, private val eventListener: EventListener) {
    companion object {
        const val BASE_URL_KEY = "base_url"
        const val EMAIL_KEY = "email"
    }

    private lateinit var baseURL: HttpUrl
    private val client = OkHttpClient.Builder()
        .cookieJar(PersistentlyPersistentCookieJar(SetCookieCache(), SharedPrefsCookiePersistor(context)))
        .build()

    private val avatarCache = AvatarCache(context, client)

    private var socket: WebSocket? = null
    private val log = eventListener::traccarApiLogMessage

    private val sharedPrefs = context.getSharedPreferences("traccar_api_prefs", Context.MODE_PRIVATE)

    private val deviceJsonCache = hashMapOf<Int, JSONObject>()
    private val devicePositionJsonCache = hashMapOf<Int, JSONObject>()

    private val positionTimers = hashMapOf<Int, Timer>()

    suspend fun login(url: HttpUrl, email: String, pass: String): Boolean {
        val loginUrl = url.newBuilder()
            .addPathSegment("api")
            .addPathSegment("session")
            .build()

        val form = FormBody.Builder()
            .add("email", email)
            .add("password", pass)
            .build()

        val req = Request.Builder()
            .url(loginUrl)
            .post(form)
            .build()

        val result = reqCall(req)
        log(Log.VERBOSE, "login result: $result")

        saveLogin(url, email)

        return true
    }

    suspend fun getPoints(): List<Point> {
        val url = apiURL().newBuilder()
            .addPathSegment("devices")
            .build()

        val data = apiCall(url)

        val points = mutableListOf<Point?>()

        val jsonDevices = JSONArray(data)
        val getPositions = mutableListOf<Deferred<Unit>>()

        val coroutineScope = CoroutineScope(Dispatchers.IO)

        for (i in 0 until jsonDevices.length()) {
            val jsonDevice = jsonDevices.getJSONObject(i)

            val posID = jsonDevice.getInt("positionId")
            val devID = jsonDevice.getInt("id")

            deviceJsonCache[devID] = jsonDevice
            points.add(null)

            getPositions.add(coroutineScope.async {
                var point = getPosition(posID)
                try {
                    point = avatarCache.updatePointAvatar(point)
                    log(Log.VERBOSE, "got avatar for point ${point.name} and saved it to ${point.avatar}")
                } catch (e: Exception) {
                    log(Log.ERROR, "unable to get avatar for point ${point.name}: (${point.imageURL}): ${e.stackTraceToString()}")
                }
                points[i] = point
            })
        }

        getPositions.awaitAll()

        return AndroidUtils.noNulls(points)
    }

    fun subscribeToPointUpdates() {
        val url = socketURL()

        log(Log.INFO, "connecting to websocket at: $url")

        val listener = object: WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                log(Log.INFO, "websocket opened")
                socketConnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                log(Log.ERROR, "websocket failure: $t")
                socketDisconnected()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                log(Log.WARN, "websocket closed")
                socketDisconnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    parseSocketMsg(text)
                } catch (e: Exception) {
                    log(Log.ERROR, "unable to parse websocket message:\n    msg: $text\n    err: $e")
                }
            }
        }

        socket = client.newWebSocket(apiReq(url), listener)
    }

    fun unsubscribePointUpdates() {
        this.socket?.close(1000, "user wants to disconnect")
    }

    private fun parseSocketMsg(msg: String) {
        val obj = JSONObject(msg)
        parseDeviceMsg(obj)
        parsePositionMsg(obj)
    }

    private fun parseDeviceMsg(obj: JSONObject) {
        val jsonDevices = try {
            obj.getJSONArray("devices")
        } catch (e: JSONException) {
            return
        }

        for (i in 0 until jsonDevices.length()) {
            val jsonDevice = jsonDevices.getJSONObject(i)

            val devID = jsonDevice.getInt("id")
            log(Log.VERBOSE, "got device update for id $devID")

            deviceJsonCache[devID] = jsonDevice

            scheduleDefaultPositionUpdate(devID)
        }
    }

    // we usually receive a "device update" and then shortly a "position update".
    // in order not to send point updates twice, device updates are cached and the
    // point is updated at the position update.
    //
    // however, sometimes we just receive a device update (e.g. device went offline).
    // in these cases, we must update the point regardless: to do this, we schedule
    // a future task that will update the point if no position arrives for some time.
    private fun scheduleDefaultPositionUpdate(devID: Int) {
        var positionTimer = positionTimers[devID]
        if (positionTimer == null) {
            positionTimer = Timer()
            positionTimers[devID] = positionTimer
        }

        positionTimer.schedule(
            object: TimerTask() {
                override fun run() {
                    val jsonPosition = devicePositionJsonCache[devID] ?: return // no position yet
                    val jsonDevice = deviceJsonCache[devID] ?: throw Exception("lost device $devID?")

                    val point = parsePoint(jsonPosition, jsonDevice)
                    log(Log.VERBOSE, "updating point since we received no position for some time: $point")
                    eventListener.traccarPointUpdate(point)
                }
            },
            5000,
        )
    }

    private fun cancelDefaultPositionUpdate(devID: Int) {
        val timer = positionTimers[devID] ?: return

        try {
            timer.cancel()
        } catch (e: Exception) {
            // probably already canceled, fuck it
        }

        positionTimers.remove(devID)
    }

    private fun parsePositionMsg(obj: JSONObject) {
        val jsonPositions = try {
            obj.getJSONArray("positions")
        } catch (e: JSONException) {
            return
        }

        for (i in 0 until jsonPositions.length()) {
            val jsonPosition = jsonPositions.getJSONObject(i)
            val devID = jsonPosition.getInt("deviceId")

            val jsonDevice = deviceJsonCache[devID] ?: throw Exception("no device with id $devID")

            devicePositionJsonCache[devID] = jsonPosition
            cancelDefaultPositionUpdate(devID)

            val point = parsePoint(jsonPosition, jsonDevice)
            log(Log.VERBOSE, "got position update: $point")
            eventListener.traccarPointUpdate(point)
        }
    }

    private suspend fun getPosition(posID: Int): Point {
        val url = apiURL().newBuilder()
            .addPathSegment("positions")
            .addQueryParameter("id", posID.toString())
            .build()

        val data = apiCall(url)

        val jsonPosition = JSONArray(data).getJSONObject(0)
        val devID = jsonPosition.getInt("deviceId")
        val jsonDevice = deviceJsonCache[devID] ?: throw Exception("no device with id $devID")

        return parsePoint(jsonPosition, jsonDevice)
    }

    // gets device data from cache and uses given position data to construct a point
    private fun parsePoint(jsonPos: JSONObject, jsonDevice: JSONObject): Point {
        val attrs = jsonDevice.getJSONObject("attributes")

        val status = Point.Status.parse(jsonDevice.getString("status"))

        val imageUrl = if (attrs.has("image_url")) {
            attrs.getString("image_url").toHttpUrl()
        } else {
            null
        }

        val category = jsonDevice.getString("category")

        var avatar = if (imageUrl != null) {
            avatarCache.getCachedWebAvatar(imageUrl, status)
        } else {
            null
        }

        if (avatar == null) {
            avatar = avatarCache.getPlaceholderPointAvatar(category, status)
        }

        val point = Point(
            ID = jsonDevice.getInt("id"),
            name = jsonDevice.getString("name"),
            positionID = jsonPos.getInt("id"),
            lat = jsonPos.getDouble("latitude"),
            lon = jsonPos.getDouble("longitude"),
            time = parseTime(jsonPos.getString("deviceTime")),
            type = category,
            status = status,
            imageURL = imageUrl,
            avatar = avatar,
        )

        return point
    }

    private fun parseTime(s: String): Instant {
        val odt = OffsetDateTime.parse(s, DateTimeFormatter.ISO_DATE_TIME)
        return Instant.from(odt)
    }

    private suspend fun apiCall(url: HttpUrl): String {
        return reqCall(apiReq(url))
    }

    private fun apiReq(url: HttpUrl): Request {
        log(Log.VERBOSE, "calling api: $url")
        return Request.Builder()
            .url(url)
            .build()
    }

    private suspend fun reqCall(req: Request): String {
        return AsyncHttp.asyncRequest(client, req) { resp -> resp.body!!.string() }
    }

    private fun socketConnected() {
        eventListener.traccarSocketConnectedState(true)
    }

    private fun socketDisconnected() {
        this.socket = null
        eventListener.traccarSocketConnectedState(false)
    }

    private fun saveLogin(url: HttpUrl, email: String) {
        baseURL = url
        with (sharedPrefs.edit()) {
            putString(BASE_URL_KEY, url.toString())
            putString(EMAIL_KEY, email)
            apply()
        }
    }

    fun getURL(): HttpUrl? {
        return sharedPrefs.getString(BASE_URL_KEY, null)?.toHttpUrl()
    }

    fun getEmail(): String? {
        return sharedPrefs.getString(EMAIL_KEY, null)
    }

    // todo: rename this
    private fun mustGetURL(): HttpUrl {
        if (!::baseURL.isInitialized) {
            baseURL = getURL() ?: "https://example.com".toHttpUrl()
        }
        return baseURL
    }

    private fun apiURL(): HttpUrl {
        return mustGetURL().newBuilder()
            .addPathSegment("api")
            .build()
    }

    private fun socketURL(): HttpUrl {
        return mustGetURL().newBuilder()
            .addPathSegment("api")
            .addPathSegment("socket")
            .build()
    }

    interface EventListener {
        fun traccarApiLogMessage(level: Int, msg: String)
        fun traccarSocketConnectedState(isConnected: Boolean)
        fun traccarPointUpdate(point: Point)
    }
}
