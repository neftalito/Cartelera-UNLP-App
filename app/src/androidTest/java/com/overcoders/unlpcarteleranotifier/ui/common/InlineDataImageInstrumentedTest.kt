/** Verifica la decodificación Android y la integración real con HtmlCompat. */
package com.overcoders.unlpcarteleranotifier.ui.common

import android.graphics.Bitmap
import android.graphics.Color
import android.text.style.ImageSpan
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.overcoders.unlpcarteleranotifier.ui.parseHtmlWithoutTextColors
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InlineDataImageInstrumentedTest {
    private val resources
        get() = InstrumentationRegistry.getInstrumentation().targetContext.resources

    @Test
    fun decodesPngIntoDrawableWithBoundedNonEmptyBounds() {
        val source = createPngDataSource(width = 940, height = 570)
        val drawable = InlineDataImageGetter(resources, maxWidthPx = 296).getDrawable(source)

        assertNotNull(drawable)
        requireNotNull(drawable)
        assertTrue(drawable.bounds.width() in 1..296)
        assertTrue(drawable.bounds.height() > 0)
        assertEquals(
            940.0 / 570.0,
            drawable.bounds.width().toDouble() / drawable.bounds.height(),
            0.01,
        )
    }

    @Test
    fun returnsNullForInvalidUnsupportedOrOversizedSource() {
        val getter = InlineDataImageGetter(
            resources = resources,
            maxWidthPx = 296,
            maxImageBytes = 4,
        )

        assertNull(getter.getDrawable("data:image/png;base64,AA*A"))
        assertNull(getter.getDrawable("data:image/svg+xml;base64,PHN2Zz4="))
        assertNull(getter.getDrawable(createPngDataSource(width = 2, height = 2)))
    }

    @Test
    fun createsImageSpanAndPreservesSurroundingText() {
        val source = createPngDataSource(width = 94, height = 57)
        val parsed = parseHtmlWithoutTextColors(
            "<p>Antes<img src=\"$source\" />Después</p>",
            InlineDataImageGetter(resources, maxWidthPx = 47),
        )
        val spans = parsed.getSpans(0, parsed.length, ImageSpan::class.java)

        assertEquals(1, spans.size)
        assertTrue(parsed.toString().contains("Antes"))
        assertTrue(parsed.toString().contains("Después"))
        assertTrue(spans.single().drawable.bounds.width() in 1..47)
        assertTrue(spans.single().drawable.bounds.height() > 0)
    }

    private fun createPngDataSource(width: Int, height: Int): String {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        bitmap.recycle()
        return "data:image/png;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
    }
}
