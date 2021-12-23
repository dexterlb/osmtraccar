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
    private lateinit var connData: TraccarData
    private val client = OkHttpClient()

    fun setConnData(connData: TraccarData) {
        this.connData = connData
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
                    connData.url.newBuilder()
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

    private suspend fun getPosition(pointID: Int): Position {
        val url = apiURL().newBuilder()
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
        val req = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(connData.user, connData.pass))
            .build()

        return reqCall(req)
    }

    private suspend fun reqCall(req: Request): String {
        return suspendCoroutine { cont ->
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

    private fun apiURL(): HttpUrl {
        return connData.url.newBuilder()
            .addPathSegment("api")
            .build()
    }
}