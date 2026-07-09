package com.overcoders.unlpcarteleranotifier.ui

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.overcoders.unlpcarteleranotifier.model.Mensaje

@Composable
fun AnuncioDetailScreen(
    anuncio: Mensaje,
    onBack: () -> Unit
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
                        Text(
                            text = adjunto.nombre,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val normalizedUri = normalizeAttachmentUri(adjunto.publicPath)
                                    if (normalizedUri == null) {
                                        Toast.makeText(
                                            context,
                                            "No se pudo abrir el adjunto",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@clickable
                                    }

                                    runCatching { uriHandler.openUri(normalizedUri) }
                                        .onFailure {
                                            Toast.makeText(
                                                context,
                                                "No se pudo abrir el adjunto",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                }
                                .padding(vertical = 4.dp)
                        )
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

@SuppressLint("UseKtx")
fun normalizeAttachmentUri(rawUri: String): String? {
    val trimmed = rawUri.trim()
    if (trimmed.isEmpty()) return null

    val parsedUri = trimmed.toUri()
    val scheme = parsedUri.scheme?.lowercase()
    if (scheme == "http" || scheme == "https") {
        return parsedUri.toString()
    }

    return null
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
