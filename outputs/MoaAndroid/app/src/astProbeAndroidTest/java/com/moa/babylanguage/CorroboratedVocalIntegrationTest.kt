package com.moa.babylanguage

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Explicit local public fixture, never bundled. Exercises the new real decoder/AST branch. */
class CorroboratedVocalIntegrationTest {
    @Test fun actualCorroborationPropagatesFailureAndCancellationThenRecovers() = runBlocking<Unit> {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("corroborated_probe") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.moa.babylanguage.astcandidate")
        val runId = args.getString("run_id")!!
        val expectedSha = args.getString("fixture_sha")!!
        require(runId.matches(Regex("[a-f0-9]{32}")) && expectedSha.matches(Regex("[a-f0-9]{64}")))
        val parent = File(context.filesDir, "qa-corroborated").canonicalFile
        val directory = File(parent, runId).canonicalFile
        val file = File(directory, "input.wav").canonicalFile
        require(parent.parentFile == context.filesDir.canonicalFile && directory.parentFile == parent &&
            file.parentFile == directory && file.isFile && file.length() in 44..1_280_044)
        fun hash() = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        check(hash() == expectedSha)
        val output = File(directory, "result.json")
        check(output.createNewFile())
        val report = JSONObject().put("run_id", runId).put("completed", false)
        val range = ClipRange(0, 2480)
        try {
            val pcm = VideoAudioDecoder.decode(context, Uri.fromFile(file), range.startMs, range.endMs)!!
            try {
                val baseline = SoundAnalyzer.analyzeDetailed(context, pcm)
                assertFalse("Must test the new branch, not existing babble fallback", BabbleSupportPolicy.shouldAnalyze(baseline))
                assertTrue(InfantVoiceSupportPolicy.shouldCorroborate(baseline, pcm.size / 16L))
            } finally { pcm.fill(0f) }
            for (cancel in listOf(false, true)) {
                var calls = 0
                val wrapped = object : ContextWrapper(context) {
                    override fun getApplicationContext(): Context = this
                    override fun getNoBackupFilesDir(): File {
                        calls++
                        if (cancel) throw CancellationException("corroboration cancellation sentinel")
                        throw IOException("synthetic model-store failure")
                    }
                }
                var caught = false
                try {
                    val result = withTimeout(45_000) {
                        DeviceClipAnalyzer.analyze(wrapped, Uri.fromFile(file), range, false)
                    }
                    assertFalse(cancel)
                    assertEquals(AnalysisIssue.MODEL_FAILURE, result.issue)
                    assertNull(result.interpretation)
                } catch (error: CancellationException) {
                    assertTrue(cancel)
                    assertEquals("corroboration cancellation sentinel", error.message)
                    caught = true
                }
                assertEquals(cancel, caught)
                assertEquals("One AST entry, no repeated native retries", 1, calls)
            }
            val started = SystemClock.elapsedRealtime()
            val result = withTimeout(45_000) {
                DeviceClipAnalyzer.analyze(context, Uri.fromFile(file), range, false)
            }
            assertNull(result.problem)
            assertEquals("아기 발성", result.interpretation!!.sound)
            assertTrue(result.interpretation.evidence.contains("어느 소리인지는 구분하지 않았어요"))
            report.put("elapsed_ms", SystemClock.elapsedRealtime() - started)
                .put("runtime_failure_preserved", true).put("cancellation_preserved", true)
                .put("subsequent_real_inference_succeeded", true).put("completed", true)
        } finally {
            report.put("fixture_unchanged", hash() == expectedSha)
            output.writeText(report.toString(2))
            assertEquals(expectedSha, hash())
        }
    }
}
