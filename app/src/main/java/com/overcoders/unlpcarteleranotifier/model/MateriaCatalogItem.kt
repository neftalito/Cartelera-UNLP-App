/** Representa una opción identificable del catálogo de materias. */
package com.overcoders.unlpcarteleranotifier.model

data class MateriaCatalogItem(
    val id: String,
    val nombre: String
)

internal fun String.toMateriaCatalogIdOrNull(): Int? =
    trim().toIntOrNull()?.takeIf { it > 0 }
