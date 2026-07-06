package com.overcoders.unlpcarteleranotifier.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.overcoders.unlpcarteleranotifier.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reprograma los trabajos periódicos después de un reinicio del dispositivo o de una
 * actualización del paquete, escenarios donde la planificación previa puede perderse.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        // El receiver debe liberar rápido el hilo principal. Continuamos la lectura de
        // preferencias y la reprogramación en segundo plano y cerramos con finish().
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                WorkScheduler.cancelLegacyPolling(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
