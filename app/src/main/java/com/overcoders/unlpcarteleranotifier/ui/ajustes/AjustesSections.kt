/**
 * Define las secciones y controles accesibles que componen la pantalla de ajustes.
 */
package com.overcoders.unlpcarteleranotifier.ui.ajustes

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.overcoders.unlpcarteleranotifier.BuildConfig
import com.overcoders.unlpcarteleranotifier.R
import com.overcoders.unlpcarteleranotifier.model.AvisoNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.CarteleraNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.CursadaNotificationTarget
import com.overcoders.unlpcarteleranotifier.push.FirebaseTopics
import com.overcoders.unlpcarteleranotifier.push.PushNotificationDispatcher
import com.overcoders.unlpcarteleranotifier.ui.common.openExternalUrl

private const val bugReportUrl = "https://forms.gle/jLNMnBGWsdQHLM9N8"
private const val repositoryUrl = "https://github.com/neftalito/Cartelera-UNLP-App"
private const val cafecitoUrl = "https://cafecito.app/neftalito"

@Composable
fun PushInfoCard(
    firebaseConfigured: Boolean,
    syncedTopics: Set<String>,
    lastSyncedInstallationId: String,
    notifyAll: Boolean,
    hideCancelledMateriasMessages: Boolean,
    notificationsAllowed: Boolean,
    notifyAllHighlightColor: Color,
    onNotifyAllChange: (Boolean) -> Unit,
    onHideCancelledChange: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (BuildConfig.DEBUG) {
                Text("Notificaciones push", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Ahora la app recibe avisos por Firebase Cloud Messaging. " +
                        "El servidor consulta cartelera y cursadas una sola vez, detecta cambios " +
                        "y los publica en el tópico general, en el tópico de cada materia " +
                        "o en el tópico global de avisos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (firebaseConfigured) {
                        "Firebase está configurado en esta instalación."
                    } else {
                        "Firebase todavía usa valores de ejemplo. Completa private-local.properties para activar el push real."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (firebaseConfigured) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                Text(
                    text = "Tópico general: ${FirebaseTopics.ALL_MATERIAS}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Tópico avisos: ${FirebaseTopics.AVISOS}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = if (syncedTopics.isEmpty()) {
                        "Topics sincronizados: todavía no hay ninguno persistido."
                    } else {
                        "Topics sincronizados: ${syncedTopics.sorted().joinToString()}"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                if (lastSyncedInstallationId.isNotBlank()) {
                    Text(
                        text = "Installation ID: $lastSyncedInstallationId",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                HorizontalDivider()
            }

            if (!notificationsAllowed) {
                Text(
                    text = "Las notificaciones están desactivadas para esta app.",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Activarlas en Android es necesario para recibir avisos de cartelera y cursadas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onOpenNotificationSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Abrir ajustes de notificaciones")
                }
                HorizontalDivider()
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val horizontalInset = 8.dp.toPx()
                        val verticalInset = 4.dp.toPx()
                        val cornerRadius = 12.dp.toPx()
                        drawRoundRect(
                            color = notifyAllHighlightColor,
                            topLeft = Offset(-horizontalInset, -verticalInset),
                            size = Size(
                                width = size.width + (horizontalInset * 2f),
                                height = size.height + (verticalInset * 2f)
                            ),
                            cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                        )
                    }
                    .toggleable(
                        value = notifyAll,
                        role = Role.Switch,
                        onValueChange = onNotifyAllChange,
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Recibir notificaciones de todas las materias")
                Switch(
                    checked = notifyAll,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors()
                )
                Text(
                    text = "Si desactivás esta opción, vas a poder suscribirte a materias específicas desde la pestaña de Suscripciones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = hideCancelledMateriasMessages,
                        role = Role.Switch,
                        onValueChange = onHideCancelledChange,
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Ocultar mensajes anulados en cartelera")
                Switch(
                    checked = hideCancelledMateriasMessages,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors()
                )
                Text(
                    text = "Si habilitás esta opción, la cartelera ocultará los mensajes anulados para que solo veas los mensajes válidos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

        }
    }
}

@Composable
fun DebugPushTestsCard(
    context: Context,
) {
    if (!BuildConfig.DEBUG) return

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Debug", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Estas pruebas muestran notificaciones locales en este dispositivo. " +
                    "No contactan al servidor ni envían nada a otros usuarios.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    PushNotificationDispatcher.showCarteleraNotification(
                        context = context,
                        target = CarteleraNotificationTarget(
                            materiaId = "123",
                            materia = "Materia de prueba",
                            titulo = "Anuncio de ejemplo",
                            fecha = "05/07/2026 21:00",
                            autor = "Servidor Firebase",
                            resumen = "Este push prueba el flujo nuevo de Firebase y la apertura dirigida hacia la cartelera.",
                            isAnulado = false
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Probar push de cartelera")
            }
            Button(
                onClick = {
                    PushNotificationDispatcher.showCursadaNotification(
                        context = context,
                        target = CursadaNotificationTarget(
                            materiaId = "123",
                            materia = "Materia de prueba",
                            fechaModificacion = "05/07/2026 21:00"
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Probar push de cursada")
            }
            Button(
                onClick = {
                    PushNotificationDispatcher.showAvisoNotification(
                        context = context,
                        target = AvisoNotificationTarget(
                            titulo = "Aviso general de prueba",
                            mensaje = "Este push prueba el nuevo topic de avisos para todas las instalaciones.",
                            autor = "Servidor Firebase",
                            fecha = "06/07/2026 01:00"
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Probar push de aviso")
            }
        }
    }
}

@Composable
fun ProjectLinksRow(
    context: Context,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FilledTonalIconButton(
                onClick = {
                    context.openExternalUrl(bugReportUrl)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = "Reportar errores o sugerir mejoras"
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Reportes",
                style = MaterialTheme.typography.labelSmall
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FilledTonalIconButton(
                onClick = {
                    context.openExternalUrl(repositoryUrl)
                }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_github),
                    contentDescription = "Abrir repositorio público"
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "GitHub",
                style = MaterialTheme.typography.labelSmall
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FilledTonalIconButton(
                onClick = {
                    context.openExternalUrl(cafecitoUrl)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.VolunteerActivism,
                    contentDescription = "Apoyar el proyecto"
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Donaciones",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
