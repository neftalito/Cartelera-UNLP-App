/** Verifica decisiones de carga con filtros, resultados resueltos y paginación. */
package com.overcoders.unlpcarteleranotifier.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentLoadingStateTest {
    @Test
    fun `un filtro resuelto usa actualización en lugar de carga inicial`() {
        val state = paginatedLoadingState(
            isContentLoading = true,
            isLoadingMore = false,
            resolvedKey = "sin-resultados",
            requestedKey = "sin-resultados",
            hasError = false,
        )

        assertEquals(ContentLoadingPhase.REFRESHING, state.phase)
        assertFalse(state.showPaginationIndicator)
    }

    @Test
    fun `cambiar de filtro vuelve a carga inicial sin mostrar datos anteriores`() {
        val state = paginatedLoadingState(
            isContentLoading = false,
            isLoadingMore = false,
            resolvedKey = "filtro-anterior",
            requestedKey = "filtro-nuevo",
            hasError = false,
        )

        assertEquals(ContentLoadingPhase.INITIAL, state.phase)
    }

    @Test
    fun `una carga auxiliar oculta el indicador de paginación simultáneo`() {
        val state = paginatedLoadingState(
            isContentLoading = false,
            isAuxiliaryLoading = true,
            isLoadingMore = true,
            resolvedKey = "actual",
            requestedKey = "actual",
            hasError = false,
        )

        assertEquals(ContentLoadingPhase.REFRESHING, state.phase)
        assertFalse(state.showPaginationIndicator)
    }

    @Test
    fun `paginar no reemplaza el contenido ni muestra la barra de actualización`() {
        val state = paginatedLoadingState(
            isContentLoading = false,
            isLoadingMore = true,
            resolvedKey = "actual",
            requestedKey = "actual",
            hasError = false,
        )

        assertEquals(ContentLoadingPhase.IDLE, state.phase)
        assertTrue(state.showPaginationIndicator)
    }

    @Test
    fun `un error inicial terminado deja visible el estado de error`() {
        assertEquals(
            ContentLoadingPhase.IDLE,
            keyedContentLoadingPhase(
                isLoading = false,
                resolvedKey = null,
                requestedKey = "actual",
                hasError = true,
            ),
        )
    }
}
