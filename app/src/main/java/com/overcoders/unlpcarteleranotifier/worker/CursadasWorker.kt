package com.overcoders.unlpcarteleranotifier.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class CursadasWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        CursadasNotificationDispatcher.process(applicationContext)
        return Result.success()
    }
}
