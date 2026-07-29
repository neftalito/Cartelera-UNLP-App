/**
 * Verifica la entrega atómica y de un solo uso de destinos de notificación.
 */
package com.overcoders.unlpcarteleranotifier.push

import com.overcoders.unlpcarteleranotifier.model.AvisoNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.CursadaNotificationTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifica la entrega de un solo uso entre el tap de una notificación y MainActivity. */
class NotificationOpenTargetStoreTest {
    @Test
    fun consumesPublishedTargetOnlyOnce() {
        val store = NotificationOpenTargetStore()
        val target = NotificationOpenTarget.Cursada(
            CursadaNotificationTarget(
                materiaId = "10",
                materia = "Algoritmos",
                fechaModificacion = "14/07/2026",
            )
        )

        store.publish(target)

        assertEquals(target, store.consume())
        assertNull(store.consume())
    }

    @Test
    fun keepsLatestTargetWhenTwoTapsArriveBeforeConsumption() {
        val store = NotificationOpenTargetStore()
        val first = NotificationOpenTarget.Aviso(
            AvisoNotificationTarget("Primero", "Mensaje", "Autor", "14/07/2026")
        )
        val latest = NotificationOpenTarget.Aviso(
            AvisoNotificationTarget("Segundo", "Mensaje", "Autor", "14/07/2026")
        )

        store.publish(first)
        store.publish(latest)

        assertEquals(latest, store.consume())
    }
}
