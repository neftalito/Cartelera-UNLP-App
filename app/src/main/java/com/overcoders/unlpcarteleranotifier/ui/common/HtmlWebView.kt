/** Renderiza documentos HTML complejos en un WebView restringido y adaptable. */
package com.overcoders.unlpcarteleranotifier.ui.common

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.overcoders.unlpcarteleranotifier.ui.ScrollMoreHint
import java.util.Locale
import kotlin.math.abs

/**
 * Renderiza contenido remoto sin JavaScript. Cuando [fitContentWidth] está habilitado, ajusta
 * la escala después de la carga y ante cambios reales de ancho; el zoom manual posterior no
 * provoca nuevos ajustes. [showScrollMoreHint] indica mientras queda contenido vertical.
 */
@Composable
fun HtmlWebView(
    html: String,
    baseUrl: String,
    modifier: Modifier = Modifier,
    fitContentWidth: Boolean = false,
    showScrollMoreHint: Boolean = false,
) {
    val backgroundColor = MaterialTheme.colorScheme.surface.toArgb()
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
    val outlineColor = MaterialTheme.colorScheme.outlineVariant.toArgb()
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    val documentHtml = remember(
        html,
        backgroundColor,
        onSurfaceColor,
        primaryColor,
        outlineColor,
        surfaceVariantColor,
        fitContentWidth,
    ) {
        buildHtmlDocument(
            contentHtml = html,
            backgroundColor = backgroundColor,
            onSurfaceColor = onSurfaceColor,
            primaryColor = primaryColor,
            outlineColor = outlineColor,
            surfaceVariantColor = surfaceVariantColor,
            fitContentWidth = fitContentWidth,
        )
    }
    val documentKey = remember(baseUrl, documentHtml) {
        HtmlDocumentKey(
            baseUrl = baseUrl,
            documentHtml = documentHtml
        )
    }
    var canScrollForward by remember(documentKey) { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                InterceptAwareWebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(backgroundColor)
                    isVerticalScrollBarEnabled = true
                    settings.javaScriptEnabled = false
                    settings.domStorageEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.setSupportZoom(true)
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = shouldUseOverviewMode(fitContentWidth)
                    setCanScrollForwardListener { canScrollForward = it }
                    webViewClient = object : WebViewClient() {
                        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            val uri = url?.let(Uri::parse) ?: return false
                            return openExternally(context, uri)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val uri = request?.url ?: return false
                            return openExternally(context, uri)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            (view as? InterceptAwareWebView)?.onDocumentLoaded()
                        }

                        override fun onScaleChanged(
                            view: WebView?,
                            oldScale: Float,
                            newScale: Float,
                        ) {
                            super.onScaleChanged(view, oldScale, newScale)
                            (view as? InterceptAwareWebView)?.onDocumentScaleChanged()
                        }
                    }
                }
            },
            update = { webView ->
                webView.setCanScrollForwardListener { canScrollForward = it }
                webView.setBackgroundColor(backgroundColor)
                webView.settings.loadWithOverviewMode = shouldUseOverviewMode(fitContentWidth)
                webView.setFitContentWidth(fitContentWidth)
                if (webView.tag != documentKey) {
                    webView.prepareForDocumentLoad()
                    webView.loadDataWithBaseURL(baseUrl, documentHtml, "text/html", "utf-8", null)
                    webView.tag = documentKey
                }
            },
            onRelease = { webView ->
                webView.prepareForRelease()
                webView.stopLoading()
                webView.removeAllViews()
                webView.destroy()
            }
        )

        ScrollMoreHint(
            shouldShowHint = showScrollMoreHint && canScrollForward,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
        )
    }
}

private data class HtmlDocumentKey(
    val baseUrl: String,
    val documentHtml: String,
)

private fun buildHtmlDocument(
    contentHtml: String,
    backgroundColor: Int,
    onSurfaceColor: Int,
    primaryColor: Int,
    outlineColor: Int,
    surfaceVariantColor: Int,
    fitContentWidth: Boolean,
): String = """
    <!DOCTYPE html>
    <html lang="es">
    <head>
        <meta charset="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
        <style>
            html, body {
                margin: 0;
                padding: 0;
                background: ${backgroundColor.toCssHex()};
                color: ${onSurfaceColor.toCssHex()};
                font-family: sans-serif;
            }

            body {
                line-height: 1.5;
                -webkit-text-size-adjust: 100%;
            }

            .content {
                padding: 16px;
                box-sizing: border-box;
            }

            .content .pad {
                padding: 0;
            }

            .content .page-title {
                margin-bottom: 16px;
            }

            .content h1,
            .content h2,
            .content h3,
            .content h4 {
                color: ${onSurfaceColor.toCssHex()};
                line-height: 1.3;
            }

            .content p,
            .content li,
            .content td,
            .content th {
                color: ${onSurfaceColor.toCssHex()};
            }

            .content a {
                color: ${primaryColor.toCssHex()};
            }

            .content hr {
                border: 0;
                border-top: 1px solid ${outlineColor.toCssHex()};
                margin: 16px 0;
            }

            .content img {
                max-width: 100%;
                height: auto;
            }

            .content .wp-block-file {
                display: flex;
                flex-direction: column;
                align-items: flex-start;
                gap: 12px;
                margin: 0 0 20px 0;
            }

            .content .fluid-width-video-wrapper {
                width: 100%;
                max-width: 100%;
            }

            .content .wp-block-file__embed {
                width: 100%;
                min-height: 480px;
                border: 1px solid ${outlineColor.toCssHex()};
                border-radius: 8px;
                background: ${surfaceVariantColor.toCssHex()};
            }

            .content .wp-block-file > a:first-of-type {
                display: block;
                max-width: 100%;
                word-break: break-word;
            }

            .content .wp-block-file__button {
                display: none !important;
            }

            .content .wp-block-table,
            .content figure {
                margin: 0 0 16px 0;
                overflow-x: ${if (fitContentWidth) "visible" else "auto"};
            }

            .content table {
                width: 100%;
                border-collapse: collapse;
                margin: 12px 0 20px;
            }

            .content th,
            .content td {
                border: 1px solid ${outlineColor.toCssHex()};
                padding: 8px;
                vertical-align: top;
                word-break: break-word;
            }

            .content th {
                background: ${surfaceVariantColor.toCssHex()};
            }

            .content tr.is-highlight td {
                background: ${surfaceVariantColor.toCssHex()};
            }

            .content tr.is-highlight td:first-child {
                border-left: 3px solid ${primaryColor.toCssHex()};
            }

            .content tr.tabla-separador-tr > td {
                background: ${surfaceVariantColor.toCssHex()} !important;
                color: ${onSurfaceColor.toCssHex()} !important;
            }

            .content tr.tabla-separador-tr > td strong {
                color: inherit !important;
            }
        </style>
    </head>
    <body>
        $contentHtml
    </body>
    </html>
""".trimIndent()

private class InterceptAwareWebView(context: Context) : WebView(context) {
    private var fitContentWidth = false
    private var baseDocumentScale: Float? = null
    private var canScrollForwardListener: ((Boolean) -> Unit)? = null
    private var lastReportedCanScrollForward: Boolean? = null
    private val fitContentRunnable = Runnable { fitLoadedContentToWidth() }

    fun setCanScrollForwardListener(listener: ((Boolean) -> Unit)?) {
        canScrollForwardListener = listener
        lastReportedCanScrollForward = null
        post { reportCanScrollForward() }
    }

    fun setFitContentWidth(enabled: Boolean) {
        if (fitContentWidth == enabled) return
        fitContentWidth = enabled
        if (enabled) {
            scheduleFitContentToWidth()
        } else {
            restoreDocumentScale()
        }
    }

    fun prepareForDocumentLoad() {
        removeCallbacks(fitContentRunnable)
        baseDocumentScale = null
        setInitialScale(0)
        reportCanScrollForward(false)
    }

    fun prepareForRelease() {
        fitContentWidth = false
        removeCallbacks(fitContentRunnable)
        baseDocumentScale = null
        canScrollForwardListener = null
        lastReportedCanScrollForward = null
    }

    @Suppress("DEPRECATION")
    fun onDocumentLoaded() {
        scale.takeIf { it > 0f }?.let { baseDocumentScale = it }
        scheduleFitContentToWidth()
        postOnAnimation { reportCanScrollForward() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw) {
            scheduleFitContentToWidth()
        }
        if (w != oldw || h != oldh) {
            postOnAnimation { reportCanScrollForward() }
        }
    }

    fun onDocumentScaleChanged() {
        postOnAnimation { reportCanScrollForward() }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        reportCanScrollForward()
    }

    private fun scheduleFitContentToWidth() {
        if (!fitContentWidth || baseDocumentScale == null) return
        removeCallbacks(fitContentRunnable)
        postOnAnimation(fitContentRunnable)
    }

    @Suppress("DEPRECATION")
    private fun fitLoadedContentToWidth() {
        if (!fitContentWidth) return
        val currentScale = scale
        val maximumScale = baseDocumentScale ?: return
        val viewportWidth = computeHorizontalScrollExtent()
            .takeIf { it > 0 }
            ?: (width - paddingLeft - paddingRight)
        val contentWidth = computeHorizontalScrollRange()
        // `computeHorizontalScrollExtent` y `computeHorizontalScrollRange` usan la misma escala,
        // por lo que su relación sirve como factor de zoom sin habilitar JavaScript.
        val adjustment = fitContentWidthAdjustment(
            viewportWidth = viewportWidth,
            contentWidth = contentWidth,
            currentScale = currentScale,
            maximumScale = maximumScale,
        ) ?: return

        zoomBy(adjustment)
        scrollTo(0, scrollY)
        postOnAnimation { reportCanScrollForward() }
    }

    @Suppress("DEPRECATION")
    private fun restoreDocumentScale() {
        removeCallbacks(fitContentRunnable)
        val targetScale = baseDocumentScale ?: return
        val currentScale = scale
        if (currentScale <= 0f) return
        val adjustment = (targetScale / currentScale).coerceIn(MIN_ZOOM_FACTOR, MAX_ZOOM_FACTOR)
        if (abs(adjustment - 1f) >= ZOOM_EPSILON) {
            zoomBy(adjustment)
            postOnAnimation { reportCanScrollForward() }
        }
    }

    private fun reportCanScrollForward(
        canScrollForward: Boolean = canScrollVertically(1),
    ) {
        if (lastReportedCanScrollForward == canScrollForward) return
        lastReportedCanScrollForward = canScrollForward
        canScrollForwardListener?.invoke(canScrollForward)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        if (event.actionMasked == MotionEvent.ACTION_UP) {
            performClick()
        }

        return super.onTouchEvent(event)
    }

    private companion object {
        const val MIN_ZOOM_FACTOR = 0.01f
        const val MAX_ZOOM_FACTOR = 100f
        const val ZOOM_EPSILON = 0.01f
    }
}

internal fun fitContentWidthAdjustment(
    viewportWidth: Int,
    contentWidth: Int,
    currentScale: Float,
    maximumScale: Float,
): Float? {
    if (
        viewportWidth <= 0 ||
        contentWidth <= 0 ||
        currentScale <= 0f ||
        maximumScale <= 0f
    ) {
        return null
    }

    val targetScale = minOf(
        currentScale * viewportWidth.toFloat() / contentWidth.toFloat(),
        maximumScale,
    )
    val adjustment = (targetScale / currentScale).coerceIn(0.01f, 100f)
    return adjustment.takeIf { abs(it - 1f) >= 0.01f }
}

internal fun shouldUseOverviewMode(fitContentWidth: Boolean): Boolean = !fitContentWidth

private fun openExternally(context: Context, uri: Uri): Boolean {
    val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
    if (scheme != "http" && scheme != "https") return true
    context.openExternalUrl(uri.toString())
    return true
}

private fun Int.toCssHex(): String =
    String.format(Locale.US, "#%06X", 0xFFFFFF and this)
