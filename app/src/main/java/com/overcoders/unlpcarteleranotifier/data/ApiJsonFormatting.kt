/** Centraliza validaciones y conversiones seguras de campos JSON del API. */
package com.overcoders.unlpcarteleranotifier.data

import java.util.Locale
import org.json.JSONObject

internal fun formatJsonHour(hourObject: JSONObject?, fallback: String): String {
    val hour = hourObject?.optString("h")?.trim()?.toIntOrNull()
    val minute = hourObject?.optString("m")?.trim()?.toIntOrNull()
    if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) {
        return fallback
    }
    return String.format(Locale.US, "%02d:%02d", hour, minute)
}
