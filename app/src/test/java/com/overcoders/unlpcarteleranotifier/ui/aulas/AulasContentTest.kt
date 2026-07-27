package com.overcoders.unlpcarteleranotifier.ui.aulas

import com.overcoders.unlpcarteleranotifier.model.AulaEstado
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AulasContentTest {
    @Test
    fun displayNameOmitsBlankParts() {
        assertEquals(
            "Aula Magna",
            aulaDisplayName(aula(name = "Aula Magna", id = "")),
        )
        assertEquals(
            "12",
            aulaDisplayName(aula(name = "", id = "12")),
        )
    }

    @Test
    fun shareTextKeepsStableClassroomOrder() {
        val text = buildAulasShareText(
            listOf(
                aula(name = "B", id = "2", start = "10:00"),
                aula(name = "A", id = "1", start = "09:00"),
            )
        )

        assertTrue(text.indexOf("• A - 1") < text.indexOf("• B - 2"))
    }

    @Test
    fun emptyShareTextExplainsThereAreNoOccupiedClassrooms() {
        assertEquals(
            "Estado actual de aulas\n\nNo hay aulas ocupadas en este momento.",
            buildAulasShareText(emptyList()),
        )
    }

    private fun aula(
        name: String,
        id: String,
        start: String = "08:00",
    ) = AulaEstado(
        aulaNombre = name,
        aulaId = id,
        materia = "Materia",
        horaDesde = start,
        horaHasta = "12:00",
    )
}
