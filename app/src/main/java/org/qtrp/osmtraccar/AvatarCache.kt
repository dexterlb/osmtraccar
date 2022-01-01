package org.qtrp.osmtraccar

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.regex.Pattern

class AvatarCache(private val context: Context, private val httpClient: OkHttpClient) {
    fun getPlaceholderPointAvatar(status: Point.Status): Uri {
        if (status.isStale()) {
            return getStaticAvatar(R.drawable.img_point_placeholder_stale)
        } else {
            return getStaticAvatar(R.drawable.img_point_placeholder_active)
        }
    }

    fun getStaticAvatar(resource: Int): Uri {
        return AndroidUtils.resourceToUri(context, resource)
    }

    fun getCachedWebAvatar(url: HttpUrl): Uri? {
        val file = getAvatarFile(url)
        val uri = AndroidUtils.getUriForFile(context, file)

        if (file.canRead()) {
            return uri
        } else {
            return null
        }
    }

    suspend fun getWebAvatar(url: HttpUrl): Uri {
        val file = getAvatarFile(url)
        val uri = AndroidUtils.getUriForFile(context, file)

        if (file.canRead()) {
            return uri
        }

        val req = Request.Builder().url(url).build()
        saveFile(req, file)
        return uri
    }

    private fun getAvatarFile(url: HttpUrl): File {
        val urlString = url.toString()
        val extensionMatcher = Pattern.compile(".*\\.([^./]*)$").matcher(urlString)
        val extension = if (extensionMatcher.matches()) {
            extensionMatcher.group(1)
        } else {
            ""
        }

        val urlHash = AndroidUtils.sha256(urlString)
        val outerDir = context.externalCacheDir

        File(outerDir,"avatars").mkdirs()

        val filename = if (extension != "") {
            "avatars/$urlHash.$extension"
        } else {
            "avatars/$urlHash"
        }

        val file = File(outerDir, filename)

        return file
    }


    private suspend fun saveFile(req: Request, f: File) {
        AsyncHttp.asyncRequest(httpClient, req) { resp ->
            val inStream = resp.body!!.byteStream()

            withContext(Dispatchers.IO) {
                val outStream = FileOutputStream(f)
                AndroidUtils.copyStream(inStream, outStream)
                outStream.close()
            }
        }
    }

    suspend fun updatePointAvatar(point: Point): Point {
        var avatar = point.avatar
        if (point.imageURL != null) {
            avatar = getWebAvatar(point.imageURL)
        }

        return point.copy(avatar = avatar)
    }
}