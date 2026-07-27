/** Coordina la carga remota y el respaldo en memoria del estado de las aulas. */
package com.overcoders.unlpcarteleranotifier.ui.aulas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.overcoders.unlpcarteleranotifier.data.AulasMemoryCache
import com.overcoders.unlpcarteleranotifier.data.AulasService
import com.overcoders.unlpcarteleranotifier.data.ContentCachePolicy
import com.overcoders.unlpcarteleranotifier.model.AulaEstado
import com.overcoders.unlpcarteleranotifier.ui.common.ContentLoadingPhase
import com.overcoders.unlpcarteleranotifier.ui.common.cachedContentWarning
import com.overcoders.unlpcarteleranotifier.ui.common.contentLoadingPhase
import com.overcoders.unlpcarteleranotifier.ui.common.userFacingError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

@Stable
internal class AulasState(
    private val aulasService: AulasService,
) {
    internal var loading by mutableStateOf(true)
        private set
    internal var error by mutableStateOf<String?>(null)
        private set
    internal var warning by mutableStateOf<String?>(null)
        private set
    internal var aulas by mutableStateOf<List<AulaEstado>>(emptyList())
        private set
    private var hasResolvedAulasSnapshot by mutableStateOf(false)
    private val loadMutex = Mutex()

    internal val canShare: Boolean
        get() = !loading && error == null && aulas.isNotEmpty()

    internal val loadingPhase: ContentLoadingPhase
        get() = contentLoadingPhase(
            isLoading = loading,
            hasResolvedContent = hasResolvedAulasSnapshot,
        )

    internal suspend fun load(forceRefresh: Boolean = false) {
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
}

@Composable
internal fun rememberAulasState(): AulasState {
    val aulasService = remember { AulasService() }
    return remember(aulasService) { AulasState(aulasService) }
}
