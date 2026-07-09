package com.overcoders.unlpcarteleranotifier.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.overcoders.unlpcarteleranotifier.BuildConfig
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.data.AnunciosService
import com.overcoders.unlpcarteleranotifier.data.ApiException
import com.overcoders.unlpcarteleranotifier.data.MateriasService
import com.overcoders.unlpcarteleranotifier.data.MateriasStore
import com.overcoders.unlpcarteleranotifier.data.SettingsStore
import com.overcoders.unlpcarteleranotifier.model.CarteleraNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.MateriaCatalogItem
import com.overcoders.unlpcarteleranotifier.model.Mensaje
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Pantalla principal de anuncios.
 *
 * Combina feed global o filtrado por materia, paginación incremental y el detalle
 * del anuncio seleccionado, incluyendo acciones para compartir o copiar contenido.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MateriasScreen(
    initialSelected: Mensaje? = null,
    initialTarget: CarteleraNotificationTarget? = null,
    onInitialSelectedConsumed: () -> Unit = {},
    onInitialTargetConsumed: () -> Unit = {},
    onTitleChange: (String?) -> Unit = {},
    onFullscreenDetailChange: (Boolean) -> Unit = {},
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val anunciosService = remember { AnunciosService() }
    val materiasService = remember { MateriasService() }
    val hideCancelledMessages by SettingsStore.hideCancelledMateriasMessagesFlow(context)
        .collectAsState(initial = false)

    val pageSize = 10

    var loadingInitial by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var offset by remember { mutableIntStateOf(0) }
    var anuncios by remember { mutableStateOf<List<Mensaje>>(emptyList()) }
    var newCount by remember { mutableIntStateOf(0) }
    var lastSeenTotal by remember { mutableStateOf<Int?>(null) }

    var selected by remember { mutableStateOf<Mensaje?>(null) }
    var pendingTarget by remember { mutableStateOf<CarteleraNotificationTarget?>(null) }
    var materias by remember { mutableStateOf<List<MateriaCatalogItem>>(emptyList()) }
    @Suppress("VariableNeverRead") var materiasError by remember { mutableStateOf<String?>(null) }
    var materiasLoading by remember { mutableStateOf(false) }

    val seenKeys = remember { mutableStateListOf<String>() }
    fun keyOf(m: Mensaje): String = "${m.materia}||${m.titulo}||${m.fecha}||${m.autor}"

    val materiasFilterState = rememberMateriasFilterState()

    val materiasFiltradas = remember(materias, materiasFilterState.query) {
        val query = materiasFilterState.query.trim().lowercase()
        if (query.isBlank()) materias
        else materias.filter { it.nombre.lowercase().contains(query) }
    }

    suspend fun loadPage(reset: Boolean) {
        error = null
        if (reset) {
            loadingInitial = true
            loadingMore = false
            offset = 0
            anuncios = emptyList()
            seenKeys.clear()
            if (materiasFilterState.selected == null) {
                lastSeenTotal = SettingsStore.getLastSeenTotal(context)
            } else {
                newCount = 0
            }
        } else {
            loadingMore = true
        }

        try {
            val materiaId = materiasFilterState.selected?.id?.toIntOrNull()
            val startOffset = offset
            val page = withContext(Dispatchers.IO) {
                anunciosService.fetch(
                    desde = startOffset,
                    cantidad = pageSize,
                    idMateria = materiaId
                )
            }

            val newOnes = page.mensajes.filter { m ->
                val k = keyOf(m)
                if (seenKeys.contains(k)) {
                    false
                } else {
                    seenKeys.add(k)
                    true
                }
            }

            anuncios = anuncios + newOnes
            if (reset && materiasFilterState.selected == null) {
                val storedTotal = lastSeenTotal ?: -1
                val diff = if (storedTotal < 0) 0 else (page.total - storedTotal).coerceAtLeast(0)
                newCount = if (storedTotal < 0) 0 else diff
                if (newCount > 0) {
                    android.widget.Toast.makeText(
                        context,
                        "Hay $newCount anuncios nuevos",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                SettingsStore.setLastSeenTotal(context, page.total)
                lastSeenTotal = page.total
            }
            offset = startOffset + pageSize
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = errorMessageFor(error = e)
        } finally {
            loadingInitial = false
            loadingMore = false
        }
    }

    LaunchedEffect(loadingInitial, loadingMore, selected) {
        if (selected != null) {
            val anuncio = selected ?: return@LaunchedEffect
            val shareText = buildShareText(anuncio)
            onHeaderActionsChange(
                listOf(
                    HeaderAction(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        onClick = { selected = null }
                    ),
                    HeaderAction(
                        icon = Icons.Default.ContentCopy,
                        contentDescription = "Copiar anuncio",
                        onClick = {
                            copyPlainText(
                                context = context,
                                label = "Anuncio",
                                text = shareText
                            )
                        }
                    ),
                    HeaderAction(
                        icon = Icons.Default.Share,
                        contentDescription = "Compartir anuncio",
                        onClick = {
                            sharePlainText(
                                context = context,
                                text = shareText,
                                chooserTitle = "Compartir anuncio"
                            )
                        }
                    )
                )
            )
        } else {
            onHeaderActionsChange(
                listOf(
                    HeaderAction(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Refrescar anuncios",
                        enabled = !loadingInitial && !loadingMore,
                        onClick = { scope.launch { loadPage(reset = true) } }
                    )
                )
            )
        }
    }

    LaunchedEffect(selected) {
        onFullscreenDetailChange(selected != null)
        onTitleChange(if (selected != null) "Anuncio" else null)
    }

    DisposableEffect(Unit) {
        onDispose {
            onHeaderActionsChange(emptyList())
            onTitleChange(null)
            onFullscreenDetailChange(false)
        }
    }

    LaunchedEffect(initialSelected) {
        if (initialSelected != null) {
            selected = initialSelected
            onInitialSelectedConsumed()
        }
    }

    LaunchedEffect(initialTarget) {
        if (initialTarget != null) {
            pendingTarget = initialTarget
            onInitialTargetConsumed()
        }
    }

    LaunchedEffect(Unit) {
        materiasLoading = true
        try {
            val cached = withContext(Dispatchers.IO) { MateriasStore.load(context) }
            materias = cached
            if (cached.isEmpty()) {
                val fetched = withContext(Dispatchers.IO) { materiasService.refresh(context) }
                if (fetched.isNotEmpty()) {
                    materias = fetched
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            @Suppress("AssignedValueIsNeverRead")
            materiasError = e.message
        } finally {
            materiasLoading = false
        }
    }

    LaunchedEffect(pendingTarget?.materiaId, materias) {
        val target = pendingTarget ?: return@LaunchedEffect
        val materiaId = target.materiaId ?: return@LaunchedEffect
        if (materias.isEmpty() || materiasFilterState.selected?.id == materiaId) {
            return@LaunchedEffect
        }
        val materia = materias.firstOrNull { it.id == materiaId } ?: return@LaunchedEffect
        materiasFilterState.selected = materia
        materiasFilterState.query = materia.nombre
    }

    LaunchedEffect(materiasFilterState.selected?.id) {
        loadPage(reset = true)
    }

    LaunchedEffect(pendingTarget, anuncios) {
        val target = pendingTarget ?: return@LaunchedEffect
        val match = anuncios.firstOrNull { anuncio ->
            anuncio.titulo == target.titulo &&
                anuncio.fecha == target.fecha &&
                anuncio.materia.trim().equals(target.materia.trim(), ignoreCase = true) &&
                (target.autor.isBlank() || anuncio.autor == target.autor)
        } ?: return@LaunchedEffect
        selected = match
        pendingTarget = null
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (selected == null && shouldLoadMore && !loadingInitial && !loadingMore && error == null) {
            loadPage(reset = false)
        }
    }

    if (selected != null) {
        AnuncioDetailScreen(
            anuncio = selected!!,
            onBack = { selected = null }
        )
        return
    }

    val showNotificationOpeningState = loadingInitial && (initialTarget != null || pendingTarget != null)

    if (showNotificationOpeningState) {
        NotificationOpeningState()
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = materiasFilterState.expanded,
            onExpandedChange = { isExpanded ->
                if (isExpanded) {
                    materiasFilterState.query = ""
                }
                materiasFilterState.expanded = isExpanded
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = materiasFilterState.query,
                onValueChange = {
                    materiasFilterState.query = it
                    if (it.isBlank() || materiasFilterState.selected?.nombre?.trim() != it.trim()) {
                        materiasFilterState.selected = null
                    }
                },
                label = { Text("Filtrar por materia") },
                placeholder = { Text("Buscar materia...") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = materiasFilterState.expanded)
                },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true)
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = materiasFilterState.expanded,
                onDismissRequest = { materiasFilterState.expanded = false }
            ) {
                if (materiasLoading && materias.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Cargando materias...") },
                        onClick = { materiasFilterState.expanded = false },
                        enabled = false
                    )
                } else if (materiasFiltradas.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("Sin resultados") },
                        onClick = { materiasFilterState.expanded = false },
                        enabled = false
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        materiasFiltradas.forEach { materia ->
                            DropdownMenuItem(
                                text = { Text(materia.nombre) },
                                onClick = {
                                    materiasFilterState.selected = materia
                                    materiasFilterState.query = materia.nombre
                                    materiasFilterState.expanded = false
                                    focusManager.clearFocus(force = true)
                                    keyboardController?.hide()
                                }
                            )
                        }
                    }
                }
            }
        }

        if (materiasFilterState.selected != null) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                materiasFilterState.selected = null
                materiasFilterState.query = ""
            }) {
                Text("Quitar filtro")
            }
        }

        Spacer(Modifier.height(8.dp))

        if (loadingInitial) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
        }

        if (error != null) {
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        val anunciosVisibles = remember(anuncios, hideCancelledMessages) {
            if (hideCancelledMessages) anuncios.filterNot { it.isAnulado } else anuncios
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(anunciosVisibles, key = { _, item -> keyOf(item) }) { index, a ->
                AnuncioCard(
                    anuncio = a,
                    isNew = materiasFilterState.selected == null && index < newCount,
                    onClick = { selected = a }
                )
            }

            item {
                Spacer(Modifier.height(8.dp))

                if (loadingMore) {
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                    Spacer(Modifier.height(8.dp))
                } else {
                    Button(
                        onClick = { scope.launch { loadPage(reset = false) } },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loadingInitial && !loadingMore
                    ) {
                        Text("Cargar más")
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

private fun errorMessageFor(error: Throwable): String {
    val errorDetail = error.message?.takeIf { it.isNotBlank() }
        ?: error::class.java.simpleName.takeIf { it.isNotBlank() }
        ?: "Error desconocido"

    fun withDebugDetail(baseMessage: String): String {
        return if (BuildConfig.DEBUG) "$baseMessage ($errorDetail)." else baseMessage
    }

    return when (error) {
        is ApiException -> {
            val suffix = error.httpCode?.let { " (HTTP $it)" } ?: ""
            withDebugDetail("Hubo un error al obtener los anuncios$suffix")
        }

        is IOException -> withDebugDetail("Hubo un error de red al obtener los anuncios")
        else -> withDebugDetail("Hubo un error al obtener los anuncios")
    }
}
