/**
 * Resuelve destinos de cartelera contra anuncios cargados y su restauración paginada.
 */
package com.overcoders.unlpcarteleranotifier.ui

import com.overcoders.unlpcarteleranotifier.model.CarteleraNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.Mensaje

internal fun findAnuncioForNotification(
    anuncios: List<Mensaje>,
    target: CarteleraNotificationTarget,
): Mensaje? {
    val localKey = target.localSelectionRestoration()?.announcementKey
    return anuncios.firstOrNull { anuncio ->
        if (localKey != null) {
            anuncio.anuncioSaveableKey() == localKey
        } else {
            anuncio.titulo == target.titulo &&
                anuncio.fecha == target.fecha &&
                anuncio.materia.trim().equals(target.materia.trim(), ignoreCase = true) &&
                (target.autor.isBlank() || anuncio.autor == target.autor)
        }
    }
}

internal data class LocalAnnouncementRestoration(
    val announcementKey: String,
    val pageIndex: Int,
)

internal fun Mensaje.toLocalRestorationTarget(
    materiaId: String?,
    pageIndex: Int,
): CarteleraNotificationTarget = CarteleraNotificationTarget(
    materiaId = materiaId,
    materia = materia,
    titulo = titulo,
    fecha = fecha,
    autor = autor,
    resumen = "$LOCAL_RESTORATION_PREFIX${pageIndex.coerceAtLeast(0)}:${anuncioSaveableKey()}",
    isAnulado = isAnulado,
)

internal fun CarteleraNotificationTarget.localSelectionRestoration():
    LocalAnnouncementRestoration? {
    if (!resumen.startsWith(LOCAL_RESTORATION_PREFIX)) return null
    val encoded = resumen.removePrefix(LOCAL_RESTORATION_PREFIX)
    val separator = encoded.indexOf(':')
    if (separator <= 0 || separator == encoded.lastIndex) return null
    val pageIndex = encoded.substring(0, separator).toIntOrNull() ?: return null
    return LocalAnnouncementRestoration(
        announcementKey = encoded.substring(separator + 1),
        pageIndex = pageIndex.coerceAtLeast(0),
    )
}

private const val LOCAL_RESTORATION_PREFIX = "local-selection-v1:"

internal fun canResolveCarteleraTargetFromCurrentAnnouncements(
    isLoading: Boolean,
    refreshFailed: Boolean,
    expectedFilterLoaded: Boolean,
    targetRefreshCompleted: Boolean,
): Boolean {
    if (isLoading || !targetRefreshCompleted) return false
    return refreshFailed || expectedFilterLoaded
}

internal enum class CarteleraTargetRefreshState {
    REQUIRED,
    IN_PROGRESS,
    COMPLETED,
}
