package com.overcoders.unlpcarteleranotifier.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.data.CursadasStore
import com.overcoders.unlpcarteleranotifier.model.CursadaInfo
import com.overcoders.unlpcarteleranotifier.model.CursadaNotificationTarget
import com.overcoders.unlpcarteleranotifier.worker.CursadasNotificationDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
fun CursadasScreen(
    initialSelected: CursadaInfo? = null,
    initialTarget: CursadaNotificationTarget? = null,
    onInitialSelectedConsumed: () -> Unit = {},
    onInitialTargetConsumed: () -> Unit = {},
    onTitleChange: (String?) -> Unit = {},
    onFullscreenDetailChange: (Boolean) -> Unit = {},
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<CursadaInfo?>(null) }
    var cursadas by remember { mutableStateOf<List<CursadaInfo>>(emptyList()) }
    var cursadasConNovedades by remember { mutableStateOf<Set<String>>(emptySet()) }
    var initialLoadCompleted by remember { mutableStateOf(false) }
    var consumedInitialSelection by remember { mutableStateOf<CursadaInfo?>(null) }
    var pendingTarget by remember { mutableStateOf<CursadaNotificationTarget?>(null) }
    val listState = rememberLazyListState()

    suspend fun refresh() {
        loading = true
        error = null
        try {
            cursadas = withContext(Dispatchers.IO) {
                CursadasNotificationDispatcher.process(context)
            }

            val vistosPorMateria = withContext(Dispatchers.IO) {
                CursadasStore.ensureSeenBaseline(context, cursadas)
            }

            cursadasConNovedades = cursadas
                .filter { cursada ->
                    val ultimaVista = vistosPorMateria[cursada.materia] ?: Long.MIN_VALUE
                    val ultimaActualizacion = cursada.ultimaModificacionEpochMillis ?: Long.MIN_VALUE
                    ultimaActualizacion > ultimaVista
                }
                .map { it.materia }
                .toSet()
        } catch (e: Exception) {
            error = errorMessageFor(e)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(loading, cursadasConNovedades, selected) {
        if (selected != null) {
            val cursada = selected ?: return@LaunchedEffect
            val shareText = buildShareText(cursada)
            onHeaderActionsChange(
                listOf(
                    HeaderAction(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        onClick = { selected = null }
                    ),
                    HeaderAction(
                        icon = Icons.Default.ContentCopy,
                        contentDescription = "Copiar cursada",
                        onClick = {
                            copyPlainText(
                                context = context,
                                label = "Cursada",
                                text = shareText
                            )
                        }
                    ),
                    HeaderAction(
                        icon = Icons.Default.Share,
                        contentDescription = "Compartir cursada",
                        onClick = {
                            sharePlainText(
                                context = context,
                                text = shareText,
                                chooserTitle = "Compartir cursada"
                            )
                        }
                    )
                )
            )
        } else {
            onHeaderActionsChange(
                listOf(
                    HeaderAction(
                        icon = Icons.Default.DoneAll,
                        contentDescription = "Marcar todas las cursadas como vistas",
                        enabled = !loading && cursadasConNovedades.isNotEmpty(),
                        onClick = {
                            cursadasConNovedades = emptySet()
                            scope.launch(Dispatchers.IO) {
                                CursadasStore.markAllAsSeen(context, cursadas)
                            }
                        }
                    ),
                    HeaderAction(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Refrescar cursadas",
                        enabled = !loading,
                        onClick = { scope.launch { refresh() } }
                    )
                )
            )
        }
    }

    LaunchedEffect(selected) {
        onFullscreenDetailChange(selected != null)
        onTitleChange(if (selected != null) "Cursada" else null)
    }

    DisposableEffect(Unit) {
        onDispose {
            onHeaderActionsChange(emptyList())
            onTitleChange(null)
            onFullscreenDetailChange(false)
        }
    }

    LaunchedEffect(initialSelected) {
        if (initialSelected == null) {
            consumedInitialSelection = null
        }
    }

    LaunchedEffect(initialTarget) {
        if (initialTarget != null) {
            pendingTarget = initialTarget
            onInitialTargetConsumed()
        }
    }

    LaunchedEffect(initialSelected, cursadas, initialLoadCompleted) {
        val pendingSelection = initialSelected ?: return@LaunchedEffect
        if (consumedInitialSelection == pendingSelection) {
            return@LaunchedEffect
        }
        val matchingCursada = cursadas.firstOrNull { it.materia == pendingSelection.materia }

        if (matchingCursada == null && !initialLoadCompleted) {
            return@LaunchedEffect
        }

        consumedInitialSelection = pendingSelection
        val cursadaToOpen = matchingCursada ?: pendingSelection
        cursadasConNovedades = cursadasConNovedades - cursadaToOpen.materia
        withContext(Dispatchers.IO) {
            CursadasStore.markAsSeen(context, cursadaToOpen)
        }
        selected = cursadaToOpen
        onInitialSelectedConsumed()
    }

    LaunchedEffect(pendingTarget, cursadas) {
        val target = pendingTarget ?: return@LaunchedEffect
        val matchingCursada = cursadas.firstOrNull { cursada ->
            cursada.materia.trim().equals(target.materia.trim(), ignoreCase = true) &&
                (target.fechaModificacion.isBlank() ||
                    cursada.ultimaModificacion == target.fechaModificacion)
        } ?: return@LaunchedEffect
        cursadasConNovedades = cursadasConNovedades - matchingCursada.materia
        withContext(Dispatchers.IO) {
            CursadasStore.markAsSeen(context, matchingCursada)
        }
        selected = matchingCursada
        pendingTarget = null
    }

    LaunchedEffect(Unit) {
        refresh()
        initialLoadCompleted = true
    }

    if (selected != null) {
        CursadaDetailScreen(
            cursada = selected!!,
            onBack = { selected = null }
        )
        return
    }

    val showNotificationOpeningState = loading && (
        initialSelected != null || initialTarget != null || pendingTarget != null
    )

    if (showNotificationOpeningState) {
        NotificationOpeningState()
        return
    }

    LaunchedEffect(cursadas, filter) {
        listState.scrollToItem(0)
    }

    val filtered = remember(cursadas, filter) {
        val query = filter.trim().lowercase()
        val matchingCursadas = if (query.isBlank()) cursadas
        else cursadas.filter { it.materia.lowercase().contains(query) }

        matchingCursadas.sortedWith(
            compareByDescending<CursadaInfo> {
                it.ultimaModificacionEpochMillis ?: Long.MIN_VALUE
            }.thenBy { it.materia.lowercase() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            label = { Text("Filtrar por nombre de materia") },
            trailingIcon = {
                if (filter.isNotBlank()) {
                    IconButton(onClick = { filter = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Limpiar filtro"
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        if (loading) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            Spacer(Modifier.height(8.dp))
        }

        if (error != null) {
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered, key = { it.materia }) { cursada ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            cursadasConNovedades = cursadasConNovedades - cursada.materia
                            scope.launch(Dispatchers.IO) {
                                CursadasStore.markAsSeen(context, cursada)
                            }
                            selected = cursada
                        }
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = cursada.materia,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            if (cursada.materia in cursadasConNovedades) {
                                Text(
                                    text = "Nuevo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = "Última actualización: ${cursada.ultimaModificacion.ifBlank { "Sin fecha" }}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

private fun errorMessageFor(error: Throwable): String {
    return when (error) {
        is IOException -> "Hubo un error al obtener las cursadas (${error.localizedMessage ?: "Error de red"})."
        else -> "Hubo un error al obtener las cursadas."
    }
}
