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
    private const val DEFAULT_INTERVAL_MINUTES = 30
    private const val DEFAULT_CURSADAS_INTERVAL_MINUTES = 60

    private val INTERVAL_KEY = intPreferencesKey("interval_minutes")
    private val MATERIAS_AUTO_CHECK_ENABLED_KEY =
        booleanPreferencesKey("materias_auto_check_enabled")
    private val NOTIFY_ALL_KEY = booleanPreferencesKey("notify_all_materias")
    private val WIFI_ONLY_KEY = booleanPreferencesKey("wifi_only_updates")
    private val LAST_TOTAL_KEY = intPreferencesKey("last_total")
    private val LAST_SEEN_TOTAL_KEY = intPreferencesKey("last_seen_total")
    private val LAST_SCHEDULED_INTERVAL_KEY = intPreferencesKey("last_scheduled_interval")
    private val LAST_SCHEDULED_WIFI_ONLY_KEY =
        booleanPreferencesKey("last_scheduled_wifi_only")
    private val HAS_SEEN_DEVELOPMENT_WARNING_KEY =
        booleanPreferencesKey("has_seen_development_warning")
    private val CURSADAS_AUTO_CHECK_ENABLED_KEY =
        booleanPreferencesKey("cursadas_auto_check_enabled")
    private val CURSADAS_INTERVAL_KEY = intPreferencesKey("cursadas_interval_minutes")
    private val LAST_SCHEDULED_CURSADAS_INTERVAL_KEY =
        intPreferencesKey("last_scheduled_cursadas_interval")
    private val LAST_SCHEDULED_CURSADAS_WIFI_ONLY_KEY =
        booleanPreferencesKey("last_scheduled_cursadas_wifi_only")
    private val HIDE_CANCELLED_MATERIAS_MESSAGES_KEY =
        booleanPreferencesKey("hide_cancelled_materias_messages")
    private val APP_OPEN_COUNT_KEY = intPreferencesKey("app_open_count")
    private val HAS_SHOWN_REVIEW_PROMPT_KEY =
        booleanPreferencesKey("has_shown_review_prompt")
    private val LAST_SYNCED_FIREBASE_TOPICS_KEY =
        stringSetPreferencesKey("last_synced_firebase_topics")
    private val LAST_SYNCED_FIREBASE_TOKEN_KEY =
        stringPreferencesKey("last_synced_firebase_token")

    fun intervalFlow(context: Context): Flow<Int> {
        return context.settingsDataStore.data.map { prefs ->
            prefs[INTERVAL_KEY] ?: DEFAULT_INTERVAL_MINUTES
        }
    }

    suspend fun getInterval(context: Context): Int {
        val prefs = context.settingsDataStore.data.first()
        return prefs[INTERVAL_KEY] ?: DEFAULT_INTERVAL_MINUTES
    }

    fun materiasAutoCheckEnabledFlow(context: Context): Flow<Boolean> {
        return context.settingsDataStore.data.map { prefs ->
            prefs[MATERIAS_AUTO_CHECK_ENABLED_KEY] ?: true
        }
    }

    suspend fun materiasAutoCheckEnabled(context: Context): Boolean {
        val prefs = context.settingsDataStore.data.first()
        return prefs[MATERIAS_AUTO_CHECK_ENABLED_KEY] ?: true
    }

    suspend fun setMateriasAutoCheckEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[MATERIAS_AUTO_CHECK_ENABLED_KEY] = enabled
        }
    }

    fun notifyAllFlow(context: Context): Flow<Boolean> {
        return context.settingsDataStore.data.map { prefs ->
            prefs[NOTIFY_ALL_KEY] ?: true
        }
    }

    fun wifiOnlyFlow(context: Context): Flow<Boolean> {
        return context.settingsDataStore.data.map { prefs ->
            prefs[WIFI_ONLY_KEY] ?: false
        }
    }

    suspend fun setInterval(context: Context, minutes: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[INTERVAL_KEY] = minutes
        }
    }

    suspend fun setNotifyAll(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[NOTIFY_ALL_KEY] = enabled
        }
    }

    suspend fun setWifiOnly(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[WIFI_ONLY_KEY] = enabled
        }
    }

    suspend fun getLastTotal(context: Context): Int {
        val prefs = context.settingsDataStore.data.first()
        return prefs[LAST_TOTAL_KEY] ?: -1
    }

    suspend fun getWifiOnly(context: Context): Boolean {
        val prefs = context.settingsDataStore.data.first()
        return prefs[WIFI_ONLY_KEY] ?: false
    }

    suspend fun setLastTotal(context: Context, total: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[LAST_TOTAL_KEY] = total
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

    suspend fun getLastScheduledInterval(context: Context): Int {
        val prefs = context.settingsDataStore.data.first()
        return prefs[LAST_SCHEDULED_INTERVAL_KEY] ?: -1
    }

    suspend fun setLastScheduledInterval(context: Context, minutes: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[LAST_SCHEDULED_INTERVAL_KEY] = minutes
        }
    }

    suspend fun getLastScheduledWifiOnly(context: Context): Boolean? {
        val prefs = context.settingsDataStore.data.first()
        return prefs[LAST_SCHEDULED_WIFI_ONLY_KEY]
    }

    suspend fun setLastScheduledWifiOnly(context: Context, wifiOnly: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[LAST_SCHEDULED_WIFI_ONLY_KEY] = wifiOnly
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

    fun cursadasAutoCheckEnabledFlow(context: Context): Flow<Boolean> {
        return context.settingsDataStore.data.map { prefs ->
            prefs[CURSADAS_AUTO_CHECK_ENABLED_KEY] ?: false
        }
    }

    suspend fun cursadasAutoCheckEnabled(context: Context): Boolean {
        val prefs = context.settingsDataStore.data.first()
        return prefs[CURSADAS_AUTO_CHECK_ENABLED_KEY] ?: false
    }

    suspend fun setCursadasAutoCheckEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[CURSADAS_AUTO_CHECK_ENABLED_KEY] = enabled
        }
    }

    fun cursadasIntervalFlow(context: Context): Flow<Int> {
        return context.settingsDataStore.data.map { prefs ->
            prefs[CURSADAS_INTERVAL_KEY] ?: DEFAULT_CURSADAS_INTERVAL_MINUTES
        }
    }

    suspend fun getCursadasInterval(context: Context): Int {
        val prefs = context.settingsDataStore.data.first()
        return prefs[CURSADAS_INTERVAL_KEY] ?: DEFAULT_CURSADAS_INTERVAL_MINUTES
    }

    suspend fun setCursadasInterval(context: Context, minutes: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[CURSADAS_INTERVAL_KEY] = minutes
        }
    }

    suspend fun getLastScheduledCursadasInterval(context: Context): Int {
        val prefs = context.settingsDataStore.data.first()
        return prefs[LAST_SCHEDULED_CURSADAS_INTERVAL_KEY] ?: -1
    }

    suspend fun setLastScheduledCursadasInterval(context: Context, minutes: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[LAST_SCHEDULED_CURSADAS_INTERVAL_KEY] = minutes
        }
    }

    suspend fun getLastScheduledCursadasWifiOnly(context: Context): Boolean? {
        val prefs = context.settingsDataStore.data.first()
        return prefs[LAST_SCHEDULED_CURSADAS_WIFI_ONLY_KEY]
    }

    suspend fun setLastScheduledCursadasWifiOnly(context: Context, wifiOnly: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[LAST_SCHEDULED_CURSADAS_WIFI_ONLY_KEY] = wifiOnly
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

    suspend fun getLastSyncedFirebaseToken(context: Context): String {
        val prefs = context.settingsDataStore.data.first()
        return prefs[LAST_SYNCED_FIREBASE_TOKEN_KEY].orEmpty()
    }

    suspend fun setLastSyncedFirebaseToken(context: Context, token: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[LAST_SYNCED_FIREBASE_TOKEN_KEY] = token
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
