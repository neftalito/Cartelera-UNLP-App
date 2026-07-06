package com.overcoders.unlpcarteleranotifier.push

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp

/**
 * Inicializa Firebase manualmente desde `BuildConfig`.
 *
 * Esto desacopla la app de `google-services.json` y permite que el repo conserve valores
 * de ejemplo mientras cada instalación inyecta su configuración privada local.
 */
object FirebaseInitializer {
    private const val TAG = "FirebaseInitializer"

    fun ensureInitialized(context: Context): Boolean {
        if (FirebaseApp.getApps(context).isNotEmpty()) {
            return true
        }
        if (!FirebaseClientConfig.isConfigured()) {
            return false
        }

        return runCatching {
            FirebaseApp.initializeApp(context, FirebaseClientConfig.buildOptions())
        }.onFailure { error ->
            Log.w(TAG, "No se pudo inicializar Firebase manualmente.", error)
        }.getOrNull() != null
    }
}
