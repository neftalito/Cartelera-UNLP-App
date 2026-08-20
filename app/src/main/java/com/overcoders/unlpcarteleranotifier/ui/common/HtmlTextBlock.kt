/** Renderiza fragmentos HTML sencillos como texto enriquecido de Compose. */
package com.overcoders.unlpcarteleranotifier.ui.common

import android.content.Context
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.overcoders.unlpcarteleranotifier.ui.parseHtmlWithoutTextColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
fun HtmlTextBlock(
    html: String,
    context: Context,
    modifier: Modifier = Modifier,
    emptyHtmlFallback: String = "<p>Sin información</p>",
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val resolvedHtml = html.ifBlank { emptyHtmlFallback }
    val containsImages = remember(resolvedHtml) {
        resolvedHtml.contains("<img", ignoreCase = true)
    }
    var contentWidthPx by remember { mutableIntStateOf(0) }
    val renderedTextHtml = remember(resolvedHtml, containsImages) {
        if (containsImages) null else parseHtmlWithoutTextColors(resolvedHtml)
    }
    var renderedImageHtml by remember(resolvedHtml) {
        mutableStateOf<Spanned?>(null)
    }
    val imageRenderMutex = remember { Mutex() }

    LaunchedEffect(resolvedHtml, contentWidthPx, containsImages) {
        if (!containsImages || contentWidthPx <= 0) return@LaunchedEffect
        val parsedHtml = withContext(Dispatchers.Default) {
            imageRenderMutex.withLock {
                currentCoroutineContext().ensureActive()
                parseHtmlWithoutTextColors(
                    resolvedHtml,
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
            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
            .padding(12.dp)
            .onSizeChanged { size ->
                if (size.width != contentWidthPx) contentWidthPx = size.width
            },
        factory = { viewContext ->
            TextView(viewContext).apply {
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { tv ->
            tv.setTextColor(textColor)
            tv.setLinkTextColor(linkColor)
            tv.text = renderedTextHtml ?: renderedImageHtml ?: ""
        }
    )
}
