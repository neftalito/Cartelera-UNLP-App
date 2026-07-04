package com.overcoders.unlpcarteleranotifier.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.method.LinkMovementMethod
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.data.AnunciosService
import com.overcoders.unlpcarteleranotifier.data.ApiException
import com.overcoders.unlpcarteleranotifier.data.MateriasService
import com.overcoders.unlpcarteleranotifier.data.MateriasStore
import com.overcoders.unlpcarteleranotifier.data.SettingsStore
import com.overcoders.unlpcarteleranotifier.model.MateriaCatalogItem
import com.overcoders.unlpcarteleranotifier.model.Mensaje
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

@Stable
private class MateriasFilterState(
    expanded: Boolean = false,
    query: String = "",
    selected: MateriaCatalogItem? = null
) {
    var expanded by mutableStateOf(expanded)
    var query by mutableStateOf(query)
    var selected by mutableStateOf(selected)
}

@Composable
private fun rememberMateriasFilterState(): MateriasFilterState {
    return remember { MateriasFilterState() }
}

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
    onInitialSelectedConsumed: () -> Unit = {},
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
    var materias by remember { mutableStateOf<List<MateriaCatalogItem>>(emptyList()) }
    @Suppress("VariableNeverRead") var materiasError by remember { mutableStateOf<String?>(null) }
    var materiasLoading by remember { mutableStateOf(false) }

    // El endpoint puede repetir elementos entre recargas o cambios de paginado. Mantener una
    // clave estable evita tarjetas duplicadas cuando acumulamos páginas en memoria.
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
            // Un reset representa un nuevo snapshot del feed: limpiamos la lista acumulada y
            // recalculamos contadores sólo cuando estamos en el feed global.
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

            // Dedupe defensivo: el backend no garantiza que cada página sea perfectamente
            // disjunta respecto de la anterior o de una recarga manual.
            val newOnes = page.mensajes.filter { m ->
                val k = keyOf(m)
                if (seenKeys.contains(k)) false else {
                    seenKeys.add(k); true
                }
            }

            anuncios = anuncios + newOnes
            // El contador de novedades se apoya en el total global visto por última vez; no
            // aplica cuando el usuario está filtrando por una materia puntual.
            if (reset && materiasFilterState.selected == null) {
                val storedTotal = lastSeenTotal ?: -1
                val diff = if (storedTotal < 0) 0 else (page.total - storedTotal).coerceAtLeast(0)
                newCount = if (storedTotal < 0) 0 else diff
                if (newCount > 0) {
                    Toast.makeText(
                        context,
                        "Hay $newCount anuncios nuevos",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                SettingsStore.setLastSeenTotal(context, page.total)
                lastSeenTotal = page.total
            }
            offset = startOffset + pageSize
        } catch (e: Exception) {
            error = errorMessageFor(e)
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
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Anuncio", shareText))
                            Toast.makeText(
                                context,
                                "Copiado al portapapeles",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ),
                    HeaderAction(
                        icon = Icons.Default.Share,
                        contentDescription = "Compartir anuncio",
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Compartir anuncio"))
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
        } catch (e: Exception) {
            @Suppress("AssignedValueIsNeverRead")
            materiasError = e.message
        } finally {
            materiasLoading = false
        }
    }

    LaunchedEffect(materiasFilterState.selected?.id) {
        loadPage(reset = true)
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
                placeholder = { Text("Buscar materia…") },
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
//            Button(
//                onClick = { scope.launch { loadPage(reset = false) } },
//                enabled = !loadingInitial && !loadingMore
//            ) {
//                Text("Reintentar cargar más")
//            }
//            Spacer(Modifier.height(12.dp))
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
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
    return when (error) {
        is ApiException -> {
            val code = error.httpCode
            if (code != null) {
                "Hubo un error al obtener los anuncios (HTTP $code)."
            } else {
                "Hubo un error al obtener los anuncios (${error.message})."
            }
        }
        is IOException -> "Hubo un error al obtener los anuncios (${error.localizedMessage ?: "Error de red"})."
        else -> "Hubo un error al obtener los anuncios."
    }
}

@Composable
private fun AnuncioCard(
    anuncio: Mensaje,
    isNew: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isNew) 1f else 0.6f)
            .clickable { onClick() }
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        anuncio.titulo,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (anuncio.adjuntos.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Tiene adjuntos",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (isNew) {
                    Text(
                        text = "Nuevo",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(anuncio.materia, style = MaterialTheme.typography.bodySmall)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(anuncio.fecha, style = MaterialTheme.typography.labelSmall)
                Text(anuncio.autor, style = MaterialTheme.typography.labelSmall)
            }

            if (anuncio.isAnulado) {
                Text("ANULADO", color = MaterialTheme.colorScheme.error)
            }

            val preview = remember(anuncio.cuerpoHtml) {
                HtmlCompat.fromHtml(
                    anuncio.cuerpoHtml,
                    HtmlCompat.FROM_HTML_MODE_LEGACY
                ).toString()
                    .replace("\n", " ")
                    .replace("\r", " ")
                    .take(180) + if (anuncio.cuerpoHtml.length > 180) "…" else ""
            }
            Text(preview, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun AnuncioDetailScreen(
    anuncio: Mensaje,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val detailScrollState = rememberScrollState()
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(anuncio.titulo, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(anuncio.materia, style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(6.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(anuncio.fecha, style = MaterialTheme.typography.bodySmall)
            Text(anuncio.autor, style = MaterialTheme.typography.bodySmall)
        }

        if (anuncio.isAnulado) {
            Spacer(Modifier.height(8.dp))
            Text("ANULADO", color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(12.dp))

        val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
        val linkColor = MaterialTheme.colorScheme.primary.toArgb()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(detailScrollState)
                    .padding(12.dp)
            ) {
                @Suppress("COMPOSE_APPLIER_CALL_MISMATCH")
                AndroidView(
                    modifier = Modifier.fillMaxWidth(),
                    factory = {
                        TextView(context).apply {
                            // habilita links <a href=...>
                            setTextIsSelectable(true)
                            movementMethod = LinkMovementMethod.getInstance()
                        }
                    },
                    update = { tv ->
                        tv.setTextColor(textColor)
                        tv.setLinkTextColor(linkColor)
                        tv.text = parseHtmlWithoutTextColors(anuncio.cuerpoHtml)
                    }
                )

                if (anuncio.adjuntos.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text("Adjuntos", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    anuncio.adjuntos.forEach { adjunto ->
                        Text(
                            text = adjunto.nombre,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val normalizedUri = normalizeAttachmentUri(adjunto.publicPath)
                                    if (normalizedUri == null) {
                                        Toast.makeText(
                                            context,
                                            "No se pudo abrir el adjunto",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@clickable
                                    }

                                    runCatching { uriHandler.openUri(normalizedUri) }
                                        .onFailure {
                                            Toast.makeText(
                                                context,
                                                "No se pudo abrir el adjunto",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }

            ScrollMoreHint(
                scrollState = detailScrollState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp)
            )
        }
    }
}

@SuppressLint("UseKtx")
private fun normalizeAttachmentUri(rawUri: String): String? {
    val trimmed = rawUri.trim()
    if (trimmed.isEmpty()) return null

    val parsedUri = trimmed.toUri()
    val scheme = parsedUri.scheme?.lowercase()
    if (scheme == "http" || scheme == "https") {
        return parsedUri.toString()
    }

    return null
}

private fun buildShareText(anuncio: Mensaje): String {
    val mensajePlano = htmlToShareText(anuncio.cuerpoHtml)
    return buildString {
        appendLine(anuncio.titulo)
        appendLine(anuncio.materia)
        appendLine(anuncio.fecha)
        appendLine(anuncio.autor)
        appendLine()
        append(mensajePlano)
    }
}
