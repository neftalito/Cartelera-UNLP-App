/**
 * Muestra las cursadas publicadas, sus novedades y el detalle abierto desde notificaciones.
 */
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.HeaderActionPlacement
import com.overcoders.unlpcarteleranotifier.shouldInvalidatePendingNotification
import com.overcoders.unlpcarteleranotifier.shouldRestoreNotificationResolution
import com.overcoders.unlpcarteleranotifier.data.CursadasStore
import com.overcoders.unlpcarteleranotifier.data.CursadasRepository
import com.overcoders.unlpcarteleranotifier.model.CursadaInfo
import com.overcoders.unlpcarteleranotifier.model.CursadaNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.cursadaMateriaKey
import com.overcoders.unlpcarteleranotifier.push.NotificationOpenKind
import com.overcoders.unlpcarteleranotifier.ui.common.LoadingContentBox
import com.overcoders.unlpcarteleranotifier.ui.common.cachedContentWarning
import com.overcoders.unlpcarteleranotifier.ui.common.contentLoadingPhase
import com.overcoders.unlpcarteleranotifier.ui.common.copyPlainText
import com.overcoders.unlpcarteleranotifier.ui.common.normalizeForSearch
import com.overcoders.unlpcarteleranotifier.ui.common.sharePlainText
import com.overcoders.unlpcarteleranotifier.ui.common.userFacingError
import com.overcoders.unlpcarteleranotifier.ui.cursadas.CursadaDetailScreen
import com.overcoders.unlpcarteleranotifier.ui.cursadas.buildShareText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
fun CursadasScreen(
    initialTarget: CursadaNotificationTarget? = null,
    notificationOpenEventId: Long = 0L,
    activeNotificationKind: NotificationOpenKind? = null,
    onInitialTargetConsumed: () -> Unit = {},
    onTitleChange: (String?) -> Unit = {},
    onFullscreenDetailChange: (Boolean) -> Unit = {},
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var warning by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var filter by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<CursadaInfo?>(null) }
    var selectedMateriaForRestoration by rememberSaveable { mutableStateOf<String?>(null) }
    var cursadas by remember { mutableStateOf<List<CursadaInfo>>(emptyList()) }
    var cursadasConNovedades by remember { mutableStateOf<Set<String>>(emptySet()) }
    var hasResolvedSnapshot by remember { mutableStateOf(false) }
    var initialLoadCompleted by remember { mutableStateOf(false) }
    var pendingTarget by rememberSaveable { mutableStateOf(initialTarget) }
    var notificationTargetForRestoration by rememberSaveable {
        mutableStateOf(initialTarget)
    }
    var targetRequestId by remember { mutableLongStateOf(0L) }
    var confirmedTargetRequestId by remember { mutableStateOf<Long?>(null) }
    var notificationOpenError by remember { mutableStateOf<String?>(null) }
    var listFilter by rememberSaveable { mutableStateOf(filter) }
    val refreshMutex = remember { Mutex() }
    val listState = rememberLazyListState()

    fun closeSelectedCursada() {
        selected = null
        selectedMateriaForRestoration = null
        notificationTargetForRestoration = null
    }

    fun openCursada(cursada: CursadaInfo) {
        selected = cursada
        selectedMateriaForRestoration = cursada.materia.cursadaMateriaKey()
        notificationOpenError = null
    }

    fun cancelPendingNotification() {
        pendingTarget = null
        notificationTargetForRestoration = null
        confirmedTargetRequestId = null
    }

    suspend fun persistSeenState(block: suspend () -> Unit): Boolean {
        actionError = null
        return try {
            withContext(Dispatchers.IO) { block() }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            actionError = userFacingError(
                operation = "guardar el estado de lectura de las cursadas",
                error = e,
            )
            false
        }
    }

    suspend fun refresh(forceRefresh: Boolean = false): Boolean {
        return refreshMutex.withLock {
            if (pendingTarget == null) {
                notificationOpenError = null
            }
            loading = true
            loadError = null
            warning = null
            actionError = null
            try {
                val refreshedCursadas = withContext(Dispatchers.IO) {
                    CursadasRepository.load(context, forceRefresh)
                }

                val vistosPorMateria = try {
                    withContext(Dispatchers.IO) {
                        CursadasStore.ensureSeenBaseline(context, refreshedCursadas)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Si no puede persistirse el baseline, se usa el snapshot actual para no
                    // marcar falsamente todas las cursadas como nuevas.
                    currentCursadasBaseline(refreshedCursadas)
                }

                val refreshedCursadasConNovedades = refreshedCursadas
                    .filter { cursada ->
                        val ultimaVista = vistosPorMateria[
                            cursada.materia.cursadaMateriaKey()
                        ] ?: Long.MIN_VALUE
                        val ultimaActualizacion =
                            cursada.ultimaModificacionEpochMillis ?: Long.MIN_VALUE
                        ultimaActualizacion > ultimaVista
                    }
                    .map { it.materia.cursadaMateriaKey() }
                    .toSet()

                // Se publican juntos al final para que un matcher no cancele la corrutina entre
                // la lista remota y la actualización del baseline de novedades.
                cursadas = refreshedCursadas
                cursadasConNovedades = refreshedCursadasConNovedades
                hasResolvedSnapshot = true
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (hasResolvedSnapshot) {
                    warning = cachedContentWarning(
                        operation = "actualizar las cursadas",
                        error = e,
                    )
                } else {
                    loadError = userFacingError(
                        operation = "cargar las cursadas",
                        error = e,
                    )
                }
                false
            } finally {
                loading = false
            }
        }
    }

    suspend fun refreshPendingTarget() {
        val target = pendingTarget
        val requestId = targetRequestId
        val refreshSucceeded = refresh(forceRefresh = true)
        if (
            refreshSucceeded &&
            target != null &&
            pendingTarget == target &&
            targetRequestId == requestId
        ) {
            confirmedTargetRequestId = requestId
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
                        placement = HeaderActionPlacement.Leading,
                        onClick = ::closeSelectedCursada
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
                            scope.launch {
                                if (persistSeenState {
                                    CursadasStore.markAllAsSeen(context, cursadas)
                                }) {
                                    cursadasConNovedades = emptySet()
                                }
                            }
                        }
                    ),
                    HeaderAction(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Refrescar cursadas",
                        enabled = !loading,
                        onClick = {
                            scope.launch {
                                if (pendingTarget != null) {
                                    refreshPendingTarget()
                                } else {
                                    refresh(forceRefresh = true)
                                }
                            }
                        }
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

    LaunchedEffect(initialTarget) {
        if (initialTarget != null) {
            selected = null
            selectedMateriaForRestoration = null
            pendingTarget = initialTarget
            notificationTargetForRestoration = initialTarget
            targetRequestId += 1
            confirmedTargetRequestId = null
            notificationOpenError = null
            onInitialTargetConsumed()
        }
    }

    LaunchedEffect(notificationOpenEventId, activeNotificationKind) {
        if (
            shouldInvalidatePendingNotification(
                eventId = notificationOpenEventId,
                activeKind = activeNotificationKind,
                screenKind = NotificationOpenKind.CURSADA,
            )
        ) {
            cancelPendingNotification()
            notificationOpenError = null
        }
    }

    LaunchedEffect(notificationTargetForRestoration, selected, pendingTarget) {
        val target = notificationTargetForRestoration ?: return@LaunchedEffect
        if (
            shouldRestoreNotificationResolution(
                hasSelectedContent = selected != null,
                hasPendingTarget = pendingTarget != null,
                hasRestorableTarget = true,
            )
        ) {
            pendingTarget = target
            targetRequestId += 1
            confirmedTargetRequestId = null
            notificationOpenError = null
        }
    }

    LaunchedEffect(pendingTarget, initialLoadCompleted, targetRequestId) {
        if (pendingTarget != null && initialLoadCompleted) {
            refreshPendingTarget()
        }
    }

    LaunchedEffect(cursadas, selectedMateriaForRestoration, initialLoadCompleted) {
        val materia = selectedMateriaForRestoration ?: return@LaunchedEffect
        val materiaKey = materia.cursadaMateriaKey()
        val restored = cursadas.firstOrNull {
            it.materia.cursadaMateriaKey() == materiaKey
        }
        if (restored != null) {
            if (selected != restored) {
                selected = restored
            }
            pendingTarget = null
            confirmedTargetRequestId = null
            notificationOpenError = null
        } else if (initialLoadCompleted) {
            selected = null
            selectedMateriaForRestoration = null
        }
    }

    LaunchedEffect(
        pendingTarget,
        cursadas,
        targetRequestId,
        confirmedTargetRequestId,
        loading,
    ) {
        val target = pendingTarget ?: return@LaunchedEffect
        val matchingCursada = findCursadaForNotification(cursadas, target)
        val targetRefreshConfirmed = isCursadaTargetResolutionConfirmed(
            targetRequestId = targetRequestId,
            confirmedTargetRequestId = confirmedTargetRequestId,
            loading = loading,
        )
        if (!targetRefreshConfirmed) return@LaunchedEffect
        if (matchingCursada == null) {
            notificationOpenError =
                "La cursada de la notificación ya no está disponible."
            cancelPendingNotification()
            return@LaunchedEffect
        }
        if (persistSeenState {
            CursadasStore.markAsSeen(context, matchingCursada)
        }) {
            cursadasConNovedades = cursadasConNovedades -
                matchingCursada.materia.cursadaMateriaKey()
        }
        openCursada(matchingCursada)
        pendingTarget = null
        confirmedTargetRequestId = null
        notificationOpenError = null
    }

    LaunchedEffect(Unit) {
        val (cached, hasCachedSnapshot) = try {
            withContext(Dispatchers.IO) {
                CursadasStore.load(context) to
                    CursadasStore.hasSnapshot(context)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList<CursadaInfo>() to false
        }
        if (hasCachedSnapshot) {
            cursadas = cached
            hasResolvedSnapshot = true
        }
        val hasNotificationTarget = pendingTarget != null
        if (!hasNotificationTarget) {
            refresh()
        }
        initialLoadCompleted = true
    }

    LaunchedEffect(filter) {
        if (listFilter != filter) {
            if (listState.layoutInfo.totalItemsCount > 0) {
                listState.scrollToItem(0)
            }
            listFilter = filter
        }
    }

    if (selected != null) {
        CursadaDetailScreen(
            cursada = selected!!,
            onBack = ::closeSelectedCursada,
            statusMessage = actionError ?: warning,
            statusIsError = actionError != null,
        )
        return
    }

    val showNotificationOpeningState = loading && pendingTarget != null

    if (showNotificationOpeningState) {
        NotificationOpeningState()
        return
    }

    val filtered = remember(cursadas, filter) {
        val query = filter.normalizeForSearch()
        val matchingCursadas = if (query.isBlank()) cursadas
        else cursadas.filter { it.materia.normalizeForSearch().contains(query) }

        matchingCursadas.sortedWith(
            compareByDescending<CursadaInfo> {
                it.ultimaModificacionEpochMillis ?: Long.MIN_VALUE
            }.thenBy { it.materia.cursadaMateriaKey() }
        )
    }
    val emptyStateMessage = cursadasEmptyStateMessage(
        cursadaCount = cursadas.size,
        filteredCount = filtered.size,
        hasActiveFilter = filter.isNotBlank(),
    )
    val loadingPhase = contentLoadingPhase(
        isLoading = loading,
        hasResolvedContent = hasResolvedSnapshot,
    )

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

        val visibleFeedback = loadError ?: notificationOpenError ?: actionError ?: warning
        val visibleFeedbackIsWarning = loadError == null &&
            notificationOpenError == null &&
            actionError == null &&
            warning != null
        if (visibleFeedback != null) {
            Text(
                visibleFeedback,
                color = if (visibleFeedbackIsWarning) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            val canRetryLoad = loadError != null ||
                (notificationOpenError == null && actionError == null && warning != null)
            if (canRetryLoad) {
                TextButton(
                    onClick = {
                        scope.launch {
                            if (pendingTarget != null) {
                                refreshPendingTarget()
                            } else {
                                refresh(forceRefresh = true)
                            }
                        }
                    },
                    enabled = !loading,
                ) {
                    Text("Reintentar")
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        LoadingContentBox(
            phase = loadingPhase,
            initialText = "Cargando cursadas…",
            refreshContentDescription = "Actualizando cursadas",
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (
                    emptyStateMessage != null &&
                    (visibleFeedback == null || visibleFeedbackIsWarning) &&
                    initialLoadCompleted
                ) {
                    item {
                        Text(
                            text = emptyStateMessage,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                        )
                    }
                }

                items(filtered, key = { it.materia.cursadaMateriaKey() }) { cursada ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                cancelPendingNotification()
                                notificationOpenError = null
                                scope.launch {
                                    if (persistSeenState {
                                        CursadasStore.markAsSeen(context, cursada)
                                    }) {
                                        cursadasConNovedades = cursadasConNovedades -
                                            cursada.materia.cursadaMateriaKey()
                                    }
                                }
                                openCursada(cursada)
                            }
                    ) {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
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
                                if (cursada.materia.cursadaMateriaKey() in cursadasConNovedades) {
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

            ScrollMoreHint(
                listState = listState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
            )
        }
    }
}

internal fun isCursadaTargetResolutionConfirmed(
    targetRequestId: Long,
    confirmedTargetRequestId: Long?,
    loading: Boolean,
): Boolean {
    return !loading && confirmedTargetRequestId == targetRequestId
}
