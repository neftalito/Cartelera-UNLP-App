package com.overcoders.unlpcarteleranotifier.push

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.overcoders.unlpcarteleranotifier.MainActivity
import com.overcoders.unlpcarteleranotifier.R
import com.overcoders.unlpcarteleranotifier.model.AvisoNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.CarteleraNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.CursadaNotificationTarget
import com.overcoders.unlpcarteleranotifier.worker.NotificationHelper
import com.overcoders.unlpcarteleranotifier.worker.NotificationHelper.CHANNEL_ID

/**
 * Convierte payloads push en notificaciones del sistema y también reconstruye el target
 * mínimo desde los intents de apertura.
 */
object PushNotificationDispatcher {
    private const val EXTRA_PUSH_TYPE = "extra_push_type"
    private const val TYPE_CARTELERA = "push_cartelera"
    private const val TYPE_CURSADA = "push_cursada"
    private const val EXTRA_MATERIA_ID = "extra_push_materia_id"
    private const val EXTRA_MATERIA = "extra_push_materia"
    private const val EXTRA_TITULO = "extra_push_titulo"
    private const val EXTRA_FECHA = "extra_push_fecha"
    private const val EXTRA_AUTOR = "extra_push_autor"
    private const val EXTRA_RESUMEN = "extra_push_resumen"
    private const val EXTRA_ANULADO = "extra_push_anulado"
    private const val EXTRA_FECHA_MODIFICACION = "extra_push_fecha_modificacion"

    @SuppressLint("MissingPermission")
    fun showCarteleraNotification(
        context: Context,
        target: CarteleraNotificationTarget
    ) {
        if (!canPostNotifications(context)) return

        NotificationHelper.ensureChannel(context)
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
        if (!canPostNotifications(context)) return

        NotificationHelper.ensureChannel(context)
        NotificationManagerCompat.from(context).notify(
            avisoNotificationId(target),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(target.titulo)
                .setContentText(target.autor.ifBlank { "Aviso general" })
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        target.mensaje.ifBlank {
                            "Tenés un nuevo aviso general."
                        }
                    )
                )
                .setContentIntent(avisoPendingIntent(context))
                .setAutoCancel(true)
                .build()
        )
    }

    @SuppressLint("MissingPermission")
    fun showCursadaNotification(
        context: Context,
        target: CursadaNotificationTarget
    ) {
        if (!canPostNotifications(context)) return

        NotificationHelper.ensureChannel(context)
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

    fun carteleraTargetFromIntent(intent: Intent?): CarteleraNotificationTarget? {
        if (intent?.getStringExtra(EXTRA_PUSH_TYPE) != TYPE_CARTELERA) {
            return null
        }
        val materia = intent.getStringExtra(EXTRA_MATERIA) ?: return null
        val titulo = intent.getStringExtra(EXTRA_TITULO) ?: return null
        val fecha = intent.getStringExtra(EXTRA_FECHA) ?: return null
        return CarteleraNotificationTarget(
            materiaId = intent.getStringExtra(EXTRA_MATERIA_ID),
            materia = materia,
            titulo = titulo,
            fecha = fecha,
            autor = intent.getStringExtra(EXTRA_AUTOR).orEmpty(),
            resumen = intent.getStringExtra(EXTRA_RESUMEN).orEmpty(),
            isAnulado = intent.getBooleanExtra(EXTRA_ANULADO, false)
        )
    }

    fun cursadaTargetFromIntent(intent: Intent?): CursadaNotificationTarget? {
        if (intent?.getStringExtra(EXTRA_PUSH_TYPE) != TYPE_CURSADA) {
            return null
        }
        val materia = intent.getStringExtra(EXTRA_MATERIA) ?: return null
        return CursadaNotificationTarget(
            materiaId = intent.getStringExtra(EXTRA_MATERIA_ID),
            materia = materia,
            fechaModificacion = intent.getStringExtra(EXTRA_FECHA_MODIFICACION).orEmpty()
        )
    }

    private fun carteleraPendingIntent(
        context: Context,
        target: CarteleraNotificationTarget
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            // Guardamos sólo un target liviano para no depender del límite de tamaño de FCM.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
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
        val intent = Intent(context, MainActivity::class.java).apply {
            // La pantalla resuelve el detalle final una vez que vuelve a cargar su snapshot.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
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

    private fun avisoPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            AVISO_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun carteleraNotificationId(target: CarteleraNotificationTarget): Int {
        return (target.materia + target.titulo + target.fecha + target.autor).hashCode()
    }

    private fun cursadaNotificationId(target: CursadaNotificationTarget): Int {
        return (target.materia + target.fechaModificacion).hashCode()
    }

    private fun avisoNotificationId(target: AvisoNotificationTarget): Int {
        return (target.titulo + target.mensaje + target.autor + target.fecha).hashCode()
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return context.checkPermission(
            Manifest.permission.POST_NOTIFICATIONS,
            Process.myPid(),
            Process.myUid()
        ) == PackageManager.PERMISSION_GRANTED
    }

    private const val AVISO_REQUEST_CODE = 10_001
}
