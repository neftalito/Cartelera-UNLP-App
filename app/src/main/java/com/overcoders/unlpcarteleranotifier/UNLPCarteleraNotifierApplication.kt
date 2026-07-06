package com.overcoders.unlpcarteleranotifier

import android.app.Application
import com.overcoders.unlpcarteleranotifier.push.FirebaseInitializer
import com.overcoders.unlpcarteleranotifier.push.FirebaseTopicSyncManager
import com.overcoders.unlpcarteleranotifier.worker.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class UNLPCarteleraNotifierApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        FirebaseInitializer.ensureInitialized(this)
        applicationScope.launch {
            WorkScheduler.cancelLegacyPolling(this@UNLPCarteleraNotifierApplication)
            FirebaseTopicSyncManager.sync(this@UNLPCarteleraNotifierApplication)
        }
    }
}
