/**
 * Verifica caché, fallback y single-flight del repositorio compartido de materias.
 */
package com.overcoders.unlpcarteleranotifier.data

import com.overcoders.unlpcarteleranotifier.model.MateriaCatalogItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MateriasRepositoryTest {
    private val cached = listOf(MateriaCatalogItem("1", "Análisis"))
    private val refreshed = listOf(MateriaCatalogItem("2", "Algoritmos"))

    @Test
    fun freshCacheAvoidsRemoteRefresh() = runBlocking {
        val source = FakeMateriasCatalogSource(cached = cached, fresh = true)
        val repository = MateriasRepository(source)

        val result = repository.load()

        assertEquals(cached, result.items)
        assertNull(result.refreshFailure)
        assertEquals(0, source.refreshCalls)
    }

    @Test
    fun concurrentInitialLoadsShareOneRefresh() = runBlocking {
        val refreshStarted = CompletableDeferred<Unit>()
        val releaseRefresh = CompletableDeferred<Unit>()
        val source = FakeMateriasCatalogSource(
            cached = cached,
            fresh = false,
            refreshed = refreshed,
            onRefresh = {
                refreshStarted.complete(Unit)
                releaseRefresh.await()
            },
        )
        val repository = MateriasRepository(source)

        val first = async { repository.load() }
        refreshStarted.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) {
            repository.load()
        }
        releaseRefresh.complete(Unit)

        assertEquals(refreshed, first.await().items)
        assertEquals(refreshed, second.await().items)
        assertEquals(1, source.refreshCalls)
        assertEquals(refreshed, repository.items.value)
    }

    @Test
    fun refreshFailureKeepsCachedCatalogAndReportsCause() = runBlocking {
        val source = FakeMateriasCatalogSource(
            cached = cached,
            fresh = false,
            refreshFailure = IllegalStateException("sin red"),
        )
        val repository = MateriasRepository(source)

        val result = repository.load()

        assertEquals(cached, result.items)
        assertNotNull(result.refreshFailure)
        assertEquals(cached, repository.items.value)
    }

    @Test
    fun cancelledCacheReadIsRetriedByNextLoad() = runBlocking {
        var cacheReads = 0
        val source = object : MateriasCatalogSource {
            override suspend fun loadCached(): List<MateriaCatalogItem> {
                cacheReads += 1
                if (cacheReads == 1) throw CancellationException("cancelada")
                return cached
            }

            override suspend fun isCacheFresh(): Boolean = true

            override suspend fun refresh(): List<MateriaCatalogItem> = refreshed
        }
        val repository = MateriasRepository(source)

        try {
            repository.load()
            throw AssertionError("La primera carga debía propagarse como cancelada")
        } catch (_: CancellationException) {
            // Esperado: una cancelación no consume la inicialización del caché.
        }

        val result = repository.load()

        assertEquals(2, cacheReads)
        assertEquals(cached, result.items)
    }

    @Test
    fun failedCacheReadIsRetriedWhenRemoteAlsoFails() = runBlocking {
        var cacheReads = 0
        val source = object : MateriasCatalogSource {
            override suspend fun loadCached(): List<MateriaCatalogItem> {
                cacheReads += 1
                if (cacheReads == 1) throw IllegalStateException("almacenamiento ocupado")
                return cached
            }

            override suspend fun isCacheFresh(): Boolean = true

            override suspend fun refresh(): List<MateriaCatalogItem> {
                throw IllegalStateException("sin red")
            }
        }
        val repository = MateriasRepository(source)

        val first = repository.load()
        val second = repository.load()

        assertNotNull(first.refreshFailure)
        assertEquals(emptyList<MateriaCatalogItem>(), first.items)
        assertEquals(2, cacheReads)
        assertEquals(cached, second.items)
        assertNull(second.refreshFailure)
    }
}

private class FakeMateriasCatalogSource(
    private val cached: List<MateriaCatalogItem>,
    private val fresh: Boolean,
    private val refreshed: List<MateriaCatalogItem> = emptyList(),
    private val refreshFailure: Throwable? = null,
    private val onRefresh: suspend () -> Unit = {},
) : MateriasCatalogSource {
    var refreshCalls: Int = 0
        private set

    override suspend fun loadCached(): List<MateriaCatalogItem> = cached

    override suspend fun isCacheFresh(): Boolean = fresh

    override suspend fun refresh(): List<MateriaCatalogItem> {
        refreshCalls += 1
        onRefresh()
        refreshFailure?.let { throw it }
        return refreshed
    }
}
