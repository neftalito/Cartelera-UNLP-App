/**
 * Descarga, valida y normaliza el catálogo remoto de materias de cartelera.
 */
package com.overcoders.unlpcarteleranotifier.data

import android.content.Context
import com.overcoders.unlpcarteleranotifier.model.MateriaCatalogItem
import com.overcoders.unlpcarteleranotifier.model.toMateriaCatalogIdOrNull
import java.util.Locale
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

class MateriasService(client: OkHttpClient? = null) {
    private val client: OkHttpClient by lazy {
        client ?: AppHttpClient.instance
    }
    private val url = "https://gestiondocente.info.unlp.edu.ar/cartelera/"

    /**
     * Fuerza refrescar desde la web y pisa cache.
     */
    suspend fun refresh(context: Context): List<MateriaCatalogItem> {
        val fetched = fetchAndParse()
        if (fetched.isNotEmpty()) {
            try {
                MateriasStore.save(context, fetched)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // El catálogo remoto sigue siendo utilizable aunque no pueda persistirse.
            }
        }
        return fetched
    }

    private suspend fun fetchAndParse(): List<MateriaCatalogItem> {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return client.awaitParsedBody(request, ::parse)
    }

    internal fun parse(html: String): List<MateriaCatalogItem> {
        check(html.isNotBlank()) { "La respuesta del catálogo de materias está vacía." }
        val doc = Jsoup.parse(html)
        val options = doc.select("select#form_materia option")

        val materias = options.mapNotNull { opt ->
            val id = opt.attr("value").toMateriaCatalogIdOrNull() ?: return@mapNotNull null
            val nombre = opt.text().trim()
            if (nombre.isBlank()) null
            else MateriaCatalogItem(id = id.toString(), nombre = nombre).withFriendlyTermName()
        }
            .distinctBy { it.id }
            .sortedBy { it.nombre.lowercase(Locale.ROOT) }
        check(materias.isNotEmpty()) { "No se encontró el catálogo de materias." }
        return materias
    }
}
