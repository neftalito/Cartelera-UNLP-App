/** Descarga y convierte las páginas JSON de anuncios de cartelera. */
package com.overcoders.unlpcarteleranotifier.data

import com.overcoders.unlpcarteleranotifier.model.Adjunto
import com.overcoders.unlpcarteleranotifier.model.Mensaje
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class AnunciosService(client: OkHttpClient? = null) {
    private val client: OkHttpClient by lazy {
        client ?: AppHttpClient.instance
    }
    private val baseUrl = "https://gestiondocente.info.unlp.edu.ar/cartelera/data"

    data class FetchResult(
        val total: Int,
        val mensajes: List<Mensaje>,
        val receivedCount: Int,
    )

    /**
     * @param desde offset inicial (incluye)
     * @param cantidad cantidad de elementos a solicitar desde el offset
     * @param idMateria si es null o vacío => feed global. Si no, filtra por materia.
     */
    suspend fun fetch(
        desde: Int,
        cantidad: Int,
        idMateria: Int? = null
    ): FetchResult {
        require(desde >= 0) { "desde debe ser >= 0" }
        require(cantidad >= 0) { "cantidad debe ser >= 0" }

        val url = buildUrl(desde, cantidad, idMateria)

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .build()

        return client.awaitParsedBody(request, ::parse)
    }

    internal fun parse(body: String): FetchResult {
        val root = JSONObject(body)
        val total = root.optInt("total", 0).coerceAtLeast(0)
        val mensajesJson = root.getJSONArray("mensajes")

        val out = ArrayList<Mensaje>(mensajesJson.length())

        for (i in 0 until mensajesJson.length()) {
            val item = mensajesJson.optJSONObject(i) ?: continue

            val materia = item.optString("materia", "").trim()
            val titulo = item.optString("titulo", "").trim()
            val cuerpoHtml = item.optString("cuerpo", "")
            val fecha = item.optString("fecha", "").trim()
            val autor = item.optString("autor", "").trim()
            if (materia.isEmpty() || titulo.isEmpty() || fecha.isEmpty()) continue
            val isAnulado = item.optBoolean("is_anulado", false)
            val adjuntosJson = item.optJSONArray("adjuntos")
            val adjuntos = buildList {
                if (adjuntosJson != null) {
                    for (j in 0 until adjuntosJson.length()) {
                        val adjunto = adjuntosJson.optJSONObject(j) ?: continue
                        val nombre = adjunto.optString("nombre", "").trim()
                        val publicPath = adjunto.optString("public_path", "").trim()
                        if (nombre.isNotEmpty() && publicPath.isNotEmpty()) {
                            add(Adjunto(nombre = nombre, publicPath = publicPath))
                        }
                    }
                }
            }

            out.add(
                Mensaje(
                    materia = materia,
                    titulo = titulo,
                    cuerpoHtml = cuerpoHtml,
                    fecha = fecha,
                    autor = autor,
                    isAnulado = isAnulado,
                    adjuntos = adjuntos
                )
            )
        }

        check(mensajesJson.length() == 0 || out.isNotEmpty()) {
            "La respuesta contiene anuncios inválidos."
        }

        return FetchResult(
            total = total,
            mensajes = out,
            receivedCount = mensajesJson.length()
        )
    }

    private fun buildUrl(desde: Int, cantidad: Int, idMateria: Int?): String {
        val endpoint = "$baseUrl/$desde/$cantidad"
        return if (idMateria != null) "$endpoint?idMateria=$idMateria" else "$endpoint?idMateria="
    }
}
