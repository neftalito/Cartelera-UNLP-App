/**
 * Verifica las decisiones puras de restauración y permisos usadas por MainActivity.
 */
package com.overcoders.unlpcarteleranotifier

import com.overcoders.unlpcarteleranotifier.push.NotificationOpenKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityTest {
    @Test
    fun aNewTargetInvalidatesPendingWorkFromOtherScreens() {
        assertTrue(
            shouldInvalidatePendingNotification(
                eventId = 2L,
                activeKind = NotificationOpenKind.CURSADA,
                screenKind = NotificationOpenKind.CARTELERA,
            )
        )
        assertFalse(
            shouldInvalidatePendingNotification(
                eventId = 2L,
                activeKind = NotificationOpenKind.CURSADA,
                screenKind = NotificationOpenKind.CURSADA,
            )
        )
    }

    @Test
    fun restoresNotificationOnlyWhenItsResolutionStateWasLost() {
        assertTrue(
            shouldRestoreNotificationResolution(
                hasSelectedContent = false,
                hasPendingTarget = false,
                hasRestorableTarget = true,
            )
        )
        assertFalse(
            shouldRestoreNotificationResolution(
                hasSelectedContent = true,
                hasPendingTarget = false,
                hasRestorableTarget = true,
            )
        )
        assertFalse(
            shouldRestoreNotificationResolution(
                hasSelectedContent = false,
                hasPendingTarget = true,
                hasRestorableTarget = true,
            )
        )
        assertFalse(
            shouldRestoreNotificationResolution(
                hasSelectedContent = false,
                hasPendingTarget = false,
                hasRestorableTarget = false,
            )
        )
    }
}
