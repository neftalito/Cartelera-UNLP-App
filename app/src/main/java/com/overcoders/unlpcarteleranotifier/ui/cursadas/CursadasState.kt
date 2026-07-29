/** Coordina el snapshot, las novedades y las marcas de lectura de cursadas. */
package com.overcoders.unlpcarteleranotifier.ui.cursadas

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.overcoders.unlpcarteleranotifier.data.CursadasRepository
import com.overcoders.unlpcarteleranotifier.data.CursadasStore
import com.overcoders.unlpcarteleranotifier.model.CursadaInfo
import com.overcoders.unlpcarteleranotifier.model.cursadaMateriaKey
import com.overcoders.unlpcarteleranotifier.model.normalizedCursadaSeenEpochs
import com.overcoders.unlpcarteleranotifier.ui.common.cachedContentWarning
import com.overcoders.unlpcarteleranotifier.ui.common.userFacingError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Stable
internal class CursadasState(
    private val context: Context,
) {
    private val refreshMutex = Mutex()

    internal var loading by mutableStateOf(true)
        private set
    internal var loadError by mutableStateOf<String?>(null)
        private set
    internal var warning by mutableStateOf<String?>(null)
        private set
    internal var actionError by mutableStateOf<String?>(null)
        private set
    internal var cursadas by mutableStateOf<List<CursadaInfo>>(emptyList())
        private set
    internal var cursadasConNovedades by mutableStateOf<Set<String>>(emptySet())
        private set
    internal var hasResolvedSnapshot by mutableStateOf(false)
        private set
    internal var initialLoadCompleted by mutableStateOf(false)
        private set

    internal suspend fun refresh(
        forceRefresh: Boolean = false,
        onRefreshStart: () -> Unit = {},
    ): Boolean {
        return refreshMutex.withLock {
            onRefreshStart()
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
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Evita marcar falsamente todo como nuevo si no se puede guardar el baseline.
                    currentCursadasBaseline(refreshedCursadas)
                }

                val refreshedCursadasConNovedades = cursadasWithNews(
                    cursadas = refreshedCursadas,
                    seenEpochByMateria = vistosPorMateria,
                )

                // Se publican juntos al final para que la resolución de una notificación no
                // observe una lista remota con las novedades del snapshot anterior.
                cursadas = refreshedCursadas
                cursadasConNovedades = refreshedCursadasConNovedades
                hasResolvedSnapshot = true
                true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (hasResolvedSnapshot) {
                    warning = cachedContentWarning(
                        operation = "actualizar las cursadas",
                        error = error,
                    )
                } else {
                    loadError = userFacingError(
                        operation = "cargar las cursadas",
                        error = error,
                    )
                }
                false
            } finally {
                loading = false
            }
        }
    }

    internal suspend fun loadCachedSnapshot() {
        val (cached, hasCachedSnapshot) = try {
            withContext(Dispatchers.IO) {
                CursadasStore.load(context) to CursadasStore.hasSnapshot(context)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList<CursadaInfo>() to false
        }
        if (hasCachedSnapshot) {
            cursadas = cached
            hasResolvedSnapshot = true
        }
    }

    internal fun completeInitialLoad() {
        initialLoadCompleted = true
    }

    internal suspend fun markAsSeen(cursada: CursadaInfo): Boolean {
        val persisted = persistSeenState {
            CursadasStore.markAsSeen(context, cursada)
        }
        if (persisted) {
            cursadasConNovedades = cursadasConNovedades - cursada.materia.cursadaMateriaKey()
        }
        return persisted
    }

    internal suspend fun markAllAsSeen(): Boolean {
        val persisted = persistSeenState {
            CursadasStore.markAllAsSeen(context, cursadas)
        }
        if (persisted) {
            cursadasConNovedades = emptySet()
        }
        return persisted
    }

    private suspend fun persistSeenState(block: suspend () -> Unit): Boolean {
        actionError = null
        return try {
            withContext(Dispatchers.IO) { block() }
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            actionError = userFacingError(
                operation = "guardar el estado de lectura de las cursadas",
                error = error,
            )
            false
        }
    }
}

@Composable
internal fun rememberCursadasState(): CursadasState {
    val context = LocalContext.current
    return remember(context) { CursadasState(context) }
}

internal fun currentCursadasBaseline(cursadas: List<CursadaInfo>): Map<String, Long> =
    normalizedCursadaSeenEpochs(cursadas.mapNotNull { cursada ->
        cursada.ultimaModificacionEpochMillis?.let { epoch -> cursada.materia to epoch }
    })

internal fun cursadasWithNews(
    cursadas: List<CursadaInfo>,
    seenEpochByMateria: Map<String, Long>,
): Set<String> = cursadas
    .filter { cursada ->
        val ultimaVista = seenEpochByMateria[
            cursada.materia.cursadaMateriaKey()
        ] ?: Long.MIN_VALUE
        val ultimaActualizacion = cursada.ultimaModificacionEpochMillis ?: Long.MIN_VALUE
        ultimaActualizacion > ultimaVista
    }
    .map { cursada -> cursada.materia.cursadaMateriaKey() }
    .toSet()
