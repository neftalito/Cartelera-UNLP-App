/** Verifica la normalización de texto usada por los buscadores. */
package com.overcoders.unlpcarteleranotifier.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchNormalizerTest {
    @Test
    fun removesAccentsAndNormalizesCase() {
        assertEquals("matematica", "Matemática".normalizeForSearch())
    }

    @Test
    fun compactsRegularAndNonBreakingWhitespace() {
        assertEquals("licenciatura en sistemas", "  Licenciatura\u00A0 en   Sistemas ".normalizeForSearch())
    }

    @Test
    fun normalizedQueryMatchesAccentedLabel() {
        assertTrue(
            "Diseño de Interacción".normalizeForSearch()
                .contains("diseno de interaccion".normalizeForSearch())
        )
    }
}
