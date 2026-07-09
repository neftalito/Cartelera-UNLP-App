package com.overcoders.unlpcarteleranotifier.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.data.HorariosService
import com.overcoders.unlpcarteleranotifier.data.MateriasService
import com.overcoders.unlpcarteleranotifier.data.MateriasStore
import com.overcoders.unlpcarteleranotifier.model.HorarioMateria
import com.overcoders.unlpcarteleranotifier.model.HorarioReserva
import com.overcoders.unlpcarteleranotifier.model.MateriaCatalogItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HorariosScreen(
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val materiasService = remember { MateriasService() }
    val horariosService = remember { HorariosService() }
    val expandedDays = remember { mutableStateMapOf<Int, Boolean>() }

    var loadingMaterias by remember { mutableStateOf(false) }
    var loadingHorarios by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var materias by remember { mutableStateOf<List<MateriaCatalogItem>>(emptyList()) }
    var selectedMateria by remember { mutableStateOf<MateriaCatalogItem?>(null) }
    var materiaQuery by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var horarioMateria by remember { mutableStateOf<HorarioMateria?>(null) }
    var horariosJob by remember { mutableStateOf<Job?>(null) }
    val horariosListState = rememberLazyListState()

    val materiasFiltradas = remember(materias, materiaQuery) {
        val query = materiaQuery.trim().lowercase()
        if (query.isBlank()) materias
        else materias.filter { it.nombre.lowercase().contains(query) }
    }
    val canShareHorarios =
        selectedMateria != null && !loadingHorarios && horarioMateria?.reservas?.isNotEmpty() == true

    fun cancelHorariosLoad() {
        val activeJob = horariosJob
        horariosJob = null
        activeJob?.cancel()
        loadingHorarios = false
    }

    fun resetHorariosState() {
        cancelHorariosLoad()
        horarioMateria = null
        error = null
        expandedDays.clear()
    }

    fun loadHorarios() {
        val materia = selectedMateria ?: return
        val id = materia.id.toIntOrNull() ?: return

        val previousJob = horariosJob
        horariosJob = null
        previousJob?.cancel()
        horariosJob = scope.launch {
            val currentJob = coroutineContext[Job]
            loadingHorarios = true
            error = null
            try {
                val fetchedHorarios = horariosService.fetch(id, materia.nombre)
                if (selectedMateria?.id != materia.id) return@launch
                horarioMateria = fetchedHorarios
                expandedDays.clear()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (selectedMateria?.id != materia.id) return@launch
                horarioMateria = null
                error = when (e) {
                    is IOException -> "Error de red al obtener horarios."
                    else -> "No se pudieron obtener los horarios."
                }
            } finally {
                if (horariosJob === currentJob) {
                    loadingHorarios = false
                    horariosJob = null
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadingMaterias = true
        try {
            val cached = withContext(Dispatchers.IO) { MateriasStore.load(context) }
            materias = cached
            if (cached.isEmpty()) {
                materias = withContext(Dispatchers.IO) { materiasService.refresh(context) }
            }
        } catch (_: Exception) {
            error = "No se pudieron cargar las materias."
        } finally {
            loadingMaterias = false
        }
    }

    LaunchedEffect(selectedMateria?.id) {
        if (selectedMateria != null) {
            loadHorarios()
        }
    }

    LaunchedEffect(canShareHorarios, loadingHorarios, selectedMateria, horarioMateria) {
        onHeaderActionsChange(
            listOf(
                HeaderAction(
                    icon = Icons.Default.ContentCopy,
                    contentDescription = "Copiar reservas de aulas",
                    enabled = canShareHorarios,
                    onClick = copyClick@{
                        val horarios = horarioMateria ?: return@copyClick
                        copyPlainText(
                            context = context,
                            label = "Reservas de aulas",
                            text = buildShareText(horarios)
                        )
                    }
                ),
                HeaderAction(
                    icon = Icons.Default.Share,
                    contentDescription = "Compartir reservas de aulas",
                    enabled = canShareHorarios,
                    onClick = shareClick@{
                        val horarios = horarioMateria ?: return@shareClick
                        sharePlainText(
                            context = context,
                            text = buildShareText(horarios),
                            chooserTitle = "Compartir reservas de aulas"
                        )
                    }
                ),
                HeaderAction(
                    icon = Icons.Default.Refresh,
                    contentDescription = "Refrescar horarios",
                    enabled = selectedMateria != null && !loadingHorarios,
                    onClick = { loadHorarios() }
                )
            )
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            cancelHorariosLoad()
            onHeaderActionsChange(emptyList())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = dropdownExpanded,
            onExpandedChange = { isExpanded ->
                if (isExpanded && selectedMateria != null) {
                    selectedMateria = null
                    materiaQuery = ""
                    resetHorariosState()
                }
                dropdownExpanded = isExpanded
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = materiaQuery,
                onValueChange = {
                    materiaQuery = it
                    if (selectedMateria?.nombre?.trim() != it.trim()) {
                        selectedMateria = null
                        resetHorariosState()
                    }
                },
                label = { Text("Seleccionar materia") },
                placeholder = { Text("Buscar materia...") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false }
            ) {
                if (loadingMaterias && materias.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Cargando materias...") },
                        onClick = {},
                        enabled = false
                    )
                } else if (materiasFiltradas.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Sin resultados") },
                        onClick = {},
                        enabled = false
                    )
                } else {
                    materiasFiltradas.forEach { materia ->
                        DropdownMenuItem(
                            text = { Text(materia.nombre) },
                            onClick = {
                                selectedMateria = materia
                                materiaQuery = materia.nombre
                                dropdownExpanded = false
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                            }
                        )
                    }
                }
            }
        }

        if (selectedMateria != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    selectedMateria = null
                    materiaQuery = ""
                    dropdownExpanded = false
                    resetHorariosState()
                }
            ) {
                Text("Quitar filtro")
            }
        }

        Spacer(Modifier.height(8.dp))

        if (loadingHorarios) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            Spacer(Modifier.height(8.dp))
        }

        if (error != null) {
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        if (selectedMateria == null && !loadingHorarios) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Seleccione una materia para ver sus reservas de aulas",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        horarioMateria?.let { horarios ->
            val diasConReservas = diasHabiles.filter { (diaNumero, _) ->
                horarios.reservas.any { it.dia == diaNumero }
            }

            Text(
                text = horarios.periodo.nombre.ifBlank { "Sin nombre de período" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${horarios.periodo.desde} - ${horarios.periodo.hasta}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(12.dp))

            if (diasConReservas.isEmpty()) {
                Text(
                    text = "No hay ninguna reserva.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                return@let
            }

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = horariosListState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 36.dp)
                ) {
                    items(diasConReservas, key = { it.first }) { (diaNumero, diaNombre) ->
                        val reservasDelDia = horarios.reservas
                            .filter { it.dia == diaNumero }
                            .sortedWith(compareBy(HorarioReserva::horaInicio, HorarioReserva::horaFin, HorarioReserva::aula))
                        val isExpanded = expandedDays[diaNumero] == true

                        HorarioDiaCard(
                            diaNombre = diaNombre,
                            reservasDelDia = reservasDelDia,
                            isExpanded = isExpanded,
                            onToggle = { expandedDays[diaNumero] = !isExpanded }
                        )
                    }
                }

                ScrollMoreHint(
                    listState = horariosListState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                )
            }
        }
    }
}
