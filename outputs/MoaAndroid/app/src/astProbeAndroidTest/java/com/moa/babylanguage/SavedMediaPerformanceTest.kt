package com.moa.babylanguage

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.PowerManager
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
import java.security.MessageDigest

/** Exact retained public failure input; never opens caregiver media or records. */
class SavedMediaPerformanceTest {
    @Test fun retainedTwentySecondInputCompletesWithoutFabricatingLowFrameRateCoverage() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("Explicit local performance opt-in only",
            InstrumentationRegistry.getArguments().getString("saved_media_performance") == "true")
        val context = instrumentation.targetContext
        check(context.packageName == "com.moa.babylanguage.astcandidate")
        val folder = File(context.filesDir, "qa-saved-media-performance").canonicalFile
        require(folder.parentFile == context.filesDir.canonicalFile)
        val source = File(folder, "public20.mp4")
        val output = File(folder, "result.json")
        check(source.isFile && !output.exists())
        fun hash() = MessageDigest.getInstance("SHA-256").digest(source.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val expected = "9d57307e1fbd0ff3329e9ed5eba90d6713e619c9ac5d7b65cc6118a5876d8877"
        assertEquals(expected, hash())
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val activity = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val rows = JSONArray()
        val report = JSONObject().put("status", "running").put("input_sha256", expected)
            .put("actual_device_clip_analyzer", true).put("quality_validated", false)
            .put("scope", "one public input; process first call then warm call; not filesystem-cold")
            .put("timeout_ms", 45_000).put("rows", rows)
        try {
            for (index in 0..1) {
                val memory = ActivityManager.MemoryInfo().also(activity::getMemoryInfo)
                val row = JSONObject().put("index", index).put("available_memory_bytes", memory.availMem)
                    .put("low_memory", memory.lowMemory).put("thermal_before", power.currentThermalStatus)
                rows.put(row)
                output.writeText(report.toString(2))
                val started = SystemClock.elapsedRealtime()
                try {
                    val result = withTimeout(45_000) {
                        DeviceClipAnalyzer.analyze(context, Uri.fromFile(source), ClipRange(0, 20_000), true)
                    }
                    val elapsed = SystemClock.elapsedRealtime() - started
                    row.put("elapsed_ms", elapsed).put("has_interpretation", result.interpretation != null)
                        .put("has_problem", result.problem != null).put("issue", result.issue?.name ?: JSONObject.NULL)
                        .put("visual_status", result.visualEvidence?.status?.name ?: JSONObject.NULL)
                        .put("visual_frames", result.visualEvidence?.frames?.size ?: 0)
                        .put("visual_ordered", result.visualEvidence?.timeline?.ordered == true)
                        .put("visual_covers_selection", result.visualEvidence?.timeline?.coversSelection == true)
                    assertNull(result.issue); assertNull(result.problem); assertNotNull(result.interpretation)
                    assertTrue(elapsed < 45_000)
                    assertEquals(VisualEvidenceStatus.PARTIAL, result.visualEvidence?.status)
                    // The exact old timing fixture is one encoded frame/second. Preserve
                    // its 20 observations without inventing the missing 200ms observations.
                    assertEquals(20, result.visualEvidence?.frames?.size)
                    assertTrue(result.visualEvidence?.timeline?.ordered == true)
                    assertFalse(result.visualEvidence?.timeline?.coversSelection == true)
                    assertEquals(expected, hash())
                    row.put("status", "pass")
                } finally {
                    row.put("thermal_after", power.currentThermalStatus)
                    output.writeText(report.toString(2))
                }
            }
            report.put("status", "pass")
        } catch (error: Throwable) {
            report.put("status", "fail").put("error_type", error.javaClass.simpleName)
            throw error
        } finally { output.writeText(report.toString(2)) }
    }
}
