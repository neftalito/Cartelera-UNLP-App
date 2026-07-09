package com.overcoders.unlpcarteleranotifier

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

internal const val MAX_HISTORY_SIZE = 20

internal data class NavigationState(
    val destination: AppDestinations,
    val category: MainCategory,
)

enum class MainCategory(
    val label: String,
    val icon: ImageVector,
    val destinations: List<AppDestinations>,
    val initialDestination: AppDestinations,
) {
    MATERIAS(
        label = "Materias",
        icon = Icons.Default.EventAvailable,
        destinations = listOf(
            AppDestinations.CARTELERA,
            AppDestinations.CURSADAS,
            AppDestinations.SUBSCRIPCIONES
        ),
        initialDestination = AppDestinations.CARTELERA
    ),
    HERRAMIENTAS(
        label = "Herramientas",
        icon = Icons.Default.Build,
        destinations = listOf(
            AppDestinations.AULAS,
            AppDestinations.HORARIOS
        ),
        initialDestination = AppDestinations.AULAS
    ),
    AJUSTES(
        label = "Ajustes",
        icon = Icons.Default.Settings,
        destinations = listOf(AppDestinations.AJUSTES),
        initialDestination = AppDestinations.AJUSTES
    ),
}

enum class AppDestinations {
    CARTELERA,
    CURSADAS,
    SUBSCRIPCIONES,
    HORARIOS,
    AULAS,
    AJUSTES,
}

internal val AppDestinations.label: String
    get() = when (this) {
        AppDestinations.CARTELERA -> "Cartelera"
        AppDestinations.CURSADAS -> "Cursadas"
        AppDestinations.SUBSCRIPCIONES -> "Suscripciones"
        AppDestinations.HORARIOS -> "Reservas de aulas"
        AppDestinations.AULAS -> "Estado de aulas"
        AppDestinations.AJUSTES -> "Ajustes"
    }
