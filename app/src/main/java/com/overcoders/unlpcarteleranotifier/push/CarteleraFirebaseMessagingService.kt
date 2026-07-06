package com.overcoders.unlpcarteleranotifier.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.overcoders.unlpcarteleranotifier.model.AvisoNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.CarteleraNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.CursadaNotificationTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Entrada de FCM para data messages.
 *
 * El backend manda payloads chicos con la información mínima para mostrar una notificación
 * local y para que la app pueda reenfocar el contenido correcto al tocarla.
 */
class CarteleraFirebaseMessagingService : FirebaseMessagingService() {
    override fun onRegistered(installationId: String) {
        Log.d(TAG, "FCM registro actualizado para la instalación $installationId.")
        resyncTopics(installationId)
    }

    @Deprecated(
        "La API nueva usa register() + onRegistered(). Este override queda como puente para herramientas y callbacks legacy."
    )
    override fun onNewToken(token: String) {
        // Compatibilidad transitoria: si más adelante confirmamos que `onRegistered()`
        // cubre todos los casos relevantes, este puente se puede eliminar.
        Log.d(TAG, "FCM token actualizado; se relanza el registro por installation id.")
        CoroutineScope(Dispatchers.IO).launch {
            FirebaseTopicSyncManager.register(applicationContext)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        when (data["type"]) {
            TYPE_CARTELERA -> {
                // Cartelera y cursadas comparten canal, pero usan payloads distintos.
                val materia = data["materia"] ?: return
                val titulo = data["titulo"] ?: return
                val fecha = data["fecha"] ?: return
                PushNotificationDispatcher.showCarteleraNotification(
                    context = applicationContext,
                    target = CarteleraNotificationTarget(
                        materiaId = data["materia_id"],
                        materia = materia,
                        titulo = titulo,
                        fecha = fecha,
                        autor = data["autor"].orEmpty(),
                        resumen = data["resumen"].orEmpty(),
                        isAnulado = data["is_anulado"]?.toBooleanStrictOrNull() ?: false
                    )
                )
            }

            TYPE_CURSADA -> {
                val materia = data["materia"] ?: return
                PushNotificationDispatcher.showCursadaNotification(
                    context = applicationContext,
                    target = CursadaNotificationTarget(
                        materiaId = data["materia_id"],
                        materia = materia,
                        fechaModificacion = data["fecha_modificacion"].orEmpty()
                    )
                )
            }

            TYPE_AVISO -> {
                val titulo = data["titulo"] ?: return
                PushNotificationDispatcher.showAvisoNotification(
                    context = applicationContext,
                    target = AvisoNotificationTarget(
                        titulo = titulo,
                        mensaje = data["mensaje"].orEmpty(),
                        autor = data["autor"].orEmpty(),
                        fecha = data["fecha"].orEmpty()
                    )
                )
            }

            else -> {
                Log.d(TAG, "Push recibido sin tipo soportado: ${data.keys}")
            }
        }
    }

    private companion object {
        const val TAG = "CarteleraFCMService"
        const val TYPE_CARTELERA = "cartelera"
        const val TYPE_CURSADA = "cursada"
        const val TYPE_AVISO = "aviso"
    }

    private fun resyncTopics(installationId: String) {
        // La instalación ya quedó registrada, así que rehidratamos los topics sobre ese FID.
        CoroutineScope(Dispatchers.IO).launch {
            FirebaseTopicSyncManager.sync(
                context = applicationContext,
                currentInstallationId = installationId,
            )
        }
    }
}
