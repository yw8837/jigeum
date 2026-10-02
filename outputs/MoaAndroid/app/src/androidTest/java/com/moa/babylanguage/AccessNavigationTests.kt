package com.moa.babylanguage

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
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
        analyzer.gate?.complete(Unit)
        if (started) { compose.runOnIdle { visible.value = false }; compose.waitForIdle() }
        runBlocking { store.close() }; compose.activity.deleteDatabase("$id.db"); clip.ownedFile?.delete()
    }
    private fun start(paid: Boolean = false, preview: Boolean = false) {
        billing.publish(paid); restoration = StateRestorationTester(compose); started = true
        restoration.setContent { if (visible.value) JigeumApp(compose.activity, store, billing, captureAnalyzer = analyzer, captureSeed = clip,
            testDistribution = preview) }
        waitText("아기말 번역하기"); click("아기말 번역하기")
        waitText("어느 순간을 들어볼까요?")
    }
    private fun waitText(text: String) { compose.waitUntil(8_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }
    private fun visual() { compose.onNodeWithTag("visual-analysis").performScrollTo().performClick() }
    private fun awaitResult() {
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("baby-phrase").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("keep-scene").performScrollTo().performClick()
    }
    private fun openDraft() {
        // isDisplayed also accepts a partially clipped node. After scrolling to
        // keep-scene, bring this button fully into view before a pointer click.
        compose.onNodeWithText("기록에 메모·수정 남기기").performScrollTo()
        captureDiagnostic("draft-before-click")
        compose.onNodeWithText("기록에 메모·수정 남기기").performClick()
        captureDiagnostic("draft-after-click")
        try {
            compose.waitUntil(8_000) { compose.onAllNodesWithTag("draft-phrase-editor").fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: Throwable) {
            captureDiagnostic("draft-click-failed", screenshot = true)
            throw failure
        }
    }

    /** Synthetic UI only. Preserve the tree/bounds before a click and on failure for diagnosis. */
    private fun captureDiagnostic(stage: String, screenshot: Boolean = false) {
        val folder = File(compose.activity.getExternalFilesDir(null), "qa-access-059").apply { mkdirs() }
        val stem = "$id-${android.os.SystemClock.uptimeMillis()}-$stage"
        runCatching {
            fun bounds(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().map { it.boundsInRoot }
            val openButton = compose.onAllNodesWithText("기록에 메모·수정 남기기").fetchSemanticsNodes().map { it.boundsInRoot }
            val closeButton = compose.onAllNodesWithText("기록 수정 접기").fetchSemanticsNodes().map { it.boundsInRoot }
            val summary = "$stage card=${bounds("baby-phrase")} open=$openButton close=$closeButton " +
                "loading=${bounds("page-loading")} capture=${bounds("page-capture")} editor=${bounds("draft-phrase-editor")}"
            android.util.Log.i("AccessNavigationQA", summary)
            val tree = compose.onRoot(useUnmergedTree = true).printToString()
            File(folder, "$stem.txt").writeText(summary + "\n" + tree)
            if (screenshot) tree.chunked(3_000).forEach { android.util.Log.e("AccessNavigationQA", it) }
        }
        if (screenshot) runCatching {
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let { bitmap ->
                try { File(folder, "$stem.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
                finally { bitmap.recycle() }
            }
        }
    }

    @Test fun previewRunsVisualWithoutPurchaseAndPreservesResultAcrossRestart() {
        start(preview = true)
        compose.onNodeWithTag("test-distribution").assertIsDisplayed()
        compose.onNodeWithTag("visual-analysis").assertIsOn()
        assertFalse(billing.hasCurrentEntitlement())
        assertFalse(billing.state.value.isPremium)
        click("영상 + 소리로 번역하기"); awaitResult()
        assertTrue(analyzer.lastVisual)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("baby-phrase").assertExists()
        click("이 문장 저장하기")
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("page-timeline").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("VIDEO_FILE", runBlocking { store.snapshot().observations.single().source })
        compose.onNodeWithText("아기말북").performClick()
        compose.onNodeWithText("이 달의 아기말북 PDF 만들기").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("전체 이용권 보기").assertDoesNotExist()
    }

    @Test fun previewOptionRowCanChooseAudioAndDoesNotCreateResultForMissingEvidence() {
        analyzer.problem = "아기 소리 단서가 없어요"
        start(preview = true)
        click("표정도 함께 보기")
        compose.onNodeWithTag("visual-analysis").assertIsOff()
        click("이 구간 소리로 번역하기"); waitText("아기 소리 단서가 없어요")
        assertFalse(analyzer.lastVisual)
        compose.onNodeWithTag("baby-phrase").assertDoesNotExist()
    }

    @Test fun testDistributionNeverPersistsAnEntitlementForRelease() {
        val preview = AccessPolicy({ false }, true)
        assertTrue(preview.canUse(AppFeature.VISUAL_ANALYSIS))
        assertTrue(preview.canUse(AppFeature.PDF))
        val production = AccessPolicy { false }
        assertFalse(production.canUse(AppFeature.VISUAL_ANALYSIS))
        assertFalse(production.canUse(AppFeature.PDF))
        assertTrue(production.canUse(AppFeature.CAPTURE))
        assertFalse(BuildConfig.FULL_ACCESS_PREVIEW)
    }

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
        analyzer.ignoreCancellation = true
        start(true); visual(); click("영상 + 소리로 번역하기")
        compose.waitUntil(8_000) { analyzer.calls == 1 }
        compose.runOnIdle { billing.publish(false) }
        compose.waitUntil(8_000) { compose.onAllNodesWithText("이 구간 소리로 번역하기").fetchSemanticsNodes().isNotEmpty() }
        analyzer.gate!!.complete(Unit)
        compose.waitUntil(8_000) { analyzer.returns == 1 }
        compose.waitForIdle()
        compose.onNodeWithTag("baby-phrase").assertDoesNotExist()
        assertTrue(clip.ownedFile!!.exists())
        click("이 구간 소리로 번역하기"); awaitResult()
        assertFalse(analyzer.lastVisual)
    }
    @Test fun resultAndEditedDraftStayFreeAfterExpiryAndRestoration() {
        start(true); visual(); click("영상 + 소리로 번역하기"); awaitResult()
        openDraft()
        compose.onNodeWithTag("draft-phrase-editor").performScrollTo().performTextReplacement("오늘의 소중한 소리")
        compose.runOnIdle { billing.publish(false) }
        restoration.emulateSavedInstanceStateRestore()
        captureDiagnostic("after-state-restore")
        // JigeumApp reloads its local DB asynchronously after restoration before
        // the preserved capture screen is composed. Await that state explicitly.
        try {
            compose.waitUntil(8_000) { compose.onAllNodesWithTag("draft-phrase-editor").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("draft-phrase-editor").assertTextContains("오늘의 소중한 소리")
        } catch (failure: Throwable) {
            captureDiagnostic("restore-editor-failed", screenshot = true)
            throw failure
        }
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
        analyzer.ignoreCancellation = true
        start(); click("이 구간 소리로 번역하기")
        compose.waitUntil(8_000) { analyzer.calls == 1 }
        click("분석 취소"); analyzer.gate!!.complete(Unit)
        compose.waitUntil(8_000) { analyzer.returns == 1 }
        compose.waitForIdle()
        compose.onNodeWithTag("baby-phrase").assertDoesNotExist()
        compose.onNodeWithText("이 구간 소리로 번역하기").assertExists()
    }
    @Test fun collapsingEditedDraftStillSavesTheEditedPhrase() {
        start(); click("이 구간 소리로 번역하기"); awaitResult()
        openDraft()
        compose.onNodeWithTag("draft-phrase-editor").performScrollTo().performTextReplacement("부모가 고친 소중한 문장")
        click("기록 수정 접기")
        restoration.emulateSavedInstanceStateRestore()
        click("이 문장 저장하기")
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("page-timeline").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("부모가 고친 소중한 문장", runBlocking { store.snapshot().observations.single().phrase })
    }
    @Test fun collapsedEmptyEditCannotSilentlySaveOriginalPhrase() {
        start(); click("이 구간 소리로 번역하기"); awaitResult()
        openDraft()
        compose.onNodeWithTag("draft-phrase-editor").performScrollTo().performTextReplacement("")
        click("기록 수정 접기")
        compose.onNodeWithText("이 문장 저장하기").assertIsNotEnabled()
        openDraft()
        assertEquals("", compose.onNodeWithTag("draft-phrase-editor").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        compose.onNodeWithText("이 문장 저장하기").assertIsNotEnabled()
    }
    @Test fun failedVisualNoticeIsVisibleWithoutExpandingAndSurvivesRestore() {
        analyzer.visualFailure = true
        start(true); visual(); click("영상 + 소리로 번역하기"); awaitResult()
        compose.onNodeWithTag("visual-notice").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("0:00–0:10 · 소리 관찰").assertExists()
        compose.onNodeWithTag("analysis-evidence").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("visual-notice").performScrollTo().assertIsDisplayed()
        click("이 문장 저장하기")
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("page-timeline").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(1, runBlocking { store.snapshot().observations.size })
    }
    @Test fun expiredVideoOptionCannotCancelNextFreeAudioCapture() {
        analyzer.gate = CompletableDeferred()
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        ui.grantRuntimePermission(compose.activity.packageName, android.Manifest.permission.RECORD_AUDIO)
        start(true); visual(); click("다시 촬영하기")
        click("카메라 없이 소리만 녹음"); click("녹음 시작")
        waitText("0:02 / 2:00"); click("촬영 마치기"); waitText("어느 순간을 들어볼까요?")
        compose.runOnIdle { billing.publish(false) }
        click("이 구간 소리로 번역하기")
        compose.waitUntil(8_000) { analyzer.calls == 1 }
        // Give the entitlement watcher time to run while the real audio clip is held.
        Thread.sleep(700)
        compose.onNodeWithText("분석 취소").assertExists()
        assertFalse(analyzer.lastVisual)
        analyzer.gate!!.complete(Unit); awaitResult()
    }
    @Test fun selectedRangeIsUsedByBothPlaybackAndAnalysis() {
        start()
        compose.onNodeWithTag("clip-range").performScrollTo()
        val handles = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        handles[0].performSemanticsAction(SemanticsActions.SetProgress) { it(8_000f) }
        handles[1].performSemanticsAction(SemanticsActions.SetProgress) { it(11_000f) }
        compose.onNodeWithTag("selected-range").assertTextContains("0:08.0 – 0:11.0 · 3초")
        compose.onNodeWithTag("clip-play").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("재생 멈추기 · 0:08")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(3_000) { compose.onAllNodes(hasText("재생 멈추기 · 0:09")).fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(4_000) { compose.onAllNodes(hasText("재생 멈추기", substring = true)).fetchSemanticsNodes().isEmpty() }
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
        @Volatile var returns = 0
        var ignoreCancellation = false
        var visualFailure = false
        var lastVisual = false
        var lastRange: ClipRange? = null
        var gate: CompletableDeferred<Unit>? = null
        var problem: String? = null
        override suspend fun analyze(context: Context, uri: Uri, range: ClipRange, visual: Boolean): ClipAnalysisResult {
            calls++; lastVisual = visual; lastRange = range
            if (ignoreCancellation) withContext(NonCancellable) { gate?.await(); returns++ }
            else { gate?.await(); returns++ }
            return if (problem != null) ClipAnalysisResult(problem = problem)
            else EvidenceInterpreter.interpret(SoundAnalysis(listOf(SoundCue("울음", .8f))), AcousticShape(.8f, false, 1), range,
                if (visual) {
                    if (visualFailure) VideoFrameSummary(3, 3, 0, 0, 0, true, false).visualEvidence()
                    else VisualEvidence(VisualEvidenceStatus.COMPLETE, "테스트에서 제공한 얼굴 관찰")
                } else null)
        }
    }
}
