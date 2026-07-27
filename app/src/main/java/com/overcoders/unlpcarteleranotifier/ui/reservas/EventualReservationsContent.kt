/** Renderiza filtros, estados y resultados de reservas eventuales. */
package com.overcoders.unlpcarteleranotifier.ui.reservas

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.overcoders.unlpcarteleranotifier.model.EventualReservation
import com.overcoders.unlpcarteleranotifier.model.EventualReservationFilterOption
import com.overcoders.unlpcarteleranotifier.ui.ScrollMoreHint
import com.overcoders.unlpcarteleranotifier.ui.common.LoadingContentBox
import com.overcoders.unlpcarteleranotifier.ui.common.PaginationLoadingIndicator

@Composable
internal fun EventualReservationsContent(
    uiState: EventualReservationsUiState,
    listState: LazyListState,
    onClassroomQueryChange: (String) -> Unit,
    onClassroomExpandedChange: (Boolean) -> Unit,
    onClassroomSelected: (EventualReservationFilterOption) -> Unit,
    onSubjectQueryChange: (String) -> Unit,
    onSubjectExpandedChange: (Boolean) -> Unit,
    onSubjectSelected: (EventualReservationFilterOption) -> Unit,
    onClearFilters: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        ReservationFilterField(
            label = "Aula",
            placeholder = "Buscar aula...",
            state = uiState.classroomFilter,
            onQueryChange = onClassroomQueryChange,
            onExpandedChange = onClassroomExpandedChange,
            onOptionSelected = { option ->
                onClassroomSelected(option)
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            },
        )

        Spacer(Modifier.height(8.dp))

        ReservationFilterField(
            label = "Materia",
            placeholder = "Buscar materia...",
            state = uiState.subjectFilter,
            onQueryChange = onSubjectQueryChange,
            onExpandedChange = onSubjectExpandedChange,
            onOptionSelected = { option ->
                onSubjectSelected(option)
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            },
        )

        if (uiState.showClearFilters) {
            TextButton(
                onClick = onClearFilters,
                enabled = uiState.clearFiltersEnabled,
            ) {
                Text("Quitar filtros")
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }

        uiState.visibleInitialWarning?.let { warning ->
            Text(
                text = warning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }

        uiState.totalCount?.let { totalCount ->
            Text(
                text = if (totalCount == 1) {
                    "1 reserva eventual"
                } else {
                    "$totalCount reservas eventuales"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }

        LoadingContentBox(
            phase = uiState.loadingState.phase,
            initialText = "Cargando reservas eventuales…",
            refreshContentDescription = "Actualizando reservas eventuales",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                uiState.initialFailureMessage != null -> EventualReservationsMessage(
                    body = uiState.initialFailureMessage,
                    isError = true,
                )

                uiState.reservations.isEmpty() -> EventualReservationsMessage(
                    title = "Sin reservas",
                    body = "No se encontraron reservas eventuales con los filtros seleccionados.",
                )

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(uiState.reservations, key = EventualReservation::id) { reservation ->
                            EventualReservationCard(reservation)
                        }

                        item(key = "pagination") {
                            when {
                                uiState.loadingState.showPaginationIndicator ->
                                    PaginationLoadingIndicator(
                                        text = "Cargando más reservas eventuales…",
                                    )

                                uiState.paginationErrorMessage != null ->
                                    EventualReservationsMessage(
                                        body = uiState.paginationErrorMessage,
                                        isError = true,
                                    )

                                !uiState.hasNextPage -> Text(
                                    text = "No hay más reservas para mostrar.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                )
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReservationFilterField(
    label: String,
    placeholder: String,
    state: EventualReservationFilterUiState,
    onQueryChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onOptionSelected: (EventualReservationFilterOption) -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = state.expanded,
        onExpandedChange = { if (state.enabled) onExpandedChange(it) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { if (state.enabled) onQueryChange(it) },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = state.expanded)
            },
            modifier = Modifier
                .menuAnchor(
                    ExposedDropdownMenuAnchorType.PrimaryEditable,
                    enabled = state.enabled,
                )
                .fillMaxWidth(),
            enabled = state.enabled,
        )

        ExposedDropdownMenu(
            expanded = state.expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.heightIn(max = 320.dp),
        ) {
            when {
                state.loading && state.options.isEmpty() -> DropdownMenuItem(
                    text = { Text("Cargando opciones…") },
                    onClick = {},
                    enabled = false,
                )

                state.unavailable -> DropdownMenuItem(
                    text = { Text("Opciones no disponibles") },
                    onClick = {},
                    enabled = false,
                )

                state.options.isEmpty() -> DropdownMenuItem(
                    text = { Text("Sin resultados") },
                    onClick = {},
                    enabled = false,
                )

                else -> {
                    state.options.take(MAX_VISIBLE_FILTER_OPTIONS).forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = { onOptionSelected(option) },
                        )
                    }
                    if (
                        state.options.size > MAX_VISIBLE_FILTER_OPTIONS &&
                        state.selectedOption == null
                    ) {
                        DropdownMenuItem(
                            text = { Text("Escribí más para refinar la búsqueda") },
                            onClick = {},
                            enabled = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EventualReservationCard(reservation: EventualReservation) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = reservation.classroomName.ifBlank { "Aula sin informar" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = reservation.subjectName.ifBlank { "Materia sin informar" },
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = reservation.date.ifBlank { "Fecha sin informar" },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = reservation.timeRange,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (reservation.reason.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Motivo: ${reservation.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (reservation.teacherName.isNotBlank()) {
                Text(
                    text = "Docente: ${reservation.teacherName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EventualReservationsMessage(
    title: String? = null,
    body: String,
    isError: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
        )
    }
}

private val EventualReservation.timeRange: String
    get() = when {
        startTime.isNotBlank() && endTime.isNotBlank() -> "$startTime a $endTime"
        startTime.isNotBlank() -> "Desde $startTime"
        endTime.isNotBlank() -> "Hasta $endTime"
        else -> "Horario sin informar"
    }

private const val MAX_VISIBLE_FILTER_OPTIONS = 50
