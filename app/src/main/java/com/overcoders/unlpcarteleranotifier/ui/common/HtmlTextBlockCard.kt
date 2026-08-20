/** Renderiza el resumen de una tarjeta y admite imágenes Base64 de forma acotada. */
package com.overcoders.unlpcarteleranotifier.ui.common

import android.content.Context
import android.text.Spanned
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import com.overcoders.unlpcarteleranotifier.ui.parseHtmlWithoutTextColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
fun HtmlTextBlockCard(
    html: String,
    context: Context,
    modifier: Modifier = Modifier,
) {
    val containsImages = remember(html) {
        html.contains("<img", ignoreCase = true)
    }

    if (!containsImages) {
        val preview = remember(html) {
            val plainText = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
                .toString()
                .replace("\n", " ")
                .replace("\r", " ")
            truncateAnuncioPreview(plainText)
        }
        Text(
            text = preview,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier
        )
    } else {
        var contentWidthPx by remember { mutableIntStateOf(0) }
        var renderedImageHtml by remember(html) { mutableStateOf<Spanned?>(null) }
        val imageRenderMutex = remember { Mutex() }
        val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

        LaunchedEffect(html, contentWidthPx) {
            if (contentWidthPx <= 0) return@LaunchedEffect
            val parsedHtml = withContext(Dispatchers.Default) {
                imageRenderMutex.withLock {
                    currentCoroutineContext().ensureActive()
                    parseHtmlWithoutTextColors(
                        html,
                        InlineDataImageGetter(
                            resources = context.resources,
                            maxWidthPx = contentWidthPx,
                        ),
                    )
                }
            }
            renderedImageHtml = parsedHtml
        }

        AndroidView(
            modifier = modifier
                .fillMaxWidth()
                .onSizeChanged { size ->
                    if (size.width != contentWidthPx) contentWidthPx = size.width
                },
            factory = ::TextView,
            update = { tv ->
                tv.setTextColor(textColor)
                tv.text = renderedImageHtml ?: ""
            }
        )
    }
}

internal fun truncateAnuncioPreview(plainText: String, limit: Int = 180): String =
    plainText.take(limit) + if (plainText.length > limit) "…" else ""
