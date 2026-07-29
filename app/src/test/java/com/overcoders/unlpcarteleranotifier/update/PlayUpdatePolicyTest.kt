/** Verifica la política de reintentos y revalidación del estado de Google Play. */
package com.overcoders.unlpcarteleranotifier.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayUpdatePolicyTest {
    @Test
    fun checksImmediatelyWhenNoResultExists() {
        assertTrue(
            shouldCheckForPlayUpdate(
                status = PlayUpdateStatus.NOT_CHECKED,
                lastCheckAtEpochMillis = null,
                nowEpochMillis = 1_000L,
                isCheckInProgress = false,
                trigger = PlayUpdateCheckTrigger.RESUME,
            )
        )
    }

    @Test
    fun revalidatesAKnownAvailableUpdateAfterTheRefreshInterval() {
        assertFalse(
            shouldCheckForPlayUpdate(
                status = PlayUpdateStatus.AVAILABLE,
                lastCheckAtEpochMillis = 1_000L,
                nowEpochMillis = 1_000L + PLAY_UPDATE_RECHECK_MILLIS - 1L,
                isCheckInProgress = false,
                trigger = PlayUpdateCheckTrigger.RESUME,
            )
        )
        assertTrue(
            shouldCheckForPlayUpdate(
                status = PlayUpdateStatus.AVAILABLE,
                lastCheckAtEpochMillis = 1_000L,
                nowEpochMillis = 1_000L + PLAY_UPDATE_RECHECK_MILLIS,
                isCheckInProgress = false,
                trigger = PlayUpdateCheckTrigger.SCHEDULED,
            )
        )
    }

    @Test
    fun retriesUnavailableResultOnResumeAndUsesBackoffWhileOpen() {
        assertTrue(
            shouldCheckForPlayUpdate(
                status = PlayUpdateStatus.UNAVAILABLE,
                lastCheckAtEpochMillis = 1_000L,
                nowEpochMillis = 1_001L,
                isCheckInProgress = false,
                trigger = PlayUpdateCheckTrigger.RESUME,
            )
        )
        assertFalse(
            shouldCheckForPlayUpdate(
                status = PlayUpdateStatus.UNAVAILABLE,
                lastCheckAtEpochMillis = 1_000L,
                nowEpochMillis = 1_001L,
                isCheckInProgress = false,
                trigger = PlayUpdateCheckTrigger.SCHEDULED,
            )
        )
        assertEquals(
            PLAY_UPDATE_UNAVAILABLE_RETRY_MILLIS - 1L,
            remainingPlayUpdateCheckDelayMillis(
                status = PlayUpdateStatus.UNAVAILABLE,
                lastCheckAtEpochMillis = 1_000L,
                nowEpochMillis = 1_001L,
            )
        )
    }

    @Test
    fun rechecksDefinitiveNoUpdateOnlyAfterRefreshInterval() {
        val checkedAt = 10_000L

        assertFalse(
            shouldCheckForPlayUpdate(
                status = PlayUpdateStatus.NOT_AVAILABLE,
                lastCheckAtEpochMillis = checkedAt,
                nowEpochMillis = checkedAt + PLAY_UPDATE_RECHECK_MILLIS - 1L,
                isCheckInProgress = false,
                trigger = PlayUpdateCheckTrigger.RESUME,
            )
        )
        assertTrue(
            shouldCheckForPlayUpdate(
                status = PlayUpdateStatus.NOT_AVAILABLE,
                lastCheckAtEpochMillis = checkedAt,
                nowEpochMillis = checkedAt + PLAY_UPDATE_RECHECK_MILLIS,
                isCheckInProgress = false,
                trigger = PlayUpdateCheckTrigger.SCHEDULED,
            )
        )
    }

    @Test
    fun doesNotStartOverlappingChecks() {
        assertFalse(
            shouldCheckForPlayUpdate(
                status = PlayUpdateStatus.UNAVAILABLE,
                lastCheckAtEpochMillis = 1_000L,
                nowEpochMillis = 2_000L,
                isCheckInProgress = true,
                trigger = PlayUpdateCheckTrigger.RESUME,
            )
        )
    }

    @Test
    fun clockRollbackMakesStaleResultEligibleImmediately() {
        assertEquals(
            0L,
            remainingPlayUpdateCheckDelayMillis(
                status = PlayUpdateStatus.NOT_AVAILABLE,
                lastCheckAtEpochMillis = 2_000L,
                nowEpochMillis = 1_000L,
            )
        )
    }

    @Test
    fun transientFailureDoesNotEraseAKnownUpdate() {
        assertEquals(
            PlayUpdateSnapshot(
                status = PlayUpdateStatus.AVAILABLE,
                availableVersionCode = 28,
            ),
            reducePlayUpdateResult(
                currentStatus = PlayUpdateStatus.AVAILABLE,
                currentAvailableVersionCode = 28,
                result = PlayUpdateCheckResult.Unavailable,
            )
        )
    }

    @Test
    fun definitiveResultCanReplaceAKnownUpdate() {
        assertEquals(
            PlayUpdateSnapshot(
                status = PlayUpdateStatus.NOT_AVAILABLE,
                availableVersionCode = null,
            ),
            reducePlayUpdateResult(
                currentStatus = PlayUpdateStatus.AVAILABLE,
                currentAvailableVersionCode = 28,
                result = PlayUpdateCheckResult.NotAvailable,
            )
        )
        assertEquals(
            PlayUpdateSnapshot(
                status = PlayUpdateStatus.AVAILABLE,
                availableVersionCode = 29,
            ),
            reducePlayUpdateResult(
                currentStatus = PlayUpdateStatus.AVAILABLE,
                currentAvailableVersionCode = 28,
                result = PlayUpdateCheckResult.Available(29),
            )
        )
    }

    @Test
    fun restoredAvailableCodeMustStillBeNewerThanInstalledVersion() {
        assertFalse(
            isAvailableUpdateNewerThanInstalled(
                availableVersionCode = 28,
                installedVersionCode = 28,
            )
        )
        assertTrue(
            isAvailableUpdateNewerThanInstalled(
                availableVersionCode = 29,
                installedVersionCode = 28,
            )
        )
    }
}
