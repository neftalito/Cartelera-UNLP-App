/**
 * Aloja la UI principal, gestiona permisos y consume aperturas internas de notificaciones.
 */
package com.overcoders.unlpcarteleranotifier

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import com.overcoders.unlpcarteleranotifier.model.AvisoNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.CarteleraNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.CursadaNotificationTarget
import com.overcoders.unlpcarteleranotifier.navigation.UNLPCarteleraNotifierApp
import com.overcoders.unlpcarteleranotifier.push.NotificationOpenCoordinator
import com.overcoders.unlpcarteleranotifier.push.NotificationOpenKind
import com.overcoders.unlpcarteleranotifier.push.NotificationOpenTarget
import com.overcoders.unlpcarteleranotifier.push.kind
import com.overcoders.unlpcarteleranotifier.push.hasNotificationRuntimePermission
import com.overcoders.unlpcarteleranotifier.push.shouldRequestNotificationPermission
import com.overcoders.unlpcarteleranotifier.ui.theme.UNLPCarteleraNotifierTheme

internal fun shouldRestoreNotificationResolution(
    hasSelectedContent: Boolean,
    hasPendingTarget: Boolean,
    hasRestorableTarget: Boolean,
): Boolean = !hasSelectedContent && !hasPendingTarget && hasRestorableTarget

internal fun shouldInvalidatePendingNotification(
    eventId: Long,
    activeKind: NotificationOpenKind?,
    screenKind: NotificationOpenKind,
): Boolean = eventId > 0L && activeKind != null && activeKind != screenKind

/**
 * Punto de entrada de la app.
 *
 * Consume targets ya validados por la entrada interna de notificaciones y los entrega como
 * estado tipado a la UI de Compose.
 */
class MainActivity : ComponentActivity() {
    // Esta actividad usa ComponentActivity directamente y no depende de FragmentActivity.
    // La regla interpreta la ausencia de androidx.fragment como una versión antigua.
    @SuppressLint("InvalidFragmentVersionForActivityResult")
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    private val permissionPromptPreferences by lazy {
        getSharedPreferences(PERMISSION_PROMPT_PREFERENCES, MODE_PRIVATE)
    }
    private val pendingCarteleraTarget = mutableStateOf<CarteleraNotificationTarget?>(null)
    private val pendingCursadaTarget = mutableStateOf<CursadaNotificationTarget?>(null)
    private val pendingAvisoTarget = mutableStateOf<AvisoNotificationTarget?>(null)
    private val notificationOpenEventId = mutableLongStateOf(0L)
    private val activeNotificationKind = mutableStateOf<NotificationOpenKind?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumePendingNotificationTarget()
        enableEdgeToEdge()
        setContent {
            UNLPCarteleraNotifierTheme {
                val carteleraTarget = pendingCarteleraTarget.value
                val cursadaTarget = pendingCursadaTarget.value
                val avisoTarget = pendingAvisoTarget.value
                UNLPCarteleraNotifierApp(
                    initialCarteleraTarget = carteleraTarget,
                    initialCursadaTarget = cursadaTarget,
                    initialAvisoTarget = avisoTarget,
                    notificationOpenEventId = notificationOpenEventId.longValue,
                    activeNotificationKind = activeNotificationKind.value,
                    onCarteleraTargetConsumed = { pendingCarteleraTarget.value = null },
                    onCursadaTargetConsumed = { pendingCursadaTarget.value = null },
                    onAvisoTargetConsumed = { pendingAvisoTarget.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumePendingNotificationTarget()
    }

    override fun onStart() {
        super.onStart()
        checkAndRequestRequiredPermissions()
    }

    @SuppressLint("InlinedApi")
    private fun checkAndRequestRequiredPermissions() {
        val wasAlreadyRequested = permissionPromptPreferences.getBoolean(
            KEY_NOTIFICATION_PERMISSION_REQUESTED,
            false
        )
        if (
            !shouldRequestNotificationPermission(
                sdkInt = Build.VERSION.SDK_INT,
                runtimePermissionGranted = hasNotificationRuntimePermission(),
                wasAlreadyRequested = wasAlreadyRequested
            )
        ) {
            return
        }

        // Un rechazo no debe volver a abrir el diálogo cada vez que la app recupera el foco.
        // Ajustes conserva una entrada explícita a la configuración del sistema.
        permissionPromptPreferences.edit {
            putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, true)
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun consumePendingNotificationTarget() {
        val openTarget = NotificationOpenCoordinator.consume() ?: return
        when (openTarget) {
            is NotificationOpenTarget.Cartelera -> {
                pendingCarteleraTarget.value = openTarget.target
                pendingCursadaTarget.value = null
                pendingAvisoTarget.value = null
            }

            is NotificationOpenTarget.Cursada -> {
                pendingCarteleraTarget.value = null
                pendingCursadaTarget.value = openTarget.target
                pendingAvisoTarget.value = null
            }

            is NotificationOpenTarget.Aviso -> {
                pendingCarteleraTarget.value = null
                pendingCursadaTarget.value = null
                pendingAvisoTarget.value = openTarget.target
            }
        }
        activeNotificationKind.value = openTarget.kind
        notificationOpenEventId.longValue += 1L
    }

    private companion object {
        const val PERMISSION_PROMPT_PREFERENCES = "runtime_permission_prompts"
        const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
    }
}
