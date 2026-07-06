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

object FirebaseTopicSyncManager {
    private const val TAG = "FirebaseTopicSync"

    suspend fun sync(context: Context) {
        if (!FirebaseInitializer.ensureInitialized(context)) {
            return
        }

        val notifyAll = SettingsStore.notifyAllFlow(context).first()
        val subscribedMateriaIds = SubscripcionesStore.subscripcionesFlow(context).first()
        val desiredTopics = FirebaseTopics.desiredTopics(notifyAll, subscribedMateriaIds)

        val lastSyncedTopics = SettingsStore.getLastSyncedFirebaseTopics(context)
        val lastSyncedToken = SettingsStore.getLastSyncedFirebaseToken(context)

        val currentToken = withContext(Dispatchers.IO) {
            runCatching {
                Tasks.await(FirebaseMessaging.getInstance().token)
            }.onFailure { error ->
                Log.w(TAG, "No se pudo obtener el token actual de Firebase.", error)
            }.getOrNull().orEmpty()
        }

        if (currentToken.isBlank()) {
            return
        }

        val tokenChanged = currentToken != lastSyncedToken
        if (!tokenChanged && desiredTopics == lastSyncedTopics) {
            return
        }

        val topicsToSubscribe = if (tokenChanged) {
            desiredTopics
        } else {
            desiredTopics - lastSyncedTopics
        }
        val topicsToUnsubscribe = if (tokenChanged) {
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
        SettingsStore.setLastSyncedFirebaseToken(context, currentToken)
    }
}
