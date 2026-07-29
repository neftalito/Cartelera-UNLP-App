/** Verifica el vencimiento temporal de valores cacheados. */
package com.overcoders.unlpcarteleranotifier.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedValueTest {
    @Test
    fun isFreshAtTtlBoundary() {
        val cached = CachedValue(value = "data", savedAtEpochMillis = 1_000L)

        assertTrue(cached.isFresh(ttlMillis = 500L, nowEpochMillis = 1_500L))
    }

    @Test
    fun isStaleAfterTtl() {
        val cached = CachedValue(value = "data", savedAtEpochMillis = 1_000L)

        assertFalse(cached.isFresh(ttlMillis = 500L, nowEpochMillis = 1_501L))
    }

    @Test
    fun futureTimestampIsNotFresh() {
        val cached = CachedValue(value = "data", savedAtEpochMillis = 2_000L)

        assertFalse(cached.isFresh(ttlMillis = 500L, nowEpochMillis = 1_999L))
    }
}
