/** Descarga y parsea las reservas de aulas asociadas a una materia. */
package com.overcoders.unlpcarteleranotifier.data

import com.overcoders.unlpcarteleranotifier.model.HorarioMateria
import com.overcoders.unlpcarteleranotifier.model.HorarioPeriodo
import com.overcoders.unlpcarteleranotifier.model.HorarioReserva
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class HorariosService(client: OkHttpClient? = null) {
    private val client: OkHttpClient by lazy {
        client ?: AppHttpClient.instance
    }
    private val baseUrl = "https://gestiondocente.info.unlp.edu.ar/reservas/consulta/xmateria/data"

    suspend fun fetch(idMateria: Int, materiaNombre: String): HorarioMateria {
        require(idMateria > 0) { "idMateria debe ser mayor a cero" }

        val request = Request.Builder()
            .url("$baseUrl/$idMateria")
            .get()
            .header("Accept", "application/json")
            .build()

        return client.awaitParsedBody(request) { body ->
            parse(body, materiaNombre)
        }
    }

    internal fun parse(body: String, materiaNombre: String): HorarioMateria {
        val root = JSONObject(body)

        val periodoJson = root.getJSONObject("periodo")
        val desde = periodoJson.getJSONObject("desde")
        val hasta = periodoJson.getJSONObject("hasta")

        val periodo = HorarioPeriodo(
            nombre = periodoJson.optString("nombre").trim().ifBlank { "Sin período" },
            desde = "${desde.optString("d", "--")}/${desde.optString("m", "--")}/${desde.optString("y", "----")}",
            hasta = "${hasta.optString("d", "--")}/${hasta.optString("m", "--")}/${hasta.optString("y", "----")}"
        )

        val reservasJson = root.optJSONArray("reservas")
        val reservas = if (reservasJson == null) {
            emptyList()
        } else {
            buildList(reservasJson.length()) {
                for (index in 0 until reservasJson.length()) {
                    val item = reservasJson.optJSONObject(index) ?: continue
                    val day = item.optInt("dia", -1)
                    if (day !in 0..5) continue

                    add(
                        HorarioReserva(
                            aula = item.optString("aula").trim().ifBlank { "Sin aula" },
                            confirmada = item.optBoolean("confirmada", false),
                            tipo = item.optString("tipo").trim().ifBlank { "Sin tipo" },
                            dia = day,
                            horaInicio = formatJsonHour(
                                item.optJSONObject("horaInicio"),
                                fallback = "--:--"
                            ),
                            horaFin = formatJsonHour(
                                item.optJSONObject("horaFin"),
                                fallback = "--:--"
                            )
                        )
                    )
                }
            }
        }

        check(reservasJson == null || reservasJson.length() == 0 || reservas.isNotEmpty()) {
            "La respuesta contiene horarios inválidos."
        }

        return HorarioMateria(materiaNombre = materiaNombre, periodo = periodo, reservas = reservas)
    }
}
