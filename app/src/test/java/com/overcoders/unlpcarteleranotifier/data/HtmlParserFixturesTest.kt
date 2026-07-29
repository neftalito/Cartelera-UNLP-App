/**
 * Valida los parsers HTML contra fixtures representativos y cambios estructurales inválidos.
 */
package com.overcoders.unlpcarteleranotifier.data

import com.overcoders.unlpcarteleranotifier.model.OptativeCareer
import com.overcoders.unlpcarteleranotifier.model.StudyCareer
import com.overcoders.unlpcarteleranotifier.model.StudyPlanSource
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlParserFixturesTest {
    @Test
    fun parsesAndSanitizesAcademicCalendar() {
        val url = "https://www.info.unlp.edu.ar/calendario-academico-2026/"
        val result = CalendarioAcademicoService().parseCalendario(
            html = fixture("calendario.html"),
            anio = 2026,
            url = url,
        )

        assertEquals(2026, result.anio)
        assertTrue(result.contenidoHtml.contains("Calendario Académico 2026"))
        assertTrue(result.contenidoHtml.contains("https://www.info.unlp.edu.ar/documentos/calendario-2026.pdf"))
        assertFalse(result.contenidoHtml.contains("<script"))
    }

    @Test
    fun parsesAndCleansOptativeSubjectsTable() {
        val url = "https://www.info.unlp.edu.ar/optativas-2026-licenciatura-en-sistemas/"
        val result = OptativeSubjectsService().parsePage(
            year = 2026,
            career = OptativeCareer.SISTEMAS,
            url = url,
            html = fixture("optativas.html"),
        )

        assertEquals("Materias optativas 2026 - Sistemas", result.pageTitle)
        assertTrue(result.contentHtml.contains("Cloud Computing y Cloud Robotics"))
        assertTrue(result.contentHtml.contains("https://www.info.unlp.edu.ar/materias/cloud-computing/"))
        assertFalse(result.contentHtml.contains("summary="))
        assertFalse(result.contentHtml.contains("<script"))
    }

    @Test
    fun parsesStudyPlanMetadataAndSubjects() {
        val source = StudyPlanSource(
            career = StudyCareer.LS,
            planLabel = "2021",
            url = "https://www.info.unlp.edu.ar/licenciatura-en-sistemas-plan-2021/",
        )
        val result = StudyPlansService().parseStudyPlan(source, fixture("plan-estudio.html"))

        assertEquals(2, result.subjectCount)
        assertEquals("Licenciado/a en Sistemas", result.degreeTitle)
        assertTrue(result.contentHtml.contains("Algoritmos y Estructuras de Datos"))
        assertTrue(result.contentHtml.contains("https://www.info.unlp.edu.ar/materias/algoritmos/"))
        assertFalse(result.contentHtml.contains("tablas-esconder-td"))
        assertTrue(result.contentHtml.contains("colspan=\"4\""))
    }

    @Test
    fun rejectsStudyPlanWithoutRecognizableSubjects() {
        val source = StudyPlanSource(
            career = StudyCareer.LS,
            planLabel = "2021",
            url = "https://www.info.unlp.edu.ar/licenciatura-en-sistemas-plan-2021/",
        )

        assertThrows(IllegalStateException::class.java) {
            StudyPlansService().parseStudyPlan(
                source = source,
                html = """
                    <div class="content">
                      <table class="table-bordered">
                        <tr><th>Código</th><th>Materia</th></tr>
                      </table>
                    </div>
                """.trimIndent()
            )
        }
    }

    @Test
    fun parsesOnlyMeaningfulCursadaRows() {
        val result = CursadasService().parse(fixture("cursadas.html"))

        assertEquals(1, result.size)
        assertEquals("Matemática 1", result.single().materia)
        assertTrue(result.single().inicioCursadaHtml.contains("Inicio 10/08"))
        assertTrue(result.single().inicioCursadaHtml.contains("Aula 1"))
        assertTrue(result.single().inicioCursadaHtml.contains("<br"))
        assertEquals(
            LocalDateTime.of(2026, 7, 13, 10, 30)
                .atZone(ZoneId.of("America/Argentina/Buenos_Aires"))
                .toInstant()
                .toEpochMilli(),
            result.single().ultimaModificacionEpochMillis
        )
    }

    @Test
    fun rejectsPageWithoutCursadasTable() {
        assertThrows(IllegalStateException::class.java) {
            CursadasService().parse("<html><body></body></html>")
        }
    }

    @Test
    fun acceptsCursadasTableWithAnActuallyEmptyBody() {
        val result = CursadasService().parse(
            """
                <table>
                  <thead><tr><th>Materia</th><th>Carreras</th><th>Inicio</th><th>Horarios</th><th>Última modificación</th></tr></thead>
                  <tbody></tbody>
                </table>
            """.trimIndent()
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun rejectsCursadasTableWhenEveryPresentRowIsUnrecognizable() {
        assertThrows(IllegalStateException::class.java) {
            CursadasService().parse(
                """
                    <table>
                      <thead><tr><th>Materia</th><th>Carreras</th><th>Inicio</th><th>Horarios</th><th>Última modificación</th></tr></thead>
                      <tbody>
                        <tr><td colspan="5">El formato remoto cambió</td></tr>
                      </tbody>
                    </table>
                """.trimIndent()
            )
        }
    }

    @Test
    fun acceptsRecognizableCursadaRowsWithoutPublishedContent() {
        val result = CursadasService().parse(
            """
                <table>
                  <thead><tr><th>Materia</th><th>Carreras</th><th>Inicio</th><th>Horarios</th><th>Última modificación</th></tr></thead>
                  <tbody>
                    <tr><td>Matemática 1</td><td>LI</td><td></td><td></td><td></td></tr>
                  </tbody>
                </table>
            """.trimIndent()
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun keepsOnlyTheNewestCursadaWhenTheSourceRepeatsAMateria() {
        val result = CursadasService().parse(
            """
                <table>
                  <thead><tr><th>Materia</th><th>Carreras</th><th>Inicio</th><th>Horarios</th><th>Última modificación</th></tr></thead>
                  <tbody>
                    <tr><td>Algoritmos</td><td>LI</td><td>Inicio anterior</td><td>8:00</td><td>01/07/2026 10:00</td></tr>
                    <tr><td>algoritmos</td><td>LI</td><td>Inicio actualizado</td><td>9:00</td><td>02/07/2026 10:00</td></tr>
                  </tbody>
                </table>
            """.trimIndent()
        )

        assertEquals(1, result.size)
        assertTrue(result.single().inicioCursadaHtml.contains("actualizado"))
    }

    @Test
    fun parsesAndDeduplicatesMateriaCatalog() {
        val result = MateriasService().parse(fixture("materias.html"))

        assertEquals(listOf("101", "102"), result.map { it.id })
        assertEquals("Algoritmos (Primer semestre)", result.first().nombre)
    }

    @Test
    fun rejectsPageWithoutMateriaCatalog() {
        assertThrows(IllegalStateException::class.java) {
            MateriasService().parse("<html><body></body></html>")
        }
    }

    @Test
    fun parsesEventualReservationFilters() {
        val result = EventualReservationsService().parseFilterOptions(
            fixture("reservas-filtros.html")
        )

        assertEquals(listOf(12, 13), result.classrooms.map { it.id })
        assertEquals(listOf(301), result.subjects.map { it.id })
    }

    @Test
    fun rejectsEventualReservationPageWithoutExpectedFilters() {
        assertThrows(IllegalStateException::class.java) {
            EventualReservationsService().parseFilterOptions("<html><body></body></html>")
        }
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/$name")) {
            "No se encontró el fixture $name"
        }.readText()
}
