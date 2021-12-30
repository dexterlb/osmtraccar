package org.qtrp.osmtraccar

import okhttp3.HttpUrl

data class Point(
    val ID: Int,
    val name: String,
    val status: Status,
    val type: String,
    val imageURL: HttpUrl?,

    val position: Position,
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
