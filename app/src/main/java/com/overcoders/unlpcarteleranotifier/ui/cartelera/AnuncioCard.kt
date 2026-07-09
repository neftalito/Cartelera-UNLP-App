package com.overcoders.unlpcarteleranotifier.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import com.overcoders.unlpcarteleranotifier.model.Mensaje

@Composable
fun AnuncioCard(
    anuncio: Mensaje,
    isNew: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isNew) 1f else 0.6f)
            .clickable { onClick() }
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        anuncio.titulo,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (anuncio.adjuntos.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Tiene adjuntos",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (isNew) {
                    Text(
                        text = "Nuevo",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(anuncio.materia, style = MaterialTheme.typography.bodySmall)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(anuncio.fecha, style = MaterialTheme.typography.labelSmall)
                Text(anuncio.autor, style = MaterialTheme.typography.labelSmall)
            }

            if (anuncio.isAnulado) {
                Text("ANULADO", color = MaterialTheme.colorScheme.error)
            }

            val preview = remember(anuncio.cuerpoHtml) {
                HtmlCompat.fromHtml(
                    anuncio.cuerpoHtml,
                    HtmlCompat.FROM_HTML_MODE_LEGACY
                ).toString()
                    .replace("\n", " ")
                    .replace("\r", " ")
                    .take(180) + if (anuncio.cuerpoHtml.length > 180) "…" else ""
            }
            Text(preview, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
