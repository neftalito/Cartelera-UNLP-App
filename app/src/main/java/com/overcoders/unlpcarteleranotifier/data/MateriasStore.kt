package com.overcoders.unlpcarteleranotifier.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.overcoders.unlpcarteleranotifier.model.MateriaCatalogItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.materiasDataStore by preferencesDataStore(name = "materias_store")

object MateriasStore {

    private val MATERIAS_JSON_KEY = stringPreferencesKey("materias_json")

    @Suppress("unused")
    fun materiasFlow(context: Context): Flow<List<MateriaCatalogItem>> {
        return context.materiasDataStore.data.map { prefs ->
            val json = prefs[MATERIAS_JSON_KEY].orEmpty()
            if (json.isBlank()) emptyList() else decode(json)
        }
    }

    suspend fun load(context: Context): List<MateriaCatalogItem> {
        val prefs = context.materiasDataStore.data
        val storedPrefs = prefs.first()
        val json = storedPrefs[MATERIAS_JSON_KEY].orEmpty()

        if (json.isBlank()) return emptyList()

        return decode(json)
    }

    suspend fun save(context: Context, materias: List<MateriaCatalogItem>) {
        val json = encode(materias)
        context.materiasDataStore.edit { prefs ->
            prefs[MATERIAS_JSON_KEY] = json
        }
    }

    @Suppress("unused")
    suspend fun clear(context: Context) {
        context.materiasDataStore.edit { prefs ->
            prefs.remove(MATERIAS_JSON_KEY)
        }
    }

    private fun encode(materias: List<MateriaCatalogItem>): String {
        val arr = JSONArray()
        materias.forEach { m ->
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("nombre", m.nombre)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decode(json: String): List<MateriaCatalogItem> {
        val arr = JSONArray(json)
        val out = ArrayList<MateriaCatalogItem>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            out.add(
                MateriaCatalogItem(
                    id = obj.getString("id"),
                    nombre = obj.getString("nombre")
                )
            )
        }
        return out
    }
}
