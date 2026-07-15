/**
 * Consulta y presenta el calendario académico disponible para cada año.
 */
package com.overcoders.unlpcarteleranotifier.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
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
import com.overcoders.unlpcarteleranotifier.data.CalendarioCacheStore
import com.overcoders.unlpcarteleranotifier.data.CalendarioAcademicoService
import com.overcoders.unlpcarteleranotifier.data.ContentCachePolicy
import com.overcoders.unlpcarteleranotifier.data.NotFoundCacheStore
import com.overcoders.unlpcarteleranotifier.model.CalendarioAcademico
import com.overcoders.unlpcarteleranotifier.ui.common.HtmlWebView
import com.overcoders.unlpcarteleranotifier.ui.common.LoadingContentBox
import com.overcoders.unlpcarteleranotifier.ui.common.cachedContentWarning
import com.overcoders.unlpcarteleranotifier.ui.common.keyedContentLoadingPhase
import com.overcoders.unlpcarteleranotifier.ui.common.userFacingError
import java.time.Year
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val FIRST_AVAILABLE_YEAR = 2018

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarioAcademicoScreen(
    onFullscreenDetailChange: (Boolean) -> Unit = {},
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val calendarioService = remember { CalendarioAcademicoService() }
    val currentYear = remember { maxOf(Year.now().value, FIRST_AVAILABLE_YEAR) }
    val years = remember(currentYear) { (currentYear downTo FIRST_AVAILABLE_YEAR).toList() }

    var selectedYear by rememberSaveable { mutableIntStateOf(currentYear) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var warning by remember { mutableStateOf<String?>(null) }
    var unavailableYear by remember { mutableStateOf<Int?>(null) }
    var calendario by remember { mutableStateOf<CalendarioAcademico?>(null) }
    var resolvedYear by remember { mutableStateOf<Int?>(null) }
    var loadJob by remember { mutableStateOf<Job?>(null) }

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
                val requestUrl = calendarioService.buildUrl(anio)
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

                val fetched = calendarioService.fetch(anio)
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
                    NotFoundCacheStore.mark(context, calendarioService.buildUrl(anio))
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

    LaunchedEffect(selectedYear) {
        loadCalendario(selectedYear)
    }

    val showFullscreenAction = isFullscreen || calendario != null
    val headerActions = remember(isFullscreen, showFullscreenAction, loading, selectedYear) {
        buildList {
            add(
                HeaderAction(
                    icon = Icons.Default.Refresh,
                    enabled = !loading,
                    contentDescription = "Refrescar calendario académico",
                    onClick = { loadCalendario(selectedYear, forceRefresh = true) }
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
                            dropdownExpanded = false
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
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(1f)
            ) {
                OutlinedTextField(
                    value = selectedYear.toString(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Año") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    years.forEach { year ->
                        DropdownMenuItem(
                            text = { Text(year.toString()) },
                            onClick = {
                                selectedYear = year
                                dropdownExpanded = false
                            }
                        )
                    }
                }
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

        val hasResolvedSelectedYear = resolvedYear == selectedYear && (
            calendario?.anio == selectedYear || unavailableYear == selectedYear
        )
        val resolvedSelectedYear = resolvedYear.takeIf { hasResolvedSelectedYear }

        LoadingContentBox(
            phase = keyedContentLoadingPhase(
                isLoading = loading,
                resolvedKey = resolvedSelectedYear,
                requestedKey = selectedYear,
                hasError = error != null,
            ),
            initialText = "Cargando calendario académico $selectedYear…",
            refreshContentDescription = "Actualizando calendario académico $selectedYear",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                calendario != null -> {
                    HtmlWebView(
                        html = calendario!!.contenidoHtml,
                        baseUrl = calendario!!.url,
                        fitContentWidth = true,
                        showScrollMoreHint = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                unavailableYear != null -> {
                    StateMessage(
                        title = "Calendario $unavailableYear no disponible",
                        body = "El calendario del año seleccionado no está disponible."
                    )
                }

                error != null -> {
                    StateMessage(
                        title = null,
                        body = error.orEmpty(),
                        isError = true,
                        onRetry = { loadCalendario(selectedYear, forceRefresh = true) },
                    )
                }

                else -> {
                    StateMessage(
                        title = "Sin contenido",
                        body = "No se encontró información para el año seleccionado."
                    )
                }
            }
        }
    }
}

@Composable
private fun StateMessage(
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
