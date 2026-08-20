/** Verifica validación, límites y escalado de imágenes embebidas sin depender de Android. */
package com.overcoders.unlpcarteleranotifier.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InlineDataImageTest {
    @Test
    fun acceptsSupportedBase64PngDataUrl() {
        val payload = parseInlineDataImageSource(
            "data:image/png;base64,iVBORw0KGgo=",
            maxDecodedBytes = 16,
        )

        requireNotNull(payload)
        assertEquals("image/png", payload.mimeType)
        assertEquals("iVBORw0KGgo=", payload.encodedData)
        assertEquals(8, payload.estimatedDecodedBytes)
        assertTrue(
            matchesInlineImageSignature(
                payload.mimeType,
                byteArrayOf(
                    0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                ),
            )
        )
    }

    @Test
    fun rejectsMalformedUnsupportedAndOversizedSources() {
        assertNull(parseInlineDataImageSource("https://example.com/image.png"))
        assertNull(parseInlineDataImageSource("data:image/svg+xml;base64,PHN2Zz4="))
        assertNull(parseInlineDataImageSource("data:image/png,AAAA"))
        assertNull(parseInlineDataImageSource("data:image/png;base64,AA*A"))
        assertNull(parseInlineDataImageSource("data:image/png;base64,A==="))
        assertNull(
            parseInlineDataImageSource(
                "data:image/png;base64,iVBORw0KGgo=",
                maxDecodedBytes = 7,
            )
        )
    }

    @Test
    fun scalesDownPreservingAspectRatioAndNeverUpscales() {
        val scaled = requireNotNull(
            fitInlineImageDimensions(
                sourceWidth = 940,
                sourceHeight = 570,
                maxWidth = 296,
            )
        )
        assertEquals(296, scaled.width)
        assertTrue(scaled.height in 179..180)
        assertEquals(940.0 / 570.0, scaled.width.toDouble() / scaled.height, 0.01)

        assertEquals(
            InlineImageDimensions(100, 50),
            fitInlineImageDimensions(100, 50, maxWidth = 300),
        )
    }

    @Test
    fun validatesDimensionsWithLongArithmeticAndCalculatesSampling() {
        assertFalse(isValidInlineImageSourceDimensions(0, 100))
        assertFalse(isValidInlineImageSourceDimensions(100, -1))
        assertFalse(isValidInlineImageSourceDimensions(8_193, 1))
        assertFalse(
            isValidInlineImageSourceDimensions(
                width = 50_000,
                height = 50_000,
                maxSide = 100_000,
                maxPixels = 2_000_000_000L,
            )
        )
        assertTrue(
            isValidInlineImageSourceDimensions(
                width = 50_000,
                height = 50_000,
                maxSide = 100_000,
                maxPixels = 3_000_000_000L,
            )
        )
        assertEquals(
            4,
            calculateInlineImageSampleSize(
                sourceWidth = 4_000,
                sourceHeight = 4_000,
                targetWidth = 1_414,
                targetHeight = 1_414,
            ),
        )
        assertTrue(estimatedSampledImagePixels(4_000, 4_000, 4) <= 2_000_000L)
    }
}
