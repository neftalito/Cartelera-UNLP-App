/** Verifica unicidad y estabilidad de las claves guardables de anuncios. */
package com.overcoders.unlpcarteleranotifier.ui

import com.overcoders.unlpcarteleranotifier.model.Adjunto
import com.overcoders.unlpcarteleranotifier.model.Mensaje
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnuncioIdentityTest {
    @Test
    fun lazyListKeyIsStableAndBundleCompatible() {
        val message = message(materia = "Algoritmos", titulo = "Final")

        val key = message.anuncioSaveableKey()

        assertEquals(key, message.copy().anuncioSaveableKey())
        assertTrue(key.matches(Regex("anuncio_[0-9a-f]{64}")))
    }

    @Test
    fun lengthPrefixPreventsConcatenationCollisions() {
        val first = message(materia = "ab", titulo = "c")
        val second = message(materia = "a", titulo = "bc")

        assertNotEquals(first.anuncioSaveableKey(), second.anuncioSaveableKey())
    }

    @Test
    fun differentContentsDoNotShareIdentityOrLazyListKey() {
        val original = message(
            materia = "Algoritmos",
            titulo = "Final",
            cuerpoHtml = "<p>Aula 1</p>"
        )
        val edited = original.copy(cuerpoHtml = "<p>Aula 2</p>")
        val cancelled = original.copy(isAnulado = true)
        val withAttachment = original.copy(
            adjuntos = listOf(Adjunto("Consigna", "https://example.test/consigna.pdf"))
        )

        listOf(edited, cancelled, withAttachment).forEach { variant ->
            assertNotEquals(original.anuncioIdentity(), variant.anuncioIdentity())
            assertNotEquals(original.anuncioSaveableKey(), variant.anuncioSaveableKey())
        }
    }

    private fun message(
        materia: String,
        titulo: String,
        cuerpoHtml: String = "",
    ) = Mensaje(
        materia = materia,
        titulo = titulo,
        cuerpoHtml = cuerpoHtml,
        fecha = "13/07/2026",
        autor = "Cátedra",
        isAnulado = false,
        adjuntos = emptyList()
    )
}
