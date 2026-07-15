/** Verifica decisiones de carga y estado de materias optativas. */
package com.overcoders.unlpcarteleranotifier.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class OptativeSubjectsScreenTest {
    @Test
    fun includesEveryYearThroughCurrentYear() {
        assertEquals(
            listOf(2028, 2027, 2026, 2025, 2024, 2023, 2022),
            optativeYears(2028),
        )
    }

    @Test
    fun neverReturnsYearsBeforeFirstAvailableYear() {
        assertEquals(listOf(FIRST_OPTATIVAS_YEAR), optativeYears(2020))
    }
}
