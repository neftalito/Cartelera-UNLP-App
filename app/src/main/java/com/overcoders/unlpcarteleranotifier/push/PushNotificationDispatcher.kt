/**
 * Interpreta mensajes FCM y publica notificaciones con destinos internos tipados.
 */
package com.overcoders.unlpcarteleranotifier.push

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.overcoders.unlpcarteleranotifier.R
import com.overcoders.unlpcarteleranotifier.model.AvisoNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.CarteleraNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.CursadaNotificationTarget
import com.overcoders.unlpcarteleranotifier.push.NotificationChannelManager.CHANNEL_ID

/**
 * Convierte payloads push en notificaciones del sistema y también reconstruye el target
 * mínimo desde los intents de apertura.
 */
object PushNotificationDispatcher {
    private const val EXTRA_PUSH_TYPE = "extra_push_type"
    private const val TYPE_CARTELERA = "push_cartelera"
    private const val TYPE_CURSADA = "push_cursada"
    private const val TYPE_AVISO = "push_aviso"
    private const val EXTRA_MATERIA_ID = "extra_push_materia_id"
    private const val EXTRA_MATERIA = "extra_push_materia"
    private const val EXTRA_TITULO = "extra_push_titulo"
    private const val EXTRA_FECHA = "extra_push_fecha"
    private const val EXTRA_AUTOR = "extra_push_autor"
    private const val EXTRA_RESUMEN = "extra_push_resumen"
    private const val EXTRA_MENSAJE = "extra_push_mensaje"
    private const val EXTRA_ANULADO = "extra_push_anulado"
    private const val EXTRA_FECHA_MODIFICACION = "extra_push_fecha_modificacion"
    private val CARTELERA_INTENT_EXTRAS = arrayOf(
        EXTRA_PUSH_TYPE,
        EXTRA_MATERIA_ID,
        EXTRA_MATERIA,
        EXTRA_TITULO,
        EXTRA_FECHA,
        EXTRA_AUTOR,
        EXTRA_RESUMEN,
        EXTRA_ANULADO,
    )
    private val CURSADA_INTENT_EXTRAS = arrayOf(
        EXTRA_PUSH_TYPE,
        EXTRA_MATERIA_ID,
        EXTRA_MATERIA,
        EXTRA_FECHA_MODIFICACION,
    )
    private val AVISO_INTENT_EXTRAS = arrayOf(
        EXTRA_PUSH_TYPE,
        EXTRA_TITULO,
        EXTRA_MENSAJE,
        EXTRA_AUTOR,
        EXTRA_FECHA,
    )

    @SuppressLint("MissingPermission")
    fun showCarteleraNotification(
        context: Context,
        target: CarteleraNotificationTarget
    ) {
        if (!context.canPostNotifications()) return

        NotificationChannelManager.ensureChannel(context)
        NotificationManagerCompat.from(context).notify(
            carteleraNotificationId(target),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(target.titulo)
                .setContentText(target.materia)
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        target.resumen.ifBlank {
                            "Se publicó una novedad en ${target.materia}."
                        }
                    )
                )
                .setContentIntent(carteleraPendingIntent(context, target))
                .setAutoCancel(true)
                .build()
        )
    }

    @SuppressLint("MissingPermission")
    fun showAvisoNotification(
        context: Context,
        target: AvisoNotificationTarget
    ) {
        if (!context.canPostNotifications()) return

        NotificationChannelManager.ensureChannel(context)
        NotificationManagerCompat.from(context).notify(
            avisoNotificationId(target),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(target.titulo)
                .setContentText(target.autor.ifBlank { "Aviso general" })
                .setStyle(NotificationCompat.BigTextStyle().bigText(target.mensaje))
                .setContentIntent(avisoPendingIntent(context, target))
                .setAutoCancel(true)
                .build()
        )
    }

    @SuppressLint("MissingPermission")
    fun showCursadaNotification(
        context: Context,
        target: CursadaNotificationTarget
    ) {
        if (!context.canPostNotifications()) return

        NotificationChannelManager.ensureChannel(context)
        NotificationManagerCompat.from(context).notify(
            cursadaNotificationId(target),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(target.materia)
                .setContentText("Se actualizó la información de cursada")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "La materia ${target.materia} actualizó su información de cursada (${target.fechaModificacion.ifBlank { "sin fecha" }})."
                    )
                )
                .setContentIntent(cursadaPendingIntent(context, target))
                .setAutoCancel(true)
                .build()
        )
    }

    internal fun notificationTargetFromIntent(intent: Intent?): NotificationOpenTarget? {
        val source = intent ?: return null
        return when (source.getStringExtra(EXTRA_PUSH_TYPE)) {
            TYPE_CARTELERA -> if (source.hasAllExtras(CARTELERA_INTENT_EXTRAS)) {
                createCarteleraNotificationTarget(
                    materiaId = source.getStringExtra(EXTRA_MATERIA_ID),
                    materia = source.getStringExtra(EXTRA_MATERIA),
                    titulo = source.getStringExtra(EXTRA_TITULO),
                    fecha = source.getStringExtra(EXTRA_FECHA),
                    autor = source.getStringExtra(EXTRA_AUTOR),
                    resumen = source.getStringExtra(EXTRA_RESUMEN),
                    isAnulado = source.getBooleanExtra(EXTRA_ANULADO, false),
                )?.let(NotificationOpenTarget::Cartelera)
            } else {
                null
            }

            TYPE_CURSADA -> if (source.hasAllExtras(CURSADA_INTENT_EXTRAS)) {
                createCursadaNotificationTarget(
                    materiaId = source.getStringExtra(EXTRA_MATERIA_ID),
                    materia = source.getStringExtra(EXTRA_MATERIA),
                    fechaModificacion = source.getStringExtra(EXTRA_FECHA_MODIFICACION),
                )?.let(NotificationOpenTarget::Cursada)
            } else {
                null
            }

            TYPE_AVISO -> if (source.hasAllExtras(AVISO_INTENT_EXTRAS)) {
                createAvisoNotificationTarget(
                    titulo = source.getStringExtra(EXTRA_TITULO),
                    mensaje = source.getStringExtra(EXTRA_MENSAJE),
                    autor = source.getStringExtra(EXTRA_AUTOR),
                    fecha = source.getStringExtra(EXTRA_FECHA),
                )?.let(NotificationOpenTarget::Aviso)
            } else {
                null
            }

            else -> null
        }
    }

    private fun Intent.hasAllExtras(names: Array<String>): Boolean = names.all(::hasExtra)

    private fun carteleraPendingIntent(
        context: Context,
        target: CarteleraNotificationTarget
    ): PendingIntent {
        val intent = Intent(context, NotificationOpenActivity::class.java).apply {
            // Guardamos sólo un target liviano para no depender del límite de tamaño de FCM.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_PUSH_TYPE, TYPE_CARTELERA)
            putExtra(EXTRA_MATERIA_ID, target.materiaId)
            putExtra(EXTRA_MATERIA, target.materia)
            putExtra(EXTRA_TITULO, target.titulo)
            putExtra(EXTRA_FECHA, target.fecha)
            putExtra(EXTRA_AUTOR, target.autor)
            putExtra(EXTRA_RESUMEN, target.resumen)
            putExtra(EXTRA_ANULADO, target.isAnulado)
        }
        return PendingIntent.getActivity(
            context,
            carteleraNotificationId(target),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cursadaPendingIntent(
        context: Context,
        target: CursadaNotificationTarget
    ): PendingIntent {
        val intent = Intent(context, NotificationOpenActivity::class.java).apply {
            // La pantalla resuelve el detalle final una vez que vuelve a cargar su snapshot.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_PUSH_TYPE, TYPE_CURSADA)
            putExtra(EXTRA_MATERIA_ID, target.materiaId)
            putExtra(EXTRA_MATERIA, target.materia)
            putExtra(EXTRA_FECHA_MODIFICACION, target.fechaModificacion)
        }
        return PendingIntent.getActivity(
            context,
            cursadaNotificationId(target),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun avisoPendingIntent(
        context: Context,
        target: AvisoNotificationTarget,
    ): PendingIntent {
        val intent = Intent(context, NotificationOpenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_PUSH_TYPE, TYPE_AVISO)
            putExtra(EXTRA_TITULO, target.titulo)
            putExtra(EXTRA_MENSAJE, target.mensaje)
            putExtra(EXTRA_AUTOR, target.autor)
            putExtra(EXTRA_FECHA, target.fecha)
        }
        return PendingIntent.getActivity(
            context,
            avisoNotificationId(target),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun carteleraNotificationId(target: CarteleraNotificationTarget): Int {
        return stableNotificationId(
            TYPE_CARTELERA,
            target.materia,
            target.titulo,
            target.fecha,
            target.autor
        )
    }

    private fun cursadaNotificationId(target: CursadaNotificationTarget): Int {
        return stableNotificationId(TYPE_CURSADA, target.materia, target.fechaModificacion)
    }

    private fun avisoNotificationId(target: AvisoNotificationTarget): Int {
        return stableNotificationId(
            TYPE_AVISO,
            target.titulo,
            target.mensaje,
            target.autor,
            target.fecha
        )
    }

}
