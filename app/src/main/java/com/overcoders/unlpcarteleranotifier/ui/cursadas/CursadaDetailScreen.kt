package com.overcoders.unlpcarteleranotifier.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.overcoders.unlpcarteleranotifier.model.CursadaInfo

@Composable
fun CursadaDetailScreen(
    cursada: CursadaInfo,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val detailScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(cursada.materia, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Última actualización: ${cursada.ultimaModificacion.ifBlank { "Sin fecha" }}",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(detailScrollState)
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Inicio de cursada", style = MaterialTheme.typography.titleMedium)
                HtmlTextBlock(
                    html = cursada.inicioCursadaHtml,
                    context = context
                )
                HorizontalDivider()
                Text("Horarios de cursada", style = MaterialTheme.typography.titleMedium)
                HtmlTextBlock(
                    html = cursada.horariosCursadaHtml,
                    context = context
                )
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

fun buildShareText(cursada: CursadaInfo): String {
    val inicioCursadaPlano = htmlToShareText(cursada.inicioCursadaHtml)
    val horariosCursadaPlano = htmlToShareText(cursada.horariosCursadaHtml)

    return buildString {
        appendLine(cursada.materia)
        appendLine("Última actualización: ${cursada.ultimaModificacion.ifBlank { "Sin fecha" }}")
        appendLine()
        appendLine("Inicio de cursada")
        appendLine(inicioCursadaPlano.ifBlank { "Sin información" })
        appendLine()
        appendLine("Horarios de cursada")
        append(horariosCursadaPlano.ifBlank { "Sin información" })
    }
}
