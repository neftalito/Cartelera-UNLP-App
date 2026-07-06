package com.overcoders.unlpcarteleranotifier.data

import android.content.Context
import com.overcoders.unlpcarteleranotifier.model.MateriaCatalogItem
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

class MateriasService(
    private val client: OkHttpClient = AppHttpClient.instance,
) {
    private val url = "https://gestiondocente.info.unlp.edu.ar/cartelera/"

    /**
     * Fuerza refrescar desde la web y pisa cache.
     */
    suspend fun refresh(context: Context): List<MateriaCatalogItem> {
        val fetched = fetchAndParse()
        if (fetched.isNotEmpty()) {
            MateriasStore.save(context, fetched)
            MateriasRepository.invalidateCache()
        }
        return fetched
    }

    private fun fetchAndParse(): List<MateriaCatalogItem> {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw RuntimeException("HTTP ${resp.code}")
            }
            val html = resp.body.string()
            if (html.isBlank()) return emptyList()

            val doc = Jsoup.parse(html)
            val options = doc.select("select#form_materia option")

            return options.mapNotNull { opt ->
                val id = opt.attr("value").trim()
                val nombre = opt.text().trim()
                if (id.isBlank() || nombre.isBlank()) null
                else MateriaCatalogItem(id = id, nombre = nombre).withFriendlyTermName()
            }
                .distinctBy { it.id }
                .sortedBy { it.nombre.lowercase() }
        }
    }
}
