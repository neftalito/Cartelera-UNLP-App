/** Interpreta los campos de los data messages recibidos desde FCM. */
package com.overcoders.unlpcarteleranotifier.push

import com.overcoders.unlpcarteleranotifier.model.AvisoNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.CarteleraNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.CursadaNotificationTarget

internal sealed interface PushPayload {
    data class Cartelera(val target: CarteleraNotificationTarget) : PushPayload
    data class Cursada(val target: CursadaNotificationTarget) : PushPayload
    data class Aviso(val target: AvisoNotificationTarget) : PushPayload
}

internal fun parsePushPayload(data: Map<String, String>): PushPayload? {
    return when (data.value("type")) {
        "cartelera" -> {
            if (!data.hasFields(CARTELERA_FIELDS)) return null
            val isAnulado = data.value("is_anulado")?.toBooleanStrictOrNull() ?: return null
            createCarteleraNotificationTarget(
                materiaId = data["materia_id"],
                materia = data["materia"],
                titulo = data["titulo"],
                fecha = data["fecha"],
                autor = data["autor"],
                resumen = data["resumen"],
                isAnulado = isAnulado
            )?.let(PushPayload::Cartelera)
        }

        "cursada" -> {
            if (!data.hasFields(CURSADA_FIELDS)) return null
            createCursadaNotificationTarget(
                materiaId = data["materia_id"],
                materia = data["materia"],
                fechaModificacion = data["fecha_modificacion"]
            )?.let(PushPayload::Cursada)
        }

        "aviso" -> {
            if (!data.hasFields(AVISO_FIELDS)) return null
            createAvisoNotificationTarget(
                titulo = data["titulo"],
                mensaje = data["mensaje"],
                autor = data["autor"],
                fecha = data["fecha"]
            )?.let(PushPayload::Aviso)
        }

        else -> null
    }
}

private fun Map<String, String>.value(key: String): String? =
    this[key]?.trim()?.takeIf(String::isNotEmpty)

private fun Map<String, String>.hasFields(fields: Set<String>): Boolean =
    fields.all(::containsKey)

private val CARTELERA_FIELDS = setOf(
    "materia_id",
    "materia",
    "titulo",
    "fecha",
    "autor",
    "resumen",
    "is_anulado",
)
private val CURSADA_FIELDS = setOf("materia_id", "materia", "fecha_modificacion")
private val AVISO_FIELDS = setOf("titulo", "mensaje", "autor", "fecha")
