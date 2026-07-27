/** Define claves e invalidación de la caché paginada de reservas eventuales. */
package com.overcoders.unlpcarteleranotifier.ui.reservas

import com.overcoders.unlpcarteleranotifier.data.CachedValue
import com.overcoders.unlpcarteleranotifier.model.EventualReservationsPage

internal data class EventualReservationPageKey(
    val classroomId: Int?,
    val subjectId: Int?,
    val page: Int,
)

internal fun <T> MutableMap<EventualReservationPageKey, T>.invalidateFollowingPagesForFilters(
    classroomId: Int?,
    subjectId: Int?,
) {
    keys.removeAll { key ->
        key.classroomId == classroomId && key.subjectId == subjectId && key.page > 1
    }
}

internal fun shouldInvalidateEventualReservationPages(
    reset: Boolean,
    forceRefresh: Boolean,
    cacheIsFresh: Boolean,
): Boolean = reset && (forceRefresh || !cacheIsFresh)

internal fun <T> MutableMap<EventualReservationPageKey, T>
    .invalidateFollowingPagesAfterEventualReservationRefresh(
        classroomId: Int?,
        subjectId: Int?,
        reset: Boolean,
        forceRefresh: Boolean,
        cacheIsFresh: Boolean,
        refreshSucceeded: Boolean,
    ) {
        if (
            refreshSucceeded &&
            shouldInvalidateEventualReservationPages(reset, forceRefresh, cacheIsFresh)
        ) {
            invalidateFollowingPagesForFilters(classroomId, subjectId)
        }
    }

internal fun shouldUseCachedEventualReservationsFallback(
    reset: Boolean,
    cachedPage: CachedValue<EventualReservationsPage>?,
): Boolean = reset && cachedPage != null
