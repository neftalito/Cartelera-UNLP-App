package com.overcoders.unlpcarteleranotifier.push

object FirebaseTopics {
    const val ALL_MATERIAS = "materias_all"
    private const val MATERIA_PREFIX = "materia_"

    fun forMateria(idMateria: String): String {
        return MATERIA_PREFIX + idMateria.trim().replace("[^A-Za-z0-9\\-_.~%]".toRegex(), "_")
    }

    fun desiredTopics(
        notifyAll: Boolean,
        subscribedMateriaIds: Set<String>
    ): Set<String> {
        return if (notifyAll) {
            setOf(ALL_MATERIAS)
        } else {
            subscribedMateriaIds
                .filter { it.isNotBlank() }
                .map(::forMateria)
                .toSet()
        }
    }
}
