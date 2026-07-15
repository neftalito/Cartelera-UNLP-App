/** Adapta llamadas asíncronas de OkHttp a coroutines cancelables. */
package com.overcoders.unlpcarteleranotifier.data

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class ApiException(
    val httpCode: Int?,
    message: String,
) : RuntimeException(message)

internal suspend fun OkHttpClient.awaitBody(request: Request): String =
    withContext(Dispatchers.IO) {
        readBody(request)
    }

/** Mantiene tanto la lectura como el parseo síncrono fuera del hilo principal. */
internal suspend fun <T> OkHttpClient.awaitParsedBody(
    request: Request,
    parser: (String) -> T,
): T = withContext(Dispatchers.IO) {
    parser(readBody(request))
}

private suspend fun OkHttpClient.readBody(request: Request): String =
    newCall(request).awaitBody()

internal suspend fun Call.awaitBody(): String =
    suspendCancellableCoroutine { continuation ->
        // La continuación permanece suspendida hasta terminar de consumir el body. De esta
        // forma, una cancelación posterior a los headers también cierra la llamada en curso.
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (!continuation.isActive) {
                    response.close()
                    return
                }
                try {
                    val body = response.use {
                        if (!it.isSuccessful) {
                            throw ApiException(
                                it.code,
                                "HTTP ${it.code} - ${it.message}"
                            )
                        }
                        it.body.string()
                    }
                    if (continuation.isActive) {
                        continuation.resume(body)
                    }
                } catch (error: Exception) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(error)
                    }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }
        })
    }
