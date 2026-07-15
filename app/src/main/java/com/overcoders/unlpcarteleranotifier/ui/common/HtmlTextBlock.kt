/** Renderiza fragmentos HTML sencillos como texto enriquecido de Compose. */
package com.overcoders.unlpcarteleranotifier.ui.common

import android.content.Context
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.overcoders.unlpcarteleranotifier.ui.parseHtmlWithoutTextColors

@Composable
fun HtmlTextBlock(
    html: String,
    context: Context,
    modifier: Modifier = Modifier,
    emptyHtmlFallback: String = "<p>Sin información</p>",
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(12.dp),
        factory = {
            TextView(context).apply {
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { tv ->
            tv.setTextColor(textColor)
            tv.setLinkTextColor(linkColor)
            tv.text = parseHtmlWithoutTextColors(
                html.ifBlank { emptyHtmlFallback }
            )
        }
    )
}
