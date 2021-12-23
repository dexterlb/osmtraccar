package org.qtrp.osmtraccar

import okhttp3.HttpUrl

data class TraccarData(
    val user: String,
    val pass: String,
    val url: HttpUrl,
)
