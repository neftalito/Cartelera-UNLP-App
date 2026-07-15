/**
 * Contiene decisiones puras de presentación para los estados vacíos de cursadas.
 */
package com.overcoders.unlpcarteleranotifier.ui

import com.overcoders.unlpcarteleranotifier.model.CursadaInfo
import com.overcoders.unlpcarteleranotifier.model.normalizedCursadaSeenEpochs

internal fun currentCursadasBaseline(cursadas: List<CursadaInfo>): Map<String, Long> =
    normalizedCursadaSeenEpochs(cursadas.mapNotNull { cursada ->
        cursada.ultimaModificacionEpochMillis?.let { epoch -> cursada.materia to epoch }
    })

internal fun cursadasEmptyStateMessage(
    cursadaCount: Int,
    filteredCount: Int,
    hasActiveFilter: Boolean,
): String? {
    require(cursadaCount >= 0)
    require(filteredCount in 0..cursadaCount)

    return when {
        cursadaCount == 0 -> "No hay cursadas disponibles."
        hasActiveFilter && filteredCount == 0 ->
            "No hay cursadas que coincidan con el filtro."
        else -> null
    }
}
