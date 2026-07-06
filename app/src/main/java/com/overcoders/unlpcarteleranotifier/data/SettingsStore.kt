package com.overcoders.unlpcarteleranotifier.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * Fuente única de preferencias persistidas y pequeños baselines operativos.
 *
 * Además de flags de UI, guarda metadatos usados para evitar notificaciones duplicadas
 * y reprogramaciones innecesarias de WorkManager.
 */
object SettingsStore {

    private val NOTIFY_ALL_KEY = booleanPreferencesKey("notify_all_materias")
    private val LAST_SEEN_TOTAL_KEY = intPreferencesKey("last_seen_total")
    private val HAS_SEEN_DEVELOPMENT_WARNING_KEY =
        booleanPreferencesKey("has_seen_development_warning")
    private val HIDE_CANCELLED_MATERIAS_MESSAGES_KEY =
        booleanPreferencesKey("hide_cancelled_materias_messages")
    private val APP_OPEN_COUNT_KEY = intPreferencesKey("app_open_count")
    private val HAS_SHOWN_REVIEW_PROMPT_KEY =
        booleanPreferencesKey("has_shown_review_prompt")
    private val LAST_SYNCED_FIREBASE_TOPICS_KEY =
        stringSetPreferencesKey("last_synced_firebase_topics")
    private val LAST_SYNCED_FIREBASE_INSTALLATION_ID_KEY =
        stringPreferencesKey("last_synced_firebase_installation_id")

    fun notifyAllFlow(context: Context): Flow<Boolean> {
        return context.settingsDataStore.data.map { prefs ->
            prefs[NOTIFY_ALL_KEY] ?: true
        }
    }

    suspend fun setNotifyAll(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[NOTIFY_ALL_KEY] = enabled
        }
    }

    suspend fun getLastSeenTotal(context: Context): Int {
        val prefs = context.settingsDataStore.data.first()
        return prefs[LAST_SEEN_TOTAL_KEY] ?: -1
    }

    suspend fun setLastSeenTotal(context: Context, total: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[LAST_SEEN_TOTAL_KEY] = total
        }
    }

    suspend fun hasSeenDevelopmentWarning(context: Context): Boolean {
        val prefs = context.settingsDataStore.data.first()
        return prefs[HAS_SEEN_DEVELOPMENT_WARNING_KEY] ?: false
    }

    suspend fun setHasSeenDevelopmentWarning(context: Context, hasSeen: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[HAS_SEEN_DEVELOPMENT_WARNING_KEY] = hasSeen
        }
    }

    fun hideCancelledMateriasMessagesFlow(context: Context): Flow<Boolean> {
        return context.settingsDataStore.data.map { prefs ->
            prefs[HIDE_CANCELLED_MATERIAS_MESSAGES_KEY] ?: false
        }
    }

    suspend fun setHideCancelledMateriasMessages(context: Context, hide: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[HIDE_CANCELLED_MATERIAS_MESSAGES_KEY] = hide
        }
    }

    suspend fun getLastSyncedFirebaseTopics(context: Context): Set<String> {
        val prefs = context.settingsDataStore.data.first()
        return prefs[LAST_SYNCED_FIREBASE_TOPICS_KEY] ?: emptySet()
    }

    suspend fun setLastSyncedFirebaseTopics(context: Context, topics: Set<String>) {
        context.settingsDataStore.edit { prefs ->
            prefs[LAST_SYNCED_FIREBASE_TOPICS_KEY] = topics
        }
    }

    suspend fun getLastSyncedFirebaseInstallationId(context: Context): String {
        val prefs = context.settingsDataStore.data.first()
        return prefs[LAST_SYNCED_FIREBASE_INSTALLATION_ID_KEY].orEmpty()
    }

    suspend fun setLastSyncedFirebaseInstallationId(context: Context, installationId: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[LAST_SYNCED_FIREBASE_INSTALLATION_ID_KEY] = installationId
        }
    }

    /**
     * Cuenta aperturas hasta mostrar el prompt una sola vez. Devuelve `true` únicamente
     * en la ejecución que cruza el umbral y marca el prompt como ya mostrado.
     */
    suspend fun registerAppOpenAndShouldShowReviewPrompt(context: Context): Boolean {
        var shouldShow = false
        context.settingsDataStore.edit { prefs ->
            val hasShownPrompt = prefs[HAS_SHOWN_REVIEW_PROMPT_KEY] ?: false
            if (hasShownPrompt) {
                return@edit
            }
            val currentCount = prefs[APP_OPEN_COUNT_KEY] ?: 0
            val updatedCount = currentCount + 1
            prefs[APP_OPEN_COUNT_KEY] = updatedCount
            if (updatedCount >= 5) {
                prefs[HAS_SHOWN_REVIEW_PROMPT_KEY] = true
                shouldShow = true
            }
        }
        return shouldShow
    }

}
