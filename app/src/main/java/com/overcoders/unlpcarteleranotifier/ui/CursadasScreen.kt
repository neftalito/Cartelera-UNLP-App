package com.overcoders.unlpcarteleranotifier.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.data.CursadasStore
import com.overcoders.unlpcarteleranotifier.data.SettingsStore
import com.overcoders.unlpcarteleranotifier.model.CursadaInfo
import com.overcoders.unlpcarteleranotifier.worker.CursadasNotificationDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
fun CursadasScreen(
    initialSelected: CursadaInfo? = null,
    onInitialSelectedConsumed: () -> Unit = {},
    onTitleChange: (String?) -> Unit = {},
    onFullscreenDetailChange: (Boolean) -> Unit = {},
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<CursadaInfo?>(null) }
    var cursadas by remember { mutableStateOf<List<CursadaInfo>>(emptyList()) }
    var cursadasConNovedades by remember { mutableStateOf<Set<String>>(emptySet()) }
    var initialLoadCompleted by remember { mutableStateOf(false) }
    var consumedInitialSelection by remember { mutableStateOf<CursadaInfo?>(null) }
    val listState = rememberLazyListState()

    suspend fun refresh(notifyChanges: Boolean) {
        loading = true
        error = null
        try {
            cursadas = withContext(Dispatchers.IO) {
                CursadasNotificationDispatcher.process(
                    context = context,
                    notifyChanges = notifyChanges
                )
            }

            val vistosPorMateria = withContext(Dispatchers.IO) {
                CursadasStore.ensureSeenBaseline(context, cursadas)
            }

            cursadasConNovedades = cursadas
                .filter { cursada ->
                    val ultimaVista = vistosPorMateria[cursada.materia] ?: Long.MIN_VALUE
                    val ultimaActualizacion = cursada.ultimaModificacionEpochMillis ?: Long.MIN_VALUE
                    ultimaActualizacion > ultimaVista
                }
                .map { it.materia }
                .toSet()
        } catch (e: Exception) {
            error = errorMessageFor(e)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(loading, cursadasConNovedades, selected) {
        if (selected != null) {
            val cursada = selected ?: return@LaunchedEffect
            val shareText = buildShareText(cursada)
            onHeaderActionsChange(
                listOf(
                    HeaderAction(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        onClick = { selected = null }
                    ),
                    HeaderAction(
                        icon = Icons.Default.ContentCopy,
                        contentDescription = "Copiar cursada",
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Cursada", shareText))
                            Toast.makeText(
                                context,
                                "Copiado al portapapeles",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ),
                    HeaderAction(
                        icon = Icons.Default.Share,
                        contentDescription = "Compartir cursada",
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Compartir cursada"))
                        }
                    )
                )
            )
        } else {
            onHeaderActionsChange(
                listOf(
                    HeaderAction(
                        icon = Icons.Default.DoneAll,
                        contentDescription = "Marcar todas las cursadas como vistas",
                        enabled = !loading && cursadasConNovedades.isNotEmpty(),
                        onClick = {
                            cursadasConNovedades = emptySet()
                            scope.launch(Dispatchers.IO) {
                                CursadasStore.markAllAsSeen(context, cursadas)
                            }
                        }
                    ),
                    HeaderAction(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Refrescar cursadas",
                        enabled = !loading,
                        onClick = { scope.launch { refresh(notifyChanges = false) } }
                    )
                )
            )
        }
    }

    LaunchedEffect(selected) {
        onFullscreenDetailChange(selected != null)
        onTitleChange(if (selected != null) "Cursada" else null)
    }

    DisposableEffect(Unit) {
        onDispose {
            onHeaderActionsChange(emptyList())
            onTitleChange(null)
            onFullscreenDetailChange(false)
        }
    }

    LaunchedEffect(initialSelected) {
        if (initialSelected == null) {
            consumedInitialSelection = null
        }
    }

    LaunchedEffect(initialSelected, cursadas, initialLoadCompleted) {
        val pendingSelection = initialSelected ?: return@LaunchedEffect
        if (consumedInitialSelection == pendingSelection) {
            return@LaunchedEffect
        }
        val matchingCursada = cursadas.firstOrNull { it.materia == pendingSelection.materia }

        if (matchingCursada == null && !initialLoadCompleted) {
            return@LaunchedEffect
        }

        consumedInitialSelection = pendingSelection
        val cursadaToOpen = matchingCursada ?: pendingSelection
        cursadasConNovedades = cursadasConNovedades - cursadaToOpen.materia
        withContext(Dispatchers.IO) {
            CursadasStore.markAsSeen(context, cursadaToOpen)
        }
        selected = cursadaToOpen
        onInitialSelectedConsumed()
    }

    LaunchedEffect(Unit) {
        val autoCheckEnabled = SettingsStore.cursadasAutoCheckEnabled(context)
        refresh(notifyChanges = !autoCheckEnabled)
        initialLoadCompleted = true
    }

    if (selected != null) {
        CursadaDetailScreen(cursada = selected!!, onBack = {
            selected = null
        })
        return
    }

    LaunchedEffect(cursadas, filter) {
        listState.scrollToItem(0)
    }

    val filtered = remember(cursadas, filter) {
        val query = filter.trim().lowercase()
        val matchingCursadas = if (query.isBlank()) cursadas
        else cursadas.filter { it.materia.lowercase().contains(query) }

        matchingCursadas.sortedWith(
            compareByDescending<CursadaInfo> {
                it.ultimaModificacionEpochMillis ?: Long.MIN_VALUE
            }.thenBy { it.materia.lowercase() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            label = { Text("Filtrar por nombre de materia") },
            trailingIcon = {
                if (filter.isNotBlank()) {
                    IconButton(onClick = { filter = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Limpiar filtro"
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        if (loading) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            Spacer(Modifier.height(8.dp))
        }

        if (error != null) {
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered, key = { it.materia }) { cursada ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            cursadasConNovedades = cursadasConNovedades - cursada.materia
                            scope.launch(Dispatchers.IO) {
                                CursadasStore.markAsSeen(context, cursada)
                            }
                            selected = cursada
                        }
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = cursada.materia,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            if (cursada.materia in cursadasConNovedades) {
                                Text(
                                    text = "Nuevo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            text = "Última actualización: ${cursada.ultimaModificacion.ifBlank { "Sin fecha" }}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

private fun errorMessageFor(error: Throwable): String {
    return when (error) {
        is IOException -> "Hubo un error al obtener las cursadas (${error.localizedMessage ?: "Error de red"})."
        else -> "Hubo un error al obtener las cursadas."
    }
}

@Composable
private fun CursadaDetailScreen(cursada: CursadaInfo, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val detailScrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(cursada.materia, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Última actualización: ${cursada.ultimaModificacion.ifBlank { "Sin fecha" }}",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(detailScrollState)
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Inicio de cursada", style = MaterialTheme.typography.titleMedium)
                HtmlBlock(
                    html = cursada.inicioCursadaHtml,
                    textColor = textColor,
                    linkColor = linkColor,
                    context = context
                )
                HorizontalDivider()
                Text("Horarios de cursada", style = MaterialTheme.typography.titleMedium)
                HtmlBlock(
                    html = cursada.horariosCursadaHtml,
                    textColor = textColor,
                    linkColor = linkColor,
                    context = context
                )
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

private fun buildShareText(cursada: CursadaInfo): String {
    val inicioCursadaPlano = htmlToShareText(cursada.inicioCursadaHtml)
    val horariosCursadaPlano = htmlToShareText(cursada.horariosCursadaHtml)

    return buildString {
        appendLine(cursada.materia)
        appendLine("Última actualización: ${cursada.ultimaModificacion.ifBlank { "Sin fecha" }}")
        appendLine()
        appendLine("Inicio de cursada")
        appendLine(inicioCursadaPlano.ifBlank { "Sin información" })
        appendLine()
        appendLine("Horarios de cursada")
        append(horariosCursadaPlano.ifBlank { "Sin información" })
    }
}

@Composable
private fun HtmlBlock(html: String, textColor: Int, linkColor: Int, context: Context) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(12.dp),
        factory = {
            TextView(context).apply {
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { tv ->
            tv.setTextColor(textColor)
            tv.setLinkTextColor(linkColor)
            tv.text = parseHtmlWithoutTextColors(
                html.ifBlank { "<p>Sin información</p>" }
            )
        }
    )
}
