package com.overcoders.unlpcarteleranotifier.model

data class CarteleraNotificationTarget(
    val materiaId: String?,
    val materia: String,
    val titulo: String,
    val fecha: String,
    val autor: String,
    val resumen: String,
    val isAnulado: Boolean
)

data class CursadaNotificationTarget(
    val materiaId: String?,
    val materia: String,
    val fechaModificacion: String
)

data class AvisoNotificationTarget(
    val titulo: String,
    val mensaje: String,
    val autor: String,
    val fecha: String
)
