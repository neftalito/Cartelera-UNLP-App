/**
 * Verifica que los errores públicos no filtren detalles y que debug conserve información útil.
 */
package com.overcoders.unlpcarteleranotifier.ui.common

import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UserFacingErrorTest {
    @Test
    fun releaseUsesTheSameGenericMessageWithoutTechnicalDetails() {
        val message = userFacingError(
            operation = "cargar anuncios",
            error = IOException("timeout interno"),
            isDebug = false,
        )

        assertEquals(GENERIC_ERROR_MESSAGE, message)
        assertFalse(message.contains("anuncios"))
        assertFalse(message.contains("timeout"))
    }

    @Test
    fun debugAppendsOperationTypeAndCause() {
        val message = userFacingError(
            operation = "cargar anuncios",
            error = IOException("timeout interno"),
            isDebug = true,
        )

        assertTrue(message.startsWith(GENERIC_ERROR_MESSAGE))
        assertTrue(message.contains("cargar anuncios"))
        assertTrue(message.contains("IOException: timeout interno"))
    }

    @Test
    fun warningsKeepStableReleaseCopy() {
        assertEquals(
            CACHED_CONTENT_WARNING,
            cachedContentWarning(
                operation = "actualizar aulas",
                detail = "respuesta vacía",
                isDebug = false,
            ),
        )
        assertEquals(
            PARTIAL_CONTENT_WARNING,
            partialContentWarning(
                operation = "actualizar planes",
                detail = "fallaron 2 fuentes",
                isDebug = false,
            ),
        )
    }

    @Test
    fun cancellationIsNeverConvertedIntoAVisibleError() {
        assertThrows(CancellationException::class.java) {
            userFacingError(
                operation = "cargar contenido",
                error = CancellationException("cancelado"),
                isDebug = false,
            )
        }
    }
}
