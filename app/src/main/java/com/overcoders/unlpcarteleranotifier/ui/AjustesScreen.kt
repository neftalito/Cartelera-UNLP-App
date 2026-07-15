/**
 * Presenta los ajustes de notificaciones y persiste las preferencias de la aplicacion.
 */
package com.overcoders.unlpcarteleranotifier.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.overcoders.unlpcarteleranotifier.BuildConfig
import com.overcoders.unlpcarteleranotifier.HeaderAction
import com.overcoders.unlpcarteleranotifier.data.FirebaseTopicSyncStateStore
import com.overcoders.unlpcarteleranotifier.data.SettingsStore
import com.overcoders.unlpcarteleranotifier.push.FirebaseTopicSyncState
import com.overcoders.unlpcarteleranotifier.push.FirebaseClientConfig
import com.overcoders.unlpcarteleranotifier.push.canPostNotifications
import com.overcoders.unlpcarteleranotifier.push.openNotificationSettings
import com.overcoders.unlpcarteleranotifier.ui.ajustes.DebugPushTestsCard
import com.overcoders.unlpcarteleranotifier.ui.ajustes.ProjectLinksRow
import com.overcoders.unlpcarteleranotifier.ui.ajustes.PushInfoCard
import com.overcoders.unlpcarteleranotifier.ui.common.CenteredLoadingState
import com.overcoders.unlpcarteleranotifier.ui.common.userFacingError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
fun AjustesScreen(
    highlightNotifyAllTrigger: Int = 0,
    onHighlightNotifyAllConsumed: () -> Unit = {},
    onHeaderActionsChange: (List<HeaderAction>) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val notifyAllFlow = remember(context) { SettingsStore.notifyAllFlow(context) }
    val hideCancelledMessagesFlow = remember(context) {
        SettingsStore.hideCancelledMateriasMessagesFlow(context)
    }
    val notifyAll by notifyAllFlow.collectAsStateWithLifecycle(initialValue = true)
    val hideCancelledMateriasMessages by hideCancelledMessagesFlow
        .collectAsStateWithLifecycle(initialValue = false)
    val firebaseTopicSyncStateFlow = remember(context) {
        FirebaseTopicSyncStateStore.stateFlow(context)
    }
    val firebaseTopicSyncState by firebaseTopicSyncStateFlow.collectAsStateWithLifecycle(
        initialValue = FirebaseTopicSyncState()
    )

    var showNotifyAllHighlight by remember { mutableStateOf(false) }
    var areSettingsLoaded by remember { mutableStateOf(false) }
    var settingsError by remember { mutableStateOf<String?>(null) }
    var notificationsAllowed by remember(context) {
        mutableStateOf(context.canPostNotifications())
    }
    val settingsUpdateMutex = remember { Mutex() }

    val scrollState = rememberScrollState()
    val firebaseConfigured = remember { FirebaseClientConfig.isConfigured() }
    val notifyAllHighlightColor by animateColorAsState(
        targetValue = if (showNotifyAllHighlight) {
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.20f)
        } else {
            Color.Transparent
        },
        label = "notifyAllHighlightColor"
    )

    fun updateSetting(block: suspend () -> Unit) {
        scope.launch {
            settingsUpdateMutex.withLock {
                try {
                    withContext(Dispatchers.IO) { block() }
                    settingsError = null
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    settingsError = userFacingError(
                        operation = "guardar un ajuste",
                        error = error,
                    )
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onHeaderActionsChange(emptyList())
        onDispose { onHeaderActionsChange(emptyList()) }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsAllowed = context.canPostNotifications()
    }

    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) {
                SettingsStore.notifyAllFlow(context).first()
                SettingsStore.hideCancelledMateriasMessagesFlow(context).first()
            }
            if (BuildConfig.DEBUG) {
                FirebaseTopicSyncStateStore.load(context)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            settingsError = userFacingError(
                operation = "leer los ajustes guardados",
                error = error,
            )
        } finally {
            areSettingsLoaded = true
        }
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

    Box(modifier = Modifier.fillMaxSize()) {
        if (!areSettingsLoaded) {
            CenteredLoadingState(
                text = "Cargando ajustes…",
                modifier = Modifier.fillMaxSize(),
            )
            return@Box
        }

        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
        ) {
            if (settingsError != null) {
                Text(
                    text = settingsError.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            PushInfoCard(
                firebaseConfigured = firebaseConfigured,
                syncedTopics = firebaseTopicSyncState.topics,
                lastSyncedInstallationId = firebaseTopicSyncState.installationId,
                notifyAll = notifyAll,
                hideCancelledMateriasMessages = hideCancelledMateriasMessages,
                notificationsAllowed = notificationsAllowed,
                notifyAllHighlightColor = notifyAllHighlightColor,
                onNotifyAllChange = { enabled ->
                    updateSetting {
                        SettingsStore.setNotifyAll(context, enabled)
                    }
                    if (!enabled) {
                        showNotifyAllHighlight = false
                    }
                },
                onHideCancelledChange = { enabled ->
                    updateSetting {
                        SettingsStore.setHideCancelledMateriasMessages(context, enabled)
                    }
                },
                onOpenNotificationSettings = {
                    context.openNotificationSettings()
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
