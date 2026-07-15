/** Verifica el parseo JSON de anuncios, horarios y reservas eventuales. */
package com.overcoders.unlpcarteleranotifier.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonParserTest {
    @Test
    fun parsesAnunciosAndDropsIncompleteAttachments() {
        val result = AnunciosService().parse(
            """
            {
              "total": 1,
              "mensajes": [{
                "materia": "Algoritmos",
                "titulo": "Parcial",
                "cuerpo": "<p>Fecha confirmada</p>",
                "fecha": "13/07/2026",
                "autor": " Cátedra ",
                "is_anulado": false,
                "adjuntos": [
                  {"nombre": "Guía", "public_path": "/guia.pdf"},
                  {"nombre": "", "public_path": "/invalido.pdf"}
                ]
              }]
            }
            """.trimIndent()
        )

        assertEquals(1, result.total)
        assertEquals(1, result.receivedCount)
        assertEquals("Cátedra", result.mensajes.single().autor)
        assertEquals(1, result.mensajes.single().adjuntos.size)
        assertFalse(result.mensajes.single().isAnulado)
    }

    @Test
    fun normalizesNegativeAnunciosTotal() {
        val result = AnunciosService().parse("""{"total": -1, "mensajes": []}""")

        assertEquals(0, result.total)
    }

    @Test
    fun rejectsAnunciosPayloadWhenEveryMessageIsInvalid() {
        assertThrows(IllegalStateException::class.java) {
            AnunciosService().parse(
                """{"total": 1, "mensajes": [{"materia": "", "titulo": "", "fecha": ""}]}"""
            )
        }
    }

    @Test
    fun reportsRawAnunciosRowsConsumedBeforeFiltering() {
        val result = AnunciosService().parse(
            """
            {
              "total": 2,
              "mensajes": [
                {"materia": "Algoritmos", "titulo": "Parcial", "fecha": "13/07/2026"},
                {"materia": "", "titulo": "Fila incompleta", "fecha": "13/07/2026"}
              ]
            }
            """.trimIndent()
        )

        assertEquals(1, result.mensajes.size)
        assertEquals(2, result.receivedCount)
    }

    @Test
    fun parsesAulasAndNormalizesHours() {
        val result = AulasService().parse(
            """
            [{
              "aula": {"id": "12", "nombre": "Aula 12"},
              "materia": {"nombre": "Bases de Datos"},
              "horaDesde": {"h": "8", "m": "5"},
              "horaHasta": {"h": "25", "m": "0"}
            }, {}]
            """.trimIndent()
        )

        assertEquals("Aula 12", result.single().aulaNombre)
        assertEquals("08:05", result.single().horaDesde)
        assertEquals("-", result.single().horaHasta)
    }

    @Test
    fun rejectsAulasPayloadWhenEveryRowIsInvalid() {
        assertThrows(IllegalStateException::class.java) {
            AulasService().parse("[{}]")
        }
    }

    @Test
    fun rejectsBlankAulasPayload() {
        assertThrows(IllegalStateException::class.java) {
            AulasService().parse("   ")
        }
    }

    @Test
    fun parsesHorarioPeriodAndReservations() {
        val result = HorariosService().parse(
            body = """
            {
              "periodo": {
                "nombre": "Primer semestre",
                "desde": {"d": "1", "m": "3", "y": "2026"},
                "hasta": {"d": "30", "m": "6", "y": "2026"}
              },
              "reservas": [{
                "aula": "1-1",
                "confirmada": true,
                "tipo": "Teoría",
                "dia": 1,
                "horaInicio": {"h": "8", "m": "5"},
                "horaFin": {"h": "12", "m": "00"}
              }]
            }
            """.trimIndent(),
            materiaNombre = "Programación"
        )

        assertEquals("Primer semestre", result.periodo.nombre)
        assertEquals("Programación", result.materiaNombre)
        assertTrue(result.reservas.single().confirmada)
        assertEquals("08:05", result.reservas.single().horaInicio)
    }

    @Test
    fun rejectsHorarioPayloadWhenEveryReservationHasAnInvalidDay() {
        assertThrows(IllegalStateException::class.java) {
            HorariosService().parse(
                body = """
                    {
                      "periodo": {
                        "nombre": "Primer semestre",
                        "desde": {},
                        "hasta": {}
                      },
                      "reservas": [{"dia": -1}]
                    }
                """.trimIndent(),
                materiaNombre = "Programación"
            )
        }
    }

    @Test
    fun replacesBlankHorarioLabelsWithFallbacks() {
        val result = HorariosService().parse(
            body = """
                {
                  "periodo": {"nombre": " ", "desde": {}, "hasta": {}},
                  "reservas": [{"dia": 0, "aula": " ", "tipo": " "}]
                }
            """.trimIndent(),
            materiaNombre = "Programación"
        )

        assertEquals("Sin período", result.periodo.nombre)
        assertEquals("Sin aula", result.reservas.single().aula)
        assertEquals("Sin tipo", result.reservas.single().tipo)
    }

    @Test
    fun ignoresNonPositiveEventualReservationFilterIds() {
        val options = EventualReservationsService().parseFilterOptions(
            """
                <select id="reservaseventualesadminfilterform_aula">
                  <option value="0">Inválida</option>
                  <option value="10">Aula 10</option>
                </select>
                <select id="reservaseventualesadminfilterform_materia">
                  <option value="-1">Inválida</option>
                  <option value="20">Algoritmos</option>
                </select>
            """.trimIndent()
        )

        assertEquals(listOf(10), options.classrooms.map { it.id })
        assertEquals(listOf(20), options.subjects.map { it.id })
    }

    @Test
    fun parsesEventualReservationPaginationAndSkipsInvalidRows() {
        val result = EventualReservationsService().parsePage(
            """
            {
              "data": [
                {
                  "id": 40,
                  "aula_nombre": "Aula 1",
                  "materia_nombre": "Redes",
                  "docente_nombre": "Ana",
                  "docente_apellido": "Pérez",
                  "motivo": "Consulta",
                  "fecha": "13/07/2026",
                  "horaInicio": "18:00",
                  "horaFin": "20:00"
                },
                {
                  "id": 40,
                  "aula_nombre": "Aula duplicada"
                },
                {"id": 0}
              ],
              "paginator-data": {
                "current": 1,
                "pageCount": 2,
                "next": 2,
                "totalCount": 21
              }
            }
            """.trimIndent()
        )

        assertEquals(1, result.reservations.size)
        assertEquals("Ana Pérez", result.reservations.single().teacherName)
        assertEquals(2, result.nextPage)
        assertEquals(21, result.totalCount)
    }

    @Test
    fun omitsInvalidNextPage() {
        val result = EventualReservationsService().parsePage(
            """{"data": [], "paginator-data": {"current": 2, "pageCount": 2, "next": 3}}"""
        )

        assertNull(result.nextPage)
        assertTrue(result.reservations.isEmpty())
    }

    @Test
    fun rejectsEventualReservationsPayloadWhenEveryRowIsInvalid() {
        assertThrows(IllegalStateException::class.java) {
            EventualReservationsService().parsePage(
                """{"data": [{"id": 0}], "paginator-data": {"current": 1, "pageCount": 1}}"""
            )
        }
    }

    @Test
    fun rejectsEventualReservationsPayloadWithInvalidCurrentPage() {
        assertThrows(IllegalStateException::class.java) {
            EventualReservationsService().parsePage(
                """{"data": [], "paginator-data": {"current": 0, "pageCount": 1}}"""
            )
        }
    }

    @Test
    fun rejectsEventualReservationsPayloadForAnotherPage() {
        assertThrows(IllegalStateException::class.java) {
            EventualReservationsService().parsePage(
                body = """{"data": [], "paginator-data": {"current": 1, "pageCount": 2, "next": 2}}""",
                expectedPage = 2
            )
        }
    }
}
