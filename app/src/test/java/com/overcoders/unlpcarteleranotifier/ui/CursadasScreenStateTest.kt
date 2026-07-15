/**
 * Verifica que Cursadas diferencie una fuente vacía de un filtro sin coincidencias.
 */
package com.overcoders.unlpcarteleranotifier.ui

import com.overcoders.unlpcarteleranotifier.model.CursadaInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CursadasScreenStateTest {
    @Test
    fun currentSnapshotCanActAsSeenBaselineWhenPersistenceFails() {
        val cursadas = listOf(
            CursadaInfo(
                materia = "Algoritmos",
                inicioCursadaHtml = "",
                horariosCursadaHtml = "",
                ultimaModificacion = "14/07/2026 10:00",
                ultimaModificacionEpochMillis = 1234L,
            )
        )

        assertEquals(mapOf("algoritmos" to 1234L), currentCursadasBaseline(cursadas))
    }

    @Test
    fun mergesCaseVariantsUsingTheNewestSeenEpoch() {
        val cursadas = listOf(
            CursadaInfo("Algoritmos", "", "", "", 1_000L),
            CursadaInfo(" algoritmos ", "", "", "", 2_000L),
        )

        assertEquals(mapOf("algoritmos" to 2_000L), currentCursadasBaseline(cursadas))
    }

    @Test
    fun reportsAnEmptyRemoteCatalog() {
        assertEquals(
            "No hay cursadas disponibles.",
            cursadasEmptyStateMessage(
                cursadaCount = 0,
                filteredCount = 0,
                hasActiveFilter = false,
            ),
        )
    }

    @Test
    fun reportsAFilterWithoutMatches() {
        assertEquals(
            "No hay cursadas que coincidan con el filtro.",
            cursadasEmptyStateMessage(
                cursadaCount = 3,
                filteredCount = 0,
                hasActiveFilter = true,
            ),
        )
    }

    @Test
    fun omitsTheEmptyStateWhenThereIsVisibleContent() {
        assertNull(
            cursadasEmptyStateMessage(
                cursadaCount = 3,
                filteredCount = 2,
                hasActiveFilter = true,
            )
        )
    }
}
