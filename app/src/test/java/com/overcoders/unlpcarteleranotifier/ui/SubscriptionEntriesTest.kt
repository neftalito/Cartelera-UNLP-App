/** Verifica la presentación de suscripciones conocidas y obsoletas. */
package com.overcoders.unlpcarteleranotifier.ui

import com.overcoders.unlpcarteleranotifier.model.MateriaCatalogItem
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionEntriesTest {
    @Test
    fun doesNotMarkSubscriptionsInvalidWithoutACatalog() {
        val result = buildSubscriptionEntries(
            materias = emptyList(),
            subscriptionIds = setOf("42")
        )

        assertEquals(SubscriptionStatus.UNVERIFIED, result.single().status)
    }

    @Test
    fun distinguishesKnownAndMissingSubscriptionsWhenCatalogIsAvailable() {
        val result = buildSubscriptionEntries(
            materias = listOf(MateriaCatalogItem(id = "1", nombre = "Algoritmos")),
            subscriptionIds = setOf("1", "2")
        )

        assertEquals(SubscriptionStatus.VALID, result.first { it.id == "1" }.status)
        assertEquals(SubscriptionStatus.INVALID, result.first { it.id == "2" }.status)
    }
}
