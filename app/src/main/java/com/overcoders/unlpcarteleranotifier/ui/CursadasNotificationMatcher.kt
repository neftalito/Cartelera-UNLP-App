/**
 * Resuelve una notificación de cursada contra el snapshot remoto vigente.
 */
package com.overcoders.unlpcarteleranotifier.ui

import com.overcoders.unlpcarteleranotifier.model.CursadaInfo
import com.overcoders.unlpcarteleranotifier.model.CursadaNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.cursadaMateriaKey
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val cursadaDisplayDateFormatter = DateTimeFormatter.ofPattern(
    "dd/MM/yyyy HH:mm",
    Locale.Builder()
        .setLanguage("es")
        .setRegion("AR")
        .build(),
)
private val cursadaZone: ZoneId = ZoneId.of("America/Argentina/Buenos_Aires")

internal fun findCursadaForNotification(
    cursadas: List<CursadaInfo>,
    target: CursadaNotificationTarget,
): CursadaInfo? = cursadas.firstOrNull { cursada ->
    cursada.materia.cursadaMateriaKey() == target.materia.cursadaMateriaKey() &&
        isCursadaVersionAtLeast(cursada, target.fechaModificacion)
}

internal fun isCursadaVersionAtLeast(
    cursada: CursadaInfo,
    targetDate: String,
): Boolean {
    val normalizedTargetDate = targetDate.trim()
    if (normalizedTargetDate.isEmpty()) return true

    val currentEpoch = cursada.ultimaModificacionEpochMillis
        ?: parseCursadaDate(cursada.ultimaModificacion)
        ?: return false
    val targetEpoch = parseCursadaDate(normalizedTargetDate) ?: return false
    return currentEpoch >= targetEpoch
}

private fun parseCursadaDate(value: String): Long? {
    val normalized = value.trim()
    if (normalized.isEmpty()) return null

    return runCatching { Instant.parse(normalized).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(normalized).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { ZonedDateTime.parse(normalized).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching {
            LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(cursadaZone)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
        ?: runCatching {
            LocalDateTime.parse(normalized, cursadaDisplayDateFormatter)
                .atZone(cursadaZone)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
}
