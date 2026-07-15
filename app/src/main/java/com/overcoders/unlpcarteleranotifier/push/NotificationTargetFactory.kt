/** Valida payloads y construye destinos tipados de notificación. */
package com.overcoders.unlpcarteleranotifier.push

import com.overcoders.unlpcarteleranotifier.model.AvisoNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.CarteleraNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.CursadaNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.toMateriaCatalogIdOrNull

internal fun createCarteleraNotificationTarget(
    materiaId: String?,
    materia: String?,
    titulo: String?,
    fecha: String?,
    autor: String?,
    resumen: String?,
    isAnulado: Boolean,
): CarteleraNotificationTarget? {
    val normalizedMateriaId = materiaId.optionalText()
        ?.toMateriaCatalogIdOrNull()
        ?.toString()
    return CarteleraNotificationTarget(
        materiaId = normalizedMateriaId,
        materia = materia.requiredText() ?: return null,
        titulo = titulo.requiredText() ?: return null,
        fecha = fecha.requiredText() ?: return null,
        autor = autor.optionalText().orEmpty(),
        resumen = resumen.optionalText().orEmpty(),
        isAnulado = isAnulado
    )
}

internal fun createCursadaNotificationTarget(
    materiaId: String?,
    materia: String?,
    fechaModificacion: String?,
): CursadaNotificationTarget? {
    val normalizedMateriaId = materiaId.optionalText()
        ?.toMateriaCatalogIdOrNull()
        ?.toString()
    return CursadaNotificationTarget(
        materiaId = normalizedMateriaId,
        materia = materia.requiredText() ?: return null,
        fechaModificacion = fechaModificacion.optionalText().orEmpty()
    )
}

internal fun createAvisoNotificationTarget(
    titulo: String?,
    mensaje: String?,
    autor: String?,
    fecha: String?,
): AvisoNotificationTarget? {
    return AvisoNotificationTarget(
        titulo = titulo.requiredText() ?: return null,
        mensaje = mensaje.requiredText() ?: return null,
        autor = autor.optionalText().orEmpty(),
        fecha = fecha.requiredText() ?: return null
    )
}

private fun String?.requiredText(): String? = optionalText()

private fun String?.optionalText(): String? =
    this?.trim()?.takeIf(String::isNotEmpty)
