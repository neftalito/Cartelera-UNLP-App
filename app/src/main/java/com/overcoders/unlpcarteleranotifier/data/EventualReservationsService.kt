/** Consulta filtros y páginas de reservas eventuales de aulas. */
package com.overcoders.unlpcarteleranotifier.data

import com.overcoders.unlpcarteleranotifier.model.EventualReservation
import com.overcoders.unlpcarteleranotifier.model.EventualReservationFilterOption
import com.overcoders.unlpcarteleranotifier.model.EventualReservationFilterOptions
import com.overcoders.unlpcarteleranotifier.model.EventualReservationsPage
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup

class EventualReservationsService(client: OkHttpClient? = null) {
    private val client: OkHttpClient by lazy {
        client ?: AppHttpClient.instance
    }
    suspend fun fetchFilterOptions(): EventualReservationFilterOptions {
        val request = Request.Builder()
            .url(RESERVATIONS_URL)
            .get()
            .header("Accept", "text/html")
            .build()
        return client.awaitParsedBody(request, ::parseFilterOptions)
    }

    internal fun parseFilterOptions(html: String): EventualReservationFilterOptions {
        val document = Jsoup.parse(html, RESERVATIONS_URL)
        val options = EventualReservationFilterOptions(
            classrooms = parseOptions(
                document.select("select#reservaseventualesadminfilterform_aula option[value]")
                    .map { it.attr("value") to it.text() }
            ),
            subjects = parseOptions(
                document.select("select#reservaseventualesadminfilterform_materia option[value]")
                    .map { it.attr("value") to it.text() }
            )
        )
        check(options.classrooms.isNotEmpty() && options.subjects.isNotEmpty()) {
            "La respuesta no contiene los filtros esperados."
        }
        return options
    }

    suspend fun fetchPage(
        page: Int,
        classroomId: Int? = null,
        subjectId: Int? = null,
    ): EventualReservationsPage {
        require(page > 0) { "page debe ser mayor a cero" }

        val url = DATA_URL.toHttpUrl().newBuilder()
            .addQueryParameter("reservaseventualesadminfilterform[id]", "")
            .addQueryParameter("reservaseventualesadminfilterform[aula]", classroomId?.toString().orEmpty())
            .addQueryParameter("reservaseventualesadminfilterform[materia]", subjectId?.toString().orEmpty())
            .addQueryParameter("reservaseventualesadminfilterform[fechaDesde]", "")
            .addQueryParameter("reservaseventualesadminfilterform[fechaHasta]", "")
            .addQueryParameter("page", page.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()

        return client.awaitParsedBody(request) { body ->
            parsePage(body, expectedPage = page)
        }
    }

    private fun parseOptions(rawOptions: List<Pair<String, String>>): List<EventualReservationFilterOption> =
        rawOptions.mapNotNull { (rawId, rawLabel) ->
            val id = rawId.trim().toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            val label = rawLabel.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            EventualReservationFilterOption(id = id, label = label)
        }.distinctBy(EventualReservationFilterOption::id)

    internal fun parsePage(body: String, expectedPage: Int? = null): EventualReservationsPage {
        val root = JSONObject(body)
        val data = root.optJSONArray("data")
            ?: throw IllegalStateException("La respuesta no contiene reservas.")
        val paginator = root.optJSONObject("paginator-data")
            ?: throw IllegalStateException("La respuesta no contiene paginación.")

        val reservations = buildList(data.length()) {
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val id = item.optInt("id", 0)
                if (id <= 0) continue

                val teacherName = listOf(
                    item.optString("docente_nombre").trim(),
                    item.optString("docente_apellido").trim()
                ).filter(String::isNotBlank).joinToString(" ")

                add(
                    EventualReservation(
                        id = id,
                        classroomName = item.optString("aula_nombre").trim(),
                        subjectName = item.optString("materia_nombre").trim(),
                        teacherName = teacherName,
                        reason = item.optString("motivo").trim(),
                        date = item.optString("fecha").trim(),
                        startTime = item.optString("horaInicio").trim(),
                        endTime = item.optString("horaFin").trim()
                    )
                )
            }
        }.distinctBy(EventualReservation::id)
        check(data.length() == 0 || reservations.isNotEmpty()) {
            "La respuesta contiene reservas inválidas."
        }

        val currentPage = paginator.optInt("current", 0)
        check(currentPage > 0) { "La respuesta contiene una página actual inválida." }
        check(expectedPage == null || currentPage == expectedPage) {
            "La respuesta corresponde a la página $currentPage, no a la $expectedPage."
        }
        val pageCount = paginator.optInt("pageCount", currentPage).coerceAtLeast(0)
        val nextPage = paginator.optInt("next", 0)
            .takeIf { it > currentPage && it <= pageCount }

        return EventualReservationsPage(
            reservations = reservations,
            currentPage = currentPage,
            nextPage = nextPage,
            totalCount = paginator.optInt("totalCount", reservations.size).coerceAtLeast(0)
        )
    }

    private companion object {
        const val RESERVATIONS_URL =
            "https://gestiondocente.info.unlp.edu.ar/reservas/eventuales"
        const val DATA_URL =
            "https://gestiondocente.info.unlp.edu.ar/reservas/eventuales/data"
    }
}
