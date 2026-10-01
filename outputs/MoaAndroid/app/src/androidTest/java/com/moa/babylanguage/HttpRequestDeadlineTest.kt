package com.moa.babylanguage

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.net.InetAddress
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread

/** Real loopback TLS sockets, without allowing production HTTP or overriding verifier configuration. */
@RunWith(AndroidJUnit4::class)
class HttpRequestDeadlineTest {
    @Test fun slowDripCannotExtendOverallDeadlineDespiteIdleReads() {
        LoopbackTls(Mode.DRIP).use { server ->
            val connection = server.connection()
            connection.inputStream.use { input ->
                val deadline = HttpRequestDeadline(connection, deadlineMs = 500)
                val start = SystemClock.elapsedRealtime()
                try {
                    while (true) {
                        deadline.checkActive()
                        if (input.read() < 0) break
                    }
                    deadline.checkActive()
                    fail("A slow-drip response must be interrupted by its overall deadline")
                } catch (_: IOException) {
                    assertTrue(deadline.wasAborted)
                } finally { deadline.close(); connection.disconnect() }
                val elapsed = SystemClock.elapsedRealtime() - start
                assertTrue("The deadline should end the request promptly: $elapsed", elapsed in 300..2_000)
                assertTrue("Several bytes must have arrived within the idle timeout", server.dripBytes.get() >= 3)
                assertTrue(deadline.isClosed)
            }
        }
    }

    @Test fun coroutineCancellationDisconnectsAnAlreadyBlockedSocketRead() {
        LoopbackTls(Mode.STALL).use { server ->
            val connection = server.connection()
            connection.inputStream.use { input ->
                assertEquals('x'.code, input.read())
                val job = Job()
                val deadline = HttpRequestDeadline(connection, job, deadlineMs = 10_000)
                val start = SystemClock.elapsedRealtime()
                val canceller = thread(isDaemon = true) { Thread.sleep(150); job.cancel() }
                try {
                    input.read()
                    deadline.checkActive()
                    fail("Cancelled blocking reads cannot remain open")
                } catch (_: Exception) {
                    assertTrue(job.isCancelled)
                    assertTrue(deadline.wasAborted)
                } finally { deadline.close(); connection.disconnect(); canceller.join(2_000) }
                val elapsed = SystemClock.elapsedRealtime() - start
                assertTrue("Cancellation must disconnect before the five-second read timeout: $elapsed", elapsed < 2_000)
            }
        }
    }

    @Test fun successfulEarlyCloseCancelsTheTimerAndCancellationRegistration() {
        LoopbackTls(Mode.NORMAL).use { server ->
            val connection = server.connection()
            val job = Job()
            val deadline = HttpRequestDeadline(connection, job, deadlineMs = 500)
            try {
                val body = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                deadline.checkActive()
                assertEquals("ok", body)
            } finally { deadline.close(); connection.disconnect() }
            job.cancel()
            Thread.sleep(650)
            assertTrue(deadline.isClosed)
            assertFalse("Neither a cancelled registration nor a retired timer may abort a completed request", deadline.wasAborted)
        }
    }

    private enum class Mode { DRIP, STALL, NORMAL }
    private class LoopbackTls(private val mode: Mode) : AutoCloseable {
        val dripBytes = AtomicInteger()
        private val stop = CountDownLatch(1)
        private val fixture = KeyStore.getInstance("PKCS12").apply {
            // This fixture's private key is synthetic, test-only, and excluded from release assets.
            InstrumentationRegistry.getInstrumentation().context.assets.open("transport-deadline-test.p12").use {
                load(it, "test-fixture-only".toCharArray())
            }
        }
        private val expected = fixture.getCertificate("transport-test").encoded
        private val serverContext = SSLContext.getInstance("TLS").apply {
            val km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(fixture, "test-fixture-only".toCharArray())
            }
            init(km.keyManagers, null, null)
        }
        private val clientContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(object : X509TrustManager {
                override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    if (chain.isNullOrEmpty() || !MessageDigest.isEqual(chain[0].encoded, expected)) throw CertificateException("Not the isolated TLS test fixture")
                }
            }), null)
        }
        private val socket = serverContext.serverSocketFactory.createServerSocket(0, 1, InetAddress.getByName("127.0.0.1")) as SSLServerSocket
        private val serverThread = thread(name = "jigeum-loopback-test", isDaemon = true) {
            try {
                socket.accept().use { client ->
                    client.soTimeout = 3_000
                    val reader = client.getInputStream().bufferedReader(Charsets.US_ASCII)
                    while (true) { val line = reader.readLine() ?: return@thread; if (line.isEmpty()) break }
                    val output = client.getOutputStream()
                    val size = if (mode == Mode.NORMAL) 2 else 1_000
                    output.write("HTTP/1.1 200 OK\r\nContent-Length: $size\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    if (mode == Mode.NORMAL) { output.write("ok".toByteArray(Charsets.US_ASCII)); output.flush() }
                    else if (mode == Mode.STALL) {
                        output.write('x'.code); output.flush(); stop.await(10, TimeUnit.SECONDS)
                    } else {
                        repeat(100) {
                            if (stop.count == 0L) return@thread
                            output.write('x'.code); output.flush(); dripBytes.incrementAndGet()
                            Thread.sleep(70)
                        }
                    }
                }
            } catch (_: Exception) { /* Closing or deadline interruption is expected. */ }
        }
        fun connection(): HttpsURLConnection = (URL("https://127.0.0.1:${socket.localPort}/test").openConnection() as HttpsURLConnection).apply {
            sslSocketFactory = clientContext.socketFactory
            hostnameVerifier = javax.net.ssl.HostnameVerifier { host, _ -> host == "127.0.0.1" }
            connectTimeout = 3_000
            readTimeout = 5_000
            instanceFollowRedirects = false
        }
        override fun close() { stop.countDown(); socket.close(); serverThread.join(2_000) }
    }
}
