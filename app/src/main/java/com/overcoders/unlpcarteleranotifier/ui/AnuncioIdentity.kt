/** Calcula identidades completas y claves guardables de anuncios. */
package com.overcoders.unlpcarteleranotifier.ui

import com.overcoders.unlpcarteleranotifier.model.Adjunto
import com.overcoders.unlpcarteleranotifier.model.Mensaje
import java.security.MessageDigest

internal data class AnuncioIdentity(
    val materia: String,
    val titulo: String,
    val cuerpoHtml: String,
    val fecha: String,
    val autor: String,
    val isAnulado: Boolean,
    val adjuntos: List<Adjunto>,
)

internal fun Mensaje.anuncioIdentity() = AnuncioIdentity(
    materia = materia,
    titulo = titulo,
    cuerpoHtml = cuerpoHtml,
    fecha = fecha,
    autor = autor,
    isAnulado = isAnulado,
    adjuntos = adjuntos,
)

/**
 * Las claves de LazyColumn deben poder guardarse en un Bundle. El resumen mantiene la clave
 * pequeña incluso cuando el cuerpo HTML es grande, pero incorpora todo el anuncio para no
 * descartar publicaciones distintas que compartan sus metadatos visibles.
 */
internal fun Mensaje.anuncioSaveableKey(): String {
    val canonicalValue = buildString {
        appendKeyPart(materia)
        appendKeyPart(titulo)
        appendKeyPart(cuerpoHtml)
        appendKeyPart(fecha)
        appendKeyPart(autor)
        appendKeyPart(isAnulado.toString())
        appendKeyPart(adjuntos.size.toString())
        adjuntos.forEach { attachment ->
            appendKeyPart(attachment.nombre)
            appendKeyPart(attachment.publicPath)
        }
    }
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(canonicalValue.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }
    return "anuncio_$digest"
}

private fun StringBuilder.appendKeyPart(value: String) {
    append(value.length)
    append(':')
    append(value)
    append(';')
}
