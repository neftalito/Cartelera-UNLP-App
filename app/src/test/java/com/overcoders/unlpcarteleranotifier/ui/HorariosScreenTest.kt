/**
 * Verifica la reconciliación entre horarios cacheados y el catálogo actual de materias.
 */
package com.overcoders.unlpcarteleranotifier.ui

import com.overcoders.unlpcarteleranotifier.model.HorarioMateria
import com.overcoders.unlpcarteleranotifier.model.HorarioPeriodo
import org.junit.Assert.assertEquals
import org.junit.Test

class HorariosScreenTest {
    @Test
    fun cachedScheduleUsesCurrentCatalogName() {
        val cached = HorarioMateria(
            materiaNombre = "Nombre anterior",
            periodo = HorarioPeriodo(
                nombre = "Primer semestre",
                desde = "01/03/2026",
                hasta = "31/07/2026",
            ),
            reservas = emptyList(),
        )

        assertEquals(
            "Nombre actual",
            cached.withCurrentMateriaName("Nombre actual").materiaNombre,
        )
    }
}
