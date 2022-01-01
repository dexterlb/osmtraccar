package org.qtrp.osmtraccar

import okhttp3.*
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object AsyncHttp {
    suspend fun<T> asyncRequest(client: OkHttpClient, req: Request, f: suspend (Response) -> T): T {
        val response: Response = suspendCoroutine { cont ->
            client.newCall(req).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    cont.resume(response)
                }
            })
        }

        return response.use {
            if (!response.isSuccessful) {
                throw IOException("Unexpected code $response")
            }

            f(response)
        }
    }
}