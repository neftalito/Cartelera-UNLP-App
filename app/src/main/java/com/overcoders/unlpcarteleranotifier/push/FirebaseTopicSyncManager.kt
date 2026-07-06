package com.overcoders.unlpcarteleranotifier.push

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import com.overcoders.unlpcarteleranotifier.data.SettingsStore
import com.overcoders.unlpcarteleranotifier.data.SubscripcionesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Mantiene sincronizadas las suscripciones reales de FCM con las preferencias del usuario.
 *
 * Mientras existan instalaciones que llegan desde versiones previas sin topics hidratados,
 * este sync también cumple el rol de migración automática desde las preferencias locales
 * viejas hacia el esquema nuevo de topics. Una vez que toda la base haya pasado por esta
 * versión, esta compatibilidad transitoria ya no debería ser necesaria.
 */
object FirebaseTopicSyncManager {
    private const val TAG = "FirebaseTopicSync"

    suspend fun register(context: Context) {
        if (!FirebaseInitializer.ensureInitialized(context)) {
            return
        }

        withContext(Dispatchers.IO) {
            runCatching {
                Tasks.await(FirebaseMessaging.getInstance().register())
            }.onFailure { error ->
                Log.w(TAG, "No se pudo registrar la instalación actual en Firebase.", error)
            }
        }
    }

    suspend fun sync(
        context: Context,
        currentInstallationId: String? = null,
    ) {
        if (!FirebaseInitializer.ensureInitialized(context)) {
            return
        }

        val notifyAll = SettingsStore.notifyAllFlow(context).first()
        val subscribedMateriaIds = SubscripcionesStore.subscripcionesFlow(context).first()
        // Compatibilidad transitoria: las preferencias locales siguen siendo la fuente de verdad
        // para rehidratar topics en instalaciones que se actualizaron desde versiones previas.
        val desiredTopics = FirebaseTopics.desiredTopics(notifyAll, subscribedMateriaIds)

        val lastSyncedTopics = SettingsStore.getLastSyncedFirebaseTopics(context)
        val lastSyncedInstallationId = SettingsStore.getLastSyncedFirebaseInstallationId(context)
        val normalizedInstallationId = currentInstallationId?.trim().orEmpty()
        val registrationChanged = normalizedInstallationId.isNotBlank() &&
            normalizedInstallationId != lastSyncedInstallationId

        if (!registrationChanged && desiredTopics == lastSyncedTopics) {
            return
        }

        val topicsToSubscribe = if (registrationChanged) {
            // Si cambia la instalación registrada, rehidratamos todos los topics sobre el nuevo FID.
            // Esto también cubre la migración temporal de instalaciones ya actualizadas.
            desiredTopics
        } else {
            desiredTopics - lastSyncedTopics
        }
        val topicsToUnsubscribe = if (registrationChanged) {
            emptySet()
        } else {
            lastSyncedTopics - desiredTopics
        }

        withContext(Dispatchers.IO) {
            topicsToSubscribe.sorted().forEach { topic ->
                Tasks.await(FirebaseMessaging.getInstance().subscribeToTopic(topic))
            }
            topicsToUnsubscribe.sorted().forEach { topic ->
                Tasks.await(FirebaseMessaging.getInstance().unsubscribeFromTopic(topic))
            }
        }

        SettingsStore.setLastSyncedFirebaseTopics(context, desiredTopics)
        if (normalizedInstallationId.isNotBlank()) {
            SettingsStore.setLastSyncedFirebaseInstallationId(context, normalizedInstallationId)
        }
    }
}
