package com.moa.babylanguage

import androidx.test.platform.app.InstrumentationRegistry
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.FloatBuffer
import java.util.concurrent.TimeUnit

class AstAudioAnalyzerTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun chronologicalCoverageIncludesLastWindow() {
        assertEquals(listOf(0 to 160_000), AstAudioAnalyzer.windowRanges(160_000))
        assertEquals(listOf(0 to 160_000, 80_000 to 240_000, 160_000 to 320_000), AstAudioAnalyzer.windowRanges(320_000))
        assertEquals(listOf(0 to 160_000, 80_000 to 240_000, 83_216 to 243_216), AstAudioAnalyzer.windowRanges(243_216))
        assertEquals(listOf(0 to 16_000), AstAudioAnalyzer.windowRanges(16_000))
        for (size in listOf(0, 15999, 320001))
            assertThrows(IllegalArgumentException::class.java) { AstAudioAnalyzer.windowRanges(size) }
    }

    @Test fun normalizedBoundsAndNonFiniteInputsFailExplicitly() = runBlocking {
        for (input in listOf(FloatArray(0), FloatArray(15999), FloatArray(320001),
            FloatArray(16000) { Float.NaN }, FloatArray(16000) { Float.POSITIVE_INFINITY },
            FloatArray(16000) { 1.0001f }, FloatArray(16000) { -1.0001f })) {
            val result = AstAudioAnalyzer.analyze(context, input)
            assertTrue(result.windows.isEmpty())
            assertNotNull(result.problem)
        }
    }

    @Test fun sigmoidIsIndependentPerClassAndRequiresFiniteLogits() {
        val logits = FloatArray(527) { 0f }.apply { this[0] = 1000f; this[1] = -1000f }
        val scores = AstAudioAnalyzer.scoresFromLogits(logits)
        assertEquals(527, scores.size)
        assertTrue(scores.containsKey("Babbling"))
        assertTrue(scores.containsKey("Hubbub, speech noise, speech babble"))
        assertEquals(1f, scores.values.elementAt(0), 0f)
        assertEquals(0f, scores.values.elementAt(1), 0f)
        assertEquals(0.5f, scores.getValue("Babbling"), 0f)
        assertTrue(scores.values.all { it.isFinite() && it in 0f..1f })
        assertThrows(IllegalArgumentException::class.java) { AstAudioAnalyzer.scoresFromLogits(FloatArray(526)) }
        assertThrows(IllegalArgumentException::class.java) { AstAudioAnalyzer.scoresFromLogits(FloatArray(527) { Float.NaN }) }
    }

    @Test fun syntheticInt8OrtLogitsMatchHostRuntimeGolden() {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val golden = JSONObject(testContext.assets.open("ast-int8-logits-golden.json").bufferedReader().use { it.readText() })
        assertEquals(AstModelStore.SHA256, golden.getString("model_sha256"))
        val environment = OrtEnvironment.getEnvironment()
        val model = AstModelStore.resolve(context) {}
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2)
            options.setInterOpNumThreads(1)
            options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            options.setMemoryPatternOptimization(false)
            options.setCPUArenaAllocator(false)
            environment.createSession(model.absolutePath, options).use { session ->
                val cases = golden.getJSONArray("cases")
                for (index in 0 until cases.length()) {
                    val case = cases.getJSONObject(index)
                    val features = AstFeatures.extract(AstFeaturesTest.synthetic(case.getInt("duration_ms"), case.getString("kind")))
                    OnnxTensor.createTensor(environment, FloatBuffer.wrap(features), longArrayOf(1, 1024, 128)).use { input ->
                        session.run(mapOf("input_values" to input)).use { result ->
                            val logits = (result.get(0) as OnnxTensor).floatBuffer
                            assertEquals(527, logits.remaining())
                            val expected = case.getJSONArray("logits")
                            for (j in 0 until 527) assertEquals("${case.getString("kind")}/${case.getInt("duration_ms")}/$j",
                                expected.getDouble(j).toFloat(), logits.get(), golden.getDouble("max_abs_tolerance").toFloat())
                        }
                    }
                }
            }
        }
    }

    @Test fun analyzerCancellationReleasesOwnerAndSubsequentRunSucceeds() = runBlocking {
        withTimeout(150_000) {
            val cancelled = launch(Dispatchers.Default) {
                AstAudioAnalyzer.analyze(context, AstFeaturesTest.synthetic(20000, "tone"))
            }
            delay(150)
            cancelled.cancelAndJoin()
            val result = AstAudioAnalyzer.analyze(context, AstFeaturesTest.synthetic(1000, "tone"))
            assertNull(result.problem)
            assertEquals(1, result.windows.size)
            assertEquals(0L, result.windows.single().startMs)
            assertEquals(1000L, result.windows.single().endMs)
            assertEquals(527, result.windows.single().scores.size)
        }
    }

    @Test fun cancellingNativeRunTerminatesAndFreshRunOptionsResume() = runBlocking {
        val model = AstModelStore.resolve(context) {}
        val environment = OrtEnvironment.getEnvironment()
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(1)
            environment.createSession(model.absolutePath, options).use { session ->
                val features = AstFeatures.extract(AstFeaturesTest.synthetic(10000, "tone"))
                OnnxTensor.createTensor(environment, FloatBuffer.wrap(features), longArrayOf(1, 1024, 128)).use { input ->
                    val job = Job()
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
                    AstAudioAnalyzer.NativeRunGuard(job, deadline).use { guard ->
                        val started = CompletableDeferred<Unit>()
                        val run = async(Dispatchers.Default) {
                            started.complete(Unit)
                            // Keep running until native termination, so a fast device cannot
                            // finish the only run before the cancelling coroutine is scheduled.
                            runCatching {
                                while (true) session.run(mapOf("input_values" to input), guard.options).use {}
                            }
                        }
                        started.await()
                        delay(60)
                        job.cancel()
                        val failed = withTimeout(30_000) { run.await() }
                        assertNotNull("Terminated native run must fail", failed.exceptionOrNull())
                    }
                    OrtSession.RunOptions().use { fresh ->
                        session.run(mapOf("input_values" to input), fresh).use { result ->
                            assertEquals(527, (result.get(0) as OnnxTensor).floatBuffer.remaining())
                        }
                    }
                }
            }
        }
    }

    @Test fun expiredDeadlineTerminatesNativeRunWithoutCancellingJob() = runBlocking {
        val model = AstModelStore.resolve(context) {}
        val environment = OrtEnvironment.getEnvironment()
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(1)
            environment.createSession(model.absolutePath, options).use { session ->
                val features = AstFeatures.extract(AstFeaturesTest.synthetic(1000, "tone"))
                OnnxTensor.createTensor(environment, FloatBuffer.wrap(features), longArrayOf(1, 1024, 128)).use { input ->
                    val job = Job()
                    AstAudioAnalyzer.NativeRunGuard(job, System.nanoTime() - 1).use { guard ->
                        val run = async(Dispatchers.Default) {
                            runCatching {
                                while (true) session.run(mapOf("input_values" to input), guard.options).use {}
                            }
                        }
                        val failed = withTimeout(10_000) { run.await() }
                        assertNotNull("Expired absolute deadline must terminate native inference", failed.exceptionOrNull())
                        assertTrue("Deadline must not cancel its caller's job", job.isActive)
                    }
                    OrtSession.RunOptions().use { fresh ->
                        session.run(mapOf("input_values" to input), fresh).use { result ->
                            assertEquals(527, (result.get(0) as OnnxTensor).floatBuffer.remaining())
                        }
                    }
                    job.cancel()
                }
            }
        }
    }

    @Test fun concurrentAnalysisHasOneOwnerAndKeepsTwentySecondCoverage() = runBlocking {
        withTimeout(150_000) {
            val first = async { AstAudioAnalyzer.analyze(context, AstFeaturesTest.synthetic(10000, "silence")) }
            val second = async { AstAudioAnalyzer.analyze(context, AstFeaturesTest.synthetic(20000, "silence")) }
            assertNull(first.await().problem)
            val result = second.await()
            assertNull(result.problem)
            assertEquals(listOf(0L to 10000L, 5000L to 15000L, 10000L to 20000L), result.windows.map { it.startMs to it.endMs })
        }
    }
}
