@file:Suppress("AssignedValueIsNeverRead")

package com.overcoders.unlpcarteleranotifier.ui

import android.annotation.SuppressLint
import android.os.PowerManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.overcoders.unlpcarteleranotifier.BuildConfig
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.data.SettingsStore
import com.overcoders.unlpcarteleranotifier.push.FirebaseClientConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("BatteryLife")
@Composable
fun AjustesScreen(
    highlightNotifyAllTrigger: Int = 0,
    onHighlightNotifyAllConsumed: () -> Unit = {},
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val powerManager = remember { context.getSystemService(PowerManager::class.java) }

    val notifyAll by SettingsStore.notifyAllFlow(context).collectAsState(initial = true)
    val hideCancelledMateriasMessages by SettingsStore.hideCancelledMateriasMessagesFlow(context)
        .collectAsState(initial = false)

    var isIgnoringBatteryOptimizations by remember { mutableStateOf(false) }
    var showNotifyAllHighlight by remember { mutableStateOf(false) }
    var areSettingsLoaded by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val firebaseConfigured = remember { FirebaseClientConfig.isConfigured() }
    var syncedTopics by remember { mutableStateOf<Set<String>>(emptySet()) }
    var lastSyncedInstallationId by remember { mutableStateOf("") }
    val notifyAllHighlightColor by animateColorAsState(
        targetValue = if (showNotifyAllHighlight) {
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.20f)
        } else {
            Color.Transparent
        },
        label = "notifyAllHighlightColor"
    )

    suspend fun refreshDebugPushState() {
        syncedTopics = SettingsStore.getLastSyncedFirebaseTopics(context)
        lastSyncedInstallationId = SettingsStore.getLastSyncedFirebaseInstallationId(context)
    }

    DisposableEffect(Unit) {
        onHeaderActionsChange(emptyList())
        onDispose { onHeaderActionsChange(emptyList()) }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            SettingsStore.notifyAllFlow(context).first()
            SettingsStore.hideCancelledMateriasMessagesFlow(context).first()
        }
        if (BuildConfig.DEBUG) {
            refreshDebugPushState()
        }
        areSettingsLoaded = true
    }

    LaunchedEffect(highlightNotifyAllTrigger) {
        if (highlightNotifyAllTrigger > 0) {
            try {
                scrollState.animateScrollTo(0)
                showNotifyAllHighlight = true
                delay(2500)
            } finally {
                showNotifyAllHighlight = false
                onHighlightNotifyAllConsumed()
            }
        }
    }

    LaunchedEffect(powerManager) {
        if (powerManager != null) {
            isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(
                context.packageName
            )
        }
    }

    DisposableEffect(lifecycleOwner, powerManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && powerManager != null) {
                isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(
                    context.packageName
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!areSettingsLoaded) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Box
        }

        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            PushInfoCard(
                firebaseConfigured = firebaseConfigured,
                syncedTopics = syncedTopics,
                lastSyncedInstallationId = lastSyncedInstallationId,
                notifyAll = notifyAll,
                hideCancelledMateriasMessages = hideCancelledMateriasMessages,
                notifyAllHighlightColor = notifyAllHighlightColor,
                isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                onNotifyAllChange = { enabled ->
                    scope.launch(Dispatchers.IO) {
                        SettingsStore.setNotifyAll(context, enabled)
                    }
                    if (!enabled) {
                        showNotifyAllHighlight = false
                    }
                },
                onHideCancelledChange = { enabled ->
                    scope.launch(Dispatchers.IO) {
                        SettingsStore.setHideCancelledMateriasMessages(context, enabled)
                    }
                },
                onOpenBatterySettings = {
                    context.startActivity(
                        batteryIntent(
                            context = context,
                            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations
                        )
                    )
                }
            )

            DebugPushTestsCard(context = context)

            if (BuildConfig.DEBUG) {
                Spacer(Modifier.height(8.dp))
            }

            ProjectLinksRow(context = context)

            Spacer(Modifier.height(28.dp))
        }

        ScrollMoreHint(
            scrollState = scrollState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}
