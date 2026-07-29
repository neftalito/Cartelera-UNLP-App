/**
 * Persiste preferencias generales y contadores de experiencia de usuario mediante DataStore.
 */
package com.overcoders.unlpcarteleranotifier.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")
internal const val UPDATE_PROMPT_COOLDOWN_MILLIS = 48L * 60L * 60L * 1_000L

/**
 * Fuente única de preferencias de usuario y pequeños baselines de interfaz.
 */
object SettingsStore {

    private val NOTIFY_ALL_KEY = booleanPreferencesKey("notify_all_materias")
    private val LAST_SEEN_TOTAL_KEY = intPreferencesKey("last_seen_total")
    private val HIDE_CANCELLED_MATERIAS_MESSAGES_KEY =
        booleanPreferencesKey("hide_cancelled_materias_messages")
    private val APP_OPEN_COUNT_KEY = intPreferencesKey("app_open_count")
    private val HAS_SHOWN_REVIEW_PROMPT_KEY =
        booleanPreferencesKey("has_shown_review_prompt")
    private val UPDATE_PROMPT_POSTPONED_VERSION_CODE_KEY =
        intPreferencesKey("update_prompt_postponed_version_code")
    private val UPDATE_PROMPT_POSTPONED_AT_EPOCH_MILLIS_KEY =
        longPreferencesKey("update_prompt_postponed_at_epoch_millis")

    fun notifyAllFlow(context: Context): Flow<Boolean> {
        return preferencesFlow(context).map { prefs ->
            prefs[NOTIFY_ALL_KEY] ?: true
        }
    }

    suspend fun setNotifyAll(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[NOTIFY_ALL_KEY] = enabled
        }
    }

    internal suspend fun getNotifyAllForTopicSync(context: Context): Boolean =
        context.settingsDataStore.data.first()[NOTIFY_ALL_KEY] ?: true

    suspend fun getLastSeenTotal(context: Context): Int {
        val prefs = context.settingsDataStore.data.first()
        return prefs[LAST_SEEN_TOTAL_KEY] ?: -1
    }

    suspend fun setLastSeenTotal(context: Context, total: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[LAST_SEEN_TOTAL_KEY] = total
        }
    }

    fun hideCancelledMateriasMessagesFlow(context: Context): Flow<Boolean> {
        return preferencesFlow(context).map { prefs ->
            prefs[HIDE_CANCELLED_MATERIAS_MESSAGES_KEY] ?: false
        }
    }

    suspend fun setHideCancelledMateriasMessages(context: Context, hide: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[HIDE_CANCELLED_MATERIAS_MESSAGES_KEY] = hide
        }
    }

    /** Cuenta aperturas y permite diferir el prompt hasta una sesión sin navegación dirigida. */
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
                shouldShow = true
            }
        }
        return shouldShow
    }

    /** Marca el prompt únicamente después de que el usuario pudo interactuar con él. */
    suspend fun markReviewPromptShown(context: Context) {
        context.settingsDataStore.edit { prefs ->
            prefs[HAS_SHOWN_REVIEW_PROMPT_KEY] = true
        }
    }

    internal suspend fun updatePromptDecision(
        context: Context,
        availableVersionCode: Int,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): UpdatePromptDecision {
        val prefs = preferencesFlow(context).first()
        return updatePromptDecision(
            availableVersionCode = availableVersionCode,
            postponedVersionCode = prefs[UPDATE_PROMPT_POSTPONED_VERSION_CODE_KEY],
            postponedAtEpochMillis = prefs[UPDATE_PROMPT_POSTPONED_AT_EPOCH_MILLIS_KEY],
            nowEpochMillis = nowEpochMillis,
        )
    }

    internal suspend fun postponeUpdatePrompt(
        context: Context,
        availableVersionCode: Int,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ) {
        context.settingsDataStore.edit { prefs ->
            prefs[UPDATE_PROMPT_POSTPONED_VERSION_CODE_KEY] = availableVersionCode
            prefs[UPDATE_PROMPT_POSTPONED_AT_EPOCH_MILLIS_KEY] = nowEpochMillis
        }
    }

    private fun preferencesFlow(context: Context): Flow<Preferences> =
        context.settingsDataStore.data.catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }

}

internal data class UpdatePromptDecision(
    val shouldShow: Boolean,
    val remainingCooldownMillis: Long?,
)

internal fun updatePromptDecision(
    availableVersionCode: Int,
    postponedVersionCode: Int?,
    postponedAtEpochMillis: Long?,
    nowEpochMillis: Long,
    cooldownMillis: Long = UPDATE_PROMPT_COOLDOWN_MILLIS,
): UpdatePromptDecision {
    if (
        availableVersionCode != postponedVersionCode ||
        postponedAtEpochMillis == null
    ) {
        return UpdatePromptDecision(
            shouldShow = true,
            remainingCooldownMillis = null,
        )
    }

    val elapsed = nowEpochMillis - postponedAtEpochMillis
    if (elapsed < 0L || elapsed >= cooldownMillis) {
        return UpdatePromptDecision(
            shouldShow = true,
            remainingCooldownMillis = null,
        )
    }

    return UpdatePromptDecision(
        shouldShow = false,
        remainingCooldownMillis = cooldownMillis - elapsed,
    )
}

internal fun isUpdatePromptEligible(
    availableVersionCode: Int,
    postponedVersionCode: Int?,
    postponedAtEpochMillis: Long?,
    nowEpochMillis: Long,
    cooldownMillis: Long = UPDATE_PROMPT_COOLDOWN_MILLIS,
): Boolean = updatePromptDecision(
    availableVersionCode = availableVersionCode,
    postponedVersionCode = postponedVersionCode,
    postponedAtEpochMillis = postponedAtEpochMillis,
    nowEpochMillis = nowEpochMillis,
    cooldownMillis = cooldownMillis,
).shouldShow
