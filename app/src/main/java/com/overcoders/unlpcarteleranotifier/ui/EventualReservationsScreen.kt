/** Muestra reservas eventuales paginadas, con filtros remotos y fallbacks de caché. */
package com.overcoders.unlpcarteleranotifier.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.ui.reservas.EventualReservationsContent
import com.overcoders.unlpcarteleranotifier.ui.reservas.rememberEventualReservationsState

@Composable
fun EventualReservationsScreen(
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {}
) {
    val state = rememberEventualReservationsState()
    val listState = rememberLazyListState()
    val currentOnHeaderActionsChange = rememberUpdatedState(onHeaderActionsChange)

    LaunchedEffect(Unit) {
        state.loadFilterOptions()
    }

    LaunchedEffect(state.selectedClassroomId, state.selectedSubjectId) {
        if (
            state.paginationRestorationFilterKey != state.activePaginationFilterKey &&
            listState.layoutInfo.totalItemsCount > 0
        ) {
            listState.scrollToItem(0)
        }
        state.loadPage(reset = true)
    }

    LaunchedEffect(listState, state.listPositionRestorationPending) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, scrollOffset) ->
            state.recordListPosition(index, scrollOffset)
        }
    }

    LaunchedEffect(
        state.canContinueListRestoration,
        state.listPositionRestorationPending,
        state.lastAppliedPage,
        state.paginationRestorationTargetPage,
        state.nextPage,
        state.loadingInitial,
        state.loadingMore,
        state.initialError,
        state.refreshWarning,
        state.paginationError,
        state.activePaginationFilterKey,
    ) {
        if (state.canContinueListRestoration) {
            state.loadPage(reset = false)
            return@LaunchedEffect
        }

        state.listPositionToRestore()?.let { position ->
            listState.scrollToItem(
                index = position.index,
                scrollOffset = position.scrollOffset,
            )
            state.completeListPositionRestoration()
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleIndex =
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalItems = listState.layoutInfo.totalItemsCount
            state.hasReservations &&
                totalItems > 0 &&
                lastVisibleIndex >= totalItems - 3
        }
    }

    LaunchedEffect(
        shouldLoadMore,
        state.nextPage,
        state.loadingInitial,
        state.loadingMore,
        state.paginationError,
        state.initialError,
        state.refreshWarning,
        state.listPositionRestorationPending,
    ) {
        if (state.shouldLoadNextPage(shouldLoadMore)) {
            state.loadPage(reset = false)
        }
    }

    LaunchedEffect(
        state.refreshEnabled,
        state.selectedClassroomId,
        state.selectedSubjectId,
    ) {
        currentOnHeaderActionsChange.value(
            listOf(
                HeaderAction(
                    icon = Icons.Default.Refresh,
                    enabled = state.refreshEnabled,
                    contentDescription = "Refrescar reservas eventuales",
                    onClick = state::retryInitialLoad,
                )
            )
        )
    }

    DisposableEffect(state) {
        onDispose {
            state.dispose()
            currentOnHeaderActionsChange.value(emptyList())
        }
    }

    EventualReservationsContent(
        uiState = state.uiState,
        listState = listState,
        onClassroomQueryChange = state::updateClassroomQuery,
        onClassroomExpandedChange = state::setClassroomExpanded,
        onClassroomSelected = state::selectClassroom,
        onSubjectQueryChange = state::updateSubjectQuery,
        onSubjectExpandedChange = state::setSubjectExpanded,
        onSubjectSelected = state::selectSubject,
        onClearFilters = state::clearFilters,
    )
}
