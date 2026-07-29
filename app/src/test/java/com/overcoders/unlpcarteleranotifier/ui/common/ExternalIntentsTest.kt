/** Verifica la aceptación segura de URLs para intents externos. */
package com.overcoders.unlpcarteleranotifier.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalIntentsTest {
    @Test
    fun acceptsHttpUrlsWithAHost() {
        assertEquals(
            "https://example.com/path",
            normalizeExternalHttpUrl("  https://example.com/path  ")
        )
        assertEquals(
            "HTTP://example.com",
            normalizeExternalHttpUrl("HTTP://example.com")
        )
    }

    @Test
    fun rejectsUnsafeOrMalformedUrls() {
        assertNull(normalizeExternalHttpUrl("javascript:alert(1)"))
        assertNull(normalizeExternalHttpUrl("https:///missing-host"))
        assertNull(normalizeExternalHttpUrl("not a url"))
    }

    @Test
    fun buildsPlayStoreWebUrlFromPackageName() {
        assertEquals(
            "https://play.google.com/store/apps/details?id=com.example.app",
            playStoreWebUrl("com.example.app")
        )
    }

    @Test
    fun usesFallbackOnlyWhenPrimaryHandlerFails() {
        var fallbackCalls = 0

        assertTrue(
            openWithFallback(
                primary = { true },
                fallback = {
                    fallbackCalls += 1
                    true
                },
            )
        )
        assertEquals(0, fallbackCalls)

        assertTrue(
            openWithFallback(
                primary = { false },
                fallback = {
                    fallbackCalls += 1
                    true
                },
            )
        )
        assertEquals(1, fallbackCalls)
    }

    @Test
    fun reportsFailureWhenNeitherHandlerCanOpenTheListing() {
        assertFalse(
            openWithFallback(
                primary = { false },
                fallback = { false },
            )
        )
    }
}
