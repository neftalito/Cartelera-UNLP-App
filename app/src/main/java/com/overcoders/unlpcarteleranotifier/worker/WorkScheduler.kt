package com.overcoders.unlpcarteleranotifier.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.overcoders.unlpcarteleranotifier.data.SettingsStore
import java.util.concurrent.TimeUnit

/**
 * Encapsula la programación de trabajos periódicos y recuerda la última configuración
 * aplicada para evitar reenqueues innecesarios al abrir la app o reiniciar el dispositivo.
 */
object WorkScheduler {
    private const val WORK_NAME = "cartelera_worker"
    private const val CURSADAS_WORK_NAME = "cursadas_worker"

    suspend fun schedule(context: Context, enabled: Boolean, intervalMinutes: Int, wifiOnly: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }

        // WorkManager no admite períodos menores a 15 minutos para trabajos periódicos.
        val safeIntervalMinutes = intervalMinutes.coerceAtLeast(15)
        val lastScheduledInterval = SettingsStore.getLastScheduledInterval(context)
        val lastScheduledWifiOnly = SettingsStore.getLastScheduledWifiOnly(context)
        val networkType = if (wifiOnly) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val request = PeriodicWorkRequestBuilder<CarteleraWorker>(
            safeIntervalMinutes.toLong(),
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        // Si la configuración no cambió, KEEP conserva el trabajo existente y evita recrearlo.
        val policy = if (
            lastScheduledInterval == safeIntervalMinutes &&
            lastScheduledWifiOnly == wifiOnly
        ) {
            ExistingPeriodicWorkPolicy.KEEP
        } else {
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
        }

        manager.enqueueUniquePeriodicWork(
            WORK_NAME,
            policy,
            request
        )

        SettingsStore.setLastScheduledInterval(context, safeIntervalMinutes)
        SettingsStore.setLastScheduledWifiOnly(context, wifiOnly)
    }

    suspend fun scheduleCursadas(
        context: Context,
        enabled: Boolean,
        intervalMinutes: Int,
        wifiOnly: Boolean
    ) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(CURSADAS_WORK_NAME)
            return
        }

        // WorkManager no admite períodos menores a 15 minutos para trabajos periódicos.
        val safeIntervalMinutes = intervalMinutes.coerceAtLeast(15)
        val lastScheduledInterval = SettingsStore.getLastScheduledCursadasInterval(context)
        val lastScheduledWifiOnly = SettingsStore.getLastScheduledCursadasWifiOnly(context)
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val request = PeriodicWorkRequestBuilder<CursadasWorker>(
            safeIntervalMinutes.toLong(),
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        // Si la configuración no cambió, KEEP conserva el trabajo existente y evita recrearlo.
        val policy = if (
            lastScheduledInterval == safeIntervalMinutes &&
            lastScheduledWifiOnly == wifiOnly
        ) {
            ExistingPeriodicWorkPolicy.KEEP
        } else {
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
        }

        manager.enqueueUniquePeriodicWork(
            CURSADAS_WORK_NAME,
            policy,
            request
        )

        SettingsStore.setLastScheduledCursadasInterval(context, safeIntervalMinutes)
        SettingsStore.setLastScheduledCursadasWifiOnly(context, wifiOnly)
    }
}
