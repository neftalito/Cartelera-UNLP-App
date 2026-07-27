/** Construye las acciones de copiar, compartir y refrescar horarios. */
package com.overcoders.unlpcarteleranotifier.ui.horarios

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.model.HorarioMateria
import com.overcoders.unlpcarteleranotifier.ui.common.copyPlainText
import com.overcoders.unlpcarteleranotifier.ui.common.sharePlainText

internal fun horariosHeaderActions(
    context: Context,
    canShare: Boolean,
    horarioMateria: HorarioMateria?,
    refreshesCatalog: Boolean,
    refreshEnabled: Boolean,
    onRefresh: () -> Unit,
): List<HeaderAction> = listOf(
    HeaderAction(
        icon = Icons.Default.ContentCopy,
        contentDescription = "Copiar reservas de aulas",
        enabled = canShare,
        onClick = copyClick@{
            val horarios = horarioMateria ?: return@copyClick
            copyPlainText(
                context = context,
                label = "Reservas de aulas",
                text = buildShareText(horarios),
            )
        },
    ),
    HeaderAction(
        icon = Icons.Default.Share,
        contentDescription = "Compartir reservas de aulas",
        enabled = canShare,
        onClick = shareClick@{
            val horarios = horarioMateria ?: return@shareClick
            sharePlainText(
                context = context,
                text = buildShareText(horarios),
                chooserTitle = "Compartir reservas de aulas",
            )
        },
    ),
    HeaderAction(
        icon = Icons.Default.Refresh,
        contentDescription = if (refreshesCatalog) {
            "Refrescar materias"
        } else {
            "Refrescar horarios"
        },
        enabled = refreshEnabled,
        onClick = onRefresh,
    ),
)
