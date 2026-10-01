package com.moa.babylanguage

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID

/** 0.5 real composition: freely captured clip, gated visual analysis, preserved draft. */
class AccessNavigationTests {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val id = "capture-ui-${UUID.randomUUID()}"
    private lateinit var store: ObservationStore
    private lateinit var clip: LocalClip
    private val visible = mutableStateOf(true)
    private val billing = FakeBilling()
    private val analyzer = ControlledAnalyzer()
    private lateinit var restoration: StateRestorationTester
    private var started = false

    @Before fun setup() {
        store = ObservationStore(compose.activity, "$id.db", id)
        val file = File(compose.activity.cacheDir, "$id.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("range-silence-tone.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
        clip = CaptureFiles.inspect(compose.activity, Uri.fromFile(file), file)
    }
    @After fun cleanup() {
        if (started) { compose.runOnIdle { visible.value = false }; compose.waitForIdle() }
        runBlocking { store.close() }; compose.activity.deleteDatabase("$id.db"); clip.ownedFile?.delete()
    }
    private fun start(paid: Boolean = false) {
        billing.publish(paid); restoration = StateRestorationTester(compose); started = true
        restoration.setContent { if (visible.value) JigeumApp(compose.activity, store, billing, captureAnalyzer = analyzer, captureSeed = clip) }
        waitText("아기말 번역하기"); click("아기말 번역하기")
        waitText("어느 순간을 들어볼까요?")
    }
    private fun waitText(text: String) { compose.waitUntil(8_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        if (text != "이 문장 저장하기") node.performScrollTo()
        node.performClick()
    }
    private fun visual() { compose.onNodeWithTag("visual-analysis").performScrollTo().performClick() }
    private fun awaitResult() { compose.waitUntil(8_000) { compose.onAllNodesWithTag("baby-phrase").fetchSemanticsNodes().isNotEmpty() } }

    @Test fun freeAudioWorksAndNoManualOrAgeChoicesExist() {
        start()
        compose.onNodeWithText("우리 아기는 몇 개월인가요?").assertDoesNotExist()
        compose.onNodeWithText("녹음 없이 직접 번역").assertDoesNotExist()
        click("이 구간 소리로 번역하기"); awaitResult()
        assertFalse(analyzer.lastVisual)
        assertEquals(1, analyzer.calls)
        compose.onNodeWithText("다른 말로도 볼 수 있어요").assertDoesNotExist()
        click("이 문장 저장하기")
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("page-timeline").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, runBlocking { store.snapshot().observations.size })
    }
    @Test fun lockedVisualShowsPassAndReturnsToSameClipWithoutAnalysis() {
        start(); visual(); waitText("지금의 표정도 함께 살펴봐요")
        assertEquals(0, analyzer.calls)
        click("개인정보처리방침"); click("돌아가기")
        click("같은 구간 소리로 계속하기")
        waitText("어느 순간을 들어볼까요?")
        compose.onNodeWithTag("clip-play").assertExists()
        assertTrue(clip.ownedFile!!.exists())
        assertEquals(0, analyzer.calls)
    }
    @Test fun pendingAndUiPremiumCannotUnlockVisualAnalysis() {
        start()
        compose.runOnIdle { billing.state.value = BillingUiState(status = BillingStatus.PENDING, isPremium = true) }
        visual(); waitText("지금의 표정도 함께 살펴봐요")
        assertEquals(0, analyzer.calls)
        click("같은 구간 소리로 계속하기")
        compose.onNodeWithTag("visual-analysis").assertIsOff()
    }
    @Test fun verifiedRestoreNeverStartsCaptureOrAnalysisAutomatically() {
        start(); visual()
        compose.runOnIdle { billing.publish(true) }
        click("선택한 구간으로 돌아가기")
        assertEquals(0, analyzer.calls)
        compose.onNodeWithTag("visual-analysis").assertIsOff()
        visual(); click("영상 + 소리로 번역하기"); awaitResult()
        assertTrue(analyzer.lastVisual)
    }
    @Test fun expiryDuringAnalysisRejectsLateResultAndKeepsFreeClip() {
        analyzer.gate = CompletableDeferred()
        start(true); visual(); click("영상 + 소리로 번역하기")
        compose.waitUntil(8_000) { analyzer.calls == 1 }
        compose.runOnIdle { billing.publish(false) }
        compose.waitUntil(8_000) { compose.onAllNodesWithText("이 구간 소리로 번역하기").fetchSemanticsNodes().isNotEmpty() }
        analyzer.gate!!.complete(Unit)
        compose.waitForIdle()
        compose.onNodeWithTag("baby-phrase").assertDoesNotExist()
        assertTrue(clip.ownedFile!!.exists())
        click("이 구간 소리로 번역하기"); awaitResult()
        assertFalse(analyzer.lastVisual)
    }
    @Test fun resultAndEditedDraftStayFreeAfterExpiryAndRestoration() {
        start(true); visual(); click("영상 + 소리로 번역하기"); awaitResult()
        click("기록에 메모·수정 남기기")
        compose.onNodeWithTag("draft-phrase-editor").performScrollTo().performTextReplacement("오늘의 소중한 소리")
        compose.runOnIdle { billing.publish(false) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("draft-phrase-editor").assertTextContains("오늘의 소중한 소리")
        click("이 문장 저장하기")
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("page-timeline").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("오늘의 소중한 소리", runBlocking { store.snapshot().observations.single().phrase })
    }
    @Test fun analysisProblemDoesNotFallBackToCannedResult() {
        analyzer.problem = "아기 소리 단서가 없어요"
        start(); click("이 구간 소리로 번역하기"); waitText("아기 소리 단서가 없어요")
        compose.onNodeWithTag("capture-message").assertIsDisplayed()
        compose.onNodeWithTag("baby-phrase").assertDoesNotExist()
        compose.onNodeWithText("이 문장 저장하기").assertDoesNotExist()
    }
    @Test fun cancellingAnalysisIgnoresLateCallback() {
        analyzer.gate = CompletableDeferred()
        start(); click("이 구간 소리로 번역하기")
        compose.waitUntil(8_000) { analyzer.calls == 1 }
        click("분석 취소"); analyzer.gate!!.complete(Unit); compose.waitForIdle()
        compose.onNodeWithTag("baby-phrase").assertDoesNotExist()
        compose.onNodeWithText("이 구간 소리로 번역하기").assertExists()
    }
    @Test fun selectedRangeIsUsedByBothPlaybackAndAnalysis() {
        start()
        compose.onNodeWithTag("clip-range").performScrollTo()
        val handles = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        handles[0].performSemanticsAction(SemanticsActions.SetProgress) { it(8_000f) }
        handles[1].performSemanticsAction(SemanticsActions.SetProgress) { it(11_000f) }
        compose.onNodeWithTag("selected-range").assertTextContains("0:08 — 0:11 · 3초 선택")
        compose.onNodeWithTag("clip-play").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("재생 멈추기 · 0:08")).fetchSemanticsNodes().isNotEmpty() }
        click("이 구간 소리로 번역하기"); awaitResult()
        assertEquals(ClipRange(8_000, 11_000), analyzer.lastRange)
    }

    /** Run at device font scale 1.0 and 2.0; the result is synthetic, not model accuracy evidence. */
    @Test fun resultLayoutKeepsSaveAndEvidenceAccessible() {
        start(); click("이 구간 소리로 번역하기"); awaitResult()
        compose.onNodeWithTag("baby-phrase").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("이 문장 저장하기").assertIsDisplayed()
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        val dir = File(compose.activity.getExternalFilesDir(null), "qa-layout").apply { mkdirs() }
        val scale = compose.activity.resources.configuration.fontScale
        ui.takeScreenshot().let { bitmap ->
            File(dir, "synthetic-result-$scale.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        click("어떤 소리를 들었나요?")
        compose.onNodeWithTag("analysis-evidence").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("이 문장 저장하기").assertIsDisplayed()
        ui.takeScreenshot().let { bitmap ->
            File(dir, "synthetic-evidence-$scale.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        click("이 문장 저장하기")
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("page-timeline").fetchSemanticsNodes().isNotEmpty() }
    }

    private class FakeBilling : AccessBilling {
        var verified = false
        override val state = MutableStateFlow(BillingUiState(status = BillingStatus.UNAVAILABLE))
        fun publish(value: Boolean) { verified = value; state.value = BillingUiState(isPremium = value, status = if (value) BillingStatus.PREMIUM else BillingStatus.UNAVAILABLE) }
        override fun hasCurrentEntitlement() = verified
        override fun refresh() = Unit
        override fun restore() = Unit
        override fun purchase(activity: Activity, plan: BillingPlan) = Unit
    }
    private class ControlledAnalyzer : ClipAnalyzer {
        @Volatile var calls = 0
        var lastVisual = false
        var lastRange: ClipRange? = null
        var gate: CompletableDeferred<Unit>? = null
        var problem: String? = null
        override suspend fun analyze(context: Context, uri: Uri, range: ClipRange, visual: Boolean): ClipAnalysisResult {
            calls++; lastVisual = visual; lastRange = range
            gate?.await()
            return if (problem != null) ClipAnalysisResult(problem = problem)
            else EvidenceInterpreter.interpret(SoundAnalysis(listOf(SoundCue("울음", .8f))), AcousticShape(.8f, false, 1), range,
                if (visual) "테스트에서 제공한 얼굴 관찰" else null)
        }
    }
}
