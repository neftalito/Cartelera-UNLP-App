package com.overcoders.unlpcarteleranotifier.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.overcoders.unlpcarteleranotifier.model.HorarioMateria
import com.overcoders.unlpcarteleranotifier.model.HorarioReserva

val diasHabiles = listOf(
    0 to "Lunes",
    1 to "Martes",
    2 to "Miércoles",
    3 to "Jueves",
    4 to "Viernes",
    5 to "Sábado"
)

fun buildShareText(horarioMateria: HorarioMateria): String {
    val reservas = horarioMateria.reservas
        .sortedWith(compareBy(HorarioReserva::dia, HorarioReserva::horaInicio, HorarioReserva::horaFin, HorarioReserva::aula))

    val lines = reservas.joinToString("\n") { reserva ->
        val dia = diasHabiles.firstOrNull { it.first == reserva.dia }?.second ?: "Día ${reserva.dia}"
        val estado = if (reserva.confirmada) "Confirmada" else "Sin confirmar"
        "• $dia ${reserva.horaInicio}-${reserva.horaFin} | Aula ${reserva.aula} | ${reserva.tipo} | $estado"
    }

    return buildString {
        appendLine("Reservas de aulas")
        appendLine(horarioMateria.materiaNombre)
        appendLine("Período: ${horarioMateria.periodo.nombre} (${horarioMateria.periodo.desde} - ${horarioMateria.periodo.hasta})")
        appendLine()
        append(lines)
    }.trim()
}

@Composable
fun HorarioDiaCard(
    diaNombre: String,
    reservasDelDia: List<HorarioReserva>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = diaNombre,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val cantidadReservas = reservasDelDia.size
                    val etiquetaReservas = if (cantidadReservas == 1) "reserva" else "reservas"
                    Text(
                        text = "$cantidadReservas $etiquetaReservas",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Contraer" else "Expandir",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isExpanded) {
                Spacer(Modifier.height(8.dp))
                reservasDelDia.forEach { reserva ->
                    ReservaItem(reserva)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
fun ReservaItem(reserva: HorarioReserva) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "${reserva.horaInicio} - ${reserva.horaFin}",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (reserva.confirmada) "Confirmada" else "Sin confirmar",
                    color = if (reserva.confirmada) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text("Aula: ${reserva.aula}", color = MaterialTheme.colorScheme.onSurface)
            Text("Tipo: ${reserva.tipo}", color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
