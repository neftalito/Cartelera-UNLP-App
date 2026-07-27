/** Verifica el catálogo de años disponibles del calendario académico. */
package com.overcoders.unlpcarteleranotifier.ui.herramientas

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarioAcademicoStateTest {
    @Test
    fun includesEveryYearFromCurrentToFirstAvailable() {
        assertEquals(
            listOf(2022, 2021, 2020, 2019, 2018),
            calendarYears(2022),
        )
    }

    @Test
    fun neverReturnsYearsBeforeFirstAvailableYear() {
        assertEquals(listOf(FIRST_CALENDAR_YEAR), calendarYears(2017))
    }
}
