/** Construye las acciones de copiar, compartir y refrescar el estado de aulas. */
package com.overcoders.unlpcarteleranotifier.ui.aulas

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.model.AulaEstado
import com.overcoders.unlpcarteleranotifier.ui.common.copyPlainText
import com.overcoders.unlpcarteleranotifier.ui.common.sharePlainText

internal fun aulasHeaderActions(
    context: Context,
    aulas: List<AulaEstado>,
    canShare: Boolean,
    loading: Boolean,
    onRefresh: () -> Unit,
): List<HeaderAction> = listOf(
    HeaderAction(
        icon = Icons.Default.ContentCopy,
        contentDescription = "Copiar estado de aulas",
        enabled = canShare,
        onClick = {
            copyPlainText(
                context = context,
                label = "Estado de aulas",
                text = buildAulasShareText(aulas),
            )
        },
    ),
    HeaderAction(
        icon = Icons.Default.Share,
        contentDescription = "Compartir estado de aulas",
        enabled = canShare,
        onClick = {
            sharePlainText(
                context = context,
                text = buildAulasShareText(aulas),
                chooserTitle = "Compartir estado de aulas",
            )
        },
    ),
    HeaderAction(
        icon = Icons.Default.Refresh,
        contentDescription = "Refrescar estado de aulas",
        enabled = !loading,
        onClick = onRefresh,
    ),
)
