package com.overcoders.unlpcarteleranotifier.push

/**
 * Nombres de topics compartidos por cliente Android y backend Python.
 */
object FirebaseTopics {
    const val ALL_MATERIAS = "materias_all"
    const val AVISOS = "avisos_all"
    private const val MATERIA_PREFIX = "materia_"

    fun forMateria(idMateria: String): String {
        return MATERIA_PREFIX + idMateria.trim().replace("[^A-Za-z0-9\\-_.~%]".toRegex(), "_")
    }

    fun desiredTopics(
        notifyAll: Boolean,
        subscribedMateriaIds: Set<String>
    ): Set<String> {
        // La instalación siempre escucha avisos globales y además el topic general o uno por materia.
        val materiasTopics = if (notifyAll) {
            setOf(ALL_MATERIAS)
        } else {
            subscribedMateriaIds
                .filter { it.isNotBlank() }
                .map(::forMateria)
                .toSet()
        }
        return materiasTopics + AVISOS
    }
}
