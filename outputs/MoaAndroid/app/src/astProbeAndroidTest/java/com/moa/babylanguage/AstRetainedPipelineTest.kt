package com.moa.babylanguage

import android.net.Uri
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Opt-in local decoder integration, not new independent infant evaluation. */
class AstRetainedPipelineTest {
    @Test fun retainedFailuresRecoverThroughWavDecoderAndActualClipAnalyzer() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit retained decoder probe only", args.getString("ast_retained_pipeline") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.moa.babylanguage.astcandidate" && BuildConfig.AST_INFANT_PRESENCE)
        val runId = args.getString("ast_retained_run_id") ?: error("Missing run ID")
        require(runId.matches(Regex("[a-f0-9]{32}")))
        val root = File(context.filesDir, "qa-retained-pipeline").canonicalFile
        val directory = File(root, runId).canonicalFile
        require(root.parentFile == context.filesDir.canonicalFile && directory.parentFile == root && directory.isDirectory)
        val manifestFile = File(directory, "manifest.json")
        require(manifestFile.canonicalFile.parentFile == directory && manifestFile.length() in 1L..32_768L)
        val manifest = JSONArray(manifestFile.readText(Charsets.UTF_8))
        require(manifest.length() == 3)
        val ids = listOf("clip_b_0_base", "clip_b_1_base", "clip_c_0_base")
        require((0 until 3).map { manifest.getJSONObject(it).getString("id") } == ids)
        val output = File(directory, "results.json")
        check(!output.exists()) { "Never reuse completed or stale decoder results" }
        val rows = JSONArray()
        val report = JSONObject().put("run_id", runId).put("status", "running")
            .put("known_development_cases", true).put("independent_quality_validated", false)
            .put("derived_pcm16_wav", true).put("original_video_exercised", false).put("cases", rows)
        try {
            for (index in 0 until 3) {
                val entry = manifest.getJSONObject(index)
                require(entry.getString("run_id") == runId)
                val input = File(directory, ids[index] + ".f32").canonicalFile
                require(input.parentFile == directory && input.isFile && input.length() in 64_000L..1_280_000L)
                val bytes = input.readBytes()
                var pcm = FloatArray(0)
                val wav = File.createTempFile("retained-decoder-", ".wav", context.cacheDir)
                var encoded = ByteArray(0)
                try {
                    val decoded = PublicAudioProbeContract.decode(bytes, entry)
                    pcm = decoded.pcm
                    require(pcm.all { it.isFinite() && it in -1f..1f })
                    encoded = ByteArray(44 + pcm.size * 2)
                    val buffer = ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN)
                    buffer.put("RIFF".toByteArray()).putInt(36 + pcm.size * 2).put("WAVEfmt ".toByteArray())
                    buffer.putInt(16).putShort(1.toShort()).putShort(1.toShort()).putInt(16_000)
                    buffer.putInt(32_000).putShort(2.toShort()).putShort(16.toShort())
                    buffer.put("data".toByteArray()).putInt(pcm.size * 2)
                    pcm.forEach { buffer.putShort((it * 32_767).toInt().toShort()) }
                    wav.writeBytes(encoded)
                    val before = PublicAudioProbeContract.sha256(encoded)
                    val begun = SystemClock.elapsedRealtime()
                    val result = withTimeout(45_000) {
                        DeviceClipAnalyzer.analyze(context, Uri.fromFile(wav), decoded.range, false)
                    }
                    rows.put(JSONObject().put("id", ids[index]).put("duration_ms", decoded.range.durationMs)
                        .put("elapsed_ms", SystemClock.elapsedRealtime() - begun)
                        .put("has_interpretation", result.interpretation != null)
                        .put("sound", result.interpretation?.sound ?: JSONObject.NULL)
                        .put("issue", result.issue?.name ?: JSONObject.NULL))
                    output.writeText(report.toString(2))
                    assertNull("Retained decoder path must not fail or silently abstain", result.issue)
                    assertEquals(if (index < 2) "아기 발성" else "울음", result.interpretation?.sound)
                    assertEquals(before, PublicAudioProbeContract.sha256(wav.readBytes()))
                    assertEquals(entry.getString("pcm_sha256"), PublicAudioProbeContract.sha256(input.readBytes()))
                } finally {
                    pcm.fill(0f)
                    bytes.fill(0)
                    encoded.fill(0)
                    check(wav.delete() || !wav.exists()) { "Owned decoder fixture cleanup failed" }
                }
            }
            report.put("status", "completed")
        } catch (error: Throwable) {
            report.put("status", "failed").put("error_type", error.javaClass.simpleName)
            throw error
        } finally { output.writeText(report.toString(2)) }
    }
}
