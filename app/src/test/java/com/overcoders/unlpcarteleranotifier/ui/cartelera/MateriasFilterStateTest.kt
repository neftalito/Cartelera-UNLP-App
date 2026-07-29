/**
 * Verifica que la búsqueda temporal no modifique el filtro de materias ya aplicado.
 */
package com.overcoders.unlpcarteleranotifier.ui.cartelera

import com.overcoders.unlpcarteleranotifier.model.MateriaCatalogItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MateriasFilterStateTest {
    private val materia = MateriaCatalogItem(id = "42", nombre = "Sistemas Operativos")

    @Test
    fun editingAndDismissingKeepsAppliedSelection() {
        val state = MateriasFilterState(selected = materia, query = materia.nombre)

        state.beginEditing()
        state.query = "otra materia"

        assertTrue(state.expanded)
        assertEquals(materia, state.selected)

        state.dismissEditing()

        assertFalse(state.expanded)
        assertEquals(materia, state.selected)
        assertEquals(materia.nombre, state.query)
    }

    @Test
    fun clearingSelectionIsTheOnlyExplicitRemovalPath() {
        val state = MateriasFilterState(selected = materia, query = materia.nombre)

        state.clearSelection()

        assertNull(state.selected)
        assertEquals("", state.query)
        assertFalse(state.expanded)
    }

    @Test
    fun restoreDiscardsUnappliedSearchAndShowsAppliedSelection() {
        val restored = MateriasFilterState.Saver.restore(listOf(materia.id, materia.nombre))

        requireNotNull(restored)
        assertFalse(restored.expanded)
        assertEquals(materia, restored.selected)
        assertEquals(materia.nombre, restored.query)
    }
}
