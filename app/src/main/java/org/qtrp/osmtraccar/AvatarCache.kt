package org.qtrp.osmtraccar

import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class AvatarCache(private val context: Context, private val httpClient: OkHttpClient) {
    enum class State {
        ORIGINAL,
        STALE,
        ONLINE,
    }

    fun getPlaceholderPointAvatar(status: Point.Status): Uri {
        if (status.isStale()) {
            return getStaticAvatar(R.drawable.img_point_placeholder_stale)
        } else {
            return getStaticAvatar(R.drawable.img_point_placeholder_active)
        }
    }

    private fun getStaticAvatar(resource: Int): Uri {
        return AndroidUtils.resourceToUri(context, resource)
    }

    fun getCachedWebAvatar(url: HttpUrl, status: Point.Status): Uri? {
        val file = getAvatarFile(url, statusState(status))
        val uri = AndroidUtils.getUriForFile(context, file)

        if (file.canRead()) {
            return uri
        } else {
            return null
        }
    }

    suspend fun getWebAvatar(url: HttpUrl, status: Point.Status): Uri {
        val cached = getCachedWebAvatar(url, status)
        if (cached != null) {
            return cached
        }

        val img = getImage(url)

        for (state in State.values()) {
            saveImage(processImage(img, state), getAvatarFile(url, state))
        }

        return getCachedWebAvatar(url, status)!!
    }

    private fun getAvatarFile(url: HttpUrl, state: State): File {
        val urlString = url.toString()
        val suffix = when (state) {
            State.ONLINE -> "online"
            State.STALE -> "stale"
            State.ORIGINAL -> "orig"
        }

        val urlHash = AndroidUtils.sha256(urlString + "baz")
        val outerDir = context.externalCacheDir

        File(outerDir,"avatars").mkdirs()

        val filename = "avatars/$urlHash.$suffix.png"

        return File(outerDir, filename)
    }

    private fun statusState(status: Point.Status): State {
        return if (status.isStale()) {
            State.STALE
        } else {
            State.ONLINE
        }
    }

    private fun processImage(img: Bitmap, state: State): Bitmap {
        return when (state) {
            State.ORIGINAL -> img
            State.ONLINE -> img
            State.STALE -> desaturateImage(img)
        }
    }

    private fun desaturateImage(img: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(img.width, img.height, img.config)
        val canvas = Canvas(result)

        val matrix = ColorMatrix()
        matrix.setSaturation(0.0f)


        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(matrix)

        canvas.drawBitmap(img, 0.0f, 0.0f, paint)

        return result
    }

    private suspend fun getImage(url: HttpUrl): Bitmap {
        val req = Request.Builder().url(url).build()

        return AsyncHttp.asyncRequest(httpClient, req) { resp ->
            val inStream = resp.body!!.byteStream()

            BitmapDrawable(Resources.getSystem(), inStream).bitmap
        }
    }

    private suspend fun saveImage(img: Bitmap, f: File) {
        withContext(Dispatchers.IO) {
            val outStream = FileOutputStream(f)
            img.compress(Bitmap.CompressFormat.PNG, 6, outStream)
            outStream.close()
        }
    }

    suspend fun updatePointAvatar(point: Point): Point {
        var avatar = point.avatar
        if (point.imageURL != null) {
            avatar = getWebAvatar(point.imageURL, point.status)
        }

        return point.copy(avatar = avatar)
    }
}