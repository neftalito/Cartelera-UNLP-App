/** Verifica la normalización segura de adjuntos absolutos y relativos de cartelera. */
package com.overcoders.unlpcarteleranotifier.ui.cartelera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnuncioDetailScreenTest {
    @Test
    fun resolvesRootRelativeAttachmentAgainstGestionDocente() {
        assertEquals(
            "https://gestiondocente.info.unlp.edu.ar/guia.pdf",
            normalizeAttachmentUri("/guia.pdf"),
        )
    }

    @Test
    fun preservesAbsoluteHttpAttachment() {
        assertEquals(
            "https://cdn.example.com/guia.pdf",
            normalizeAttachmentUri("https://cdn.example.com/guia.pdf"),
        )
    }

    @Test
    fun rejectsUnsafeOrHostRelativeSchemes() {
        assertNull(normalizeAttachmentUri("javascript:alert(1)"))
        assertNull(normalizeAttachmentUri("//example.com/guia.pdf"))
    }
}
