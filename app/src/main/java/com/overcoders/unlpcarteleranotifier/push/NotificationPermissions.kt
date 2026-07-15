/** Centraliza decisiones y comprobaciones del permiso de notificaciones. */
package com.overcoders.unlpcarteleranotifier.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

internal fun Context.canPostNotifications(): Boolean {
    val channelImportance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(NotificationChannelManager.CHANNEL_ID)
            ?.importance
    } else {
        null
    }
    return canPostNotificationsForState(
        sdkInt = Build.VERSION.SDK_INT,
        runtimePermissionGranted = hasNotificationRuntimePermission(),
        appNotificationsEnabled = NotificationManagerCompat.from(this)
            .areNotificationsEnabled(),
        channelImportance = channelImportance,
    )
}

@SuppressLint("InlinedApi")
internal fun canPostNotificationsForState(
    sdkInt: Int,
    runtimePermissionGranted: Boolean,
    appNotificationsEnabled: Boolean,
    channelImportance: Int?,
): Boolean {
    if (!runtimePermissionGranted || !appNotificationsEnabled) return false
    if (sdkInt < Build.VERSION_CODES.O) return true

    // Un canal ausente todavía es utilizable: se crea justo antes de publicar.
    return channelImportance == null || channelImportance != NotificationManager.IMPORTANCE_NONE
}

internal fun Context.hasNotificationRuntimePermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

internal fun shouldRequestNotificationPermission(
    sdkInt: Int,
    runtimePermissionGranted: Boolean,
    wasAlreadyRequested: Boolean,
): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU &&
    !runtimePermissionGranted &&
    !wasAlreadyRequested

internal fun Context.openNotificationSettings(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return startSettingsIntent(applicationDetailsIntent())
    }

    val notificationSettingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    }
    return startSettingsIntent(notificationSettingsIntent) ||
        startSettingsIntent(applicationDetailsIntent())
}

private fun Context.applicationDetailsIntent(): Intent =
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "package:$packageName".toUri()
    )

private fun Context.startSettingsIntent(intent: Intent): Boolean = runCatching {
    if (this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
    true
}.getOrDefault(false)
