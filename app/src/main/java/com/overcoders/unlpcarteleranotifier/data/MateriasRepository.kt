package com.overcoders.unlpcarteleranotifier.data

/**
 * Cachea la relación entre id de materia y nombre normalizado.
 *
 * Se usa sobre todo en el pipeline de notificaciones, donde algunos endpoints sólo exponen
 * nombres visibles y no el id original elegido por el usuario al suscribirse.
 */
object MateriasRepository {
    private var cachedIdToName: Map<String, String> = emptyMap()

    fun invalidateCache() {
        cachedIdToName = emptyMap()
    }

}
