/** Provee el cliente HTTP compartido y la identificación de red de la aplicación. */
package com.overcoders.unlpcarteleranotifier.data

import com.overcoders.unlpcarteleranotifier.BuildConfig
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

object AppHttpClient {
    private const val USER_AGENT = "UNLPCarteleraNotifier/${BuildConfig.VERSION_NAME}"

    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
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
