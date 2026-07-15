/** Define las acciones que cada pantalla publica en el encabezado compartido. */
package com.overcoders.unlpcarteleranotifier

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Acción que se renderiza en el encabezado fijo junto al título.
 *
 * Para mostrar acciones desde una pantalla, publicalas con su callback `onHeaderActionsChange`:
 *
 * ```kotlin
 * SideEffect {
 *     onHeaderActionsChange(
 *         listOf(
 *             HeaderAction(
 *                 icon = Icons.Default.Refresh,
 *                 contentDescription = "Refrescar",
 *                 enabled = !loading,
 *                 onClick = { refreshData() }
 *             )
 *         )
 *     )
 * }
 *
 * DisposableEffect(Unit) {
 *     onDispose { onHeaderActionsChange(emptyList()) }
 * }
 * ```
 */
data class HeaderAction(
    val icon: ImageVector,
    val contentDescription: String,
    val enabled: Boolean = true,
    val placement: HeaderActionPlacement = HeaderActionPlacement.Trailing,
    val onClick: () -> Unit,
)

enum class HeaderActionPlacement {
    Leading,
    Trailing,
}
