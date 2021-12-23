package org.qtrp.osmtraccar

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
        for (i in 0 until jsonDevices.length()) {
            val jsonDevice = jsonDevices.getJSONObject(i)
            val pos = Position(42, 0.0, 0.0, "foo") // fixme

            val point = Point(
                ID = jsonDevice.getInt("id"),
                name = jsonDevice.getString("name"),
                position = pos
            )

            points.add(point)
        }

        return points
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
                override fun onResponse(call: Call, resp: Response){
                    resp.use {
                        if (!resp.isSuccessful) cont.resumeWithException(IOException("Unexpected code $resp"))
                        cont.resume(resp.body!!.string())
                    }
                }
            })
        }
    }
}