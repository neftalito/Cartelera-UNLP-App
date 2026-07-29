/** Verifica el cooldown por versión del aviso de actualización. */
package com.overcoders.unlpcarteleranotifier.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreTest {
    @Test
    fun showsWhenPostponementIsMissingOrIncomplete() {
        assertTrue(
            isUpdatePromptEligible(
                availableVersionCode = 28,
                postponedVersionCode = null,
                postponedAtEpochMillis = null,
                nowEpochMillis = 1_000L,
            )
        )
        assertTrue(
            isUpdatePromptEligible(
                availableVersionCode = 28,
                postponedVersionCode = 28,
                postponedAtEpochMillis = null,
                nowEpochMillis = 1_000L,
            )
        )
    }

    @Test
    fun hidesSameVersionDuringCooldown() {
        assertFalse(
            isUpdatePromptEligible(
                availableVersionCode = 28,
                postponedVersionCode = 28,
                postponedAtEpochMillis = 1_000L,
                nowEpochMillis = 1_000L + UPDATE_PROMPT_COOLDOWN_MILLIS - 1L,
            )
        )
    }

    @Test
    fun reportsWhenEligibilityMustBeCheckedAgain() {
        assertEquals(
            UpdatePromptDecision(
                shouldShow = false,
                remainingCooldownMillis = UPDATE_PROMPT_COOLDOWN_MILLIS - 1_000L,
            ),
            updatePromptDecision(
                availableVersionCode = 28,
                postponedVersionCode = 28,
                postponedAtEpochMillis = 1_000L,
                nowEpochMillis = 2_000L,
            )
        )
    }

    @Test
    fun showsSameVersionAtCooldownBoundary() {
        assertTrue(
            isUpdatePromptEligible(
                availableVersionCode = 28,
                postponedVersionCode = 28,
                postponedAtEpochMillis = 1_000L,
                nowEpochMillis = 1_000L + UPDATE_PROMPT_COOLDOWN_MILLIS,
            )
        )
    }

    @Test
    fun showsNewerVersionWithoutWaitingForOldCooldown() {
        assertTrue(
            isUpdatePromptEligible(
                availableVersionCode = 29,
                postponedVersionCode = 28,
                postponedAtEpochMillis = 1_000L,
                nowEpochMillis = 2_000L,
            )
        )
    }

    @Test
    fun futureTimestampDoesNotHidePromptIndefinitely() {
        assertTrue(
            isUpdatePromptEligible(
                availableVersionCode = 28,
                postponedVersionCode = 28,
                postponedAtEpochMillis = 2_000L,
                nowEpochMillis = 1_000L,
            )
        )
    }
}
