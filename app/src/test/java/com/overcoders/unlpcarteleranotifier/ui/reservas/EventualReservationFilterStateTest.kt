/** Verifica que la búsqueda temporal no modifique el filtro eventual aplicado. */
package com.overcoders.unlpcarteleranotifier.ui.reservas

import androidx.compose.runtime.mutableStateOf
import com.overcoders.unlpcarteleranotifier.model.EventualReservationFilterOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventualReservationFilterStateTest {
    private val classroom = EventualReservationFilterOption(id = 12, label = "Aula 12")

    @Test
    fun editingAndDismissingKeepsAppliedSelection() {
        val state = stateWith(classroom)

        state.setExpanded(true, listOf(classroom))
        state.updateQuery("otra aula")

        assertTrue(state.expanded)
        assertEquals(classroom.id, state.selectedId)

        state.setExpanded(false, listOf(classroom))

        assertFalse(state.expanded)
        assertEquals(classroom.id, state.selectedId)
        assertEquals(classroom.label, state.query)
    }

    @Test
    fun refreshedOptionsReconcileTheSavedLabel() {
        val state = stateWith(classroom)
        val renamed = classroom.copy(label = "Aula Magna")

        state.reconcileSelection(listOf(renamed))

        assertEquals(renamed, state.selectedOption(listOf(renamed)))
        assertEquals(renamed.label, state.query)
    }

    @Test
    fun searchIsCaseAndAccentInsensitive() {
        val state = emptyState()
        val options = listOf(
            EventualReservationFilterOption(id = 1, label = "Álgebra"),
            EventualReservationFilterOption(id = 2, label = "Programación"),
        )

        state.updateQuery("algebra")

        assertEquals(listOf(options.first()), state.filteredOptions(options))
    }

    @Test
    fun clearIsTheOnlyExplicitRemovalPath() {
        val state = stateWith(classroom)

        state.clear()

        assertNull(state.selectedId)
        assertEquals("", state.query)
        assertFalse(state.expanded)
    }

    private fun stateWith(option: EventualReservationFilterOption) =
        EventualReservationFilterState(
            selectedIdState = mutableStateOf(option.id),
            selectedLabelState = mutableStateOf(option.label),
        )

    private fun emptyState() = EventualReservationFilterState(
        selectedIdState = mutableStateOf(null),
        selectedLabelState = mutableStateOf(""),
    )
}
