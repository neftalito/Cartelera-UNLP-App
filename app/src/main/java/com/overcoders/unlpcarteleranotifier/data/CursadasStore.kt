package com.overcoders.unlpcarteleranotifier.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.overcoders.unlpcarteleranotifier.model.CursadaInfo
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.cursadasDataStore by preferencesDataStore(name = "cursadas_store")

object CursadasStore {
    private val CURSADAS_JSON_KEY = stringPreferencesKey("cursadas_json")
    private val TABLE_HASH_KEY = stringPreferencesKey("cursadas_table_hash")
    private val LAST_SEEN_BY_MATERIA_KEY = stringPreferencesKey("last_seen_by_materia")

    suspend fun load(context: Context): List<CursadaInfo> {
        val prefs = context.cursadasDataStore.data.first()
        val json = prefs[CURSADAS_JSON_KEY].orEmpty()
        return if (json.isBlank()) emptyList() else decode(json)
    }

    suspend fun save(context: Context, cursadas: List<CursadaInfo>, tableHash: String) {
        context.cursadasDataStore.edit { prefs ->
            prefs[CURSADAS_JSON_KEY] = encode(cursadas)
            prefs[TABLE_HASH_KEY] = tableHash
        }
    }

    suspend fun getTableHash(context: Context): String {
        val prefs = context.cursadasDataStore.data.first()
        return prefs[TABLE_HASH_KEY].orEmpty()
    }

    suspend fun loadLastSeenEpochByMateria(context: Context): Map<String, Long> {
        val prefs = context.cursadasDataStore.data.first()
        val json = prefs[LAST_SEEN_BY_MATERIA_KEY].orEmpty()
        if (json.isBlank()) return emptyMap()

        val obj = JSONObject(json)
        val out = mutableMapOf<String, Long>()
        obj.keys().forEach { materia ->
            if (!obj.isNull(materia)) {
                out[materia] = obj.optLong(materia)
            }
        }
        return out
    }

    suspend fun ensureSeenBaseline(context: Context, cursadas: List<CursadaInfo>): Map<String, Long> {
        val currentSeen = loadLastSeenEpochByMateria(context)
        if (currentSeen.isNotEmpty()) return currentSeen

        val baseline = cursadas.mapNotNull { cursada ->
            val epoch = cursada.ultimaModificacionEpochMillis ?: return@mapNotNull null
            cursada.materia to epoch
        }.toMap()

        if (baseline.isNotEmpty()) {
            saveLastSeenEpochByMateria(context, baseline)
        }
        return baseline
    }

    suspend fun markAsSeen(context: Context, cursada: CursadaInfo) {
        val epoch = cursada.ultimaModificacionEpochMillis ?: return
        val seen = loadLastSeenEpochByMateria(context).toMutableMap()
        val previous = seen[cursada.materia]
        if (previous == null || epoch > previous) {
            seen[cursada.materia] = epoch
            saveLastSeenEpochByMateria(context, seen)
        }
    }

    suspend fun markAllAsSeen(context: Context, cursadas: List<CursadaInfo>) {
        val seen = loadLastSeenEpochByMateria(context).toMutableMap()
        var hasChanges = false
        cursadas.forEach { cursada ->
            val epoch = cursada.ultimaModificacionEpochMillis ?: return@forEach
            val previous = seen[cursada.materia]
            if (previous == null || epoch > previous) {
                seen[cursada.materia] = epoch
                hasChanges = true
            }
        }
        if (hasChanges) {
            saveLastSeenEpochByMateria(context, seen)
        }
    }

    private suspend fun saveLastSeenEpochByMateria(context: Context, byMateria: Map<String, Long>) {
        val obj = JSONObject()
        byMateria.forEach { (materia, epoch) ->
            obj.put(materia, epoch)
        }
        context.cursadasDataStore.edit { prefs ->
            prefs[LAST_SEEN_BY_MATERIA_KEY] = obj.toString()
        }
    }

    private fun encode(cursadas: List<CursadaInfo>): String {
        val arr = JSONArray()
        cursadas.forEach { c ->
            val obj = JSONObject()
            obj.put("materia", c.materia)
            obj.put("inicioCursadaHtml", c.inicioCursadaHtml)
            obj.put("horariosCursadaHtml", c.horariosCursadaHtml)
            obj.put("ultimaModificacion", c.ultimaModificacion)
            obj.put("ultimaModificacionEpochMillis", c.ultimaModificacionEpochMillis)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decode(json: String): List<CursadaInfo> {
        val arr = JSONArray(json)
        val out = ArrayList<CursadaInfo>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            out.add(
                CursadaInfo(
                    materia = obj.optString("materia"),
                    inicioCursadaHtml = obj.optString("inicioCursadaHtml"),
                    horariosCursadaHtml = obj.optString("horariosCursadaHtml"),
                    ultimaModificacion = obj.optString("ultimaModificacion"),
                    ultimaModificacionEpochMillis =
                        if (obj.isNull("ultimaModificacionEpochMillis")) null
                        else obj.optLong("ultimaModificacionEpochMillis")
                )
            )
        }
        return out
    }
}
