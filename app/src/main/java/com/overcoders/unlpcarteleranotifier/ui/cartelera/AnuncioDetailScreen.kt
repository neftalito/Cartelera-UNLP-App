/**
 * Presenta el contenido completo de un anuncio y abre o comparte adjuntos seguros.
 */
package com.overcoders.unlpcarteleranotifier.ui.cartelera

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.overcoders.unlpcarteleranotifier.model.Mensaje
import com.overcoders.unlpcarteleranotifier.ui.common.HtmlTextBlock
import com.overcoders.unlpcarteleranotifier.ui.ScrollMoreHint
import com.overcoders.unlpcarteleranotifier.ui.htmlToShareText
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val attachmentOrigin = "https://gestiondocente.info.unlp.edu.ar/".toHttpUrl()

@Composable
fun AnuncioDetailScreen(
    anuncio: Mensaje,
    onBack: () -> Unit,
    warningMessage: String? = null,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val detailScrollState = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(anuncio.titulo, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(anuncio.materia, style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(6.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(anuncio.fecha, style = MaterialTheme.typography.bodySmall)
            Text(anuncio.autor, style = MaterialTheme.typography.bodySmall)
        }

        if (warningMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = warningMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (anuncio.isAnulado) {
            Spacer(Modifier.height(8.dp))
            Text("ANULADO", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(detailScrollState)
                    .padding(bottom = 36.dp),
            ) {
                HtmlTextBlock(
                    html = anuncio.cuerpoHtml,
                    context = context
                )

                if (anuncio.adjuntos.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text("Adjuntos", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    anuncio.adjuntos.forEach { adjunto ->
                        TextButton(
                            onClick = attachmentClick@{
                                val normalizedUri = normalizeAttachmentUri(adjunto.publicPath)
                                if (normalizedUri == null) {
                                    Toast.makeText(
                                        context,
                                        "No se pudo abrir el adjunto",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@attachmentClick
                                }

                                runCatching { uriHandler.openUri(normalizedUri) }
                                    .onFailure {
                                        Toast.makeText(
                                            context,
                                            "No se pudo abrir el adjunto",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        ) {
                            Text(
                                text = adjunto.nombre,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            ScrollMoreHint(
                scrollState = detailScrollState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            )
        }
    }
}

fun normalizeAttachmentUri(rawUri: String): String? {
    val trimmed = rawUri.trim()
    if (trimmed.isEmpty()) return null

    trimmed.toHttpUrlOrNull()?.let { absoluteUrl ->
        val scheme = absoluteUrl.scheme.lowercase(Locale.ROOT)
        if (scheme == "http" || scheme == "https") {
            return absoluteUrl.toString()
        }
    }

    // Las rutas del API son relativas al sitio de Gestión Docente. Una referencia de red
    // (`//otro-host`) no debe poder escapar de ese origen confiable.
    if (trimmed.startsWith("//")) return null
    val resolved = attachmentOrigin.resolve(trimmed) ?: return null
    return resolved
        .takeIf { it.host == attachmentOrigin.host && it.scheme == attachmentOrigin.scheme }
        ?.toString()
}

fun buildShareText(anuncio: Mensaje): String {
    val mensajePlano = htmlToShareText(anuncio.cuerpoHtml)
    val adjuntosPlano = anuncio.adjuntos.mapNotNull { adjunto ->
        val normalizedUri = normalizeAttachmentUri(adjunto.publicPath) ?: return@mapNotNull null
        "${adjunto.nombre} ($normalizedUri)"
    }

    val sections = mutableListOf(
        listOf(
            anuncio.titulo,
            anuncio.materia,
            anuncio.fecha,
            anuncio.autor
        ).joinToString("\n")
    )

    if (mensajePlano.isNotBlank()) {
        sections += mensajePlano
    }

    if (adjuntosPlano.isNotEmpty()) {
        sections += buildString {
            appendLine("Adjuntos:")
            adjuntosPlano.forEach { appendLine(it) }
        }.trimEnd()
    }

    return sections.joinToString("\n\n")
}
