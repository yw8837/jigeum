package com.moa.babylanguage

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Actual AV route on one synthetic fixture. Runtime evidence is separate from quality. */
class AstIntegratedPerformanceTest {
    @Test fun measureActualVisualTenAndTwentySecondCalls() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit integrated AV performance probe only",
            args.getString("ast_integrated_performance") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName == "com.moa.babylanguage.astcandidate" && BuildConfig.AST_INTEGRATED_CANDIDATE)
        val runId = args.getString("ast_integrated_performance_run_id") ?: error("Missing performance run ID")
        require(runId.matches(Regex("[a-f0-9]{32}")))
        val directory = File(context.filesDir, "qa-ast-integrated-performance").canonicalFile
        require(directory.parentFile == context.filesDir.canonicalFile && directory.name == "qa-ast-integrated-performance")
        check(directory.isDirectory || directory.mkdirs())
        val output = File(directory, "$runId.json")
        check(output.createNewFile()) { "Performance run already has evidence" }
        val rows = JSONArray()
        val report = JSONObject().put("schema_version", 1).put("run_id", runId).put("status", "running")
            .put("synthetic_only", true).put("quality_validated", false)
            .put("actual_device_clip_analyzer", true).put("visual_requested", true)
            .put("model_sha256", AstModelStore.SHA256).put("device_model", Build.MODEL)
            .put("api", Build.VERSION.SDK_INT).put("abis", JSONArray(Build.SUPPORTED_ABIS.toList()))
            .put("candidate_config_enabled", BuildConfig.AST_INTEGRATED_CANDIDATE)
            .put("ast_native_success_inferred_from_abstention", false)
            .put("pss_measurement", "Process PSS sampled every 100ms; not an absolute peak or incremental model allocation")
            .put("thermal_unavailable_value", -1).put("call_timeout_ms", 45_000)
            .put("case_count", 2).put("measured_count", 0).put("cases", rows)
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        fun thermal(): Int = if (Build.VERSION.SDK_INT >= 29) power.currentThermalStatus else -1
        val started = SystemClock.elapsedRealtime()
        var fixture: File? = null
        var inputHash: String? = null
        var measuredCount = 0
        var primaryFailure: Throwable? = null
        try {
            output.writeText(report.toString(2))
            val source = File.createTempFile("ast-performance-tone-", ".mp4", context.cacheDir).also { fixture = it }
            instrumentation.context.assets.open("capture-two-minute-tone.mp4").use { input ->
                source.outputStream().use { input.copyTo(it) }
            }
            inputHash = hash(source)
            report.put("input_sha256_before", inputHash)
            for ((index, duration) in listOf(10_000L, 20_000L).withIndex()) {
                val range = ClipRange(100_000, 100_000 + duration)
                val row = JSONObject().put("index", index).put("status", "running")
                    .put("start_ms", range.startMs).put("end_ms", range.endMs).put("duration_ms", duration)
                    .put("visual", true)
                rows.put(row)
                val astEntries = AtomicInteger()
                val observed = object : ContextWrapper(context) {
                    override fun getApplicationContext(): Context = this
                    override fun getNoBackupFilesDir(): File {
                        // Observe model-store entry only; return the real directory unchanged.
                        astEntries.incrementAndGet()
                        return super.getNoBackupFilesDir()
                    }
                }
                val beforeThermal = thermal()
                row.put("thermal_before", beforeThermal)
                val sampling = AtomicBoolean(false)
                val peakPss = AtomicLong()
                val sampleCount = AtomicLong()
                val samplerError = AtomicReference<String?>(null)
                var sampler: Thread? = null
                val callStarted = SystemClock.elapsedRealtime()
                var stage = "preflight"
                var callFailure: Throwable? = null
                try {
                    check(beforeThermal < PowerManager.THERMAL_STATUS_SEVERE) { "Device reports severe thermal status" }
                    val initialPss = Debug.getPss()
                    check(initialPss > 0) { "PSS measurement unavailable" }
                    peakPss.set(initialPss)
                    sampleCount.set(1)
                    sampling.set(true)
                    sampler = Thread({
                        try {
                            while (sampling.get()) {
                                try { Thread.sleep(100) } catch (_: InterruptedException) { break }
                                if (!sampling.get()) break
                                val pss = Debug.getPss()
                                check(pss > 0) { "PSS measurement unavailable" }
                                peakPss.updateAndGet { previous -> maxOf(previous, pss) }
                                sampleCount.incrementAndGet()
                            }
                        } catch (error: Throwable) { samplerError.set(error.javaClass.simpleName) }
                    }, "ast-integrated-performance-pss").apply { isDaemon = true; start() }
                    stage = "actual_av_analysis"
                    val analyzed = withTimeout(45_000) {
                        DeviceClipAnalyzer.analyze(observed, Uri.fromFile(source), range, true)
                    }
                    val elapsed = SystemClock.elapsedRealtime() - callStarted
                    val visual = analyzed.visualEvidence
                    row.put("elapsed_ms", elapsed).put("analysis_returned", true)
                        .put("has_interpretation", analyzed.interpretation != null)
                        .put("issue", analyzed.issue?.name ?: JSONObject.NULL)
                        .put("has_problem", analyzed.problem != null)
                        .put("visual_status", visual?.status?.name ?: JSONObject.NULL)
                        .put("visual_frame_count", visual?.frames?.size ?: 0)
                        .put("visual_ordered", visual?.timeline?.ordered == true)
                        .put("visual_covers_selection", visual?.timeline?.coversSelection == true)
                    stage = "returned_contract"
                    check(elapsed <= 45_000) { "Actual AV analysis exceeded the budget" }
                    check(astEntries.get() > 0) { "Actual AST model-store path was not reached" }
                    check(analyzed.issue != AnalysisIssue.MODEL_FAILURE) { "Audio model runtime failed" }
                    check(analyzed.interpretation == null && analyzed.problem != null) { "Synthetic tone must abstain" }
                    check(visual?.status == VisualEvidenceStatus.COMPLETE)
                    check(visual?.frames?.size == (duration / 200).toInt()) { "Missing selected visual observations" }
                    check(visual?.timeline?.ordered == true && visual?.timeline?.coversSelection == true)
                    check(hash(source) == inputHash) { "Synthetic input changed" }
                    row.put("input_preserved", true).put("status", "measured")
                } catch (error: Throwable) {
                    callFailure = error
                    row.put("status", "failed").put("failure_stage", stage).put("error_type", error.javaClass.simpleName)
                    throw error
                } finally {
                    sampling.set(false)
                    sampler?.interrupt()
                    sampler?.join(2000)
                    val terminated = sampler?.isAlive != true
                    val afterThermal = thermal()
                    row.put("sampler_terminated", terminated).put("sampler_error_type", samplerError.get() ?: JSONObject.NULL)
                        .put("peak_sampled_pss_kb", peakPss.get()).put("pss_samples", sampleCount.get())
                        .put("thermal_after", afterThermal).put("ast_model_store_entries", astEntries.get())
                    if (!row.has("elapsed_ms")) row.put("elapsed_ms", SystemClock.elapsedRealtime() - callStarted)
                    output.writeText(report.toString(2))
                    if (!terminated || samplerError.get() != null || afterThermal >= PowerManager.THERMAL_STATUS_SEVERE) {
                        val cleanupFailure = IllegalStateException("PSS sampler cleanup or thermal gate failed")
                        row.put("status", "failed").put("cleanup_failure_stage", "sampler_or_thermal_cleanup")
                            .put("cleanup_error_type", cleanupFailure.javaClass.simpleName)
                        if (callFailure != null) {
                            callFailure.addSuppressed(cleanupFailure)
                        } else {
                            row.put("failure_stage", "sampler_or_thermal_cleanup")
                                .put("error_type", cleanupFailure.javaClass.simpleName)
                            throw cleanupFailure
                        }
                    }
                }
                measuredCount++
                report.put("measured_count", measuredCount)
                output.writeText(report.toString(2))
            }
            check(measuredCount == 2)
            report.put("status", "completed")
        } catch (error: Throwable) {
            primaryFailure = error
            report.put("status", "failed").put("error_type", error.javaClass.simpleName)
            throw error
        } finally {
            try {
                val source = fixture
                if (source != null) {
                    try {
                        val preserved = inputHash != null && source.isFile && hash(source) == inputHash
                        report.put("input_preserved", preserved)
                        check(inputHash == null || preserved) { "Synthetic input was changed" }
                    } finally {
                        check(!source.exists() || source.delete()) { "Synthetic fixture cleanup failed" }
                    }
                }
            } catch (error: Throwable) {
                report.put("status", "failed").put("cleanup_error_type", error.javaClass.simpleName)
                if (primaryFailure != null) primaryFailure.addSuppressed(error) else throw error
            } finally {
                report.put("fixture_removed", fixture?.exists() != true)
                    .put("elapsed_ms", SystemClock.elapsedRealtime() - started)
                output.writeText(report.toString(2))
            }
        }
    }

    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            try {
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(count > 0)
                    digest.update(buffer, 0, count)
                }
            } finally { buffer.fill(0) }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
