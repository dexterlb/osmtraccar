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
        ACTIVE,
    }

    fun getPlaceholderPointAvatar(category: String, status: Point.Status): Uri {
        return getStaticAvatar(traccarImageResource(category, statusState(status)))
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
            State.ACTIVE -> "active"
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
            State.ACTIVE
        }
    }

    private fun processImage(img: Bitmap, state: State): Bitmap {
        return when (state) {
            State.ORIGINAL -> img
            State.ACTIVE -> img
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

    private fun traccarImageResource(category: String, state: State): Int {
        return when (state) {
            State.ACTIVE ->
                when (category) {
                    "animal" -> R.drawable.traccar_animal_active
                    "arrow" -> R.drawable.traccar_arrow_active
                    "bicycle" -> R.drawable.traccar_bicycle_active
                    "boat" -> R.drawable.traccar_boat_active
                    "bus" -> R.drawable.traccar_bus_active
                    "car" -> R.drawable.traccar_car_active
                    "crane" -> R.drawable.traccar_crane_active
                    "default" -> R.drawable.traccar_default_active
                    "helicopter" -> R.drawable.traccar_helicopter_active
                    "motorcycle" -> R.drawable.traccar_motorcycle_active
                    "offroad" -> R.drawable.traccar_offroad_active
                    "person" -> R.drawable.traccar_person_active
                    "pickup" -> R.drawable.traccar_pickup_active
                    "plane" -> R.drawable.traccar_plane_active
                    "scooter" -> R.drawable.traccar_scooter_active
                    "ship" -> R.drawable.traccar_ship_active
                    "tractor" -> R.drawable.traccar_tractor_active
                    "train" -> R.drawable.traccar_train_active
                    "tram" -> R.drawable.traccar_tram_active
                    "trolleybus" -> R.drawable.traccar_trolleybus_active
                    "truck" -> R.drawable.traccar_truck_active
                    "van" -> R.drawable.traccar_van_active
                    else -> R.drawable.img_point_placeholder_active
                }
            else ->
                when (category) {
                    "animal" -> R.drawable.traccar_animal_stale
                    "arrow" -> R.drawable.traccar_arrow_stale
                    "bicycle" -> R.drawable.traccar_bicycle_stale
                    "boat" -> R.drawable.traccar_boat_stale
                    "bus" -> R.drawable.traccar_bus_stale
                    "car" -> R.drawable.traccar_car_stale
                    "crane" -> R.drawable.traccar_crane_stale
                    "default" -> R.drawable.traccar_default_stale
                    "helicopter" -> R.drawable.traccar_helicopter_stale
                    "motorcycle" -> R.drawable.traccar_motorcycle_stale
                    "offroad" -> R.drawable.traccar_offroad_stale
                    "person" -> R.drawable.traccar_person_stale
                    "pickup" -> R.drawable.traccar_pickup_stale
                    "plane" -> R.drawable.traccar_plane_stale
                    "scooter" -> R.drawable.traccar_scooter_stale
                    "ship" -> R.drawable.traccar_ship_stale
                    "tractor" -> R.drawable.traccar_tractor_stale
                    "train" -> R.drawable.traccar_train_stale
                    "tram" -> R.drawable.traccar_tram_stale
                    "trolleybus" -> R.drawable.traccar_trolleybus_stale
                    "truck" -> R.drawable.traccar_truck_stale
                    "van" -> R.drawable.traccar_van_stale
                    else -> R.drawable.img_point_placeholder_stale
                }
           }
        }
    }