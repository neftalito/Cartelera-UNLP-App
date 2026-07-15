/** Recibe data messages de FCM y los deriva al despachador local. */
package com.overcoders.unlpcarteleranotifier.push

import android.annotation.SuppressLint
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.overcoders.unlpcarteleranotifier.BuildConfig

/**
 * Entrada de FCM para data messages.
 *
 * El backend manda payloads chicos con la información mínima para mostrar una notificación
 * local y para que la app pueda reenfocar el contenido correcto al tocarla.
 */
// Firebase Messaging entrega las altas y renovaciones vigentes mediante onRegistered.
@SuppressLint("MissingFirebaseInstanceTokenRefresh")
class CarteleraFirebaseMessagingService : FirebaseMessagingService() {
    override fun onRegistered(installationId: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "FCM registro actualizado para la instalación $installationId.")
        }
        resyncTopics(installationId)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        when (val payload = parsePushPayload(data)) {
            is PushPayload.Cartelera -> {
                PushNotificationDispatcher.showCarteleraNotification(
                    context = applicationContext,
                    target = payload.target
                )
            }

            is PushPayload.Cursada -> {
                PushNotificationDispatcher.showCursadaNotification(
                    context = applicationContext,
                    target = payload.target
                )
            }

            is PushPayload.Aviso -> {
                PushNotificationDispatcher.showAvisoNotification(
                    context = applicationContext,
                    target = payload.target
                )
            }

            null -> {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Push recibido con tipo no soportado o datos incompletos: ${data.keys}")
                }
            }
        }
    }

    private companion object {
        const val TAG = "CarteleraFCMService"
    }

    private fun resyncTopics(installationId: String) {
        // La instalación ya quedó registrada, así que rehidratamos los topics sobre ese FID.
        FirebaseTopicSyncManager.requestSync(
            context = applicationContext,
            currentInstallationId = installationId,
        )
    }
}
