/** Conserva la selección aplicada y separa la búsqueda temporal de los filtros. */
package com.overcoders.unlpcarteleranotifier.ui.reservas

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.overcoders.unlpcarteleranotifier.model.EventualReservationFilterOption
import com.overcoders.unlpcarteleranotifier.ui.common.normalizeForSearch

@Stable
internal class EventualReservationFilterState(
    private val selectedIdState: MutableState<Int?>,
    private val selectedLabelState: MutableState<String>,
) {
    var query by mutableStateOf(
        selectedLabelState.value.takeIf { selectedIdState.value != null }.orEmpty()
    )
        private set

    var expanded by mutableStateOf(false)
        private set

    val selectedId: Int?
        get() = selectedIdState.value

    fun selectedOption(
        options: List<EventualReservationFilterOption>
    ): EventualReservationFilterOption? = selectedId?.let { id ->
        options.firstOrNull { it.id == id }
            ?: EventualReservationFilterOption(id = id, label = selectedLabelState.value)
    }

    fun updateQuery(value: String) {
        query = value
    }

    fun setExpanded(
        value: Boolean,
        options: List<EventualReservationFilterOption>,
    ) {
        query = if (value) {
            ""
        } else {
            selectedOption(options)?.label.orEmpty()
        }
        expanded = value
        if (!value) {
            reconcileSelection(options)
        }
    }

    fun select(option: EventualReservationFilterOption) {
        selectedIdState.value = option.id
        selectedLabelState.value = option.label
        query = option.label
        expanded = false
    }

    fun clear() {
        selectedIdState.value = null
        selectedLabelState.value = ""
        query = ""
        expanded = false
    }

    fun reconcileSelection(options: List<EventualReservationFilterOption>) {
        val current = selectedOption(options) ?: return
        if (!expanded && selectedLabelState.value != current.label) {
            selectedLabelState.value = current.label
            query = current.label
        }
    }

    fun filteredOptions(
        options: List<EventualReservationFilterOption>
    ): List<EventualReservationFilterOption> {
        val normalizedQuery = query.normalizeForSearch()
        return if (normalizedQuery.isBlank()) {
            options
        } else {
            options.filter { option ->
                option.label.normalizeForSearch().contains(normalizedQuery)
            }
        }
    }
}
