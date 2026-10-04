package com.moa.babylanguage

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Explicit local integration tests. Test doubles exist only in androidTest. */
class AstIntegratedCandidateTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun optIn() = assumeTrue("Explicit integrated candidate test only",
        InstrumentationRegistry.getArguments().getString("ast_integrated_probe") == "true")

    private fun tone(): File = File.createTempFile("ast-integrated-tone-", ".mp4", context.cacheDir).also { target ->
        instrumentation.context.assets.open("tone44-mono.mp4").use { source ->
            target.outputStream().use { source.copyTo(it) }
        }
    }

    @Test fun candidatePackageAccessAndPinnedModelArePresent() {
        optIn()
        assertEquals("com.moa.babylanguage.astcandidate", context.packageName)
        // Reflection keeps the initial RED test compilable before adding this field.
        assertTrue(BuildConfig::class.java.getField("AST_INTEGRATED_CANDIDATE").getBoolean(null))
        assertFalse(BabbleSupportPolicy.ENABLED)
        assertTrue(BuildConfig.FULL_ACCESS_CLOSED_TEST)
        assertFalse(BuildConfig.FULL_ACCESS_PREVIEW)
        val digest = MessageDigest.getInstance("SHA-256")
        var bytes = 0L
        context.assets.open(AstModelStore.ASSET).use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                bytes += count
                digest.update(buffer, 0, count)
            }
        }
        assertEquals(AstModelStore.SIZE, bytes)
        assertEquals(AstModelStore.SHA256, digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
    }

    @Test fun actualDevicePathInvokesAstAndPropagatesCancellation() = runBlocking {
        optIn()
        val file = tone()
        var calls = 0
        val cancellation = CancellationException("AST integration cancellation sentinel")
        val wrapped = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File {
                calls++
                throw cancellation
            }
        }
        try {
            val pcm = VideoAudioDecoder.decode(context, Uri.fromFile(file), 0, 2000)!!
            try { assertTrue("Synthetic input must reach the AST branch",
                BabbleSupportPolicy.shouldAnalyze(SoundAnalyzer.analyzeDetailed(context, pcm))) }
            finally { pcm.fill(0f) }
            var caught: CancellationException? = null
            try { withTimeout(45_000) { DeviceClipAnalyzer.analyze(wrapped, Uri.fromFile(file), ClipRange(0, 2000), false) } }
            catch (error: CancellationException) { caught = error }
            assertEquals("DeviceClipAnalyzer must actually enter the AST model-store path", 1, calls)
            assertEquals(cancellation.message, caught?.message)
        } finally { file.delete() }
    }

    @Test fun actualDevicePathKeepsBaselineWhenAstModelCannotOpen() = runBlocking {
        optIn()
        val file = tone()
        var calls = 0
        val wrapped = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getNoBackupFilesDir(): File {
                calls++
                throw IOException("Synthetic model-store unavailable")
            }
        }
        try {
            val pcm = VideoAudioDecoder.decode(context, Uri.fromFile(file), 0, 2000)!!
            val baseline = try { SoundAnalyzer.analyzeDetailed(context, pcm) } finally { pcm.fill(0f) }
            assertTrue(BabbleSupportPolicy.shouldAnalyze(baseline))
            val result = withTimeout(45_000) {
                DeviceClipAnalyzer.analyze(wrapped, Uri.fromFile(file), ClipRange(0, 2000), false)
            }
            assertEquals(1, calls)
            assertNull(result.interpretation)
            assertEquals(SoundTimeline.decide(baseline).issue, result.issue)
            assertNotNull(result.problem)
        } finally { file.delete() }
    }

    /**
     * Explicit known development case only; never bundled or silently discovered.
     * filesDir/qa-ast-integrated/<run-id>/manifest.json:
     * {"schema_version":1,"run_id":"<32 lowercase hex>","file":"known.mp4","source_sha256":"<64 lowercase hex>"}
     * Parent owns local transfer/result retrieval/cleanup. This test never deletes source media.
     */
    @Test fun optInKnownClipUsesActualIntegratedTenAndTwentySecondPaths() = runBlocking<Unit> {
        optIn()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Known media requires separate explicit opt-in", args.getString("ast_integrated_known") == "true")
        check(context.packageName == "com.moa.babylanguage.astcandidate")
        check(BuildConfig::class.java.getField("AST_INTEGRATED_CANDIDATE").getBoolean(null))
        val runId = args.getString("ast_integrated_run_id") ?: error("Missing integrated run ID")
        require(runId.matches(Regex("[a-f0-9]{32}")))
        val root = File(context.filesDir, "qa-ast-integrated").canonicalFile
        require(root.parentFile == context.filesDir.canonicalFile && root.name == "qa-ast-integrated")
        val directory = File(root, runId).canonicalFile
        require(directory.parentFile == root && directory.name == runId && directory.isDirectory)
        val output = File(directory, "results.json")
        check(!output.exists()) { "Integrated run already has results" }
        val manifestFile = File(directory, "manifest.json")
        require(manifestFile.canonicalFile.parentFile == directory && manifestFile.isFile && manifestFile.length() in 1L..4096L)
        val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
        require(manifest.keys().asSequence().toSet() == setOf("schema_version", "run_id", "file", "source_sha256"))
        require(manifest.getInt("schema_version") == 1 && manifest.getString("run_id") == runId)
        val name = manifest.getString("file")
        require(name.matches(Regex("[a-z0-9_-]+\\.mp4")))
        val source = File(directory, name).canonicalFile
        require(source.parentFile == directory && source.name == name && source.isFile &&
            source.length() in 1L..(200L * 1024 * 1024))
        val expectedHash = manifest.getString("source_sha256")
        require(expectedHash.matches(Regex("[a-f0-9]{64}")))
        val digest = MessageDigest.getInstance("SHA-256")
        source.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        require(digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == expectedHash)
        val rows = JSONArray()
        val report = JSONObject().put("run_id", runId).put("status", "running")
            .put("source_sha256", expectedHash).put("model_sha256", AstModelStore.SHA256)
            .put("actual_device_clip_analyzer", true).put("candidate_config_enabled", true)
            .put("policy_source_enabled", BabbleSupportPolicy.ENABLED)
            .put("known_development_case", true).put("independent_quality_validated", false)
            .put("cases", rows)
        output.writeText(report.toString(2))
        try {
            for (duration in listOf(10_000L, 20_000L)) {
                val start = SystemClock.elapsedRealtime()
                // The product entry point owns decoding, model selection, visual ordering and PCM cleanup.
                val result = withTimeout(45_000) {
                    DeviceClipAnalyzer.analyze(context, Uri.fromFile(source), ClipRange(0, duration), true)
                }
                val visual = result.visualEvidence
                rows.put(JSONObject().put("start_ms", 0).put("end_ms", duration)
                    .put("elapsed_ms", SystemClock.elapsedRealtime() - start)
                    .put("has_interpretation", result.interpretation != null)
                    .put("sound", result.interpretation?.sound ?: JSONObject.NULL)
                    .put("issue", result.issue?.name ?: JSONObject.NULL)
                    .put("visual_status", visual?.status?.name ?: JSONObject.NULL)
                    .put("visual_frame_count", visual?.frames?.size ?: 0)
                    .put("visual_ordered", visual?.timeline?.ordered == true)
                    .put("visual_covers_selection", visual?.timeline?.coversSelection == true)
                    .put("visual_pts_ms", JSONArray(visual?.frames?.map { it.presentationOffsetMs ?: JSONObject.NULL } ?: emptyList<Any>())))
                output.writeText(report.toString(2))
                assertNull("Known integrated case must not silently fall back", result.issue)
                assertEquals("옹알이", result.interpretation?.sound)
                assertEquals(VisualEvidenceStatus.COMPLETE, visual?.status)
                assertEquals((duration / 500).toInt(), visual?.frames?.size)
                assertTrue(visual?.timeline?.ordered == true && visual?.timeline?.coversSelection == true)
            }
            report.put("status", "completed")
        } catch (error: Throwable) {
            report.put("status", "failed").put("error_type", error.javaClass.simpleName)
            throw error
        } finally { output.writeText(report.toString(2)) }
    }
}
