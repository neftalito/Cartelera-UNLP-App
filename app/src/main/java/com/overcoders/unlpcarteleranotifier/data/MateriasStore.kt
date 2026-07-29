/** Persiste el catálogo de materias y la fecha de su última actualización. */
package com.overcoders.unlpcarteleranotifier.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.overcoders.unlpcarteleranotifier.model.MateriaCatalogItem
import com.overcoders.unlpcarteleranotifier.model.toMateriaCatalogIdOrNull
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.materiasDataStore by preferencesDataStore(name = "materias_store")

object MateriasStore {

    private val MATERIAS_JSON_KEY = stringPreferencesKey("materias_json")
    private val SAVED_AT_KEY = longPreferencesKey("materias_saved_at")

    suspend fun load(context: Context): List<MateriaCatalogItem> {
        val prefs = context.materiasDataStore.data
        val storedPrefs = prefs.first()
        val json = storedPrefs[MATERIAS_JSON_KEY].orEmpty()

        if (json.isBlank()) return emptyList()

        return try {
            decode(json)
        } catch (_: Exception) {
            context.materiasDataStore.edit { prefs ->
                prefs.remove(MATERIAS_JSON_KEY)
                prefs.remove(SAVED_AT_KEY)
            }
            emptyList()
        }
    }

    suspend fun save(context: Context, materias: List<MateriaCatalogItem>) {
        val json = encode(materias)
        context.materiasDataStore.edit { prefs ->
            prefs[MATERIAS_JSON_KEY] = json
            prefs[SAVED_AT_KEY] = System.currentTimeMillis()
        }
    }

    suspend fun isFresh(
        context: Context,
        ttlMillis: Long = ContentCachePolicy.CATALOG_TTL_MILLIS,
    ): Boolean {
        val savedAt = context.materiasDataStore.data.first()[SAVED_AT_KEY] ?: return false
        return System.currentTimeMillis() - savedAt in 0..ttlMillis
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
            val id = obj.getString("id").toMateriaCatalogIdOrNull()
                ?: error("Identificador de materia inválido en caché.")
            val nombre = obj.getString("nombre").trim()
            require(nombre.isNotEmpty())
            out.add(
                MateriaCatalogItem(
                    id = id.toString(),
                    nombre = nombre,
                )
            )
        }
        return out
    }
}
