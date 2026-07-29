/** Verifica en Android que OkHttp lea y procese respuestas fuera del hilo principal. */
package com.overcoders.unlpcarteleranotifier.data

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HttpAwaitInstrumentedTest {
    @Test
    fun responseBodyIsReadOutsideTheMainThread() {
        val readThread = AtomicReference<Thread>()
        val body = RecordingResponseBody(readThread)
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            }
            .build()
        val request = Request.Builder()
            .url("https://example.test/content")
            .build()
        var result: String? = null

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = runBlocking { client.awaitBody(request) }
        }

        assertEquals("contenido", result)
        assertNotSame(Looper.getMainLooper().thread, readThread.get())
    }

    @Test
    fun responseParserRunsOutsideTheMainThread() {
        val parseThread = AtomicReference<Thread>()
        val client = recordingClient(AtomicReference())
        val request = Request.Builder()
            .url("https://example.test/content")
            .build()
        var result: Int? = null

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = runBlocking {
                client.awaitParsedBody(request) { content ->
                    parseThread.set(Thread.currentThread())
                    content.length
                }
            }
        }

        assertEquals(9, result)
        assertNotSame(Looper.getMainLooper().thread, parseThread.get())
    }

    private fun recordingClient(readThread: AtomicReference<Thread>): OkHttpClient {
        val body = RecordingResponseBody(readThread)
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            }
            .build()
    }
}

private class RecordingResponseBody(
    private val readThread: AtomicReference<Thread>,
) : ResponseBody() {
    private val source = object : ForwardingSource(Buffer().writeUtf8("contenido")) {
        override fun read(sink: Buffer, byteCount: Long): Long {
            readThread.compareAndSet(null, Thread.currentThread())
            return super.read(sink, byteCount)
        }
    }.buffer()

    override fun contentType(): MediaType? = null

    override fun contentLength(): Long = 9

    override fun source(): BufferedSource = source
}
