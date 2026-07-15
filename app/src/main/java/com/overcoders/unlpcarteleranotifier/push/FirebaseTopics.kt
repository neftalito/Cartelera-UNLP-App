/** Construye y valida los nombres de tópicos FCM usados por la app. */
package com.overcoders.unlpcarteleranotifier.push

import com.overcoders.unlpcarteleranotifier.model.toMateriaCatalogIdOrNull

/**
 * Nombres de topics compartidos por cliente Android y backend Python.
 */
object FirebaseTopics {
    const val ALL_MATERIAS = "materias_all"
    const val AVISOS = "avisos_all"
    private const val MATERIA_PREFIX = "materia_"

    internal fun forMateria(idMateria: String): String? =
        idMateria.toMateriaCatalogIdOrNull()?.let { id -> "$MATERIA_PREFIX$id" }

    fun desiredTopics(
        notifyAll: Boolean,
        subscribedMateriaIds: Set<String>
    ): Set<String> {
        // La instalación siempre escucha avisos globales y además el topic general o uno por materia.
        val materiasTopics = if (notifyAll) {
            setOf(ALL_MATERIAS)
        } else {
            subscribedMateriaIds
                .mapNotNull(::forMateria)
                .toSet()
        }
        return materiasTopics + AVISOS
    }
}
