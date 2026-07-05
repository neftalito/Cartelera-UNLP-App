package com.overcoders.unlpcarteleranotifier.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.data.AppHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

private data class AulaEstado(
    val aulaNombre: String,
    val aulaId: String,
    val materia: String,
    val horaDesde: String,
    val horaHasta: String,
)

private class AulasService(
    private val client: OkHttpClient = AppHttpClient.instance,
) {
    private val url = "https://gestiondocente.info.unlp.edu.ar/reservas/api/consulta/estadoactual"

    fun fetchEstadoActual(): List<AulaEstado> {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
            val body = resp.body.string()
            if (body.isBlank()) return emptyList()

            val json = JSONArray(body)
            return buildList {
                for (i in 0 until json.length()) {
                    val row = json.optJSONObject(i) ?: continue
                    val aula = row.optJSONObject("aula")
                    val materia = row.optJSONObject("materia")
                    val desde = row.optJSONObject("horaDesde")
                    val hasta = row.optJSONObject("horaHasta")

                    add(
                        AulaEstado(
                            aulaNombre = aula?.optString("nombre").orEmpty(),
                            aulaId = aula?.optString("id").orEmpty(),
                            materia = materia?.optString("nombre").orEmpty(),
                            horaDesde = formatHour(desde),
                            horaHasta = formatHour(hasta)
                        )
                    )
                }
            }
        }
    }

    private fun formatHour(hourObj: org.json.JSONObject?): String {
        val rawHour = hourObj?.optString("h").orEmpty().trim()
        val rawMinute = hourObj?.optString("m").orEmpty().trim()
        val hour = rawHour.toIntOrNull()
        val minute = rawMinute.toIntOrNull()

        if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) {
            return "-"
        }

        return "%02d:%02d".format(hour, minute)
    }
}

@Composable
fun AulasScreen(
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val aulasService = remember { AulasService() }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var aulas by remember { mutableStateOf<List<AulaEstado>>(emptyList()) }
    val aulasListState = rememberLazyListState()

    suspend fun loadAulas() {
        loading = true
        error = null
        try {
            aulas = withContext(Dispatchers.IO) {
                aulasService.fetchEstadoActual()
            }
        } catch (e: Exception) {
            error = e.message ?: "No se pudo cargar el estado actual de aulas."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        loadAulas()
    }

    val canShareAulas = !loading && error == null && aulas.isNotEmpty()

    LaunchedEffect(loading, canShareAulas) {
        onHeaderActionsChange(
            listOf(
                HeaderAction(
                    icon = Icons.Default.ContentCopy,
                    contentDescription = "Copiar estado de aulas",
                    enabled = canShareAulas,
                    onClick = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("Estado de aulas", buildShareText(aulas))
                        )
                        android.widget.Toast.makeText(
                            context,
                            "Copiado al portapapeles",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                ),
                HeaderAction(
                    icon = Icons.Default.Share,
                    contentDescription = "Compartir estado de aulas",
                    enabled = canShareAulas,
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, buildShareText(aulas))
                        }
                        context.startActivity(Intent.createChooser(intent, "Compartir estado de aulas"))
                    }
                ),
                HeaderAction(
                    icon = Icons.Default.Refresh,
                    contentDescription = "Refrescar estado de aulas",
                    enabled = !loading,
                    onClick = { scope.launch { loadAulas() } }
                )
            )
        )
    }

    DisposableEffect(Unit) {
        onDispose { onHeaderActionsChange(emptyList()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        when {
            loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Cargando estado actual de las aulas...")
                }
            }

            error != null -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Error al cargar aulas",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(error.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
            }

            aulas.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "No hay aulas ocupadas en este momento.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = aulasListState,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 36.dp)
                    ) {
                        items(aulas, key = { "${it.aulaNombre}-${it.aulaId}-${it.horaDesde}" }) { aula ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "${aula.aulaNombre} - ${aula.aulaId}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = aula.materia,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "${aula.horaDesde} a ${aula.horaHasta}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    ScrollMoreHint(
                        listState = aulasListState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}

private fun buildShareText(aulas: List<AulaEstado>): String {
    if (aulas.isEmpty()) {
        return "Estado actual de aulas\n\nNo hay aulas ocupadas en este momento."
    }

    val lines = aulas
        .sortedWith(compareBy(AulaEstado::aulaNombre, AulaEstado::aulaId, AulaEstado::horaDesde))
        .map { aula ->
            "• ${aula.aulaNombre} - ${aula.aulaId}: ${aula.materia} (${aula.horaDesde} a ${aula.horaHasta})"
        }

    return buildString {
        appendLine("Estado actual de aulas")
        appendLine()
        append(lines.joinToString("\n"))
    }.trim()
}
