/** Permite administrar materias suscriptas y validarlas contra el catálogo. */
package com.overcoders.unlpcarteleranotifier.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.data.MateriasRepository
import com.overcoders.unlpcarteleranotifier.data.SettingsStore
import com.overcoders.unlpcarteleranotifier.data.SubscripcionesStore
import com.overcoders.unlpcarteleranotifier.ui.subscripciones.SubscripcionesContent
import com.overcoders.unlpcarteleranotifier.ui.subscripciones.rememberSubscripcionesState
import kotlinx.coroutines.flow.map

@Composable
fun SubscripcionesScreen(
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {},
) {
    val context = LocalContext.current
    val materiasRepository = remember(context) { MateriasRepository.get(context) }
    val state = rememberSubscripcionesState(materiasRepository)
    val currentOnHeaderActionsChange = rememberUpdatedState(onHeaderActionsChange)

    val materias by materiasRepository.items.collectAsStateWithLifecycle()
    val subscriptionsFlow = remember(context) {
        SubscripcionesStore.subscripcionesFlow(context)
    }
    val notifyAllFlow = remember(context) {
        SettingsStore.notifyAllFlow(context).map<Boolean, Boolean?> { it }
    }
    val persistedSubscriptionIds by subscriptionsFlow.collectAsStateWithLifecycle(
        initialValue = emptySet(),
    )
    val notifyAllPreference by notifyAllFlow.collectAsStateWithLifecycle(initialValue = null)
    val uiState = state.uiState(
        materias = materias,
        persistedSubscriptionIds = persistedSubscriptionIds,
        notifyAllPreference = notifyAllPreference,
    )

    LaunchedEffect(state.loading) {
        currentOnHeaderActionsChange.value(
            listOf(
                HeaderAction(
                    icon = Icons.Default.Refresh,
                    contentDescription = "Refrescar materias",
                    enabled = !state.loading,
                    onClick = state::refreshMaterias,
                )
            )
        )
    }

    DisposableEffect(Unit) {
        onDispose { currentOnHeaderActionsChange.value(emptyList()) }
    }

    LaunchedEffect(Unit) {
        state.loadMateriasCatalog(forceRefresh = false)
    }

    LaunchedEffect(persistedSubscriptionIds) {
        state.reconcilePersistedSubscriptions(persistedSubscriptionIds)
    }

    SubscripcionesContent(
        state = uiState,
        onExpandedChange = { expanded ->
            state.setExpanded(expanded, enabled = uiState.enabled)
        },
        onDismissSelector = state::dismissSelector,
        onSearchQueryChange = { query ->
            state.updateSearchQuery(query, enabled = uiState.enabled)
        },
        onSubscribe = { materia ->
            state.subscribe(materia.id, enabled = uiState.enabled)
        },
        onUnsubscribe = { subscription ->
            state.unsubscribe(subscription.id, enabled = uiState.enabled)
        },
    )
}
