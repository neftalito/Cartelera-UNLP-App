/**
 * Modela y entrega de forma atómica el destino pendiente de una notificación.
 */
package com.overcoders.unlpcarteleranotifier.push

import com.overcoders.unlpcarteleranotifier.model.AvisoNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.CarteleraNotificationTarget
import com.overcoders.unlpcarteleranotifier.model.CursadaNotificationTarget
import java.util.concurrent.atomic.AtomicReference

/** Entrega de forma interna y de un solo uso el destino asociado a un tap de notificación. */
internal sealed interface NotificationOpenTarget {
    data class Cartelera(val target: CarteleraNotificationTarget) : NotificationOpenTarget
    data class Cursada(val target: CursadaNotificationTarget) : NotificationOpenTarget
    data class Aviso(val target: AvisoNotificationTarget) : NotificationOpenTarget
}

enum class NotificationOpenKind {
    CARTELERA,
    CURSADA,
    AVISO,
}

internal val NotificationOpenTarget.kind: NotificationOpenKind
    get() = when (this) {
        is NotificationOpenTarget.Cartelera -> NotificationOpenKind.CARTELERA
        is NotificationOpenTarget.Cursada -> NotificationOpenKind.CURSADA
        is NotificationOpenTarget.Aviso -> NotificationOpenKind.AVISO
    }

/**
 * Conserva solamente el último tap pendiente. El consumidor lo retira atómicamente para que
 * una recreación de la actividad no vuelva a procesar la misma notificación.
 */
internal class NotificationOpenTargetStore {
    private val pendingTarget = AtomicReference<NotificationOpenTarget?>(null)

    fun publish(target: NotificationOpenTarget) {
        pendingTarget.set(target)
    }

    fun consume(): NotificationOpenTarget? = pendingTarget.getAndSet(null)
}

/** Punto de encuentro en memoria entre la actividad interna y la actividad principal. */
internal object NotificationOpenCoordinator {
    private val store = NotificationOpenTargetStore()

    fun publish(target: NotificationOpenTarget) = store.publish(target)

    fun consume(): NotificationOpenTarget? = store.consume()
}
