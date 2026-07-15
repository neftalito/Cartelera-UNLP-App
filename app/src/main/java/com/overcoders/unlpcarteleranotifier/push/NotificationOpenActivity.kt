/**
 * Recibe taps de notificaciones en una actividad interna y los deriva a la actividad principal.
 */
package com.overcoders.unlpcarteleranotifier.push

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.overcoders.unlpcarteleranotifier.MainActivity

/**
 * Recibe exclusivamente PendingIntents creados por la app y entrega su target a MainActivity sin
 * exponer los extras de notificación en la actividad launcher exportada.
 */
class NotificationOpenActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PushNotificationDispatcher.notificationTargetFromIntent(intent)?.let(
            NotificationOpenCoordinator::publish
        )
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = MAIN_ACTIVITY_NOTIFICATION_FLAGS
            }
        )
        finish()
    }

    internal companion object {
        const val MAIN_ACTIVITY_NOTIFICATION_FLAGS =
            Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
}
