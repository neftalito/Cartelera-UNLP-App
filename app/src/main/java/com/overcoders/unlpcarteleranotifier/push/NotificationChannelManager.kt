/** Mantiene el canal compartido de notificaciones del sistema. */
package com.overcoders.unlpcarteleranotifier.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannelManager {
    const val CHANNEL_ID = "cartelera_updates"
    private const val CHANNEL_NAME = "Novedades y avisos"
    private const val CHANNEL_DESCRIPTION =
        "Avisos generales y actualizaciones de cartelera y cursadas."

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESCRIPTION
        }
        // Crear nuevamente el mismo ID actualiza nombre y descripción sin perder las
        // preferencias de importancia elegidas por el usuario.
        manager.createNotificationChannel(channel)
    }
}
