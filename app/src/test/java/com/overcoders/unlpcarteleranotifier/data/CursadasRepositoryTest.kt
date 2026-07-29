/** Verifica el flujo real de caché, red y persistencia del repositorio de cursadas. */
package com.overcoders.unlpcarteleranotifier.data

import com.overcoders.unlpcarteleranotifier.model.CursadaInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class CursadasRepositoryTest {
    private val cached = listOf(cursada("Análisis", 1L))
    private val refreshed = listOf(cursada("Algoritmos", 2L))

    @Test
    fun freshCacheAvoidsRemoteRefreshAndPersistence() = runBlocking {
        val source = FakeCursadasSource(cached = cached, fresh = true, refreshed = refreshed)

        val result = CursadasLoader(source).load()

        assertEquals(cached, result)
        assertEquals(1, source.cacheLoadCalls)
        assertEquals(1, source.freshnessCalls)
        assertEquals(0, source.refreshCalls)
        assertEquals(0, source.saveCalls)
    }

    @Test
    fun forcedRefreshBypassesFreshCacheAndPersistsRemoteResult() = runBlocking {
        val source = FakeCursadasSource(cached = cached, fresh = true, refreshed = refreshed)

        val result = CursadasLoader(source).load(forceRefresh = true)

        assertEquals(refreshed, result)
        assertEquals(1, source.refreshCalls)
        assertEquals(listOf(refreshed), source.savedSnapshots)
    }

    @Test
    fun staleCacheRefreshesAndPersistsRemoteResult() = runBlocking {
        val source = FakeCursadasSource(cached = cached, fresh = false, refreshed = refreshed)

        val result = CursadasLoader(source).load()

        assertEquals(refreshed, result)
        assertEquals(1, source.refreshCalls)
        assertEquals(listOf(refreshed), source.savedSnapshots)
    }

    @Test
    fun failedCacheReadStillAttemptsRemoteRefresh() = runBlocking {
        val source = FakeCursadasSource(
            cached = cached,
            fresh = true,
            refreshed = refreshed,
            cacheLoadFailure = IllegalStateException("almacenamiento ocupado"),
        )

        val result = CursadasLoader(source).load()

        assertEquals(refreshed, result)
        assertEquals(0, source.freshnessCalls)
        assertEquals(1, source.refreshCalls)
        assertEquals(listOf(refreshed), source.savedSnapshots)
    }

    @Test
    fun cancelledCacheReadIsPropagatedWithoutRemoteWork() = runBlocking {
        val source = FakeCursadasSource(
            cached = cached,
            fresh = true,
            refreshed = refreshed,
            cacheLoadFailure = CancellationException("cancelada"),
        )

        expectCancellation { CursadasLoader(source).load() }

        assertEquals(0, source.freshnessCalls)
        assertEquals(0, source.refreshCalls)
        assertEquals(0, source.saveCalls)
    }

    @Test
    fun failedFreshnessCheckTreatsCacheAsStale() = runBlocking {
        val source = FakeCursadasSource(
            cached = cached,
            fresh = true,
            refreshed = refreshed,
            freshnessFailure = IllegalStateException("marca inválida"),
        )

        val result = CursadasLoader(source).load()

        assertEquals(refreshed, result)
        assertEquals(1, source.refreshCalls)
        assertEquals(listOf(refreshed), source.savedSnapshots)
    }

    @Test
    fun cancelledFreshnessCheckIsPropagatedWithoutRemoteWork() = runBlocking {
        val source = FakeCursadasSource(
            cached = cached,
            fresh = true,
            refreshed = refreshed,
            freshnessFailure = CancellationException("cancelada"),
        )

        expectCancellation { CursadasLoader(source).load() }

        assertEquals(0, source.refreshCalls)
        assertEquals(0, source.saveCalls)
    }

    @Test
    fun persistenceFailureDoesNotDiscardValidRemoteResult() = runBlocking {
        val source = FakeCursadasSource(
            cached = cached,
            fresh = false,
            refreshed = refreshed,
            saveFailure = IllegalStateException("sin espacio"),
        )

        val result = CursadasLoader(source).load()

        assertEquals(refreshed, result)
        assertEquals(1, source.saveCalls)
    }

    @Test
    fun cancelledPersistenceIsPropagated() = runBlocking {
        val source = FakeCursadasSource(
            cached = cached,
            fresh = false,
            refreshed = refreshed,
            saveFailure = CancellationException("cancelada"),
        )

        expectCancellation { CursadasLoader(source).load() }

        assertEquals(1, source.saveCalls)
    }

    private suspend fun expectCancellation(block: suspend () -> Unit) {
        try {
            block()
            fail("La cancelación debía propagarse")
        } catch (_: CancellationException) {
            // Esperado: la cancelación no debe convertirse en un fallo recuperable.
        }
    }

    private fun cursada(materia: String, epoch: Long) = CursadaInfo(
        materia = materia,
        inicioCursadaHtml = "Inicio",
        horariosCursadaHtml = "Horarios",
        ultimaModificacion = "01/01/2026 10:00",
        ultimaModificacionEpochMillis = epoch,
    )
}

private class FakeCursadasSource(
    private val cached: List<CursadaInfo>,
    private val fresh: Boolean,
    private val refreshed: List<CursadaInfo>,
    private val cacheLoadFailure: Throwable? = null,
    private val freshnessFailure: Throwable? = null,
    private val saveFailure: Throwable? = null,
) : CursadasSource {
    var cacheLoadCalls = 0
        private set
    var freshnessCalls = 0
        private set
    var refreshCalls = 0
        private set
    var saveCalls = 0
        private set
    val savedSnapshots = mutableListOf<List<CursadaInfo>>()

    override suspend fun loadCached(): List<CursadaInfo> {
        cacheLoadCalls += 1
        cacheLoadFailure?.let { throw it }
        return cached
    }

    override suspend fun isCacheFresh(): Boolean {
        freshnessCalls += 1
        freshnessFailure?.let { throw it }
        return fresh
    }

    override suspend fun refresh(): List<CursadaInfo> {
        refreshCalls += 1
        return refreshed
    }

    override suspend fun save(cursadas: List<CursadaInfo>) {
        saveCalls += 1
        saveFailure?.let { throw it }
        savedSnapshots += cursadas
    }
}
