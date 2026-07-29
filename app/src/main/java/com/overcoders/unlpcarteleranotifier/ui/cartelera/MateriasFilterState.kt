/**
 * Conserva por ID la materia aplicada y separa ese filtro de la búsqueda temporal del selector.
 */
package com.overcoders.unlpcarteleranotifier.ui.cartelera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.overcoders.unlpcarteleranotifier.model.MateriaCatalogItem
import com.overcoders.unlpcarteleranotifier.model.toMateriaCatalogIdOrNull

@Stable
class MateriasFilterState(
    expanded: Boolean = false,
    query: String = "",
    selected: MateriaCatalogItem? = null,
) {
    var expanded by mutableStateOf(expanded)
    var query by mutableStateOf(query)
    private var selectedId by mutableStateOf(selected?.id)
    private var selectedName by mutableStateOf(selected?.nombre.orEmpty())

    var selected: MateriaCatalogItem?
        get() = selectedId?.toMateriaCatalogIdOrNull()?.let { id ->
            MateriaCatalogItem(id = id.toString(), nombre = selectedName)
        }
        set(value) {
            selectedId = value?.id?.toMateriaCatalogIdOrNull()?.toString()
            selectedName = value?.nombre.takeIf { selectedId != null }.orEmpty()
        }

    fun beginEditing() {
        query = ""
        expanded = true
    }

    fun dismissEditing() {
        query = selected?.nombre.orEmpty()
        expanded = false
    }

    fun select(materia: MateriaCatalogItem) {
        selected = materia
        query = materia.nombre
        expanded = false
    }

    fun reconcileSelection(materia: MateriaCatalogItem) {
        if (selectedId != materia.id) return
        selectedName = materia.nombre
        if (!expanded) {
            query = materia.nombre
        }
    }

    fun clearSelection() {
        selected = null
        query = ""
        expanded = false
    }

    companion object {
        val Saver = listSaver<MateriasFilterState, String>(
            save = { state ->
                listOf(
                    state.selected?.id.orEmpty(),
                    state.selected?.nombre.orEmpty(),
                )
            },
            restore = { saved ->
                val selectedId = saved.getOrNull(0).orEmpty()
                val selectedName = saved.getOrNull(1).orEmpty()
                val validSelectedId = selectedId.toMateriaCatalogIdOrNull()?.toString()
                MateriasFilterState(
                    // El desplegable se restaura cerrado: una búsqueda a medio escribir no
                    // puede quedar visible como si fuera el filtro efectivamente aplicado.
                    query = selectedName.takeIf { validSelectedId != null }.orEmpty(),
                    selected = validSelectedId?.let { id ->
                        MateriaCatalogItem(id = id, nombre = selectedName)
                    },
                )
            },
        )
    }
}

@Composable
fun rememberMateriasFilterState(): MateriasFilterState {
    return rememberSaveable(saver = MateriasFilterState.Saver) {
        MateriasFilterState()
    }
}
