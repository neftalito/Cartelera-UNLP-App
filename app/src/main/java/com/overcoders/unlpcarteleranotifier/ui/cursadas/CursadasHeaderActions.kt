/** Construye las acciones de encabezado del listado y el detalle de cursadas. */
package com.overcoders.unlpcarteleranotifier.ui.cursadas

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.HeaderActionPlacement
import com.overcoders.unlpcarteleranotifier.model.CursadaInfo
import com.overcoders.unlpcarteleranotifier.ui.common.copyPlainText
import com.overcoders.unlpcarteleranotifier.ui.common.sharePlainText

internal fun cursadaDetailHeaderActions(
    context: Context,
    cursada: CursadaInfo,
    onBack: () -> Unit,
): List<HeaderAction> {
    val shareText = buildShareText(cursada)
    return listOf(
        HeaderAction(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Volver",
            placement = HeaderActionPlacement.Leading,
            onClick = onBack,
        ),
        HeaderAction(
            icon = Icons.Default.ContentCopy,
            contentDescription = "Copiar cursada",
            onClick = {
                copyPlainText(
                    context = context,
                    label = "Cursada",
                    text = shareText,
                )
            },
        ),
        HeaderAction(
            icon = Icons.Default.Share,
            contentDescription = "Compartir cursada",
            onClick = {
                sharePlainText(
                    context = context,
                    text = shareText,
                    chooserTitle = "Compartir cursada",
                )
            },
        ),
    )
}

internal fun cursadasListHeaderActions(
    loading: Boolean,
    hasUnseenCursadas: Boolean,
    onMarkAllAsSeen: () -> Unit,
    onRefresh: () -> Unit,
): List<HeaderAction> = listOf(
    HeaderAction(
        icon = Icons.Default.DoneAll,
        contentDescription = "Marcar todas las cursadas como vistas",
        enabled = !loading && hasUnseenCursadas,
        onClick = onMarkAllAsSeen,
    ),
    HeaderAction(
        icon = Icons.Default.Refresh,
        contentDescription = "Refrescar cursadas",
        enabled = !loading,
        onClick = onRefresh,
    ),
)
