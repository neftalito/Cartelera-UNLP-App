package com.overcoders.unlpcarteleranotifier.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.overcoders.unlpcarteleranotifier.BuildConfig
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.data.MateriasService
import com.overcoders.unlpcarteleranotifier.data.MateriasStore
import com.overcoders.unlpcarteleranotifier.data.SettingsStore
import com.overcoders.unlpcarteleranotifier.data.SubscripcionesStore
import com.overcoders.unlpcarteleranotifier.model.MateriaCatalogItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SubscriptionEntry(
    val id: String,
    val nombre: String,
    val invalid: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscripcionesScreen(
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val materiasService = remember { MateriasService() }

    var materias by remember { mutableStateOf<List<MateriaCatalogItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var expanded by remember { mutableStateOf(false) }
    var materiasSearchQuery by remember { mutableStateOf("") }

    fun refreshMaterias() {
        scope.launch {
            loading = true
            error = null
            try {
                val refreshed = withContext(Dispatchers.IO) {
                    materiasService.refresh(context)
                }
                if (refreshed.isNotEmpty()) {
                    materias = refreshed
                }
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    val subscriptasIds by SubscripcionesStore
        .subscripcionesFlow(context)
        .collectAsState(initial = emptySet())

    val notifyAll by SettingsStore.notifyAllFlow(context).collectAsState(initial = true)
    val isEnabled = !notifyAll

    var pendingSubscriptions by remember { mutableStateOf(emptySet<String>()) }
    val effectiveSubscripcionesIds = remember(subscriptasIds, pendingSubscriptions) {
        subscriptasIds + pendingSubscriptions
    }

    LaunchedEffect(isEnabled, loading) {
        onHeaderActionsChange(
            listOf(
                HeaderAction(
                    icon = Icons.Default.Refresh,
                    contentDescription = "Refrescar materias",
                    enabled = isEnabled && !loading,
                    onClick = { refreshMaterias() }
                )
            )
        )
    }

    DisposableEffect(Unit) {
        onDispose { onHeaderActionsChange(emptyList()) }
    }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        try {
            val cached = withContext(Dispatchers.IO) { MateriasStore.load(context) }
            materias = cached
            if (cached.isEmpty()) {
                val fetched = withContext(Dispatchers.IO) { materiasService.refresh(context) }
                if (fetched.isNotEmpty()) {
                    materias = fetched
                }
            }
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    val materiasFiltradas = remember(materias, materiasSearchQuery, effectiveSubscripcionesIds) {
        val noSubscriptas = materias.filterNot { effectiveSubscripcionesIds.contains(it.id) }
        if (materiasSearchQuery.isBlank()) {
            noSubscriptas
        } else {
            noSubscriptas.filter { it.nombre.contains(materiasSearchQuery, ignoreCase = true) }
        }
    }

    val subscriptas = remember(materias, effectiveSubscripcionesIds) {
        val materiasById = materias.associateBy { it.id }

        effectiveSubscripcionesIds
            .map { id ->
                val materia = materiasById[id]
                if (materia != null) {
                    SubscriptionEntry(
                        id = materia.id,
                        nombre = materia.nombre,
                        invalid = false
                    )
                } else {
                    SubscriptionEntry(
                        id = id,
                        nombre = "Suscripción inválida (materia no encontrada)",
                        invalid = true
                    )
                }
            }
            .sortedWith(
                compareBy<SubscriptionEntry> { it.invalid }
                    .thenBy { it.nombre.lowercase() }
            )
    }

    LaunchedEffect(subscriptasIds) {
        pendingSubscriptions = pendingSubscriptions.filterNot { subscriptasIds.contains(it) }.toSet()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (!isEnabled) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Activaste las notificaciones para todas las materias. " +
                        "Desactivá esa opción en ajustes para volver a gestionar tus suscripciones.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
        }

        if (loading) {
            Column {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "Refrescando la lista de materias...",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text("Error: $error", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(12.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { isExpanded ->
                if (isEnabled) {
                    if (isExpanded) {
                        materiasSearchQuery = ""
                    }
                    expanded = isExpanded
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = materiasSearchQuery,
                onValueChange = { value ->
                    if (isEnabled) {
                        materiasSearchQuery = value
                        if (!expanded) {
                            expanded = true
                        }
                    }
                },
                label = { Text("Seleccioná una materia…") },
                placeholder = { Text("Seleccioná una materia…") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = isEnabled)
                    .fillMaxWidth()
                    .then(if (isEnabled) Modifier else Modifier.alpha(0.6f)),
                enabled = isEnabled
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (loading && materias.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Cargando materias...") },
                            onClick = { expanded = false },
                            enabled = false
                        )
                    } else if (materiasFiltradas.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Sin resultados") },
                            onClick = { expanded = false },
                            enabled = false
                        )
                    } else {
                        materiasFiltradas.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.nombre) },
                                onClick = {
                                    if (isEnabled) {
                                        pendingSubscriptions = pendingSubscriptions + m.id
                                        materiasSearchQuery = ""
                                        expanded = false
                                        focusManager.clearFocus(force = true)
                                        keyboardController?.hide()
                                        scope.launch {
                                            try {
                                                SubscripcionesStore.subscribe(context, m.id)
                                            } catch (e: Exception) {
                                                pendingSubscriptions = pendingSubscriptions - m.id
                                                error = e.message
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Materias suscriptas", style = MaterialTheme.typography.titleMedium)
            AssistChip(
                onClick = {},
                modifier = Modifier.animateContentSize(),
                label = {
                    AnimatedContent(
                        targetState = subscriptas.size,
                        transitionSpec = {
                            if (targetState > initialState) {
                                slideInVertically { it / 2 } + fadeIn() togetherWith
                                        slideOutVertically { -it / 2 } + fadeOut()
                            } else {
                                slideInVertically { -it / 2 } + fadeIn() togetherWith
                                        slideOutVertically { it / 2 } + fadeOut()
                            }
                        },
                        label = "subscriptions-counter-animation"
                    ) { total ->
                        Text("$total")
                    }
                }
            )
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (subscriptas.isEmpty()) {
                item(key = "empty-subscripciones-placeholder") {
                    Text(
                        "Todavía no estás suscrito a ninguna materia.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.animateItem()
                    )
                }
            }

            items(subscriptas, key = { it.id }) { m ->
                Card(
                    modifier = Modifier.animateItem()
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                m.nombre,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (m.invalid) MaterialTheme.colorScheme.error else LocalContentColor.current
                            )
                            if (m.invalid) {
                                Text(
                                    "Esta materia cambió o ya no existe. Renová tus suscripciones.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (BuildConfig.DEBUG) {
                                Text(
                                    "idMateria=${m.id}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                if (isEnabled) {
                                    pendingSubscriptions = pendingSubscriptions - m.id
                                    scope.launch { SubscripcionesStore.unsubscribe(context, m.id) }
                                }
                            },
                            enabled = isEnabled
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Desuscribir")
                        }
                    }
                }
            }
        }
    }
}
