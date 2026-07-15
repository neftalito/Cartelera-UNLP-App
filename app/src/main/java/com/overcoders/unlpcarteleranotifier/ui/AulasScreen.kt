/**
 * Consulta y presenta el estado actual de ocupación de las aulas con respaldo en memoria.
 */
package com.overcoders.unlpcarteleranotifier.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.data.AulasMemoryCache
import com.overcoders.unlpcarteleranotifier.data.AulasService
import com.overcoders.unlpcarteleranotifier.data.ContentCachePolicy
import com.overcoders.unlpcarteleranotifier.model.AulaEstado
import com.overcoders.unlpcarteleranotifier.ui.common.LoadingContentBox
import com.overcoders.unlpcarteleranotifier.ui.common.cachedContentWarning
import com.overcoders.unlpcarteleranotifier.ui.common.contentLoadingPhase
import com.overcoders.unlpcarteleranotifier.ui.common.copyPlainText
import com.overcoders.unlpcarteleranotifier.ui.common.sharePlainText
import com.overcoders.unlpcarteleranotifier.ui.common.userFacingError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

@Composable
fun AulasScreen(
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aulasService = remember { AulasService() }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var warning by remember { mutableStateOf<String?>(null) }
    var aulas by remember { mutableStateOf<List<AulaEstado>>(emptyList()) }
    var hasResolvedAulasSnapshot by remember { mutableStateOf(false) }
    val aulasListState = rememberLazyListState()
    val loadMutex = remember { Mutex() }

    suspend fun loadAulas(forceRefresh: Boolean = false) {
        if (!loadMutex.tryLock()) return
        loading = true
        error = null
        warning = null
        try {
            val cached = AulasMemoryCache.load()
            val hasCachedSnapshot = cached != null
            if (cached != null) {
                aulas = cached.value
                hasResolvedAulasSnapshot = true
                if (!forceRefresh && cached.isFresh(ContentCachePolicy.AULAS_MEMORY_TTL_MILLIS)) {
                    return
                }
            }

            try {
                aulas = aulasService.fetchEstadoActual()
                hasResolvedAulasSnapshot = true
                AulasMemoryCache.save(aulas)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (hasCachedSnapshot) {
                    warning = cachedContentWarning(
                        operation = "actualizar el estado de aulas",
                        error = e,
                    )
                } else {
                    error = userFacingError(
                        operation = "cargar el estado de aulas",
                        error = e,
                    )
                }
            }
        } finally {
            loading = false
            loadMutex.unlock()
        }
    }

    LaunchedEffect(Unit) {
        loadAulas()
    }

    val canShareAulas = !loading && error == null && aulas.isNotEmpty()
    val loadingPhase = contentLoadingPhase(
        isLoading = loading,
        hasResolvedContent = hasResolvedAulasSnapshot,
    )

    LaunchedEffect(loading, canShareAulas, aulas) {
        onHeaderActionsChange(
            listOf(
                HeaderAction(
                    icon = Icons.Default.ContentCopy,
                    contentDescription = "Copiar estado de aulas",
                    enabled = canShareAulas,
                    onClick = {
                        copyPlainText(
                            context = context,
                            label = "Estado de aulas",
                            text = buildShareText(aulas)
                        )
                    }
                ),
                HeaderAction(
                    icon = Icons.Default.Share,
                    contentDescription = "Compartir estado de aulas",
                    enabled = canShareAulas,
                    onClick = {
                        sharePlainText(
                            context = context,
                            text = buildShareText(aulas),
                            chooserTitle = "Compartir estado de aulas"
                        )
                    }
                ),
                HeaderAction(
                    icon = Icons.Default.Refresh,
                    contentDescription = "Refrescar estado de aulas",
                    enabled = !loading,
                    onClick = { scope.launch { loadAulas(forceRefresh = true) } }
                )
            )
        )
    }

    DisposableEffect(Unit) {
        onDispose { onHeaderActionsChange(emptyList()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (warning != null) {
            Text(
                text = warning.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = { scope.launch { loadAulas(forceRefresh = true) } },
                enabled = !loading,
            ) {
                Text("Reintentar")
            }
            Spacer(Modifier.height(8.dp))
        }

        LoadingContentBox(
            phase = loadingPhase,
            initialText = "Cargando estado actual de las aulas…",
            refreshContentDescription = "Actualizando estado de aulas",
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                error != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = error.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = { scope.launch { loadAulas(forceRefresh = true) } },
                            enabled = !loading,
                        ) {
                            Text("Reintentar")
                        }
                    }
                }

                aulas.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No hay aulas ocupadas en este momento.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                else -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = aulasListState,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(aulas) { aula ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = aulaDisplayName(aula),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = aula.materia,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "${aula.horaDesde} a ${aula.horaHasta}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        ScrollMoreHint(
                            listState = aulasListState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun buildShareText(aulas: List<AulaEstado>): String {
    if (aulas.isEmpty()) {
        return "Estado actual de aulas\n\nNo hay aulas ocupadas en este momento."
    }

    val lines = aulas
        .sortedWith(compareBy(AulaEstado::aulaNombre, AulaEstado::aulaId, AulaEstado::horaDesde))
        .map { aula ->
            "• ${aulaDisplayName(aula)}: ${aula.materia} (${aula.horaDesde} a ${aula.horaHasta})"
        }

    return buildString {
        appendLine("Estado actual de aulas")
        appendLine()
        append(lines.joinToString("\n"))
    }.trim()
}

private fun aulaDisplayName(aula: AulaEstado): String =
    listOf(aula.aulaNombre, aula.aulaId)
        .filter(String::isNotBlank)
        .joinToString(" - ")
