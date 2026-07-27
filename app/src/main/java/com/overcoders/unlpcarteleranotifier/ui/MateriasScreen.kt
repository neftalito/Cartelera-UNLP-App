/**
 * Presenta la cartelera de anuncios, su catálogo de materias y la apertura de notificaciones.
 */
package com.overcoders.unlpcarteleranotifier.ui

import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.shouldInvalidatePendingNotification
import com.overcoders.unlpcarteleranotifier.shouldRestoreNotificationResolution
import com.overcoders.unlpcarteleranotifier.data.MateriasRepository
import com.overcoders.unlpcarteleranotifier.data.SettingsStore
import com.overcoders.unlpcarteleranotifier.model.CarteleraNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.Mensaje
import com.overcoders.unlpcarteleranotifier.model.toMateriaCatalogIdOrNull
import com.overcoders.unlpcarteleranotifier.push.NotificationOpenKind
import com.overcoders.unlpcarteleranotifier.ui.cartelera.AnuncioDetailScreen
import com.overcoders.unlpcarteleranotifier.ui.cartelera.CarteleraTargetRefreshState
import com.overcoders.unlpcarteleranotifier.ui.cartelera.DEFAULT_NOTIFICATION_SEARCH_PAGE_LIMIT
import com.overcoders.unlpcarteleranotifier.ui.cartelera.MateriasScreenContent
import com.overcoders.unlpcarteleranotifier.ui.cartelera.MateriasScreenContentState
import com.overcoders.unlpcarteleranotifier.ui.cartelera.NotificationSearchDecision
import com.overcoders.unlpcarteleranotifier.ui.cartelera.anuncioSaveableKey
import com.overcoders.unlpcarteleranotifier.ui.cartelera.announcementDetailHeaderActions
import com.overcoders.unlpcarteleranotifier.ui.cartelera.announcementsRefreshHeaderActions
import com.overcoders.unlpcarteleranotifier.ui.cartelera.canResolveCarteleraTargetFromCurrentAnnouncements
import com.overcoders.unlpcarteleranotifier.ui.cartelera.findAnuncioForNotification
import com.overcoders.unlpcarteleranotifier.ui.cartelera.localSelectionRestoration
import com.overcoders.unlpcarteleranotifier.ui.cartelera.notificationSearchDecision
import com.overcoders.unlpcarteleranotifier.ui.cartelera.notificationTargetFilterId
import com.overcoders.unlpcarteleranotifier.ui.cartelera.rememberMateriasFeedState
import com.overcoders.unlpcarteleranotifier.ui.cartelera.rememberMateriasFilterState
import com.overcoders.unlpcarteleranotifier.ui.cartelera.toLocalRestorationTarget
import com.overcoders.unlpcarteleranotifier.ui.common.cachedContentWarning
import com.overcoders.unlpcarteleranotifier.ui.common.userFacingError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Pantalla principal de anuncios.
 *
 * Combina feed global o filtrado por materia, paginación incremental y el detalle
 * del anuncio seleccionado, incluyendo acciones para compartir o copiar contenido.
 */
@Composable
fun MateriasScreen(
    initialTarget: CarteleraNotificationTarget? = null,
    notificationOpenEventId: Long = 0L,
    activeNotificationKind: NotificationOpenKind? = null,
    onInitialTargetConsumed: () -> Unit = {},
    onTitleChange: (String?) -> Unit = {},
    onFullscreenDetailChange: (Boolean) -> Unit = {},
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val materiasFilterState = rememberMateriasFilterState()
    val initialFilterKey = materiasFilterState.selected?.id
        ?.toMateriaCatalogIdOrNull()
        ?.toString()
        ?: "__all__"
    val feedState = rememberMateriasFeedState(initialFilterKey)
    val materiasRepository = remember(context) { MateriasRepository.get(context) }
    val hideCancelledMessagesFlow = remember(context) {
        SettingsStore.hideCancelledMateriasMessagesFlow(context)
    }
    val hideCancelledMessages by hideCancelledMessagesFlow
        .collectAsStateWithLifecycle(initialValue = false)

    val pageSize = feedState.pageSize
    val loadingInitial = feedState.loadingInitial
    val loadingMore = feedState.loadingMore
    val error = feedState.error
    val warning = feedState.warning
    val offset = feedState.offset
    val paginationRestorationFilterKey = feedState.paginationRestorationFilterKey
    val paginationRestorationTargetOffset = feedState.paginationRestorationTargetOffset
    val listPositionRestorationPending = feedState.listPositionRestorationPending
    val anuncios = feedState.anuncios
    val loadedFilterKey = feedState.loadedFilterKey
    val hasMore = feedState.hasMore

    var selected by remember { mutableStateOf<Mensaje?>(null) }
    var selectedAnnouncementKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingTarget by rememberSaveable { mutableStateOf(initialTarget) }
    var notificationTargetForRestoration by rememberSaveable {
        mutableStateOf(initialTarget)
    }
    var notificationOpenError by remember { mutableStateOf<String?>(null) }
    var notificationAutoPagesLoaded by remember { mutableIntStateOf(0) }
    var notificationGlobalFallbackAttempted by rememberSaveable { mutableStateOf(false) }
    var notificationRefreshState by remember {
        mutableStateOf(CarteleraTargetRefreshState.REQUIRED)
    }
    val materias by materiasRepository.items.collectAsStateWithLifecycle()
    var materiasError by remember { mutableStateOf<String?>(null) }
    var materiasWarning by remember { mutableStateOf<String?>(null) }
    var materiasLoading by remember { mutableStateOf(false) }

    fun openAnnouncement(
        anuncio: Mensaje,
        restorationTarget: CarteleraNotificationTarget,
    ) {
        selected = anuncio
        selectedAnnouncementKey = anuncio.anuncioSaveableKey()
        notificationTargetForRestoration = restorationTarget
        notificationOpenError = null
    }

    fun closeSelectedAnnouncement() {
        selected = null
        selectedAnnouncementKey = null
        notificationTargetForRestoration = null
    }

    fun cancelPendingNotification() {
        selected = null
        selectedAnnouncementKey = null
        pendingTarget = null
        notificationTargetForRestoration = null
        notificationAutoPagesLoaded = 0
        notificationGlobalFallbackAttempted = false
        notificationRefreshState = CarteleraTargetRefreshState.REQUIRED
    }

    suspend fun loadPage(reset: Boolean, forceRefresh: Boolean = false) {
        feedState.loadPage(
            reset = reset,
            forceRefresh = forceRefresh,
            currentMateriaId = {
                materiasFilterState.selected?.id?.toMateriaCatalogIdOrNull()
            },
            hasPendingNotification = pendingTarget != null,
            onClearNotificationOpenError = { notificationOpenError = null },
        )
    }
    LaunchedEffect(selected) {
        onFullscreenDetailChange(selected != null)
        onTitleChange(if (selected != null) "Anuncio" else null)
    }

    DisposableEffect(Unit) {
        onDispose {
            onHeaderActionsChange(emptyList())
            onTitleChange(null)
            onFullscreenDetailChange(false)
        }
    }

    LaunchedEffect(anuncios, selectedAnnouncementKey) {
        val keyToRestore = selectedAnnouncementKey ?: return@LaunchedEffect
        if (selected?.anuncioSaveableKey() != keyToRestore) {
            anuncios.firstOrNull { it.anuncioSaveableKey() == keyToRestore }?.let { match ->
                selected = match
                if (
                    pendingTarget?.localSelectionRestoration()?.announcementKey == keyToRestore
                ) {
                    pendingTarget = null
                    notificationOpenError = null
                    notificationAutoPagesLoaded = 0
                    notificationGlobalFallbackAttempted = false
                    notificationRefreshState = CarteleraTargetRefreshState.REQUIRED
                }
            }
        }
    }

    LaunchedEffect(materias, materiasFilterState.selected?.id) {
        val selectedId = materiasFilterState.selected?.id ?: return@LaunchedEffect
        materias.firstOrNull { it.id == selectedId }?.let(
            materiasFilterState::reconcileSelection
        )
    }

    LaunchedEffect(notificationOpenEventId, activeNotificationKind) {
        if (
            shouldInvalidatePendingNotification(
                eventId = notificationOpenEventId,
                activeKind = activeNotificationKind,
                screenKind = NotificationOpenKind.CARTELERA,
            )
        ) {
            cancelPendingNotification()
            notificationOpenError = null
        }
    }

    suspend fun refreshPendingNotificationTarget() {
        val target = pendingTarget ?: return
        notificationRefreshState = CarteleraTargetRefreshState.IN_PROGRESS
        loadPage(
            reset = true,
            // Una selección local restaurada debe poder resolverse desde el snapshot persistido.
            // Los destinos externos sí exigen una consulta actual para evitar abrir datos obsoletos.
            forceRefresh = target.localSelectionRestoration() == null,
        )
        if (pendingTarget == target) {
            notificationRefreshState = CarteleraTargetRefreshState.COMPLETED
        }
    }

    LaunchedEffect(initialTarget) {
        if (initialTarget != null) {
            selected = null
            selectedAnnouncementKey = null
            pendingTarget = initialTarget
            notificationTargetForRestoration = initialTarget
            notificationOpenError = null
            feedState.clearFeedback()
            notificationAutoPagesLoaded = 0
            notificationGlobalFallbackAttempted = false
            notificationRefreshState = CarteleraTargetRefreshState.REQUIRED
            onInitialTargetConsumed()
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
            notificationOpenError = null
            feedState.clearFeedback()
            notificationAutoPagesLoaded = 0
            notificationGlobalFallbackAttempted = false
            notificationRefreshState = CarteleraTargetRefreshState.REQUIRED
        }
    }

    suspend fun loadMateriasCatalog(forceRefresh: Boolean = false) {
        materiasLoading = true
        materiasError = null
        materiasWarning = null
        try {
            val result = materiasRepository.load(forceRefresh)
            val failure = result.refreshFailure
            if (failure != null && result.items.isNotEmpty()) {
                materiasWarning = cachedContentWarning(
                    operation = "actualizar el catálogo de materias",
                    error = failure,
                )
            } else if (failure != null) {
                materiasError = userFacingError(
                    operation = "cargar el catálogo de materias",
                    error = failure,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            materiasLoading = false
        }
    }

    val headerRetriesNotification = pendingTarget != null && (error != null || warning != null)
    val headerRefreshesCatalog = pendingTarget == null &&
        error == null &&
        notificationOpenError == null &&
        (materiasError != null || (warning == null && materiasWarning != null))

    LaunchedEffect(
        loadingInitial,
        loadingMore,
        materiasLoading,
        selected,
        pendingTarget,
        headerRetriesNotification,
        headerRefreshesCatalog,
    ) {
        if (selected != null) {
            val anuncio = selected ?: return@LaunchedEffect
            onHeaderActionsChange(
                announcementDetailHeaderActions(
                    context = context,
                    announcement = anuncio,
                    onBack = ::closeSelectedAnnouncement,
                )
            )
        } else {
            val refreshEnabled = when {
                pendingTarget != null -> headerRetriesNotification &&
                    !loadingInitial &&
                    !loadingMore
                headerRefreshesCatalog -> !materiasLoading
                else -> !loadingInitial && !loadingMore
            }
            onHeaderActionsChange(
                announcementsRefreshHeaderActions(
                    refreshesCatalog = headerRefreshesCatalog,
                    enabled = refreshEnabled,
                    onRefresh = {
                        when {
                            headerRetriesNotification -> scope.launch {
                                feedState.clearFeedback()
                                notificationAutoPagesLoaded = 0
                                loadPage(reset = true, forceRefresh = true)
                            }
                            headerRefreshesCatalog -> scope.launch {
                                loadMateriasCatalog(forceRefresh = true)
                            }
                            else -> scope.launch {
                                loadPage(reset = true, forceRefresh = true)
                            }
                        }
                    },
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        loadMateriasCatalog()
    }

    LaunchedEffect(
        pendingTarget?.materiaId,
        notificationGlobalFallbackAttempted,
        materias,
        materiasLoading,
    ) {
        val target = pendingTarget ?: return@LaunchedEffect
        val materiaId = target.materiaId
        if (materiasLoading) {
            return@LaunchedEffect
        }
        val desiredMateriaId = notificationTargetFilterId(
            targetMateriaId = materiaId,
            availableMateriaIds = materias.mapTo(mutableSetOf()) { it.id },
            useGlobalFallback = notificationGlobalFallbackAttempted,
        )
        if (materiasFilterState.selected?.id == desiredMateriaId) return@LaunchedEffect
        val materia = materias.firstOrNull { it.id == desiredMateriaId }
        if (materia != null) {
            materiasFilterState.select(materia)
        } else {
            // El fallback y los ids desconocidos se resuelven únicamente en el feed global.
            materiasFilterState.clearSelection()
        }
    }

    LaunchedEffect(materiasFilterState.selected?.id) {
        val selectedFilterKey = materiasFilterState.selected?.id?.toMateriaCatalogIdOrNull()?.toString()
            ?: "__all__"
        if (paginationRestorationFilterKey != selectedFilterKey) {
            listState.scrollToItem(0)
        }
        if (pendingTarget != null) {
            refreshPendingNotificationTarget()
        } else {
            loadPage(reset = true)
        }
    }

    LaunchedEffect(listState, listPositionRestorationPending) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, scrollOffset) ->
            feedState.recordListPosition(index = index, scrollOffset = scrollOffset)
        }
    }

    val activeFilterKey = materiasFilterState.selected?.id?.toMateriaCatalogIdOrNull()?.toString()
        ?: "__all__"
    val canContinueListRestoration = listPositionRestorationPending &&
        paginationRestorationFilterKey == activeFilterKey &&
        loadedFilterKey == activeFilterKey &&
        offset < paginationRestorationTargetOffset &&
        hasMore &&
        pendingTarget == null &&
        selected == null &&
        !loadingInitial &&
        !loadingMore &&
        error == null &&
        warning == null

    LaunchedEffect(
        canContinueListRestoration,
        listPositionRestorationPending,
        offset,
        paginationRestorationTargetOffset,
        hasMore,
        loadedFilterKey,
        activeFilterKey,
        loadingInitial,
        loadingMore,
        error,
        warning,
        pendingTarget,
        selected,
        hideCancelledMessages,
    ) {
        if (canContinueListRestoration) {
            loadPage(reset = false)
            return@LaunchedEffect
        }

        val restorationReachedEnd = offset >= paginationRestorationTargetOffset || !hasMore
        if (
            listPositionRestorationPending &&
            restorationReachedEnd &&
            loadedFilterKey == activeFilterKey &&
            pendingTarget == null &&
            selected == null &&
            !loadingInitial &&
            !loadingMore &&
            error == null &&
            warning == null
        ) {
            if (!hasMore) {
                feedState.clampRestorationTargetToLoadedOffset()
            }
            val visibleItemCount = if (hideCancelledMessages) {
                anuncios.count { !it.isAnulado }
            } else {
                anuncios.size
            }
            val restoredPosition = feedState.restoredListPosition(visibleItemCount)
            listState.scrollToItem(
                index = restoredPosition.index,
                scrollOffset = restoredPosition.scrollOffset,
            )
            feedState.completeListPositionRestoration()
        }
    }

    LaunchedEffect(
        pendingTarget,
        anuncios,
        loadedFilterKey,
        hasMore,
        loadingInitial,
        loadingMore,
        materiasLoading,
        materiasFilterState.selected?.id,
        materias,
        notificationRefreshState,
        notificationGlobalFallbackAttempted,
    ) {
        val target = pendingTarget ?: return@LaunchedEffect
        val isLoading = loadingInitial || loadingMore

        val targetMateriaId = target.materiaId
        val usingGlobalFallback = notificationGlobalFallbackAttempted && targetMateriaId != null
        val selectedTargetFilter = !usingGlobalFallback && targetMateriaId != null &&
            materiasFilterState.selected?.id == targetMateriaId
        val targetMissingFromCatalog = targetMateriaId != null &&
            !materiasLoading &&
            materias.none { it.id == targetMateriaId }
        val canSearchCurrentFilter = when {
            usingGlobalFallback -> materiasFilterState.selected == null
            targetMateriaId == null -> true
            selectedTargetFilter -> true
            targetMissingFromCatalog -> true
            else -> false
        }
        if (!canSearchCurrentFilter) return@LaunchedEffect

        val expectedFilterKey = if (selectedTargetFilter) targetMateriaId else "__all__"
        val expectedFilterLoaded = loadedFilterKey == expectedFilterKey
        if (
            canResolveCarteleraTargetFromCurrentAnnouncements(
                isLoading = isLoading,
                refreshFailed = error != null || warning != null,
                expectedFilterLoaded = expectedFilterLoaded,
                targetRefreshCompleted =
                    notificationRefreshState == CarteleraTargetRefreshState.COMPLETED,
            )
        ) {
            val match = findAnuncioForNotification(anuncios, target)
            if (match != null) {
                val absoluteIndex = anuncios.indexOfFirst {
                    it.anuncioSaveableKey() == match.anuncioSaveableKey()
                }.coerceAtLeast(0)
                openAnnouncement(
                    anuncio = match,
                    restorationTarget = match.toLocalRestorationTarget(
                        materiaId = materiasFilterState.selected?.id,
                        pageIndex = absoluteIndex / pageSize,
                    ),
                )
                pendingTarget = null
                notificationOpenError = null
                notificationAutoPagesLoaded = 0
                notificationGlobalFallbackAttempted = false
                notificationRefreshState = CarteleraTargetRefreshState.REQUIRED
                return@LaunchedEffect
            }
        }
        if (isLoading) return@LaunchedEffect
        if (error != null || warning != null) return@LaunchedEffect

        if (notificationRefreshState == CarteleraTargetRefreshState.REQUIRED) {
            scope.launch { refreshPendingNotificationTarget() }
            return@LaunchedEffect
        }
        if (notificationRefreshState == CarteleraTargetRefreshState.IN_PROGRESS) {
            return@LaunchedEffect
        }

        if (!expectedFilterLoaded) {
            scope.launch { loadPage(reset = true, forceRefresh = true) }
            return@LaunchedEffect
        }

        when (
            notificationSearchDecision(
                hasMore = hasMore,
                automaticallyLoadedPages = notificationAutoPagesLoaded,
                searchedWithMateriaFilter = selectedTargetFilter,
                globalFallbackAttempted = notificationGlobalFallbackAttempted,
                isLocalRestoration = target.localSelectionRestoration() != null,
                maximumAutomaticPages = maxOf(
                    DEFAULT_NOTIFICATION_SEARCH_PAGE_LIMIT,
                    target.localSelectionRestoration()?.pageIndex ?: 0,
                ),
            )
        ) {
            NotificationSearchDecision.LOAD_NEXT_PAGE -> {
                notificationAutoPagesLoaded += 1
                scope.launch { loadPage(reset = false) }
            }

            NotificationSearchDecision.RETRY_GLOBAL_FEED -> {
                notificationGlobalFallbackAttempted = true
                notificationAutoPagesLoaded = 0
                // El cambio de filtro dispara el refresh global; marcarlo en progreso evita
                // que el resolver programe en paralelo una segunda solicitud de reinicio.
                notificationRefreshState = CarteleraTargetRefreshState.IN_PROGRESS
                feedState.clearFeedback()
                materiasFilterState.clearSelection()
            }

            NotificationSearchDecision.NOT_FOUND -> {
                notificationOpenError =
                    "La publicación de la notificación ya no está disponible en cartelera."
                cancelPendingNotification()
            }

            NotificationSearchDecision.SEARCH_LIMIT_REACHED -> {
                notificationOpenError =
                    "No se pudo encontrar automáticamente la publicación. " +
                        "Podés buscarla de forma manual en la cartelera."
                cancelPendingNotification()
            }
        }
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore, hasMore, offset, pendingTarget) {
        if (
            selected == null &&
            pendingTarget == null &&
            shouldLoadMore &&
            hasMore &&
            !loadingInitial &&
            !loadingMore &&
            error == null &&
            warning == null
        ) {
            loadPage(reset = false)
        }
    }

    if (selected != null) {
        AnuncioDetailScreen(
            anuncio = selected!!,
            onBack = ::closeSelectedAnnouncement,
            warningMessage = warning,
        )
        return
    }

    // Mientras se resuelve un destino no se expone el feed: una interacción manual podría
    // competir con la paginación y ser reemplazada al aparecer el anuncio buscado.
    val showNotificationOpeningState = pendingTarget != null

    if (showNotificationOpeningState) {
        val notificationLoadFeedback = error ?: warning
        NotificationOpeningState(
            errorMessage = notificationLoadFeedback,
            openingKey = notificationOpenEventId to pendingTarget,
            onCancel = ::cancelPendingNotification,
        )
        return
    }

    MateriasScreenContent(
        state = MateriasScreenContentState(
            materias = materias,
            materiasLoading = materiasLoading,
            materiasError = materiasError,
            materiasWarning = materiasWarning,
            announcementsForDisplay = feedState.announcementsForDisplay,
            newAnnouncementIdentities = feedState.announcementIdentitiesForDisplay,
            hideCancelledMessages = hideCancelledMessages,
            loadingInitial = loadingInitial,
            loadingMore = loadingMore,
            error = error,
            notificationOpenError = notificationOpenError,
            warning = warning,
            loadedFilterKey = loadedFilterKey,
            hasMore = hasMore,
        ),
        filterState = materiasFilterState,
        listState = listState,
        onAnnouncementClick = { announcement, absoluteIndex ->
            openAnnouncement(
                anuncio = announcement,
                restorationTarget = announcement.toLocalRestorationTarget(
                    materiaId = materiasFilterState.selected?.id,
                    pageIndex = absoluteIndex / pageSize,
                ),
            )
        },
        onLoadMore = { scope.launch { loadPage(reset = false) } },
    )
}
