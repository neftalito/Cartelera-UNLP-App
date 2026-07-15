/** Combina el catálogo y los IDs guardados para presentar suscripciones coherentes. */
package com.overcoders.unlpcarteleranotifier.ui

import com.overcoders.unlpcarteleranotifier.model.MateriaCatalogItem
import java.util.Locale

internal enum class SubscriptionStatus {
    VALID,
    UNVERIFIED,
    INVALID,
}

internal data class SubscriptionEntry(
    val id: String,
    val nombre: String,
    val status: SubscriptionStatus,
)

internal fun buildSubscriptionEntries(
    materias: List<MateriaCatalogItem>,
    subscriptionIds: Set<String>,
): List<SubscriptionEntry> {
    val materiasById = materias.associateBy { it.id }
    val catalogAvailable = materias.isNotEmpty()

    return subscriptionIds.map { id ->
        val materia = materiasById[id]
        when {
            materia != null -> SubscriptionEntry(
                id = materia.id,
                nombre = materia.nombre,
                status = SubscriptionStatus.VALID
            )

            catalogAvailable -> SubscriptionEntry(
                id = id,
                nombre = "Suscripción inválida (materia no encontrada)",
                status = SubscriptionStatus.INVALID
            )

            else -> SubscriptionEntry(
                id = id,
                nombre = "Suscripción guardada",
                status = SubscriptionStatus.UNVERIFIED
            )
        }
    }.sortedWith(
        compareBy<SubscriptionEntry> { it.status.ordinal }
            .thenBy { it.nombre.lowercase(Locale.ROOT) }
            .thenBy { it.id }
    )
}
