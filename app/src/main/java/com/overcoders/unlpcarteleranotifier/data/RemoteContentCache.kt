/** Almacena documentos remotos y respuestas negativas con vencimiento configurable. */
package com.overcoders.unlpcarteleranotifier.data

import android.content.Context
import android.util.Log
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class CachedValue<T>(
    val value: T,
    val savedAtEpochMillis: Long,
) {
    fun isFresh(ttlMillis: Long, nowEpochMillis: Long = System.currentTimeMillis()): Boolean =
        nowEpochMillis - savedAtEpochMillis in 0..ttlMillis
}

object ContentCachePolicy {
    const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
    const val STUDY_PLANS_TTL_MILLIS = 7L * DAY_MILLIS
    const val CURRENT_YEAR_TTL_MILLIS = DAY_MILLIS
    const val HISTORICAL_YEAR_TTL_MILLIS = 30L * DAY_MILLIS
    const val CATALOG_TTL_MILLIS = DAY_MILLIS
    const val SCHEDULE_TTL_MILLIS = 6L * 60L * 60L * 1_000L
    const val CURSADAS_CACHE_TTL_MILLIS = 5L * 60L * 1_000L
    const val EVENTUAL_RESERVATIONS_MEMORY_TTL_MILLIS = 2L * 60L * 1_000L
    const val AULAS_MEMORY_TTL_MILLIS = 60L * 1_000L
    const val NOT_FOUND_TTL_MILLIS = 6L * 60L * 60L * 1_000L
}

internal object RemoteContentCache {
    private const val SCHEMA_VERSION = 1
    private const val CACHE_DIRECTORY = "remote_content_v1"
    private val unsafeKeyCharacter = Regex("[^A-Za-z0-9._-]")

    private val memory = ConcurrentHashMap<String, CachedValue<String>>()
    private val mutex = Mutex()

    suspend fun read(context: Context, key: String): CachedValue<String>? =
        withContext(Dispatchers.IO) {
            memory[key] ?: mutex.withLock {
                memory[key] ?: readFromDisk(context, key)?.also { memory[key] = it }
            }
        }

    suspend fun <T> readDecoded(
        context: Context,
        key: String,
        decoder: (String) -> T,
    ): CachedValue<T>? {
        val cached = read(context, key) ?: return null
        return try {
            CachedValue(
                value = decoder(cached.value),
                savedAtEpochMillis = cached.savedAtEpochMillis
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Un payload incompatible no debe fallar en cada apertura: se descarta y se
            // permite que la siguiente carga lo regenere desde la fuente remota.
            try {
                removeIfUnchanged(context, key, cached)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("RemoteContentCache", "No se pudo descartar una caché inválida.", error)
            }
            null
        }
    }

    suspend fun write(
        context: Context,
        key: String,
        payload: String,
        savedAtEpochMillis: Long = System.currentTimeMillis(),
    ) = withContext(Dispatchers.IO) {
        val value = CachedValue(payload, savedAtEpochMillis)
        mutex.withLock {
            val envelope = JSONObject()
                .put("schemaVersion", SCHEMA_VERSION)
                .put("savedAt", savedAtEpochMillis)
                .put("payload", payload)

            val atomicFile = AtomicFile(cacheFile(context, key))
            val output = atomicFile.startWrite()
            try {
                output.write(envelope.toString().toByteArray(Charsets.UTF_8))
                atomicFile.finishWrite(output)
                memory[key] = value
            } catch (error: Exception) {
                atomicFile.failWrite(output)
                throw error
            }
        }
    }

    suspend fun remove(context: Context, key: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            memory.remove(key)
            AtomicFile(cacheFile(context, key)).delete()
        }
    }

    private suspend fun removeIfUnchanged(
        context: Context,
        key: String,
        expected: CachedValue<String>,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            // Otra corrutina puede haber reemplazado la entrada mientras se decodificaba.
            // En ese caso se conserva la versión nueva en lugar de borrar por la falla anterior.
            if (memory[key] == expected) {
                memory.remove(key)
                AtomicFile(cacheFile(context, key)).delete()
            }
        }
    }

    private fun readFromDisk(context: Context, key: String): CachedValue<String>? {
        val atomicFile = AtomicFile(cacheFile(context, key))
        if (!atomicFile.baseFile.exists()) return null

        return runCatching {
            val envelope = JSONObject(String(atomicFile.readFully(), Charsets.UTF_8))
            if (envelope.optInt("schemaVersion") != SCHEMA_VERSION) {
                atomicFile.delete()
                return null
            }
            val payload = envelope.optString("payload")
            val savedAt = envelope.optLong("savedAt", 0L)
            if (payload.isBlank() || savedAt <= 0L) {
                atomicFile.delete()
                return null
            }
            CachedValue(payload, savedAt)
        }.getOrElse {
            atomicFile.delete()
            null
        }
    }

    private fun cacheFile(context: Context, key: String): File {
        val directory = File(context.cacheDir, CACHE_DIRECTORY).apply { mkdirs() }
        val safeKey = key.replace(unsafeKeyCharacter, "_")
        return File(directory, "$safeKey.json")
    }
}

internal fun cacheKeyForUrl(prefix: String, url: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return "${prefix}_${digest.take(24)}"
}

object NotFoundCacheStore {
    suspend fun isFresh(context: Context, url: String): Boolean {
        return try {
            val cached = RemoteContentCache.read(
                context,
                cacheKeyForUrl("not_found", url)
            ) ?: return false
            cached.isFresh(ContentCachePolicy.NOT_FOUND_TTL_MILLIS)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "No se pudo leer la caché negativa.", error)
            false
        }
    }

    suspend fun mark(context: Context, url: String) {
        try {
            RemoteContentCache.write(
                context = context,
                key = cacheKeyForUrl("not_found", url),
                payload = url
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "No se pudo guardar la caché negativa.", error)
        }
    }

    suspend fun clear(context: Context, url: String) {
        try {
            RemoteContentCache.remove(context, cacheKeyForUrl("not_found", url))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "No se pudo limpiar la caché negativa.", error)
        }
    }

    private const val TAG = "NotFoundCacheStore"
}
