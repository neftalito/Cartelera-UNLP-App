/** Presenta el filtro y el listado de cursadas sin administrar cargas ni efectos externos. */
package com.overcoders.unlpcarteleranotifier.ui.cursadas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.overcoders.unlpcarteleranotifier.model.CursadaInfo
import com.overcoders.unlpcarteleranotifier.model.cursadaMateriaKey
import com.overcoders.unlpcarteleranotifier.ui.ScrollMoreHint
import com.overcoders.unlpcarteleranotifier.ui.common.LoadingContentBox
import com.overcoders.unlpcarteleranotifier.ui.common.contentLoadingPhase
import com.overcoders.unlpcarteleranotifier.ui.common.normalizeForSearch

internal data class CursadasScreenContentState(
    val cursadas: List<CursadaInfo>,
    val cursadasConNovedades: Set<String>,
    val loading: Boolean,
    val hasResolvedSnapshot: Boolean,
    val initialLoadCompleted: Boolean,
    val loadError: String?,
    val notificationOpenError: String?,
    val actionError: String?,
    val warning: String?,
)

internal data class CursadasFeedback(
    val message: String,
    val isWarning: Boolean,
)

internal fun cursadasFeedback(state: CursadasScreenContentState): CursadasFeedback? {
    val message = state.loadError
        ?: state.notificationOpenError
        ?: state.actionError
        ?: state.warning
        ?: return null
    return CursadasFeedback(
        message = message,
        isWarning = state.loadError == null &&
            state.notificationOpenError == null &&
            state.actionError == null,
    )
}

internal fun filteredCursadas(
    cursadas: List<CursadaInfo>,
    filter: String,
): List<CursadaInfo> {
    val query = filter.normalizeForSearch()
    val matchingCursadas = if (query.isBlank()) {
        cursadas
    } else {
        cursadas.filter { cursada ->
            cursada.materia.normalizeForSearch().contains(query)
        }
    }
    return matchingCursadas.sortedWith(
        compareByDescending<CursadaInfo> {
            it.ultimaModificacionEpochMillis ?: Long.MIN_VALUE
        }.thenBy { cursada -> cursada.materia.cursadaMateriaKey() }
    )
}

internal fun cursadasEmptyStateMessage(
    cursadaCount: Int,
    filteredCount: Int,
    hasActiveFilter: Boolean,
): String? {
    require(cursadaCount >= 0)
    require(filteredCount in 0..cursadaCount)

    return when {
        cursadaCount == 0 -> "No hay cursadas disponibles."
        hasActiveFilter && filteredCount == 0 ->
            "No hay cursadas que coincidan con el filtro."
        else -> null
    }
}

@Composable
internal fun CursadasScreenContent(
    state: CursadasScreenContentState,
    filter: String,
    listState: LazyListState,
    onFilterChange: (String) -> Unit,
    onCursadaClick: (CursadaInfo) -> Unit,
) {
    val filtered = remember(state.cursadas, filter) {
        filteredCursadas(cursadas = state.cursadas, filter = filter)
    }
    val emptyStateMessage = cursadasEmptyStateMessage(
        cursadaCount = state.cursadas.size,
        filteredCount = filtered.size,
        hasActiveFilter = filter.isNotBlank(),
    )
    val loadingPhase = contentLoadingPhase(
        isLoading = state.loading,
        hasResolvedContent = state.hasResolvedSnapshot,
    )
    val feedback = cursadasFeedback(state)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = filter,
            onValueChange = onFilterChange,
            label = { Text("Filtrar por nombre de materia") },
            trailingIcon = {
                if (filter.isNotBlank()) {
                    IconButton(onClick = { onFilterChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Limpiar filtro",
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        if (feedback != null) {
            Text(
                text = feedback.message,
                color = if (feedback.isWarning) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
        }

        LoadingContentBox(
            phase = loadingPhase,
            initialText = "Cargando cursadas…",
            refreshContentDescription = "Actualizando cursadas",
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (
                    emptyStateMessage != null &&
                    (feedback == null || feedback.isWarning) &&
                    state.initialLoadCompleted
                ) {
                    item {
                        Text(
                            text = emptyStateMessage,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                        )
                    }
                }

                items(filtered, key = { cursada -> cursada.materia.cursadaMateriaKey() }) { cursada ->
                    CursadaCard(
                        cursada = cursada,
                        hasNews = cursada.materia.cursadaMateriaKey() in
                            state.cursadasConNovedades,
                        onClick = { onCursadaClick(cursada) },
                    )
                }
            }

            ScrollMoreHint(
                listState = listState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun CursadaCard(
    cursada: CursadaInfo,
    hasNews: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = cursada.materia,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                )
                if (hasNews) {
                    Text(
                        text = "Nuevo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = "Última actualización: ${cursada.ultimaModificacion.ifBlank { "Sin fecha" }}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
