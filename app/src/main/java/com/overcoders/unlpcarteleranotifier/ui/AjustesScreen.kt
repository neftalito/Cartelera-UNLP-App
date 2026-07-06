@file:Suppress("AssignedValueIsNeverRead")

package com.overcoders.unlpcarteleranotifier.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.overcoders.unlpcarteleranotifier.BuildConfig
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.R
import com.overcoders.unlpcarteleranotifier.data.SettingsStore
import com.overcoders.unlpcarteleranotifier.model.CarteleraNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.CursadaNotificationTarget
import com.overcoders.unlpcarteleranotifier.push.FirebaseClientConfig
import com.overcoders.unlpcarteleranotifier.push.FirebaseTopics
import com.overcoders.unlpcarteleranotifier.push.PushNotificationDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val bugReportUrl = "https://forms.gle/jLNMnBGWsdQHLM9N8"
private const val repositoryUrl = "https://github.com/neftalito/Cartelera-UNLP-App"
private const val cafecitoUrl = "https://cafecito.app/neftalito"

@SuppressLint("BatteryLife")
@Composable
fun AjustesScreen(
    highlightNotifyAllTrigger: Int = 0,
    onHighlightNotifyAllConsumed: () -> Unit = {},
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val powerManager = remember { context.getSystemService(PowerManager::class.java) }

    val notifyAll by SettingsStore.notifyAllFlow(context).collectAsState(initial = true)
    val hideCancelledMateriasMessages by SettingsStore.hideCancelledMateriasMessagesFlow(context)
        .collectAsState(initial = false)

    var isIgnoringBatteryOptimizations by remember { mutableStateOf(false) }
    var showNotifyAllHighlight by remember { mutableStateOf(false) }
    var areSettingsLoaded by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val firebaseConfigured = remember { FirebaseClientConfig.isConfigured() }
    val firebaseServerConfigured = remember { FirebaseClientConfig.isServerConfigured() }
    val notifyAllHighlightColor by animateColorAsState(
        targetValue = if (showNotifyAllHighlight) {
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.20f)
        } else {
            Color.Transparent
        },
        label = "notifyAllHighlightColor"
    )

    DisposableEffect(Unit) {
        onHeaderActionsChange(emptyList())
        onDispose { onHeaderActionsChange(emptyList()) }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            SettingsStore.notifyAllFlow(context).first()
            SettingsStore.hideCancelledMateriasMessagesFlow(context).first()
        }
        areSettingsLoaded = true
    }

    LaunchedEffect(highlightNotifyAllTrigger) {
        if (highlightNotifyAllTrigger > 0) {
            try {
                scrollState.animateScrollTo(0)
                showNotifyAllHighlight = true
                delay(2500)
            } finally {
                showNotifyAllHighlight = false
                onHighlightNotifyAllConsumed()
            }
        }
    }

    LaunchedEffect(powerManager) {
        if (powerManager != null) {
            isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(
                context.packageName
            )
        }
    }

    DisposableEffect(lifecycleOwner, powerManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && powerManager != null) {
                isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(
                    context.packageName
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!areSettingsLoaded) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Notificaciones push", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Ahora la app recibe avisos por Firebase Cloud Messaging. " +
                            "El servidor consulta cartelera y cursadas una sola vez, detecta cambios " +
                            "y los publica en el topico general o en el topico de cada materia.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (firebaseConfigured) {
                            "Firebase esta configurado en esta instalacion."
                        } else {
                            "Firebase todavia usa valores de ejemplo. Completa private-local.properties para activar el push real."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (firebaseConfigured) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    if (BuildConfig.DEBUG) {
                        Text(
                            text = "Topico general: ${FirebaseTopics.ALL_MATERIAS}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = if (firebaseServerConfigured) {
                                "Servidor: ${FirebaseClientConfig.serverBaseUrl}"
                            } else {
                                "Servidor: URL de ejemplo"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    HorizontalDivider()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                val horizontalInset = 8.dp.toPx()
                                val verticalInset = 4.dp.toPx()
                                val cornerRadius = 12.dp.toPx()
                                drawRoundRect(
                                    color = notifyAllHighlightColor,
                                    topLeft = Offset(-horizontalInset, -verticalInset),
                                    size = Size(
                                        width = size.width + (horizontalInset * 2f),
                                        height = size.height + (verticalInset * 2f)
                                    ),
                                    cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                                )
                            },
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Recibir notificaciones de todas las materias")
                        Switch(
                            checked = notifyAll,
                            onCheckedChange = { enabled ->
                                scope.launch(Dispatchers.IO) {
                                    SettingsStore.setNotifyAll(context, enabled)
                                }
                                if (!enabled) {
                                    showNotifyAllHighlight = false
                                }
                            },
                            colors = SwitchDefaults.colors()
                        )
                        Text(
                            text = "Si desactivas esta opcion, esta instalacion solo quedara suscripta a las materias elegidas en la pestana de subscripciones.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ocultar mensajes anulados en cartelera")
                        Switch(
                            checked = hideCancelledMateriasMessages,
                            onCheckedChange = { enabled ->
                                scope.launch(Dispatchers.IO) {
                                    SettingsStore.setHideCancelledMateriasMessages(context, enabled)
                                }
                            },
                            colors = SwitchDefaults.colors()
                        )
                        Text(
                            text = "Este filtro sigue afectando solo la visualizacion dentro de la app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Bateria")
                        val batteryText = if (isIgnoringBatteryOptimizations) {
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("La optimizacion de bateria ya esta desactivada para esta app.")
                                }
                                append("\n")
                                append(
                                    "Eso ayuda a que Android no limite la entrega de push o la apertura de la app en segundo plano."
                                )
                            }
                        } else {
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append("Conviene permitir uso sin restricciones.")
                                }
                                append("\n")
                                append(
                                    "En algunos equipos las notificaciones push llegan tarde si el sistema ahorra bateria agresivamente."
                                )
                            }
                        }
                        Text(
                            text = batteryText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val buttonLabel = if (isIgnoringBatteryOptimizations) {
                            "Abrir ajustes de bateria"
                        } else {
                            "Permitir uso sin restricciones"
                        }
                        Button(
                            onClick = {
                                val intent = if (isIgnoringBatteryOptimizations) {
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                } else {
                                    Intent(
                                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                                    ).apply {
                                        data = "package:${context.packageName}".toUri()
                                    }
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(buttonLabel)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (BuildConfig.DEBUG) {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Debug", style = MaterialTheme.typography.titleMedium)
                        Button(
                            onClick = {
                                PushNotificationDispatcher.showCarteleraNotification(
                                    context = context,
                                    target = CarteleraNotificationTarget(
                                        materiaId = "123",
                                        materia = "Materia de prueba",
                                        titulo = "Anuncio de ejemplo",
                                        fecha = "05/07/2026 21:00",
                                        autor = "Servidor Firebase",
                                        resumen = "Este push prueba el flujo nuevo de Firebase y la apertura dirigida hacia la cartelera.",
                                        isAnulado = false
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Probar push de cartelera")
                        }
                        Button(
                            onClick = {
                                PushNotificationDispatcher.showCursadaNotification(
                                    context = context,
                                    target = CursadaNotificationTarget(
                                        materiaId = "123",
                                        materia = "Materia de prueba",
                                        fechaModificacion = "05/07/2026 21:00"
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Probar push de cursada")
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Top
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledTonalIconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, bugReportUrl.toUri())
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Reportar errores o sugerir mejoras"
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Reportes",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledTonalIconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, repositoryUrl.toUri())
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_github),
                            contentDescription = "Abrir repositorio publico"
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "GitHub",
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledTonalIconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, cafecitoUrl.toUri())
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolunteerActivism,
                            contentDescription = "Apoyar el proyecto"
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Donaciones",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
        }

        ScrollMoreHint(
            scrollState = scrollState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}
