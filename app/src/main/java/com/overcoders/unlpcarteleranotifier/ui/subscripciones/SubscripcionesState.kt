/** Coordina la carga del catálogo y las actualizaciones optimistas de suscripciones. */
package com.overcoders.unlpcarteleranotifier.ui.subscripciones

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.overcoders.unlpcarteleranotifier.data.MateriasRepository
import com.overcoders.unlpcarteleranotifier.data.SubscripcionesStore
import com.overcoders.unlpcarteleranotifier.model.MateriaCatalogItem
import com.overcoders.unlpcarteleranotifier.ui.common.ContentLoadingPhase
import com.overcoders.unlpcarteleranotifier.ui.common.cachedContentWarning
import com.overcoders.unlpcarteleranotifier.ui.common.contentLoadingPhase
import com.overcoders.unlpcarteleranotifier.ui.common.normalizeForSearch
import com.overcoders.unlpcarteleranotifier.ui.common.userFacingError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class SubscripcionesUiState(
    val loadingPhase: ContentLoadingPhase,
    val loading: Boolean,
    val catalogEmpty: Boolean,
    val enabled: Boolean,
    val notifyAll: Boolean,
    val expanded: Boolean,
    val searchQuery: String,
    val availableMaterias: List<MateriaCatalogItem>,
    val subscriptions: List<SubscriptionEntry>,
    val catalogUnavailable: Boolean,
    val feedback: String?,
    val feedbackIsWarning: Boolean,
)

internal fun availableSubscriptionMaterias(
    materias: List<MateriaCatalogItem>,
    subscriptionIds: Set<String>,
    query: String,
): List<MateriaCatalogItem> {
    val normalizedQuery = query.normalizeForSearch()
    val available = materias.filterNot { materia -> materia.id in subscriptionIds }
    return if (normalizedQuery.isBlank()) {
        available
    } else {
        available.filter { materia ->
            materia.nombre.normalizeForSearch().contains(normalizedQuery)
        }
    }
}

@Stable
internal class SubscripcionesState(
    private val context: Context,
    private val scope: CoroutineScope,
    private val materiasRepository: MateriasRepository,
) {
    internal var loading by mutableStateOf(true)
        private set
    private var catalogError by mutableStateOf<String?>(null)
    private var catalogWarning by mutableStateOf<String?>(null)
    private var actionError by mutableStateOf<String?>(null)

    internal var expanded by mutableStateOf(false)
        private set
    internal var searchQuery by mutableStateOf("")
        private set
    private var pendingSubscriptions by mutableStateOf(emptySet<String>())

    internal suspend fun loadMateriasCatalog(forceRefresh: Boolean) {
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
        } catch (error: CancellationException) {
            throw error
        } finally {
            loading = false
        }
    }

    internal fun refreshMaterias() {
        scope.launch {
            loadMateriasCatalog(forceRefresh = true)
        }
    }

    internal fun setExpanded(value: Boolean, enabled: Boolean) {
        if (!enabled) return
        if (value) {
            searchQuery = ""
        }
        expanded = value
    }

    internal fun dismissSelector() {
        expanded = false
    }

    internal fun updateSearchQuery(value: String, enabled: Boolean) {
        if (!enabled) return
        searchQuery = value
        if (!expanded) {
            expanded = true
        }
    }

    internal fun subscribe(materiaId: String, enabled: Boolean) {
        if (!enabled) return
        actionError = null
        pendingSubscriptions = pendingSubscriptions + materiaId
        searchQuery = ""
        expanded = false
        scope.launch {
            try {
                SubscripcionesStore.subscribe(context, materiaId)
            } catch (error: CancellationException) {
                pendingSubscriptions = pendingSubscriptions - materiaId
                throw error
            } catch (error: Exception) {
                pendingSubscriptions = pendingSubscriptions - materiaId
                actionError = userFacingError(
                    operation = "guardar una suscripción",
                    error = error,
                )
            }
        }
    }

    internal fun unsubscribe(materiaId: String, enabled: Boolean) {
        if (!enabled) return
        actionError = null
        pendingSubscriptions = pendingSubscriptions - materiaId
        scope.launch {
            try {
                SubscripcionesStore.unsubscribe(context, materiaId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                actionError = userFacingError(
                    operation = "eliminar una suscripción",
                    error = error,
                )
            }
        }
    }

    internal fun reconcilePersistedSubscriptions(persistedIds: Set<String>) {
        pendingSubscriptions = pendingSubscriptions
            .filterNot(persistedIds::contains)
            .toSet()
    }

    internal fun uiState(
        materias: List<MateriaCatalogItem>,
        persistedSubscriptionIds: Set<String>,
        notifyAllPreference: Boolean?,
    ): SubscripcionesUiState {
        val enabled = notifyAllPreference == false
        val effectiveSubscriptionIds = persistedSubscriptionIds + pendingSubscriptions
        val availableMaterias = availableSubscriptionMaterias(
            materias = materias,
            subscriptionIds = effectiveSubscriptionIds,
            query = searchQuery,
        )
        val feedback = catalogError ?: actionError ?: catalogWarning

        return SubscripcionesUiState(
            loadingPhase = contentLoadingPhase(
                isLoading = loading,
                hasResolvedContent = materias.isNotEmpty(),
            ),
            loading = loading,
            catalogEmpty = materias.isEmpty(),
            enabled = enabled,
            notifyAll = notifyAllPreference == true,
            expanded = expanded,
            searchQuery = searchQuery,
            availableMaterias = availableMaterias,
            subscriptions = buildSubscriptionEntries(materias, effectiveSubscriptionIds),
            catalogUnavailable = catalogError != null && materias.isEmpty(),
            feedback = feedback,
            feedbackIsWarning = catalogError == null && actionError == null && feedback != null,
        )
    }
}

@Composable
internal fun rememberSubscripcionesState(
    materiasRepository: MateriasRepository,
): SubscripcionesState {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    return remember(context, scope, materiasRepository) {
        SubscripcionesState(
            context = context,
            scope = scope,
            materiasRepository = materiasRepository,
        )
    }
}
