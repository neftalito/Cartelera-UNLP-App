/** Verifica la validación y creación de destinos de notificación. */
package com.overcoders.unlpcarteleranotifier.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationTargetFactoryTest {
    @Test
    fun normalizesCarteleraTargetFromAnyInputSource() {
        val target = createCarteleraNotificationTarget(
            materiaId = " 0012 ",
            materia = " Algoritmos ",
            titulo = " Parcial ",
            fecha = " 13/07/2026 ",
            autor = " Docente ",
            resumen = " Novedad ",
            isAnulado = false
        )

        assertEquals("12", target?.materiaId)
        assertEquals("Algoritmos", target?.materia)
        assertEquals("Parcial", target?.titulo)
        assertEquals("Docente", target?.autor)
    }

    @Test
    fun dropsNonCanonicalMateriaIdWithoutDiscardingTheNotification() {
        val target = createCursadaNotificationTarget(
            materiaId = "12/3",
            materia = "Algoritmos",
            fechaModificacion = "14/07/2026 10:00",
        )

        assertEquals("Algoritmos", target?.materia)
        assertNull(target?.materiaId)
    }

    @Test
    fun requiresCurrentAvisoContentAndDate() {
        assertNull(
            createAvisoNotificationTarget(
                titulo = "Aviso",
                mensaje = " ",
                autor = "Administración",
                fecha = "14/07/2026",
            )
        )
        assertNull(
            createAvisoNotificationTarget(
                titulo = "Aviso",
                mensaje = "Contenido vigente",
                autor = "Administración",
                fecha = " ",
            )
        )
    }

    @Test
    fun rejectsTargetsWithoutRequiredIdentity() {
        assertNull(
            createCarteleraNotificationTarget(
                materiaId = null,
                materia = " ",
                titulo = "Parcial",
                fecha = "13/07/2026",
                autor = null,
                resumen = null,
                isAnulado = false
            )
        )
        assertNull(
            createCursadaNotificationTarget(
                materiaId = null,
                materia = " ",
                fechaModificacion = null
            )
        )
        assertNull(
            createAvisoNotificationTarget(
                titulo = " ",
                mensaje = null,
                autor = null,
                fecha = null
            )
        )
    }
}
