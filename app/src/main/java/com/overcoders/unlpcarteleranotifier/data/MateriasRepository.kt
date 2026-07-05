package com.overcoders.unlpcarteleranotifier.data

import android.content.Context
import java.text.Normalizer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Cachea la relación entre id de materia y nombre normalizado.
 *
 * Se usa sobre todo en el pipeline de notificaciones, donde algunos endpoints sólo exponen
 * nombres visibles y no el id original elegido por el usuario al suscribirse.
 */
object MateriasRepository {
    private val mutex = Mutex()
    private var cachedIdToName: Map<String, String> = emptyMap()

    fun invalidateCache() {
        cachedIdToName = emptyMap()
    }

    suspend fun loadIdToNameMap(context: Context): Map<String, String> {
        return mutex.withLock {
            if (cachedIdToName.isNotEmpty()) return cachedIdToName

            val materias = MateriasStore.load(context).ifEmpty {
                MateriasService().loadOrFetch(context)
            }

            val map = materias.associate { it.id to normalizeName(it.nombre) }
            cachedIdToName = map
            map
        }
    }

    /**
     * Normaliza nombres para poder comparar feeds heterogéneos.
     *
     * Elimina sufijos de período ("segundo semestre", "anual", etc.), tildes y espacios
     * variables para que anuncios, cursadas y catálogo se puedan cruzar entre sí.
     */
    fun normalizeName(value: String): String {
        val trimmed = value.trim()
        val withoutTerm = trimmed.replace(
            "\\s*\\((?i)(primero|segundo|primer\\s+semestre|segundo\\s+semestre|anual|verano|ingreso)\\)".toRegex(),
            " "
        )
        val normalized = Normalizer.normalize(withoutTerm, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
        return normalized
            .lowercase()
            .replace("[\\s\\u00A0]+".toRegex(), " ")
            .trim()
    }
}
