/** Conserva los filtros y coordina la carga de materias optativas. */
package com.overcoders.unlpcarteleranotifier.ui.herramientas

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
import com.overcoders.unlpcarteleranotifier.data.ApiException
import com.overcoders.unlpcarteleranotifier.data.ContentCachePolicy
import com.overcoders.unlpcarteleranotifier.data.NotFoundCacheStore
import com.overcoders.unlpcarteleranotifier.data.OptativeSubjectsCacheStore
import com.overcoders.unlpcarteleranotifier.data.OptativeSubjectsService
import com.overcoders.unlpcarteleranotifier.model.OptativeCareer
import com.overcoders.unlpcarteleranotifier.model.OptativeSubjectsPage
import com.overcoders.unlpcarteleranotifier.ui.common.cachedContentWarning
import com.overcoders.unlpcarteleranotifier.ui.common.userFacingError
import java.time.Year
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal const val FIRST_OPTATIVAS_YEAR = 2022

internal fun optativeYears(currentYear: Int): List<Int> =
    (FIRST_OPTATIVAS_YEAR..maxOf(FIRST_OPTATIVAS_YEAR, currentYear))
        .toList()
        .asReversed()

internal data class OptativeSubjectsQuery(
    val year: Int,
    val career: OptativeCareer,
)

internal data class OptativeSubjectsUiState(
    val years: List<Int>,
    val selectedCareer: OptativeCareer,
    val selectedYear: Int,
    val careerDropdownExpanded: Boolean,
    val yearDropdownExpanded: Boolean,
    val isFullscreen: Boolean,
    val loading: Boolean,
    val error: String?,
    val warning: String?,
    val unavailableYear: Int?,
    val page: OptativeSubjectsPage?,
    val selectedQuery: OptativeSubjectsQuery,
    val resolvedSelectedQuery: OptativeSubjectsQuery?,
)

@Stable
internal class OptativeSubjectsState(
    private val context: Context,
    private val service: OptativeSubjectsService,
    private val scope: CoroutineScope,
    val currentYear: Int,
    val years: List<Int>,
    selectedCareerState: MutableState<OptativeCareer>,
    selectedYearState: MutableIntState,
    isFullscreenState: MutableState<Boolean>,
) {
    var selectedCareer by selectedCareerState
        private set
    var selectedYear by selectedYearState
        private set
    var isFullscreen by isFullscreenState
        private set
    var careerDropdownExpanded by mutableStateOf(false)
        private set
    var yearDropdownExpanded by mutableStateOf(false)
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var warning by mutableStateOf<String?>(null)
        private set
    private var unavailableYear by mutableStateOf<Int?>(null)
    private var page by mutableStateOf<OptativeSubjectsPage?>(null)
    private var resolvedQuery by mutableStateOf<OptativeSubjectsQuery?>(null)
    private var loadJob: Job? = null

    val uiState: OptativeSubjectsUiState
        get() {
            val selectedQuery = OptativeSubjectsQuery(
                year = selectedYear,
                career = selectedCareer,
            )
            val hasResolvedSelectedQuery = resolvedQuery == selectedQuery && (
                page?.let {
                    it.year == selectedYear && it.career == selectedCareer
                } == true || unavailableYear == selectedYear
            )
            return OptativeSubjectsUiState(
                years = years,
                selectedCareer = selectedCareer,
                selectedYear = selectedYear,
                careerDropdownExpanded = careerDropdownExpanded,
                yearDropdownExpanded = yearDropdownExpanded,
                isFullscreen = isFullscreen,
                loading = loading,
                error = error,
                warning = warning,
                unavailableYear = unavailableYear,
                page = page,
                selectedQuery = selectedQuery,
                resolvedSelectedQuery = resolvedQuery.takeIf { hasResolvedSelectedQuery },
            )
        }

    val hasPage: Boolean
        get() = page != null

    fun updateCareerDropdownExpanded(expanded: Boolean) {
        careerDropdownExpanded = expanded
        if (expanded) yearDropdownExpanded = false
    }

    fun updateYearDropdownExpanded(expanded: Boolean) {
        yearDropdownExpanded = expanded
        if (expanded) careerDropdownExpanded = false
    }

    fun selectCareer(career: OptativeCareer) {
        selectedCareer = career
        careerDropdownExpanded = false
        yearDropdownExpanded = false
    }

    fun selectYear(year: Int) {
        selectedYear = year
        careerDropdownExpanded = false
        yearDropdownExpanded = false
    }

    fun toggleFullscreen() {
        careerDropdownExpanded = false
        yearDropdownExpanded = false
        isFullscreen = !isFullscreen
    }

    fun exitFullscreen() {
        isFullscreen = false
    }

    fun cancelLoad() {
        val activeJob = loadJob
        loadJob = null
        activeJob?.cancel()
        loading = false
    }

    fun loadPage(
        year: Int,
        career: OptativeCareer,
        forceRefresh: Boolean = false,
    ) {
        val requestedQuery = OptativeSubjectsQuery(year = year, career = career)
        val preservesResolvedState = resolvedQuery == requestedQuery
        val previousJob = loadJob
        loadJob = null
        previousJob?.cancel()

        if (page?.year != year || page?.career != career) {
            page = null
        }
        if (!preservesResolvedState) {
            resolvedQuery = null
            unavailableYear = null
        }

        loading = true
        error = null
        warning = null

        loadJob = scope.launch {
            val currentJob = coroutineContext[Job]

            try {
                val requestUrl = service.buildUrl(year, career)
                if (forceRefresh) {
                    NotFoundCacheStore.clear(context, requestUrl)
                }
                val cached = OptativeSubjectsCacheStore.load(context, year, career)
                if (cached != null) {
                    page = cached.value
                    unavailableYear = null
                    resolvedQuery = requestedQuery
                    val ttl = if (year == currentYear) {
                        ContentCachePolicy.CURRENT_YEAR_TTL_MILLIS
                    } else {
                        ContentCachePolicy.HISTORICAL_YEAR_TTL_MILLIS
                    }
                    if (!forceRefresh && cached.isFresh(ttl)) {
                        return@launch
                    }
                } else if (
                    !forceRefresh &&
                    NotFoundCacheStore.isFresh(context, requestUrl)
                ) {
                    page = null
                    unavailableYear = year
                    resolvedQuery = requestedQuery
                    return@launch
                }

                val fetched = service.fetch(year = year, career = career)
                page = fetched
                unavailableYear = null
                resolvedQuery = requestedQuery
                try {
                    OptativeSubjectsCacheStore.save(context, fetched)
                    NotFoundCacheStore.clear(context, fetched.url)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // La caché es una optimización: un fallo de escritura no invalida la respuesta remota.
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val hasResolvedPage = resolvedQuery == requestedQuery &&
                    page?.year == year && page?.career == career
                val hasResolvedUnavailableState = resolvedQuery == requestedQuery &&
                    page == null && unavailableYear == year
                if (hasResolvedPage) {
                    warning = cachedContentWarning(
                        operation = "Actualizar las materias optativas",
                        error = e,
                    )
                    return@launch
                }
                if (e is ApiException && e.httpCode == 404) {
                    page = null
                    unavailableYear = year
                    resolvedQuery = requestedQuery
                    NotFoundCacheStore.mark(context, service.buildUrl(year, career))
                } else if (hasResolvedUnavailableState) {
                    warning = cachedContentWarning(
                        operation = "Actualizar las materias optativas",
                        error = e,
                    )
                } else {
                    error = userFacingError(
                        operation = "Cargar las materias optativas",
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
}

@Composable
internal fun rememberOptativeSubjectsState(): OptativeSubjectsState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { OptativeSubjectsService() }
    val currentYear = remember { Year.now().value }
    val years = remember(currentYear) { optativeYears(currentYear) }
    val selectedCareerState = rememberSaveable { mutableStateOf(OptativeCareer.SISTEMAS) }
    val selectedYearState = rememberSaveable {
        mutableIntStateOf(years.firstOrNull() ?: currentYear)
    }
    val isFullscreenState = rememberSaveable { mutableStateOf(false) }

    return remember(
        context,
        service,
        scope,
        currentYear,
        years,
        selectedCareerState,
        selectedYearState,
        isFullscreenState,
    ) {
        OptativeSubjectsState(
            context = context,
            service = service,
            scope = scope,
            currentYear = currentYear,
            years = years,
            selectedCareerState = selectedCareerState,
            selectedYearState = selectedYearState,
            isFullscreenState = isFullscreenState,
        )
    }
}
