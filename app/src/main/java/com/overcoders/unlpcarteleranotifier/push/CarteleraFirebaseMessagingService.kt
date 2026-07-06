package com.overcoders.unlpcarteleranotifier.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.overcoders.unlpcarteleranotifier.model.CarteleraNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.CursadaNotificationTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CarteleraFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(Dispatchers.IO).launch {
            FirebaseTopicSyncManager.sync(applicationContext)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        when (data["type"]) {
            TYPE_CARTELERA -> {
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

            else -> {
                Log.d(TAG, "Push recibido sin tipo soportado: ${data.keys}")
            }
        }
    }

    private companion object {
        const val TAG = "CarteleraFCMService"
        const val TYPE_CARTELERA = "cartelera"
        const val TYPE_CURSADA = "cursada"
    }
}
