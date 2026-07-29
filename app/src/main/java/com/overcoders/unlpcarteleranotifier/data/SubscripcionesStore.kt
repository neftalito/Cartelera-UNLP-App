/** Persiste y expone las materias elegidas para recibir notificaciones. */
package com.overcoders.unlpcarteleranotifier.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "subscripciones")

object SubscripcionesStore {

    private val IDS_KEY = stringSetPreferencesKey("materias_subscriptas")

    fun subscripcionesFlow(context: Context): Flow<Set<String>> {
        return context.dataStore.data
            .catch { error ->
                if (error is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw error
                }
            }
            .map { prefs -> prefs[IDS_KEY].orEmpty() }
    }

    internal suspend fun getSubscriptionsForTopicSync(context: Context): Set<String> =
        context.dataStore.data.first()[IDS_KEY].orEmpty()

    suspend fun subscribe(context: Context, idMateria: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[IDS_KEY]?.toMutableSet() ?: mutableSetOf()
            current.add(idMateria)
            prefs[IDS_KEY] = current
        }
    }

    suspend fun unsubscribe(context: Context, idMateria: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[IDS_KEY]?.toMutableSet() ?: mutableSetOf()
            current.remove(idMateria)
            prefs[IDS_KEY] = current
        }
    }

}
