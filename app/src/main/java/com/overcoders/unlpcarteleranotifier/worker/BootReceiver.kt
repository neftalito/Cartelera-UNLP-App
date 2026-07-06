package com.overcoders.unlpcarteleranotifier.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Mantiene la misma estrategia de compatibilidad que `Application`: ante reinicios o updates,
 * se asegura de que no reaparezcan trabajos del polling local ya reemplazado por FCM.
 *
 * Este receiver existe sólo por la migración. Cuando el stack legacy se elimine de verdad,
 * debería desaparecer junto con `cancelLegacyPolling`.
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

        // El receiver debe liberar rápido el hilo principal. Cancelamos en segundo plano
        // por si el sistema restauró trabajos viejos al reinstalar o actualizar el paquete.
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
