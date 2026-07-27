/** Presenta la ocupación actual de aulas sin administrar red, caché ni efectos externos. */
package com.overcoders.unlpcarteleranotifier.ui.aulas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.overcoders.unlpcarteleranotifier.model.AulaEstado
import com.overcoders.unlpcarteleranotifier.ui.ScrollMoreHint
import com.overcoders.unlpcarteleranotifier.ui.common.ContentLoadingPhase
import com.overcoders.unlpcarteleranotifier.ui.common.LoadingContentBox

internal data class AulasContentState(
    val loadingPhase: ContentLoadingPhase,
    val warning: String?,
    val error: String?,
    val aulas: List<AulaEstado>,
)

@Composable
internal fun AulasContent(
    state: AulasContentState,
    listState: LazyListState,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        state.warning?.let { warning ->
            Text(
                text = warning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }

        LoadingContentBox(
            phase = state.loadingPhase,
            initialText = "Cargando estado actual de las aulas…",
            refreshContentDescription = "Actualizando estado de aulas",
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.error != null -> CenteredAulasMessage(
                    text = state.error,
                    isError = true,
                )

                state.aulas.isEmpty() -> CenteredAulasMessage(
                    text = "No hay aulas ocupadas en este momento.",
                    isError = false,
                )

                else -> Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.aulas) { aula ->
                            AulaEstadoCard(aula)
                        }
                    }

                    ScrollMoreHint(
                        listState = listState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredAulasMessage(
    text: String,
    isError: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = if (isError) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.bodyLarge
            },
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun AulaEstadoCard(aula: AulaEstado) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = aulaDisplayName(aula),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = aula.materia,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${aula.horaDesde} a ${aula.horaHasta}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

internal fun buildAulasShareText(aulas: List<AulaEstado>): String {
    if (aulas.isEmpty()) {
        return "Estado actual de aulas\n\nNo hay aulas ocupadas en este momento."
    }

    val lines = aulas
        .sortedWith(compareBy(AulaEstado::aulaNombre, AulaEstado::aulaId, AulaEstado::horaDesde))
        .map { aula ->
            "• ${aulaDisplayName(aula)}: ${aula.materia} " +
                "(${aula.horaDesde} a ${aula.horaHasta})"
        }

    return buildString {
        appendLine("Estado actual de aulas")
        appendLine()
        append(lines.joinToString("\n"))
    }.trim()
}

internal fun aulaDisplayName(aula: AulaEstado): String =
    listOf(aula.aulaNombre, aula.aulaId)
        .filter(String::isNotBlank)
        .joinToString(" - ")
