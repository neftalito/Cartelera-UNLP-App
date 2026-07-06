package com.overcoders.unlpcarteleranotifier.data

import com.overcoders.unlpcarteleranotifier.BuildConfig
import okhttp3.OkHttpClient

object AppHttpClient {
    private const val USER_AGENT = "UNLPCarteleraNotifier/${BuildConfig.VERSION_NAME}"

    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                    .newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .build()
    }
}
