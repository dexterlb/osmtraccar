package org.qtrp.osmtraccar

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class TraccarApi() {
    private var mConnData: TraccarData? = null
    private var apiURL = "https://example.com".toHttpUrl()
    private val client = OkHttpClient()

    fun setConnData(connData: TraccarData) {
        this.mConnData = connData
        this.apiURL = connData.url.newBuilder()
            .addPathSegment("api")
            .build()
    }

    suspend fun getPoints(): List<Point> {
        val url = apiURL.newBuilder()
            .addPathSegment("devices")
            .build()

        val data = apiCall(url)

        var points = mutableListOf<Point>()

        val jsonDevices = JSONArray(data)
        val getPositions = mutableListOf<Deferred<Unit>>()

        val coroutineScope = CoroutineScope(Dispatchers.IO)

        for (i in 0 until jsonDevices.length()) {
            val jsonDevice = jsonDevices.getJSONObject(i)

            val pointID =  jsonDevice.getInt("id")

            val point = Point(
                ID = pointID,
                name = jsonDevice.getString("name"),
                position = Position()
            )

            points.add(point)

            getPositions.add(coroutineScope.async {
                points[i] = points[i].copy(
                    position = getPosition(pointID)
                )
            })
        }

        getPositions.awaitAll()

        return points
    }

    private suspend fun getPosition(pointID: Int): Position {
        val url = apiURL.newBuilder()
            .addPathSegment("positions")
            .addQueryParameter("id", pointID.toString())
            .build()

        val data = apiCall(url)

        val jsonPos = JSONArray(data).getJSONObject(0)

        return Position(
            pointID = jsonPos.getInt("deviceId"),
            lat = jsonPos.getDouble("latitude"),
            lon = jsonPos.getDouble("longitude"),
            time = jsonPos.getString("deviceTime"),
        )
    }

    private suspend fun apiCall(url: HttpUrl): String {
        val connData = mConnData
        if (connData == null) {
            throw IOException("traccar api not initialised with connection data")
        }

        val req = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(connData.user, connData.pass))
            .build()

        return reqCall(req)
    }

    private suspend fun reqCall(req: Request): String {
        return suspendCoroutine { cont ->
            if (mConnData == null) {
                cont.resumeWithException(IOException("traccar api not initialised with connection data"))
            }

            client.newCall(req).enqueue(object: Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response){
                    response.use {
                        if (!response.isSuccessful) cont.resumeWithException(IOException("Unexpected code $response"))
                        cont.resume(response.body!!.string())
                    }
                }
            })
        }
    }
}