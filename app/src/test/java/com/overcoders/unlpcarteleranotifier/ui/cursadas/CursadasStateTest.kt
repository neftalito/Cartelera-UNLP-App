/** Verifica las decisiones puras usadas al publicar el estado de cursadas. */
package com.overcoders.unlpcarteleranotifier.ui.cursadas

import com.overcoders.unlpcarteleranotifier.model.CursadaInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class CursadasStateTest {
    @Test
    fun currentSnapshotCanActAsSeenBaselineWhenPersistenceFails() {
        val cursadas = listOf(
            cursada(
                materia = "Algoritmos",
                ultimaModificacionEpochMillis = 1234L,
            )
        )

        assertEquals(mapOf("algoritmos" to 1234L), currentCursadasBaseline(cursadas))
    }

    @Test
    fun baselineMergesCaseVariantsUsingTheNewestSeenEpoch() {
        val cursadas = listOf(
            cursada("Algoritmos", 1_000L),
            cursada(" algoritmos ", 2_000L),
        )

        assertEquals(mapOf("algoritmos" to 2_000L), currentCursadasBaseline(cursadas))
    }

    @Test
    fun newsContainsOnlyMateriasUpdatedAfterTheirSeenEpoch() {
        val cursadas = listOf(
            cursada("Matemática", 2_000L),
            cursada("Algoritmos", 1_000L),
            cursada("Sin fecha", null),
        )

        assertEquals(
            setOf("matemática"),
            cursadasWithNews(
                cursadas = cursadas,
                seenEpochByMateria = mapOf(
                    "matemática" to 1_999L,
                    "algoritmos" to 1_000L,
                ),
            ),
        )
    }

    @Test
    fun itemWithoutModificationEpochIsNotReportedAsNew() {
        assertEquals(
            emptySet<String>(),
            cursadasWithNews(
                cursadas = listOf(cursada("Sin fecha", null)),
                seenEpochByMateria = emptyMap(),
            ),
        )
    }

    private fun cursada(
        materia: String,
        ultimaModificacionEpochMillis: Long?,
    ) = CursadaInfo(
        materia = materia,
        inicioCursadaHtml = "",
        horariosCursadaHtml = "",
        ultimaModificacion = "",
        ultimaModificacionEpochMillis = ultimaModificacionEpochMillis,
    )
}
