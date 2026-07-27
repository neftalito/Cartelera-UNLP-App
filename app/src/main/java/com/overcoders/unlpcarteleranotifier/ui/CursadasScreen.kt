/**
 * Muestra las cursadas publicadas, sus novedades y el detalle abierto desde notificaciones.
 */
package com.overcoders.unlpcarteleranotifier.ui

import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.model.CursadaInfo
import com.overcoders.unlpcarteleranotifier.model.CursadaNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.cursadaMateriaKey
import com.overcoders.unlpcarteleranotifier.push.NotificationOpenKind
import com.overcoders.unlpcarteleranotifier.shouldInvalidatePendingNotification
import com.overcoders.unlpcarteleranotifier.shouldRestoreNotificationResolution
import com.overcoders.unlpcarteleranotifier.ui.cursadas.CursadaDetailScreen
import com.overcoders.unlpcarteleranotifier.ui.cursadas.CursadasScreenContent
import com.overcoders.unlpcarteleranotifier.ui.cursadas.CursadasScreenContentState
import com.overcoders.unlpcarteleranotifier.ui.cursadas.cursadaDetailHeaderActions
import com.overcoders.unlpcarteleranotifier.ui.cursadas.cursadasListHeaderActions
import com.overcoders.unlpcarteleranotifier.ui.cursadas.findCursadaForNotification
import com.overcoders.unlpcarteleranotifier.ui.cursadas.isCursadaTargetResolutionConfirmed
import com.overcoders.unlpcarteleranotifier.ui.cursadas.rememberCursadasState
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cursadasState = rememberCursadasState()
    val listState = rememberLazyListState()

    var filter by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf<CursadaInfo?>(null) }
    var selectedMateriaForRestoration by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingTarget by rememberSaveable { mutableStateOf(initialTarget) }
    var notificationTargetForRestoration by rememberSaveable {
        mutableStateOf(initialTarget)
    }
    var targetRequestId by remember { mutableLongStateOf(0L) }
    var confirmedTargetRequestId by remember { mutableStateOf<Long?>(null) }
    var notificationOpenError by remember { mutableStateOf<String?>(null) }
    var listFilter by rememberSaveable { mutableStateOf(filter) }

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

    suspend fun refresh(forceRefresh: Boolean = false): Boolean {
        return cursadasState.refresh(
            forceRefresh = forceRefresh,
            onRefreshStart = {
                if (pendingTarget == null) {
                    notificationOpenError = null
                }
            },
        )
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

    LaunchedEffect(
        cursadasState.loading,
        cursadasState.cursadasConNovedades,
        selected,
    ) {
        val selectedCursada = selected
        if (selectedCursada != null) {
            onHeaderActionsChange(
                cursadaDetailHeaderActions(
                    context = context,
                    cursada = selectedCursada,
                    onBack = ::closeSelectedCursada,
                )
            )
        } else {
            onHeaderActionsChange(
                cursadasListHeaderActions(
                    loading = cursadasState.loading,
                    hasUnseenCursadas = cursadasState.cursadasConNovedades.isNotEmpty(),
                    onMarkAllAsSeen = {
                        scope.launch { cursadasState.markAllAsSeen() }
                    },
                    onRefresh = {
                        scope.launch {
                            if (pendingTarget != null) {
                                refreshPendingTarget()
                            } else {
                                refresh(forceRefresh = true)
                            }
                        }
                    },
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

    LaunchedEffect(
        pendingTarget,
        cursadasState.initialLoadCompleted,
        targetRequestId,
    ) {
        if (pendingTarget != null && cursadasState.initialLoadCompleted) {
            refreshPendingTarget()
        }
    }

    LaunchedEffect(
        cursadasState.cursadas,
        selectedMateriaForRestoration,
        cursadasState.initialLoadCompleted,
    ) {
        val materia = selectedMateriaForRestoration ?: return@LaunchedEffect
        val materiaKey = materia.cursadaMateriaKey()
        val restored = cursadasState.cursadas.firstOrNull { cursada ->
            cursada.materia.cursadaMateriaKey() == materiaKey
        }
        if (restored != null) {
            if (selected != restored) {
                selected = restored
            }
            pendingTarget = null
            confirmedTargetRequestId = null
            notificationOpenError = null
        } else if (cursadasState.initialLoadCompleted) {
            selected = null
            selectedMateriaForRestoration = null
        }
    }

    LaunchedEffect(
        pendingTarget,
        cursadasState.cursadas,
        targetRequestId,
        confirmedTargetRequestId,
        cursadasState.loading,
    ) {
        val target = pendingTarget ?: return@LaunchedEffect
        val matchingCursada = findCursadaForNotification(cursadasState.cursadas, target)
        val targetRefreshConfirmed = isCursadaTargetResolutionConfirmed(
            targetRequestId = targetRequestId,
            confirmedTargetRequestId = confirmedTargetRequestId,
            loading = cursadasState.loading,
        )
        if (!targetRefreshConfirmed) return@LaunchedEffect
        if (matchingCursada == null) {
            notificationOpenError = "La cursada de la notificación ya no está disponible."
            cancelPendingNotification()
            return@LaunchedEffect
        }
        cursadasState.markAsSeen(matchingCursada)
        openCursada(matchingCursada)
        pendingTarget = null
        confirmedTargetRequestId = null
        notificationOpenError = null
    }

    LaunchedEffect(Unit) {
        cursadasState.loadCachedSnapshot()
        val hasNotificationTarget = pendingTarget != null
        if (!hasNotificationTarget) {
            refresh()
        }
        cursadasState.completeInitialLoad()
    }

    LaunchedEffect(filter) {
        if (listFilter != filter) {
            if (listState.layoutInfo.totalItemsCount > 0) {
                listState.scrollToItem(0)
            }
            listFilter = filter
        }
    }

    val selectedCursada = selected
    if (selectedCursada != null) {
        CursadaDetailScreen(
            cursada = selectedCursada,
            onBack = ::closeSelectedCursada,
            statusMessage = cursadasState.actionError ?: cursadasState.warning,
            statusIsError = cursadasState.actionError != null,
        )
        return
    }

    val showNotificationOpeningState = cursadasState.loading && pendingTarget != null
    if (showNotificationOpeningState) {
        NotificationOpeningState(
            openingKey = notificationOpenEventId to pendingTarget,
            onCancel = ::cancelPendingNotification,
        )
        return
    }

    CursadasScreenContent(
        state = CursadasScreenContentState(
            cursadas = cursadasState.cursadas,
            cursadasConNovedades = cursadasState.cursadasConNovedades,
            loading = cursadasState.loading,
            hasResolvedSnapshot = cursadasState.hasResolvedSnapshot,
            initialLoadCompleted = cursadasState.initialLoadCompleted,
            loadError = cursadasState.loadError,
            notificationOpenError = notificationOpenError,
            actionError = cursadasState.actionError,
            warning = cursadasState.warning,
        ),
        filter = filter,
        listState = listState,
        onFilterChange = { filter = it },
        onCursadaClick = { cursada ->
            cancelPendingNotification()
            notificationOpenError = null
            scope.launch { cursadasState.markAsSeen(cursada) }
            openCursada(cursada)
        },
    )
}
