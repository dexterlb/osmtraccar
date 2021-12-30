package org.qtrp.osmtraccar

import java.time.Instant

data class Position(
    val pointID: Int = 0,
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val time: Instant = Instant.ofEpochSecond(0)
)
