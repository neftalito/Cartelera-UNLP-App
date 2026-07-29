/** Abre enlaces externos y crea intents seguros para acciones del usuario. */
package com.overcoders.unlpcarteleranotifier.ui.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import java.net.URI

internal fun Context.openExternalUrl(url: String): Boolean {
    val normalizedUrl = normalizeExternalHttpUrl(url) ?: return false
    return runCatching {
        val intent = Intent(Intent.ACTION_VIEW, normalizedUrl.toUri())
        if (this !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        true
    }.getOrDefault(false)
}

internal fun Context.openPlayStoreListing(): Boolean {
    val listingUrl = playStoreWebUrl(packageName)
    val playStoreIntent = Intent(
        Intent.ACTION_VIEW,
        listingUrl.toUri()
    ).apply {
        setPackage("com.android.vending")
        if (this@openPlayStoreListing !is Activity) {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    return openWithFallback(
        primary = {
            runCatching {
                startActivity(playStoreIntent)
                true
            }.getOrDefault(false)
        },
        fallback = {
            openExternalUrl(listingUrl)
        },
    )
}

internal fun playStoreWebUrl(packageName: String): String =
    "https://play.google.com/store/apps/details?id=$packageName"

internal fun openWithFallback(
    primary: () -> Boolean,
    fallback: () -> Boolean,
): Boolean = primary() || fallback()

internal fun normalizeExternalHttpUrl(url: String): String? = runCatching {
    val normalized = url.trim()
    val uri = URI(normalized)
    val isHttp = uri.scheme.equals("http", ignoreCase = true) ||
        uri.scheme.equals("https", ignoreCase = true)
    normalized.takeIf { isHttp && !uri.host.isNullOrBlank() }
}.getOrNull()
