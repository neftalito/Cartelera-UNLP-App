package com.overcoders.unlpcarteleranotifier.model

data class Mensaje(
    val materia: String,
    val titulo: String,
    val cuerpoHtml: String,
    val fecha: String,
    val autor: String,
    val isAnulado: Boolean,
    val adjuntos: List<Adjunto>
)

data class Adjunto(
    val nombre: String,
    val publicPath: String
)
