package org.qtrp.osmtraccar

import android.net.Uri
import okhttp3.HttpUrl
import java.time.Instant

data class Point(
    // these change almost never:
    val ID: Int,
    val name: String,
    val type: String,
    val imageURL: HttpUrl?,
    val avatar: Uri,

    // these change sometimes:
    val status: Status,

    // these change frequently:
    val positionID: Int,    // changes every time the position changes
    val lat: Double,
    val lon: Double,
    val time: Instant,
) {
    fun isStale(): Boolean {
        return status.isStale()
    }

    enum class Status {
        ONLINE,
        OFFLINE,
        UNKNOWN;

        companion object {
            fun parse(s: String): Status {
                when (s){
                    "online" -> return ONLINE
                    "offline" -> return OFFLINE
                    else -> return UNKNOWN
                }
            }
        }

        fun isStale(): Boolean {
            return (this != ONLINE)
        }
    }
}
