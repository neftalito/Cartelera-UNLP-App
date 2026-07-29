/** Coordina el catálogo, la selección y la carga cacheada de reservas por materia. */
package com.overcoders.unlpcarteleranotifier.ui.horarios

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.overcoders.unlpcarteleranotifier.data.ContentCachePolicy
import com.overcoders.unlpcarteleranotifier.data.HorariosCacheStore
import com.overcoders.unlpcarteleranotifier.data.HorariosService
import com.overcoders.unlpcarteleranotifier.data.MateriasRepository
import com.overcoders.unlpcarteleranotifier.model.HorarioMateria
import com.overcoders.unlpcarteleranotifier.model.MateriaCatalogItem
import com.overcoders.unlpcarteleranotifier.model.toMateriaCatalogIdOrNull
import com.overcoders.unlpcarteleranotifier.ui.common.ContentLoadingPhase
import com.overcoders.unlpcarteleranotifier.ui.common.cachedContentWarning
import com.overcoders.unlpcarteleranotifier.ui.common.normalizeForSearch
import com.overcoders.unlpcarteleranotifier.ui.common.resolvedContentLoadingPhase
import com.overcoders.unlpcarteleranotifier.ui.common.userFacingError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class HorariosIssue(
    val message: String,
    val isError: Boolean,
    val belongsToCatalog: Boolean,
)

@Stable
internal class HorariosState(
    private val context: Context,
    private val materiasRepository: MateriasRepository,
    private val horariosService: HorariosService,
    private val scope: CoroutineScope,
    private val expandedDaysMaskState: MutableIntState,
    private val selectedMateriaIdState: MutableState<String?>,
    private val selectedMateriaNameState: MutableState<String>,
    private val lastDisplayedMateriaIdState: MutableState<String?>,
) {
    internal val materiasFlow = materiasRepository.items

    internal var loadingMaterias by mutableStateOf(false)
        private set
    internal var loadingHorarios by mutableStateOf(false)
        private set
    internal var materiasError by mutableStateOf<String?>(null)
        private set
    internal var materiasWarning by mutableStateOf<String?>(null)
        private set
    internal var horariosError by mutableStateOf<String?>(null)
        private set
    internal var horariosWarning by mutableStateOf<String?>(null)
        private set

    internal var materiasSnapshotResolved by mutableStateOf(
        materiasRepository.items.value.isNotEmpty()
    )
        private set

    internal var selectedMateriaId: String?
        get() = selectedMateriaIdState.value
        private set(value) {
            selectedMateriaIdState.value = value
        }

    private var selectedMateriaName: String
        get() = selectedMateriaNameState.value
        set(value) {
            selectedMateriaNameState.value = value
        }

    internal var materiaQuery by mutableStateOf(
        selectedMateriaName.takeIf { selectedMateriaId != null }.orEmpty()
    )
        private set
    internal var dropdownExpanded by mutableStateOf(false)
        private set
    internal var horarioMateria by mutableStateOf<HorarioMateria?>(null)
        private set
    internal var resolvedHorarioMateriaId by mutableStateOf<String?>(null)
        private set

    internal val expandedDaysMask: Int
        get() = expandedDaysMaskState.intValue

    private var horariosJob: Job? = null

    internal fun selectedMateria(materias: List<MateriaCatalogItem>): MateriaCatalogItem? =
        selectedMateriaId?.let { id ->
            materias.firstOrNull { it.id == id }
                ?: MateriaCatalogItem(id = id, nombre = selectedMateriaName)
        }

    internal fun filteredMaterias(materias: List<MateriaCatalogItem>): List<MateriaCatalogItem> =
        filterMateriasByQuery(materias, materiaQuery)

    internal fun visibleIssue(): HorariosIssue? = resolveHorariosIssue(
        materiasError = materiasError,
        horariosError = horariosError,
        horariosWarning = horariosWarning,
        materiasWarning = materiasWarning,
    )

    internal fun canShareHorarios(selectedMateria: MateriaCatalogItem?): Boolean =
        selectedMateria != null &&
            !loadingHorarios &&
            horarioMateria?.reservas?.isNotEmpty() == true

    internal fun loadingPhase(selectedMateria: MateriaCatalogItem?): ContentLoadingPhase {
        val horariosSnapshotResolved = selectedMateria != null &&
            resolvedHorarioMateriaId == selectedMateria.id
        return resolvedContentLoadingPhase(
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
    }

    internal fun reconcileSelectedMateria(selectedMateria: MateriaCatalogItem?) {
        val current = selectedMateria ?: return
        if (!dropdownExpanded && selectedMateriaName != current.nombre) {
            selectedMateriaName = current.nombre
            materiaQuery = current.nombre
        }
        if (horarioMateria?.materiaNombre != current.nombre) {
            horarioMateria = horarioMateria?.withCurrentMateriaName(current.nombre)
        }
    }

    internal fun updateQuery(query: String) {
        materiaQuery = query
    }

    internal fun setDropdownExpanded(
        isExpanded: Boolean,
        selectedMateria: MateriaCatalogItem?,
    ) {
        materiaQuery = if (isExpanded) "" else selectedMateria?.nombre.orEmpty()
        dropdownExpanded = isExpanded
    }

    internal fun dismissDropdown(selectedMateria: MateriaCatalogItem?) {
        materiaQuery = selectedMateria?.nombre.orEmpty()
        dropdownExpanded = false
    }

    internal fun selectMateria(materia: MateriaCatalogItem) {
        if (selectedMateriaId != materia.id) {
            resetHorariosState()
        }
        selectedMateriaId = materia.id
        selectedMateriaName = materia.nombre
        materiaQuery = materia.nombre
        dropdownExpanded = false
    }

    internal fun clearSelection() {
        selectedMateriaId = null
        selectedMateriaName = ""
        materiaQuery = ""
        dropdownExpanded = false
        resetHorariosState()
    }

    internal fun recordDisplayedMateria(materiaId: String?): Boolean {
        if (lastDisplayedMateriaIdState.value == materiaId) return false

        resolvedHorarioMateriaId = null
        lastDisplayedMateriaIdState.value = materiaId
        return true
    }

    internal fun toggleDay(dayNumber: Int) {
        val dayBit = 1 shl dayNumber
        expandedDaysMaskState.intValue = expandedDaysMaskState.intValue xor dayBit
    }

    internal fun cancelHorariosLoad() {
        val activeJob = horariosJob
        horariosJob = null
        activeJob?.cancel()
        loadingHorarios = false
    }

    internal fun resetHorariosState() {
        cancelHorariosLoad()
        horarioMateria = null
        resolvedHorarioMateriaId = null
        horariosError = null
        horariosWarning = null
        expandedDaysMaskState.intValue = 0
    }

    internal suspend fun loadMaterias(forceRefresh: Boolean = false) {
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

    internal fun loadHorarios(
        materia: MateriaCatalogItem,
        forceRefresh: Boolean = false,
    ) {
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
                        error = e,
                    )
                } else {
                    horarioMateria = null
                    horariosError = userFacingError(
                        operation = "cargar las reservas de aulas",
                        error = e,
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

    internal fun dispose() {
        cancelHorariosLoad()
    }
}

@Composable
internal fun rememberHorariosState(): HorariosState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val materiasRepository = remember(context) { MateriasRepository.get(context) }
    val horariosService = remember { HorariosService() }
    val expandedDaysMaskState = rememberSaveable { mutableIntStateOf(0) }
    val selectedMateriaIdState = rememberSaveable { mutableStateOf<String?>(null) }
    val selectedMateriaNameState = rememberSaveable { mutableStateOf("") }
    val lastDisplayedMateriaIdState = rememberSaveable {
        mutableStateOf(selectedMateriaIdState.value)
    }

    return remember(
        context,
        materiasRepository,
        horariosService,
        scope,
        expandedDaysMaskState,
        selectedMateriaIdState,
        selectedMateriaNameState,
        lastDisplayedMateriaIdState,
    ) {
        HorariosState(
            context = context,
            materiasRepository = materiasRepository,
            horariosService = horariosService,
            scope = scope,
            expandedDaysMaskState = expandedDaysMaskState,
            selectedMateriaIdState = selectedMateriaIdState,
            selectedMateriaNameState = selectedMateriaNameState,
            lastDisplayedMateriaIdState = lastDisplayedMateriaIdState,
        )
    }
}

internal fun filterMateriasByQuery(
    materias: List<MateriaCatalogItem>,
    query: String,
): List<MateriaCatalogItem> {
    val normalizedQuery = query.normalizeForSearch()
    return if (normalizedQuery.isBlank()) {
        materias
    } else {
        materias.filter { materia ->
            materia.nombre.normalizeForSearch().contains(normalizedQuery)
        }
    }
}

internal fun resolveHorariosIssue(
    materiasError: String?,
    horariosError: String?,
    horariosWarning: String?,
    materiasWarning: String?,
): HorariosIssue? {
    val message = materiasError ?: horariosError ?: horariosWarning ?: materiasWarning
        ?: return null
    return HorariosIssue(
        message = message,
        isError = materiasError != null || horariosError != null,
        belongsToCatalog = materiasError != null || (
            horariosError == null && horariosWarning == null
        ),
    )
}

internal fun HorarioMateria.withCurrentMateriaName(currentName: String): HorarioMateria =
    if (materiaNombre == currentName) this else copy(materiaNombre = currentName)
