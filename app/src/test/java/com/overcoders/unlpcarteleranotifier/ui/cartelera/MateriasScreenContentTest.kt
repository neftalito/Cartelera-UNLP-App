/** Verifica las decisiones puras de feedback, visibilidad y estado vacío de cartelera. */
package com.overcoders.unlpcarteleranotifier.ui.cartelera

import com.overcoders.unlpcarteleranotifier.model.Mensaje
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MateriasScreenContentTest {
    @Test
    fun feedErrorKeepsPriorityOverNotificationCatalogAndWarnings() {
        val feedback = materiasFeedback(
            state(
                error = "Error de anuncios",
                notificationOpenError = "Error de notificación",
                materiasError = "Error de catálogo",
                warning = "Contenido cacheado",
                materiasWarning = "Catálogo cacheado",
            )
        )

        requireNotNull(feedback)
        assertEquals("Error de anuncios", feedback.message)
        assertFalse(feedback.isWarning)
    }

    @Test
    fun cachedContentFeedbackIsPresentedAsWarning() {
        val feedback = materiasFeedback(state(warning = "Contenido cacheado"))

        requireNotNull(feedback)
        assertEquals("Contenido cacheado", feedback.message)
        assertTrue(feedback.isWarning)
    }

    @Test
    fun hiddenCancelledAnnouncementsDoNotAlterOriginalFeed() {
        val active = announcement(title = "Parcial", cancelled = false)
        val cancelled = announcement(title = "Final", cancelled = true)
        val original = listOf(active, cancelled)

        val visible = visibleAnnouncements(original, hideCancelledMessages = true)

        assertEquals(listOf(active), visible)
        assertEquals(listOf(active, cancelled), original)
    }

    @Test
    fun emptyStateWaitsUntilCurrentFilterIsResolvedAndFeedIsExhausted() {
        assertNull(
            materiasEmptyStateMessage(
                hasLoadedCurrentFilter = false,
                loadingMore = false,
                hasMore = false,
                hasError = false,
                visibleAnnouncementCount = 0,
                announcementCount = 0,
                hideCancelledMessages = false,
                hasSelectedMateria = false,
            )
        )
        assertNull(
            materiasEmptyStateMessage(
                hasLoadedCurrentFilter = true,
                loadingMore = false,
                hasMore = true,
                hasError = false,
                visibleAnnouncementCount = 0,
                announcementCount = 0,
                hideCancelledMessages = false,
                hasSelectedMateria = false,
            )
        )
    }

    @Test
    fun emptyStateDistinguishesHiddenCancelledAndSelectedMateria() {
        assertEquals(
            "No hay anuncios visibles con la configuración actual.",
            materiasEmptyStateMessage(
                hasLoadedCurrentFilter = true,
                loadingMore = false,
                hasMore = false,
                hasError = false,
                visibleAnnouncementCount = 0,
                announcementCount = 1,
                hideCancelledMessages = true,
                hasSelectedMateria = true,
            )
        )
        assertEquals(
            "No hay anuncios disponibles para la materia seleccionada.",
            materiasEmptyStateMessage(
                hasLoadedCurrentFilter = true,
                loadingMore = false,
                hasMore = false,
                hasError = false,
                visibleAnnouncementCount = 0,
                announcementCount = 0,
                hideCancelledMessages = false,
                hasSelectedMateria = true,
            )
        )
    }

    private fun state(
        error: String? = null,
        notificationOpenError: String? = null,
        materiasError: String? = null,
        warning: String? = null,
        materiasWarning: String? = null,
    ) = MateriasScreenContentState(
        materias = emptyList(),
        materiasLoading = false,
        materiasError = materiasError,
        materiasWarning = materiasWarning,
        announcementsForDisplay = emptyList(),
        newAnnouncementIdentities = emptySet(),
        hideCancelledMessages = false,
        loadingInitial = false,
        loadingMore = false,
        error = error,
        notificationOpenError = notificationOpenError,
        warning = warning,
        loadedFilterKey = "__all__",
        hasMore = false,
    )

    private fun announcement(title: String, cancelled: Boolean) = Mensaje(
        materia = "Algoritmos",
        titulo = title,
        cuerpoHtml = "",
        fecha = "18/07/2026",
        autor = "Cátedra",
        isAnulado = cancelled,
        adjuntos = emptyList(),
    )
}
