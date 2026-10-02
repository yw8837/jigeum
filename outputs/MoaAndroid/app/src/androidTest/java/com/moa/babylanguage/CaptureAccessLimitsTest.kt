package com.moa.babylanguage

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Actual CaptureFlow composition; only media/model and verified entitlement are test substitutes. */
class CaptureAccessLimitsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val entitled = AtomicBoolean(false)
    private val revokedReads = AtomicInteger(0)
    private val visible = mutableStateOf(true)
    private val analyzer = HeldAnalyzer()
    private var started = false
    private var saved: ClipInterpretation? = null

    @After fun dispose() {
        analyzer.hold?.complete(Unit)
        analyzer.calls.forEach { it.gate?.complete(Unit) }
        if (started) {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
        }
    }

    private fun start(paid: Boolean = false, preview: Boolean = false) {
        entitled.set(paid)
        val duration = if (paid || preview) 120_000L else 60_000L
        // A missing preview must not gate editing or the injected analysis. No real user media.
        val clip = LocalClip(Uri.fromFile(File(compose.activity.cacheDir, "missing-capture-access-test.mp4")),
            duration, hasVideo = true)
        started = true
        compose.setContent { MaterialTheme {
            if (visible.value) CaptureFlow(
                authorizeVisual = {
                    entitled.get().also { if (!it) revokedReads.incrementAndGet() }
                },
                onBack = {}, saving = false,
                onSave = { result, _, _ -> saved = result },
                passContent = { _ -> Text("이용권 화면", modifier = Modifier.testTag("test-pass")) },
                analyzer = analyzer, initialClip = clip, testDistribution = preview
            )
        } }
        awaitText("어느 순간을 들어볼까요?")
    }

    private fun awaitText(text: String) {
        compose.waitUntil(8_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }

    private fun setEnd(milliseconds: Float) {
        compose.onNodeWithTag("clip-range-end").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(milliseconds) }
    }

    private fun awaitResult() {
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("baby-phrase").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun extendedAnalysisExpiryClampsParentRangeAndNextFreeRequestUsesTenSeconds() {
        val staleGate = CompletableDeferred<Unit>()
        analyzer.hold = staleGate
        analyzer.ignoreCancellation = true
        start(paid = true)
        setEnd(20_000f)
        click("이 구간 소리로 번역하기")
        compose.waitUntil(8_000) { analyzer.calls.size == 1 }
        assertEquals(ClipRange(0, 20_000), analyzer.calls[0].range)
        assertFalse(analyzer.calls[0].visual)

        compose.runOnIdle { entitled.set(false) }
        awaitText("이 구간 소리로 번역하기")
        compose.waitUntil(8_000) {
            compose.onAllNodesWithTag("selected-range").fetchSemanticsNodes().size == 1 &&
                compose.onAllNodesWithText("0:00.0 – 0:10.0 · 10초").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("baby-phrase").assertDoesNotExist()

        // Checking the next analyzer request proves the parent range changed, not just editor text.
        compose.runOnIdle { analyzer.hold = null }
        click("이 구간 소리로 번역하기")
        awaitResult()
        assertEquals(2, analyzer.calls.size)
        assertEquals(ClipRange(0, 10_000), analyzer.calls[1].range)
        assertFalse(analyzer.calls[1].visual)
        compose.onNodeWithTag("test-pass").assertDoesNotExist()
        compose.onNodeWithTag("baby-phrase").assertTextEquals("테스트 한마디 2")

        // An old model call ignoring cancellation may finish later; it must not replace the new result.
        staleGate.complete(Unit)
        compose.waitUntil(8_000) { analyzer.calls[0].finished.get() }
        compose.waitForIdle()
        compose.onNodeWithTag("baby-phrase").assertTextEquals("테스트 한마디 2")
    }

    @Test fun permissionLostImmediatelyBeforeModelReturnCannotExposeExtendedResult() {
        start(paid = true)
        setEnd(20_000f)
        analyzer.beforeReturn = { entitled.set(false) }
        click("이 구간 소리로 번역하기")
        compose.waitUntil(8_000) { analyzer.calls.size == 1 && analyzer.calls[0].finished.get() }
        awaitText("이 구간 소리로 번역하기")
        compose.onNodeWithTag("baby-phrase").assertDoesNotExist()
        compose.onNodeWithText("이 문장 저장하기").assertDoesNotExist()
        assertEquals(ClipRange(0, 20_000), analyzer.calls.single().range)
        assertFalse(entitled.get())
        compose.onNodeWithTag("capture-message").assertExists()
    }

    @Test fun previewAllowsTwentySecondVisualAnalysisWithNoEntitlement() {
        start(preview = true)
        compose.onNodeWithTag("visual-analysis").assertIsOn()
        setEnd(120_000f)
        click("영상 + 소리로 번역하기")
        awaitResult()
        assertFalse(entitled.get())
        assertEquals(ClipRange(0, 20_000), analyzer.calls.single().range)
        assertTrue(analyzer.calls.single().visual)
        compose.onNodeWithTag("test-pass").assertDoesNotExist()
        compose.onNodeWithText("이 문장 저장하기").assertIsEnabled()
    }

    @Test fun completedExtendedResultRemainsVisibleAndSaveableAfterExpiry() {
        start(paid = true)
        setEnd(20_000f)
        compose.onNodeWithTag("visual-analysis").performScrollTo().performClick()
        click("영상 + 소리로 번역하기")
        awaitResult()
        val readsBefore = revokedReads.get()
        compose.runOnIdle { entitled.set(false) }
        // Wait for the live entitlement poll, rather than only checking before the watcher runs.
        compose.waitUntil(8_000) { revokedReads.get() > readsBefore }
        compose.waitForIdle()
        compose.onNodeWithTag("baby-phrase").assertTextEquals("테스트 한마디 1")
        compose.onNodeWithText("이 문장 저장하기").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals("테스트 한마디 1", saved?.phrase)
            assertEquals(ClipRange(0, 20_000).label(), saved?.range)
            assertEquals(true, saved?.visual)
        }
        compose.onNodeWithTag("test-pass").assertDoesNotExist()
    }

    @Test fun freeEndpointCannotProduceAnExtendedAnalysisRequest() {
        start()
        setEnd(60_000f)
        click("이 구간 소리로 번역하기")
        awaitResult()
        assertEquals(ClipRange(0, 10_000), analyzer.calls.single().range)
        assertFalse(analyzer.calls.single().visual)
    }

    private data class Call(val number: Int, val range: ClipRange, val visual: Boolean,
                            val gate: CompletableDeferred<Unit>?, val finished: AtomicBoolean = AtomicBoolean(false))

    private class HeldAnalyzer : ClipAnalyzer {
        val calls = CopyOnWriteArrayList<Call>()
        @Volatile var hold: CompletableDeferred<Unit>? = null
        @Volatile var ignoreCancellation = false
        @Volatile var beforeReturn: (() -> Unit)? = null

        override suspend fun analyze(context: Context, uri: Uri, range: ClipRange, visual: Boolean): ClipAnalysisResult {
            val call = Call(calls.size + 1, range, visual, hold)
            calls += call
            try {
                if (ignoreCancellation) withContext(NonCancellable) { call.gate?.await() }
                else call.gate?.await()
                beforeReturn?.invoke()
                return ClipAnalysisResult(ClipInterpretation("테스트 한마디 ${call.number}", "테스트 소리",
                    "테스트에서 제공한 근거", "테스트 안내", range.label(), visual))
            } finally { call.finished.set(true) }
        }
    }
}
