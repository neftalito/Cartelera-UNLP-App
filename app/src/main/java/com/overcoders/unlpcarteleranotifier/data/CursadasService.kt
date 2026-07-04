package com.overcoders.unlpcarteleranotifier.data

import com.overcoders.unlpcarteleranotifier.model.CursadaInfo
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

/**
 * Descarga la tabla pública de cursadas, la normaliza a un modelo estable y genera un hash
 * del HTML relevante para detectar cambios remotos de forma barata en sincronizaciones futuras.
 */
class CursadasService(
    private val client: OkHttpClient = AppHttpClient.instance,
) {
    private val url = "https://gestiondocente.info.unlp.edu.ar/cursadas/"
    private val formatter = DateTimeFormatter.ofPattern(
        "dd/MM/yyyy HH:mm",
        Locale.Builder()
            .setLanguage("es")
            .setRegion("AR")
            .build()
    )

    data class FetchResult(
        val cursadas: List<CursadaInfo>,
        val tableHash: String
    )

    fun fetchAndParse(): FetchResult {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("HTTP ${resp.code}")
            val html = resp.body.string()
            if (html.isBlank()) return FetchResult(emptyList(), "")

            val doc = Jsoup.parse(html)

            // La página puede incluir otras tablas auxiliares. Anclamos la búsqueda a la que
            // contiene el encabezado "Materia", que es la fuente real de la sección.
            val mainTable = doc.selectFirst(
                "table:has(> thead > tr > th:matchesOwn(^\\s*Materia\\s*$))"
            ) ?: return FetchResult(emptyList(), "")

            val rows = mainTable.select("> tbody > tr")

            val cursadas = rows.mapNotNull { row ->
                val cells = row.select("> td")
                if (cells.size < 5) return@mapNotNull null

                val materia = cells[0].text().trim()
                if (materia.isBlank()) return@mapNotNull null

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
            }.sortedWith(
                compareByDescending<CursadaInfo> { it.ultimaModificacionEpochMillis ?: Long.MIN_VALUE }
                    .thenBy { it.materia.lowercase() }
            )

            // Hashamos el HTML de cada fila para detectar cambios aunque el orden visual o el
            // modelo derivado se mantengan similares entre dos descargas consecutivas.
            val fullHtmlForHash = rows.joinToString("\n") { it.outerHtml() }
            return FetchResult(cursadas = cursadas, tableHash = sha256(fullHtmlForHash))
        }
    }


    private fun parseDate(value: String): Long? {
        if (value.isBlank()) return null
        return runCatching {
            val localDateTime = LocalDateTime.parse(value, formatter)
            localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
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
