/** Consulta a Google Play si esta instalación tiene una versión más nueva disponible. */
package com.overcoders.unlpcarteleranotifier.update

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import com.overcoders.unlpcarteleranotifier.BuildConfig
import java.util.concurrent.Executor
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private val DIRECT_EXECUTOR = Executor(Runnable::run)

internal object PlayUpdateChecker {
    suspend fun check(context: Context): PlayUpdateCheckResult =
        awaitPlayUpdateTask(
            taskProvider = {
                AppUpdateManagerFactory.create(context.applicationContext).appUpdateInfo
            },
            transform = { info ->
                val availability = info.updateAvailability()
                playUpdateCheckResult(
                    updateAvailability = availability,
                    availableVersionCode = if (
                        availability == UpdateAvailability.UPDATE_AVAILABLE
                    ) {
                        info.availableVersionCode()
                    } else {
                        null
                    },
                    installedVersionCode = BuildConfig.VERSION_CODE,
                )
            },
        )
}

internal sealed interface PlayUpdateCheckResult {
    data class Available(val versionCode: Int) : PlayUpdateCheckResult

    data object NotAvailable : PlayUpdateCheckResult

    data object Unavailable : PlayUpdateCheckResult
}

internal fun playUpdateCheckResult(
    updateAvailability: Int,
    availableVersionCode: Int?,
    installedVersionCode: Int,
): PlayUpdateCheckResult = when {
    updateAvailability == UpdateAvailability.UPDATE_NOT_AVAILABLE ->
        PlayUpdateCheckResult.NotAvailable

    updateAvailability != UpdateAvailability.UPDATE_AVAILABLE ->
        PlayUpdateCheckResult.Unavailable

    availableVersionCode == null ->
        PlayUpdateCheckResult.Unavailable

    availableVersionCode > installedVersionCode ->
        PlayUpdateCheckResult.Available(availableVersionCode)

    else ->
        PlayUpdateCheckResult.NotAvailable
}

internal suspend fun <T> awaitPlayUpdateTask(
    taskProvider: () -> Task<T>,
    transform: (T) -> PlayUpdateCheckResult,
): PlayUpdateCheckResult = suspendCancellableCoroutine { continuation ->
    val task = try {
        taskProvider()
    } catch (_: Exception) {
        continuation.completeIfPending(PlayUpdateCheckResult.Unavailable)
        return@suspendCancellableCoroutine
    }

    try {
        task
            .addOnSuccessListener(DIRECT_EXECUTOR) { value ->
                if (!continuation.isActive) return@addOnSuccessListener
                val result = try {
                    transform(value)
                } catch (_: Exception) {
                    PlayUpdateCheckResult.Unavailable
                }
                continuation.completeIfPending(result)
            }
            .addOnFailureListener(DIRECT_EXECUTOR) {
                continuation.completeIfPending(PlayUpdateCheckResult.Unavailable)
            }
            .addOnCanceledListener(DIRECT_EXECUTOR) {
                continuation.completeIfPending(PlayUpdateCheckResult.Unavailable)
            }
    } catch (_: Exception) {
        continuation.completeIfPending(PlayUpdateCheckResult.Unavailable)
    }
}

private fun CancellableContinuation<PlayUpdateCheckResult>.completeIfPending(
    result: PlayUpdateCheckResult,
) {
    if (isActive) {
        resume(result)
    }
}
