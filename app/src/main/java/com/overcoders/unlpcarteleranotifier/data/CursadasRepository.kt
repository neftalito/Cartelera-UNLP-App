/** Actualiza y conserva el snapshot local de cursadas que consume la aplicación. */
package com.overcoders.unlpcarteleranotifier.data

import android.content.Context
import com.overcoders.unlpcarteleranotifier.model.CursadaInfo
import kotlinx.coroutines.CancellationException

/** Abstrae almacenamiento y red para probar el flujo completo sin depender de Android. */
internal interface CursadasSource {
    suspend fun loadCached(): List<CursadaInfo>
    suspend fun isCacheFresh(): Boolean
    suspend fun refresh(): List<CursadaInfo>
    suspend fun save(cursadas: List<CursadaInfo>)
}

/** Aplica la política de caché y conserva las cancelaciones como control de flujo. */
internal class CursadasLoader(
    private val source: CursadasSource,
) {
    suspend fun load(forceRefresh: Boolean = false): List<CursadaInfo> {
        val cached = try {
            source.loadCached()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        val cacheIsFresh = if (cached != null) {
            try {
                source.isCacheFresh()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }

        if (cached != null && !forceRefresh && cacheIsFresh) {
            return cached
        }

        val current = source.refresh()
        try {
            source.save(current)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // La respuesta remota sigue siendo válida aunque el snapshot no pueda guardarse.
        }
        return current
    }
}

/** Coordina la caché y la fuente remota de cursadas. */
object CursadasRepository {
    internal suspend fun load(
        context: Context,
        forceRefresh: Boolean = false,
    ): List<CursadaInfo> = CursadasLoader(
        AndroidCursadasSource(context.applicationContext)
    ).load(forceRefresh)
}

private class AndroidCursadasSource(
    private val context: Context,
    private val service: CursadasService = CursadasService(),
) : CursadasSource {
    override suspend fun loadCached(): List<CursadaInfo> = CursadasStore.load(context)

    override suspend fun isCacheFresh(): Boolean = CursadasStore.isFresh(
        context,
        ContentCachePolicy.CURSADAS_CACHE_TTL_MILLIS,
    )

    override suspend fun refresh(): List<CursadaInfo> = service.fetchAndParse()

    override suspend fun save(cursadas: List<CursadaInfo>) {
        CursadasStore.save(context, cursadas)
    }
}
