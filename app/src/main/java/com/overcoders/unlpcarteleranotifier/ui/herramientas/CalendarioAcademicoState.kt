/** Conserva la selección y coordina la carga del calendario académico. */
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
import com.overcoders.unlpcarteleranotifier.data.CalendarioAcademicoService
import com.overcoders.unlpcarteleranotifier.data.CalendarioCacheStore
import com.overcoders.unlpcarteleranotifier.data.ContentCachePolicy
import com.overcoders.unlpcarteleranotifier.data.NotFoundCacheStore
import com.overcoders.unlpcarteleranotifier.model.CalendarioAcademico
import com.overcoders.unlpcarteleranotifier.ui.common.cachedContentWarning
import com.overcoders.unlpcarteleranotifier.ui.common.userFacingError
import java.time.Year
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal const val FIRST_CALENDAR_YEAR = 2018

internal fun calendarYears(currentYear: Int): List<Int> =
    (maxOf(currentYear, FIRST_CALENDAR_YEAR) downTo FIRST_CALENDAR_YEAR).toList()

internal data class CalendarioAcademicoUiState(
    val years: List<Int>,
    val selectedYear: Int,
    val dropdownExpanded: Boolean,
    val isFullscreen: Boolean,
    val loading: Boolean,
    val error: String?,
    val warning: String?,
    val unavailableYear: Int?,
    val calendario: CalendarioAcademico?,
    val resolvedSelectedYear: Int?,
)

@Stable
internal class CalendarioAcademicoState(
    private val context: Context,
    private val service: CalendarioAcademicoService,
    private val scope: CoroutineScope,
    val currentYear: Int,
    val years: List<Int>,
    selectedYearState: MutableIntState,
    isFullscreenState: MutableState<Boolean>,
) {
    var selectedYear by selectedYearState
        private set
    var isFullscreen by isFullscreenState
        private set
    var dropdownExpanded by mutableStateOf(false)
        private set
    var loading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var warning by mutableStateOf<String?>(null)
        private set
    private var unavailableYear by mutableStateOf<Int?>(null)
    private var calendario by mutableStateOf<CalendarioAcademico?>(null)
    private var resolvedYear by mutableStateOf<Int?>(null)
    private var loadJob: Job? = null

    val uiState: CalendarioAcademicoUiState
        get() {
            val hasResolvedSelectedYear = resolvedYear == selectedYear && (
                calendario?.anio == selectedYear || unavailableYear == selectedYear
            )
            return CalendarioAcademicoUiState(
                years = years,
                selectedYear = selectedYear,
                dropdownExpanded = dropdownExpanded,
                isFullscreen = isFullscreen,
                loading = loading,
                error = error,
                warning = warning,
                unavailableYear = unavailableYear,
                calendario = calendario,
                resolvedSelectedYear = resolvedYear.takeIf { hasResolvedSelectedYear },
            )
        }

    val hasCalendar: Boolean
        get() = calendario != null

    fun updateDropdownExpanded(expanded: Boolean) {
        dropdownExpanded = expanded
    }

    fun selectYear(year: Int) {
        selectedYear = year
        dropdownExpanded = false
    }

    fun toggleFullscreen() {
        dropdownExpanded = false
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

    fun loadCalendario(anio: Int, forceRefresh: Boolean = false) {
        val preservesResolvedState = resolvedYear == anio
        val previousJob = loadJob
        loadJob = null
        previousJob?.cancel()

        if (calendario?.anio != anio) {
            calendario = null
        }
        if (!preservesResolvedState) {
            resolvedYear = null
            unavailableYear = null
        }

        loading = true
        error = null
        warning = null

        loadJob = scope.launch {
            val currentJob = coroutineContext[Job]

            try {
                val requestUrl = service.buildUrl(anio)
                if (forceRefresh) {
                    NotFoundCacheStore.clear(context, requestUrl)
                }
                val cached = CalendarioCacheStore.load(context, anio)
                if (cached != null) {
                    calendario = cached.value
                    unavailableYear = null
                    resolvedYear = anio
                    val ttl = if (anio == currentYear) {
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
                    calendario = null
                    unavailableYear = anio
                    resolvedYear = anio
                    return@launch
                }

                val fetched = service.fetch(anio)
                calendario = fetched
                unavailableYear = null
                resolvedYear = anio
                try {
                    CalendarioCacheStore.save(context, fetched)
                    NotFoundCacheStore.clear(context, fetched.url)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // La caché es una optimización: un fallo de escritura no invalida la respuesta remota.
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val hasResolvedCalendar = resolvedYear == anio && calendario?.anio == anio
                val hasResolvedUnavailableState = resolvedYear == anio &&
                    calendario == null && unavailableYear == anio
                if (hasResolvedCalendar) {
                    warning = cachedContentWarning(
                        operation = "Actualizar el calendario académico",
                        error = e,
                    )
                    return@launch
                }
                if (e is ApiException && e.httpCode == 404) {
                    calendario = null
                    unavailableYear = anio
                    resolvedYear = anio
                    NotFoundCacheStore.mark(context, service.buildUrl(anio))
                } else if (hasResolvedUnavailableState) {
                    warning = cachedContentWarning(
                        operation = "Actualizar el calendario académico",
                        error = e,
                    )
                } else {
                    error = userFacingError(
                        operation = "Cargar el calendario académico",
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
internal fun rememberCalendarioAcademicoState(): CalendarioAcademicoState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { CalendarioAcademicoService() }
    val currentYear = remember { maxOf(Year.now().value, FIRST_CALENDAR_YEAR) }
    val years = remember(currentYear) { calendarYears(currentYear) }
    val selectedYearState = rememberSaveable { mutableIntStateOf(currentYear) }
    val isFullscreenState = rememberSaveable { mutableStateOf(false) }

    return remember(
        context,
        service,
        scope,
        currentYear,
        years,
        selectedYearState,
        isFullscreenState,
    ) {
        CalendarioAcademicoState(
            context = context,
            service = service,
            scope = scope,
            currentYear = currentYear,
            years = years,
            selectedYearState = selectedYearState,
            isFullscreenState = isFullscreenState,
        )
    }
}
