package org.qtrp.osmtraccar

import okhttp3.HttpUrl
import java.time.Instant

data class Point(
    // these change almost never:
    val ID: Int = 0,
    val name: String = "",
    val type: String = "",
    val imageURL: HttpUrl? = null,

    // these change sometimes:
    val status: Status = Status.UNKNOWN,

    // these change frequently:
    val positionID: Int = 0,    // changes every time the position changes
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val time: Instant = Instant.ofEpochSecond(0),
) {
    fun isStale(): Boolean {
        return (status != Status.ONLINE)
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
    }
}
