/** Consulta y parsea el estado remoto de las aulas. */
package com.overcoders.unlpcarteleranotifier.data

import com.overcoders.unlpcarteleranotifier.model.AulaEstado
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

class AulasService(client: OkHttpClient? = null) {
    private val client: OkHttpClient by lazy {
        client ?: AppHttpClient.instance
    }
    private val url = "https://gestiondocente.info.unlp.edu.ar/reservas/api/consulta/estadoactual"

    suspend fun fetchEstadoActual(): List<AulaEstado> {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return client.awaitParsedBody(request, ::parse)
    }

    internal fun parse(body: String): List<AulaEstado> {
        check(body.isNotBlank()) { "La respuesta de aulas está vacía." }

        val json = JSONArray(body)
        val result = buildList {
            for (index in 0 until json.length()) {
                val row = json.optJSONObject(index) ?: continue
                val aula = row.optJSONObject("aula")
                val materia = row.optJSONObject("materia")
                val desde = row.optJSONObject("horaDesde")
                val hasta = row.optJSONObject("horaHasta")
                val aulaNombre = aula?.optString("nombre").orEmpty().trim()
                val aulaId = aula?.optString("id").orEmpty().trim()
                if (aulaNombre.isBlank() && aulaId.isBlank()) continue

                add(
                    AulaEstado(
                        aulaNombre = aulaNombre,
                        aulaId = aulaId,
                        materia = materia?.optString("nombre").orEmpty().trim(),
                        horaDesde = formatJsonHour(desde, fallback = "-"),
                        horaHasta = formatJsonHour(hasta, fallback = "-")
                    )
                )
            }
        }
        check(json.length() == 0 || result.isNotEmpty()) {
            "La respuesta contiene aulas inválidas."
        }
        return result
    }
}
