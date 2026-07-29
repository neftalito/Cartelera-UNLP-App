/** Verifica el parseo de cada variante de data message FCM. */
package com.overcoders.unlpcarteleranotifier.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushPayloadParserTest {
    @Test
    fun parsesAndTrimsCarteleraPayload() {
        val payload = parsePushPayload(
            mapOf(
                "type" to " cartelera ",
                "materia_id" to " 12 ",
                "materia" to " Algoritmos ",
                "titulo" to " Parcial ",
                "fecha" to " 13/07/2026 ",
                "autor" to "",
                "resumen" to "",
                "is_anulado" to "true"
            )
        ) as PushPayload.Cartelera

        assertEquals("12", payload.target.materiaId)
        assertEquals("Algoritmos", payload.target.materia)
        assertTrue(payload.target.isAnulado)
    }

    @Test
    fun parsesExpectedCursadaPayloadShape() {
        val payload = parsePushPayload(
            mapOf(
                "type" to "cursada",
                "materia" to " Algoritmos ",
                "materia_id" to " 12 ",
                "fecha_modificacion" to " 2026-07-14T12:00:00+00:00 ",
            )
        ) as PushPayload.Cursada

        assertEquals("12", payload.target.materiaId)
        assertEquals("Algoritmos", payload.target.materia)
        assertEquals("2026-07-14T12:00:00+00:00", payload.target.fechaModificacion)
    }

    @Test
    fun parsesAvisoPayloadShape() {
        val payload = parsePushPayload(
            mapOf(
                "type" to "aviso",
                "titulo" to " Mantenimiento ",
                "mensaje_resumen" to " El servicio estará en mantenimiento. ",
                "mensaje_detallado" to " El servicio estará en mantenimiento hasta las 18. ",
                "fecha" to " 2026-07-14T15:00:00+00:00 ",
            )
        ) as PushPayload.Aviso

        assertEquals("Mantenimiento", payload.target.titulo)
        assertEquals("El servicio estará en mantenimiento.", payload.target.mensaje)
        assertEquals(
            "El servicio estará en mantenimiento hasta las 18.",
            payload.target.mensajeDetallado,
        )
        assertEquals("2026-07-14T15:00:00+00:00", payload.target.fecha)
    }

    @Test
    fun rejectsLegacyAvisoPayloadShape() {
        assertNull(
            parsePushPayload(
                mapOf(
                    "type" to "aviso",
                    "titulo" to "Inscripciones",
                    "mensaje" to "Comienzan las inscripciones.",
                    "autor" to "Cartelera UNLP",
                    "fecha" to "2026-07-14T15:00:00+00:00",
                )
            )
        )
    }

    @Test
    fun rejectsAvisoWhenEitherCurrentMessageFieldIsMissingOrBlank() {
        val validFields = mapOf(
            "type" to "aviso",
            "titulo" to "Inscripciones",
            "mensaje_resumen" to "Comienzan las inscripciones.",
            "mensaje_detallado" to "Comienzan las inscripciones al primer semestre.",
            "fecha" to "2026-07-14T15:00:00+00:00",
        )

        assertNull(parsePushPayload(validFields - "mensaje_resumen"))
        assertNull(parsePushPayload(validFields - "mensaje_detallado"))
        assertNull(parsePushPayload(validFields + ("mensaje_resumen" to " ")))
        assertNull(parsePushPayload(validFields + ("mensaje_detallado" to " ")))
    }

    @Test
    fun rejectsBlankRequiredPushFields() {
        assertNull(
            parsePushPayload(
                mapOf(
                    "type" to "cartelera",
                    "materia" to " ",
                    "materia_id" to "12",
                    "titulo" to "Parcial",
                    "fecha" to "13/07/2026",
                    "autor" to "",
                    "resumen" to "",
                    "is_anulado" to "false",
                )
            )
        )
    }

    @Test
    fun rejectsMissingOrInvalidCurrentFields() {
        val validFields = mapOf(
            "type" to "cartelera",
            "materia_id" to "12",
            "materia" to "Algoritmos",
            "titulo" to "Parcial",
            "fecha" to "13/07/2026",
            "autor" to "",
            "resumen" to "",
            "is_anulado" to "false",
        )

        assertNull(parsePushPayload(validFields - "resumen"))
        assertNull(
            parsePushPayload(
                validFields + ("is_anulado" to "TRUE")
            )
        )
    }
}
