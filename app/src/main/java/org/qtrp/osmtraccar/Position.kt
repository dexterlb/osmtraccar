package org.qtrp.osmtraccar

data class Position(
    val pointID: Int = 0,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val time: String = "", // fixme
)
