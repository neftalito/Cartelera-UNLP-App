/**
 * Presenta la cartelera de anuncios, su catálogo de materias y la apertura de notificaciones.
 */
package com.overcoders.unlpcarteleranotifier.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.HeaderActionPlacement
import com.overcoders.unlpcarteleranotifier.shouldInvalidatePendingNotification
import com.overcoders.unlpcarteleranotifier.shouldRestoreNotificationResolution
import com.overcoders.unlpcarteleranotifier.data.AnunciosCacheStore
import com.overcoders.unlpcarteleranotifier.data.AnunciosSnapshot
import com.overcoders.unlpcarteleranotifier.data.AnunciosService
import com.overcoders.unlpcarteleranotifier.data.MateriasRepository
import com.overcoders.unlpcarteleranotifier.data.SettingsStore
import com.overcoders.unlpcarteleranotifier.model.CarteleraNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.MateriaCatalogItem
import com.overcoders.unlpcarteleranotifier.model.Mensaje
import com.overcoders.unlpcarteleranotifier.model.toMateriaCatalogIdOrNull
import com.overcoders.unlpcarteleranotifier.push.NotificationOpenKind
import com.overcoders.unlpcarteleranotifier.ui.cartelera.AnuncioCard
import com.overcoders.unlpcarteleranotifier.ui.cartelera.AnuncioDetailScreen
import com.overcoders.unlpcarteleranotifier.ui.cartelera.buildShareText
import com.overcoders.unlpcarteleranotifier.ui.cartelera.rememberMateriasFilterState
import com.overcoders.unlpcarteleranotifier.ui.common.LoadingContentBox
import com.overcoders.unlpcarteleranotifier.ui.common.PaginationLoadingIndicator
import com.overcoders.unlpcarteleranotifier.ui.common.cachedContentWarning
import com.overcoders.unlpcarteleranotifier.ui.common.paginatedLoadingState
import com.overcoders.unlpcarteleranotifier.ui.common.copyPlainText
import com.overcoders.unlpcarteleranotifier.ui.common.normalizeForSearch
import com.overcoders.unlpcarteleranotifier.ui.common.partialContentWarning
import com.overcoders.unlpcarteleranotifier.ui.common.sharePlainText
import com.overcoders.unlpcarteleranotifier.ui.common.userFacingError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class AnnouncementRefreshBackup(
    val filterKey: String,
    val announcements: List<Mensaje>,
    val totalAvailable: Int?,
    val offset: Int,
    val newCount: Int,
    val newAnnouncementIdentities: Set<AnuncioIdentity>,
    val loadedFilterKey: String?,
    val hasPersistedSnapshot: Boolean,
)

/**
 * Pantalla principal de anuncios.
 *
 * Combina feed global o filtrado por materia, paginación incremental y el detalle
 * del anuncio seleccionado, incluyendo acciones para compartir o copiar contenido.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val materiasFilterState = rememberMateriasFilterState()
    val anunciosService = remember { AnunciosService() }
    val materiasRepository = remember(context) { MateriasRepository.get(context) }
    val hideCancelledMessagesFlow = remember(context) {
        SettingsStore.hideCancelledMateriasMessagesFlow(context)
    }
    val hideCancelledMessages by hideCancelledMessagesFlow
        .collectAsStateWithLifecycle(initialValue = false)

    val pageSize = 10

    var loadingInitial by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var warning by remember { mutableStateOf<String?>(null) }

    var offset by remember { mutableIntStateOf(0) }
    val initialFilterKey = materiasFilterState.selected?.id?.toMateriaCatalogIdOrNull()?.toString()
        ?: "__all__"
    var paginationRestorationFilterKey by rememberSaveable {
        mutableStateOf(initialFilterKey)
    }
    var paginationRestorationTargetOffset by rememberSaveable { mutableIntStateOf(0) }
    var savedListIndex by rememberSaveable { mutableIntStateOf(0) }
    var savedListScrollOffset by rememberSaveable { mutableIntStateOf(0) }
    var listPositionRestorationPending by remember {
        mutableStateOf(paginationRestorationTargetOffset > pageSize || savedListIndex > 0)
    }
    var totalAvailable by remember { mutableStateOf<Int?>(null) }
    var anuncios by remember { mutableStateOf<List<Mensaje>>(emptyList()) }
    var newCount by remember { mutableIntStateOf(0) }
    var newAnnouncementIdentities by remember {
        mutableStateOf<Set<AnuncioIdentity>>(emptySet())
    }
    var refreshBackup by remember { mutableStateOf<AnnouncementRefreshBackup?>(null) }
    var lastSeenTotal by remember { mutableStateOf<Int?>(null) }

    var selected by remember { mutableStateOf<Mensaje?>(null) }
    var selectedAnnouncementKey by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingTarget by rememberSaveable { mutableStateOf(initialTarget) }
    var notificationTargetForRestoration by rememberSaveable {
        mutableStateOf(initialTarget)
    }
    var notificationOpenError by remember { mutableStateOf<String?>(null) }
    var notificationAutoPagesLoaded by remember { mutableIntStateOf(0) }
    var notificationRefreshState by remember {
        mutableStateOf(CarteleraTargetRefreshState.REQUIRED)
    }
    var loadedFilterKey by remember { mutableStateOf<String?>(null) }
    var hasPersistedCurrentSnapshot by remember { mutableStateOf(false) }
    var pageRequestGeneration by remember { mutableIntStateOf(0) }
    val materias by materiasRepository.items.collectAsStateWithLifecycle()
    var materiasError by remember { mutableStateOf<String?>(null) }
    var materiasWarning by remember { mutableStateOf<String?>(null) }
    var materiasLoading by remember { mutableStateOf(false) }

    val seenKeys = remember { mutableSetOf<AnuncioIdentity>() }
    fun keyOf(m: Mensaje): AnuncioIdentity = m.anuncioIdentity()

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
        notificationRefreshState = CarteleraTargetRefreshState.REQUIRED
    }

    val materiasFiltradas = remember(materias, materiasFilterState.query) {
        val query = materiasFilterState.query.normalizeForSearch()
        if (query.isBlank()) materias
        else materias.filter { it.nombre.normalizeForSearch().contains(query) }
    }

    suspend fun loadPage(reset: Boolean, forceRefresh: Boolean = false) {
        if (!reset && (loadingInitial || loadingMore)) return

        // Cada reinicio invalida respuestas anteriores para que un filtro viejo no reemplace
        // el contenido correspondiente a la selección más reciente.
        if (reset) {
            pageRequestGeneration += 1
            if (pendingTarget == null) {
                notificationOpenError = null
            }
        }
        val requestGeneration = pageRequestGeneration
        error = null
        warning = null
        val materiaId = materiasFilterState.selected?.id?.toMateriaCatalogIdOrNull()
        val filterKey = materiaId?.toString() ?: "__all__"
        val existingSnapshotMatchesFilter = loadedFilterKey == filterKey
        val preservesVisibleSnapshot = reset && forceRefresh && existingSnapshotMatchesFilter
        if (reset) {
            if (paginationRestorationFilterKey != filterKey) {
                refreshBackup = null
                paginationRestorationFilterKey = filterKey
                paginationRestorationTargetOffset = 0
                savedListIndex = 0
                savedListScrollOffset = 0
                listPositionRestorationPending = false
            } else if (
                !preservesVisibleSnapshot &&
                (paginationRestorationTargetOffset > pageSize || savedListIndex > 0)
            ) {
                listPositionRestorationPending = true
            }
        }
        if (
            preservesVisibleSnapshot &&
            paginationRestorationTargetOffset > pageSize &&
            refreshBackup == null
        ) {
            refreshBackup = AnnouncementRefreshBackup(
                filterKey = filterKey,
                announcements = anuncios,
                totalAvailable = totalAvailable,
                offset = offset,
                newCount = newCount,
                newAnnouncementIdentities = newAnnouncementIdentities,
                loadedFilterKey = loadedFilterKey,
                hasPersistedSnapshot = hasPersistedCurrentSnapshot,
            )
        }
        var loadedCachedSnapshot = false
        fun isCurrentRequest(): Boolean {
            val currentFilterKey =
                materiasFilterState.selected?.id?.toMateriaCatalogIdOrNull()?.toString() ?: "__all__"
            return requestGeneration == pageRequestGeneration && currentFilterKey == filterKey
        }
        try {
            if (reset) {
                if (!preservesVisibleSnapshot) {
                    loadedFilterKey = null
                    hasPersistedCurrentSnapshot = false
                }
                loadingInitial = true
                loadingMore = false
                if (!preservesVisibleSnapshot) {
                    offset = 0
                    newCount = 0
                    newAnnouncementIdentities = emptySet()
                }
                if (!forceRefresh) {
                    val cached = try {
                        withContext(Dispatchers.IO) {
                            AnunciosCacheStore.load(context, materiaId)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    if (!isCurrentRequest()) return
                    hasPersistedCurrentSnapshot = cached != null
                    if (cached != null) {
                        loadedCachedSnapshot = true
                        val cachedMessages = cached.value.messages.distinctBy(::keyOf)
                        anuncios = cachedMessages
                        totalAvailable = cached.value.total
                        offset = cached.value.nextOffset
                        loadedFilterKey = filterKey
                        seenKeys.clear()
                        seenKeys.addAll(cachedMessages.map(::keyOf))
                        if (
                            paginationRestorationTargetOffset > pageSize &&
                            refreshBackup == null
                        ) {
                            refreshBackup = AnnouncementRefreshBackup(
                                filterKey = filterKey,
                                announcements = cachedMessages,
                                totalAvailable = cached.value.total,
                                offset = cached.value.nextOffset,
                                newCount = newCount,
                                newAnnouncementIdentities = newAnnouncementIdentities,
                                loadedFilterKey = filterKey,
                                hasPersistedSnapshot = true,
                            )
                        }
                    } else {
                        anuncios = emptyList()
                        totalAvailable = null
                        seenKeys.clear()
                    }
                } else if (!preservesVisibleSnapshot) {
                    // Un refresh forzado para otro filtro no debe dejar visible el snapshot
                    // anterior si la nueva solicitud falla.
                    anuncios = emptyList()
                    totalAvailable = null
                    seenKeys.clear()
                }
                if (materiasFilterState.selected == null) {
                    lastSeenTotal = try {
                        SettingsStore.getLastSeenTotal(context)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    newCount = 0
                    newAnnouncementIdentities = emptySet()
                }
            } else {
                loadingMore = true
            }
            val startOffset = if (reset) 0 else offset
            val page = anunciosService.fetch(
                desde = startOffset,
                cantidad = pageSize,
                idMateria = materiaId
            )
            if (!isCurrentRequest()) return

            val pageMessages = page.mensajes.distinctBy(::keyOf)
            val newOnes = pageMessages.filter { m ->
                val k = keyOf(m)
                if (seenKeys.contains(k)) {
                    false
                } else {
                    seenKeys.add(k)
                    true
                }
            }

            anuncios = if (reset) pageMessages else anuncios + newOnes
            totalAvailable = page.total
            if (reset) {
                if (preservesVisibleSnapshot) {
                    listPositionRestorationPending =
                        paginationRestorationTargetOffset > pageSize || savedListIndex > 0
                }
                seenKeys.clear()
                seenKeys.addAll(pageMessages.map(::keyOf))
            }
            offset = startOffset + page.receivedCount
            if (page.receivedCount == 0) {
                totalAvailable = offset
            }
            paginationRestorationFilterKey = filterKey
            paginationRestorationTargetOffset = maxOf(
                paginationRestorationTargetOffset,
                offset,
            )
            if (reset) {
                loadedFilterKey = filterKey
            }

            val activeRefreshBackup = refreshBackup?.takeIf { it.filterKey == filterKey }
            val refreshStillInProgress = activeRefreshBackup != null &&
                offset < paginationRestorationTargetOffset &&
                offset < (totalAvailable ?: Int.MAX_VALUE) &&
                page.receivedCount > 0
            val commitsGlobalRefresh = materiasFilterState.selected == null &&
                (reset && activeRefreshBackup == null ||
                    activeRefreshBackup != null && !refreshStillInProgress)
            if (commitsGlobalRefresh) {
                val storedTotal = lastSeenTotal ?: -1
                val diff = if (storedTotal < 0) 0 else (page.total - storedTotal).coerceAtLeast(0)
                newCount = if (storedTotal < 0) 0 else diff
                if (newCount > 0) {
                    android.widget.Toast.makeText(
                        context,
                        "Hay $newCount anuncios nuevos",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                try {
                    SettingsStore.setLastSeenTotal(context, page.total)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // El total sólo evita repetir el indicador de novedades; no invalida la página.
                }
                lastSeenTotal = page.total
            }
            if (materiasFilterState.selected == null) {
                newAnnouncementIdentities = anuncios
                    .take(newCount)
                    .mapTo(mutableSetOf(), ::keyOf)
            }

            if (!refreshStillInProgress) {
                try {
                    withContext(Dispatchers.IO) {
                        AnunciosCacheStore.save(
                            context = context,
                            snapshot = AnunciosSnapshot(
                                total = page.total,
                                messages = anuncios,
                                nextOffset = offset,
                            ),
                            materiaId = materiaId,
                        )
                    }
                    hasPersistedCurrentSnapshot = true
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Los anuncios remotos siguen siendo válidos aunque no puedan persistirse.
                }
                refreshBackup = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (isCurrentRequest()) {
                val activeRefreshBackup = refreshBackup?.takeIf { it.filterKey == filterKey }
                val hasPersistedFallback = reset && (
                    loadedCachedSnapshot ||
                        (preservesVisibleSnapshot && hasPersistedCurrentSnapshot)
                )
                when {
                    activeRefreshBackup != null -> {
                        anuncios = activeRefreshBackup.announcements
                        totalAvailable = activeRefreshBackup.totalAvailable
                        offset = activeRefreshBackup.offset
                        newCount = activeRefreshBackup.newCount
                        newAnnouncementIdentities =
                            activeRefreshBackup.newAnnouncementIdentities
                        loadedFilterKey = activeRefreshBackup.loadedFilterKey
                        hasPersistedCurrentSnapshot =
                            activeRefreshBackup.hasPersistedSnapshot
                        seenKeys.clear()
                        seenKeys.addAll(anuncios.map(::keyOf))
                        listPositionRestorationPending = false
                        refreshBackup = null
                        warning = cachedContentWarning(
                            operation = "actualizar los anuncios",
                            error = e,
                        )
                    }

                    hasPersistedFallback -> {
                        warning = cachedContentWarning(
                            operation = "actualizar los anuncios",
                            error = e,
                        )
                    }

                    preservesVisibleSnapshot -> {
                        warning = partialContentWarning(
                            operation = "actualizar los anuncios del filtro seleccionado",
                            error = e,
                        )
                    }

                    else -> error = userFacingError(
                        operation = "cargar los anuncios",
                        error = e,
                    )
                }
            }
        } finally {
            if (requestGeneration == pageRequestGeneration) {
                loadingInitial = false
                loadingMore = false
            }
        }
    }

    LaunchedEffect(loadingInitial, loadingMore, selected, pendingTarget) {
        if (selected != null) {
            val anuncio = selected ?: return@LaunchedEffect
            val shareText = buildShareText(anuncio)
            onHeaderActionsChange(
                listOf(
                    HeaderAction(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        placement = HeaderActionPlacement.Leading,
                        onClick = ::closeSelectedAnnouncement
                    ),
                    HeaderAction(
                        icon = Icons.Default.ContentCopy,
                        contentDescription = "Copiar anuncio",
                        onClick = {
                            copyPlainText(
                                context = context,
                                label = "Anuncio",
                                text = shareText
                            )
                        }
                    ),
                    HeaderAction(
                        icon = Icons.Default.Share,
                        contentDescription = "Compartir anuncio",
                        onClick = {
                            sharePlainText(
                                context = context,
                                text = shareText,
                                chooserTitle = "Compartir anuncio"
                            )
                        }
                    )
                )
            )
        } else {
            onHeaderActionsChange(
                listOf(
                    HeaderAction(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Refrescar anuncios",
                        enabled = pendingTarget == null && !loadingInitial && !loadingMore,
                        onClick = { scope.launch { loadPage(reset = true, forceRefresh = true) } }
                    )
                )
            )
        }
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
            error = null
            warning = null
            notificationAutoPagesLoaded = 0
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
            error = null
            warning = null
            notificationAutoPagesLoaded = 0
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

    LaunchedEffect(Unit) {
        loadMateriasCatalog()
    }

    LaunchedEffect(pendingTarget?.materiaId, materias, materiasLoading) {
        val target = pendingTarget ?: return@LaunchedEffect
        val materiaId = target.materiaId
        if (materiaId == null) {
            materiasFilterState.clearSelection()
            return@LaunchedEffect
        }
        if (materiasLoading || materiasFilterState.selected?.id == materiaId) {
            return@LaunchedEffect
        }
        val desiredMateriaId = notificationTargetFilterId(
            targetMateriaId = materiaId,
            availableMateriaIds = materias.mapTo(mutableSetOf()) { it.id },
        )
        val materia = materias.firstOrNull { it.id == desiredMateriaId }
        if (materia != null) {
            materiasFilterState.select(materia)
        } else {
            // Si el catálogo no conoce el id, la única búsqueda posible es el feed global.
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

    val hasMore = totalAvailable?.let { offset < it } ?: true

    LaunchedEffect(listState, listPositionRestorationPending) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, scrollOffset) ->
            if (!listPositionRestorationPending) {
                savedListIndex = index
                savedListScrollOffset = scrollOffset
            }
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
                paginationRestorationTargetOffset = offset
            }
            val visibleItemCount = if (hideCancelledMessages) {
                anuncios.count { !it.isAnulado }
            } else {
                anuncios.size
            }
            val maximumIndex = (visibleItemCount - 1).coerceAtLeast(0)
            listState.scrollToItem(
                index = savedListIndex.coerceIn(0, maximumIndex),
                scrollOffset = savedListScrollOffset,
            )
            listPositionRestorationPending = false
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
    ) {
        val target = pendingTarget ?: return@LaunchedEffect
        val isLoading = loadingInitial || loadingMore

        val targetMateriaId = target.materiaId
        val selectedTargetFilter = targetMateriaId != null &&
            materiasFilterState.selected?.id == targetMateriaId
        val targetMissingFromCatalog = targetMateriaId != null &&
            !materiasLoading &&
            materias.none { it.id == targetMateriaId }
        val canSearchCurrentFilter = targetMateriaId == null ||
            selectedTargetFilter ||
            targetMissingFromCatalog
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
            onRetry = if (notificationLoadFeedback != null) {
                {
                    error = null
                    warning = null
                    notificationAutoPagesLoaded = 0
                    scope.launch { loadPage(reset = true, forceRefresh = true) }
                }
            } else {
                null
            },
            onCancel = if (notificationLoadFeedback != null) {
                { cancelPendingNotification() }
            } else {
                null
            },
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = materiasFilterState.expanded,
            onExpandedChange = { isExpanded ->
                if (isExpanded) {
                    materiasFilterState.beginEditing()
                } else {
                    materiasFilterState.dismissEditing()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = materiasFilterState.query,
                onValueChange = { materiasFilterState.query = it },
                label = { Text("Filtrar por materia") },
                placeholder = { Text("Buscar materia...") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = materiasFilterState.expanded)
                },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = materiasFilterState.expanded,
                onDismissRequest = { materiasFilterState.dismissEditing() }
            ) {
                if (materiasLoading && materias.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Cargando materias…") },
                        onClick = { materiasFilterState.expanded = false },
                        enabled = false
                    )
                } else if (materiasError != null && materias.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Catálogo no disponible") },
                        onClick = { materiasFilterState.expanded = false },
                        enabled = false,
                    )
                } else if (materiasFiltradas.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Sin resultados") },
                        onClick = { materiasFilterState.expanded = false },
                        enabled = false
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        materiasFiltradas.forEach { materia ->
                            DropdownMenuItem(
                                text = { Text(materia.nombre) },
                                onClick = {
                                    materiasFilterState.select(materia)
                                    focusManager.clearFocus(force = true)
                                    keyboardController?.hide()
                                }
                            )
                        }
                    }
                }
            }
        }

        if (materiasFilterState.selected != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                materiasFilterState.clearSelection()
            }) {
                Text("Quitar filtro")
            }
        }

        Spacer(Modifier.height(8.dp))

        val visibleFeedback = error
            ?: notificationOpenError
            ?: materiasError
            ?: warning
            ?: materiasWarning
        val visibleFeedbackIsWarning = error == null &&
            notificationOpenError == null &&
            materiasError == null &&
            visibleFeedback != null
        if (visibleFeedback != null) {
            Text(
                visibleFeedback,
                color = if (visibleFeedbackIsWarning) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.bodySmall
            )
            when {
                error != null -> TextButton(
                    onClick = {
                        scope.launch { loadPage(reset = true, forceRefresh = true) }
                    },
                    enabled = !loadingInitial && !loadingMore,
                ) {
                    Text("Reintentar")
                }

                notificationOpenError == null &&
                    materiasError != null -> TextButton(
                    onClick = { scope.launch { loadMateriasCatalog(forceRefresh = true) } },
                    enabled = !materiasLoading,
                ) {
                    Text("Reintentar")
                }

                notificationOpenError == null &&
                    materiasError == null &&
                    warning != null -> TextButton(
                    onClick = {
                        scope.launch { loadPage(reset = true, forceRefresh = true) }
                    },
                    enabled = !loadingInitial && !loadingMore,
                ) {
                    Text("Reintentar")
                }

                notificationOpenError == null &&
                    materiasError == null &&
                    warning == null &&
                    materiasWarning != null -> TextButton(
                    onClick = { scope.launch { loadMateriasCatalog(forceRefresh = true) } },
                    enabled = !materiasLoading,
                ) {
                    Text("Reintentar")
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        val announcementsForDisplay = refreshBackup?.announcements ?: anuncios
        val identitiesForDisplay = refreshBackup?.newAnnouncementIdentities
            ?: newAnnouncementIdentities
        val anunciosVisibles = remember(announcementsForDisplay, hideCancelledMessages) {
            if (hideCancelledMessages) {
                announcementsForDisplay.filterNot { it.isAnulado }
            } else {
                announcementsForDisplay
            }
        }
        val currentFilterKey = materiasFilterState.selected?.id?.toMateriaCatalogIdOrNull()?.toString()
            ?: "__all__"
        val hasLoadedCurrentFilter = loadedFilterKey == currentFilterKey
        val loadingState = paginatedLoadingState(
            isContentLoading = loadingInitial,
            isLoadingMore = loadingMore,
            resolvedKey = loadedFilterKey,
            requestedKey = currentFilterKey,
            hasError = error != null,
        )
        val emptyStateMessage = when {
            !hasLoadedCurrentFilter ||
                loadingMore ||
                hasMore ||
                error != null -> null
            anunciosVisibles.isNotEmpty() -> null
            announcementsForDisplay.isNotEmpty() && hideCancelledMessages ->
                "No hay anuncios visibles con la configuración actual."
            materiasFilterState.selected != null ->
                "No hay anuncios disponibles para la materia seleccionada."
            else -> "No hay anuncios disponibles."
        }
        val canLoadMore = hasMore &&
            hasLoadedCurrentFilter &&
            !loadingInitial &&
            error == null &&
            warning == null

        LoadingContentBox(
            phase = loadingState.phase,
            initialText = "Cargando anuncios…",
            refreshContentDescription = "Actualizando anuncios",
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (emptyStateMessage != null) {
                    item(key = "empty") {
                        Text(
                            text = emptyStateMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                        )
                    }
                }

                items(anunciosVisibles, key = Mensaje::anuncioSaveableKey) { a ->
                    AnuncioCard(
                        anuncio = a,
                        isNew = materiasFilterState.selected == null &&
                                keyOf(a) in identitiesForDisplay,
                        onClick = {
                            val absoluteIndex = announcementsForDisplay.indexOfFirst {
                                it.anuncioSaveableKey() == a.anuncioSaveableKey()
                            }.coerceAtLeast(0)
                            openAnnouncement(
                                anuncio = a,
                                restorationTarget = a.toLocalRestorationTarget(
                                    materiaId = materiasFilterState.selected?.id,
                                    pageIndex = absoluteIndex / pageSize,
                                ),
                            )
                        }
                    )
                }

                if (loadingMore || canLoadMore) {
                    item(key = "pagination") {
                        Spacer(Modifier.height(8.dp))

                        if (loadingState.showPaginationIndicator) {
                            PaginationLoadingIndicator(text = "Cargando más anuncios…")
                        } else {
                            Button(
                                onClick = { scope.launch { loadPage(reset = false) } },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !loadingInitial && !loadingMore
                            ) {
                                Text("Cargar más")
                            }
                            Spacer(Modifier.height(8.dp))
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

internal fun notificationTargetFilterId(
    targetMateriaId: String?,
    availableMateriaIds: Set<String>,
): String? = targetMateriaId?.takeIf(availableMateriaIds::contains)

internal enum class NotificationSearchDecision {
    LOAD_NEXT_PAGE,
    NOT_FOUND,
    SEARCH_LIMIT_REACHED,
}

internal fun notificationSearchDecision(
    hasMore: Boolean,
    automaticallyLoadedPages: Int,
    maximumAutomaticPages: Int = DEFAULT_NOTIFICATION_SEARCH_PAGE_LIMIT,
): NotificationSearchDecision = when {
    !hasMore -> NotificationSearchDecision.NOT_FOUND
    automaticallyLoadedPages >= maximumAutomaticPages ->
        NotificationSearchDecision.SEARCH_LIMIT_REACHED
    else -> NotificationSearchDecision.LOAD_NEXT_PAGE
}

internal const val DEFAULT_NOTIFICATION_SEARCH_PAGE_LIMIT = 5
