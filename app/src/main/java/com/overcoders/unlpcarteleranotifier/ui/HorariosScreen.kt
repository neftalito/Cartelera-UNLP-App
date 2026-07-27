/**
 * Permite seleccionar una materia y coordina los efectos de sus reservas de aulas.
 */
package com.overcoders.unlpcarteleranotifier.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.model.toMateriaCatalogIdOrNull
import com.overcoders.unlpcarteleranotifier.ui.horarios.HorariosContent
import com.overcoders.unlpcarteleranotifier.ui.horarios.HorariosContentState
import com.overcoders.unlpcarteleranotifier.ui.horarios.horariosHeaderActions
import com.overcoders.unlpcarteleranotifier.ui.horarios.rememberHorariosState
import kotlinx.coroutines.launch

@Composable
fun HorariosScreen(
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val state = rememberHorariosState()
    val materias by state.materiasFlow.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val selectedMateria = state.selectedMateria(materias)
    val filteredMaterias = state.filteredMaterias(materias)
    val visibleIssue = state.visibleIssue()
    val canShareHorarios = state.canShareHorarios(selectedMateria)

    LaunchedEffect(
        selectedMateria?.id,
        selectedMateria?.nombre,
        state.dropdownExpanded,
    ) {
        state.reconcileSelectedMateria(selectedMateria)
    }

    LaunchedEffect(Unit) {
        state.loadMaterias()
    }

    LaunchedEffect(selectedMateria?.id) {
        if (state.recordDisplayedMateria(selectedMateria?.id)) {
            if (listState.layoutInfo.totalItemsCount > 0) {
                listState.scrollToItem(0)
            }
        }
        selectedMateria?.let { materia -> state.loadHorarios(materia) }
    }

    LaunchedEffect(
        canShareHorarios,
        state.loadingMaterias,
        state.loadingHorarios,
        selectedMateria,
        state.horarioMateria,
        visibleIssue?.belongsToCatalog,
    ) {
        val refreshesCatalog = visibleIssue?.belongsToCatalog == true
        onHeaderActionsChange(
            horariosHeaderActions(
                context = context,
                canShare = canShareHorarios,
                horarioMateria = state.horarioMateria,
                refreshesCatalog = refreshesCatalog,
                refreshEnabled = if (refreshesCatalog) {
                    !state.loadingMaterias && !state.loadingHorarios
                } else {
                    selectedMateria?.id?.toMateriaCatalogIdOrNull() != null &&
                        !state.loadingHorarios
                },
                onRefresh = {
                    if (refreshesCatalog) {
                        scope.launch { state.loadMaterias(forceRefresh = true) }
                    } else {
                        selectedMateria?.let { materia ->
                            state.loadHorarios(materia, forceRefresh = true)
                        }
                    }
                },
            )
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            state.dispose()
            onHeaderActionsChange(emptyList())
        }
    }

    HorariosContent(
        state = HorariosContentState(
            materias = materias,
            filteredMaterias = filteredMaterias,
            selectedMateria = selectedMateria,
            materiaQuery = state.materiaQuery,
            dropdownExpanded = state.dropdownExpanded,
            loadingMaterias = state.loadingMaterias,
            materiasError = state.materiasError,
            visibleIssue = visibleIssue,
            loadingPhase = state.loadingPhase(selectedMateria),
            horarioMateria = state.horarioMateria,
            expandedDaysMask = state.expandedDaysMask,
        ),
        listState = listState,
        onQueryChange = state::updateQuery,
        onDropdownExpandedChange = { isExpanded ->
            state.setDropdownExpanded(isExpanded, selectedMateria)
        },
        onDropdownDismiss = { state.dismissDropdown(selectedMateria) },
        onMateriaSelected = { materia ->
            state.selectMateria(materia)
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        },
        onClearSelection = state::clearSelection,
        onToggleDay = state::toggleDay,
    )
}
