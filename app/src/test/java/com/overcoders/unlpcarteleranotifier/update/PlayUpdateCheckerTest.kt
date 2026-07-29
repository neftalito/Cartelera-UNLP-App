/** Verifica el criterio que decide si Google Play ofrece una versión más nueva. */
package com.overcoders.unlpcarteleranotifier.update

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class PlayUpdateCheckerTest {
    @Test
    fun returnsNewerVersionWhenPlayReportsAnUpdate() {
        assertEquals(
            PlayUpdateCheckResult.Available(28),
            playUpdateCheckResult(
                updateAvailability = UpdateAvailability.UPDATE_AVAILABLE,
                availableVersionCode = 28,
                installedVersionCode = 27,
            )
        )
    }

    @Test
    fun returnsNotAvailableWhenPlayReportsNoUpdate() {
        assertSame(
            PlayUpdateCheckResult.NotAvailable,
            playUpdateCheckResult(
                updateAvailability = UpdateAvailability.UPDATE_NOT_AVAILABLE,
                availableVersionCode = 28,
                installedVersionCode = 27,
            )
        )
    }

    @Test
    fun treatsEqualOrOlderVersionsAsNotAvailable() {
        assertSame(
            PlayUpdateCheckResult.NotAvailable,
            playUpdateCheckResult(
                updateAvailability = UpdateAvailability.UPDATE_AVAILABLE,
                availableVersionCode = 27,
                installedVersionCode = 27,
            )
        )
        assertSame(
            PlayUpdateCheckResult.NotAvailable,
            playUpdateCheckResult(
                updateAvailability = UpdateAvailability.UPDATE_AVAILABLE,
                availableVersionCode = 26,
                installedVersionCode = 27,
            )
        )
    }

    @Test
    fun returnsUnavailableForUnknownStateOrMissingVersion() {
        assertSame(
            PlayUpdateCheckResult.Unavailable,
            playUpdateCheckResult(
                updateAvailability = UpdateAvailability.UNKNOWN,
                availableVersionCode = null,
                installedVersionCode = 27,
            )
        )
        assertSame(
            PlayUpdateCheckResult.Unavailable,
            playUpdateCheckResult(
                updateAvailability = UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
                availableVersionCode = null,
                installedVersionCode = 27,
            )
        )
        assertSame(
            PlayUpdateCheckResult.Unavailable,
            playUpdateCheckResult(
                updateAvailability = Int.MAX_VALUE,
                availableVersionCode = null,
                installedVersionCode = 27,
            )
        )
        assertSame(
            PlayUpdateCheckResult.Unavailable,
            playUpdateCheckResult(
                updateAvailability = UpdateAvailability.UPDATE_AVAILABLE,
                availableVersionCode = null,
                installedVersionCode = 27,
            )
        )
    }

    @Test
    fun asyncBridgeMapsSuccessfulTask() = runBlocking {
        val source = TaskCompletionSource<Int>()
        source.setResult(28)

        assertEquals(
            PlayUpdateCheckResult.Available(28),
            awaitPlayUpdateTask(
                taskProvider = { source.task },
                transform = { PlayUpdateCheckResult.Available(it) },
            )
        )
    }

    @Test
    fun asyncBridgeTurnsTaskAndSynchronousFailuresIntoUnavailable() = runBlocking {
        val failedSource = TaskCompletionSource<Int>()
        failedSource.setException(IllegalStateException("Play unavailable"))

        assertSame(
            PlayUpdateCheckResult.Unavailable,
            awaitPlayUpdateTask(
                taskProvider = { failedSource.task },
                transform = { PlayUpdateCheckResult.Available(it) },
            )
        )
        assertSame(
            PlayUpdateCheckResult.Unavailable,
            awaitPlayUpdateTask<Int>(
                taskProvider = { error("Factory failed") },
                transform = { PlayUpdateCheckResult.Available(it) },
            )
        )
    }

    @Test
    fun lateTaskResultIsIgnoredAfterCoroutineCancellation() = runBlocking {
        val source = TaskCompletionSource<Int>()
        var transformCalled = false
        val waitingJob = launch {
            awaitPlayUpdateTask(
                taskProvider = { source.task },
                transform = {
                    transformCalled = true
                    PlayUpdateCheckResult.Available(it)
                },
            )
        }

        yield()
        waitingJob.cancelAndJoin()
        source.setResult(28)

        assertFalse(transformCalled)
    }
}
