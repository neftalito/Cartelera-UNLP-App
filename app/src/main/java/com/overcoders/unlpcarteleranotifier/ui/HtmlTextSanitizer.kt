/** Convierte HTML en texto legible y seguro para mostrar o compartir. */
package com.overcoders.unlpcarteleranotifier.ui

import android.text.Html
import android.text.Spannable
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.URLSpan
import androidx.core.text.HtmlCompat
import androidx.core.text.parseAsHtml
import java.net.URI
import java.util.Locale

internal fun parseHtmlWithoutTextColors(
    html: String,
    imageGetter: Html.ImageGetter? = null,
): Spanned {
    val parsed = html.parseAsHtml(
        flags = HtmlCompat.FROM_HTML_MODE_LEGACY,
        imageGetter = imageGetter,
        tagHandler = null,
    )
    if (parsed is Spannable) {
        parsed.getSpans(0, parsed.length, ForegroundColorSpan::class.java).forEach { span ->
            parsed.removeSpan(span)
        }
        parsed.getSpans(0, parsed.length, BackgroundColorSpan::class.java).forEach { span ->
            parsed.removeSpan(span)
        }
        parsed.getSpans(0, parsed.length, URLSpan::class.java).forEach { span ->
            val scheme = runCatching {
                URI(span.url).scheme?.lowercase(Locale.US)
            }.getOrNull()
            if (scheme != "http" && scheme != "https") {
                parsed.removeSpan(span)
            }
        }
    }
    return parsed
}

internal fun htmlToShareText(html: String): String {
    val parsed = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
    val urlSpans = parsed.getSpans(0, parsed.length, URLSpan::class.java)
        .sortedBy { span -> parsed.getSpanStart(span) }

    val imagePlaceholder = "(no se puede copiar esta imagen)"

    if (urlSpans.isEmpty()) {
        return parsed.toString()
            .replace("\uFFFC", imagePlaceholder)
            .trim()
    }

    val out = StringBuilder()
    var cursor = 0

    urlSpans.forEach { span ->
        val start = parsed.getSpanStart(span).coerceAtLeast(cursor)
        val end = parsed.getSpanEnd(span).coerceAtLeast(start)
        if (start > cursor) {
            val segment = parsed.subSequence(cursor, start).toString()
                .replace("\uFFFC", imagePlaceholder)
            out.append(segment)
        }

        val visibleText = parsed.subSequence(start, end).toString()
            .replace("\uFFFC", imagePlaceholder)
        out.append(visibleText)

        val url = span.url?.trim().orEmpty()
        val visibleTextTrimmed = visibleText.trim()
        if (url.isNotEmpty() && !visibleTextTrimmed.equals(url, ignoreCase = true)) {
            if (visibleTextTrimmed.isBlank()) {
                out.append(url)
            } else {
                out.append(" (")
                out.append(url)
                out.append(")")
            }
        }

        cursor = end
    }

    if (cursor < parsed.length) {
        val finalSegment = parsed.subSequence(cursor, parsed.length).toString()
            .replace("\uFFFC", imagePlaceholder)
        out.append(finalSegment)
    }

    return out.toString().trim()
}
