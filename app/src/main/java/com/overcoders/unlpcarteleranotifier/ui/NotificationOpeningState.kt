/** Presenta el estado transitorio mientras se resuelve una notificación. */
package com.overcoders.unlpcarteleranotifier.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.overcoders.unlpcarteleranotifier.ui.common.CenteredLoadingState
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun NotificationOpeningState(
    errorMessage: String? = null,
    openingKey: Any? = Unit,
    onCancel: () -> Unit,
) {
    var showCancel by remember(openingKey) { mutableStateOf(false) }

    LaunchedEffect(openingKey, errorMessage) {
        showCancel = false
        if (errorMessage == null) {
            delay(NOTIFICATION_OPEN_CANCEL_DELAY_MILLIS.milliseconds)
            showCancel = true
        }
    }

    BackHandler(onBack = onCancel)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (errorMessage == null) {
            CenteredLoadingState(
                text = "Abriendo la notificación",
                modifier = Modifier.fillMaxSize(),
            ) {
                if (showCancel) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onCancel) {
                        Text("Cancelar")
                    }
                }
            }
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
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onCancel) {
                    Text("Ver cartelera")
                }
            }
        }
    }
}

internal const val NOTIFICATION_OPEN_CANCEL_DELAY_MILLIS = 5_000L
