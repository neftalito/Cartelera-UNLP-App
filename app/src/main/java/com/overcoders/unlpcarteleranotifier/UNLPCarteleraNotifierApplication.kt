package com.overcoders.unlpcarteleranotifier

import android.app.Application
import com.overcoders.unlpcarteleranotifier.push.FirebaseInitializer
import com.overcoders.unlpcarteleranotifier.push.FirebaseTopicSyncManager
import com.overcoders.unlpcarteleranotifier.worker.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Inicializa el flujo de notificaciones push antes de que la UI empiece a consumirlo.
 *
 * Mientras convivan usuarios en el esquema viejo y en el nuevo, también cancela el polling
 * legacy basado en WorkManager para evitar duplicados. Cuando toda la base migrada use FCM,
 * esta compatibilidad debería eliminarse junto con el scheduler anterior.
 */
class UNLPCarteleraNotifierApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        FirebaseInitializer.ensureInitialized(this)
        applicationScope.launch {
            // Compatibilidad transitoria: el servidor central ya hace el polling por nosotros.
            WorkScheduler.cancelLegacyPolling(this@UNLPCarteleraNotifierApplication)
            FirebaseTopicSyncManager.register(this@UNLPCarteleraNotifierApplication)
        }
    }
}
