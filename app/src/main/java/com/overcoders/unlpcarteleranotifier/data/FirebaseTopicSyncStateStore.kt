/** Guarda el último estado confirmado de sincronización de tópicos Firebase. */
package com.overcoders.unlpcarteleranotifier.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.overcoders.unlpcarteleranotifier.push.FirebaseTopicSyncState
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.firebaseTopicSyncDataStore by preferencesDataStore(
    name = "firebase_topic_sync_state"
)

/**
 * Estado operativo de FCM. Vive fuera de `settings` para que no se restaure en otro dispositivo.
 */
object FirebaseTopicSyncStateStore {
    private val TOPICS_KEY = stringSetPreferencesKey("topics")
    private val INSTALLATION_ID_KEY = stringPreferencesKey("installation_id")
    private val mutex = Mutex()

    internal fun stateFlow(context: Context): Flow<FirebaseTopicSyncState> =
        context.firebaseTopicSyncDataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }
            .map { preferences -> preferences.toState() }

    internal suspend fun load(context: Context): FirebaseTopicSyncState = mutex.withLock {
        context.firebaseTopicSyncDataStore.data.first().toState()
    }

    internal suspend fun save(context: Context, state: FirebaseTopicSyncState) = mutex.withLock {
        context.firebaseTopicSyncDataStore.edit { preferences ->
            preferences[TOPICS_KEY] = state.topics
            if (state.installationId.isBlank()) {
                preferences.remove(INSTALLATION_ID_KEY)
            } else {
                preferences[INSTALLATION_ID_KEY] = state.installationId
            }
        }
    }

    private fun androidx.datastore.preferences.core.Preferences.toState() =
        FirebaseTopicSyncState(
            topics = this[TOPICS_KEY].orEmpty(),
            installationId = this[INSTALLATION_ID_KEY].orEmpty()
        )
}
