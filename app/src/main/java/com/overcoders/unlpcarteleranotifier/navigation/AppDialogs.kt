/**
 * Reúne los diálogos globales de avisos, suscripciones bloqueadas y reseña.
 */
package com.overcoders.unlpcarteleranotifier.navigation

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.overcoders.unlpcarteleranotifier.model.AvisoNotificationTarget

internal fun avisoDialogBody(target: AvisoNotificationTarget): String = buildList {
    add(target.mensaje)
    target.autor.takeIf(String::isNotBlank)?.let { add("Autor: $it") }
    add("Fecha: ${target.fecha}")
}.joinToString(separator = "\n\n")

/** Muestra el contenido completo de un aviso general abierto desde una notificación. */
@Composable
internal fun AvisoNotificationDialog(
    target: AvisoNotificationTarget,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(target.titulo) },
        text = { Text(avisoDialogBody(target)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
internal fun ReviewPromptDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Te gusta la app?") },
        text = {
            Text(
                "Si te resulta útil, podés dejar una reseña en Google Play para apoyar."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Dejar reseña")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

@Composable
internal fun SubscriptionsBlockedDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Suscripciones deshabilitadas") },
        text = {
            Text(
                "Activaste las notificaciones para todas las materias. " +
                    "Desactivá esa opción en Ajustes para gestionar tus suscripciones."
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text("Ir a Ajustes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}
