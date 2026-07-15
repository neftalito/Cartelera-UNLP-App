/** Implementa acciones reutilizables para copiar y compartir texto plano. */
package com.overcoders.unlpcarteleranotifier.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

internal fun copyPlainText(
    context: Context,
    label: String,
    text: String,
    successMessage: String = "Copiado al portapapeles",
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(
        context,
        successMessage,
        Toast.LENGTH_SHORT
    ).show()
}

internal fun sharePlainText(
    context: Context,
    text: String,
    chooserTitle: String,
) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }.onFailure {
        Toast.makeText(
            context,
            "No hay una aplicación disponible para compartir.",
            Toast.LENGTH_SHORT
        ).show()
    }
}
