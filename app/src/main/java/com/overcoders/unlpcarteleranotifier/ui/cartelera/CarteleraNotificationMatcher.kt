/**
 * Resuelve destinos de cartelera contra anuncios cargados y su restauración paginada.
 */
package com.overcoders.unlpcarteleranotifier.ui.cartelera

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

internal fun notificationTargetFilterId(
    targetMateriaId: String?,
    availableMateriaIds: Set<String>,
    useGlobalFallback: Boolean = false,
): String? = if (useGlobalFallback) {
    null
} else {
    targetMateriaId?.takeIf(availableMateriaIds::contains)
}

internal enum class NotificationSearchDecision {
    LOAD_NEXT_PAGE,
    RETRY_GLOBAL_FEED,
    NOT_FOUND,
    SEARCH_LIMIT_REACHED,
}

internal fun notificationSearchDecision(
    hasMore: Boolean,
    automaticallyLoadedPages: Int,
    searchedWithMateriaFilter: Boolean = false,
    globalFallbackAttempted: Boolean = false,
    isLocalRestoration: Boolean = false,
    maximumAutomaticPages: Int = DEFAULT_NOTIFICATION_SEARCH_PAGE_LIMIT,
): NotificationSearchDecision = when {
    hasMore && automaticallyLoadedPages < maximumAutomaticPages ->
        NotificationSearchDecision.LOAD_NEXT_PAGE
    searchedWithMateriaFilter && !globalFallbackAttempted && !isLocalRestoration ->
        NotificationSearchDecision.RETRY_GLOBAL_FEED
    !hasMore -> NotificationSearchDecision.NOT_FOUND
    else ->
        NotificationSearchDecision.SEARCH_LIMIT_REACHED
}

internal const val DEFAULT_NOTIFICATION_SEARCH_PAGE_LIMIT = 5
