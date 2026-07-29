/** Renderiza el selector, el feedback y la lista de materias suscriptas. */
package com.overcoders.unlpcarteleranotifier.ui.subscripciones

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.overcoders.unlpcarteleranotifier.BuildConfig
import com.overcoders.unlpcarteleranotifier.model.MateriaCatalogItem
import com.overcoders.unlpcarteleranotifier.ui.common.LoadingContentBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubscripcionesContent(
    state: SubscripcionesUiState,
    onExpandedChange: (Boolean) -> Unit,
    onDismissSelector: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSubscribe: (MateriaCatalogItem) -> Unit,
    onUnsubscribe: (SubscriptionEntry) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LoadingContentBox(
        phase = state.loadingPhase,
        initialText = "Cargando suscripciones…",
        refreshContentDescription = "Actualizando materias de suscripciones",
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            if (state.notifyAll) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Activaste las notificaciones para todas las materias. " +
                        "Desactivá esa opción en ajustes para volver a gestionar tus suscripciones.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
            }

            state.feedback?.let { feedback ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = feedback,
                    color = if (state.feedbackIsWarning) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }

            Spacer(Modifier.height(12.dp))

            ExposedDropdownMenuBox(
                expanded = state.expanded,
                onExpandedChange = onExpandedChange,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = { Text("Seleccioná una materia…") },
                    placeholder = { Text("Seleccioná una materia…") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = state.expanded)
                    },
                    modifier = Modifier
                        .menuAnchor(
                            ExposedDropdownMenuAnchorType.PrimaryEditable,
                            enabled = state.enabled,
                        )
                        .fillMaxWidth()
                        .then(if (state.enabled) Modifier else Modifier.alpha(0.6f)),
                    enabled = state.enabled,
                )

                ExposedDropdownMenu(
                    expanded = state.expanded,
                    onDismissRequest = onDismissSelector,
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        when {
                            state.loading && state.catalogEmpty -> DropdownMenuItem(
                                text = { Text("Cargando materias…") },
                                onClick = onDismissSelector,
                                enabled = false,
                            )

                            state.catalogUnavailable -> DropdownMenuItem(
                                text = { Text("Catálogo no disponible") },
                                onClick = onDismissSelector,
                                enabled = false,
                            )

                            state.availableMaterias.isEmpty() -> DropdownMenuItem(
                                text = { Text("Sin resultados") },
                                onClick = onDismissSelector,
                                enabled = false,
                            )

                            else -> state.availableMaterias.forEach { materia ->
                                DropdownMenuItem(
                                    text = { Text(materia.nombre) },
                                    onClick = {
                                        onSubscribe(materia)
                                        focusManager.clearFocus(force = true)
                                        keyboardController?.hide()
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SubscriptionsHeader(total = state.subscriptions.size)
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.subscriptions.isEmpty()) {
                    item(key = "empty-subscripciones-placeholder") {
                        Text(
                            "Todavía no estás suscrito a ninguna materia.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                items(state.subscriptions, key = SubscriptionEntry::id) { subscription ->
                    SubscriptionCard(
                        subscription = subscription,
                        enabled = state.enabled,
                        onUnsubscribe = { onUnsubscribe(subscription) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionsHeader(total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Materias suscriptas", style = MaterialTheme.typography.titleMedium)
        Surface(
            modifier = Modifier.animateContentSize(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                AnimatedContent(
                    targetState = total,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInVertically { it / 2 } + fadeIn() togetherWith
                                slideOutVertically { -it / 2 } + fadeOut()
                        } else {
                            slideInVertically { -it / 2 } + fadeIn() togetherWith
                                slideOutVertically { it / 2 } + fadeOut()
                        }
                    },
                    label = "subscriptions-counter-animation",
                ) { animatedTotal ->
                    Text("$animatedTotal")
                }
            }
        }
    }
}

@Composable
private fun SubscriptionCard(
    subscription: SubscriptionEntry,
    enabled: Boolean,
    onUnsubscribe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = subscription.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (subscription.status == SubscriptionStatus.INVALID) {
                        MaterialTheme.colorScheme.error
                    } else {
                        LocalContentColor.current
                    },
                )
                when (subscription.status) {
                    SubscriptionStatus.INVALID -> Text(
                        "Esta materia cambió o ya no existe. Renová tus suscripciones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )

                    SubscriptionStatus.UNVERIFIED -> Text(
                        "No se pudo verificar la materia porque el catálogo no está disponible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    SubscriptionStatus.VALID -> Unit
                }
                if (BuildConfig.DEBUG) {
                    Text(
                        "idMateria=${subscription.id}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            IconButton(
                onClick = onUnsubscribe,
                enabled = enabled,
            ) {
                Icon(Icons.Default.Close, contentDescription = "Desuscribir")
            }
        }
    }
}
