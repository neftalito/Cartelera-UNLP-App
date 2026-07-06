package com.overcoders.unlpcarteleranotifier.worker

/**
 * Contenedor de extras legacy para abrir notificaciones locales del esquema anterior.
 *
 * El despacho real de notificaciones ya no vive acá: ahora sale del backend por FCM y el
 * cliente usa `PushNotificationDispatcher`. Este objeto debería eliminarse junto con el
 * parsing legacy de `MainActivity` cuando ya no haga falta soportar aperturas de anuncios viejos.
 */
object NotificationDispatcher {
    const val EXTRA_MATERIA = "extra_materia"
    const val EXTRA_TITULO = "extra_titulo"
    const val EXTRA_CUERPO_HTML = "extra_cuerpo_html"
    const val EXTRA_FECHA = "extra_fecha"
    const val EXTRA_AUTOR = "extra_autor"
    const val EXTRA_ANULADO = "extra_anulado"
    const val EXTRA_ADJUNTOS = "extra_adjuntos"

}
