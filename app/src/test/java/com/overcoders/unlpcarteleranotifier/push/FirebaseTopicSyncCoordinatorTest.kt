/** Verifica los planes de altas y bajas de tópicos Firebase. */
package com.overcoders.unlpcarteleranotifier.push

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FirebaseTopicSyncCoordinatorTest {
    @Test
    fun skipsRemoteOperationsWhenStateIsAlreadyCurrent() = runBlocking {
        val operations = FakeOperations(
            desired = setOf("avisos_all"),
            state = FirebaseTopicSyncState(setOf("avisos_all"), "fid-1")
        )

        val result = FirebaseTopicSyncCoordinator(emptyList()).sync("fid-1", operations)

        assertTrue(result is FirebaseTopicSyncResult.NoChanges)
        assertTrue(operations.subscribed.isEmpty())
        assertTrue(operations.unsubscribed.isEmpty())
        assertEquals(0, operations.saveCount)
    }

    @Test
    fun rehydratesAllDesiredTopicsWhenInstallationChanges() = runBlocking {
        val operations = FakeOperations(
            desired = setOf("avisos_all", "materias_all"),
            state = FirebaseTopicSyncState(setOf("materia_10"), "old-fid")
        )

        val result = FirebaseTopicSyncCoordinator(emptyList()).sync("new-fid", operations)

        assertTrue(result is FirebaseTopicSyncResult.Synced)
        assertEquals(listOf("avisos_all", "materias_all"), operations.subscribed)
        assertTrue(operations.unsubscribed.isEmpty())
        assertEquals("new-fid", operations.state.installationId)
    }

    @Test
    fun doesNotPersistPartialStateAfterRemoteFailure() = runBlocking {
        val initial = FirebaseTopicSyncState(emptySet(), "fid-1")
        val operations = FakeOperations(setOf("avisos_all"), initial).apply {
            subscribeFailuresRemaining = Int.MAX_VALUE
        }

        val result = FirebaseTopicSyncCoordinator(emptyList()).sync("fid-1", operations)

        assertTrue(result is FirebaseTopicSyncResult.Failed)
        assertEquals(initial, operations.state)
        assertEquals(0, operations.saveCount)
    }

    @Test
    fun retriesTransientTopicFailureBeforePersisting() = runBlocking {
        val operations = FakeOperations(
            desired = setOf("avisos_all"),
            state = FirebaseTopicSyncState(emptySet(), "fid-1")
        ).apply {
            subscribeFailuresRemaining = 1
        }

        val result = FirebaseTopicSyncCoordinator(listOf(0L)).sync("fid-1", operations)

        assertTrue(result is FirebaseTopicSyncResult.Synced)
        assertEquals(2, operations.subscribeAttempts)
        assertEquals(setOf("avisos_all"), operations.state.topics)
    }

    @Test
    fun serializesConcurrentSyncRequests() = runBlocking {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val coordinator = FirebaseTopicSyncCoordinator(emptyList())
        val operations = (1..2).map { index ->
            FakeOperations(
                desired = setOf("topic_$index"),
                state = FirebaseTopicSyncState()
            ).apply {
                onSubscribe = {
                    val current = active.incrementAndGet()
                    maxActive.updateAndGet { previous -> maxOf(previous, current) }
                    delay(25)
                    active.decrementAndGet()
                }
            }
        }

        operations.map { operation ->
            async(Dispatchers.Default) { coordinator.sync(null, operation) }
        }.awaitAll()

        assertEquals(1, maxActive.get())
    }

    private class FakeOperations(
        private val desired: Set<String>,
        var state: FirebaseTopicSyncState,
    ) : FirebaseTopicSyncOperations {
        val subscribed = mutableListOf<String>()
        val unsubscribed = mutableListOf<String>()
        var saveCount = 0
        var subscribeAttempts = 0
        var subscribeFailuresRemaining = 0
        var onSubscribe: suspend () -> Unit = {}

        override suspend fun desiredTopics(): Set<String> = desired

        override suspend fun loadState(): FirebaseTopicSyncState = state

        override suspend fun subscribe(topic: String) {
            subscribeAttempts += 1
            if (subscribeFailuresRemaining > 0) {
                subscribeFailuresRemaining -= 1
                throw IllegalStateException("transient")
            }
            onSubscribe()
            subscribed += topic
        }

        override suspend fun unsubscribe(topic: String) {
            unsubscribed += topic
        }

        override suspend fun saveState(state: FirebaseTopicSyncState) {
            this.state = state
            saveCount += 1
        }
    }
}
