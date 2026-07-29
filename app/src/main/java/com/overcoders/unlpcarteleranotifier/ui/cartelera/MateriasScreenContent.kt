/** Presenta el selector y el listado de anuncios sin administrar cargas ni efectos externos. */
package com.overcoders.unlpcarteleranotifier.ui.cartelera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.overcoders.unlpcarteleranotifier.model.MateriaCatalogItem
import com.overcoders.unlpcarteleranotifier.model.Mensaje
import com.overcoders.unlpcarteleranotifier.model.toMateriaCatalogIdOrNull
import com.overcoders.unlpcarteleranotifier.ui.ScrollMoreHint
import com.overcoders.unlpcarteleranotifier.ui.common.LoadingContentBox
import com.overcoders.unlpcarteleranotifier.ui.common.PaginationLoadingIndicator
import com.overcoders.unlpcarteleranotifier.ui.common.normalizeForSearch
import com.overcoders.unlpcarteleranotifier.ui.common.paginatedLoadingState

internal data class MateriasScreenContentState(
    val materias: List<MateriaCatalogItem>,
    val materiasLoading: Boolean,
    val materiasError: String?,
    val materiasWarning: String?,
    val announcementsForDisplay: List<Mensaje>,
    val newAnnouncementIdentities: Set<AnuncioIdentity>,
    val hideCancelledMessages: Boolean,
    val loadingInitial: Boolean,
    val loadingMore: Boolean,
    val error: String?,
    val notificationOpenError: String?,
    val warning: String?,
    val loadedFilterKey: String?,
    val hasMore: Boolean,
)

internal data class MateriasFeedback(
    val message: String,
    val isWarning: Boolean,
)

internal fun materiasFeedback(state: MateriasScreenContentState): MateriasFeedback? {
    val message = state.error
        ?: state.notificationOpenError
        ?: state.materiasError
        ?: state.warning
        ?: state.materiasWarning
        ?: return null
    return MateriasFeedback(
        message = message,
        isWarning = state.error == null &&
            state.notificationOpenError == null &&
            state.materiasError == null,
    )
}

internal fun visibleAnnouncements(
    announcements: List<Mensaje>,
    hideCancelledMessages: Boolean,
): List<Mensaje> = if (hideCancelledMessages) {
    announcements.filterNot { it.isAnulado }
} else {
    announcements
}

internal fun materiasEmptyStateMessage(
    hasLoadedCurrentFilter: Boolean,
    loadingMore: Boolean,
    hasMore: Boolean,
    hasError: Boolean,
    visibleAnnouncementCount: Int,
    announcementCount: Int,
    hideCancelledMessages: Boolean,
    hasSelectedMateria: Boolean,
): String? = when {
    !hasLoadedCurrentFilter || loadingMore || hasMore || hasError -> null
    visibleAnnouncementCount > 0 -> null
    announcementCount > 0 && hideCancelledMessages ->
        "No hay anuncios visibles con la configuración actual."
    hasSelectedMateria -> "No hay anuncios disponibles para la materia seleccionada."
    else -> "No hay anuncios disponibles."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MateriasScreenContent(
    state: MateriasScreenContentState,
    filterState: MateriasFilterState,
    listState: LazyListState,
    onAnnouncementClick: (Mensaje, Int) -> Unit,
    onLoadMore: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val filteredMaterias = remember(state.materias, filterState.query) {
        val query = filterState.query.normalizeForSearch()
        if (query.isBlank()) {
            state.materias
        } else {
            state.materias.filter { materia ->
                materia.nombre.normalizeForSearch().contains(query)
            }
        }
    }
    val announcements = remember(
        state.announcementsForDisplay,
        state.hideCancelledMessages,
    ) {
        visibleAnnouncements(
            announcements = state.announcementsForDisplay,
            hideCancelledMessages = state.hideCancelledMessages,
        )
    }
    val currentFilterKey = filterState.selected?.id
        ?.toMateriaCatalogIdOrNull()
        ?.toString()
        ?: "__all__"
    val hasLoadedCurrentFilter = state.loadedFilterKey == currentFilterKey
    val loadingState = paginatedLoadingState(
        isContentLoading = state.loadingInitial,
        isLoadingMore = state.loadingMore,
        resolvedKey = state.loadedFilterKey,
        requestedKey = currentFilterKey,
        hasError = state.error != null,
    )
    val emptyStateMessage = materiasEmptyStateMessage(
        hasLoadedCurrentFilter = hasLoadedCurrentFilter,
        loadingMore = state.loadingMore,
        hasMore = state.hasMore,
        hasError = state.error != null,
        visibleAnnouncementCount = announcements.size,
        announcementCount = state.announcementsForDisplay.size,
        hideCancelledMessages = state.hideCancelledMessages,
        hasSelectedMateria = filterState.selected != null,
    )
    val canLoadMore = state.hasMore &&
        hasLoadedCurrentFilter &&
        !state.loadingInitial &&
        state.error == null &&
        state.warning == null

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = filterState.expanded,
            onExpandedChange = { isExpanded ->
                if (isExpanded) {
                    filterState.beginEditing()
                } else {
                    filterState.dismissEditing()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = filterState.query,
                onValueChange = { filterState.query = it },
                label = { Text("Filtrar por materia") },
                placeholder = { Text("Buscar materia...") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterState.expanded)
                },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = filterState.expanded,
                onDismissRequest = { filterState.dismissEditing() }
            ) {
                if (state.materiasLoading && state.materias.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Cargando materias…") },
                        onClick = { filterState.expanded = false },
                        enabled = false
                    )
                } else if (state.materiasError != null && state.materias.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Catálogo no disponible") },
                        onClick = { filterState.expanded = false },
                        enabled = false,
                    )
                } else if (filteredMaterias.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Sin resultados") },
                        onClick = { filterState.expanded = false },
                        enabled = false
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        filteredMaterias.forEach { materia ->
                            DropdownMenuItem(
                                text = { Text(materia.nombre) },
                                onClick = {
                                    filterState.select(materia)
                                    focusManager.clearFocus(force = true)
                                    keyboardController?.hide()
                                }
                            )
                        }
                    }
                }
            }
        }

        if (filterState.selected != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = filterState::clearSelection) {
                Text("Quitar filtro")
            }
        }

        Spacer(Modifier.height(8.dp))

        materiasFeedback(state)?.let { feedback ->
            Text(
                feedback.message,
                color = if (feedback.isWarning) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
        }

        LoadingContentBox(
            phase = loadingState.phase,
            initialText = "Cargando anuncios…",
            refreshContentDescription = "Actualizando anuncios",
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (emptyStateMessage != null) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = emptyStateMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                items(announcements, key = Mensaje::anuncioSaveableKey) { announcement ->
                    AnuncioCard(
                        anuncio = announcement,
                        isNew = filterState.selected == null &&
                            announcement.anuncioIdentity() in state.newAnnouncementIdentities,
                        onClick = {
                            val absoluteIndex = state.announcementsForDisplay.indexOfFirst {
                                it.anuncioSaveableKey() == announcement.anuncioSaveableKey()
                            }.coerceAtLeast(0)
                            onAnnouncementClick(announcement, absoluteIndex)
                        }
                    )
                }

                if (state.loadingMore || canLoadMore) {
                    item(key = "pagination") {
                        Spacer(Modifier.height(8.dp))

                        if (loadingState.showPaginationIndicator) {
                            PaginationLoadingIndicator(text = "Cargando más anuncios…")
                        } else {
                            Button(
                                onClick = onLoadMore,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.loadingInitial && !state.loadingMore
                            ) {
                                Text("Cargar más")
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }

            ScrollMoreHint(
                listState = listState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
            )
        }
    }
}
