/** Verifica el filtrado puro del selector de suscripciones. */
package com.overcoders.unlpcarteleranotifier.ui.subscripciones

import com.overcoders.unlpcarteleranotifier.model.MateriaCatalogItem
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscripcionesStateTest {
    private val materias = listOf(
        MateriaCatalogItem(id = "1", nombre = "Álgebra"),
        MateriaCatalogItem(id = "2", nombre = "Programación"),
        MateriaCatalogItem(id = "3", nombre = "Sistemas Operativos"),
    )

    @Test
    fun selectorExcludesPersistedAndPendingSubscriptions() {
        val result = availableSubscriptionMaterias(
            materias = materias,
            subscriptionIds = setOf("1", "3"),
            query = "",
        )

        assertEquals(listOf(materias[1]), result)
    }

    @Test
    fun selectorSearchIsCaseAndAccentInsensitive() {
        val result = availableSubscriptionMaterias(
            materias = materias,
            subscriptionIds = emptySet(),
            query = "algebra",
        )

        assertEquals(listOf(materias[0]), result)
    }
}
