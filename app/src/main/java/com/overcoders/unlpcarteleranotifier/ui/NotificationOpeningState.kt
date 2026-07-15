/** Presenta el estado transitorio mientras se resuelve una notificación. */
package com.overcoders.unlpcarteleranotifier.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.overcoders.unlpcarteleranotifier.ui.common.CenteredLoadingState

@Composable
internal fun NotificationOpeningState(
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (errorMessage == null) {
            CenteredLoadingState(
                text = "Abriendo la notificación",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                if (onRetry != null) {
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onRetry) {
                        Text("Reintentar")
                    }
                }
                if (onCancel != null) {
                    TextButton(onClick = onCancel) {
                        Text("Ver cartelera")
                    }
                }
            }
        }
    }
}
