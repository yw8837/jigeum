package com.moa.babylanguage

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Shared transport guard, without any endpoint, credential or entitlement override. */
@OptIn(InternalCoroutinesApi::class)
internal class HttpRequestDeadline(
    private val connection: HttpURLConnection,
    private val job: Job? = null,
    deadlineMs: Long = 35_000
) : AutoCloseable {
    private val finished = AtomicBoolean(false)
    private val aborted = AtomicBoolean(false)
    private val durationMs = deadlineMs.also { require(it in 1..120_000) }
    private val endElapsed = SystemClock.elapsedRealtime() + durationMs
    private val timer = scheduler.schedule({ abort() }, durationMs, TimeUnit.MILLISECONDS)
    // The cancelling hook runs before completion; a blocking Java read must be
    // disconnected even while the IO coroutine has not reached its finally block.
    private val cancellation: DisposableHandle? = job?.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { cause ->
        if (cause is CancellationException || job?.isCancelled == true) abort()
    }

    private fun abort() {
        if (finished.get()) return
        if (!aborted.compareAndSet(false, true)) return
        // Android disconnect closes the engine/socket, unlike coroutine cancellation alone.
        runCatching { connection.disconnect() }
    }

    fun checkActive() {
        if (job?.isCancelled == true) {
            abort()
            throw CancellationException("HTTP request cancelled")
        }
        if (aborted.get() || SystemClock.elapsedRealtime() >= endElapsed) {
            abort()
            throw SocketTimeoutException("HTTP request deadline exceeded")
        }
    }

    override fun close() {
        finished.set(true)
        timer.cancel(false)
        cancellation?.dispose()
    }

    internal val isClosed: Boolean get() = finished.get()
    internal val wasAborted: Boolean get() = aborted.get()

    companion object {
        // Two app verification paths share one daemon timer; cancelled tasks leave no retained queue entries.
        private val scheduler = ScheduledThreadPoolExecutor(1) { task ->
            Thread(task, "jigeum-http-deadline").apply { isDaemon = true }
        }.apply { removeOnCancelPolicy = true }
    }
}
