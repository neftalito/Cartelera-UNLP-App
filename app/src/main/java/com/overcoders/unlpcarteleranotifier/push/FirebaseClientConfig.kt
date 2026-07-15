/**
 * Valida la configuración pública de Firebase y construye sus opciones de cliente Android.
 */
package com.overcoders.unlpcarteleranotifier.push

import com.google.firebase.FirebaseOptions
import com.overcoders.unlpcarteleranotifier.BuildConfig

/**
 * Puente entre propiedades privadas de Gradle y la inicialización manual de Firebase.
 */
object FirebaseClientConfig {
    val projectId: String = BuildConfig.FIREBASE_PROJECT_ID.trim()
    val applicationId: String = BuildConfig.FIREBASE_APPLICATION_ID.trim()
    val apiKey: String = BuildConfig.FIREBASE_API_KEY.trim()
    val gcmSenderId: String = BuildConfig.FIREBASE_GCM_SENDER_ID.trim()

    fun isConfigured(): Boolean = BuildConfig.FIREBASE_CONFIGURATION_VALID

    fun buildOptions(): FirebaseOptions {
        check(isConfigured()) { "La configuración Firebase no superó la validación de Gradle." }
        // FCM del lado cliente no necesita todo el ecosistema de Firebase configurado.
        return FirebaseOptions.Builder()
            .setProjectId(projectId)
            .setApplicationId(applicationId)
            .setApiKey(apiKey)
            .setGcmSenderId(gcmSenderId)
            .build()
    }
}
