package com.overcoders.unlpcarteleranotifier.ui.horarios

import com.overcoders.unlpcarteleranotifier.model.HorarioMateria
import com.overcoders.unlpcarteleranotifier.model.HorarioPeriodo
import com.overcoders.unlpcarteleranotifier.model.HorarioReserva
import com.overcoders.unlpcarteleranotifier.model.MateriaCatalogItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HorariosPresentationTest {
    @Test
    fun filterMatchesCaseAndAccentsWithoutChangingCatalogOrder() {
        val materias = listOf(
            MateriaCatalogItem(id = "1", nombre = "Álgebra"),
            MateriaCatalogItem(id = "2", nombre = "Análisis Matemático"),
            MateriaCatalogItem(id = "3", nombre = "Física"),
        )

        assertEquals(
            listOf("Análisis Matemático", "Física"),
            filterMateriasByQuery(materias, "I").map(MateriaCatalogItem::nombre),
        )
        assertEquals(
            listOf("Álgebra"),
            filterMateriasByQuery(materias, "algebra").map(MateriaCatalogItem::nombre),
        )
    }

    @Test
    fun visibleDaysExcludeEmptyDaysAndSortReservations() {
        val horario = HorarioMateria(
            materiaNombre = "Programación",
            periodo = HorarioPeriodo("Semestre", "01/03", "30/06"),
            reservas = listOf(
                reservation(day = 2, start = "10:00", classroom = "2"),
                reservation(day = 0, start = "09:00", classroom = "3"),
                reservation(day = 0, start = "08:00", classroom = "1"),
            ),
        )

        val days = visibleHorarioDays(horario)

        assertEquals(listOf(0, 2), days.map(HorarioDayReservations::number))
        assertEquals(listOf("08:00", "09:00"), days.first().reservations.map { it.horaInicio })
    }

    @Test
    fun catalogIssueKeepsPriorityAndRefreshTarget() {
        val issue = resolveHorariosIssue(
            materiasError = "Catálogo caído",
            horariosError = "Horario caído",
            horariosWarning = null,
            materiasWarning = null,
        )

        requireNotNull(issue)
        assertEquals("Catálogo caído", issue.message)
        assertTrue(issue.isError)
        assertTrue(issue.belongsToCatalog)

        val catalogWarning = resolveHorariosIssue(
            materiasError = null,
            horariosError = null,
            horariosWarning = null,
            materiasWarning = "Catálogo cacheado",
        )
        requireNotNull(catalogWarning)
        assertEquals("Catálogo cacheado", catalogWarning.message)
        assertFalse(catalogWarning.isError)
        assertTrue(catalogWarning.belongsToCatalog)

        val scheduleWarning = resolveHorariosIssue(
            materiasError = null,
            horariosError = null,
            horariosWarning = "Horario cacheado",
            materiasWarning = "Catálogo cacheado",
        )
        requireNotNull(scheduleWarning)
        assertEquals("Horario cacheado", scheduleWarning.message)
        assertFalse(scheduleWarning.isError)
        assertFalse(scheduleWarning.belongsToCatalog)
    }

    private fun reservation(
        day: Int,
        start: String,
        classroom: String,
    ) = HorarioReserva(
        aula = classroom,
        confirmada = true,
        tipo = "Cursada",
        dia = day,
        horaInicio = start,
        horaFin = "12:00",
    )
}
