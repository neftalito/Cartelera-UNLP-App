/** Modela el estado y la ocupación informada de un aula. */
package com.overcoders.unlpcarteleranotifier.model

data class AulaEstado(
    val aulaNombre: String,
    val aulaId: String,
    val materia: String,
    val horaDesde: String,
    val horaHasta: String,
)
