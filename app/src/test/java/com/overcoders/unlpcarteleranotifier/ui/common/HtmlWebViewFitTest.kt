/** Verifica el cálculo que adapta tablas HTML al ancho del WebView. */
package com.overcoders.unlpcarteleranotifier.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlWebViewFitTest {
    @Test
    fun zoomsOutToFitDoubleWidthContent() {
        assertEquals(
            0.5f,
            fitContentWidthAdjustment(
                viewportWidth = 400,
                contentWidth = 800,
                currentScale = 1f,
                maximumScale = 1f,
            ),
        )
    }

    @Test
    fun fitsExtremelyWideContentAtSupportedMinimumZoom() {
        assertEquals(
            0.01f,
            fitContentWidthAdjustment(
                viewportWidth = 320,
                contentWidth = 32_000,
                currentScale = 1f,
                maximumScale = 1f,
            ),
        )
    }

    @Test
    fun leavesScaleUnchangedWhenContentAlreadyFits() {
        assertNull(
            fitContentWidthAdjustment(
                viewportWidth = 400,
                contentWidth = 400,
                currentScale = 1f,
                maximumScale = 1f,
            )
        )
    }

    @Test
    fun canZoomBackInWithoutExceedingOriginalScale() {
        assertEquals(
            1.5f,
            fitContentWidthAdjustment(
                viewportWidth = 600,
                contentWidth = 400,
                currentScale = 0.5f,
                maximumScale = 1f,
            ),
        )
        assertEquals(
            2f,
            fitContentWidthAdjustment(
                viewportWidth = 1_000,
                contentWidth = 400,
                currentScale = 0.5f,
                maximumScale = 1f,
            ),
        )
    }

    @Test
    fun ignoresMeasurementsThatAreNotReady() {
        assertNull(
            fitContentWidthAdjustment(
                viewportWidth = 0,
                contentWidth = 800,
                currentScale = 1f,
                maximumScale = 1f,
            )
        )
    }

    @Test
    fun disablesWebViewOverviewWhenCustomWidthFitIsEnabled() {
        assertFalse(shouldUseOverviewMode(fitContentWidth = true))
        assertTrue(shouldUseOverviewMode(fitContentWidth = false))
    }
}
