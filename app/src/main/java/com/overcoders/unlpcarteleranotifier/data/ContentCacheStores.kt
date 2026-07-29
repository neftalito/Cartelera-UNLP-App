/** Implementa las cachés persistentes de contenidos remotos estructurados. */
package com.overcoders.unlpcarteleranotifier.data

import android.content.Context
import com.overcoders.unlpcarteleranotifier.model.Adjunto
import com.overcoders.unlpcarteleranotifier.model.CalendarioAcademico
import com.overcoders.unlpcarteleranotifier.model.EventualReservationFilterOption
import com.overcoders.unlpcarteleranotifier.model.EventualReservationFilterOptions
import com.overcoders.unlpcarteleranotifier.model.HorarioMateria
import com.overcoders.unlpcarteleranotifier.model.HorarioPeriodo
import com.overcoders.unlpcarteleranotifier.model.HorarioReserva
import com.overcoders.unlpcarteleranotifier.model.Mensaje
import com.overcoders.unlpcarteleranotifier.model.OptativeCareer
import com.overcoders.unlpcarteleranotifier.model.OptativeSubjectsPage
import com.overcoders.unlpcarteleranotifier.model.StudyPlanDocument
import com.overcoders.unlpcarteleranotifier.model.StudyPlanSource
import org.json.JSONArray
import org.json.JSONObject

object CalendarioCacheStore {
    suspend fun load(context: Context, year: Int): CachedValue<CalendarioAcademico>? =
        RemoteContentCache.readDecoded(context, key(year)) { payload ->
            val json = JSONObject(payload)
            require(json.getInt("year") == year)
            CalendarioAcademico(
                anio = json.getInt("year"),
                url = json.requiredNonBlank("url"),
                contenidoHtml = json.requiredNonBlank("contentHtml")
            )
        }

    suspend fun save(context: Context, calendar: CalendarioAcademico) {
        val json = JSONObject()
            .put("year", calendar.anio)
            .put("url", calendar.url)
            .put("contentHtml", calendar.contenidoHtml)
        RemoteContentCache.write(context, key(calendar.anio), json.toString())
    }

    private fun key(year: Int) = "academic_calendar_$year"
}
object StudyPlanCacheStore {
    suspend fun load(context: Context, source: StudyPlanSource): CachedValue<StudyPlanDocument>? =
        RemoteContentCache.readDecoded(context, key(source)) { payload ->
            val json = JSONObject(payload)
            require(json.getString("sourceUrl") == source.url)
            val subjectCount = json.getInt("subjectCount")
            require(subjectCount > 0)
            StudyPlanDocument(
                source = source,
                pageTitle = json.requiredNonBlank("pageTitle"),
                degreeTitle = json.requiredNonBlank("degreeTitle"),
                subjectCount = subjectCount,
                contentHtml = json.requiredNonBlank("contentHtml")
            )
        }

    suspend fun save(context: Context, document: StudyPlanDocument) {
        val json = JSONObject()
            .put("sourceUrl", document.source.url)
            .put("pageTitle", document.pageTitle)
            .put("degreeTitle", document.degreeTitle)
            .put("subjectCount", document.subjectCount)
            .put("contentHtml", document.contentHtml)
        RemoteContentCache.write(context, key(document.source), json.toString())
    }

    private fun key(source: StudyPlanSource) = "study_plan_${source.id}"
}

object OptativeSubjectsCacheStore {
    suspend fun load(
        context: Context,
        year: Int,
        career: OptativeCareer,
    ): CachedValue<OptativeSubjectsPage>? =
        RemoteContentCache.readDecoded(context, key(year, career)) { payload ->
            val json = JSONObject(payload)
            val cachedYear = json.getInt("year")
            val cachedCareer = OptativeCareer.valueOf(json.getString("career"))
            require(cachedYear == year && cachedCareer == career)
            OptativeSubjectsPage(
                year = cachedYear,
                career = cachedCareer,
                url = json.requiredNonBlank("url"),
                pageTitle = json.requiredNonBlank("pageTitle"),
                contentHtml = json.requiredNonBlank("contentHtml")
            )
        }

    suspend fun save(context: Context, page: OptativeSubjectsPage) {
        val json = JSONObject()
            .put("year", page.year)
            .put("career", page.career.name)
            .put("url", page.url)
            .put("pageTitle", page.pageTitle)
            .put("contentHtml", page.contentHtml)
        RemoteContentCache.write(context, key(page.year, page.career), json.toString())
    }

    private fun key(year: Int, career: OptativeCareer) =
        "optatives_${career.shortName}_$year"
}

object EventualReservationFiltersCacheStore {
    private const val KEY = "eventual_reservation_filter_options"

    suspend fun load(context: Context): CachedValue<EventualReservationFilterOptions>? =
        RemoteContentCache.readDecoded(context, KEY) { payload ->
            val json = JSONObject(payload)
            val options = EventualReservationFilterOptions(
                classrooms = decodeFilterOptions(json.getJSONArray("classrooms")),
                subjects = decodeFilterOptions(json.getJSONArray("subjects"))
            )
            require(options.classrooms.isNotEmpty() && options.subjects.isNotEmpty())
            options
        }

    suspend fun save(context: Context, options: EventualReservationFilterOptions) {
        val json = JSONObject()
            .put("classrooms", encodeFilterOptions(options.classrooms))
            .put("subjects", encodeFilterOptions(options.subjects))
        RemoteContentCache.write(context, KEY, json.toString())
    }

    private fun encodeFilterOptions(options: List<EventualReservationFilterOption>): JSONArray =
        JSONArray().apply {
            options.forEach { option ->
                put(JSONObject().put("id", option.id).put("label", option.label))
            }
        }

    private fun decodeFilterOptions(array: JSONArray): List<EventualReservationFilterOption> =
        buildList(array.length()) {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val id = item.getInt("id")
                val label = item.requiredNonBlank("label")
                require(id > 0)
                add(EventualReservationFilterOption(id = id, label = label))
            }
        }.also { options -> require(options.distinctBy { it.id }.size == options.size) }
}

object HorariosCacheStore {
    suspend fun load(context: Context, materiaId: Int): CachedValue<HorarioMateria>? =
        RemoteContentCache.readDecoded(context, key(materiaId)) { payload ->
            val json = JSONObject(payload)
            val period = json.getJSONObject("period")
            val reservations = json.getJSONArray("reservations")
            HorarioMateria(
                materiaNombre = json.requiredNonBlank("subjectName"),
                periodo = HorarioPeriodo(
                    nombre = period.requiredNonBlank("name"),
                    desde = period.getString("from"),
                    hasta = period.getString("to")
                ),
                reservas = buildList(reservations.length()) {
                    for (index in 0 until reservations.length()) {
                        val item = reservations.getJSONObject(index)
                        val day = item.getInt("day")
                        require(day in 0..5)
                        add(
                            HorarioReserva(
                                aula = item.getString("classroom"),
                                confirmada = item.getBoolean("confirmed"),
                                tipo = item.getString("type"),
                                dia = day,
                                horaInicio = item.getString("start"),
                                horaFin = item.getString("end")
                            )
                        )
                    }
                }
            )
        }

    suspend fun save(context: Context, materiaId: Int, schedule: HorarioMateria) {
        val reservations = JSONArray().apply {
            schedule.reservas.forEach { reservation ->
                put(
                    JSONObject()
                        .put("classroom", reservation.aula)
                        .put("confirmed", reservation.confirmada)
                        .put("type", reservation.tipo)
                        .put("day", reservation.dia)
                        .put("start", reservation.horaInicio)
                        .put("end", reservation.horaFin)
                )
            }
        }
        val json = JSONObject()
            .put("subjectName", schedule.materiaNombre)
            .put(
                "period",
                JSONObject()
                    .put("name", schedule.periodo.nombre)
                    .put("from", schedule.periodo.desde)
                    .put("to", schedule.periodo.hasta)
            )
            .put("reservations", reservations)
        RemoteContentCache.write(context, key(materiaId), json.toString())
    }

    private fun key(materiaId: Int) = "schedule_$materiaId"
}

data class AnunciosSnapshot(
    val total: Int,
    val messages: List<Mensaje>,
    val nextOffset: Int,
)

object AnunciosCacheStore {
    private const val KEY = "cartelera_first_page"

    suspend fun load(
        context: Context,
        materiaId: Int? = null,
    ): CachedValue<AnunciosSnapshot>? =
        RemoteContentCache.readDecoded(context, key(materiaId)) { payload ->
            val json = JSONObject(payload)
            val messages = json.getJSONArray("messages")
            val total = json.getInt("total")
            val nextOffset = json.getInt("nextOffset")
            require(total >= messages.length())
            require(nextOffset >= messages.length())
            val decodedMessages = buildList(messages.length()) {
                for (index in 0 until messages.length()) {
                    val item = messages.getJSONObject(index)
                    val attachments = item.getJSONArray("attachments")
                    add(
                        Mensaje(
                            materia = item.requiredNonBlank("subject"),
                            titulo = item.requiredNonBlank("title"),
                            cuerpoHtml = item.getString("bodyHtml"),
                            fecha = item.requiredNonBlank("date"),
                            autor = item.getString("author"),
                            isAnulado = item.getBoolean("cancelled"),
                            adjuntos = buildList(attachments.length()) {
                                for (attachmentIndex in 0 until attachments.length()) {
                                    val attachment = attachments.getJSONObject(attachmentIndex)
                                    add(
                                        Adjunto(
                                            nombre = attachment.requiredNonBlank("name"),
                                            publicPath = attachment.requiredNonBlank("path")
                                        )
                                    )
                                }
                            }
                        )
                    )
                }
            }
            AnunciosSnapshot(
                total = total,
                messages = decodedMessages.distinct(),
                nextOffset = nextOffset,
            )
        }

    suspend fun save(
        context: Context,
        snapshot: AnunciosSnapshot,
        materiaId: Int? = null,
    ) {
        val messages = JSONArray().apply {
            snapshot.messages.forEach { message ->
                val attachments = JSONArray().apply {
                    message.adjuntos.forEach { attachment ->
                        put(
                            JSONObject()
                                .put("name", attachment.nombre)
                                .put("path", attachment.publicPath)
                        )
                    }
                }
                put(
                    JSONObject()
                        .put("subject", message.materia)
                        .put("title", message.titulo)
                        .put("bodyHtml", message.cuerpoHtml)
                        .put("date", message.fecha)
                        .put("author", message.autor)
                        .put("cancelled", message.isAnulado)
                        .put("attachments", attachments)
                )
            }
        }
        val json = JSONObject()
            .put("total", snapshot.total)
            .put("nextOffset", snapshot.nextOffset)
            .put("messages", messages)
        RemoteContentCache.write(context, key(materiaId), json.toString())
    }

    private fun key(materiaId: Int?): String =
        materiaId?.let { "cartelera_subject_${it}_snapshot" } ?: KEY
}

private fun JSONObject.requiredNonBlank(key: String): String =
    getString(key).trim().also { require(it.isNotEmpty()) }
