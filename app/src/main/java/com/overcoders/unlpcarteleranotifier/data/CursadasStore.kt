/** Persiste snapshots de cursadas y sus marcas de lectura por materia. */
package com.overcoders.unlpcarteleranotifier.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.overcoders.unlpcarteleranotifier.model.CursadaInfo
import com.overcoders.unlpcarteleranotifier.model.cursadaMateriaKey
import com.overcoders.unlpcarteleranotifier.model.normalizedCursadaSeenEpochs
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.cursadasDataStore by preferencesDataStore(name = "cursadas_store")

object CursadasStore {
    private val CURSADAS_JSON_KEY = stringPreferencesKey("cursadas_json")
    private val SAVED_AT_KEY = longPreferencesKey("cursadas_saved_at")
    private val LAST_SEEN_BY_MATERIA_KEY = stringPreferencesKey("last_seen_by_materia")

    suspend fun load(context: Context): List<CursadaInfo> {
        val prefs = context.cursadasDataStore.data.first()
        val json = prefs[CURSADAS_JSON_KEY].orEmpty()
        if (json.isBlank()) return emptyList()
        return try {
            decode(json)
        } catch (_: Exception) {
            context.cursadasDataStore.edit { stored ->
                stored.remove(CURSADAS_JSON_KEY)
                stored.remove(SAVED_AT_KEY)
            }
            emptyList()
        }
    }

    suspend fun save(context: Context, cursadas: List<CursadaInfo>) {
        context.cursadasDataStore.edit { prefs ->
            prefs[CURSADAS_JSON_KEY] = encode(cursadas)
            prefs[SAVED_AT_KEY] = System.currentTimeMillis()
        }
    }

    suspend fun isFresh(context: Context, ttlMillis: Long): Boolean {
        val savedAt = context.cursadasDataStore.data.first()[SAVED_AT_KEY] ?: return false
        return System.currentTimeMillis() - savedAt in 0..ttlMillis
    }

    suspend fun hasSnapshot(context: Context): Boolean {
        val prefs = context.cursadasDataStore.data.first()
        return prefs.contains(CURSADAS_JSON_KEY) && prefs.contains(SAVED_AT_KEY)
    }

    suspend fun loadLastSeenEpochByMateria(context: Context): Map<String, Long> {
        val prefs = context.cursadasDataStore.data.first()
        val json = prefs[LAST_SEEN_BY_MATERIA_KEY].orEmpty()
        if (json.isBlank()) return emptyMap()
        return try {
            decodeLastSeen(json)
        } catch (_: Exception) {
            context.cursadasDataStore.edit { stored ->
                stored.remove(LAST_SEEN_BY_MATERIA_KEY)
            }
            emptyMap()
        }
    }

    suspend fun ensureSeenBaseline(context: Context, cursadas: List<CursadaInfo>): Map<String, Long> {
        var result = emptyMap<String, Long>()
        context.cursadasDataStore.edit { prefs ->
            val currentSeen = decodeLastSeenOrEmpty(prefs[LAST_SEEN_BY_MATERIA_KEY])
            if (currentSeen.isNotEmpty()) {
                result = currentSeen
                return@edit
            }

            val baseline = normalizedCursadaSeenEpochs(cursadas.mapNotNull { cursada ->
                val epoch = cursada.ultimaModificacionEpochMillis ?: return@mapNotNull null
                cursada.materia to epoch
            })
            result = baseline
            if (baseline.isNotEmpty()) {
                prefs[LAST_SEEN_BY_MATERIA_KEY] = encodeLastSeen(baseline)
            }
        }
        return result
    }

    suspend fun markAsSeen(context: Context, cursada: CursadaInfo) {
        val epoch = cursada.ultimaModificacionEpochMillis ?: return
        context.cursadasDataStore.edit { prefs ->
            val seen = decodeLastSeenOrEmpty(prefs[LAST_SEEN_BY_MATERIA_KEY]).toMutableMap()
            val materiaKey = cursada.materia.cursadaMateriaKey()
            val previous = seen[materiaKey]
            if (previous == null || epoch > previous) {
                seen[materiaKey] = epoch
                prefs[LAST_SEEN_BY_MATERIA_KEY] = encodeLastSeen(seen)
            }
        }
    }

    suspend fun markAllAsSeen(context: Context, cursadas: List<CursadaInfo>) {
        context.cursadasDataStore.edit { prefs ->
            val seen = decodeLastSeenOrEmpty(prefs[LAST_SEEN_BY_MATERIA_KEY]).toMutableMap()
            var hasChanges = false
            cursadas.forEach { cursada ->
                val epoch = cursada.ultimaModificacionEpochMillis ?: return@forEach
                val materiaKey = cursada.materia.cursadaMateriaKey()
                val previous = seen[materiaKey]
                if (previous == null || epoch > previous) {
                    seen[materiaKey] = epoch
                    hasChanges = true
                }
            }
            if (hasChanges) {
                prefs[LAST_SEEN_BY_MATERIA_KEY] = encodeLastSeen(seen)
            }
        }
    }

    private fun encodeLastSeen(byMateria: Map<String, Long>): String {
        val obj = JSONObject()
        normalizedCursadaSeenEpochs(
            byMateria.map { (materia, epoch) -> materia to epoch }
        ).forEach { (materia, epoch) ->
            obj.put(materia, epoch)
        }
        return obj.toString()
    }

    private fun decodeLastSeenOrEmpty(json: String?): Map<String, Long> =
        runCatching { decodeLastSeen(json.orEmpty()) }.getOrDefault(emptyMap())

    private fun decodeLastSeen(json: String): Map<String, Long> {
        if (json.isBlank()) return emptyMap()
        val obj = JSONObject(json)
        val entries = buildList {
            obj.keys().forEach { materia ->
                if (!obj.isNull(materia)) {
                    add(materia to obj.getLong(materia))
                }
            }
        }
        return normalizedCursadaSeenEpochs(entries)
    }

    private fun encode(cursadas: List<CursadaInfo>): String {
        val arr = JSONArray()
        cursadas.forEach { c ->
            val obj = JSONObject()
            obj.put("materia", c.materia)
            obj.put("inicioCursadaHtml", c.inicioCursadaHtml)
            obj.put("horariosCursadaHtml", c.horariosCursadaHtml)
            obj.put("ultimaModificacion", c.ultimaModificacion)
            obj.put(
                "ultimaModificacionEpochMillis",
                c.ultimaModificacionEpochMillis ?: JSONObject.NULL,
            )
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decode(json: String): List<CursadaInfo> {
        val arr = JSONArray(json)
        val out = ArrayList<CursadaInfo>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val epochValue = obj.get("ultimaModificacionEpochMillis")
            out.add(
                CursadaInfo(
                    materia = obj.getString("materia"),
                    inicioCursadaHtml = obj.getString("inicioCursadaHtml"),
                    horariosCursadaHtml = obj.getString("horariosCursadaHtml"),
                    ultimaModificacion = obj.getString("ultimaModificacion"),
                    ultimaModificacionEpochMillis =
                        if (epochValue === JSONObject.NULL) null
                        else obj.getLong("ultimaModificacionEpochMillis")
                )
            )
        }
        return out
            .sortedByDescending { it.ultimaModificacionEpochMillis ?: Long.MIN_VALUE }
            .distinctBy { it.materia.cursadaMateriaKey() }
    }
}
