/** Coordina filtros, páginas, caché temporal y restauración de reservas eventuales. */
package com.overcoders.unlpcarteleranotifier.ui.reservas

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
import com.overcoders.unlpcarteleranotifier.data.CachedValue
import com.overcoders.unlpcarteleranotifier.data.ContentCachePolicy
import com.overcoders.unlpcarteleranotifier.data.EventualReservationFiltersCacheStore
import com.overcoders.unlpcarteleranotifier.data.EventualReservationsService
import com.overcoders.unlpcarteleranotifier.model.EventualReservation
import com.overcoders.unlpcarteleranotifier.model.EventualReservationFilterOption
import com.overcoders.unlpcarteleranotifier.model.EventualReservationsPage
import com.overcoders.unlpcarteleranotifier.ui.common.PaginatedLoadingState
import com.overcoders.unlpcarteleranotifier.ui.common.cachedContentWarning
import com.overcoders.unlpcarteleranotifier.ui.common.paginatedLoadingState
import com.overcoders.unlpcarteleranotifier.ui.common.partialContentWarning
import com.overcoders.unlpcarteleranotifier.ui.common.userFacingError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class EventualReservationFilterUiState(
    val query: String,
    val selectedOption: EventualReservationFilterOption?,
    val options: List<EventualReservationFilterOption>,
    val expanded: Boolean,
    val loading: Boolean,
    val unavailable: Boolean,
    val enabled: Boolean,
)

internal data class EventualReservationsUiState(
    val classroomFilter: EventualReservationFilterUiState,
    val subjectFilter: EventualReservationFilterUiState,
    val showClearFilters: Boolean,
    val clearFiltersEnabled: Boolean,
    val visibleInitialWarning: String?,
    val totalCount: Int?,
    val loadingState: PaginatedLoadingState,
    val initialFailureMessage: String?,
    val reservations: List<EventualReservation>,
    val paginationErrorMessage: String?,
    val hasNextPage: Boolean,
)

internal data class EventualReservationsListPosition(
    val index: Int,
    val scrollOffset: Int,
)

private data class EventualReservationsRefreshBackup(
    val filterKey: String,
    val reservations: List<EventualReservation>,
    val totalCount: Int,
    val nextPage: Int?,
    val lastAppliedPage: Int,
)

@Stable
internal class EventualReservationsState(
    private val context: Context,
    private val scope: CoroutineScope,
    private val service: EventualReservationsService,
    private val classroomFilter: EventualReservationFilterState,
    private val subjectFilter: EventualReservationFilterState,
    private val paginationRestorationFilterKeyState: MutableState<String>,
    private val paginationRestorationTargetPageState: MutableIntState,
    private val savedListIndexState: MutableIntState,
    private val savedListScrollOffsetState: MutableIntState,
) {
    private val pageCache =
        mutableMapOf<EventualReservationPageKey, CachedValue<EventualReservationsPage>>()

    private var filterOptionsLoading by mutableStateOf(false)
    private var filterOptionsError by mutableStateOf<Throwable?>(null)
    private var filterOptionsWarning by mutableStateOf<Throwable?>(null)
    private var classroomOptions by
        mutableStateOf<List<EventualReservationFilterOption>>(emptyList())
    private var subjectOptions by
        mutableStateOf<List<EventualReservationFilterOption>>(emptyList())
    private var filterOptionsSnapshotResolved by mutableStateOf(false)

    private var reservations by mutableStateOf<List<EventualReservation>>(emptyList())
    private var totalCount by mutableIntStateOf(0)
    internal var nextPage by mutableStateOf<Int?>(null)
        private set
    internal var loadingInitial by mutableStateOf(false)
        private set
    internal var loadingMore by mutableStateOf(false)
        private set
    internal var initialError by mutableStateOf<Throwable?>(null)
        private set
    internal var refreshWarning by mutableStateOf<Throwable?>(null)
        private set
    internal var paginationError by mutableStateOf<Throwable?>(null)
        private set
    private var paginationRetryPage by mutableStateOf<Int?>(null)
    private var retryingInitialLoad by mutableStateOf(false)
    private var loadJob by mutableStateOf<Job?>(null)
    internal var lastAppliedPage by mutableIntStateOf(0)
        private set
    private var refreshBackup by mutableStateOf<EventualReservationsRefreshBackup?>(null)
    private var resolvedReservationsFilterKey by mutableStateOf<String?>(null)

    internal var listPositionRestorationPending by mutableStateOf(
        paginationRestorationTargetPage > 1 || savedListIndex > 0
    )
        private set

    internal val selectedClassroomId: Int?
        get() = classroomFilter.selectedId

    internal val selectedSubjectId: Int?
        get() = subjectFilter.selectedId

    internal val activePaginationFilterKey: String
        get() = "$selectedClassroomId|$selectedSubjectId"

    internal val paginationRestorationFilterKey: String
        get() = paginationRestorationFilterKeyState.value

    internal val paginationRestorationTargetPage: Int
        get() = paginationRestorationTargetPageState.intValue

    private val savedListIndex: Int
        get() = savedListIndexState.intValue

    private val savedListScrollOffset: Int
        get() = savedListScrollOffsetState.intValue

    internal val hasReservations: Boolean
        get() = reservations.isNotEmpty()

    internal val refreshEnabled: Boolean
        get() = !loadingInitial &&
            !loadingMore &&
            !filterOptionsLoading &&
            !retryingInitialLoad

    internal val canContinueListRestoration: Boolean
        get() = listPositionRestorationPending &&
            paginationRestorationFilterKey == activePaginationFilterKey &&
            lastAppliedPage < paginationRestorationTargetPage &&
            nextPage != null &&
            !loadingInitial &&
            !loadingMore &&
            initialError == null &&
            refreshWarning == null &&
            paginationError == null

    internal val uiState: EventualReservationsUiState
        get() {
            val selectedClassroom = classroomFilter.selectedOption(classroomOptions)
            val selectedSubject = subjectFilter.selectedOption(subjectOptions)
            val initialFailureMessage = initialFailureMessage()
            val initialWarningMessage = initialWarningMessage()
            val reservationsForDisplay = refreshBackup?.reservations ?: reservations
            val totalCountForDisplay = refreshBackup?.totalCount ?: totalCount
            val nextPageForDisplay = refreshBackup?.nextPage ?: nextPage
            val reservationsSnapshotResolved =
                resolvedReservationsFilterKey == activePaginationFilterKey
            val filterOptionsUnavailable =
                !filterOptionsLoading &&
                    !filterOptionsSnapshotResolved &&
                    filterOptionsError != null
            val filtersEnabled = !retryingInitialLoad && !filterOptionsLoading
            val loadingState = paginatedLoadingState(
                isContentLoading = loadingInitial,
                isAuxiliaryLoading = filterOptionsLoading,
                isLoadingMore = loadingMore,
                resolvedKey = resolvedReservationsFilterKey,
                requestedKey = activePaginationFilterKey,
                hasError = initialError != null &&
                    paginationRestorationFilterKey == activePaginationFilterKey,
            )

            return EventualReservationsUiState(
                classroomFilter = EventualReservationFilterUiState(
                    query = classroomFilter.query,
                    selectedOption = selectedClassroom,
                    options = classroomFilter.filteredOptions(classroomOptions),
                    expanded = classroomFilter.expanded,
                    loading = filterOptionsLoading,
                    unavailable = filterOptionsUnavailable && classroomOptions.isEmpty(),
                    enabled = filtersEnabled,
                ),
                subjectFilter = EventualReservationFilterUiState(
                    query = subjectFilter.query,
                    selectedOption = selectedSubject,
                    options = subjectFilter.filteredOptions(subjectOptions),
                    expanded = subjectFilter.expanded,
                    loading = filterOptionsLoading,
                    unavailable = filterOptionsUnavailable && subjectOptions.isEmpty(),
                    enabled = filtersEnabled,
                ),
                showClearFilters = selectedClassroom != null || selectedSubject != null,
                clearFiltersEnabled = !retryingInitialLoad,
                visibleInitialWarning = initialWarningMessage.takeIf {
                    paginationError == null
                },
                totalCount = totalCountForDisplay.takeIf {
                    reservationsSnapshotResolved && initialFailureMessage == null
                },
                loadingState = loadingState,
                initialFailureMessage = initialFailureMessage,
                reservations = reservationsForDisplay,
                paginationErrorMessage = paginationError?.takeIf {
                    initialFailureMessage == null
                }?.let { error ->
                    userFacingError(
                        operation = "cargar más reservas eventuales",
                        error = error,
                    )
                },
                hasNextPage = nextPageForDisplay != null,
            )
        }

    internal fun updateClassroomQuery(query: String) {
        classroomFilter.updateQuery(query)
    }

    internal fun updateSubjectQuery(query: String) {
        subjectFilter.updateQuery(query)
    }

    internal fun setClassroomExpanded(expanded: Boolean) {
        classroomFilter.setExpanded(expanded, classroomOptions)
    }

    internal fun setSubjectExpanded(expanded: Boolean) {
        subjectFilter.setExpanded(expanded, subjectOptions)
    }

    internal fun selectClassroom(option: EventualReservationFilterOption) {
        classroomFilter.select(option)
    }

    internal fun selectSubject(option: EventualReservationFilterOption) {
        subjectFilter.select(option)
    }

    internal fun clearFilters() {
        classroomFilter.clear()
        subjectFilter.clear()
    }

    internal suspend fun loadFilterOptions(forceRefresh: Boolean = false) {
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
                applyFilterOptions(
                    classrooms = cached.value.classrooms,
                    subjects = cached.value.subjects,
                )
                filterOptionsSnapshotResolved = true
                if (!forceRefresh && cached.isFresh(ContentCachePolicy.CATALOG_TTL_MILLIS)) {
                    return
                }
            }

            val options = service.fetchFilterOptions()
            applyFilterOptions(options.classrooms, options.subjects)
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

    internal fun cancelPageLoad() {
        val activeJob = loadJob
        loadJob = null
        activeJob?.cancel()
        loadingInitial = false
        loadingMore = false
    }

    internal fun loadPage(reset: Boolean, forceRefresh: Boolean = false) {
        if (!reset && (loadingInitial || loadingMore)) return
        if (reset) cancelPageLoad()

        val requestedPage = if (reset) 1 else paginationRetryPage ?: nextPage ?: return
        val requestedClassroomId = selectedClassroomId
        val requestedSubjectId = selectedSubjectId
        val requestedFilterKey = "$requestedClassroomId|$requestedSubjectId"
        val preservesVisibleSnapshot = reset &&
            forceRefresh &&
            requestedFilterKey == paginationRestorationFilterKey &&
            resolvedReservationsFilterKey == requestedFilterKey
        if (reset) {
            prepareRestorationForReset(requestedFilterKey, preservesVisibleSnapshot)
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
            page = requestedPage,
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
            paginationRestorationFilterKeyState.value = requestedFilterKey
            resolvedReservationsFilterKey = requestedFilterKey
            paginationRestorationTargetPageState.intValue = maxOf(
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
                    subjectId = requestedSubjectId,
                )
                if (
                    selectedClassroomId != requestedClassroomId ||
                    selectedSubjectId != requestedSubjectId
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
                    selectedClassroomId != requestedClassroomId ||
                    selectedSubjectId != requestedSubjectId
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
                } else if (
                    preservesVisibleSnapshot ||
                    shouldUseCachedEventualReservationsFallback(reset, cached)
                ) {
                    refreshWarning = e
                } else if (reset) {
                    initialError = e
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
                    refreshSucceeded = refreshSucceeded,
                )
                if (loadJob === currentJob) {
                    loadingInitial = false
                    loadingMore = false
                    loadJob = null
                }
            }
        }
    }

    internal fun retryInitialLoad() {
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

    internal fun recordListPosition(index: Int, scrollOffset: Int) {
        if (!listPositionRestorationPending) {
            savedListIndexState.intValue = index
            savedListScrollOffsetState.intValue = scrollOffset
        }
    }

    internal fun listPositionToRestore(): EventualReservationsListPosition? {
        val restorationReachedEnd =
            lastAppliedPage >= paginationRestorationTargetPage || nextPage == null
        if (
            !listPositionRestorationPending ||
            paginationRestorationFilterKey != activePaginationFilterKey ||
            !restorationReachedEnd ||
            loadingInitial ||
            loadingMore ||
            initialError != null ||
            refreshWarning != null ||
            paginationError != null
        ) {
            return null
        }

        if (nextPage == null) {
            paginationRestorationTargetPageState.intValue = lastAppliedPage.coerceAtLeast(1)
        }
        val maximumIndex = (reservations.size - 1).coerceAtLeast(0)
        return EventualReservationsListPosition(
            index = savedListIndex.coerceIn(0, maximumIndex),
            scrollOffset = savedListScrollOffset,
        )
    }

    internal fun completeListPositionRestoration() {
        listPositionRestorationPending = false
    }

    internal fun shouldLoadNextPage(nearListEnd: Boolean): Boolean =
        nearListEnd &&
            nextPage != null &&
            !listPositionRestorationPending &&
            !loadingInitial &&
            !loadingMore &&
            paginationError == null &&
            initialError == null &&
            refreshWarning == null

    internal fun dispose() {
        cancelPageLoad()
    }

    private fun applyFilterOptions(
        classrooms: List<EventualReservationFilterOption>,
        subjects: List<EventualReservationFilterOption>,
    ) {
        classroomOptions = classrooms
        subjectOptions = subjects
        classroomFilter.reconcileSelection(classrooms)
        subjectFilter.reconcileSelection(subjects)
    }

    private fun prepareRestorationForReset(
        requestedFilterKey: String,
        preservesVisibleSnapshot: Boolean,
    ) {
        if (paginationRestorationFilterKey != requestedFilterKey) {
            refreshBackup = null
            lastAppliedPage = 0
            paginationRestorationFilterKeyState.value = requestedFilterKey
            paginationRestorationTargetPageState.intValue = 1
            savedListIndexState.intValue = 0
            savedListScrollOffsetState.intValue = 0
            listPositionRestorationPending = false
        } else if (
            !preservesVisibleSnapshot &&
            (paginationRestorationTargetPage > 1 || savedListIndex > 0)
        ) {
            lastAppliedPage = 0
            listPositionRestorationPending = true
        }
    }

    private fun initialFailureMessage(): String? = when {
        initialError != null && filterOptionsError != null -> userFacingError(
            operation = "cargar los filtros y las reservas eventuales",
            error = initialError,
            detail = "Filtros: ${filterOptionsError?.debugSummary().orEmpty()}",
        )

        initialError != null -> userFacingError(
            operation = "cargar las reservas eventuales",
            error = initialError,
        )

        filterOptionsError != null -> userFacingError(
            operation = "cargar los filtros de reservas eventuales",
            error = filterOptionsError,
        )

        else -> null
    }

    private fun initialWarningMessage(): String? = when {
        refreshWarning != null && filterOptionsWarning != null -> partialContentWarning(
            operation = "actualizar los filtros y las reservas eventuales",
            error = refreshWarning,
            detail = "Filtros: ${filterOptionsWarning?.debugSummary().orEmpty()}",
        )

        refreshWarning != null -> cachedContentWarning(
            operation = "actualizar las reservas eventuales",
            error = refreshWarning,
        )

        filterOptionsWarning != null -> cachedContentWarning(
            operation = "actualizar los filtros de reservas eventuales",
            error = filterOptionsWarning,
        )

        else -> null
    }
}

@Composable
internal fun rememberEventualReservationsState(): EventualReservationsState {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val service = remember { EventualReservationsService() }

    val selectedClassroomIdState = rememberSaveable { mutableStateOf<Int?>(null) }
    val selectedClassroomLabelState = rememberSaveable { mutableStateOf("") }
    val selectedSubjectIdState = rememberSaveable { mutableStateOf<Int?>(null) }
    val selectedSubjectLabelState = rememberSaveable { mutableStateOf("") }
    val activeFilterKey =
        "${selectedClassroomIdState.value}|${selectedSubjectIdState.value}"
    val paginationRestorationFilterKeyState = rememberSaveable {
        mutableStateOf(activeFilterKey)
    }
    val paginationRestorationTargetPageState = rememberSaveable { mutableIntStateOf(1) }
    val savedListIndexState = rememberSaveable { mutableIntStateOf(0) }
    val savedListScrollOffsetState = rememberSaveable { mutableIntStateOf(0) }

    val classroomFilter = remember(selectedClassroomIdState, selectedClassroomLabelState) {
        EventualReservationFilterState(
            selectedIdState = selectedClassroomIdState,
            selectedLabelState = selectedClassroomLabelState,
        )
    }
    val subjectFilter = remember(selectedSubjectIdState, selectedSubjectLabelState) {
        EventualReservationFilterState(
            selectedIdState = selectedSubjectIdState,
            selectedLabelState = selectedSubjectLabelState,
        )
    }

    return remember(
        context,
        scope,
        service,
        classroomFilter,
        subjectFilter,
        paginationRestorationFilterKeyState,
        paginationRestorationTargetPageState,
        savedListIndexState,
        savedListScrollOffsetState,
    ) {
        EventualReservationsState(
            context = context,
            scope = scope,
            service = service,
            classroomFilter = classroomFilter,
            subjectFilter = subjectFilter,
            paginationRestorationFilterKeyState = paginationRestorationFilterKeyState,
            paginationRestorationTargetPageState = paginationRestorationTargetPageState,
            savedListIndexState = savedListIndexState,
            savedListScrollOffsetState = savedListScrollOffsetState,
        )
    }
}

private fun Throwable.debugSummary(): String {
    val type = this::class.java.simpleName.ifBlank { "Error" }
    val detail = message?.trim().orEmpty()
    return if (detail.isEmpty()) type else "$type: $detail"
}
