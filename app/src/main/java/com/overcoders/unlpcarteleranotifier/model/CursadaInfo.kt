package com.overcoders.unlpcarteleranotifier.model

data class CursadaInfo(
    val materia: String,
    val inicioCursadaHtml: String,
    val horariosCursadaHtml: String,
    val ultimaModificacion: String,
    val ultimaModificacionEpochMillis: Long?
)
