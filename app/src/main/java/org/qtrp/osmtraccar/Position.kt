package org.qtrp.osmtraccar

import java.util.Date

data class Position(
    val pointID: Int = 0,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val time: Date = Date(0),
)
