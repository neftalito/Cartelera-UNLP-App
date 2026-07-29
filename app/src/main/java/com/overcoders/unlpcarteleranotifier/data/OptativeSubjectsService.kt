/** Descarga los documentos HTML de materias optativas por carrera. */
package com.overcoders.unlpcarteleranotifier.data

import com.overcoders.unlpcarteleranotifier.model.OptativeCareer
import com.overcoders.unlpcarteleranotifier.model.OptativeSubjectsPage
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class OptativeSubjectsService(client: OkHttpClient? = null) {
    private val client: OkHttpClient by lazy {
        client ?: AppHttpClient.instance
    }
    suspend fun fetch(
        year: Int,
        career: OptativeCareer,
    ): OptativeSubjectsPage {
        require(year >= 2022) { "year debe ser mayor o igual a 2022" }

        val url = buildUrl(year, career)
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "text/html")
            .build()

        return client.awaitParsedBody(request) { html ->
            parsePage(
                year = year,
                career = career,
                url = url,
                html = html
            )
        }
    }

    fun buildUrl(year: Int, career: OptativeCareer): String =
        "https://www.info.unlp.edu.ar/optativas-$year-licenciatura-en-${career.slug}/"

    internal fun parsePage(
        year: Int,
        career: OptativeCareer,
        url: String,
        html: String,
    ): OptativeSubjectsPage {
        val doc = Jsoup.parse(html, url)
        val content = doc.selectFirst("div.content")
            ?: throw IllegalStateException("No se encontro el contenido de optativas.")

        content.select("script, style, noscript, iframe").remove()
        absolutizeLinks(content)

        val pageTitle = content.selectFirst("div.page-title h2")
            ?.text()
            ?.trim()
            ?.ifBlank { null }
            ?: "Optativas $year - ${career.displayName}"

        val table = content.selectFirst("table.table-bordered")
            ?.clone()
            ?: throw IllegalStateException("No se encontro la tabla de optativas.")

        cleanTable(table)

        val filteredContent = Element("div").addClass("content")
        filteredContent.appendChild(table)

        return OptativeSubjectsPage(
            year = year,
            career = career,
            url = url,
            pageTitle = pageTitle,
            contentHtml = filteredContent.outerHtml().trim()
        )
    }

    private fun cleanTable(table: Element) {
        table.removeAttr("summary")
        table.removeAttr("cellspacing")
        table.removeAttr("cellpadding")
        absolutizeLinks(table)
    }

    private fun absolutizeLinks(root: Element) {
        root.select("a[href]").forEach { link ->
            val original = link.attr("href").trim()
            val absolute = link.absUrl("href").trim()
            when {
                absolute.isNotEmpty() -> link.attr("href", absolute)
                original.isNotEmpty() -> link.attr("href", original)
            }
        }
        root.select("img[src]").forEach { image ->
            val absolute = image.absUrl("src").trim()
            if (absolute.isNotEmpty()) {
                image.attr("src", absolute)
            }
        }
    }
}
