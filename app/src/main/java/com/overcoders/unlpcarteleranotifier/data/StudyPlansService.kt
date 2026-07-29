/** Descarga y extrae el contenido HTML de cada plan de estudio. */
package com.overcoders.unlpcarteleranotifier.data

import com.overcoders.unlpcarteleranotifier.model.StudyPlanDocument
import com.overcoders.unlpcarteleranotifier.model.StudyPlanSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class StudyPlansService(client: OkHttpClient? = null) {
    private val client: OkHttpClient by lazy {
        client ?: AppHttpClient.instance
    }
    suspend fun fetchOne(source: StudyPlanSource): StudyPlanDocument {
        val request = Request.Builder()
            .url(source.url)
            .get()
            .header("Accept", "text/html")
            .build()

        return client.awaitParsedBody(request) { html ->
            parseStudyPlan(source, html)
        }
    }

    internal fun parseStudyPlan(
        source: StudyPlanSource,
        html: String,
    ): StudyPlanDocument {
        val doc = Jsoup.parse(html, source.url)
        val content = doc.selectFirst("div.content")
            ?: throw IllegalStateException("No se encontró el contenido del plan.")

        content.select("script, style, noscript, iframe").remove()
        absolutizeLinks(content)

        val pageTitle = content.selectFirst("div.page-title h2")
            ?.text()
            ?.normalizeWhitespace()
            ?.ifBlank { null }
            ?: "${source.career.displayName} - Plan ${source.planLabel}"

        val degreeTitle = extractDegreeTitle(content) ?: source.career.degreeTitle
        val table = content.selectFirst("table.table-bordered")
            ?.clone()
            ?.also { cleanTable(it) }
            ?: throw IllegalStateException("No se encontró la tabla principal del plan.")

        val countedSubjects = countSubjects(table)
        check(countedSubjects > 0) { "La tabla del plan no contiene materias reconocibles." }
        val subjectCount = extractSubjectCount(content)
            ?.takeIf { it > 0 }
            ?: countedSubjects
        val filteredHtml = buildFilteredContentHtml(
            pageTitle = pageTitle,
            degreeTitle = degreeTitle,
            subjectCount = subjectCount,
            table = table
        )

        return StudyPlanDocument(
            source = source,
            pageTitle = pageTitle,
            degreeTitle = degreeTitle,
            subjectCount = subjectCount,
            contentHtml = filteredHtml
        )
    }

    private fun absolutizeLinks(content: Element) {
        content.select("a[href]").forEach { link ->
            val original = link.attr("href").trim()
            val absolute = link.absUrl("href").trim()
            when {
                absolute.isNotEmpty() -> link.attr("href", absolute)
                original.isNotEmpty() -> link.attr("href", original)
            }
        }
        content.select("img[src]").forEach { image ->
            val absolute = image.absUrl("src").trim()
            if (absolute.isNotEmpty()) {
                image.attr("src", absolute)
            }
        }
    }

    private fun cleanTable(table: Element) {
        table.removeAttr("summary")
        table.removeAttr("cellspacing")
        table.removeAttr("cellpadding")
        table.select(".tablas-esconder-td").remove()
        table.select("[colspan]").forEach { cell ->
            val colspan = cell.attr("colspan").toIntOrNull() ?: return@forEach
            if (colspan >= 5) {
                cell.attr("colspan", "4")
            }
        }
        absolutizeLinks(table)
    }

    private fun countSubjects(table: Element): Int {
        return table.select("tr").count { row ->
            if (
                row.hasClass("tabla-titulo-tr") ||
                row.hasClass("tabla-subtitulo-tr") ||
                row.hasClass("tabla-separador-tr")
            ) {
                return@count false
            }

            val cells = row.select("> td")
            if (cells.size < 2) return@count false

            val code = cells.getOrNull(0)?.text()?.normalizeWhitespace().orEmpty()
            val nameCell = cells.getOrNull(1) ?: return@count false
            val name = nameCell.text().normalizeWhitespace()
            looksLikeSubjectRow(code, nameCell, name)
        }
    }

    private fun looksLikeSubjectRow(
        code: String,
        nameCell: Element,
        name: String,
    ): Boolean {
        if (name.isBlank()) return false

        val hasAnchor = nameCell.selectFirst("a[href]") != null
        if (hasAnchor) return true

        if (name.startsWith("Optativa", ignoreCase = true)) return true
        if (name.contains("Tesina", ignoreCase = true)) return true
        if (name.contains("Práctica Profesional", ignoreCase = true)) return true

        val normalizedCode = code.replace("\\s+".toRegex(), "")
        return normalizedCode.matches(Regex("^[A-Z0-9]{3,}$", RegexOption.IGNORE_CASE))
    }

    private fun extractSubjectCount(content: Element): Int? {
        val regex = Regex("Cantidad de asignaturas:\\s*(\\d+)", RegexOption.IGNORE_CASE)
        return metadataCandidates(content).firstNotNullOfOrNull { text ->
            regex.find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
        }
    }

    private fun extractDegreeTitle(content: Element): String? {
        val regex = Regex("T[ií]tulo:\\s*(.+)", RegexOption.IGNORE_CASE)
        return metadataCandidates(content).firstNotNullOfOrNull { text ->
            regex.find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.normalizeWhitespace()
                ?.ifBlank { null }
        }
    }

    private fun metadataCandidates(content: Element): List<String> =
        content.select("p").map(Element::text) +
            content.select("div").map(Element::ownText)

    private fun buildFilteredContentHtml(
        pageTitle: String,
        degreeTitle: String,
        subjectCount: Int,
        table: Element,
    ): String {
        val content = Element("div").addClass("content")
        val pageTitleWrapper = Element("div").addClass("page-title pad group")
        pageTitleWrapper.appendElement("h2").text(pageTitle)

        val pad = Element("div").addClass("pad group")
        val entry = Element("div").addClass("entry themeform")
        entry.appendChild(table)

        val metadata = Element("p")
        metadata.appendText("Cantidad de asignaturas: ")
        metadata.appendElement("strong").text(subjectCount.toString())
        metadata.appendElement("br")
        metadata.appendText("Título: ")
        metadata.appendElement("strong").text(degreeTitle)
        entry.appendChild(metadata)

        pad.appendChild(entry)
        content.appendChild(pageTitleWrapper)
        content.appendChild(pad)

        return content.outerHtml().trim()
    }
}

private fun String.normalizeWhitespace(): String =
    replace('\u00A0', ' ')
        .replace("\\s+".toRegex(), " ")
        .trim()
