/**
 * Centraliza los textos de fallos operativos para mantener una copia breve en release
 * y adjuntar contexto técnico únicamente en compilaciones de depuración.
 */
package com.overcoders.unlpcarteleranotifier.ui.common

import com.overcoders.unlpcarteleranotifier.BuildConfig
import kotlinx.coroutines.CancellationException

internal const val GENERIC_ERROR_MESSAGE = "Ocurrió un error. Intentá nuevamente."
internal const val CACHED_CONTENT_WARNING =
    "No se pudo actualizar la información. Se muestra la versión guardada."
internal const val PARTIAL_CONTENT_WARNING =
    "No se pudo actualizar toda la información. Se muestran los datos disponibles."

internal fun userFacingError(
    operation: String,
    error: Throwable? = null,
    detail: String? = null,
    isDebug: Boolean = BuildConfig.DEBUG,
): String = userFacingMessage(
    baseMessage = GENERIC_ERROR_MESSAGE,
    operation = operation,
    error = error,
    detail = detail,
    isDebug = isDebug,
)

internal fun cachedContentWarning(
    operation: String,
    error: Throwable? = null,
    detail: String? = null,
    isDebug: Boolean = BuildConfig.DEBUG,
): String = userFacingMessage(
    baseMessage = CACHED_CONTENT_WARNING,
    operation = operation,
    error = error,
    detail = detail,
    isDebug = isDebug,
)

internal fun partialContentWarning(
    operation: String,
    error: Throwable? = null,
    detail: String? = null,
    isDebug: Boolean = BuildConfig.DEBUG,
): String = userFacingMessage(
    baseMessage = PARTIAL_CONTENT_WARNING,
    operation = operation,
    error = error,
    detail = detail,
    isDebug = isDebug,
)

private fun userFacingMessage(
    baseMessage: String,
    operation: String,
    error: Throwable?,
    detail: String?,
    isDebug: Boolean,
): String {
    if (error is CancellationException) throw error
    if (!isDebug) return baseMessage

    val debugParts = buildList {
        operation.trim().takeIf(String::isNotEmpty)?.let(::add)
        error?.let { throwable ->
            val type = throwable::class.java.simpleName.ifBlank { "Error" }
            val message = throwable.message?.trim().orEmpty()
            add(if (message.isEmpty()) type else "$type: $message")
        }
        detail?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
    }

    return if (debugParts.isEmpty()) {
        baseMessage
    } else {
        "$baseMessage\nDetalle de depuración: ${debugParts.joinToString(" | ")}"
    }
}
