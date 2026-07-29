/** Verifica la construcción y validación de tópicos Firebase. */
package com.overcoders.unlpcarteleranotifier.push

import org.junit.Assert.assertEquals
import org.junit.Test

class FirebaseTopicsTest {
    @Test
    fun usesExpectedGlobalTopics() {
        assertEquals(
            setOf("materias_all", "avisos_all"),
            FirebaseTopics.desiredTopics(
                notifyAll = true,
                subscribedMateriaIds = setOf("12"),
            ),
        )
    }

    @Test
    fun acceptsOnlyCurrentPositiveNumericMateriaIds() {
        assertEquals("materia_12", FirebaseTopics.forMateria(" 12 "))
        assertEquals(null, FirebaseTopics.forMateria("12/3"))
        assertEquals(
            setOf("materia_12", "avisos_all"),
            FirebaseTopics.desiredTopics(
                notifyAll = false,
                subscribedMateriaIds = setOf(" 12 ", "12/3", "0", " "),
            ),
        )
    }
}
