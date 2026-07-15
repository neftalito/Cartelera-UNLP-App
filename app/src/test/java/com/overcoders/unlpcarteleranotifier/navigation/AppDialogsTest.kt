/**
 * Verifica la presentación textual de los avisos globales de la aplicación.
 */
package com.overcoders.unlpcarteleranotifier.navigation

import com.overcoders.unlpcarteleranotifier.model.AvisoNotificationTarget
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifica el texto que la app muestra al abrir avisos generales. */
class AppDialogsTest {
    @Test
    fun includesMessageAuthorAndDateWhenAvailable() {
        val body = avisoDialogBody(
            AvisoNotificationTarget(
                titulo = "Mantenimiento",
                mensaje = "El servicio estará pausado.",
                autor = "Administración",
                fecha = "14/07/2026",
            )
        )

        assertTrue(body.contains("El servicio estará pausado."))
        assertTrue(body.contains("Autor: Administración"))
        assertTrue(body.contains("Fecha: 14/07/2026"))
    }

    @Test
    fun omitsOnlyTheOptionalAuthor() {
        val body = avisoDialogBody(
            AvisoNotificationTarget(
                titulo = "Aviso",
                mensaje = "Mensaje vigente.",
                autor = "",
                fecha = "14/07/2026",
            )
        )

        assertTrue(body.contains("Mensaje vigente."))
        assertTrue(body.contains("Fecha: 14/07/2026"))
        assertFalse(body.contains("Autor:"))
    }
}
