package com.example.toplutasima.network

import okhttp3.OkHttpClient
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object ApiClient {
    val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")

}
