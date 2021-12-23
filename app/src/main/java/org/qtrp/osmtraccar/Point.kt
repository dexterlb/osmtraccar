package org.qtrp.osmtraccar

data class Point(
    val ID: Int,
    val name: String,
    val status: PointStatus,
    val type: String,

    val position: Position,
)

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