/** Calcula las altas y bajas necesarias para reconciliar tópicos FCM. */
package com.overcoders.unlpcarteleranotifier.push

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class FirebaseTopicSyncState(
    val topics: Set<String> = emptySet(),
    val installationId: String = "",
)

internal interface FirebaseTopicSyncOperations {
    suspend fun desiredTopics(): Set<String>
    suspend fun loadState(): FirebaseTopicSyncState
    suspend fun subscribe(topic: String)
    suspend fun unsubscribe(topic: String)
    suspend fun saveState(state: FirebaseTopicSyncState)
}

internal sealed interface FirebaseTopicSyncResult {
    data object NoChanges : FirebaseTopicSyncResult
    data object Synced : FirebaseTopicSyncResult
    data class Failed(val error: Exception) : FirebaseTopicSyncResult
}

/**
 * Serializa los cambios de topics y solo persiste un estado cuando todas las operaciones
 * remotas terminaron. Los reintentos son acotados para absorber fallos transitorios de FCM.
 */
internal class FirebaseTopicSyncCoordinator(
    private val retryDelaysMillis: List<Long> = listOf(500L, 1_500L),
) {
    private val mutex = Mutex()

    suspend fun sync(
        currentInstallationId: String?,
        operations: FirebaseTopicSyncOperations,
    ): FirebaseTopicSyncResult = mutex.withLock {
        try {
            val desiredTopics = operations.desiredTopics()
            val previousState = operations.loadState()
            val normalizedInstallationId = currentInstallationId?.trim().orEmpty()
            val registrationChanged = normalizedInstallationId.isNotBlank() &&
                normalizedInstallationId != previousState.installationId

            if (!registrationChanged && desiredTopics == previousState.topics) {
                return@withLock FirebaseTopicSyncResult.NoChanges
            }

            val topicsToSubscribe = if (registrationChanged) {
                desiredTopics
            } else {
                desiredTopics - previousState.topics
            }
            val topicsToUnsubscribe = if (registrationChanged) {
                emptySet()
            } else {
                previousState.topics - desiredTopics
            }

            topicsToSubscribe.sorted().forEach { topic ->
                retryTransiently { operations.subscribe(topic) }
            }
            topicsToUnsubscribe.sorted().forEach { topic ->
                retryTransiently { operations.unsubscribe(topic) }
            }

            operations.saveState(
                FirebaseTopicSyncState(
                    topics = desiredTopics,
                    installationId = normalizedInstallationId.ifBlank {
                        previousState.installationId
                    }
                )
            )
            FirebaseTopicSyncResult.Synced
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            FirebaseTopicSyncResult.Failed(error)
        }
    }

    private suspend fun retryTransiently(block: suspend () -> Unit) {
        var retryIndex = 0
        while (true) {
            try {
                block()
                return
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (retryIndex >= retryDelaysMillis.size) throw error
                delay(retryDelaysMillis[retryIndex])
                retryIndex += 1
            }
        }
    }
}
