/** Construye y valida los nombres de tópicos FCM usados por la app. */
package com.overcoders.unlpcarteleranotifier.push

import com.overcoders.unlpcarteleranotifier.model.toMateriaCatalogIdOrNull

/**
 * Nombres de topics compartidos por cliente Android y backend Python.
 */
object FirebaseTopics {
    const val ALL_MATERIAS = "materias_all"
    const val AVISOS = "avisos_all"
    const val AVISOS_TESTING = "avisos_testing"
    private const val MATERIA_PREFIX = "materia_"

    internal fun forMateria(idMateria: String): String? =
        idMateria.toMateriaCatalogIdOrNull()?.let { id -> "$MATERIA_PREFIX$id" }

    fun desiredTopics(
        notifyAll: Boolean,
        subscribedMateriaIds: Set<String>,
        includeTestingAvisos: Boolean,
    ): Set<String> {
        // Release nunca incluye el topic de testing. Si una instalación cambia de variante,
        // el reconciliador lo detecta como sobrante y solicita su desuscripción.
        val avisosTopics = buildSet {
            add(AVISOS)
            if (includeTestingAvisos) add(AVISOS_TESTING)
        }
        val materiasTopics = if (notifyAll) {
            setOf(ALL_MATERIAS)
        } else {
            subscribedMateriaIds
                .mapNotNull(::forMateria)
                .toSet()
        }
        return materiasTopics + avisosTopics
    }
}
