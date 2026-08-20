/**
 * Verifica la presentación textual de los avisos globales de la aplicación.
 */
package com.overcoders.unlpcarteleranotifier.navigation

import com.overcoders.unlpcarteleranotifier.model.AvisoNotificationTarget
import com.overcoders.unlpcarteleranotifier.push.NotificationOpenKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifica el texto que la app muestra al abrir avisos generales. */
class AppDialogsTest {
    @Test
    fun showsOnlyTheDetailedMessage() {
        val body = avisoDialogBody(
            AvisoNotificationTarget(
                titulo = "Mantenimiento",
                mensaje = "El servicio estará pausado.",
                mensajeDetallado = "El servicio estará pausado hasta las 18.",
                fecha = "14/07/2026",
            )
        )

        assertEquals("El servicio estará pausado hasta las 18.", body)
    }

    @Test
    fun doesNotRenderDateOrLegacyAuthorLabel() {
        val body = avisoDialogBody(
            AvisoNotificationTarget(
                titulo = "Aviso",
                mensaje = "Mensaje vigente.",
                mensajeDetallado = "Mensaje vigente.",
                fecha = "14/07/2026",
            )
        )

        assertTrue(body.contains("Mensaje vigente."))
        assertFalse(body.contains("Fecha:"))
        assertFalse(body.contains("Autor:"))
    }

    @Test
    fun updateDialogExplainsWhenTheStoreCouldNotBeOpened() {
        assertFalse(updateAvailableDialogBody(storeOpenFailed = false).contains("No se pudo"))
        assertTrue(updateAvailableDialogBody(storeOpenFailed = true).contains("No se pudo"))
    }

    @Test
    fun staleNotificationCompletionDoesNotClearANewerOpening() {
        assertEquals(
            12L,
            pendingNotificationOpenAfterFinished(
                pendingEventId = 12L,
                finishedEventId = 11L,
            )
        )
        assertNull(
            pendingNotificationOpenAfterFinished(
                pendingEventId = 12L,
                finishedEventId = 12L,
            )
        )
    }

    @Test
    fun leavingNotificationDestinationFinishesOnlyTheActiveScreenOpening() {
        assertFalse(
            shouldFinishNotificationOpenOnNavigation(
                pendingEventId = 12L,
                activeKind = NotificationOpenKind.CARTELERA,
                destination = AppDestinations.CARTELERA,
            )
        )
        assertTrue(
            shouldFinishNotificationOpenOnNavigation(
                pendingEventId = 12L,
                activeKind = NotificationOpenKind.CARTELERA,
                destination = AppDestinations.CURSADAS,
            )
        )
        assertFalse(
            shouldFinishNotificationOpenOnNavigation(
                pendingEventId = 12L,
                activeKind = NotificationOpenKind.AVISO,
                destination = AppDestinations.CURSADAS,
            )
        )
    }
}
