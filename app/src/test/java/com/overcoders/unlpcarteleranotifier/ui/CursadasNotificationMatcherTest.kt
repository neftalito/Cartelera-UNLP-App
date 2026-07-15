/**
 * Verifica que las notificaciones de cursadas acepten snapshots vigentes o posteriores.
 */
package com.overcoders.unlpcarteleranotifier.ui

import com.overcoders.unlpcarteleranotifier.model.CursadaInfo
import com.overcoders.unlpcarteleranotifier.model.CursadaNotificationTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CursadasNotificationMatcherTest {
    private val cursada = CursadaInfo(
        materia = "Matemática 1",
        inicioCursadaHtml = "",
        horariosCursadaHtml = "",
        ultimaModificacion = "13/07/2026 10:30",
        ultimaModificacionEpochMillis = null,
    )

    @Test
    fun matchesIgnoringWhitespaceAndCase() {
        val target = CursadaNotificationTarget(
            materiaId = null,
            materia = "  MATEMÁTICA 1 ",
            fechaModificacion = cursada.ultimaModificacion,
        )

        assertEquals(cursada, findCursadaForNotification(listOf(cursada), target))
    }

    @Test
    fun rejectsStaleCachedVersion() {
        val target = CursadaNotificationTarget(
            materiaId = null,
            materia = cursada.materia,
            fechaModificacion = "13/07/2026 11:00",
        )

        assertNull(findCursadaForNotification(listOf(cursada), target))
    }

    @Test
    fun acceptsCurrentVersionWhenItIsNewerThanTheNotification() {
        val target = CursadaNotificationTarget(
            materiaId = null,
            materia = cursada.materia,
            fechaModificacion = "13/07/2026 10:00",
        )

        assertEquals(cursada, findCursadaForNotification(listOf(cursada), target))
    }

    @Test
    fun comparesIsoNotificationDatesWithTheLocalDisplayFormat() {
        val olderTarget = CursadaNotificationTarget(
            materiaId = null,
            materia = cursada.materia,
            fechaModificacion = "2026-07-13T13:00:00Z",
        )
        val newerTarget = olderTarget.copy(
            fechaModificacion = "2026-07-13T14:00:00Z",
        )

        assertEquals(cursada, findCursadaForNotification(listOf(cursada), olderTarget))
        assertNull(findCursadaForNotification(listOf(cursada), newerTarget))
    }

    @Test
    fun rejectsUnparseableDates() {
        val invalidCursada = cursada.copy(
            ultimaModificacion = "fecha inválida",
            ultimaModificacionEpochMillis = null,
        )

        assertFalse(isCursadaVersionAtLeast(invalidCursada, "fecha inválida"))
        assertFalse(isCursadaVersionAtLeast(cursada, "fecha inválida"))
    }

    @Test
    fun matchesByMateriaWhenCurrentPayloadHasNoModificationDate() {
        val target = CursadaNotificationTarget(
            materiaId = null,
            materia = cursada.materia,
            fechaModificacion = "",
        )

        assertEquals(cursada, findCursadaForNotification(listOf(cursada), target))
    }

    @Test
    fun unconfirmedRefreshDoesNotReportTargetAsUnavailable() {
        assertFalse(
            isCursadaTargetResolutionConfirmed(
                targetRequestId = 2L,
                confirmedTargetRequestId = null,
                loading = false,
            )
        )
    }

    @Test
    fun earlierSuccessfulRefreshDoesNotConfirmANewerTarget() {
        assertFalse(
            isCursadaTargetResolutionConfirmed(
                targetRequestId = 3L,
                confirmedTargetRequestId = 2L,
                loading = false,
            )
        )
    }

    @Test
    fun successfulRefreshCanReportTargetAsUnavailableAfterLoadingEnds() {
        assertTrue(
            isCursadaTargetResolutionConfirmed(
                targetRequestId = 3L,
                confirmedTargetRequestId = 3L,
                loading = false,
            )
        )
    }

    @Test
    fun successfulRefreshDoesNotReportTargetWhileLoading() {
        assertFalse(
            isCursadaTargetResolutionConfirmed(
                targetRequestId = 3L,
                confirmedTargetRequestId = 3L,
                loading = true,
            )
        )
    }
}
