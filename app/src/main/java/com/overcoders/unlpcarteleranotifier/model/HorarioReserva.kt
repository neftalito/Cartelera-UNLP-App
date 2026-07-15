/** Modela períodos, materias y reservas que componen un horario. */
package com.overcoders.unlpcarteleranotifier.model

data class HorarioPeriodo(
    val nombre: String,
    val desde: String,
    val hasta: String
)

data class HorarioReserva(
    val aula: String,
    val confirmada: Boolean,
    val tipo: String,
    val dia: Int,
    val horaInicio: String,
    val horaFin: String
)

data class HorarioMateria(
    val materiaNombre: String,
    val periodo: HorarioPeriodo,
    val reservas: List<HorarioReserva>
)
