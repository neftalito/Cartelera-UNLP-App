/** Construye las acciones de encabezado propias del listado y el detalle de cartelera. */
package com.overcoders.unlpcarteleranotifier.ui.cartelera

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.HeaderActionPlacement
import com.overcoders.unlpcarteleranotifier.model.Mensaje
import com.overcoders.unlpcarteleranotifier.ui.common.copyPlainText
import com.overcoders.unlpcarteleranotifier.ui.common.sharePlainText

internal fun announcementDetailHeaderActions(
    context: Context,
    announcement: Mensaje,
    onBack: () -> Unit,
): List<HeaderAction> {
    val shareText = buildShareText(announcement)
    return listOf(
        HeaderAction(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Volver",
            placement = HeaderActionPlacement.Leading,
            onClick = onBack,
        ),
        HeaderAction(
            icon = Icons.Default.ContentCopy,
            contentDescription = "Copiar anuncio",
            onClick = {
                copyPlainText(
                    context = context,
                    label = "Anuncio",
                    text = shareText,
                )
            },
        ),
        HeaderAction(
            icon = Icons.Default.Share,
            contentDescription = "Compartir anuncio",
            onClick = {
                sharePlainText(
                    context = context,
                    text = shareText,
                    chooserTitle = "Compartir anuncio",
                )
            },
        ),
    )
}

internal fun announcementsRefreshHeaderActions(
    refreshesCatalog: Boolean,
    enabled: Boolean,
    onRefresh: () -> Unit,
): List<HeaderAction> = listOf(
    HeaderAction(
        icon = Icons.Default.Refresh,
        contentDescription = if (refreshesCatalog) {
            "Refrescar materias"
        } else {
            "Refrescar anuncios"
        },
        enabled = enabled,
        onClick = onRefresh,
    )
)
