/** Mantiene cachés en memoria con TTL para respuestas de uso transitorio. */
package com.overcoders.unlpcarteleranotifier.data

import com.overcoders.unlpcarteleranotifier.model.AulaEstado

object AulasMemoryCache {
    @Volatile
    private var cached: CachedValue<List<AulaEstado>>? = null

    fun load(): CachedValue<List<AulaEstado>>? = cached

    fun save(aulas: List<AulaEstado>) {
        cached = CachedValue(
            value = aulas,
            savedAtEpochMillis = System.currentTimeMillis()
        )
    }
}
