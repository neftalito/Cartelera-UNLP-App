package com.overcoders.unlpcarteleranotifier

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.overcoders.unlpcarteleranotifier.model.Adjunto
import com.overcoders.unlpcarteleranotifier.model.CarteleraNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.CursadaInfo
import com.overcoders.unlpcarteleranotifier.model.CursadaNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.Mensaje
import com.overcoders.unlpcarteleranotifier.push.PushNotificationDispatcher
import com.overcoders.unlpcarteleranotifier.ui.theme.UNLPCarteleraNotifierTheme
import com.overcoders.unlpcarteleranotifier.worker.CursadasNotificationDispatcher
import com.overcoders.unlpcarteleranotifier.worker.NotificationDispatcher
import org.json.JSONArray

/**
 * Punto de entrada de la app.
 *
 * Su responsabilidad principal es traducir intents externos (por ejemplo, taps en
 * notificaciones) a un estado inicial que la UI de Compose pueda consumir de forma segura.
 */
class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    private val pendingNotificationMessage = mutableStateOf<Mensaje?>(null)
    private val pendingCarteleraTarget = mutableStateOf<CarteleraNotificationTarget?>(null)
    private val pendingCursadaNotification = mutableStateOf<CursadaInfo?>(null)
    private val pendingCursadaTarget = mutableStateOf<CursadaNotificationTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateNotificationPayload(intent)
        enableEdgeToEdge()
        setContent {
            UNLPCarteleraNotifierTheme {
                val message = pendingNotificationMessage.value
                val carteleraTarget = pendingCarteleraTarget.value
                val cursada = pendingCursadaNotification.value
                val cursadaTarget = pendingCursadaTarget.value
                UNLPCarteleraNotifierApp(
                    initialNotificationMessage = message,
                    initialCarteleraTarget = carteleraTarget,
                    initialCursada = cursada,
                    initialCursadaTarget = cursadaTarget,
                    onNotificationMessageConsumed = { pendingNotificationMessage.value = null },
                    onCarteleraTargetConsumed = { pendingCarteleraTarget.value = null },
                    onCursadaConsumed = { pendingCursadaNotification.value = null },
                    onCursadaTargetConsumed = { pendingCursadaTarget.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateNotificationPayload(intent)
    }

    override fun onStart() {
        super.onStart()
        checkAndRequestRequiredPermissions()
    }

    private fun checkAndRequestRequiredPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun updateNotificationPayload(intent: Intent?) {
        pendingNotificationMessage.value = mensajeFromIntent(intent)
        pendingCarteleraTarget.value = if (pendingNotificationMessage.value == null) {
            PushNotificationDispatcher.carteleraTargetFromIntent(intent)
        } else {
            null
        }
        pendingCursadaNotification.value = cursadaFromIntent(intent)
        pendingCursadaTarget.value = if (pendingCursadaNotification.value == null) {
            PushNotificationDispatcher.cursadaTargetFromIntent(intent)
        } else {
            null
        }
    }

    // Compatibilidad transitoria: reconstruye el mensaje completo desde extras del esquema
    // anterior. Se puede eliminar cuando ya no haga falta abrir notificaciones legacy.
    private fun mensajeFromIntent(intent: Intent?): Mensaje? {
        if (intent == null) return null
        if (
            intent.getStringExtra(CursadasNotificationDispatcher.EXTRA_TYPE) ==
            CursadasNotificationDispatcher.TYPE_CURSADA
        ) {
            return null
        }
        val titulo = intent.getStringExtra(NotificationDispatcher.EXTRA_TITULO) ?: return null
        val materia = intent.getStringExtra(NotificationDispatcher.EXTRA_MATERIA) ?: return null
        val cuerpoHtml =
            intent.getStringExtra(NotificationDispatcher.EXTRA_CUERPO_HTML) ?: return null
        val fecha = intent.getStringExtra(NotificationDispatcher.EXTRA_FECHA) ?: return null
        val autor = intent.getStringExtra(NotificationDispatcher.EXTRA_AUTOR) ?: return null
        val anulado = intent.getBooleanExtra(NotificationDispatcher.EXTRA_ANULADO, false)
        val adjuntos = parseAdjuntosFromIntent(intent)
        return Mensaje(
            materia = materia,
            titulo = titulo,
            cuerpoHtml = cuerpoHtml,
            fecha = fecha,
            autor = autor,
            isAnulado = anulado,
            adjuntos = adjuntos
        )
    }

    private fun parseAdjuntosFromIntent(intent: Intent): List<Adjunto> {
        val serializedAdjuntos = intent.getStringExtra(NotificationDispatcher.EXTRA_ADJUNTOS)
            ?: return emptyList()

        return try {
            val adjuntosArray = JSONArray(serializedAdjuntos)
            buildList(adjuntosArray.length()) {
                for (index in 0 until adjuntosArray.length()) {
                    val adjunto = adjuntosArray.optJSONObject(index) ?: continue
                    val nombre = adjunto.optString("nombre")
                    val publicPath = adjunto.optString("publicPath")
                    if (nombre.isNotEmpty() && publicPath.isNotEmpty()) {
                        add(Adjunto(nombre = nombre, publicPath = publicPath))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // Compatibilidad transitoria: abre notificaciones viejas de cursadas hasta que ya no
    // queden pendientes en instalaciones actualizadas.
    private fun cursadaFromIntent(intent: Intent?): CursadaInfo? {
        if (intent == null) return null
        if (
            intent.getStringExtra(CursadasNotificationDispatcher.EXTRA_TYPE) !=
            CursadasNotificationDispatcher.TYPE_CURSADA
        ) {
            return null
        }
        val materia = intent.getStringExtra(CursadasNotificationDispatcher.EXTRA_MATERIA) ?: return null
        val inicio = intent.getStringExtra(CursadasNotificationDispatcher.EXTRA_INICIO).orEmpty()
        val horarios = intent.getStringExtra(CursadasNotificationDispatcher.EXTRA_HORARIOS).orEmpty()
        val fecha =
            intent.getStringExtra(CursadasNotificationDispatcher.EXTRA_FECHA_MODIFICACION).orEmpty()

        return CursadaInfo(
            materia = materia,
            inicioCursadaHtml = inicio,
            horariosCursadaHtml = horarios,
            ultimaModificacion = fecha,
            ultimaModificacionEpochMillis = null
        )
    }
}
