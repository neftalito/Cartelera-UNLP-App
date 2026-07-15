/** Verifica que los IDs de notificación sean estables y diferenciables. */
package com.overcoders.unlpcarteleranotifier.push

import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationIdsTest {
    @Test
    fun preservesIdentityPartBoundaries() {
        assertNotEquals(
            stableNotificationId("cartelera", "ab", "c"),
            stableNotificationId("cartelera", "a", "bc")
        )
    }

    @Test
    fun separatesNotificationTypes() {
        assertNotEquals(
            stableNotificationId("cartelera", "same"),
            stableNotificationId("cursada", "same")
        )
    }
}
