/** Verifica la aceptación segura de URLs para intents externos. */
package com.overcoders.unlpcarteleranotifier.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
