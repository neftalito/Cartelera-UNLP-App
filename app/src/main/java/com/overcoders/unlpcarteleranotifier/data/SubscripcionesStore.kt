package com.overcoders.unlpcarteleranotifier.data


import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "subscripciones")

object SubscripcionesStore {

    private val IDS_KEY = stringSetPreferencesKey("materias_subscriptas")

    fun subscripcionesFlow(context: Context): Flow<Set<String>> {
        return context.dataStore.data.map { prefs ->
            prefs[IDS_KEY] ?: emptySet()
        }
    }

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
