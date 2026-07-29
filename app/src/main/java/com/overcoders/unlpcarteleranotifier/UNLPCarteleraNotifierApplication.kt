/** Inicializa los servicios globales de Firebase y notificaciones al arrancar el proceso. */
package com.overcoders.unlpcarteleranotifier

import android.app.Application
import com.overcoders.unlpcarteleranotifier.push.FirebaseInitializer
import com.overcoders.unlpcarteleranotifier.push.FirebaseTopicSyncManager

/** Inicializa el flujo de notificaciones push antes de que la UI empiece a consumirlo. */
class UNLPCarteleraNotifierApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseInitializer.ensureInitialized(this)
        FirebaseTopicSyncManager.requestRegistration(this)
        // Sincronizar desde el arranque también permite que release retire avisos_testing
        // si encuentra estado persistido por una compilación debug anterior.
        FirebaseTopicSyncManager.requestSync(this)
    }
}
