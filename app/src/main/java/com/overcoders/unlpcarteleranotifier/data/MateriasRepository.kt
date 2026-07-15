/**
 * Comparte el catálogo de materias entre pantallas y coordina caché y refrescos remotos.
 */
package com.overcoders.unlpcarteleranotifier.data

import android.content.Context
import com.overcoders.unlpcarteleranotifier.model.MateriaCatalogItem
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class MateriasCatalogLoadResult(
    val items: List<MateriaCatalogItem>,
    val refreshFailure: Throwable? = null,
)

internal interface MateriasCatalogSource {
    suspend fun loadCached(): List<MateriaCatalogItem>
    suspend fun isCacheFresh(): Boolean
    suspend fun refresh(): List<MateriaCatalogItem>
}

internal class MateriasRepository internal constructor(
    private val source: MateriasCatalogSource,
) {
    private val loadMutex = Mutex()
    private val completedRefreshGeneration = AtomicLong(0)
    private val mutableItems = MutableStateFlow<List<MateriaCatalogItem>>(emptyList())
    private var cacheInitialized = false
    private var lastRefreshResult: MateriasCatalogLoadResult? = null

    val items: StateFlow<List<MateriaCatalogItem>> = mutableItems.asStateFlow()

    suspend fun load(forceRefresh: Boolean = false): MateriasCatalogLoadResult {
        // Una llamada forzada que empezó mientras otra refrescaba reutiliza ese resultado;
        // una llamada posterior vuelve a refrescar porque representa una acción nueva.
        val observedRefreshGeneration = completedRefreshGeneration.get()
        return loadMutex.withLock {
            initializeCacheIfNeeded()

            if (completedRefreshGeneration.get() > observedRefreshGeneration) {
                return@withLock lastRefreshResult
                    ?: MateriasCatalogLoadResult(items = mutableItems.value)
            }

            val hasFreshCache = mutableItems.value.isNotEmpty() && cacheIsFreshOrFalse()
            if (!forceRefresh && hasFreshCache) {
                return@withLock MateriasCatalogLoadResult(items = mutableItems.value)
            }

            val result = try {
                val refreshed = source.refresh()
                check(refreshed.isNotEmpty()) {
                    "La actualización del catálogo no devolvió materias."
                }
                mutableItems.value = refreshed
                cacheInitialized = true
                MateriasCatalogLoadResult(items = refreshed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                MateriasCatalogLoadResult(
                    items = mutableItems.value,
                    refreshFailure = e,
                )
            }

            lastRefreshResult = result
            completedRefreshGeneration.incrementAndGet()
            result
        }
    }

    private suspend fun initializeCacheIfNeeded() {
        if (cacheInitialized) return
        try {
            mutableItems.value = source.loadCached()
            cacheInitialized = true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Un fallo de almacenamiento no debe impedir intentar la fuente remota.
        }
    }

    private suspend fun cacheIsFreshOrFalse(): Boolean = try {
        source.isCacheFresh()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    companion object {
        @Volatile
        private var instance: MateriasRepository? = null

        fun get(context: Context): MateriasRepository = instance ?: synchronized(this) {
            instance ?: MateriasRepository(
                AndroidMateriasCatalogSource(context.applicationContext)
            ).also { instance = it }
        }
    }
}

private class AndroidMateriasCatalogSource(
    private val context: Context,
    private val service: MateriasService = MateriasService(),
) : MateriasCatalogSource {
    override suspend fun loadCached(): List<MateriaCatalogItem> = MateriasStore.load(context)

    override suspend fun isCacheFresh(): Boolean = MateriasStore.isFresh(context)

    override suspend fun refresh(): List<MateriaCatalogItem> = service.refresh(context)
}
