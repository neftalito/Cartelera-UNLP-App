/** Modela las carreras y períodos disponibles del calendario académico. */
package com.overcoders.unlpcarteleranotifier.model

data class CalendarioAcademico(
    val anio: Int,
    val url: String,
    val contenidoHtml: String,
)
