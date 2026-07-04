package com.overcoders.unlpcarteleranotifier.data

import com.overcoders.unlpcarteleranotifier.BuildConfig
import okhttp3.OkHttpClient

object AppHttpClient {
    private val userAgent = "UNLPCarteleraNotifier/${BuildConfig.VERSION_NAME}"

    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                    .newBuilder()
                    .header("User-Agent", userAgent)
                    .build()
                chain.proceed(request)
            }
            .build()
    }
}
