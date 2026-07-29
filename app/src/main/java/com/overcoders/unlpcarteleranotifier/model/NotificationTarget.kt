/** Define los destinos mínimos y guardables de cada tipo de notificación. */
package com.overcoders.unlpcarteleranotifier.model

import java.io.Serializable

data class CarteleraNotificationTarget(
    val materiaId: String?,
    val materia: String,
    val titulo: String,
    val fecha: String,
    val autor: String,
    val resumen: String,
    val isAnulado: Boolean
) : Serializable

data class CursadaNotificationTarget(
    val materiaId: String?,
    val materia: String,
    val fechaModificacion: String
) : Serializable

data class AvisoNotificationTarget(
    val titulo: String,
    val mensaje: String,
    val autor: String,
    val fecha: String
) : Serializable
