/** Verifica el filtrado y la prioridad de feedback del listado de cursadas. */
package com.overcoders.unlpcarteleranotifier.ui.cursadas

import com.overcoders.unlpcarteleranotifier.model.CursadaInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CursadasScreenContentTest {
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

    @Test
    fun filterIgnoresAccentsAndSortsNewestFirst() {
        val older = cursada("Matemática 1", 1_000L)
        val newer = cursada("Matemática 2", 2_000L)

        assertEquals(
            listOf(newer, older),
            filteredCursadas(
                cursadas = listOf(older, cursada("Algoritmos", 3_000L), newer),
                filter = "matematica",
            ),
        )
    }

    @Test
    fun sortUsesMateriaAsStableTieBreaker() {
        val algoritmos = cursada("Algoritmos", 1_000L)
        val matematica = cursada("Matemática", 1_000L)

        assertEquals(
            listOf(algoritmos, matematica),
            filteredCursadas(
                cursadas = listOf(matematica, algoritmos),
                filter = "",
            ),
        )
    }

    @Test
    fun loadErrorHasPriorityOverOtherFeedback() {
        val feedback = cursadasFeedback(
            contentState(
                loadError = "Error de carga",
                notificationOpenError = "Error de notificación",
                warning = "Advertencia",
            )
        )

        assertEquals("Error de carga", feedback?.message)
        assertFalse(feedback?.isWarning ?: true)
    }

    @Test
    fun cachedContentWarningUsesWarningPresentation() {
        val feedback = cursadasFeedback(contentState(warning = "Contenido en caché"))

        assertEquals("Contenido en caché", feedback?.message)
        assertTrue(feedback?.isWarning == true)
    }

    private fun contentState(
        loadError: String? = null,
        notificationOpenError: String? = null,
        actionError: String? = null,
        warning: String? = null,
    ) = CursadasScreenContentState(
        cursadas = emptyList(),
        cursadasConNovedades = emptySet(),
        loading = false,
        hasResolvedSnapshot = true,
        initialLoadCompleted = true,
        loadError = loadError,
        notificationOpenError = notificationOpenError,
        actionError = actionError,
        warning = warning,
    )

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
