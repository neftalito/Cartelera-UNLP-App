package com.overcoders.unlpcarteleranotifier.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class CarteleraWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        NotificationDispatcher.process(applicationContext)
        return Result.success()
    }
}
