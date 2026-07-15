/**
 * Muestra reservas eventuales paginadas, con filtros remotos y fallbacks de caché.
 */
package com.overcoders.unlpcarteleranotifier.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.data.CachedValue
import com.overcoders.unlpcarteleranotifier.data.ContentCachePolicy
import com.overcoders.unlpcarteleranotifier.data.EventualReservationFiltersCacheStore
import com.overcoders.unlpcarteleranotifier.data.EventualReservationsService
import com.overcoders.unlpcarteleranotifier.model.EventualReservation
import com.overcoders.unlpcarteleranotifier.model.EventualReservationFilterOption
import com.overcoders.unlpcarteleranotifier.model.EventualReservationsPage
import com.overcoders.unlpcarteleranotifier.ui.common.LoadingContentBox
import com.overcoders.unlpcarteleranotifier.ui.common.PaginationLoadingIndicator
import com.overcoders.unlpcarteleranotifier.ui.common.cachedContentWarning
import com.overcoders.unlpcarteleranotifier.ui.common.paginatedLoadingState
import com.overcoders.unlpcarteleranotifier.ui.common.normalizeForSearch
import com.overcoders.unlpcarteleranotifier.ui.common.partialContentWarning
import com.overcoders.unlpcarteleranotifier.ui.common.userFacingError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private data class EventualReservationsRefreshBackup(
    val filterKey: String,
    val reservations: List<EventualReservation>,
    val totalCount: Int,
    val nextPage: Int?,
    val lastAppliedPage: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventualReservationsScreen(
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val service = remember { EventualReservationsService() }
    val listState = rememberLazyListState()
    val pageCache = remember {
        mutableMapOf<EventualReservationPageKey, CachedValue<EventualReservationsPage>>()
    }

    var filterOptionsLoading by remember { mutableStateOf(false) }
    var filterOptionsError by remember { mutableStateOf<Throwable?>(null) }
    var filterOptionsWarning by remember { mutableStateOf<Throwable?>(null) }
    var classroomOptions by remember { mutableStateOf<List<EventualReservationFilterOption>>(emptyList()) }
    var subjectOptions by remember { mutableStateOf<List<EventualReservationFilterOption>>(emptyList()) }
    var filterOptionsSnapshotResolved by remember { mutableStateOf(false) }

    var selectedClassroomId by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedClassroomLabel by rememberSaveable { mutableStateOf("") }
    var selectedSubjectId by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedSubjectLabel by rememberSaveable { mutableStateOf("") }
    var classroomQuery by remember(selectedClassroomId, selectedClassroomLabel) {
        mutableStateOf(selectedClassroomLabel.takeIf { selectedClassroomId != null }.orEmpty())
    }
    var subjectQuery by remember(selectedSubjectId, selectedSubjectLabel) {
        mutableStateOf(selectedSubjectLabel.takeIf { selectedSubjectId != null }.orEmpty())
    }
    val activePaginationFilterKey = "$selectedClassroomId|$selectedSubjectId"
    var paginationRestorationFilterKey by rememberSaveable {
        mutableStateOf(activePaginationFilterKey)
    }
    var paginationRestorationTargetPage by rememberSaveable { mutableIntStateOf(1) }
    var savedListIndex by rememberSaveable { mutableIntStateOf(0) }
    var savedListScrollOffset by rememberSaveable { mutableIntStateOf(0) }
    var listPositionRestorationPending by remember {
        mutableStateOf(paginationRestorationTargetPage > 1 || savedListIndex > 0)
    }
    var classroomExpanded by remember { mutableStateOf(false) }
    var subjectExpanded by remember { mutableStateOf(false) }

    val selectedClassroom = remember(
        classroomOptions,
        selectedClassroomId,
        selectedClassroomLabel,
    ) {
        selectedClassroomId?.let { id ->
            classroomOptions.firstOrNull { it.id == id }
                ?: EventualReservationFilterOption(id = id, label = selectedClassroomLabel)
        }
    }
    val selectedSubject = remember(subjectOptions, selectedSubjectId, selectedSubjectLabel) {
        selectedSubjectId?.let { id ->
            subjectOptions.firstOrNull { it.id == id }
                ?: EventualReservationFilterOption(id = id, label = selectedSubjectLabel)
        }
    }

    LaunchedEffect(selectedClassroom?.label, classroomExpanded) {
        val current = selectedClassroom ?: return@LaunchedEffect
        if (!classroomExpanded && selectedClassroomLabel != current.label) {
            selectedClassroomLabel = current.label
            classroomQuery = current.label
        }
    }
    LaunchedEffect(selectedSubject?.label, subjectExpanded) {
        val current = selectedSubject ?: return@LaunchedEffect
        if (!subjectExpanded && selectedSubjectLabel != current.label) {
            selectedSubjectLabel = current.label
            subjectQuery = current.label
        }
    }

    var reservations by remember { mutableStateOf<List<EventualReservation>>(emptyList()) }
    var totalCount by remember { mutableIntStateOf(0) }
    var nextPage by remember { mutableStateOf<Int?>(null) }
    var loadingInitial by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var initialError by remember { mutableStateOf<Throwable?>(null) }
    var refreshWarning by remember { mutableStateOf<Throwable?>(null) }
    var paginationError by remember { mutableStateOf<Throwable?>(null) }
    var paginationRetryPage by remember { mutableStateOf<Int?>(null) }
    var retryingInitialLoad by remember { mutableStateOf(false) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var lastAppliedPage by remember { mutableIntStateOf(0) }
    var refreshBackup by remember {
        mutableStateOf<EventualReservationsRefreshBackup?>(null)
    }
    var resolvedReservationsFilterKey by remember { mutableStateOf<String?>(null) }

    val filteredClassrooms = remember(classroomOptions, classroomQuery) {
        filterOptions(classroomOptions, classroomQuery)
    }
    val filteredSubjects = remember(subjectOptions, subjectQuery) {
        filterOptions(subjectOptions, subjectQuery)
    }

    suspend fun loadFilterOptions(forceRefresh: Boolean = false) {
        if (filterOptionsLoading) return

        filterOptionsLoading = true
        filterOptionsError = null
        filterOptionsWarning = null
        try {
            val cached = try {
                EventualReservationFiltersCacheStore.load(context)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (cached != null) {
                classroomOptions = cached.value.classrooms
                subjectOptions = cached.value.subjects
                filterOptionsSnapshotResolved = true
                if (!forceRefresh && cached.isFresh(ContentCachePolicy.CATALOG_TTL_MILLIS)) {
                    return
                }
            }

            val options = service.fetchFilterOptions()
            classroomOptions = options.classrooms
            subjectOptions = options.subjects
            filterOptionsSnapshotResolved = true
            try {
                EventualReservationFiltersCacheStore.save(context, options)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Las opciones remotas siguen siendo utilizables aunque no puedan persistirse.
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (filterOptionsSnapshotResolved) {
                filterOptionsWarning = e
            } else {
                filterOptionsError = e
            }
        } finally {
            filterOptionsLoading = false
        }
    }

    fun cancelPageLoad() {
        val activeJob = loadJob
        loadJob = null
        activeJob?.cancel()
        loadingInitial = false
        loadingMore = false
    }

    fun loadPage(reset: Boolean, forceRefresh: Boolean = false) {
        if (!reset && (loadingInitial || loadingMore)) return
        if (reset) cancelPageLoad()

        val requestedPage = if (reset) 1 else paginationRetryPage ?: nextPage ?: return
        val requestedClassroomId = selectedClassroom?.id
        val requestedSubjectId = selectedSubject?.id
        val requestedFilterKey = "$requestedClassroomId|$requestedSubjectId"
        val preservesVisibleSnapshot = reset &&
            forceRefresh &&
            requestedFilterKey == paginationRestorationFilterKey &&
            resolvedReservationsFilterKey == requestedFilterKey
        if (reset) {
            if (paginationRestorationFilterKey != requestedFilterKey) {
                refreshBackup = null
                lastAppliedPage = 0
                paginationRestorationFilterKey = requestedFilterKey
                paginationRestorationTargetPage = 1
                savedListIndex = 0
                savedListScrollOffset = 0
                listPositionRestorationPending = false
            } else if (
                !preservesVisibleSnapshot &&
                (paginationRestorationTargetPage > 1 || savedListIndex > 0)
            ) {
                lastAppliedPage = 0
                listPositionRestorationPending = true
            }
        }
        if (
            preservesVisibleSnapshot &&
            paginationRestorationTargetPage > 1 &&
            refreshBackup == null
        ) {
            refreshBackup = EventualReservationsRefreshBackup(
                filterKey = requestedFilterKey,
                reservations = reservations,
                totalCount = totalCount,
                nextPage = nextPage,
                lastAppliedPage = lastAppliedPage,
            )
        }
        val cacheKey = EventualReservationPageKey(
            classroomId = requestedClassroomId,
            subjectId = requestedSubjectId,
            page = requestedPage
        )

        fun applyPage(page: EventualReservationsPage) {
            reservations = if (reset) {
                page.reservations
            } else {
                (reservations + page.reservations).distinctBy(EventualReservation::id)
            }
            totalCount = page.totalCount
            nextPage = page.nextPage
            lastAppliedPage = requestedPage
            paginationRestorationFilterKey = requestedFilterKey
            resolvedReservationsFilterKey = requestedFilterKey
            paginationRestorationTargetPage = maxOf(
                paginationRestorationTargetPage,
                requestedPage,
            )
        }

        val cached = pageCache[cacheKey]
        val cacheIsFresh = cached?.isFresh(
            ContentCachePolicy.EVENTUAL_RESERVATIONS_MEMORY_TTL_MILLIS
        ) == true
        if (cached != null && !preservesVisibleSnapshot) {
            applyPage(cached.value)
        }
        if (!forceRefresh && cacheIsFresh) {
            initialError = null
            refreshWarning = null
            paginationError = null
            paginationRetryPage = null
            return
        }

        if (reset) {
            if (cached == null && !preservesVisibleSnapshot) {
                reservations = emptyList()
                totalCount = 0
                nextPage = null
            }
            initialError = null
            refreshWarning = null
            paginationError = null
            paginationRetryPage = null
            loadingInitial = true
        } else {
            paginationError = null
            loadingMore = true
        }

        loadJob = scope.launch {
            val currentJob = coroutineContext[Job]
            var refreshSucceeded = false
            try {
                val page = service.fetchPage(
                    page = requestedPage,
                    classroomId = requestedClassroomId,
                    subjectId = requestedSubjectId
                )
                if (
                    selectedClassroom?.id != requestedClassroomId ||
                    selectedSubject?.id != requestedSubjectId
                ) {
                    return@launch
                }

                pageCache[cacheKey] = CachedValue(page, System.currentTimeMillis())
                if (reset && preservesVisibleSnapshot) {
                    lastAppliedPage = 0
                    listPositionRestorationPending =
                        paginationRestorationTargetPage > 1 || savedListIndex > 0
                }
                applyPage(page)
                refreshSucceeded = true
                refreshWarning = null
                paginationRetryPage = null
                val activeRefreshBackup = refreshBackup?.takeIf {
                    it.filterKey == requestedFilterKey
                }
                val refreshStillInProgress = activeRefreshBackup != null &&
                    requestedPage < paginationRestorationTargetPage &&
                    page.nextPage != null
                if (!refreshStillInProgress) {
                    refreshBackup = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (
                    selectedClassroom?.id != requestedClassroomId ||
                    selectedSubject?.id != requestedSubjectId
                ) {
                    return@launch
                }
                val activeRefreshBackup = refreshBackup?.takeIf {
                    it.filterKey == requestedFilterKey
                }
                if (activeRefreshBackup != null) {
                    reservations = activeRefreshBackup.reservations
                    totalCount = activeRefreshBackup.totalCount
                    nextPage = activeRefreshBackup.nextPage
                    lastAppliedPage = activeRefreshBackup.lastAppliedPage
                    listPositionRestorationPending = false
                    refreshBackup = null
                    refreshWarning = e
                    paginationError = null
                    paginationRetryPage = null
                } else if (reset) {
                    if (
                        preservesVisibleSnapshot ||
                        shouldUseCachedEventualReservationsFallback(reset, cached)
                    ) {
                        refreshWarning = e
                    } else {
                        initialError = e
                    }
                } else {
                    paginationError = e
                    paginationRetryPage = requestedPage
                }
            } finally {
                pageCache.invalidateFollowingPagesAfterEventualReservationRefresh(
                    classroomId = requestedClassroomId,
                    subjectId = requestedSubjectId,
                    reset = reset,
                    forceRefresh = forceRefresh,
                    cacheIsFresh = cacheIsFresh,
                    refreshSucceeded = refreshSucceeded
                )
                if (loadJob === currentJob) {
                    loadingInitial = false
                    loadingMore = false
                    loadJob = null
                }
            }
        }
    }

    fun retryInitialLoad() {
        if (retryingInitialLoad) return
        val requestedClassroomId = selectedClassroomId
        val requestedSubjectId = selectedSubjectId
        retryingInitialLoad = true
        scope.launch {
            try {
                loadFilterOptions(forceRefresh = true)
                if (
                    selectedClassroomId != requestedClassroomId ||
                    selectedSubjectId != requestedSubjectId
                ) {
                    return@launch
                }
                loadPage(reset = true, forceRefresh = true)
            } finally {
                retryingInitialLoad = false
            }
        }
    }

    val initialFailureMessage = when {
        initialError != null && filterOptionsError != null -> userFacingError(
            operation = "cargar los filtros y las reservas eventuales",
            error = initialError,
            detail = "Filtros: ${filterOptionsError?.debugSummary().orEmpty()}"
        )

        initialError != null -> userFacingError(
            operation = "cargar las reservas eventuales",
            error = initialError
        )

        filterOptionsError != null -> userFacingError(
            operation = "cargar los filtros de reservas eventuales",
            error = filterOptionsError
        )

        else -> null
    }
    val initialWarningMessage = when {
        refreshWarning != null && filterOptionsWarning != null -> partialContentWarning(
            operation = "actualizar los filtros y las reservas eventuales",
            error = refreshWarning,
            detail = "Filtros: ${filterOptionsWarning?.debugSummary().orEmpty()}"
        )

        refreshWarning != null -> cachedContentWarning(
            operation = "actualizar las reservas eventuales",
            error = refreshWarning
        )

        filterOptionsWarning != null -> cachedContentWarning(
            operation = "actualizar los filtros de reservas eventuales",
            error = filterOptionsWarning
        )

        else -> null
    }
    // Un fallo de paginación desplaza una advertencia previa, pero no un fallo inicial
    // cuyo reintento conjunto también recupera la paginación y los filtros.
    val initialIssueMessage = initialFailureMessage ?: initialWarningMessage.takeIf {
        paginationError == null
    }
    val initialIssueIsError = initialFailureMessage != null
    val showPaginationError = paginationError != null && initialFailureMessage == null
    val reservationsForDisplay = refreshBackup?.reservations ?: reservations
    val totalCountForDisplay = refreshBackup?.totalCount ?: totalCount
    val nextPageForDisplay = refreshBackup?.nextPage ?: nextPage
    val reservationsSnapshotResolved =
        resolvedReservationsFilterKey == activePaginationFilterKey
    val filterOptionsUnavailable =
        !filterOptionsLoading && !filterOptionsSnapshotResolved && filterOptionsError != null
    val loadingState = paginatedLoadingState(
        isContentLoading = loadingInitial,
        isAuxiliaryLoading = filterOptionsLoading,
        isLoadingMore = loadingMore,
        resolvedKey = resolvedReservationsFilterKey,
        requestedKey = activePaginationFilterKey,
        hasError = initialError != null &&
            paginationRestorationFilterKey == activePaginationFilterKey,
    )

    LaunchedEffect(Unit) {
        loadFilterOptions()
    }

    LaunchedEffect(selectedClassroom?.id, selectedSubject?.id) {
        if (
            paginationRestorationFilterKey != activePaginationFilterKey &&
            listState.layoutInfo.totalItemsCount > 0
        ) {
            listState.scrollToItem(0)
        }
        loadPage(reset = true)
    }

    LaunchedEffect(listState, listPositionRestorationPending) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, scrollOffset) ->
            if (!listPositionRestorationPending) {
                savedListIndex = index
                savedListScrollOffset = scrollOffset
            }
        }
    }

    val canContinueListRestoration = listPositionRestorationPending &&
        paginationRestorationFilterKey == activePaginationFilterKey &&
        lastAppliedPage < paginationRestorationTargetPage &&
        nextPage != null &&
        !loadingInitial &&
        !loadingMore &&
        initialError == null &&
        refreshWarning == null &&
        paginationError == null

    LaunchedEffect(
        canContinueListRestoration,
        listPositionRestorationPending,
        lastAppliedPage,
        paginationRestorationTargetPage,
        nextPage,
        loadingInitial,
        loadingMore,
        initialError,
        refreshWarning,
        paginationError,
        activePaginationFilterKey,
    ) {
        if (canContinueListRestoration) {
            loadPage(reset = false)
            return@LaunchedEffect
        }

        val restorationReachedEnd =
            lastAppliedPage >= paginationRestorationTargetPage || nextPage == null
        if (
            listPositionRestorationPending &&
            paginationRestorationFilterKey == activePaginationFilterKey &&
            restorationReachedEnd &&
            !loadingInitial &&
            !loadingMore &&
            initialError == null &&
            refreshWarning == null &&
            paginationError == null
        ) {
            if (nextPage == null) {
                paginationRestorationTargetPage = lastAppliedPage.coerceAtLeast(1)
            }
            val maximumIndex = (reservations.size - 1).coerceAtLeast(0)
            listState.scrollToItem(
                index = savedListIndex.coerceIn(0, maximumIndex),
                scrollOffset = savedListScrollOffset,
            )
            listPositionRestorationPending = false
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val totalItems = listState.layoutInfo.totalItemsCount
            reservations.isNotEmpty() && totalItems > 0 && lastVisibleIndex >= totalItems - 3
        }
    }

    LaunchedEffect(
        shouldLoadMore,
        nextPage,
        loadingInitial,
        loadingMore,
        paginationError,
        initialError,
        refreshWarning,
        listPositionRestorationPending,
    ) {
        if (
            shouldLoadMore &&
            nextPage != null &&
            !listPositionRestorationPending &&
            !loadingInitial &&
            !loadingMore &&
            paginationError == null &&
            initialError == null &&
            refreshWarning == null
        ) {
            loadPage(reset = false)
        }
    }

    val refreshEnabled =
        !loadingInitial && !loadingMore && !filterOptionsLoading && !retryingInitialLoad
    LaunchedEffect(refreshEnabled, selectedClassroomId, selectedSubjectId) {
        onHeaderActionsChange(
            listOf(
                HeaderAction(
                    icon = Icons.Default.Refresh,
                    enabled = refreshEnabled,
                    contentDescription = "Refrescar reservas eventuales",
                    onClick = { retryInitialLoad() }
                )
            )
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            cancelPageLoad()
            onHeaderActionsChange(emptyList())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        ReservationFilterField(
            label = "Aula",
            placeholder = "Buscar aula...",
            query = classroomQuery,
            selectedOption = selectedClassroom,
            options = filteredClassrooms,
            expanded = classroomExpanded,
            loading = filterOptionsLoading,
            unavailable = filterOptionsUnavailable && classroomOptions.isEmpty(),
            enabled = !retryingInitialLoad && !filterOptionsLoading,
            onQueryChange = { query -> classroomQuery = query },
            onExpandedChange = { expanded ->
                classroomQuery = if (expanded) "" else selectedClassroom?.label.orEmpty()
                classroomExpanded = expanded
            },
            onOptionSelected = { option ->
                selectedClassroomId = option.id
                selectedClassroomLabel = option.label
                classroomQuery = option.label
                classroomExpanded = false
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
        )

        Spacer(Modifier.height(8.dp))

        ReservationFilterField(
            label = "Materia",
            placeholder = "Buscar materia...",
            query = subjectQuery,
            selectedOption = selectedSubject,
            options = filteredSubjects,
            expanded = subjectExpanded,
            loading = filterOptionsLoading,
            unavailable = filterOptionsUnavailable && subjectOptions.isEmpty(),
            enabled = !retryingInitialLoad && !filterOptionsLoading,
            onQueryChange = { query -> subjectQuery = query },
            onExpandedChange = { expanded ->
                subjectQuery = if (expanded) "" else selectedSubject?.label.orEmpty()
                subjectExpanded = expanded
            },
            onOptionSelected = { option ->
                selectedSubjectId = option.id
                selectedSubjectLabel = option.label
                subjectQuery = option.label
                subjectExpanded = false
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
        )

        if (selectedClassroom != null || selectedSubject != null) {
            TextButton(
                onClick = {
                    selectedClassroomId = null
                    selectedClassroomLabel = ""
                    selectedSubjectId = null
                    selectedSubjectLabel = ""
                    classroomQuery = ""
                    subjectQuery = ""
                    classroomExpanded = false
                    subjectExpanded = false
                },
                enabled = !retryingInitialLoad,
            ) {
                Text("Quitar filtros")
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }

        if (initialIssueMessage != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = initialIssueMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (initialIssueIsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { retryInitialLoad() },
                    enabled = !filterOptionsLoading &&
                        !loadingInitial &&
                        !loadingMore &&
                        !retryingInitialLoad
                ) {
                    Text("Reintentar")
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (reservationsSnapshotResolved && initialError == null) {
            Text(
                text = if (totalCountForDisplay == 1) {
                    "1 reserva eventual"
                } else {
                    "$totalCountForDisplay reservas eventuales"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }

        LoadingContentBox(
            phase = loadingState.phase,
            initialText = "Cargando reservas eventuales…",
            refreshContentDescription = "Actualizando reservas eventuales",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                initialError != null -> Unit

                reservationsForDisplay.isEmpty() -> EventualReservationsMessage(
                    title = "Sin reservas",
                    body = "No se encontraron reservas eventuales con los filtros seleccionados."
                )

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(reservationsForDisplay, key = EventualReservation::id) { reservation ->
                            EventualReservationCard(reservation)
                        }

                        item(key = "pagination") {
                            when {
                                loadingState.showPaginationIndicator -> PaginationLoadingIndicator(
                                    text = "Cargando más reservas eventuales…",
                                )

                                showPaginationError -> EventualReservationsMessage(
                                    body = userFacingError(
                                        operation = "cargar más reservas eventuales",
                                        error = paginationError
                                    ),
                                    isError = true,
                                    actionLabel = "Reintentar",
                                    onAction = {
                                        loadPage(reset = false, forceRefresh = true)
                                    }
                                )

                                nextPageForDisplay == null -> Text(
                                    text = "No hay más reservas para mostrar.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                )
                            }
                        }
                    }

                    ScrollMoreHint(
                        listState = listState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
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
    query: String,
    selectedOption: EventualReservationFilterOption?,
    options: List<EventualReservationFilterOption>,
    expanded: Boolean,
    loading: Boolean,
    unavailable: Boolean,
    enabled: Boolean,
    onQueryChange: (String) -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onOptionSelected: (EventualReservationFilterOption) -> Unit,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) onExpandedChange(it) },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { if (enabled) onQueryChange(it) },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = enabled)
                .fillMaxWidth(),
            enabled = enabled,
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.heightIn(max = 320.dp)
        ) {
            when {
                loading && options.isEmpty() -> DropdownMenuItem(
                    text = { Text("Cargando opciones…") },
                    onClick = {},
                    enabled = false
                )

                unavailable -> DropdownMenuItem(
                    text = { Text("Opciones no disponibles") },
                    onClick = {},
                    enabled = false,
                )

                options.isEmpty() -> DropdownMenuItem(
                    text = { Text("Sin resultados") },
                    onClick = {},
                    enabled = false
                )

                else -> {
                    options.take(MAX_VISIBLE_FILTER_OPTIONS).forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = { onOptionSelected(option) }
                        )
                    }
                    if (options.size > MAX_VISIBLE_FILTER_OPTIONS && selectedOption == null) {
                        DropdownMenuItem(
                            text = { Text("Escribí más para refinar la búsqueda") },
                            onClick = {},
                            enabled = false
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
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = reservation.classroomName.ifBlank { "Aula sin informar" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = reservation.subjectName.ifBlank { "Materia sin informar" },
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = reservation.date.ifBlank { "Fecha sin informar" },
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = reservation.timeRange,
                style = MaterialTheme.typography.bodyMedium
            )
            if (reservation.reason.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Motivo: ${reservation.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (reservation.teacherName.isNotBlank()) {
                Text(
                    text = "Docente: ${reservation.teacherName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
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
                textAlign = TextAlign.Center
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
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

private val EventualReservation.timeRange: String
    get() = when {
        startTime.isNotBlank() && endTime.isNotBlank() -> "$startTime a $endTime"
        startTime.isNotBlank() -> "Desde $startTime"
        endTime.isNotBlank() -> "Hasta $endTime"
        else -> "Horario sin informar"
    }

private fun filterOptions(
    options: List<EventualReservationFilterOption>,
    query: String,
): List<EventualReservationFilterOption> {
    val normalizedQuery = query.normalizeForSearch()
    return if (normalizedQuery.isBlank()) {
        options
    } else {
        options.filter { it.label.normalizeForSearch().contains(normalizedQuery) }
    }
}

internal fun <T> MutableMap<EventualReservationPageKey, T>
    .invalidateFollowingPagesAfterEventualReservationRefresh(
        classroomId: Int?,
        subjectId: Int?,
        reset: Boolean,
        forceRefresh: Boolean,
        cacheIsFresh: Boolean,
        refreshSucceeded: Boolean,
    ) {
        if (
            refreshSucceeded &&
            shouldInvalidateEventualReservationPages(reset, forceRefresh, cacheIsFresh)
        ) {
            invalidateFollowingPagesForFilters(classroomId, subjectId)
        }
    }

internal fun shouldUseCachedEventualReservationsFallback(
    reset: Boolean,
    cachedPage: CachedValue<EventualReservationsPage>?,
): Boolean = reset && cachedPage != null

private fun Throwable.debugSummary(): String {
    val type = this::class.java.simpleName.ifBlank { "Error" }
    val detail = message?.trim().orEmpty()
    return if (detail.isEmpty()) type else "$type: $detail"
}

private const val MAX_VISIBLE_FILTER_OPTIONS = 50
