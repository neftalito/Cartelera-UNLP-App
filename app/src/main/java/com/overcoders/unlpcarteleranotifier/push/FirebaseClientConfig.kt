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
    val serverBaseUrl: String = BuildConfig.FIREBASE_SERVER_BASE_URL.trim()
    val serverApiToken: String = BuildConfig.FIREBASE_SERVER_API_TOKEN.trim()

    fun isConfigured(): Boolean {
        return projectId.isRealValue() &&
            applicationId.isRealValue() &&
            apiKey.isRealValue() &&
            gcmSenderId.isRealValue()
    }

    fun isServerConfigured(): Boolean {
        return serverBaseUrl.isNotBlank() &&
            !serverBaseUrl.contains("your-firebase-sync-server.example.com")
    }

    fun buildOptions(): FirebaseOptions {
        // FCM del lado cliente no necesita todo el ecosistema de Firebase configurado.
        return FirebaseOptions.Builder()
            .setProjectId(projectId)
            .setApplicationId(applicationId)
            .setApiKey(apiKey)
            .setGcmSenderId(gcmSenderId)
            .build()
    }

    private fun String.isRealValue(): Boolean {
        return isNotBlank() &&
            !startsWith("example-") &&
            !contains(":android:exampleapp") &&
            this != "1234567890"
    }
}
