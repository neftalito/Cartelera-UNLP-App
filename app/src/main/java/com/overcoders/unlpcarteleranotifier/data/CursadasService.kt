/**
 * Descarga y convierte la tabla remota de cursadas en modelos validados de la aplicación.
 */
package com.overcoders.unlpcarteleranotifier.data

import com.overcoders.unlpcarteleranotifier.model.CursadaInfo
import com.overcoders.unlpcarteleranotifier.model.cursadaMateriaKey
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Descarga la tabla pública de cursadas y la normaliza a un modelo estable.
 */
class CursadasService(client: OkHttpClient? = null) {
    private val client: OkHttpClient by lazy {
        client ?: AppHttpClient.instance
    }
    private val url = "https://gestiondocente.info.unlp.edu.ar/cursadas/"
    private val formatter = DateTimeFormatter.ofPattern(
        "dd/MM/yyyy HH:mm",
        Locale.Builder()
            .setLanguage("es")
            .setRegion("AR")
            .build()
    )

    suspend fun fetchAndParse(): List<CursadaInfo> {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return client.awaitParsedBody(request, ::parse)
    }

    internal fun parse(html: String): List<CursadaInfo> {
        check(html.isNotBlank()) { "La respuesta de cursadas está vacía." }
        val doc = Jsoup.parse(html)

        // La página puede incluir otras tablas auxiliares. Anclamos la búsqueda a la que
        // contiene el encabezado "Materia", que es la fuente real de la sección.
        val mainTable = checkNotNull(doc.selectFirst(
            "table:has(> thead > tr > th:matchesOwn(^\\s*Materia\\s*$))"
        )) { "No se encontró la tabla de cursadas." }

        val rows = mainTable.select("> tbody > tr")

        var recognizedRowCount = 0
        val parsedRows = rows.mapNotNull { row ->
            val cells = row.select("> td")
            if (cells.size < 5) return@mapNotNull null

            val materia = cells[0].text().trim()
            if (materia.isBlank()) return@mapNotNull null
            recognizedRowCount += 1

            val inicioHtml = normalizedCellHtml(cells[2])
            val horariosHtml = cells[3].html().trim()
            val ultimaModificacion = cells[4].text().trim()

            val hasDate = ultimaModificacion.isNotBlank()
            val hasInicioInfo = Jsoup.parse(inicioHtml).text().isNotBlank()
            val hasHorariosInfo = Jsoup.parse(horariosHtml).text().isNotBlank()
            if (!hasDate && !hasInicioInfo && !hasHorariosInfo) return@mapNotNull null

            CursadaInfo(
                materia = materia,
                inicioCursadaHtml = inicioHtml,
                horariosCursadaHtml = horariosHtml,
                ultimaModificacion = ultimaModificacion,
                ultimaModificacionEpochMillis = parseDate(ultimaModificacion)
            )
        }

        check(rows.isEmpty() || recognizedRowCount > 0) {
            "La tabla de cursadas contiene filas, pero ninguna tiene el formato esperado."
        }

        return parsedRows.sortedWith(
            compareByDescending<CursadaInfo> { it.ultimaModificacionEpochMillis ?: Long.MIN_VALUE }
                .thenBy { it.materia.cursadaMateriaKey() }
        ).distinctBy { it.materia.cursadaMateriaKey() }
    }

    private fun parseDate(value: String): Long? {
        if (value.isBlank()) return null
        return runCatching {
            val localDateTime = LocalDateTime.parse(value, formatter)
            localDateTime.atZone(CARTELERA_ZONE).toInstant().toEpochMilli()
        }.getOrNull()
    }

    private companion object {
        val CARTELERA_ZONE: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")
    }

    // Algunas celdas vienen como una tabla anidada. La aplanamos para reutilizar ese HTML en
    // la UI y en notificaciones sin depender de la estructura específica del sitio original.
    private fun normalizedCellHtml(cell: org.jsoup.nodes.Element): String {
        val nested = cell.selectFirst("table") ?: return cell.html().trim()

        val parts = nested.select("td")
            .map { it.html().trim() }
            .filter { it.isNotBlank() }

        return parts.joinToString("<br/>").trim()
    }
}
