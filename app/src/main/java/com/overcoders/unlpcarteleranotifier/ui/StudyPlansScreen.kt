/**
 * Conecta los efectos de Compose con el estado y la presentación de planes de estudio.
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
import com.overcoders.unlpcarteleranotifier.ui.herramientas.StudyPlansContent
import com.overcoders.unlpcarteleranotifier.ui.herramientas.rememberStudyPlansState

@Composable
fun StudyPlansScreen(
    onFullscreenDetailChange: (Boolean) -> Unit = {},
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {}
) {
    val state = rememberStudyPlansState()
    val selectedPlanSource = state.selectedPlanSource

    LaunchedEffect(selectedPlanSource?.id) {
        if (selectedPlanSource == null) {
            state.cancelLoad()
        } else {
            state.loadPlan(selectedPlanSource)
        }
    }

    val showFullscreenAction = state.isFullscreen || state.selectedPlan != null
    val headerActions = remember(
        state.isFullscreen,
        showFullscreenAction,
        state.loading,
        selectedPlanSource,
    ) {
        buildList {
            add(
                HeaderAction(
                    icon = Icons.Default.Refresh,
                    enabled = !state.loading && selectedPlanSource != null,
                    contentDescription = "Refrescar plan de estudio",
                    onClick = {
                        selectedPlanSource?.let { source ->
                            state.loadPlan(source, forceRefresh = true)
                        }
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

    StudyPlansContent(
        state = state.uiState,
        onCareerDropdownExpandedChange = state::updateCareerDropdownExpanded,
        onPlanDropdownExpandedChange = state::updatePlanDropdownExpanded,
        onCareerSelected = state::selectCareer,
        onPlanSelected = state::selectPlan,
    )
}
