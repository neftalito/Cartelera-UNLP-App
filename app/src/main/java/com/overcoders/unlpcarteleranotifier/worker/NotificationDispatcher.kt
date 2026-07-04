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
import androidx.core.text.HtmlCompat
import com.overcoders.unlpcarteleranotifier.R
import com.overcoders.unlpcarteleranotifier.MainActivity
import com.overcoders.unlpcarteleranotifier.data.AnunciosService
import com.overcoders.unlpcarteleranotifier.data.MateriasRepository
import com.overcoders.unlpcarteleranotifier.data.SettingsStore
import com.overcoders.unlpcarteleranotifier.data.SubscripcionesStore
import com.overcoders.unlpcarteleranotifier.model.Mensaje
import com.overcoders.unlpcarteleranotifier.worker.NotificationHelper.CHANNEL_ID
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sincroniza el feed de cartelera con el baseline persistido y emite notificaciones
 * únicamente para los anuncios que aparecieron desde la última ejecución exitosa.
 */
object NotificationDispatcher {
    const val EXTRA_MATERIA = "extra_materia"
    const val EXTRA_TITULO = "extra_titulo"
    const val EXTRA_CUERPO_HTML = "extra_cuerpo_html"
    const val EXTRA_FECHA = "extra_fecha"
    const val EXTRA_AUTOR = "extra_autor"
    const val EXTRA_ANULADO = "extra_anulado"
    const val EXTRA_ADJUNTOS = "extra_adjuntos"

    @SuppressLint("MissingPermission")
    suspend fun process(context: Context) {
        val anunciosService = AnunciosService()

        val notifyAll = SettingsStore.notifyAllFlow(context).first()
        val lastTotal = SettingsStore.getLastTotal(context)

        val totalResponse = try {
            anunciosService.fetch(desde = 0, cantidad = 0, idMateria = null)
        } catch (_: Exception) {
            return
        }
        val total = totalResponse.total

        // La primera corrida sólo fija el baseline para no disparar notificaciones retroactivas
        // sobre todo el histórico disponible al instalar la app.
        if (lastTotal < 0) {
            SettingsStore.setLastTotal(context, total)
            return
        }

        if (total <= lastTotal) {
            if (total != lastTotal) {
                SettingsStore.setLastTotal(context, total)
            }
            return
        }

        val diff = total - lastTotal
        // La API permite pedir el bloque más reciente desde offset 0; con el delta de totales
        // alcanzamos a reconstruir exactamente los nuevos anuncios pendientes.
        val nuevos = try {
            anunciosService.fetch(desde = 0, cantidad = diff, idMateria = null).mensajes
        } catch (_: Exception) {
            return
        }

        val anunciosParaNotificar = if (notifyAll) {
            nuevos
        } else {
            val subs = SubscripcionesStore.subscripcionesFlow(context).first()
            if (subs.isEmpty()) {
                SettingsStore.setLastTotal(context, total)
                return
            }
            // El feed de anuncios no expone ids canónicos de materia, así que resolvemos las
            // suscripciones comparando nombres normalizados contra el catálogo cacheado.
            val idToName = MateriasRepository.loadIdToNameMap(context)
            val subscripcionesNombres = subs.mapNotNull { idToName[it] }.toSet()
            if (subscripcionesNombres.isEmpty()) {
                SettingsStore.setLastTotal(context, total)
                return
            }
            nuevos.filter { anuncio ->
                val nombre = MateriasRepository.normalizeName(anuncio.materia)
                subscripcionesNombres.contains(nombre)
            }
        }

        if (anunciosParaNotificar.isNotEmpty() && canPostNotifications(context)) {
            NotificationHelper.ensureChannel(context)
            val manager = NotificationManagerCompat.from(context)
            // Revertimos el listado para que el sistema muestre primero los anuncios más viejos
            // y conserve un orden cronológico natural al apilar notificaciones.
            anunciosParaNotificar.asReversed().forEach { anuncio ->
                manager.notify(notificationIdFor(anuncio), buildNotification(context, anuncio))
            }
        } else if (anunciosParaNotificar.isNotEmpty()) {
            SettingsStore.setRequestNotificationsPermission(context, true)
        }

        SettingsStore.setLastTotal(context, total)
    }

    private fun buildNotification(context: Context, anuncio: Mensaje) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(anuncio.titulo)
            .setContentText(anuncio.materia)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        HtmlCompat.fromHtml(
                            anuncio.cuerpoHtml,
                            HtmlCompat.FROM_HTML_MODE_LEGACY
                        ).toString().trim()
                    )
            )
            .setContentIntent(notificationIntent(context, anuncio))
            .setAutoCancel(true)
            .build()

    // Nota: ".hashCode" puede generar colisiones entre las notificaciones generadas por la app. Es algo muy poco probable, pero posible.
    private fun notificationIdFor(anuncio: Mensaje): Int {
        return (anuncio.materia + anuncio.titulo + anuncio.fecha + anuncio.autor).hashCode()
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

    private fun notificationIntent(context: Context, anuncio: Mensaje): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_MATERIA, anuncio.materia)
            putExtra(EXTRA_TITULO, anuncio.titulo)
            putExtra(EXTRA_CUERPO_HTML, anuncio.cuerpoHtml)
            putExtra(EXTRA_FECHA, anuncio.fecha)
            putExtra(EXTRA_AUTOR, anuncio.autor)
            putExtra(EXTRA_ANULADO, anuncio.isAnulado)
            putExtra(EXTRA_ADJUNTOS, serializeAdjuntos(anuncio))
        }
        return PendingIntent.getActivity(
            context,
            notificationIdFor(anuncio),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun serializeAdjuntos(anuncio: Mensaje): String {
        val adjuntosArray = JSONArray()
        anuncio.adjuntos.forEach { adjunto ->
            adjuntosArray.put(
                JSONObject()
                    .put("nombre", adjunto.nombre)
                    .put("publicPath", adjunto.publicPath)
            )
        }
        return adjuntosArray.toString()
    }
}
