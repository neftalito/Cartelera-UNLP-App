/** Verifica las decisiones de solicitud del permiso de notificaciones. */
package com.overcoders.unlpcarteleranotifier.push

import android.app.NotificationManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionsTest {
    @Test
    fun requestsAndroid13PermissionOnlyOnce() {
        assertTrue(
            shouldRequestNotificationPermission(
                sdkInt = 33,
                runtimePermissionGranted = false,
                wasAlreadyRequested = false
            )
        )
        assertFalse(
            shouldRequestNotificationPermission(
                sdkInt = 33,
                runtimePermissionGranted = false,
                wasAlreadyRequested = true
            )
        )
    }

    @Test
    fun skipsRuntimePromptWhenItDoesNotApply() {
        assertFalse(
            shouldRequestNotificationPermission(
                sdkInt = 32,
                runtimePermissionGranted = false,
                wasAlreadyRequested = false
            )
        )
        assertFalse(
            shouldRequestNotificationPermission(
                sdkInt = 33,
                runtimePermissionGranted = true,
                wasAlreadyRequested = false
            )
        )
    }

    @Test
    fun rejectsDisabledAppOrRuntimePermission() {
        assertFalse(
            canPostNotificationsForState(
                sdkInt = 33,
                runtimePermissionGranted = false,
                appNotificationsEnabled = true,
                channelImportance = NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
        assertFalse(
            canPostNotificationsForState(
                sdkInt = 33,
                runtimePermissionGranted = true,
                appNotificationsEnabled = false,
                channelImportance = NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
    }

    @Test
    fun rejectsBlockedChannelOnAndroid8AndNewer() {
        assertFalse(
            canPostNotificationsForState(
                sdkInt = 26,
                runtimePermissionGranted = true,
                appNotificationsEnabled = true,
                channelImportance = NotificationManager.IMPORTANCE_NONE,
            )
        )
    }

    @Test
    fun allowsMissingChannelBecauseItWillBeCreatedWhenPosting() {
        assertTrue(
            canPostNotificationsForState(
                sdkInt = 26,
                runtimePermissionGranted = true,
                appNotificationsEnabled = true,
                channelImportance = null,
            )
        )
    }

    @Test
    fun ignoresChannelImportanceBeforeAndroid8() {
        assertTrue(
            canPostNotificationsForState(
                sdkInt = 25,
                runtimePermissionGranted = true,
                appNotificationsEnabled = true,
                channelImportance = NotificationManager.IMPORTANCE_NONE,
            )
        )
    }
}
