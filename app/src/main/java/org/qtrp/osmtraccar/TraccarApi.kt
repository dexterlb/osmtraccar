package org.qtrp.osmtraccar

import android.content.Context
import android.util.Log
import com.franmontiel.persistentcookiejar.PersistentCookieJar
import com.franmontiel.persistentcookiejar.cache.SetCookieCache
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class TraccarApi(context: Context, eventListener: TraccarEventListener) {
    companion object {
        const val BASE_URL_KEY = "base_url"
    }

    private lateinit var baseURL: HttpUrl
    private val client = OkHttpClient.Builder()
        .cookieJar(PersistentlyPersistentCookieJar(SetCookieCache(), SharedPrefsCookiePersistor(context)))
        .build()

    private var socket: WebSocket? = null
    private val log = eventListener::traccarApiLogMessage
    private val eventListener = eventListener

    private val sharedPrefs = context.getSharedPreferences("traccar_api_prefs", Context.MODE_PRIVATE)

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

        saveURL(url)

        return true
    }

    suspend fun getPoints(): List<Point> {
        val url = apiURL().newBuilder()
            .addPathSegment("devices")
            .build()

        val data = apiCall(url)

        val points = mutableListOf<Point>()

        val jsonDevices = JSONArray(data)
        val getPositions = mutableListOf<Deferred<Unit>>()

        val coroutineScope = CoroutineScope(Dispatchers.IO)

        for (i in 0 until jsonDevices.length()) {
            val jsonDevice = jsonDevices.getJSONObject(i)

            val posID = jsonDevice.getInt("positionId")
            val attrs = jsonDevice.getJSONObject("attributes")

            val point = Point(
                ID = jsonDevice.getInt("id"),
                name = jsonDevice.getString("name"),
                position = Position(),
                type = jsonDevice.getString("category"),
                status = PointStatus.parse(jsonDevice.getString("status")),
                imageURL = if (attrs.has("image_url")) {
                    attrs.getString("image_url").toHttpUrl()
                } else {
                    getURL().newBuilder()
                        .addPathSegment("images")
                        .addPathSegment(jsonDevice.getString("category") + ".svg")
                        .build()
                }
            )

            points.add(point)

            getPositions.add(coroutineScope.async {
                points[i] = points[i].copy(
                    position = getPosition(posID)
                )
            })
        }

        getPositions.awaitAll()

        return points
    }

    fun subscribeToPositionUpdates() {
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
                } catch(e: Exception) {
                    log(Log.ERROR, "unable to parse websocket message:\n    msg: $text\n    err: $e")
                }
            }
        }

        socket = client.newWebSocket(apiReq(url), listener)
    }

    fun unsubscribePositionUpdates() {
        this.socket?.close(1000, "user wants to disconnect")
    }

    private fun parseSocketMsg(msg: String) {
        val jsonOuter = JSONObject(msg)
        val jsonPositions = try {
            jsonOuter.getJSONArray("positions")
        } catch (e: JSONException) {
            return
        }

        for (i in 0 until jsonPositions.length()) {
            val jsonPosition = jsonPositions.getJSONObject(i)
            val pos = parsePosition(jsonPosition)
            log(Log.VERBOSE, "got position update: $pos")
            eventListener.traccarPositionUpdate(pos)
        }
    }

    private suspend fun getPosition(pointID: Int): Position {
        val url = apiURL().newBuilder()
            .addPathSegment("positions")
            .addQueryParameter("id", pointID.toString())
            .build()

        val data = apiCall(url)

        val jsonPos = JSONArray(data).getJSONObject(0)

        return parsePosition(jsonPos)
    }

    private fun parsePosition(jsonPos: JSONObject): Position {
        return Position(
            pointID = jsonPos.getInt("deviceId"),
            lat = jsonPos.getDouble("latitude"),
            lon = jsonPos.getDouble("longitude"),
            time = jsonPos.getString("deviceTime"),
        )
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
        return suspendCoroutine { cont ->
            client.newCall(req).enqueue(object: Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response){
                    response.use {
                        if (!response.isSuccessful) {
                            cont.resumeWithException(IOException("Unexpected code $response"))
                            return@use
                        }
                        cont.resume(response.body!!.string())
                    }
                }
            })
        }
    }

    private fun socketConnected() {
        eventListener.traccarSocketConnectedState(true)
    }

    private fun socketDisconnected() {
        this.socket = null
        eventListener.traccarSocketConnectedState(false)
    }

    private fun saveURL(url: HttpUrl) {
        with (sharedPrefs.edit()) {
            putString(BASE_URL_KEY, url.toString())
            apply()
        }
    }

    fun getURL(): HttpUrl {
        if (!::baseURL.isInitialized) {
            baseURL = sharedPrefs.getString(BASE_URL_KEY, "https://example.com")!!.toHttpUrl()
        }
        return baseURL
    }

    private fun apiURL(): HttpUrl {
        return getURL().newBuilder()
            .addPathSegment("api")
            .build()
    }

    private fun socketURL(): HttpUrl {
        return getURL().newBuilder()
            .addPathSegment("api")
            .addPathSegment("socket")
            .build()
    }
}

interface TraccarEventListener {
    fun traccarApiLogMessage(level: Int, msg: String)
    fun traccarSocketConnectedState(isConnected: Boolean)
    fun traccarPositionUpdate(pos: Position)
}