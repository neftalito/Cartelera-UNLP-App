/**
 * Coordina los efectos Compose de la consulta del estado actual de las aulas.
 */
package com.overcoders.unlpcarteleranotifier.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.ui.aulas.AulasContent
import com.overcoders.unlpcarteleranotifier.ui.aulas.AulasContentState
import com.overcoders.unlpcarteleranotifier.ui.aulas.aulasHeaderActions
import com.overcoders.unlpcarteleranotifier.ui.aulas.rememberAulasState
import kotlinx.coroutines.launch

@Composable
fun AulasScreen(
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberAulasState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        state.load()
    }

    LaunchedEffect(state.loading, state.canShare, state.aulas) {
        onHeaderActionsChange(
            aulasHeaderActions(
                context = context,
                aulas = state.aulas,
                canShare = state.canShare,
                loading = state.loading,
                onRefresh = {
                    scope.launch { state.load(forceRefresh = true) }
                },
            )
        )
    }

    DisposableEffect(Unit) {
        onDispose { onHeaderActionsChange(emptyList()) }
    }

    AulasContent(
        state = AulasContentState(
            loadingPhase = state.loadingPhase,
            warning = state.warning,
            error = state.error,
            aulas = state.aulas,
        ),
        listState = listState,
    )
}
