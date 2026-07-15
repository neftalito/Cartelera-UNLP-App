/** Agrupa los modelos de filtros, páginas y filas de reservas eventuales. */
package com.overcoders.unlpcarteleranotifier.model

data class EventualReservationFilterOption(
    val id: Int,
    val label: String,
)

data class EventualReservationFilterOptions(
    val classrooms: List<EventualReservationFilterOption>,
    val subjects: List<EventualReservationFilterOption>,
)

data class EventualReservation(
    val id: Int,
    val classroomName: String,
    val subjectName: String,
    val teacherName: String,
    val reason: String,
    val date: String,
    val startTime: String,
    val endTime: String,
)

data class EventualReservationsPage(
    val reservations: List<EventualReservation>,
    val currentPage: Int,
    val nextPage: Int?,
    val totalCount: Int,
)
