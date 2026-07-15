/**
 * Consulta y presenta las tablas de materias optativas por carrera y año.
 */
package com.overcoders.unlpcarteleranotifier.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.overcoders.unlpcarteleranotifier.data.NotFoundCacheStore
import com.overcoders.unlpcarteleranotifier.data.OptativeSubjectsCacheStore
import com.overcoders.unlpcarteleranotifier.data.OptativeSubjectsService
import com.overcoders.unlpcarteleranotifier.model.OptativeCareer
import com.overcoders.unlpcarteleranotifier.model.OptativeSubjectsPage
import com.overcoders.unlpcarteleranotifier.ui.common.HtmlWebView
import com.overcoders.unlpcarteleranotifier.ui.common.LoadingContentBox
import com.overcoders.unlpcarteleranotifier.ui.common.cachedContentWarning
import com.overcoders.unlpcarteleranotifier.ui.common.keyedContentLoadingPhase
import com.overcoders.unlpcarteleranotifier.ui.common.userFacingError
import java.time.Year
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal const val FIRST_OPTATIVAS_YEAR = 2022

internal fun optativeYears(currentYear: Int): List<Int> =
    (FIRST_OPTATIVAS_YEAR..maxOf(FIRST_OPTATIVAS_YEAR, currentYear))
        .toList()
        .asReversed()

private data class OptativeSubjectsQuery(
    val year: Int,
    val career: OptativeCareer,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptativeSubjectsScreen(
    onFullscreenDetailChange: (Boolean) -> Unit = {},
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val service = remember { OptativeSubjectsService() }
    val currentYear = remember { Year.now().value }
    val years = remember(currentYear) {
        optativeYears(currentYear)
    }

    var selectedCareer by rememberSaveable { mutableStateOf(OptativeCareer.SISTEMAS) }
    var selectedYear by rememberSaveable { mutableIntStateOf(years.firstOrNull() ?: currentYear) }
    var careerDropdownExpanded by remember { mutableStateOf(false) }
    var yearDropdownExpanded by remember { mutableStateOf(false) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var warning by remember { mutableStateOf<String?>(null) }
    var unavailableYear by remember { mutableStateOf<Int?>(null) }
    var page by remember { mutableStateOf<OptativeSubjectsPage?>(null) }
    var resolvedQuery by remember { mutableStateOf<OptativeSubjectsQuery?>(null) }
    var loadJob by remember { mutableStateOf<Job?>(null) }

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

    LaunchedEffect(selectedCareer, selectedYear) {
        loadPage(year = selectedYear, career = selectedCareer)
    }

    val showFullscreenAction = isFullscreen || page != null
    val headerActions = remember(
        isFullscreen,
        showFullscreenAction,
        loading,
        selectedYear,
        selectedCareer
    ) {
        buildList {
            add(
                HeaderAction(
                    icon = Icons.Default.Refresh,
                    enabled = !loading,
                    contentDescription = "Refrescar materias optativas",
                    onClick = {
                        loadPage(
                            year = selectedYear,
                            career = selectedCareer,
                            forceRefresh = true
                        )
                    }
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
                            yearDropdownExpanded = false
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
                OptativeCareerSelector(
                    selectedCareer = selectedCareer,
                    expanded = careerDropdownExpanded,
                    onExpandedChange = { expanded ->
                        careerDropdownExpanded = expanded
                        if (expanded) yearDropdownExpanded = false
                    },
                    onCareerSelected = { career ->
                        selectedCareer = career
                        careerDropdownExpanded = false
                        yearDropdownExpanded = false
                    },
                    modifier = Modifier.weight(3f)
                )

                Spacer(Modifier.width(8.dp))

                OptativeYearSelector(
                    years = years,
                    selectedYear = selectedYear,
                    expanded = yearDropdownExpanded,
                    onExpandedChange = { expanded ->
                        yearDropdownExpanded = expanded
                        if (expanded) careerDropdownExpanded = false
                    },
                    onYearSelected = { year ->
                        selectedYear = year
                        careerDropdownExpanded = false
                        yearDropdownExpanded = false
                    },
                    modifier = Modifier.weight(2f)
                )
            }

            Spacer(Modifier.height(12.dp))
        }

        if (warning != null) {
            Text(
                text = warning.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = if (isFullscreen) 16.dp else 0.dp),
            )
            Spacer(Modifier.height(8.dp))
        }

        val selectedQuery = OptativeSubjectsQuery(
            year = selectedYear,
            career = selectedCareer,
        )
        val hasResolvedSelectedQuery = resolvedQuery == selectedQuery && (
            page?.let { it.year == selectedYear && it.career == selectedCareer } == true ||
                unavailableYear == selectedYear
        )
        val resolvedSelectedQuery = resolvedQuery.takeIf { hasResolvedSelectedQuery }

        LoadingContentBox(
            phase = keyedContentLoadingPhase(
                isLoading = loading,
                resolvedKey = resolvedSelectedQuery,
                requestedKey = selectedQuery,
                hasError = error != null,
            ),
            initialText = "Cargando materias optativas…",
            refreshContentDescription = "Actualizando materias optativas",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                page != null -> {
                    HtmlWebView(
                        html = page!!.contentHtml,
                        baseUrl = page!!.url,
                        fitContentWidth = true,
                        showScrollMoreHint = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                unavailableYear != null -> {
                    OptativeStateMessage(
                        title = "Optativas no disponibles",
                        body = "No se encontró una tabla de materias optativas para ${selectedCareer.displayName} en $unavailableYear."
                    )
                }

                error != null -> {
                    OptativeStateMessage(
                        title = null,
                        body = error.orEmpty(),
                        isError = true,
                        onRetry = {
                            loadPage(
                                year = selectedYear,
                                career = selectedCareer,
                                forceRefresh = true,
                            )
                        },
                    )
                }

                else -> {
                    OptativeStateMessage(
                        title = "Sin contenido",
                        body = "No hay una tabla de materias optativas para mostrar."
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptativeCareerSelector(
    selectedCareer: OptativeCareer,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCareerSelected: (OptativeCareer) -> Unit,
    modifier: Modifier = Modifier,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedCareer.shortName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Carrera") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            OptativeCareer.entries.forEach { career ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(career.shortName)
                            Text(
                                text = career.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = { onCareerSelected(career) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptativeYearSelector(
    years: List<Int>,
    selectedYear: Int,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onYearSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedYear.toString(),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Año") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            years.forEach { year ->
                DropdownMenuItem(
                    text = { Text(year.toString()) },
                    onClick = { onYearSelected(year) }
                )
            }
        }
    }
}

@Composable
private fun OptativeStateMessage(
    title: String?,
    body: String,
    isError: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
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
            textAlign = TextAlign.Center
        )
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Reintentar")
            }
        }
    }
}
