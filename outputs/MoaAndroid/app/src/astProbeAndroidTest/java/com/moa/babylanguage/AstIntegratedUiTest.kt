package com.moa.babylanguage

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Actual CaptureFlow -> DeviceClipAnalyzer. The observer never supplies or changes a result. */
class AstIntegratedUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class Call(val range: ClipRange, val visual: Boolean) {
        val astEntries = AtomicInteger()
        val cancelled = AtomicBoolean()
        val terminated = AtomicBoolean()
        @Volatile var result: ClipAnalysisResult? = null
        @Volatile var failure: String? = null
    }

    private class Observer : ClipAnalyzer {
        val calls = CopyOnWriteArrayList<Call>()
        override suspend fun analyze(context: Context, uri: Uri, range: ClipRange, visual: Boolean): ClipAnalysisResult {
            val call = Call(range, visual).also { calls.add(it) }
            val observed = object : ContextWrapper(context) {
                override fun getApplicationContext(): Context = this
                override fun getNoBackupFilesDir(): File {
                    // AstModelStore.resolve visits this path. Return the real directory unchanged.
                    call.astEntries.incrementAndGet()
                    return super.getNoBackupFilesDir()
                }
            }
            try {
                return DeviceClipAnalyzer.analyze(observed, uri, range, visual).also { call.result = it }
            } catch (cancelled: CancellationException) {
                call.cancelled.set(true)
                throw cancelled
            } catch (error: Throwable) {
                call.failure = error.javaClass.simpleName
                throw error
            } finally { call.terminated.set(true) }
        }
    }

    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }

    @Test fun actualTwentySecondAnalysisCancelsAndNextAnalysisCompletesWithoutChangingInput() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit candidate UI probe only", args.getString("ast_integrated_ui") == "true")
        val context = instrumentation.targetContext
        check(context.packageName == "com.moa.babylanguage.astcandidate" && BuildConfig.AST_INTEGRATED_CANDIDATE)
        val runId = args.getString("ast_integrated_ui_run_id") ?: error("Missing UI run ID")
        require(runId.matches(Regex("[a-f0-9]{32}")))
        val directory = File(context.filesDir, "qa-ast-integrated-ui").apply { check(isDirectory || mkdirs()) }
        val output = File(directory, "$runId.json")
        check(!output.exists()) { "UI run already has evidence" }
        val fixture = File.createTempFile("ast-ui-tone-", ".mp4", context.cacheDir)
        instrumentation.context.assets.open("capture-two-minute-tone.mp4").use { input ->
            fixture.outputStream().use { input.copyTo(it) }
        }
        val before = hash(fixture)
        val visible = mutableStateOf(true)
        val observer = Observer()
        val saves = AtomicInteger()
        var composed = false
        val report = JSONObject().put("run_id", runId).put("status", "running")
            .put("synthetic_only", true).put("input_sha256_before", before)
            .put("analyzer_result_substituted", false).put("ast_native_success_inferred_from_abstention", false)
            .put("quality_validated", false)
        output.writeText(report.toString(2))
        try {
            composed = true
            compose.setContent { MaterialTheme {
                if (visible.value) CaptureFlow(
                    authorizeVisual = { false }, onBack = {}, saving = false,
                    onSave = { _, _, _ -> saves.incrementAndGet() },
                    passContent = { _ -> Text("Unexpected pass screen") },
                    analyzer = observer,
                    initialClip = LocalClip(Uri.fromFile(fixture), 120_000, hasVideo = true),
                    testDistribution = BuildConfig.FULL_ACCESS_CLOSED_TEST
                )
            } }
            compose.waitUntil(8_000) { compose.onAllNodesWithTag("visual-analysis").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("visual-analysis").performScrollTo().performClick()
            compose.onNodeWithTag("visual-analysis").assertIsOff()
            compose.onNodeWithTag("clip-range-end").performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(20_000f) }
            // This fixture is silent until 100s. Move via real adjustable controls to its late tone.
            // Each handle respects the current 20s maximum, so walk the selection forward first.
            repeat(6) {
                compose.onNodeWithTag("clip-range-start").performScrollTo()
                    .performSemanticsAction(SemanticsActions.SetProgress) { it(100_000f) }
                compose.onNodeWithTag("clip-range-end").performScrollTo()
                    .performSemanticsAction(SemanticsActions.SetProgress) { it(120_000f) }
            }
            compose.onNodeWithTag("clip-range-start").performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(100_000f) }
            compose.onNodeWithTag("selected-range").assertTextEquals("1:40.0 – 2:00.0 · 20초")
            click("이 구간 소리로 번역하기")
            compose.waitUntil(40_000) {
                observer.calls.firstOrNull()?.let { it.astEntries.get() > 0 || it.terminated.get() } == true
            }
            val first = observer.calls.single()
            assertEquals(ClipRange(100_000, 120_000), first.range)
            assertFalse(first.visual)
            assertTrue("Actual AST path must be reached before cancellation", first.astEntries.get() > 0)
            assertFalse("The call must still be active when exercising cancellation", first.terminated.get())
            val heartbeat = CountDownLatch(1)
            val heartbeatStart = SystemClock.elapsedRealtime()
            Handler(Looper.getMainLooper()).post { heartbeat.countDown() }
            assertTrue("Main thread must respond while native analysis is active", heartbeat.await(5, TimeUnit.SECONDS))
            report.put("main_heartbeat_ms", SystemClock.elapsedRealtime() - heartbeatStart)
            click("분석 취소")
            compose.waitUntil(10_000) { first.terminated.get() }
            assertTrue("Real coroutine cancellation must reach the delegated analyzer", first.cancelled.get())
            assertNull(first.result)
            compose.onNodeWithTag("baby-phrase").assertDoesNotExist()
            compose.onNodeWithTag("capture-message").assertTextEquals("분석을 취소했어요. 구간을 다시 고를 수 있어요.")

            click("이 구간 소리로 번역하기")
            compose.waitUntil(50_000) { observer.calls.getOrNull(1)?.terminated?.get() == true }
            assertEquals(2, observer.calls.size)
            val second = observer.calls[1]
            assertEquals(ClipRange(100_000, 120_000), second.range)
            assertTrue(second.astEntries.get() > 0)
            assertFalse(second.cancelled.get())
            assertNull(second.failure)
            val actual = requireNotNull(second.result) { "Real next analysis must return" }
            assertNull("A synthetic tone must not create a baby phrase", actual.interpretation)
            assertNotNull(actual.problem)
            compose.waitUntil(8_000) { compose.onAllNodesWithText(actual.problem!!).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("baby-phrase").assertDoesNotExist()
            compose.onNodeWithText("이 문장 저장하기").assertDoesNotExist()
            assertEquals(0, saves.get())
            assertEquals(before, hash(fixture))
            report.put("status", "completed")
        } catch (error: Throwable) {
            report.put("status", "failed").put("error_type", error.javaClass.simpleName)
            throw error
        } finally {
            try {
                if (composed) {
                    compose.runOnIdle { visible.value = false }
                    compose.waitForIdle()
                }
            } catch (error: Throwable) {
                report.put("status", "failed").put("cleanup_error_type", error.javaClass.simpleName)
                throw error
            } finally {
                report.put("calls", JSONArray(observer.calls.map { call ->
                    JSONObject().put("start_ms", call.range.startMs).put("end_ms", call.range.endMs)
                        .put("visual", call.visual).put("ast_model_store_entries", call.astEntries.get())
                        .put("cancelled", call.cancelled.get()).put("terminated", call.terminated.get())
                        .put("returned", call.result != null).put("failure", call.failure ?: JSONObject.NULL)
                })).put("save_calls", saves.get())
                report.put("input_preserved", fixture.isFile && hash(fixture) == before)
                output.writeText(report.toString(2))
                fixture.delete()
            }
        }
    }
}
