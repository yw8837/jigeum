package com.moa.babylanguage

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.os.Debug
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import research.efficientsed.EfficientSedFrontend
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.security.MessageDigest
import kotlin.math.abs

/** Staged in androidTest only by the local research runner. No product integration. */
class EfficientSedAndroidProbeTest {
    @Test fun fixedModelAndOfficialFrontendParityThenNativeTerminationRecovery() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("sed_probe") == "true") { "Explicit isolated research run required" }
        val id = requireNotNull(args.getString("sed_run_id"))
        require(id.matches(Regex("[a-f0-9]{32}")))
        val base = File(instrumentation.targetContext.filesDir, "qa-sed/$id")
        val manifest = JSONObject(File(base, "manifest.json").readText())
        val model = File(base, "mel.onnx")
        assertEquals(20_315_630L, model.length())
        val digest = MessageDigest.getInstance("SHA-256")
        model.inputStream().use { stream ->
            val bytes = ByteArray(65536)
            while (true) { val count = stream.read(bytes); if (count < 0) break; digest.update(bytes, 0, count) }
        }
        assertEquals("0a1bf37323cb9069d26a63b0ca4486c9c027b29e3bff087e0acd7270022716fc",
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
        fun floats(name: String, size: Int): FloatArray {
            require(name.matches(Regex("[a-zA-Z0-9_-]+\\.f32")))
            val file = File(base, name)
            assertEquals(size * 4L, file.length())
            val bytes = file.readBytes()
            return try { FloatArray(size).also { ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(it) } }
            finally { bytes.fill(0) }
        }
        val report = JSONObject().put("model_sha256", manifest.getString("model_sha256"))
            .put("device_abi", android.os.Build.SUPPORTED_ABIS.first()).put("pss_before_kb", Debug.getPss())
            .put("quality_validated", false)
        val rows = JSONArray()
        val env = OrtEnvironment.getEnvironment()
        val loadStart = System.nanoTime()
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2); options.setInterOpNumThreads(1)
            options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            options.setMemoryPatternOptimization(false); options.setCPUArenaAllocator(false)
            env.createSession(model.absolutePath, options).use { session ->
                report.put("load_ms", (System.nanoTime() - loadStart) / 1e6)
                assertEquals(1, session.inputNames.size)
                val inputName = session.inputNames.single()
                val cases = manifest.getJSONArray("cases")
                assertTrue(cases.length() >= 7)
                for (i in 0 until cases.length()) {
                    val case = cases.getJSONObject(i); val name = case.getString("id")
                    val pcm = floats("$name-pcm.f32", 160000)
                    val start = System.nanoTime()
                    val mel = try { EfficientSedFrontend.extract(pcm) } finally { pcm.fill(0f) }
                    val frontendMs = (System.nanoTime() - start) / 1e6
                    val expected = floats("$name-mel.f32", 128000)
                    var maximum = 0f
                    for (j in mel.indices) {
                        assertTrue(mel[j].isFinite()); assertTrue(expected[j].isFinite())
                        maximum = maxOf(maximum, abs(mel[j] - expected[j]))
                    }
                    expected.fill(0f)
                    assertTrue("$name mel error $maximum", maximum <= 2e-4f)
                    val row = JSONObject().put("id", name).put("mel_max_abs", maximum.toDouble()).put("frontend_ms", frontendMs)
                    try {
                        OnnxTensor.createTensor(env, FloatBuffer.wrap(mel), longArrayOf(1, 1, 128, 1000)).use { input ->
                            val runStart = System.nanoTime()
                            session.run(mapOf(inputName to input)).use { result ->
                                row.put("inference_ms", (System.nanoTime() - runStart) / 1e6)
                                assertEquals(2, result.size())
                                for ((index, suffix, shape) in listOf(
                                    Triple(0, "strong", longArrayOf(1, 447, 250)), Triple(1, "weak", longArrayOf(1, 447)))) {
                                    val tensor = result.get(index) as OnnxTensor
                                    assertArrayEquals(shape, tensor.info.shape)
                                    val values = tensor.floatBuffer
                                    val oracle = floats("$name-$suffix.f32", if (index == 0) 447 * 250 else 447)
                                    var maxError = 0f
                                    for (j in oracle.indices) {
                                        val actual = values.get(); assertTrue(actual.isFinite()); assertTrue(oracle[j].isFinite())
                                        val error = abs(actual - oracle[j]); maxError = maxOf(maxError, error)
                                        assertTrue("$name/$suffix/$j error $error", error <= 1e-4f + 1e-3f * abs(oracle[j]))
                                    }
                                    oracle.fill(0f); row.put(suffix + "_max_abs", maxError.toDouble())
                                }
                            }
                            if (i == 0) {
                                // Exercise real ORT termination and a fresh run on the same graph.
                                OrtSession.RunOptions().use { cancelled ->
                                    cancelled.setTerminate(true)
                                    assertTrue(runCatching { session.run(mapOf(inputName to input), cancelled).use {} }.isFailure)
                                }
                                OrtSession.RunOptions().use { fresh ->
                                    session.run(mapOf(inputName to input), fresh).use { assertEquals(2, it.size()) }
                                }
                                report.put("native_preterminated_and_recovered", true)
                            }
                        }
                    } finally { mel.fill(0f) }
                    row.put("pss_after_kb", Debug.getPss()); rows.put(row)
                }
            }
        }
        val cancelledPcm = FloatArray(160000)
        try { assertTrue(runCatching { EfficientSedFrontend.extract(cancelledPcm) { true } }.isFailure) }
        finally { cancelledPcm.fill(0f) }
        report.put("frontend_cancellation", true).put("cases", rows).put("status", "pass")
            .put("pss_after_close_kb", Debug.getPss())
        File(base, "result.json").writeText(report.toString(2))
    }
}
