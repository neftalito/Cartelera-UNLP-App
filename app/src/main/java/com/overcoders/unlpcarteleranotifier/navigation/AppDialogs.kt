package com.overcoders.unlpcarteleranotifier

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
internal fun DevelopmentWarningDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Atención") },
        text = {
            Text(
                "Esta aplicación está en desarrollo, por lo que puede contener errores.\n" +
                    "Ante cualquier error, hay un botón de reportes debajo de todo en Ajustes para reportarlo.\n" +
                    "Es recomendable también desactivar las optimizaciones de batería, así la app puede funcionar en segundo plano y obtener correctamente las novedades."
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Entendido")
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
