package org.qtrp.osmtraccar

import okhttp3.HttpUrl

data class Point(
    val ID: Int,
    val name: String,
    val status: PointStatus,
    val type: String,
    val imageURL: HttpUrl?,

    val position: Position,
) {
    fun isStale(): Boolean {
        return (status != PointStatus.ONLINE)
    }
}

enum class PointStatus {
    ONLINE,
    OFFLINE,
    UNKNOWN;

    companion object {
        fun parse(s: String): PointStatus {
            when (s){
                "online" -> return ONLINE
                "offline" -> return OFFLINE
                else -> return UNKNOWN
            }
        }
    }
}