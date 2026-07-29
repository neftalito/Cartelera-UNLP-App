/** Conserva la selección y coordina la carga de los planes de estudio. */
package com.overcoders.unlpcarteleranotifier.ui.herramientas

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.overcoders.unlpcarteleranotifier.data.ApiException
import com.overcoders.unlpcarteleranotifier.data.CachedValue
import com.overcoders.unlpcarteleranotifier.data.StudyPlanCacheStore
import com.overcoders.unlpcarteleranotifier.data.StudyPlanCatalog
import com.overcoders.unlpcarteleranotifier.data.StudyPlansService
import com.overcoders.unlpcarteleranotifier.model.StudyCareer
import com.overcoders.unlpcarteleranotifier.model.StudyPlanDocument
import com.overcoders.unlpcarteleranotifier.model.StudyPlanSource
import com.overcoders.unlpcarteleranotifier.ui.common.cachedContentWarning
import com.overcoders.unlpcarteleranotifier.ui.common.userFacingError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val initialStudyCareer = StudyPlanCatalog.careers.firstOrNull()?.career
private val initialStudyPlanId = StudyPlanCatalog.careers.firstOrNull()?.plans?.firstOrNull()?.id

internal data class StudyPlanCareerGroup(
    val career: StudyCareer,
    val plans: List<StudyPlanSource>,
)

internal data class StudyPlansUiState(
    val loading: Boolean,
    val error: String?,
    val warning: String?,
    val careerGroups: List<StudyPlanCareerGroup>,
    val selectedCareerGroup: StudyPlanCareerGroup?,
    val selectedPlanSource: StudyPlanSource?,
    val selectedPlan: StudyPlanDocument?,
    val careerDropdownExpanded: Boolean,
    val planDropdownExpanded: Boolean,
    val isFullscreen: Boolean,
    val resolvedPlanId: String?,
    val unavailablePlanId: String?,
)

@Stable
internal class StudyPlansState(
    private val context: Context,
    private val service: StudyPlansService,
    private val scope: CoroutineScope,
    selectedCareerShortNameState: MutableState<String?>,
    selectedPlanIdState: MutableState<String?>,
    isFullscreenState: MutableState<Boolean>,
) {
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var warning by mutableStateOf<String?>(null)
        private set
    private var plansById by mutableStateOf<Map<String, CachedValue<StudyPlanDocument>>>(
        emptyMap()
    )
    private var resolvedPlanIds by mutableStateOf<Set<String>>(emptySet())
    private var unavailablePlanIds by mutableStateOf<Set<String>>(emptySet())
    private var loadJob: Job? = null
    private var loadRequestId = 0L

    var selectedCareerShortName by selectedCareerShortNameState
        private set
    var selectedPlanId by selectedPlanIdState
        private set
    var isFullscreen by isFullscreenState
        private set
    var careerDropdownExpanded by mutableStateOf(false)
        private set
    var planDropdownExpanded by mutableStateOf(false)
        private set

    val careerGroups: List<StudyPlanCareerGroup>
        get() = StudyPlanCatalog.careers.map { catalog ->
            StudyPlanCareerGroup(
                career = catalog.career,
                plans = catalog.plans,
            )
        }

    val selectedCareerGroup: StudyPlanCareerGroup?
        get() {
            val selection = reconciledSelection()
            return careerGroups.firstOrNull {
                it.career.shortName == selection.careerShortName
            }
        }

    val selectedPlanSource: StudyPlanSource?
        get() {
            val group = selectedCareerGroup ?: return null
            val planId = reconciledSelection().planId
            return group.plans.firstOrNull { it.id == planId }
        }

    val selectedPlan: StudyPlanDocument?
        get() = selectedPlanSource?.id?.let { plansById[it]?.value }

    private val resolvedSelectedPlanId: String?
        get() = selectedPlanSource?.id?.takeIf(resolvedPlanIds::contains)

    private val unavailableSelectedPlanId: String?
        get() = selectedPlanSource?.id?.takeIf(unavailablePlanIds::contains)

    val uiState: StudyPlansUiState
        get() = StudyPlansUiState(
            loading = loading,
            error = error,
            warning = warning,
            careerGroups = careerGroups,
            selectedCareerGroup = selectedCareerGroup,
            selectedPlanSource = selectedPlanSource,
            selectedPlan = selectedPlan,
            careerDropdownExpanded = careerDropdownExpanded,
            planDropdownExpanded = planDropdownExpanded,
            isFullscreen = isFullscreen,
            resolvedPlanId = resolvedSelectedPlanId,
            unavailablePlanId = unavailableSelectedPlanId,
        )

    private fun reconciledSelection(): StudyPlanSelection =
        reconcileStudyPlanSelection(
            requested = StudyPlanSelection(
                careerShortName = selectedCareerShortName,
                planId = selectedPlanId,
            ),
            available = careerGroups.map { group ->
                StudyPlanAvailability(
                    careerShortName = group.career.shortName,
                    planIds = group.plans.map { it.id },
                )
            },
        )

    private fun persistReconciledSelection() {
        val reconciled = reconciledSelection()
        selectedCareerShortName = reconciled.careerShortName
        selectedPlanId = reconciled.planId
    }

    fun updateCareerDropdownExpanded(expanded: Boolean) {
        careerDropdownExpanded = expanded
    }

    fun updatePlanDropdownExpanded(expanded: Boolean) {
        planDropdownExpanded = expanded
    }

    fun selectCareer(career: StudyCareer) {
        val group = careerGroups.firstOrNull { it.career == career } ?: return
        val previousPlanId = selectedPlanSource?.id
        selectedCareerShortName = career.shortName
        selectedPlanId = group.plans.firstOrNull()?.id
        if (selectedPlanSource?.id != previousPlanId) {
            error = null
            warning = null
        }
        careerDropdownExpanded = false
        planDropdownExpanded = false
    }

    fun selectPlan(source: StudyPlanSource) {
        val configuredSource = StudyPlanCatalog.sources.firstOrNull { it.id == source.id }
            ?: return
        val previousPlanId = selectedPlanSource?.id
        selectedCareerShortName = configuredSource.career.shortName
        selectedPlanId = configuredSource.id
        if (configuredSource.id != previousPlanId) {
            error = null
            warning = null
        }
        planDropdownExpanded = false
    }

    fun toggleFullscreen() {
        careerDropdownExpanded = false
        planDropdownExpanded = false
        isFullscreen = !isFullscreen
    }

    fun exitFullscreen() {
        isFullscreen = false
    }

    fun cancelLoad() {
        loadRequestId += 1
        val activeJob = loadJob
        loadJob = null
        activeJob?.cancel()
        loading = false
    }

    fun loadPlan(
        source: StudyPlanSource,
        forceRefresh: Boolean = false,
    ) {
        val configuredSource = StudyPlanCatalog.sources.firstOrNull { it.id == source.id }
            ?: return
        persistReconciledSelection()
        if (selectedPlanSource?.id != configuredSource.id) return

        loadRequestId += 1
        val requestId = loadRequestId
        val previousJob = loadJob
        loadJob = null
        previousJob?.cancel()

        error = null
        warning = null

        val memoryCached = plansById[configuredSource.id]
        if (!shouldFetchStudyPlan(memoryCached, forceRefresh)) {
            resolvedPlanIds = resolvedPlanIds + configuredSource.id
            unavailablePlanIds = unavailablePlanIds - configuredSource.id
            loading = false
            return
        }

        loading = true
        val newJob = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val cached = memoryCached ?: StudyPlanCacheStore.load(context, configuredSource)
                if (!isCurrentRequest(requestId, configuredSource.id)) return@launch

                if (cached != null) {
                    plansById = plansById + (configuredSource.id to cached)
                    resolvedPlanIds = resolvedPlanIds + configuredSource.id
                    unavailablePlanIds = unavailablePlanIds - configuredSource.id
                    if (!shouldFetchStudyPlan(cached, forceRefresh)) return@launch
                }

                val fetched = service.fetchOne(configuredSource)
                if (!isCurrentRequest(requestId, configuredSource.id)) return@launch

                plansById = plansById + (
                    configuredSource.id to CachedValue(
                        value = fetched,
                        savedAtEpochMillis = System.currentTimeMillis(),
                    )
                )
                resolvedPlanIds = resolvedPlanIds + configuredSource.id
                unavailablePlanIds = unavailablePlanIds - configuredSource.id
                try {
                    StudyPlanCacheStore.save(context, fetched)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // El plan descargado sigue siendo válido aunque no pueda persistirse.
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isCurrentRequest(requestId, configuredSource.id)) return@launch

                val hasDocument = plansById[configuredSource.id] != null
                when {
                    hasDocument -> {
                        warning = cachedContentWarning(
                            operation = "Actualizar el plan de estudio",
                            error = e,
                            detail = configuredSource.errorDetail(),
                        )
                    }
                    e is ApiException && e.httpCode == 404 -> {
                        resolvedPlanIds = resolvedPlanIds + configuredSource.id
                        unavailablePlanIds = unavailablePlanIds + configuredSource.id
                    }
                    else -> {
                        error = userFacingError(
                            operation = "Cargar el plan de estudio",
                            error = e,
                            detail = configuredSource.errorDetail(),
                        )
                    }
                }
            } finally {
                if (loadRequestId == requestId) {
                    loading = false
                    loadJob = null
                }
            }
        }
        loadJob = newJob
        newJob.start()
    }

    private fun isCurrentRequest(requestId: Long, planId: String): Boolean =
        loadRequestId == requestId && selectedPlanSource?.id == planId
}

@Composable
internal fun rememberStudyPlansState(): StudyPlansState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { StudyPlansService() }
    val selectedCareerShortNameState = rememberSaveable {
        mutableStateOf(initialStudyCareer?.shortName)
    }
    val selectedPlanIdState = rememberSaveable {
        mutableStateOf(initialStudyPlanId)
    }
    val isFullscreenState = rememberSaveable { mutableStateOf(false) }

    return remember(
        context,
        service,
        scope,
        selectedCareerShortNameState,
        selectedPlanIdState,
        isFullscreenState,
    ) {
        StudyPlansState(
            context = context,
            service = service,
            scope = scope,
            selectedCareerShortNameState = selectedCareerShortNameState,
            selectedPlanIdState = selectedPlanIdState,
            isFullscreenState = isFullscreenState,
        )
    }
}

private fun StudyPlanSource.errorDetail(): String =
    "${career.shortName}, plan $planLabel"
