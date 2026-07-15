/**
 * Permite administrar las materias suscriptas y validar sus identificadores contra el catálogo.
 */
package com.overcoders.unlpcarteleranotifier.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.overcoders.unlpcarteleranotifier.BuildConfig
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.data.MateriasRepository
import com.overcoders.unlpcarteleranotifier.data.SettingsStore
import com.overcoders.unlpcarteleranotifier.data.SubscripcionesStore
import com.overcoders.unlpcarteleranotifier.ui.common.LoadingContentBox
import com.overcoders.unlpcarteleranotifier.ui.common.cachedContentWarning
import com.overcoders.unlpcarteleranotifier.ui.common.contentLoadingPhase
import com.overcoders.unlpcarteleranotifier.ui.common.normalizeForSearch
import com.overcoders.unlpcarteleranotifier.ui.common.userFacingError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscripcionesScreen(
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val materiasRepository = remember(context) { MateriasRepository.get(context) }

    val materias by materiasRepository.items.collectAsStateWithLifecycle()
    var loading by remember { mutableStateOf(true) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var catalogWarning by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }

    var expanded by remember { mutableStateOf(false) }
    var materiasSearchQuery by remember { mutableStateOf("") }
    suspend fun loadMateriasCatalog(forceRefresh: Boolean) {
        loading = true
        catalogError = null
        catalogWarning = null
        actionError = null
        try {
            val result = materiasRepository.load(forceRefresh)
            val failure = result.refreshFailure
            if (failure != null && result.items.isNotEmpty()) {
                catalogWarning = cachedContentWarning(
                    operation = "actualizar el catálogo de materias de las suscripciones",
                    error = failure,
                )
            } else if (failure != null) {
                catalogError = userFacingError(
                    operation = "cargar el catálogo de materias de las suscripciones",
                    error = failure,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            loading = false
        }
    }

    fun refreshMaterias() {
        scope.launch {
            loadMateriasCatalog(forceRefresh = true)
        }
    }

    val subscriptionsFlow = remember(context) { SubscripcionesStore.subscripcionesFlow(context) }
    val notifyAllFlow = remember(context) {
        SettingsStore.notifyAllFlow(context).map<Boolean, Boolean?> { it }
    }
    val subscriptasIds by subscriptionsFlow
        .collectAsStateWithLifecycle(initialValue = emptySet())
    val notifyAllPreference by notifyAllFlow.collectAsStateWithLifecycle(initialValue = null)
    val isEnabled = notifyAllPreference == false

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
        loadMateriasCatalog(forceRefresh = false)
    }

    val materiasFiltradas = remember(materias, materiasSearchQuery, effectiveSubscripcionesIds) {
        val noSubscriptas = materias.filterNot { effectiveSubscripcionesIds.contains(it.id) }
        if (materiasSearchQuery.isBlank()) {
            noSubscriptas
        } else {
            val normalizedQuery = materiasSearchQuery.normalizeForSearch()
            noSubscriptas.filter { it.nombre.normalizeForSearch().contains(normalizedQuery) }
        }
    }

    val subscriptas = remember(materias, effectiveSubscripcionesIds) {
        buildSubscriptionEntries(materias, effectiveSubscripcionesIds)
    }

    LaunchedEffect(subscriptasIds) {
        pendingSubscriptions = pendingSubscriptions.filterNot { subscriptasIds.contains(it) }.toSet()
    }

    LoadingContentBox(
        phase = contentLoadingPhase(
            isLoading = loading,
            hasResolvedContent = materias.isNotEmpty(),
        ),
        initialText = "Cargando suscripciones…",
        refreshContentDescription = "Actualizando materias de suscripciones",
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (notifyAllPreference == true) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Activaste las notificaciones para todas las materias. " +
                        "Desactivá esa opción en ajustes para volver a gestionar tus suscripciones.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
            }

            val visibleFeedback = catalogError ?: actionError ?: catalogWarning
            if (visibleFeedback != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    visibleFeedback,
                    color = if (catalogError == null && actionError == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                if (catalogError != null || (actionError == null && catalogWarning != null)) {
                    TextButton(
                        onClick = { refreshMaterias() },
                        enabled = !loading,
                    ) {
                        Text("Reintentar")
                    }
                }
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
                            text = { Text("Cargando materias…") },
                            onClick = { expanded = false },
                            enabled = false
                        )
                    } else if (catalogError != null && materias.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Catálogo no disponible") },
                            onClick = { expanded = false },
                            enabled = false,
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
                                        actionError = null
                                        pendingSubscriptions = pendingSubscriptions + m.id
                                        materiasSearchQuery = ""
                                        expanded = false
                                        focusManager.clearFocus(force = true)
                                        keyboardController?.hide()
                                        scope.launch {
                                            try {
                                                SubscripcionesStore.subscribe(context, m.id)
                                            } catch (e: CancellationException) {
                                                pendingSubscriptions = pendingSubscriptions - m.id
                                                throw e
                                            } catch (e: Exception) {
                                                pendingSubscriptions = pendingSubscriptions - m.id
                                                actionError = userFacingError(
                                                    operation = "guardar una suscripción",
                                                    error = e,
                                                )
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
            Surface(
                modifier = Modifier.animateContentSize(),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
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
            }
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
                                color = if (m.status == SubscriptionStatus.INVALID) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    LocalContentColor.current
                                }
                            )
                            when (m.status) {
                                SubscriptionStatus.INVALID -> Text(
                                    "Esta materia cambió o ya no existe. Renová tus suscripciones.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )

                                SubscriptionStatus.UNVERIFIED -> Text(
                                    "No se pudo verificar la materia porque el catálogo no está disponible.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                SubscriptionStatus.VALID -> Unit
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
                                    actionError = null
                                    pendingSubscriptions = pendingSubscriptions - m.id
                                    scope.launch {
                                        try {
                                            SubscripcionesStore.unsubscribe(context, m.id)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            actionError = userFacingError(
                                                operation = "eliminar una suscripción",
                                                error = e,
                                            )
                                        }
                                    }
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
}
