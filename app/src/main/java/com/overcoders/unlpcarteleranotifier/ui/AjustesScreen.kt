@file:Suppress("AssignedValueIsNeverRead")

package com.overcoders.unlpcarteleranotifier.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
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
import com.overcoders.unlpcarteleranotifier.worker.NotificationDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class IntervalOption(val minutes: Int, val label: String)

private val intervalOptions = listOf(
    IntervalOption(15, "15 minutos"),
    IntervalOption(30, "30 minutos"),
    IntervalOption(60, "1 hora"),
    IntervalOption(720, "12 horas"),
    IntervalOption(1440, "24 horas")
)

private const val bugReportUrl = "https://forms.gle/jLNMnBGWsdQHLM9N8"
private const val repositoryUrl = "https://github.com/neftalito/Cartelera-UNLP-App"
private const val cafecitoUrl = "https://cafecito.app/neftalito"

/**
 * Pantalla de configuración operativa de la app.
 *
 * Reúne preferencias persistidas, atajos a permisos relevantes y pequeños estados de soporte
 * que impactan en el comportamiento en segundo plano.
 */
@SuppressLint("BatteryLife")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AjustesScreen(
    highlightNotifyAllTrigger: Int = 0,
    onHighlightNotifyAllConsumed: () -> Unit = {},
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val interval by SettingsStore.intervalFlow(context).collectAsState(initial = 30)
    val materiasAutoCheckEnabled by SettingsStore.materiasAutoCheckEnabledFlow(context)
        .collectAsState(initial = true)
    val notifyAll by SettingsStore.notifyAllFlow(context).collectAsState(initial = true)
    val wifiOnly by SettingsStore.wifiOnlyFlow(context).collectAsState(initial = false)
    val hideCancelledMateriasMessages by SettingsStore.hideCancelledMateriasMessagesFlow(context)
        .collectAsState(initial = false)
    val cursadasAutoCheckEnabled by SettingsStore.cursadasAutoCheckEnabledFlow(context)
        .collectAsState(initial = false)
    val cursadasInterval by SettingsStore.cursadasIntervalFlow(context).collectAsState(initial = 60)
    val powerManager = remember {
        context.getSystemService(PowerManager::class.java)
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    var intervalExpanded by remember { mutableStateOf(false) }
    var cursadasIntervalExpanded by remember { mutableStateOf(false) }
    var lastTotalText by remember { mutableStateOf("") }
    var lastSeenTotalText by remember { mutableStateOf("") }
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(false) }
    var showNotifyAllHighlight by remember { mutableStateOf(false) }
    var areSettingsLoaded by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
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

    // Esperamos a tener un snapshot consistente de DataStore antes de pintar la UI completa.
    // Así evitamos mostrar defaults transitorios y que luego "salten" al valor real guardado.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            SettingsStore.intervalFlow(context).first()
            SettingsStore.materiasAutoCheckEnabledFlow(context).first()
            SettingsStore.notifyAllFlow(context).first()
            SettingsStore.wifiOnlyFlow(context).first()
            SettingsStore.hideCancelledMateriasMessagesFlow(context).first()
            SettingsStore.cursadasAutoCheckEnabledFlow(context).first()
            SettingsStore.cursadasIntervalFlow(context).first()
        }

        areSettingsLoaded = true

        val lastTotal = withContext(Dispatchers.IO) {
            SettingsStore.getLastTotal(context)
        }
        lastTotalText = lastTotal.toString()
        val lastSeenTotal = withContext(Dispatchers.IO) {
            SettingsStore.getLastSeenTotal(context)
        }
        lastSeenTotalText = lastSeenTotal.toString()
    }

    // Este disparador se usa cuando otra pantalla redirige al usuario a la opción "notify all"
    // y necesita que quede visualmente resaltada al llegar aquí.
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

    // El usuario puede cambiar esta excepción desde ajustes del sistema; la refrescamos al volver
    // a la app para reflejar el estado real sin requerir un reinicio manual.
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
                    Text("Notificaciones", style = MaterialTheme.typography.titleMedium)

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
                            text = "Si desactivás esta opción, sólo vas a recibir notificaciones de las materias a las que estás suscrito.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Chequeo automático de cartelera")
                        Switch(
                            checked = materiasAutoCheckEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch(Dispatchers.IO) {
                                    SettingsStore.setMateriasAutoCheckEnabled(context, enabled)
                                }
                            },
                            colors = SwitchDefaults.colors()
                        )
                    }

                    if (materiasAutoCheckEnabled) {
                        ExposedDropdownMenuBox(
                            expanded = intervalExpanded,
                            onExpandedChange = { intervalExpanded = !intervalExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val selectedLabel = intervalOptions
                                .firstOrNull { it.minutes == interval }
                                ?.label
                                ?: "$interval minutos"
                            OutlinedTextField(
                                value = selectedLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Intervalo para la cartelera") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor(
                                        ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                        enabled = true
                                    )
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = intervalExpanded,
                                onDismissRequest = { intervalExpanded = false }
                            ) {
                                intervalOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            intervalExpanded = false
                                            if (option.minutes != interval) {
                                                scope.launch(Dispatchers.IO) {
                                                    SettingsStore.setInterval(context, option.minutes)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Nota: a menor intervalo, mayor es el consumo de batería y datos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    HorizontalDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Chequeo automático de cursadas")
                        Switch(
                            checked = cursadasAutoCheckEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch(Dispatchers.IO) {
                                    SettingsStore.setCursadasAutoCheckEnabled(context, enabled)
                                }
                            },
                            colors = SwitchDefaults.colors()
                        )
                    }

                    if (cursadasAutoCheckEnabled) {
                        ExposedDropdownMenuBox(
                            expanded = cursadasIntervalExpanded,
                            onExpandedChange = { cursadasIntervalExpanded = !cursadasIntervalExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val selectedLabel = intervalOptions
                                .firstOrNull { it.minutes == cursadasInterval }
                                ?.label
                                ?: "$cursadasInterval minutos"
                            OutlinedTextField(
                                value = selectedLabel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Intervalo para las cursadas") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = cursadasIntervalExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor(
                                        ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                        enabled = true
                                    )
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = cursadasIntervalExpanded,
                                onDismissRequest = { cursadasIntervalExpanded = false }
                            ) {
                                intervalOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            cursadasIntervalExpanded = false
                                            if (option.minutes != cursadasInterval) {
                                                scope.launch(Dispatchers.IO) {
                                                    SettingsStore.setCursadasInterval(context, option.minutes)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Nota: a menor intervalo, mayor es el consumo de batería y datos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    HorizontalDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ocultar mensajes anulados en cartelera")
                        // Con este filtro puede aparecer el badge "Nuevo" en anuncios que no serían nuevos
                        // Como es un problema meramente visual y de bajo impacto, no planeo arreglarlo.
                        Switch(
                            checked = hideCancelledMateriasMessages,
                            onCheckedChange = { enabled ->
                                scope.launch(Dispatchers.IO) {
                                    SettingsStore.setHideCancelledMateriasMessages(context, enabled)
                                }
                            },
                            colors = SwitchDefaults.colors()
                        )
                    }

                    HorizontalDivider()


                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ahorro de datos (solo Wi-Fi)")
                        Switch(
                            checked = wifiOnly,
                            onCheckedChange = { enabled ->
                                scope.launch(Dispatchers.IO) {
                                    SettingsStore.setWifiOnly(context, enabled)
                                }
                            },
                            colors = SwitchDefaults.colors()
                        )
                        Text(
                            text = "Si está activado, la app solo consultará novedades y enviará notificaciones cuando estés conectado a Wi-Fi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Batería")
                        if (powerManager != null) {
                            val buttonLabel = if (isIgnoringBatteryOptimizations) {
                                "Abrir ajustes de batería"
                            } else {
                                "Permitir uso sin restricciones"
                            }
                            Text(
                                text = if (isIgnoringBatteryOptimizations) {
                                    buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append("La optimización de batería está desactivada para esta app.")
                                        }
                                        append("\n")
                                        append(
                                            "Esto es útil para evitar que el sistema cierre el proceso en segundo plano. Si querés volver a activarla, tocá el botón para abrir los ajustes del sistema."
                                        )
                                    }
                                } else {
                                    buildAnnotatedString {
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append(
                                                "Para evitar que el sistema cierre el proceso en segundo plano, habilitá el uso sin restricciones."
                                            )
                                        }
                                        append("\n")
                                        append(
                                            "Si las notificaciones no llegan cuando la app está en segundo plano o cerrada y sólo llegan al abrir la app, activá esta opción."
                                        )
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                        } else {
                            Text(
                                text = "Esta versión de Android no requiere ajustes extra de batería.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                                    )
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Abrir ajustes de batería")
                            }
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
                        Text("Total anterior")
                        OutlinedTextField(
                            value = lastTotalText,
                            onValueChange = { lastTotalText = it },
                            label = { Text("Último total") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    val parsed = lastTotalText.toIntOrNull()
                                    if (parsed == null) {
                                        Toast.makeText(
                                            context,
                                            "Ingresá un número válido.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@launch
                                    }
                                    withContext(Dispatchers.IO) {
                                        SettingsStore.setLastTotal(context, parsed)
                                    }
                                    Toast.makeText(
                                        context,
                                        "Total anterior guardado.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Guardar total anterior")
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    val parsed = lastTotalText.toIntOrNull()
                                    if (parsed == null) {
                                        Toast.makeText(
                                            context,
                                            "Ingresá un número válido.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@launch
                                    }
                                    withContext(Dispatchers.IO) {
                                        SettingsStore.setLastTotal(context, parsed)
                                        NotificationDispatcher.process(context)
                                    }
                                    Toast.makeText(
                                        context,
                                        "Notificaciones encoladas para debug.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Probar notificaciones")
                        }
                        HorizontalDivider()
                        Text("Nuevos anuncios")
                        OutlinedTextField(
                            value = lastSeenTotalText,
                            onValueChange = { lastSeenTotalText = it },
                            label = { Text("Último total visto") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    val parsed = lastSeenTotalText.toIntOrNull()
                                    if (parsed == null) {
                                        Toast.makeText(
                                            context,
                                            "Ingresá un número válido.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@launch
                                    }
                                    withContext(Dispatchers.IO) {
                                        SettingsStore.setLastSeenTotal(context, parsed)
                                    }
                                    Toast.makeText(
                                        context,
                                        "Valores de vistos guardados.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Guardar vistos")
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
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                bugReportUrl.toUri()
                            )
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
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                repositoryUrl.toUri()
                            )
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
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                cafecitoUrl.toUri()
                            )
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
