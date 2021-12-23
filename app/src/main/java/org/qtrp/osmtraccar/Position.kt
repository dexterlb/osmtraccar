package org.qtrp.osmtraccar

data class Position(
    val pointID: Int,
    val lat: Double,
    val lon: Double,
    val time: String, // fixme
)
