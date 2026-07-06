package com.overcoders.unlpcarteleranotifier.worker

import android.content.Context
import com.overcoders.unlpcarteleranotifier.data.CursadasService
import com.overcoders.unlpcarteleranotifier.data.CursadasStore
import com.overcoders.unlpcarteleranotifier.model.CursadaInfo

/**
 * Mantiene un snapshot local de la tabla de cursadas.
 *
 * Las notificaciones del sistema ya no salen de acá: ahora las publica el backend
 * central por Firebase. Este objeto sigue siendo válido para refrescar el cache local,
 * pero la compatibilidad de extras intent legacy debería eliminarse cuando ya no haga
 * falta soportar aperturas de notificaciones viejas.
 */
object CursadasNotificationDispatcher {
    // Compatibilidad transitoria para intents del flujo local anterior.
    const val EXTRA_TYPE = "extra_notification_type"
    const val TYPE_CURSADA = "cursada"
    const val EXTRA_MATERIA = "extra_cursada_materia"
    const val EXTRA_INICIO = "extra_cursada_inicio"
    const val EXTRA_HORARIOS = "extra_cursada_horarios"
    const val EXTRA_FECHA_MODIFICACION = "extra_cursada_fecha_modificacion"

    suspend fun process(context: Context): List<CursadaInfo> {
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

        CursadasStore.save(context, current, result.tableHash)
        return current
    }
}
