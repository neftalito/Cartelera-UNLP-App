/**
 * Verifica el recorte del resumen y la diferenciación visual entre anuncios nuevos y vistos.
 */
package com.overcoders.unlpcarteleranotifier.ui.cartelera

import org.junit.Assert.assertEquals
import org.junit.Test

class AnuncioCardTest {
    @Test
    fun seenAnnouncementsAreDimmedWhileNewOnesRemainOpaque() {
        assertEquals(1f, anuncioCardAlpha(isNew = true))
        assertEquals(0.6f, anuncioCardAlpha(isNew = false))
    }

    @Test
    fun previewDoesNotAddEllipsisWhenVisibleTextFits() {
        assertEquals("Texto corto", truncateAnuncioPreview("Texto corto", limit = 20))
    }

    @Test
    fun previewAddsEllipsisOnlyWhenVisibleTextExceedsLimit() {
        assertEquals("Texto…", truncateAnuncioPreview("Texto largo", limit = 5))
    }
}
