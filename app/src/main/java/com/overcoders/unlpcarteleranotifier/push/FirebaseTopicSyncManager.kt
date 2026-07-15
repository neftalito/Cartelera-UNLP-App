/** Ejecuta y serializa la sincronización efectiva de tópicos Firebase. */
package com.overcoders.unlpcarteleranotifier.push

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import com.overcoders.unlpcarteleranotifier.data.FirebaseTopicSyncStateStore
import com.overcoders.unlpcarteleranotifier.data.SettingsStore
import com.overcoders.unlpcarteleranotifier.data.SubscripcionesStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Mantiene las suscripciones reales de FCM alineadas con las preferencias del usuario. */
internal object FirebaseTopicSyncManager {
    private const val TAG = "FirebaseTopicSync"
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val registrationMutex = Mutex()
    private val coordinator = FirebaseTopicSyncCoordinator()

    fun requestRegistration(context: Context) {
        val applicationContext = context.applicationContext
        applicationScope.launch { register(applicationContext) }
    }

    fun requestSync(
        context: Context,
        currentInstallationId: String? = null,
    ) {
        val applicationContext = context.applicationContext
        applicationScope.launch {
            sync(applicationContext, currentInstallationId)
        }
    }

    private suspend fun register(context: Context) {
        if (!FirebaseInitializer.ensureInitialized(context)) return

        registrationMutex.withLock {
            try {
                retryTransiently {
                    withContext(Dispatchers.IO) {
                        Tasks.await(FirebaseMessaging.getInstance().register())
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "No se pudo registrar la instalación actual en Firebase.", error)
            }
        }
    }

    private suspend fun sync(
        context: Context,
        currentInstallationId: String? = null,
    ) {
        if (!FirebaseInitializer.ensureInitialized(context)) return

        val messaging = FirebaseMessaging.getInstance()
        val operations = object : FirebaseTopicSyncOperations {
            override suspend fun desiredTopics(): Set<String> {
                // Para una sincronización remota no usamos los fallbacks tolerantes de la UI:
                // ante un error de lectura es más seguro reintentar que aplicar topics por defecto.
                val notifyAll = SettingsStore.getNotifyAllForTopicSync(context)
                val subscribedMateriaIds = SubscripcionesStore.getSubscriptionsForTopicSync(context)
                return FirebaseTopics.desiredTopics(notifyAll, subscribedMateriaIds)
            }

            override suspend fun loadState(): FirebaseTopicSyncState =
                FirebaseTopicSyncStateStore.load(context)

            override suspend fun subscribe(topic: String) {
                withContext(Dispatchers.IO) {
                    Tasks.await(messaging.subscribeToTopic(topic))
                }
            }

            override suspend fun unsubscribe(topic: String) {
                withContext(Dispatchers.IO) {
                    Tasks.await(messaging.unsubscribeFromTopic(topic))
                }
            }

            override suspend fun saveState(state: FirebaseTopicSyncState) {
                FirebaseTopicSyncStateStore.save(context, state)
            }
        }

        when (val result = coordinator.sync(currentInstallationId, operations)) {
            is FirebaseTopicSyncResult.Failed -> {
                Log.w(
                    TAG,
                    "No se pudieron sincronizar los topics de Firebase; se reintentará en el próximo disparador.",
                    result.error
                )
            }

            FirebaseTopicSyncResult.NoChanges,
            FirebaseTopicSyncResult.Synced -> Unit
        }
    }

    private suspend fun retryTransiently(block: suspend () -> Unit) {
        val delays = listOf(500L, 1_500L)
        var retryIndex = 0
        while (true) {
            try {
                block()
                return
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (retryIndex >= delays.size) throw error
                delay(delays[retryIndex])
                retryIndex += 1
            }
        }
    }
}
