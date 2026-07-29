/** Genera identificadores estables para reemplazar notificaciones equivalentes. */
package com.overcoders.unlpcarteleranotifier.push

internal fun stableNotificationId(type: String, vararg identityParts: String): Int =
    listOf(type, *identityParts).hashCode()
