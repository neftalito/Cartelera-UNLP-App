/** Coordina páginas, caché, novedades y restauración del feed de cartelera. */
package com.overcoders.unlpcarteleranotifier.ui.cartelera

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.overcoders.unlpcarteleranotifier.data.AnunciosCacheStore
import com.overcoders.unlpcarteleranotifier.data.AnunciosService
import com.overcoders.unlpcarteleranotifier.data.AnunciosSnapshot
import com.overcoders.unlpcarteleranotifier.data.SettingsStore
import com.overcoders.unlpcarteleranotifier.model.Mensaje
import com.overcoders.unlpcarteleranotifier.ui.common.cachedContentWarning
import com.overcoders.unlpcarteleranotifier.ui.common.partialContentWarning
import com.overcoders.unlpcarteleranotifier.ui.common.userFacingError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
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

internal data class MateriasListPosition(
    val index: Int,
    val scrollOffset: Int,
)

@Stable
internal class MateriasFeedState(
    private val context: Context,
    private val anunciosService: AnunciosService,
    private val paginationRestorationFilterKeyState: MutableState<String>,
    private val paginationRestorationTargetOffsetState: MutableIntState,
    private val savedListIndexState: MutableIntState,
    private val savedListScrollOffsetState: MutableIntState,
) {
    internal val pageSize: Int = 10

    internal var loadingInitial by mutableStateOf(true)
        private set
    internal var loadingMore by mutableStateOf(false)
        private set
    internal var error by mutableStateOf<String?>(null)
        private set
    internal var warning by mutableStateOf<String?>(null)
        private set

    internal var offset by mutableIntStateOf(0)
        private set
    private var totalAvailable by mutableStateOf<Int?>(null)
    internal var anuncios by mutableStateOf<List<Mensaje>>(emptyList())
        private set
    private var newCount by mutableIntStateOf(0)
    private var newAnnouncementIdentities by
        mutableStateOf<Set<AnuncioIdentity>>(emptySet())
    private var refreshBackup by mutableStateOf<AnnouncementRefreshBackup?>(null)
    private var lastSeenTotal by mutableStateOf<Int?>(null)
    internal var loadedFilterKey by mutableStateOf<String?>(null)
        private set
    private var hasPersistedCurrentSnapshot by mutableStateOf(false)
    private var pageRequestGeneration by mutableIntStateOf(0)
    private val seenKeys = mutableSetOf<AnuncioIdentity>()

    internal var listPositionRestorationPending by mutableStateOf(
        paginationRestorationTargetOffset > pageSize || savedListIndex > 0
    )
        private set

    internal val paginationRestorationFilterKey: String
        get() = paginationRestorationFilterKeyState.value

    internal val paginationRestorationTargetOffset: Int
        get() = paginationRestorationTargetOffsetState.intValue

    private val savedListIndex: Int
        get() = savedListIndexState.intValue

    private val savedListScrollOffset: Int
        get() = savedListScrollOffsetState.intValue

    internal val hasMore: Boolean
        get() = totalAvailable?.let { offset < it } ?: true

    internal val announcementsForDisplay: List<Mensaje>
        get() = refreshBackup?.announcements ?: anuncios

    internal val announcementIdentitiesForDisplay: Set<AnuncioIdentity>
        get() = refreshBackup?.newAnnouncementIdentities ?: newAnnouncementIdentities

    internal fun clearFeedback() {
        error = null
        warning = null
    }

    internal fun recordListPosition(index: Int, scrollOffset: Int) {
        if (listPositionRestorationPending) return
        savedListIndexState.intValue = index
        savedListScrollOffsetState.intValue = scrollOffset
    }

    internal fun clampRestorationTargetToLoadedOffset() {
        paginationRestorationTargetOffsetState.intValue = offset
    }

    internal fun restoredListPosition(visibleItemCount: Int): MateriasListPosition {
        val maximumIndex = (visibleItemCount - 1).coerceAtLeast(0)
        return MateriasListPosition(
            index = savedListIndex.coerceIn(0, maximumIndex),
            scrollOffset = savedListScrollOffset,
        )
    }

    internal fun completeListPositionRestoration() {
        listPositionRestorationPending = false
    }

    internal suspend fun loadPage(
        reset: Boolean,
        forceRefresh: Boolean = false,
        currentMateriaId: () -> Int?,
        hasPendingNotification: Boolean,
        onClearNotificationOpenError: () -> Unit,
    ) {
        if (!reset && (loadingInitial || loadingMore)) return

        // Cada reinicio invalida respuestas anteriores para que un filtro viejo no reemplace
        // el contenido correspondiente a la selección más reciente.
        if (reset) {
            pageRequestGeneration += 1
            if (!hasPendingNotification) {
                onClearNotificationOpenError()
            }
        }
        val requestGeneration = pageRequestGeneration
        error = null
        warning = null
        val materiaId = currentMateriaId()
        val filterKey = materiaId?.toString() ?: "__all__"
        val existingSnapshotMatchesFilter = loadedFilterKey == filterKey
        val preservesVisibleSnapshot = reset && forceRefresh && existingSnapshotMatchesFilter
        if (reset) {
            if (paginationRestorationFilterKey != filterKey) {
                refreshBackup = null
                paginationRestorationFilterKeyState.value = filterKey
                paginationRestorationTargetOffsetState.intValue = 0
                savedListIndexState.intValue = 0
                savedListScrollOffsetState.intValue = 0
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
        fun keyOf(message: Mensaje): AnuncioIdentity = message.anuncioIdentity()
        fun isCurrentRequest(): Boolean {
            val currentFilterKey = currentMateriaId()?.toString() ?: "__all__"
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
                if (currentMateriaId() == null) {
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
            val newOnes = pageMessages.filter { message ->
                val key = keyOf(message)
                if (seenKeys.contains(key)) {
                    false
                } else {
                    seenKeys.add(key)
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
            paginationRestorationFilterKeyState.value = filterKey
            paginationRestorationTargetOffsetState.intValue = maxOf(
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
            val commitsGlobalRefresh = currentMateriaId() == null &&
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
            if (currentMateriaId() == null) {
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
}

@Composable
internal fun rememberMateriasFeedState(initialFilterKey: String): MateriasFeedState {
    val context = LocalContext.current
    val anunciosService = remember { AnunciosService() }
    val paginationRestorationFilterKeyState = rememberSaveable {
        mutableStateOf(initialFilterKey)
    }
    val paginationRestorationTargetOffsetState = rememberSaveable { mutableIntStateOf(0) }
    val savedListIndexState = rememberSaveable { mutableIntStateOf(0) }
    val savedListScrollOffsetState = rememberSaveable { mutableIntStateOf(0) }

    return remember(
        context,
        anunciosService,
        paginationRestorationFilterKeyState,
        paginationRestorationTargetOffsetState,
        savedListIndexState,
        savedListScrollOffsetState,
    ) {
        MateriasFeedState(
            context = context,
            anunciosService = anunciosService,
            paginationRestorationFilterKeyState = paginationRestorationFilterKeyState,
            paginationRestorationTargetOffsetState = paginationRestorationTargetOffsetState,
            savedListIndexState = savedListIndexState,
            savedListScrollOffsetState = savedListScrollOffsetState,
        )
    }
}
