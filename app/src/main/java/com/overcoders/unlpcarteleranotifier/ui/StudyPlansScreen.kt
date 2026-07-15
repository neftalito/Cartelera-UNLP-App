/**
 * Carga, conserva y presenta los planes de estudio disponibles para cada carrera.
 */
package com.overcoders.unlpcarteleranotifier.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.HeaderActionPlacement
import com.overcoders.unlpcarteleranotifier.data.ApiException
import com.overcoders.unlpcarteleranotifier.data.ContentCachePolicy
import com.overcoders.unlpcarteleranotifier.data.StudyPlanCatalog
import com.overcoders.unlpcarteleranotifier.data.StudyPlanCacheStore
import com.overcoders.unlpcarteleranotifier.data.StudyPlansService
import com.overcoders.unlpcarteleranotifier.model.StudyCareer
import com.overcoders.unlpcarteleranotifier.model.StudyPlanDocument
import com.overcoders.unlpcarteleranotifier.model.StudyPlanSource
import com.overcoders.unlpcarteleranotifier.ui.common.HtmlWebView
import com.overcoders.unlpcarteleranotifier.ui.common.LoadingContentBox
import com.overcoders.unlpcarteleranotifier.ui.common.contentLoadingPhase
import com.overcoders.unlpcarteleranotifier.ui.common.partialContentWarning
import com.overcoders.unlpcarteleranotifier.ui.common.userFacingError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val initialCareer = StudyPlanCatalog.careers.firstOrNull()?.career
private val initialPlanId = StudyPlanCatalog.careers.firstOrNull()?.plans?.firstOrNull()?.id

internal enum class StudyPlansLoadOutcome {
    CONTENT,
    CONTENT_WITH_WARNING,
    ERROR,
    EMPTY,
}

internal fun studyPlansLoadOutcome(
    documentCount: Int,
    attemptedSourceCount: Int,
    unavailableCount: Int,
    operationalFailureCount: Int,
): StudyPlansLoadOutcome {
    require(documentCount >= 0)
    require(attemptedSourceCount >= 0)
    require(unavailableCount >= 0)
    require(operationalFailureCount >= 0)

    return when {
        documentCount > 0 && unavailableCount + operationalFailureCount > 0 ->
            StudyPlansLoadOutcome.CONTENT_WITH_WARNING
        documentCount > 0 -> StudyPlansLoadOutcome.CONTENT
        operationalFailureCount > 0 -> StudyPlansLoadOutcome.ERROR
        attemptedSourceCount == 0 || unavailableCount == attemptedSourceCount ->
            StudyPlansLoadOutcome.EMPTY
        else -> StudyPlansLoadOutcome.ERROR
    }
}

@Composable
fun StudyPlansScreen(
    onFullscreenDetailChange: (Boolean) -> Unit = {},
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { StudyPlansService() }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var warning by remember { mutableStateOf<String?>(null) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var plans by remember { mutableStateOf<List<StudyPlanDocument>>(emptyList()) }
    var hasResolvedPlans by remember { mutableStateOf(false) }

    var selectedCareerShortName by rememberSaveable { mutableStateOf(initialCareer?.shortName) }
    var selectedPlanId by rememberSaveable { mutableStateOf(initialPlanId) }
    var careerDropdownExpanded by remember { mutableStateOf(false) }
    var planDropdownExpanded by remember { mutableStateOf(false) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    val planById = remember(plans) { plans.associateBy { it.source.id } }
    val careerGroups = remember(planById) {
        StudyPlanCatalog.careers.mapNotNull { catalog ->
            val availablePlans = catalog.plans.mapNotNull { source -> planById[source.id] }
            if (availablePlans.isEmpty()) {
                null
            } else {
                StudyPlanCareerGroup(
                    career = catalog.career,
                    plans = availablePlans
                )
            }
        }
    }
    val selectedCareerGroup = remember(careerGroups, selectedCareerShortName, loading) {
        careerGroups.firstOrNull { it.career.shortName == selectedCareerShortName }
            ?: careerGroups.firstOrNull().takeUnless {
                loading && selectedCareerShortName != null
            }
    }
    val selectedPlan = remember(selectedCareerGroup, selectedPlanId, loading) {
        val group = selectedCareerGroup ?: return@remember null
        group.plans.firstOrNull { it.source.id == selectedPlanId }
            ?: group.plans.firstOrNull().takeUnless { loading && selectedPlanId != null }
    }

    LaunchedEffect(careerGroups, loading, selectedCareerShortName, selectedPlanId) {
        val reconciled = reconcileStudyPlanSelection(
            requested = StudyPlanSelection(
                careerShortName = selectedCareerShortName,
                planId = selectedPlanId,
            ),
            available = careerGroups.map { group ->
                StudyPlanAvailability(
                    careerShortName = group.career.shortName,
                    planIds = group.plans.map { it.source.id },
                )
            },
            loading = loading,
        )
        selectedCareerShortName = reconciled.careerShortName
        selectedPlanId = reconciled.planId
    }

    fun cancelLoad() {
        val activeJob = loadJob
        loadJob = null
        activeJob?.cancel()
        loading = false
    }

    fun loadPlans(forceRefresh: Boolean = false) {
        val previousJob = loadJob
        loadJob = null
        previousJob?.cancel()

        loading = true
        loadJob = scope.launch {
            val currentJob = coroutineContext[Job]
            error = null
            warning = null

            try {
                val cachedById = StudyPlanCatalog.sources.mapNotNull { source ->
                    StudyPlanCacheStore.load(context, source)?.let { source.id to it }
                }.toMap()

                val documentsById = cachedById
                    .mapValuesTo(mutableMapOf()) { it.value.value }
                plans = StudyPlanCatalog.sources.mapNotNull { documentsById[it.id] }
                if (plans.isNotEmpty()) {
                    hasResolvedPlans = true
                }

                val sourcesToRefresh = orderStudyPlanSourcesForRefresh(
                    sources = StudyPlanCatalog.sources.filter { source ->
                        forceRefresh || cachedById[source.id]
                            ?.isFresh(ContentCachePolicy.STUDY_PLANS_TTL_MILLIS) != true
                    },
                    selectedPlanId = selectedPlanId
                )
                val unavailableFailures = mutableListOf<ApiException>()
                val operationalFailures = mutableListOf<Throwable>()
                studyPlanRefreshBatches(
                    sources = sourcesToRefresh,
                    selectedPlanId = selectedPlanId,
                ).forEach { batch ->
                    val results = coroutineScope {
                        batch.map { source ->
                            async { fetchStudyPlan(service, source) }
                        }.awaitAll()
                    }

                    var addedDocument = false
                    results.forEach { result ->
                        when (result) {
                            is StudyPlanRefreshResult.Loaded -> {
                                val document = result.document
                                documentsById[document.source.id] = document
                                addedDocument = true
                                try {
                                    StudyPlanCacheStore.save(context, document)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    // El plan descargado sigue siendo válido aunque no pueda persistirse.
                                }
                            }
                            is StudyPlanRefreshResult.Unavailable ->
                                unavailableFailures += result.error
                            is StudyPlanRefreshResult.Failed ->
                                operationalFailures += result.error
                        }
                    }
                    if (addedDocument) {
                        plans = StudyPlanCatalog.sources.mapNotNull { documentsById[it.id] }
                        hasResolvedPlans = true
                    }
                }

                val outcome = studyPlansLoadOutcome(
                    documentCount = plans.size,
                    attemptedSourceCount = sourcesToRefresh.size,
                    unavailableCount = unavailableFailures.size,
                    operationalFailureCount = operationalFailures.size,
                )
                val firstIssue = operationalFailures.firstOrNull()
                    ?: unavailableFailures.firstOrNull()
                val failureDetail = buildString {
                    append("Fuentes consultadas: ${sourcesToRefresh.size}")
                    append("; no disponibles: ${unavailableFailures.size}")
                    append("; fallos operativos: ${operationalFailures.size}")
                }

                when (outcome) {
                    StudyPlansLoadOutcome.CONTENT -> {
                        hasResolvedPlans = true
                    }
                    StudyPlansLoadOutcome.CONTENT_WITH_WARNING -> {
                        hasResolvedPlans = true
                        warning = partialContentWarning(
                            operation = "Actualizar los planes de estudio",
                            error = firstIssue,
                            detail = failureDetail,
                        )
                    }
                    StudyPlansLoadOutcome.ERROR -> {
                        if (hasResolvedPlans) {
                            warning = partialContentWarning(
                                operation = "Actualizar los planes de estudio",
                                error = firstIssue,
                                detail = failureDetail,
                            )
                        } else {
                            error = userFacingError(
                                operation = "Cargar los planes de estudio",
                                error = firstIssue,
                                detail = failureDetail,
                            )
                        }
                    }
                    StudyPlansLoadOutcome.EMPTY -> {
                        hasResolvedPlans = true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (plans.isNotEmpty()) {
                    hasResolvedPlans = true
                }
                if (hasResolvedPlans) {
                    warning = partialContentWarning(
                        operation = "Actualizar los planes de estudio",
                        error = e,
                    )
                } else {
                    error = userFacingError(
                        operation = "Cargar los planes de estudio",
                        error = e,
                    )
                }
            } finally {
                if (loadJob === currentJob) {
                    loading = false
                    loadJob = null
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadPlans()
    }

    val showFullscreenAction = isFullscreen || selectedPlan != null
    val headerActions = remember(
        isFullscreen,
        showFullscreenAction,
        loading,
        selectedPlanId
    ) {
        buildList {
            add(
                HeaderAction(
                    icon = Icons.Default.Refresh,
                    enabled = !loading,
                    contentDescription = "Refrescar planes de estudio",
                    onClick = { loadPlans(forceRefresh = true) }
                )
            )
            if (isFullscreen) {
                add(
                    HeaderAction(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        placement = HeaderActionPlacement.Leading,
                        onClick = { isFullscreen = false }
                    )
                )
            }
            if (showFullscreenAction) {
                add(
                    HeaderAction(
                        icon = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (isFullscreen) {
                            "Salir de pantalla completa"
                        } else {
                            "Ver en pantalla completa"
                        },
                        onClick = {
                            careerDropdownExpanded = false
                            planDropdownExpanded = false
                            isFullscreen = !isFullscreen
                        }
                    )
                )
            }
        }
    }

    LaunchedEffect(headerActions) {
        onHeaderActionsChange(headerActions)
    }

    LaunchedEffect(isFullscreen) {
        onFullscreenDetailChange(isFullscreen)
    }

    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    DisposableEffect(Unit) {
        onDispose {
            cancelLoad()
            onHeaderActionsChange(emptyList())
            onFullscreenDetailChange(false)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isFullscreen) 0.dp else 16.dp)
    ) {
        if (!isFullscreen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(1f)
            ) {
                CareerSelector(
                    careerGroups = careerGroups,
                    selectedCareer = selectedCareerGroup?.career,
                    expanded = careerDropdownExpanded,
                    onExpandedChange = { careerDropdownExpanded = it },
                    onCareerSelected = { career ->
                        val group = careerGroups.firstOrNull { it.career == career }
                            ?: return@CareerSelector
                        selectedCareerShortName = career.shortName
                        selectedPlanId = group.plans.firstOrNull()?.source?.id
                        careerDropdownExpanded = false
                        planDropdownExpanded = false
                    },
                    modifier = Modifier.weight(3f)
                )

                Spacer(Modifier.width(8.dp))

                PlanSelector(
                    plans = selectedCareerGroup?.plans.orEmpty(),
                    selectedPlan = selectedPlan,
                    expanded = planDropdownExpanded,
                    onExpandedChange = { planDropdownExpanded = it },
                    onPlanSelected = { plan ->
                        selectedPlanId = plan.source.id
                        planDropdownExpanded = false
                    },
                    modifier = Modifier.weight(2f)
                )
            }

            Spacer(Modifier.height(12.dp))

        }

        if (warning != null) {
            Text(
                text = warning.orEmpty(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = if (isFullscreen) 16.dp else 0.dp),
            )
            Spacer(Modifier.height(8.dp))
        }

        LoadingContentBox(
            phase = contentLoadingPhase(
                isLoading = loading,
                hasResolvedContent = hasResolvedPlans || selectedPlan != null,
            ),
            initialText = "Cargando planes de estudio…",
            refreshContentDescription = "Actualizando planes de estudio",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                error != null -> {
                    StudyPlansStateMessage(
                        title = null,
                        body = error.orEmpty(),
                        isError = true,
                        onRetry = { loadPlans(forceRefresh = true) },
                    )
                }

                selectedPlan != null -> {
                    HtmlWebView(
                        html = selectedPlan.contentHtml,
                        baseUrl = selectedPlan.source.url,
                        fitContentWidth = true,
                        showScrollMoreHint = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                else -> EmptyStudyPlansState()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CareerSelector(
    careerGroups: List<StudyPlanCareerGroup>,
    selectedCareer: StudyCareer?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCareerSelected: (StudyCareer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = careerGroups.isNotEmpty()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) onExpandedChange(it) },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedCareer?.shortName.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text("Carrera") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = enabled)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            careerGroups.forEach { group ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(group.career.shortName)
                            Text(
                                text = group.career.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = { onCareerSelected(group.career) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlanSelector(
    plans: List<StudyPlanDocument>,
    selectedPlan: StudyPlanDocument?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPlanSelected: (StudyPlanDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = plans.isNotEmpty()
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) onExpandedChange(it) },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedPlan?.source?.planLabel.orEmpty(),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text("Plan") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = enabled)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            plans.forEach { plan ->
                DropdownMenuItem(
                    text = { Text("Plan ${plan.source.planLabel}") },
                    onClick = { onPlanSelected(plan) }
                )
            }
        }
    }
}

@Composable
private fun EmptyStudyPlansState() {
    StudyPlansStateMessage(
        title = "Sin contenido",
        body = "No hay planes de estudio disponibles.",
    )
}

@Composable
private fun StudyPlansStateMessage(
    title: String?,
    body: String,
    isError: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
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
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Reintentar")
            }
        }
    }
}

private data class StudyPlanCareerGroup(
    val career: StudyCareer,
    val plans: List<StudyPlanDocument>,
)

private sealed interface StudyPlanRefreshResult {
    data class Loaded(val document: StudyPlanDocument) : StudyPlanRefreshResult
    data class Unavailable(val error: ApiException) : StudyPlanRefreshResult
    data class Failed(val error: Throwable) : StudyPlanRefreshResult
}

private suspend fun fetchStudyPlan(
    service: StudyPlansService,
    source: StudyPlanSource,
): StudyPlanRefreshResult {
    return try {
        StudyPlanRefreshResult.Loaded(service.fetchOne(source))
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiException) {
        if (e.httpCode == 404) {
            StudyPlanRefreshResult.Unavailable(e)
        } else {
            StudyPlanRefreshResult.Failed(e)
        }
    } catch (e: Exception) {
        StudyPlanRefreshResult.Failed(e)
    }
}
