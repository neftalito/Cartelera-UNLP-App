/** Verifica la aplicación e invalidación de páginas de reservas eventuales. */
package com.overcoders.unlpcarteleranotifier.ui

import com.overcoders.unlpcarteleranotifier.data.CachedValue
import com.overcoders.unlpcarteleranotifier.model.EventualReservationsPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventualReservationPageCacheTest {
    @Test
    fun invalidatesOnForcedOrStaleResetButNotDuringPagination() {
        assertTrue(
            shouldInvalidateEventualReservationPages(
                reset = true,
                forceRefresh = true,
                cacheIsFresh = true
            )
        )
        assertTrue(
            shouldInvalidateEventualReservationPages(
                reset = true,
                forceRefresh = false,
                cacheIsFresh = false
            )
        )
        assertFalse(
            shouldInvalidateEventualReservationPages(
                reset = false,
                forceRefresh = true,
                cacheIsFresh = false
            )
        )
        assertFalse(
            shouldInvalidateEventualReservationPages(
                reset = true,
                forceRefresh = false,
                cacheIsFresh = true
            )
        )
    }

    @Test
    fun failedRefreshKeepsAllCachedPages() {
        val pageOne = EventualReservationPageKey(10, 20, 1)
        val pageTwo = EventualReservationPageKey(10, 20, 2)
        val cache = mutableMapOf(pageOne to "one", pageTwo to "two")

        cache.invalidateFollowingPagesAfterEventualReservationRefresh(
            classroomId = 10,
            subjectId = 20,
            reset = true,
            forceRefresh = true,
            cacheIsFresh = false,
            refreshSucceeded = false
        )

        assertTrue(cache.containsKey(pageOne))
        assertTrue(cache.containsKey(pageTwo))
        assertEquals(2, cache.size)
    }

    @Test
    fun successfulRefreshKeepsTheFirstPageAndInvalidatesFollowingPagesForItsFilters() {
        val sameFiltersPageOne = EventualReservationPageKey(10, 20, 1)
        val sameFiltersPageTwo = EventualReservationPageKey(10, 20, 2)
        val otherClassroom = EventualReservationPageKey(11, 20, 1)
        val otherSubject = EventualReservationPageKey(10, 21, 1)
        val cache = mutableMapOf(
            sameFiltersPageOne to "one",
            sameFiltersPageTwo to "two",
            otherClassroom to "classroom",
            otherSubject to "subject"
        )

        cache.invalidateFollowingPagesAfterEventualReservationRefresh(
            classroomId = 10,
            subjectId = 20,
            reset = true,
            forceRefresh = true,
            cacheIsFresh = false,
            refreshSucceeded = true
        )

        assertTrue(cache.containsKey(sameFiltersPageOne))
        assertFalse(cache.containsKey(sameFiltersPageTwo))
        assertTrue(cache.containsKey(otherClassroom))
        assertTrue(cache.containsKey(otherSubject))
        assertEquals(3, cache.size)
    }

    @Test
    fun cachedEmptyPageIsAValidRefreshFailureFallback() {
        val cachedEmptyPage = CachedValue(
            value = EventualReservationsPage(
                reservations = emptyList(),
                currentPage = 1,
                nextPage = null,
                totalCount = 0
            ),
            savedAtEpochMillis = 1_000L
        )

        assertTrue(
            shouldUseCachedEventualReservationsFallback(
                reset = true,
                cachedPage = cachedEmptyPage
            )
        )
        assertFalse(
            shouldUseCachedEventualReservationsFallback(
                reset = true,
                cachedPage = null
            )
        )
        assertFalse(
            shouldUseCachedEventualReservationsFallback(
                reset = false,
                cachedPage = cachedEmptyPage
            )
        )
    }
}
