package com.overcoders.unlpcarteleranotifier.worker

import android.content.Context
import androidx.work.WorkManager

/**
 * Encapsula la programación de trabajos periódicos y recuerda la última configuración
 * aplicada para evitar reenqueues innecesarios al abrir la app o reiniciar el dispositivo.
 */
object WorkScheduler {
    private const val WORK_NAME = "cartelera_worker"
    private const val CURSADAS_WORK_NAME = "cursadas_worker"

    fun cancelLegacyPolling(context: Context) {
        // Compatibilidad transitoria: el backend Firebase reemplaza estas tareas periódicas.
        // Cuando no quede nadie en el esquema anterior, esta API y las clases relacionadas
        // deberían borrarse de verdad en vez de seguir cancelándolas al iniciar.
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(WORK_NAME)
        manager.cancelUniqueWork(CURSADAS_WORK_NAME)
    }

}
