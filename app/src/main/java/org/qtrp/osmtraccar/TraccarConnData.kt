package org.qtrp.osmtraccar

import okhttp3.HttpUrl

data class TraccarConnData(
    val email: String,
    val pass: String,
    val url: HttpUrl,
)
