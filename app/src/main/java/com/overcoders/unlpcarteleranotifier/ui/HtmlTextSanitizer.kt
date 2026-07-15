/** Convierte HTML en texto legible y seguro para mostrar o compartir. */
package com.overcoders.unlpcarteleranotifier.ui

import android.text.Spannable
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.URLSpan
import androidx.core.text.HtmlCompat
import java.net.URI
import java.util.Locale

internal fun parseHtmlWithoutTextColors(html: String): Spanned {
    val parsed = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
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

    if (urlSpans.isEmpty()) {
        return parsed.toString().trim()
    }

    val out = StringBuilder()
    var cursor = 0

    urlSpans.forEach { span ->
        val start = parsed.getSpanStart(span).coerceAtLeast(cursor)
        val end = parsed.getSpanEnd(span).coerceAtLeast(start)
        if (start > cursor) {
            out.append(parsed.subSequence(cursor, start))
        }

        val visibleText = parsed.subSequence(start, end).toString()
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
        out.append(parsed.subSequence(cursor, parsed.length))
    }

    return out.toString().trim()
}
