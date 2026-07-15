/** Representa la información publicada y la fecha vigente de una cursada. */
package com.overcoders.unlpcarteleranotifier.model

import java.util.Locale

data class CursadaInfo(
    val materia: String,
    val inicioCursadaHtml: String,
    val horariosCursadaHtml: String,
    val ultimaModificacion: String,
    val ultimaModificacionEpochMillis: Long?
)

internal fun String.cursadaMateriaKey(): String = trim().lowercase(Locale.ROOT)

internal fun normalizedCursadaSeenEpochs(
    entries: Iterable<Pair<String, Long>>,
): Map<String, Long> {
    val normalized = mutableMapOf<String, Long>()
    entries.forEach { (materia, epoch) ->
        val key = materia.cursadaMateriaKey()
        if (key.isNotEmpty() && epoch > (normalized[key] ?: Long.MIN_VALUE)) {
            normalized[key] = epoch
        }
    }
    return normalized
}
