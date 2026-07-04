package com.overcoders.unlpcarteleranotifier.data

import com.overcoders.unlpcarteleranotifier.model.HorarioMateria
import com.overcoders.unlpcarteleranotifier.model.HorarioPeriodo
import com.overcoders.unlpcarteleranotifier.model.HorarioReserva
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class HorariosService(
    private val client: OkHttpClient = OkHttpClient(),
) {
    private val baseUrl = "https://gestiondocente.info.unlp.edu.ar/reservas/consulta/xmateria/data"

    fun fetch(idMateria: Int, materiaNombre: String): HorarioMateria {
        require(idMateria > 0) { "idMateria debe ser mayor a cero" }

        val request = Request.Builder()
            .url("$baseUrl/$idMateria")
            .get()
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw ApiException(resp.code, "HTTP ${resp.code} - ${resp.message}")
            }

            val body = resp.body.string()
            val root = JSONObject(body)

            val periodoJson = root.getJSONObject("periodo")
            val desde = periodoJson.getJSONObject("desde")
            val hasta = periodoJson.getJSONObject("hasta")

            val periodo = HorarioPeriodo(
                nombre = periodoJson.optString("nombre", "Sin período"),
                desde = "${desde.optString("d", "--")}/${desde.optString("m", "--")}/${desde.optString("y", "----")}",
                hasta = "${hasta.optString("d", "--")}/${hasta.optString("m", "--")}/${hasta.optString("y", "----")}"
            )

            val reservasJson = root.optJSONArray("reservas")
            val reservas = if (reservasJson == null) {
                emptyList()
            } else {
                List(reservasJson.length()) { index ->
                    val item = reservasJson.getJSONObject(index)
                    val inicio = item.optJSONObject("horaInicio") ?: JSONObject()
                    val fin = item.optJSONObject("horaFin") ?: JSONObject()

                    HorarioReserva(
                        aula = item.optString("aula", "Sin aula"),
                        confirmada = item.optBoolean("confirmada", false),
                        tipo = item.optString("tipo", "Sin tipo"),
                        dia = item.optInt("dia", -1),
                        horaInicio = "${inicio.optString("h", "--")}:${inicio.optString("m", "--")}",
                        horaFin = "${fin.optString("h", "--")}:${fin.optString("m", "--")}"
                    )
                }
            }

            return HorarioMateria(materiaNombre = materiaNombre, periodo = periodo, reservas = reservas)
        }
    }
}
