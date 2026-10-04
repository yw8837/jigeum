package com.moa.babylanguage

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Synthetic runtime measurement only. No private media, inferred labels or product claims. */
class AstDeviceBenchmarkTest {
    @Test fun measureSyntheticTenAndTwentySecondCalls() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit standalone benchmark only", args.getString("ast_device_probe") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.moa.babylanguage.astprobe")
        val runId = args.getString("ast_device_run_id") ?: error("Missing run ID")
        require(runId.matches(Regex("[a-f0-9]{32}")))
        val directory = File(context.filesDir, "qa-ast-device").apply { check(isDirectory || mkdirs()) }
        val output = File(directory, "$runId.json")
        check(!output.exists()) { "Run ID already measured" }
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        fun thermal(): Int = if (Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else -1
        val rows = JSONArray()
        val result = JSONObject().put("run_id", runId).put("status", "running")
            .put("model_sha256", AstModelStore.SHA256).put("device_model", Build.MODEL)
            .put("api", Build.VERSION.SDK_INT).put("abi", Build.SUPPORTED_ABIS.first())
            .put("synthetic_only", true).put("cases", rows)
        try {
            for ((index, duration) in listOf(10000, 20000, 20000).withIndex()) {
                val beforeThermal = thermal()
                check(beforeThermal < PowerManager.THERMAL_STATUS_SEVERE) { "Device already reports severe thermal status" }
                val samples = AstFeaturesTest.synthetic(duration, "tone")
                val sampling = AtomicBoolean(true)
                val peakPss = AtomicLong(Debug.getPss())
                val sampleCount = AtomicLong(1)
                val sampler = Thread({
                    while (sampling.get()) {
                        try { Thread.sleep(100) } catch (_: InterruptedException) { break }
                        if (!sampling.get()) break
                        peakPss.updateAndGet { previous -> maxOf(previous, Debug.getPss()) }
                        sampleCount.incrementAndGet()
                    }
                }, "ast-benchmark-pss").apply { isDaemon = true; start() }
                val started = SystemClock.elapsedRealtime()
                val analyzed = try { AstAudioAnalyzer.analyze(context, samples) } finally {
                    samples.fill(0f)
                    sampling.set(false)
                    sampler.interrupt()
                    sampler.join(2000)
                    check(!sampler.isAlive) { "PSS sampler did not terminate" }
                }
                val elapsed = SystemClock.elapsedRealtime() - started
                val afterThermal = thermal()
                val row = JSONObject().put("index", index).put("duration_ms", duration)
                    .put("input_samples", duration * 16).put("elapsed_ms", elapsed)
                    .put("peak_sampled_pss_kb", peakPss.get()).put("pss_samples", sampleCount.get())
                    .put("thermal_before", beforeThermal).put("thermal_after", afterThermal)
                    .put("problem", analyzed.problem ?: JSONObject.NULL)
                    .put("windows", JSONArray(analyzed.windows.map {
                        JSONObject().put("start_ms", it.startMs).put("end_ms", it.endMs)
                    }))
                rows.put(row)
                output.writeText(result.toString(2))
                check(analyzed.problem == null) { "AST analysis failed: ${analyzed.problem}" }
                check(analyzed.windows.map { it.startMs to it.endMs } ==
                    AstAudioAnalyzer.windowRanges(duration * 16).map { it.first * 1000L / 16000 to it.second * 1000L / 16000 }) {
                    "Incomplete selected interval"
                }
                check(afterThermal < PowerManager.THERMAL_STATUS_SEVERE) { "Device reached severe thermal status" }
            }
            result.put("status", "completed")
        } catch (error: Throwable) {
            result.put("status", "failed").put("error_type", error.javaClass.simpleName)
            throw error
        } finally { output.writeText(result.toString(2)) }
    }
}
