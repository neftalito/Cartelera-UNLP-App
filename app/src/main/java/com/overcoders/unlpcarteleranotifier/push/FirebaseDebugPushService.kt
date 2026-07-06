package com.overcoders.unlpcarteleranotifier.push

import com.overcoders.unlpcarteleranotifier.data.AppHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object FirebaseDebugPushService {
    enum class PushType(val queryValue: String, val label: String) {
        CARTELERA(queryValue = "cartelera", label = "cartelera"),
        CURSADA(queryValue = "cursada", label = "cursada"),
    }

    enum class Target(val queryValue: String) {
        GENERAL(queryValue = "general"),
        SPECIFIC(queryValue = "specific"),
        BOTH(queryValue = "both"),
    }

    suspend fun sendTestPush(
        pushType: PushType,
        target: Target,
        materiaId: String,
        materia: String,
    ): String = withContext(Dispatchers.IO) {
        check(FirebaseClientConfig.isServerConfigured()) {
            "Configurá firebase.serverBaseUrl para enviar pushes reales desde DEBUG."
        }

        val normalizedMateriaId = materiaId.trim()
        val normalizedMateria = materia.trim().ifBlank { "Materia de prueba" }
        if (target != Target.GENERAL) {
            require(normalizedMateriaId.isNotBlank()) {
                "Necesitás un idMateria real para probar un push por materia."
            }
        }

        val urlBuilder = FirebaseClientConfig.serverBaseUrl
            .trim()
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?: error("La URL de firebase.serverBaseUrl no es válida.")

        urlBuilder.addPathSegment("push")
        urlBuilder.addPathSegment("test")
        urlBuilder.addQueryParameter("push_type", pushType.queryValue)
        urlBuilder.addQueryParameter("target", target.queryValue)
        urlBuilder.addQueryParameter("materia", normalizedMateria)
        if (normalizedMateriaId.isNotBlank()) {
            urlBuilder.addQueryParameter("materia_id", normalizedMateriaId)
        }

        val requestBuilder = Request.Builder()
            .url(urlBuilder.build())
            .post(ByteArray(0).toRequestBody(null))

        FirebaseClientConfig.serverApiToken
            .takeIf { it.isNotBlank() }
            ?.let { requestBuilder.header("X-Api-Key", it) }

        AppHttpClient.instance.newCall(requestBuilder.build()).execute().use { response ->
            val errorBody = response.body.string()
            if (!response.isSuccessful) {
                val detail = errorBody.ifBlank { "HTTP ${response.code}" }
                error("El servidor rechazó el push de prueba: $detail")
            }
        }

        when (target) {
            Target.GENERAL -> {
                "Push de ${pushType.label} enviado al topic ${FirebaseTopics.ALL_MATERIAS}."
            }
            Target.SPECIFIC -> {
                "Push de ${pushType.label} enviado al topic ${FirebaseTopics.forMateria(normalizedMateriaId)}."
            }
            Target.BOTH -> {
                "Push de ${pushType.label} enviado al topic general y al de ${normalizedMateria}."
            }
        }
    }
}
