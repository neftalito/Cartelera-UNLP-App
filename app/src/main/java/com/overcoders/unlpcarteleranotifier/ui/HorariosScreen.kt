/**
 * Permite seleccionar una materia, consultar sus reservas de aulas y reutilizar datos cacheados.
 */
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.data.ContentCachePolicy
import com.overcoders.unlpcarteleranotifier.data.HorariosCacheStore
import com.overcoders.unlpcarteleranotifier.data.HorariosService
import com.overcoders.unlpcarteleranotifier.data.MateriasRepository
import com.overcoders.unlpcarteleranotifier.model.HorarioMateria
import com.overcoders.unlpcarteleranotifier.model.HorarioReserva
import com.overcoders.unlpcarteleranotifier.model.MateriaCatalogItem
import com.overcoders.unlpcarteleranotifier.model.toMateriaCatalogIdOrNull
import com.overcoders.unlpcarteleranotifier.ui.common.LoadingContentBox
import com.overcoders.unlpcarteleranotifier.ui.common.cachedContentWarning
import com.overcoders.unlpcarteleranotifier.ui.common.resolvedContentLoadingPhase
import com.overcoders.unlpcarteleranotifier.ui.common.copyPlainText
import com.overcoders.unlpcarteleranotifier.ui.common.normalizeForSearch
import com.overcoders.unlpcarteleranotifier.ui.common.sharePlainText
import com.overcoders.unlpcarteleranotifier.ui.common.userFacingError
import com.overcoders.unlpcarteleranotifier.ui.horarios.HorarioDiaCard
import com.overcoders.unlpcarteleranotifier.ui.horarios.buildShareText
import com.overcoders.unlpcarteleranotifier.ui.horarios.diasHabiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HorariosScreen(
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val materiasRepository = remember(context) { MateriasRepository.get(context) }
    val horariosService = remember { HorariosService() }
    var expandedDaysMask by rememberSaveable { mutableIntStateOf(0) }

    var loadingMaterias by remember { mutableStateOf(false) }
    var loadingHorarios by remember { mutableStateOf(false) }
    var materiasError by remember { mutableStateOf<String?>(null) }
    var materiasWarning by remember { mutableStateOf<String?>(null) }
    var horariosError by remember { mutableStateOf<String?>(null) }
    var horariosWarning by remember { mutableStateOf<String?>(null) }

    val materias by materiasRepository.items.collectAsStateWithLifecycle()
    var materiasSnapshotResolved by remember {
        mutableStateOf(materiasRepository.items.value.isNotEmpty())
    }
    var selectedMateriaId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMateriaName by rememberSaveable { mutableStateOf("") }
    var lastDisplayedMateriaId by rememberSaveable { mutableStateOf(selectedMateriaId) }
    var materiaQuery by remember(selectedMateriaId, selectedMateriaName) {
        mutableStateOf(selectedMateriaName.takeIf { selectedMateriaId != null }.orEmpty())
    }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var horarioMateria by remember { mutableStateOf<HorarioMateria?>(null) }
    var resolvedHorarioMateriaId by remember { mutableStateOf<String?>(null) }
    var horariosJob by remember { mutableStateOf<Job?>(null) }
    val horariosListState = rememberLazyListState()
    val selectedMateria = remember(materias, selectedMateriaId, selectedMateriaName) {
        selectedMateriaId?.let { id ->
            materias.firstOrNull { it.id == id }
                ?: MateriaCatalogItem(id = id, nombre = selectedMateriaName)
        }
    }


    LaunchedEffect(selectedMateria?.id, selectedMateria?.nombre, dropdownExpanded) {
        val current = selectedMateria ?: return@LaunchedEffect
        if (!dropdownExpanded && selectedMateriaName != current.nombre) {
            selectedMateriaName = current.nombre
            materiaQuery = current.nombre
        }
        if (horarioMateria?.materiaNombre != current.nombre) {
            horarioMateria = horarioMateria?.withCurrentMateriaName(current.nombre)
        }
    }

    val materiasFiltradas = remember(materias, materiaQuery) {
        val query = materiaQuery.normalizeForSearch()
        if (query.isBlank()) materias
        else materias.filter { it.nombre.normalizeForSearch().contains(query) }
    }
    val canShareHorarios =
        selectedMateria != null && !loadingHorarios && horarioMateria?.reservas?.isNotEmpty() == true
    val visibleIssueMessage =
        materiasError ?: horariosError ?: horariosWarning ?: materiasWarning
    val visibleIssueIsError = materiasError != null || horariosError != null
    val visibleIssueBelongsToCatalog = materiasError != null || (
        horariosError == null && horariosWarning == null && materiasWarning != null
    )
    val visibleIssueCanRetry = visibleIssueBelongsToCatalog ||
        selectedMateria?.id?.toMateriaCatalogIdOrNull() != null
    val horariosSnapshotResolved =
        selectedMateria != null && resolvedHorarioMateriaId == selectedMateria.id
    val loadingPhase = resolvedContentLoadingPhase(
        isLoading = loadingMaterias || loadingHorarios,
        hasResolvedContent = if (selectedMateria == null) {
            materiasSnapshotResolved
        } else {
            horariosSnapshotResolved
        },
        hasError = if (selectedMateria == null) {
            materiasError != null
        } else {
            horariosError != null
        },
    )

    fun cancelHorariosLoad() {
        val activeJob = horariosJob
        horariosJob = null
        activeJob?.cancel()
        loadingHorarios = false
    }

    fun resetHorariosState() {
        cancelHorariosLoad()
        horarioMateria = null
        resolvedHorarioMateriaId = null
        horariosError = null
        horariosWarning = null
        expandedDaysMask = 0
    }

    suspend fun loadMaterias(forceRefresh: Boolean = false) {
        if (loadingMaterias) return

        loadingMaterias = true
        materiasError = null
        materiasWarning = null
        try {
            val result = materiasRepository.load(forceRefresh)
            val failure = result.refreshFailure
            if (result.items.isNotEmpty() || failure == null) {
                materiasSnapshotResolved = true
            }
            if (failure != null && result.items.isNotEmpty()) {
                materiasWarning = cachedContentWarning(
                    operation = "actualizar el catálogo de materias",
                    error = failure,
                )
            } else if (failure != null) {
                materiasError = userFacingError(
                    operation = "cargar el catálogo de materias",
                    error = failure,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            loadingMaterias = false
        }
    }

    fun loadHorarios(forceRefresh: Boolean = false) {
        val materia = selectedMateria ?: return
        val id = materia.id.toMateriaCatalogIdOrNull()
        if (id == null) {
            cancelHorariosLoad()
            horarioMateria = null
            resolvedHorarioMateriaId = null
            horariosWarning = null
            horariosError = userFacingError(
                operation = "cargar las reservas de aulas",
                detail = "El identificador de materia '${materia.id}' no es numérico.",
            )
            return
        }

        val previousJob = horariosJob
        horariosJob = null
        previousJob?.cancel()
        horariosJob = scope.launch {
            val currentJob = coroutineContext[Job]
            loadingHorarios = true
            horariosError = null
            horariosWarning = null
            try {
                val cached = HorariosCacheStore.load(context, id)
                if (cached != null) {
                    if (selectedMateriaId != materia.id) return@launch
                    horarioMateria = cached.value.withCurrentMateriaName(materia.nombre)
                    resolvedHorarioMateriaId = materia.id
                    if (!forceRefresh && cached.isFresh(ContentCachePolicy.SCHEDULE_TTL_MILLIS)) {
                        return@launch
                    }
                }

                val fetchedHorarios = horariosService.fetch(id, materia.nombre)
                if (selectedMateriaId != materia.id) return@launch
                horarioMateria = fetchedHorarios
                resolvedHorarioMateriaId = materia.id
                try {
                    HorariosCacheStore.save(context, id, fetchedHorarios)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Los horarios remotos siguen siendo utilizables aunque no puedan persistirse.
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (selectedMateriaId != materia.id) return@launch
                if (horarioMateria != null) {
                    horariosWarning = cachedContentWarning(
                        operation = "actualizar las reservas de aulas",
                        error = e
                    )
                } else {
                    horarioMateria = null
                    horariosError = userFacingError(
                        operation = "cargar las reservas de aulas",
                        error = e
                    )
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
        loadMaterias()
    }

    LaunchedEffect(selectedMateria?.id) {
        if (lastDisplayedMateriaId != selectedMateria?.id) {
            resolvedHorarioMateriaId = null
            if (horariosListState.layoutInfo.totalItemsCount > 0) {
                horariosListState.scrollToItem(0)
            }
            lastDisplayedMateriaId = selectedMateria?.id
        }
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
                    enabled = selectedMateria?.id?.toMateriaCatalogIdOrNull() != null &&
                        !loadingHorarios,
                    onClick = { loadHorarios(forceRefresh = true) }
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
                if (isExpanded) {
                    materiaQuery = ""
                } else {
                    materiaQuery = selectedMateria?.nombre.orEmpty()
                }
                dropdownExpanded = isExpanded
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = materiaQuery,
                onValueChange = { materiaQuery = it },
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
                onDismissRequest = {
                    materiaQuery = selectedMateria?.nombre.orEmpty()
                    dropdownExpanded = false
                }
            ) {
                if (loadingMaterias && materias.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Cargando materias…") },
                        onClick = {},
                        enabled = false
                    )
                } else if (materiasError != null && materias.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Catálogo no disponible") },
                        onClick = {},
                        enabled = false,
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
                                if (selectedMateriaId != materia.id) {
                                    resetHorariosState()
                                }
                                selectedMateriaId = materia.id
                                selectedMateriaName = materia.nombre
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
                    selectedMateriaId = null
                    selectedMateriaName = ""
                    materiaQuery = ""
                    dropdownExpanded = false
                    resetHorariosState()
                }
            ) {
                Text("Quitar filtro")
            }
        }

        Spacer(Modifier.height(8.dp))

        if (visibleIssueMessage != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = visibleIssueMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (visibleIssueIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f)
                )
                if (visibleIssueCanRetry) {
                    TextButton(
                        onClick = {
                            if (visibleIssueBelongsToCatalog) {
                                scope.launch { loadMaterias(forceRefresh = true) }
                            } else {
                                loadHorarios(forceRefresh = true)
                            }
                        },
                        enabled = !loadingMaterias && !loadingHorarios
                    ) {
                        Text("Reintentar")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        LoadingContentBox(
            phase = loadingPhase,
            initialText = if (selectedMateria == null) {
                "Cargando materias…"
            } else {
                "Cargando reservas de aulas…"
            },
            refreshContentDescription = if (selectedMateria == null) {
                "Actualizando materias"
            } else {
                "Actualizando reservas de aulas"
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Column(Modifier.fillMaxSize()) {
                if (selectedMateria == null && materiasError == null) {
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

                horarioMateria?.let { horarios ->
                    val diasConReservas = diasHabiles.filter { (diaNumero, _) ->
                        horarios.reservas.any { it.dia == diaNumero }
                    }

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

                    if (diasConReservas.isEmpty()) {
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
                            state = horariosListState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(diasConReservas, key = { it.first }) { (diaNumero, diaNombre) ->
                                val reservasDelDia = horarios.reservas
                                    .filter { it.dia == diaNumero }
                                    .sortedWith(
                                        compareBy(
                                            HorarioReserva::horaInicio,
                                            HorarioReserva::horaFin,
                                            HorarioReserva::aula,
                                        )
                                    )
                                val dayBit = 1 shl diaNumero
                                val isExpanded = expandedDaysMask and dayBit != 0

                                HorarioDiaCard(
                                    diaNombre = diaNombre,
                                    reservasDelDia = reservasDelDia,
                                    isExpanded = isExpanded,
                                    onToggle = { expandedDaysMask = expandedDaysMask xor dayBit },
                                )
                            }
                        }

                        ScrollMoreHint(
                            listState = horariosListState,
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

internal fun HorarioMateria.withCurrentMateriaName(currentName: String): HorarioMateria =
    if (materiaNombre == currentName) this else copy(materiaNombre = currentName)
