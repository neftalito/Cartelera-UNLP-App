/** Verifica en JVM la cancelación y el cierre seguro de llamadas OkHttp suspendidas. */
package com.overcoders.unlpcarteleranotifier.data

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.EventListener
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HttpAwaitTest {
    @Test
    fun cancellingCoroutineCancelsUnderlyingCall() = runBlocking {
        val requestStarted = CountDownLatch(1)
        val releaseRequest = CountDownLatch(1)
        val capturedCall = AtomicReference<Call>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                capturedCall.set(chain.call())
                requestStarted.countDown()
                releaseRequest.await(5, TimeUnit.SECONDS)
                throw IOException("Solicitud liberada después de cancelarse")
            }
            .build()
        val request = Request.Builder()
            .url("https://example.test/cancel")
            .build()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { client.awaitBody(request) }

        try {
            assertTrue(requestStarted.await(5, TimeUnit.SECONDS))
            job.cancelAndJoin()
            assertTrue(capturedCall.get().isCanceled())
        } finally {
            releaseRequest.countDown()
        }
    }

    @Test
    fun responseArrivingAfterCancellationIsClosed() = runBlocking {
        val body = CloseRecordingResponseBody()
        val request = Request.Builder()
            .url("https://example.test/late-response")
            .build()
        val call = ControllableCall(request)
        val job = launch(start = CoroutineStart.UNDISPATCHED) { call.awaitBody() }

        assertTrue(call.awaitEnqueued())
        job.cancelAndJoin()
        assertTrue(call.isCanceled())
        call.respond(
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body)
                .build()
        )

        assertTrue(body.awaitClosed())
    }

    @Test
    fun cancellingAfterHeadersCancelsCallAndClosesBlockedBody() = runBlocking {
        val body = CancellationBlockingResponseBody()
        val request = Request.Builder()
            .url("https://example.test/blocked-body")
            .build()
        val call = ControllableCall(request, onCancel = body::cancelRead)
        val job = launch(start = CoroutineStart.UNDISPATCHED) { call.awaitBody() }

        assertTrue(call.awaitEnqueued())
        val responseJob = launch(Dispatchers.IO) {
            call.respond(
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            )
        }

        assertTrue(body.awaitReadStarted())
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertTrue(call.isCanceled())
        assertTrue(body.awaitClosed())
        responseJob.join()
    }

    @Test
    fun unsuccessfulResponseThrowsApiExceptionAndClosesBody() = runBlocking {
        val body = CloseRecordingResponseBody()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(503)
                    .message("Service Unavailable")
                    .body(body)
                    .build()
            }
            .build()
        val request = Request.Builder()
            .url("https://example.test/unavailable")
            .build()

        try {
            client.awaitBody(request)
            fail("Se esperaba ApiException")
        } catch (error: ApiException) {
            assertEquals(503, error.httpCode)
        }
        assertTrue(body.awaitClosed())
    }
}

private class ControllableCall(
    private val originalRequest: Request,
    private val onCancel: () -> Unit = {},
) : Call {
    private val callback = AtomicReference<Callback>()
    private val enqueued = CountDownLatch(1)
    private val executed = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)

    override fun request(): Request = originalRequest

    override fun execute(): Response = error("Sólo se admite enqueue en esta prueba")

    override fun enqueue(responseCallback: Callback) {
        check(executed.compareAndSet(false, true))
        callback.set(responseCallback)
        enqueued.countDown()
    }

    override fun cancel() {
        cancelled.set(true)
        onCancel()
    }

    override fun isExecuted(): Boolean = executed.get()

    override fun isCanceled(): Boolean = cancelled.get()

    override fun timeout(): Timeout = Timeout.NONE

    override fun addEventListener(eventListener: EventListener) = Unit

    override fun <T : Any> tag(type: KClass<T>): T? = null

    override fun <T> tag(type: Class<out T>): T? = null

    override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T =
        computeIfAbsent()

    override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T =
        computeIfAbsent()

    public override fun clone(): Call = ControllableCall(originalRequest, onCancel)

    fun awaitEnqueued(): Boolean = enqueued.await(5, TimeUnit.SECONDS)

    fun respond(response: Response) {
        callback.get().onResponse(this, response)
    }
}

private class CancellationBlockingResponseBody : ResponseBody() {
    private val readStarted = CountDownLatch(1)
    private val releaseRead = CountDownLatch(1)
    private val closed = CountDownLatch(1)
    private val cancelled = AtomicBoolean(false)
    private val source = object : ForwardingSource(Buffer()) {
        override fun read(sink: Buffer, byteCount: Long): Long {
            readStarted.countDown()
            check(releaseRead.await(5, TimeUnit.SECONDS)) {
                "La lectura no fue liberada por la cancelación."
            }
            if (cancelled.get()) {
                throw IOException("Lectura cancelada")
            }
            return -1L
        }

        override fun close() {
            closed.countDown()
            super.close()
        }
    }.buffer()

    override fun contentType(): MediaType? = null

    override fun contentLength(): Long = -1L

    override fun source(): BufferedSource = source

    fun cancelRead() {
        cancelled.set(true)
        releaseRead.countDown()
    }

    fun awaitReadStarted(): Boolean = readStarted.await(5, TimeUnit.SECONDS)

    fun awaitClosed(): Boolean = closed.await(5, TimeUnit.SECONDS)
}

private class CloseRecordingResponseBody : ResponseBody() {
    private val closed = CountDownLatch(1)
    private val source = object : ForwardingSource(Buffer().writeUtf8("contenido")) {
        override fun close() {
            closed.countDown()
            super.close()
        }
    }.buffer()

    override fun contentType(): MediaType? = null

    override fun contentLength(): Long = 9

    override fun source(): BufferedSource = source

    fun awaitClosed(): Boolean = closed.await(5, TimeUnit.SECONDS)
}
