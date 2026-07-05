package com.overcoders.unlpcarteleranotifier.worker

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
import com.overcoders.unlpcarteleranotifier.data.CursadasService
import com.overcoders.unlpcarteleranotifier.data.CursadasStore
import com.overcoders.unlpcarteleranotifier.data.MateriasRepository
import com.overcoders.unlpcarteleranotifier.data.SettingsStore
import com.overcoders.unlpcarteleranotifier.data.SubscripcionesStore
import com.overcoders.unlpcarteleranotifier.model.CursadaInfo
import com.overcoders.unlpcarteleranotifier.worker.NotificationHelper.CHANNEL_ID
import kotlinx.coroutines.flow.first

/**
 * Mantiene un snapshot local de la tabla de cursadas y notifica solamente
 * las materias cuya información cambió desde la última descarga exitosa.
 */
object CursadasNotificationDispatcher {
    const val EXTRA_TYPE = "extra_notification_type"
    const val TYPE_CURSADA = "cursada"
    const val EXTRA_MATERIA = "extra_cursada_materia"
    const val EXTRA_INICIO = "extra_cursada_inicio"
    const val EXTRA_HORARIOS = "extra_cursada_horarios"
    const val EXTRA_FECHA_MODIFICACION = "extra_cursada_fecha_modificacion"

    @SuppressLint("MissingPermission")
    suspend fun process(
        context: Context,
        notifyChanges: Boolean = true
    ): List<CursadaInfo> {
        val previous = CursadasStore.load(context)
        val previousHash = CursadasStore.getTableHash(context)

        val result = try {
            CursadasService().fetchAndParse()
        } catch (e: Exception) {
            // Si ya tenemos cache, preferimos seguir mostrando lo último conocido antes que
            // dejar la sección vacía por un error transitorio de red o scraping.
            if (!previous.isEmpty()) {
                return previous
            }
            throw e
        }

        val current = result.cursadas
        if (current.isEmpty()) return previous

        // Primera sincronización exitosa: persistimos baseline sin notificar cambios viejos.
        if (previousHash.isBlank()) {
            CursadasStore.save(context, current, result.tableHash)
            return current
        }

        // El hash evita recalcular diferencias fila por fila cuando la tabla remota no cambió.
        if (previousHash == result.tableHash) {
            if (previous != current) {
                CursadasStore.save(context, current, result.tableHash)
            }
            return current
        }

        val changed = findChanged(previous, current)
        val changedForNotifications = filterBySubscriptions(context, changed)

        if (notifyChanges && changedForNotifications.isNotEmpty()) {
            if (canPostNotifications(context)) {
                NotificationHelper.ensureChannel(context)
                val manager = NotificationManagerCompat.from(context)
                changedForNotifications.forEach { cursada ->
                    manager.notify(
                        notificationIdFor(cursada),
                        buildNotification(context, cursada)
                    )
                }
            }
        }

        CursadasStore.save(context, current, result.tableHash)
        return current
    }

    private fun findChanged(previous: List<CursadaInfo>, current: List<CursadaInfo>): List<CursadaInfo> {
        val previousByMateria = previous.associateBy { it.materia }
        return current.filter { cursada ->
            val old = previousByMateria[cursada.materia]
            // Con chequear el campo "ultimaModificacion" ya es suficiente, no hace falta chequear todo.
            old == null || old.ultimaModificacion != cursada.ultimaModificacion
        }
    }

    private suspend fun filterBySubscriptions(
        context: Context,
        cursadas: List<CursadaInfo>
    ): List<CursadaInfo> {
        if (cursadas.isEmpty()) return emptyList()

        val notifyAll = SettingsStore.notifyAllFlow(context).first()
        if (notifyAll) return cursadas

        val subs = SubscripcionesStore.subscripcionesFlow(context).first()
        if (subs.isEmpty()) return emptyList()

        // Igual que en cartelera, la tabla no expone ids estables de materia. Normalizamos
        // nombres para comparar suscripciones guardadas con el texto visible al usuario.
        val idToName = MateriasRepository.loadIdToNameMap(context)
        val subscripcionesNombres = subs.mapNotNull { idToName[it] }.toSet()
        if (subscripcionesNombres.isEmpty()) return emptyList()

        return cursadas.filter { cursada ->
            val nombre = MateriasRepository.normalizeName(cursada.materia)
            subscripcionesNombres.contains(nombre)
        }
    }

    private fun buildNotification(context: Context, cursada: CursadaInfo) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(cursada.materia)
            .setContentText("Se actualizó la información de cursada")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "La materia ${cursada.materia} actualizó su información de cursada (${cursada.ultimaModificacion.ifBlank { "sin fecha" }})."
                )
            )
            .setContentIntent(notificationIntent(context, cursada))
            .setAutoCancel(true)
            .build()

    private fun notificationIntent(context: Context, cursada: CursadaInfo): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TYPE, TYPE_CURSADA)
            putExtra(EXTRA_MATERIA, cursada.materia)
            putExtra(EXTRA_INICIO, cursada.inicioCursadaHtml)
            putExtra(EXTRA_HORARIOS, cursada.horariosCursadaHtml)
            putExtra(EXTRA_FECHA_MODIFICACION, cursada.ultimaModificacion)
        }
        return PendingIntent.getActivity(
            context,
            notificationIdFor(cursada),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // Nota: ".hashCode" puede generar colisiones entre las notificaciones generadas por la app. Es algo muy poco probable, pero posible.
    private fun notificationIdFor(cursada: CursadaInfo): Int {
        return (cursada.materia + cursada.ultimaModificacion).hashCode()
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
}
