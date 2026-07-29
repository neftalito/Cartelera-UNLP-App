/** Renderiza las acciones contextuales de la pantalla activa en la barra superior. */
package com.overcoders.unlpcarteleranotifier.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.overcoders.unlpcarteleranotifier.HeaderAction

@Composable
internal fun HeaderActionsRow(
    actions: List<HeaderAction>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        actions.forEach { action ->
            IconButton(onClick = action.onClick, enabled = action.enabled) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = action.contentDescription
                )
            }
        }
    }
}
