/** Define cuándo consultar Google Play de nuevo sin perder resultados ya confirmados. */
package com.overcoders.unlpcarteleranotifier.update

internal const val PLAY_UPDATE_CHECK_TIMEOUT_MILLIS = 10_000L
internal const val PLAY_UPDATE_UNAVAILABLE_RETRY_MILLIS = 5L * 60L * 1_000L
internal const val PLAY_UPDATE_RECHECK_MILLIS = 6L * 60L * 60L * 1_000L

internal enum class PlayUpdateStatus {
    NOT_CHECKED,
    NOT_AVAILABLE,
    UNAVAILABLE,
    AVAILABLE,
}

internal enum class PlayUpdateCheckTrigger {
    RESUME,
    SCHEDULED,
}

internal data class PlayUpdateSnapshot(
    val status: PlayUpdateStatus,
    val availableVersionCode: Int?,
)

internal fun reducePlayUpdateResult(
    currentStatus: PlayUpdateStatus,
    currentAvailableVersionCode: Int?,
    result: PlayUpdateCheckResult,
): PlayUpdateSnapshot = when (result) {
    is PlayUpdateCheckResult.Available -> PlayUpdateSnapshot(
        status = PlayUpdateStatus.AVAILABLE,
        availableVersionCode = result.versionCode,
    )

    PlayUpdateCheckResult.NotAvailable -> PlayUpdateSnapshot(
        status = PlayUpdateStatus.NOT_AVAILABLE,
        availableVersionCode = null,
    )

    PlayUpdateCheckResult.Unavailable -> {
        if (
            currentStatus == PlayUpdateStatus.AVAILABLE &&
            currentAvailableVersionCode != null
        ) {
            PlayUpdateSnapshot(
                status = currentStatus,
                availableVersionCode = currentAvailableVersionCode,
            )
        } else {
            PlayUpdateSnapshot(
                status = PlayUpdateStatus.UNAVAILABLE,
                availableVersionCode = null,
            )
        }
    }
}

internal fun isAvailableUpdateNewerThanInstalled(
    availableVersionCode: Int?,
    installedVersionCode: Int,
): Boolean = availableVersionCode != null && availableVersionCode > installedVersionCode

internal fun shouldCheckForPlayUpdate(
    status: PlayUpdateStatus,
    lastCheckAtEpochMillis: Long?,
    nowEpochMillis: Long,
    isCheckInProgress: Boolean,
    trigger: PlayUpdateCheckTrigger,
): Boolean {
    if (isCheckInProgress) return false

    return when (status) {
        PlayUpdateStatus.NOT_CHECKED -> true
        PlayUpdateStatus.UNAVAILABLE ->
            trigger == PlayUpdateCheckTrigger.RESUME ||
                remainingPlayUpdateCheckDelayMillis(
                    status = status,
                    lastCheckAtEpochMillis = lastCheckAtEpochMillis,
                    nowEpochMillis = nowEpochMillis,
                ) == 0L

        PlayUpdateStatus.NOT_AVAILABLE,
        PlayUpdateStatus.AVAILABLE,
        ->
            remainingPlayUpdateCheckDelayMillis(
                status = status,
                lastCheckAtEpochMillis = lastCheckAtEpochMillis,
                nowEpochMillis = nowEpochMillis,
            ) == 0L
    }
}

internal fun remainingPlayUpdateCheckDelayMillis(
    status: PlayUpdateStatus,
    lastCheckAtEpochMillis: Long?,
    nowEpochMillis: Long,
): Long? {
    val interval = when (status) {
        PlayUpdateStatus.UNAVAILABLE -> PLAY_UPDATE_UNAVAILABLE_RETRY_MILLIS
        PlayUpdateStatus.NOT_AVAILABLE,
        PlayUpdateStatus.AVAILABLE,
        -> PLAY_UPDATE_RECHECK_MILLIS

        PlayUpdateStatus.NOT_CHECKED,
        -> return null
    }
    val checkedAt = lastCheckAtEpochMillis ?: return 0L
    val elapsed = nowEpochMillis - checkedAt
    if (elapsed < 0L) return 0L
    return (interval - elapsed).coerceAtLeast(0L)
}
