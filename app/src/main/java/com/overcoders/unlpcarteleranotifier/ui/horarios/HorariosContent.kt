/** Presenta el selector y las reservas por día sin administrar red, caché ni efectos externos. */
package com.overcoders.unlpcarteleranotifier.ui.horarios

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.overcoders.unlpcarteleranotifier.model.HorarioMateria
import com.overcoders.unlpcarteleranotifier.model.HorarioReserva
import com.overcoders.unlpcarteleranotifier.model.MateriaCatalogItem
import com.overcoders.unlpcarteleranotifier.ui.ScrollMoreHint
import com.overcoders.unlpcarteleranotifier.ui.common.ContentLoadingPhase
import com.overcoders.unlpcarteleranotifier.ui.common.LoadingContentBox

internal data class HorariosContentState(
    val materias: List<MateriaCatalogItem>,
    val filteredMaterias: List<MateriaCatalogItem>,
    val selectedMateria: MateriaCatalogItem?,
    val materiaQuery: String,
    val dropdownExpanded: Boolean,
    val loadingMaterias: Boolean,
    val materiasError: String?,
    val visibleIssue: HorariosIssue?,
    val loadingPhase: ContentLoadingPhase,
    val horarioMateria: HorarioMateria?,
    val expandedDaysMask: Int,
)

internal data class HorarioDayReservations(
    val number: Int,
    val name: String,
    val reservations: List<HorarioReserva>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HorariosContent(
    state: HorariosContentState,
    listState: LazyListState,
    onQueryChange: (String) -> Unit,
    onDropdownExpandedChange: (Boolean) -> Unit,
    onDropdownDismiss: () -> Unit,
    onMateriaSelected: (MateriaCatalogItem) -> Unit,
    onClearSelection: () -> Unit,
    onToggleDay: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = state.dropdownExpanded,
            onExpandedChange = onDropdownExpandedChange,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = state.materiaQuery,
                onValueChange = onQueryChange,
                label = { Text("Seleccionar materia") },
                placeholder = { Text("Buscar materia...") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = state.dropdownExpanded
                    )
                },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true)
                    .fillMaxWidth(),
            )

            ExposedDropdownMenu(
                expanded = state.dropdownExpanded,
                onDismissRequest = onDropdownDismiss,
            ) {
                when {
                    state.loadingMaterias && state.materias.isEmpty() -> DropdownMenuItem(
                        text = { Text("Cargando materias…") },
                        onClick = {},
                        enabled = false,
                    )

                    state.materiasError != null && state.materias.isEmpty() -> DropdownMenuItem(
                        text = { Text("Catálogo no disponible") },
                        onClick = {},
                        enabled = false,
                    )

                    state.filteredMaterias.isEmpty() -> DropdownMenuItem(
                        text = { Text("Sin resultados") },
                        onClick = {},
                        enabled = false,
                    )

                    else -> state.filteredMaterias.forEach { materia ->
                        DropdownMenuItem(
                            text = { Text(materia.nombre) },
                            onClick = { onMateriaSelected(materia) },
                        )
                    }
                }
            }
        }

        if (state.selectedMateria != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onClearSelection) {
                Text("Quitar filtro")
            }
        }

        Spacer(Modifier.height(8.dp))

        state.visibleIssue?.let { issue ->
            Text(
                text = issue.message,
                style = MaterialTheme.typography.bodySmall,
                color = if (issue.isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.height(8.dp))
        }

        LoadingContentBox(
            phase = state.loadingPhase,
            initialText = if (state.selectedMateria == null) {
                "Cargando materias…"
            } else {
                "Cargando reservas de aulas…"
            },
            refreshContentDescription = if (state.selectedMateria == null) {
                "Actualizando materias"
            } else {
                "Actualizando reservas de aulas"
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Column(Modifier.fillMaxSize()) {
                if (state.selectedMateria == null && state.materiasError == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Seleccione una materia para ver sus reservas de aulas",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                state.horarioMateria?.let { horarios ->
                    val days = visibleHorarioDays(horarios)

                    Text(
                        text = horarios.periodo.nombre.ifBlank { "Sin nombre de período" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${horarios.periodo.desde} - ${horarios.periodo.hasta}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(Modifier.height(12.dp))

                    if (days.isEmpty()) {
                        Text(
                            text = "No hay ninguna reserva.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        return@let
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(days, key = HorarioDayReservations::number) { day ->
                                val dayBit = 1 shl day.number
                                HorarioDiaCard(
                                    diaNombre = day.name,
                                    reservasDelDia = day.reservations,
                                    isExpanded = state.expandedDaysMask and dayBit != 0,
                                    onToggle = { onToggleDay(day.number) },
                                )
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
}

internal fun visibleHorarioDays(horarioMateria: HorarioMateria): List<HorarioDayReservations> =
    diasHabiles.mapNotNull { (dayNumber, dayName) ->
        val reservations = horarioMateria.reservas
            .filter { it.dia == dayNumber }
            .sortedWith(
                compareBy(
                    HorarioReserva::horaInicio,
                    HorarioReserva::horaFin,
                    HorarioReserva::aula,
                )
            )
        reservations.takeIf { it.isNotEmpty() }?.let {
            HorarioDayReservations(
                number = dayNumber,
                name = dayName,
                reservations = reservations,
            )
        }
    }
