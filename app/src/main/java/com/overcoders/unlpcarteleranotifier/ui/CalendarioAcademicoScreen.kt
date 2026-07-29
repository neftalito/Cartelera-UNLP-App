/**
 * Conecta los efectos de Compose con el estado y la presentación del calendario académico.
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
import com.overcoders.unlpcarteleranotifier.ui.herramientas.CalendarioAcademicoContent
import com.overcoders.unlpcarteleranotifier.ui.herramientas.rememberCalendarioAcademicoState

@Composable
fun CalendarioAcademicoScreen(
    onFullscreenDetailChange: (Boolean) -> Unit = {},
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {}
) {
    val state = rememberCalendarioAcademicoState()

    LaunchedEffect(state.selectedYear) {
        state.loadCalendario(state.selectedYear)
    }

    val showFullscreenAction = state.isFullscreen || state.hasCalendar
    val headerActions = remember(
        state.isFullscreen,
        showFullscreenAction,
        state.loading,
        state.selectedYear,
    ) {
        buildList {
            add(
                HeaderAction(
                    icon = Icons.Default.Refresh,
                    enabled = !state.loading,
                    contentDescription = "Refrescar calendario académico",
                    onClick = {
                        state.loadCalendario(
                            anio = state.selectedYear,
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

    CalendarioAcademicoContent(
        state = state.uiState,
        onDropdownExpandedChange = state::updateDropdownExpanded,
        onYearSelected = state::selectYear,
    )
}
