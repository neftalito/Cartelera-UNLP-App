package com.overcoders.unlpcarteleranotifier.data

import com.overcoders.unlpcarteleranotifier.model.AulaEstado
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class AulasService(
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
                for (index in 0 until json.length()) {
                    val row = json.optJSONObject(index) ?: continue
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

    private fun formatHour(hourObj: JSONObject?): String {
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
