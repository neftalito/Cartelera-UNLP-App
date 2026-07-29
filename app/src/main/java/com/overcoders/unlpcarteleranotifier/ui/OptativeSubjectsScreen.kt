/**
 * Conecta los efectos de Compose con el estado y la presentación de materias optativas.
 */
package com.overcoders.unlpcarteleranotifier.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.HeaderActionPlacement
import com.overcoders.unlpcarteleranotifier.ui.herramientas.OptativeSubjectsContent
import com.overcoders.unlpcarteleranotifier.ui.herramientas.rememberOptativeSubjectsState

@Composable
fun OptativeSubjectsScreen(
    onFullscreenDetailChange: (Boolean) -> Unit = {},
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {}
) {
    val state = rememberOptativeSubjectsState()

    LaunchedEffect(state.selectedCareer, state.selectedYear) {
        state.loadPage(
            year = state.selectedYear,
            career = state.selectedCareer,
        )
    }

    val showFullscreenAction = state.isFullscreen || state.hasPage
    val headerActions = remember(
        state.isFullscreen,
        showFullscreenAction,
        state.loading,
        state.selectedYear,
        state.selectedCareer,
    ) {
        buildList {
            add(
                HeaderAction(
                    icon = Icons.Default.Refresh,
                    enabled = !state.loading,
                    contentDescription = "Refrescar materias optativas",
                    onClick = {
                        state.loadPage(
                            year = state.selectedYear,
                            career = state.selectedCareer,
                            forceRefresh = true,
                        )
                    },
                ),
            )
            if (state.isFullscreen) {
                add(
                    HeaderAction(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        placement = HeaderActionPlacement.Leading,
                        onClick = state::exitFullscreen,
                    ),
                )
            }
            if (showFullscreenAction) {
                add(
                    HeaderAction(
                        icon = if (state.isFullscreen) {
                            Icons.Default.FullscreenExit
                        } else {
                            Icons.Default.Fullscreen
                        },
                        contentDescription = if (state.isFullscreen) {
                            "Salir de pantalla completa"
                        } else {
                            "Ver en pantalla completa"
                        },
                        onClick = state::toggleFullscreen,
                    ),
                )
            }
        }
    }

    LaunchedEffect(headerActions) {
        onHeaderActionsChange(headerActions)
    }

    LaunchedEffect(state.isFullscreen) {
        onFullscreenDetailChange(state.isFullscreen)
    }

    BackHandler(enabled = state.isFullscreen) {
        state.exitFullscreen()
    }

    DisposableEffect(state) {
        onDispose {
            state.cancelLoad()
            onHeaderActionsChange(emptyList())
            onFullscreenDetailChange(false)
        }
    }

    OptativeSubjectsContent(
        state = state.uiState,
        onCareerDropdownExpandedChange = state::updateCareerDropdownExpanded,
        onYearDropdownExpandedChange = state::updateYearDropdownExpanded,
        onCareerSelected = state::selectCareer,
        onYearSelected = state::selectYear,
    )
}
