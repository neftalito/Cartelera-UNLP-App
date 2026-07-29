/** Verifica la decisión común entre carga inicial y actualización con contenido resuelto. */
package com.overcoders.unlpcarteleranotifier.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class LoadingIndicatorsTest {
    @Test
    fun `resuelve las cuatro combinaciones de carga y contenido previo`() {
        val cases = listOf(
            LoadingCase(
                isLoading = false,
                hasResolvedContent = false,
                expected = ContentLoadingPhase.IDLE,
            ),
            LoadingCase(
                isLoading = false,
                hasResolvedContent = true,
                expected = ContentLoadingPhase.IDLE,
            ),
            LoadingCase(
                isLoading = true,
                hasResolvedContent = false,
                expected = ContentLoadingPhase.INITIAL,
            ),
            LoadingCase(
                isLoading = true,
                hasResolvedContent = true,
                expected = ContentLoadingPhase.REFRESHING,
            ),
        )

        cases.forEach { case ->
            assertEquals(
                case.toString(),
                case.expected,
                contentLoadingPhase(
                    isLoading = case.isLoading,
                    hasResolvedContent = case.hasResolvedContent,
                ),
            )
        }
    }

    private data class LoadingCase(
        val isLoading: Boolean,
        val hasResolvedContent: Boolean,
        val expected: ContentLoadingPhase,
    )
}
