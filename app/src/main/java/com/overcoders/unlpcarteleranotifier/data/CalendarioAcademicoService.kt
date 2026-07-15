/** Descarga el documento HTML del calendario académico seleccionado. */
package com.overcoders.unlpcarteleranotifier.data

import com.overcoders.unlpcarteleranotifier.model.CalendarioAcademico
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

class CalendarioAcademicoService(client: OkHttpClient? = null) {
    private val client: OkHttpClient by lazy {
        client ?: AppHttpClient.instance
    }
    suspend fun fetch(anio: Int): CalendarioAcademico {
        require(anio >= 2018) { "anio debe ser mayor o igual a 2018" }

        val url = buildUrl(anio)
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "text/html")
            .build()

        return client.awaitParsedBody(request) { html ->
            parseCalendario(html, anio, url)
        }
    }

    fun buildUrl(anio: Int): String =
        "https://www.info.unlp.edu.ar/calendario-academico-$anio/"

    internal fun parseCalendario(html: String, anio: Int, url: String): CalendarioAcademico {
        val doc = Jsoup.parse(html, url)

        // La parte estable entre años es el bloque `.content`; descartamos el resto del sitio.
        val content = doc.selectFirst("div.content")
            ?: throw IllegalStateException("No se encontró el contenido del calendario.")

        content.select("script, style, noscript, iframe").remove()
        content.select("a[href]").forEach { link ->
            val originalHref = link.attr("href").trim()
            val absoluteHref = link.absUrl("href").trim()
            if (absoluteHref.isNotEmpty()) {
                link.attr("href", absoluteHref)
            } else if (originalHref.isNotEmpty()) {
                link.attr("href", originalHref)
            }
        }

        val contenidoHtml = content.outerHtml().trim()
        if (Jsoup.parse(contenidoHtml).text().isBlank()) {
            throw IllegalStateException("El calendario no contiene información visible.")
        }

        return CalendarioAcademico(
            anio = anio,
            url = url,
            contenidoHtml = contenidoHtml
        )
    }
}
