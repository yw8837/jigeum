package com.moa.babylanguage

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Model output/picker responses are substitutes; UI state and real metadata/file ownership are exercised. */
class ResultNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val visible = mutableStateOf(true)
    private val saving = mutableStateOf(false)
    private val registry = PickerRegistry()
    private val analyzer = StubAnalyzer()
    private val files = mutableListOf<File>()
    private lateinit var source: LocalClip
    private var saved: Saved? = null
    private var gallery: Saved? = null
    private var blockSave = false
    private var blockGallery = false
    private var backCalls = 0
    private var started = false

    @After fun dispose() {
        analyzer.gate?.complete(Unit)
        if (started) {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
        }
        files.forEach { it.delete() }
    }

    private fun fixture(): File = File(compose.activity.cacheDir, "result-navigation-${UUID.randomUUID()}.mp4").also { file ->
        files.add(file)
        InstrumentationRegistry.getInstrumentation().context.assets.open("range-silence-tone.mp4").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
    }

    private fun start(compact: Boolean = false, landscape: Boolean = false, fontScale: Float? = null) {
        val file = fixture()
        source = CaptureFiles.inspect(compose.activity, Uri.fromFile(file), file)
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        started = true
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner,
                LocalDensity provides Density(density.density, fontScale ?: if (compact) 2f else density.fontScale)) {
                MaterialTheme {
                    // Main runs these tests on a landscape host with >=640dp available width.
                    Box(if (landscape) Modifier.widthIn(max = 640.dp).heightIn(max = 320.dp)
                        else if (compact) Modifier.widthIn(max = 360.dp).heightIn(max = 640.dp) else Modifier) {
                        if (visible.value) CaptureFlow({ true }, { backCalls++ }, saving.value,
                            onSave = { result, phrase, note -> saved = Saved(result, phrase, note, null, null) },
                            passContent = { Text("이용권") }, analyzer = analyzer, initialClip = source,
                            testDistribution = true,
                            onSaveWithMedia = { result, phrase, note, clip, range ->
                                saved = Saved(result, phrase, note, clip, range)
                                if (blockSave) saving.value = true
                            },
                            onGallery = { result, phrase, clip, range, _ ->
                                gallery = Saved(result, phrase, "", clip, range)
                                if (blockGallery) saving.value = true
                            })
                    }
                }
            }
        }
        awaitTag("selected-range")
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(8_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }
    private fun clickTag(tag: String) {
        val node = compose.onNodeWithTag(tag)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }
    private fun analyze() { click("영상 + 소리로 번역하기"); awaitTag("result-action") }
    private fun chooseRange(start: Float, end: Float) {
        compose.onNodeWithTag("clip-range-start").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(start) }
        compose.onNodeWithTag("clip-range-end").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(end) }
    }
    private fun draft() {
        click("기록에 메모·수정 남기기")
        compose.onNodeWithTag("draft-phrase-editor").performScrollTo().performTextReplacement("보호자가 남긴 문장")
        compose.onNodeWithTag("draft-note-editor").performScrollTo().performTextReplacement("우리 아기 메모")
        click("기록 수정 접기")
    }
    private fun back() = compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

    @Test fun resultBackCanLeaveInsteadOfCyclingThroughRangeEditor() {
        start(); analyze()
        clickTag("result-other-range-fixed")
        back(); awaitTag("result-action")
        back()
        compose.onNodeWithTag("confirm-close-capture").assertExists()
        click("계속 보기")
        compose.onNodeWithTag("baby-phrase").assertExists()
        clickTag("capture-back")
        clickTag("confirm-close-capture")
        compose.runOnIdle { assertEquals(1, backCalls) }
    }

    @Test fun sameSourceRangeAndBackPreserveDraftButNewAnalysisStartsFresh() {
        start(); chooseRange(8_000f, 11_000f); analyze(); draft()
        clickTag("result-other-range-fixed")
        compose.onNodeWithTag("selected-range").assertTextEquals("0:08.0 – 0:11.0 · 3초")
        compose.onNodeWithTag("range-reanalysis-notice").assertExists()
        chooseRange(9_000f, 12_000f)
        back()
        awaitTag("result-action")
        click("장면과 함께 저장하기")
        compose.runOnIdle {
            assertEquals(ClipRange(8_000, 11_000), saved!!.range)
            assertEquals(source.uri, saved!!.clip!!.uri)
            assertEquals("보호자가 남긴 문장", saved!!.phrase)
            assertEquals("우리 아기 메모", saved!!.note)
            assertTrue(source.ownedFile!!.exists())
        }
        clickTag("result-other-range-fixed"); awaitTag("selected-range")
        chooseRange(9_000f, 12_000f); analyze()
        click("장면과 함께 저장하기")
        compose.runOnIdle {
            assertEquals(ClipRange(9_000, 12_000), analyzer.ranges.last())
            assertEquals(source.uri, analyzer.uris.last())
            assertEquals("두 번째 아기말", saved!!.phrase)
            assertEquals("", saved!!.note)
            assertEquals(ExpressionBasis.AUDIO, saved!!.result.basis)
            assertEquals(0, backCalls)
        }
    }

    @Test fun pickerCancelAndInvalidFilePreserveResultDraftRangeAndOwnedSource() {
        start(); chooseRange(8_000f, 11_000f); analyze(); draft()
        clickTag("result-other-file")
        compose.onNodeWithTag("result-other-range-fixed").assertIsNotEnabled()
        compose.onNodeWithText("장면과 함께 저장하기").assertIsNotEnabled()
        back()
        compose.onNodeWithTag("result-action").assertExists()
        assertTrue(source.ownedFile!!.exists())
        compose.runOnIdle { registry.reply(null) }
        compose.onNodeWithTag("result-other-range-fixed").assertIsEnabled()
        val invalid = File(compose.activity.cacheDir, "missing-navigation-${UUID.randomUUID()}.mp4")
        clickTag("result-other-file")
        compose.runOnIdle { registry.reply(Uri.fromFile(invalid)) }
        awaitTag("capture-message")
        compose.onNodeWithTag("result-action").assertExists()
        click("영상 카드 앨범에 저장")
        click("장면과 함께 저장하기")
        compose.runOnIdle {
            assertEquals("보호자가 남긴 문장", saved!!.phrase)
            assertEquals("우리 아기 메모", saved!!.note)
            assertEquals(source.uri, saved!!.clip!!.uri)
            assertEquals(ClipRange(8_000, 11_000), saved!!.range)
            assertEquals(saved!!.phrase, gallery!!.phrase)
            assertEquals(saved!!.range, gallery!!.range)
            assertTrue(source.ownedFile!!.exists())
            assertEquals(1, analyzer.ranges.size)
            assertArrayEquals(arrayOf("video/*", "audio/*"), registry.mimeTypes)
        }
    }

    @Test fun successfulOtherFileDeletesOnlyOwnedSourceAndClearsDraft() {
        start(); analyze(); draft()
        val imported = fixture()
        clickTag("result-other-file")
        compose.runOnIdle { registry.reply(Uri.fromFile(imported)) }
        awaitTag("selected-range")
        compose.waitUntil(8_000) { !source.ownedFile!!.exists() }
        analyze(); click("장면과 함께 저장하기")
        compose.runOnIdle {
            assertEquals(Uri.fromFile(imported), saved!!.clip!!.uri)
            assertNull(saved!!.clip!!.ownedFile)
            assertEquals("두 번째 아기말", saved!!.phrase)
            assertEquals("", saved!!.note)
        }
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        assertTrue("Provider-owned source must survive closing flow", imported.exists())
    }

    @Test fun pickerLaunchFailureReleasesLockAndPreservesSource() {
        start(); analyze(); registry.failLaunch = true
        clickTag("result-other-file"); awaitTag("capture-message")
        compose.onNodeWithTag("capture-message").assertTextEquals("파일 선택기를 열지 못했어요. 현재 장면은 그대로 있어요.")
        compose.onNodeWithTag("result-other-range-fixed").assertIsEnabled()
        click("장면과 함께 저장하기")
        compose.runOnIdle { assertEquals(source.uri, saved!!.clip!!.uri); assertTrue(source.ownedFile!!.exists()) }
    }

    private fun assertNavigationLocked() {
        listOf("result-other-range", "result-other-file", "result-new-capture", "result-other-range-fixed", "capture-back", "capture-close")
            .forEach { compose.onNodeWithTag(it).assertIsNotEnabled() }
        compose.onNodeWithText("카드 이미지 앨범에 저장").assertIsNotEnabled()
        compose.onNodeWithText("영상 카드 앨범에 저장").assertIsNotEnabled()
        back()
        compose.onNodeWithTag("result-action").assertExists()
        compose.runOnIdle { assertTrue(source.ownedFile!!.exists()); assertEquals(0, backCalls) }
    }

    @Test fun recordSavingLocksEveryNavigationPathAndRetainsSource() {
        start(); analyze(); blockSave = true
        click("장면과 함께 저장하기"); assertNavigationLocked()
        compose.runOnIdle { saving.value = false }
        clickTag("result-other-range-fixed"); awaitTag("selected-range")
        compose.runOnIdle { assertEquals(source.uri, saved!!.clip!!.uri); assertTrue(source.ownedFile!!.exists()) }
    }

    @Test fun gallerySavingLocksEveryNavigationPathAndRetainsSource() {
        start(); analyze(); blockGallery = true
        click("영상 카드 앨범에 저장"); assertNavigationLocked()
        compose.runOnIdle { saving.value = false }
        clickTag("result-other-range-fixed"); awaitTag("selected-range")
        compose.runOnIdle { assertEquals(source.uri, gallery!!.clip!!.uri); assertTrue(source.ownedFile!!.exists()) }
    }

    @Test fun newCaptureRequiresDiscardConfirmationAndCancelKeepsDraft() {
        start(); analyze(); draft()
        clickTag("result-new-capture"); click("계속 보기")
        click("장면과 함께 저장하기")
        compose.runOnIdle { assertEquals("보호자가 남긴 문장", saved!!.phrase); assertTrue(source.ownedFile!!.exists()) }
        clickTag("result-new-capture"); clickTag("confirm-new-capture")
        awaitTag("capture-action")
        compose.onNodeWithTag("result-action").assertDoesNotExist()
        assertFalse(source.ownedFile!!.exists())
    }

    @Test fun cancelledReaderFinishesBeforeOwnedFileDeletionAndCannotPublishLateResult() {
        start(); analyze(); analyzer.gate = CompletableDeferred()
        clickTag("result-other-range-fixed"); click("영상 + 소리로 번역하기")
        compose.waitUntil(8_000) { analyzer.ranges.size == 2 }
        back(); click("다시 촬영하기"); awaitTag("capture-action")
        assertTrue("Cancelled native-style reader still owns its source", source.ownedFile!!.exists())
        analyzer.gate!!.complete(Unit)
        compose.waitUntil(8_000) { !source.ownedFile!!.exists() }
        compose.onNodeWithTag("result-action").assertDoesNotExist()
    }

    @Test fun disposedFlowWaitsForCancelledReaderBeforeOwnedFileDeletion() {
        start(); analyzer.gate = CompletableDeferred()
        click("영상 + 소리로 번역하기")
        compose.waitUntil(8_000) { analyzer.ranges.size == 1 }
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        assertTrue("Disposal must not delete a source still used by cancelled IO", source.ownedFile!!.exists())
        analyzer.gate!!.complete(Unit)
        compose.waitUntil(8_000) { !source.ownedFile!!.exists() }
        compose.onNodeWithTag("result-action").assertDoesNotExist()
    }

    @Test fun resultNavigationIsReachableAt360DpAndDoubleFontScale() {
        start(compact = true); analyze()
        compose.onNodeWithTag("result-other-range-fixed").assertIsDisplayed()
        compose.onNodeWithText("장면과 함께 저장하기").assertIsDisplayed()
        listOf("result-other-range", "result-other-file", "result-new-capture").forEach {
            compose.onNodeWithTag(it).performScrollTo().assertIsDisplayed()
        }
        clickTag("result-other-range-fixed"); awaitTag("selected-range")
        compose.onNodeWithTag("capture-back").assertIsDisplayed()
        clickTag("capture-back"); awaitTag("result-action")
        compose.onNodeWithTag("result-other-range-fixed").assertIsDisplayed()
    }

    @Test fun resultUsesOneCardAndSourceReplayWhileEditorKeepsVideoPreview() {
        start()
        compose.onNodeWithTag("clip-video-preview").assertExists()
        compose.onAllNodesWithTag("clip-play").assertCountEquals(1)
        chooseRange(8_000f, 11_000f)
        analyze()
        compose.onNodeWithTag("clip-video-preview").assertDoesNotExist()
        compose.onNodeWithTag("clip-audio-preview").assertDoesNotExist()
        compose.onAllNodesWithTag("baby-phrase").assertCountEquals(1)
        compose.onAllNodesWithTag("clip-play").assertCountEquals(1)
        compose.onNodeWithTag("clip-play").assertTextEquals("선택 구간 다시 보기·듣기")
        compose.waitUntil(8_000) { compose.onAllNodes(hasTestTag("clip-play") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        clickTag("clip-play")
        awaitTag("clip-video-preview")
        compose.waitUntil(8_000) { compose.onAllNodesWithText("재생 멈추기", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("clip-video-preview").assertExists()
        clickTag("clip-play")
        compose.onNodeWithTag("clip-video-preview").assertDoesNotExist()
        compose.onNodeWithTag("clip-play").assertTextEquals("선택 구간 다시 보기·듣기")
        clickTag("clip-play")
        compose.waitUntil(8_000) { compose.onAllNodesWithText("재생 멈추기", substring = true).fetchSemanticsNodes().isNotEmpty() }
        val playbackStates = linkedSetOf<String>()
        try {
            compose.waitUntil(8_000) {
                playbackStates += compose.onNodeWithTag("clip-play").fetchSemanticsNode().config[SemanticsProperties.Text].toString()
                compose.onAllNodesWithTag("clip-video-preview").fetchSemanticsNodes().isEmpty()
            }
        } catch (failure: Throwable) { throw AssertionError("Replay did not end; states=$playbackStates", failure) }
        compose.onNodeWithTag("clip-play").assertTextEquals("선택 구간 다시 보기·듣기")
        clickTag("result-other-range"); awaitTag("selected-range")
        compose.onNodeWithTag("clip-video-preview").assertExists()
        compose.onNodeWithTag("clip-play").assertTextEquals("선택 구간 다시 듣기 · 영상 함께 보기")
        compose.onNodeWithTag("baby-phrase").assertDoesNotExist()
        assertContentStartsAtTop()
    }

    @Test fun shortLandscapeKeepsResultAndRangeActionsReachable() = shortLandscape(1f)

    @Test fun shortLandscapeAtDoubleFontKeepsResultAndRangeActionsReachable() = shortLandscape(2f)

    private fun screenshot(name: String) {
        val folder = File(compose.activity.getExternalFilesDir(null), "qa-nav-layout").apply { mkdirs() }
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try {
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    private fun assertFullyInContent(tag: String) {
        val content = compose.onNodeWithTag("capture-content").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val layoutHeight = compose.onNodeWithTag(tag).fetchSemanticsNode().size.height
        assertTrue("$tag is clipped by the short scroll viewport: $target / $content",
            target.top >= content.top - 1f && target.bottom <= content.bottom + 1f && target.height >= layoutHeight - 1f)
    }

    private fun shortLandscape(scale: Float) {
        start(landscape = true, fontScale = scale); analyze()
        val viewport = compose.onNodeWithTag("capture-flow").fetchSemanticsNode().boundsInRoot
        assertEquals("The test must exercise a short landscape viewport", 2f, viewport.width / viewport.height, .03f)
        compose.onNodeWithTag("capture-back").assertIsDisplayed()
        compose.onNodeWithTag("capture-back").assertTextEquals("닫기")
        compose.onNodeWithTag("result-other-range-fixed").assertDoesNotExist()
        compose.onNodeWithText("장면과 함께 저장하기").assertIsDisplayed()
        assertContentStartsAtTop()
        screenshot("result-landscape-$scale")
        listOf("result-other-range", "result-other-file", "result-new-capture").forEach {
            compose.onNodeWithTag(it).performScrollTo().assertIsDisplayed()
            screenshot("$it-landscape-$scale")
            assertFullyInContent(it)
        }
        clickTag("result-other-range"); awaitTag("selected-range")
        compose.onNodeWithTag("capture-back").assertIsDisplayed()
        assertContentStartsAtTop()
        compose.onNodeWithTag("clip-range-start").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(8_000f) }
        compose.onNodeWithTag("clip-range-end").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(11_000f) }
        compose.onNodeWithTag("selected-range").assertTextEquals("0:08.0 – 0:11.0 · 3초")
        compose.onNodeWithText("영상 + 소리로 번역하기").assertIsDisplayed()
        screenshot("range-landscape-$scale")
        analyze(); assertContentStartsAtTop(); click("장면과 함께 저장하기")
        compose.runOnIdle { assertEquals(ClipRange(8_000, 11_000), saved!!.range); assertEquals(source.uri, saved!!.clip!!.uri) }
    }

    private fun assertContentStartsAtTop() {
        compose.waitForIdle()
        val position = compose.onNodeWithTag("capture-content").fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals("Phase transition must open at the top without discarding selection", 0f, position, .5f)
    }

    private data class Saved(val result: ClipInterpretation, val phrase: String, val note: String,
        val clip: LocalClip?, val range: ClipRange?)

    private class PickerRegistry : ActivityResultRegistry() {
        private var requestCode: Int? = null
        var mimeTypes: Array<String>? = null
        var failLaunch = false
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            check(contract is ActivityResultContracts.OpenDocument) { "Unexpected hardware/permission launch in navigation test" }
            if (failLaunch) error("Picker unavailable")
            this.requestCode = requestCode
            @Suppress("UNCHECKED_CAST")
            mimeTypes = input as Array<String>
        }
        fun reply(uri: Uri?) {
            val code = checkNotNull(requestCode)
            requestCode = null
            dispatchResult(code, if (uri == null) Activity.RESULT_CANCELED else Activity.RESULT_OK,
                uri?.let { Intent().setData(it) })
        }
    }

    private class StubAnalyzer : ClipAnalyzer {
        val ranges = mutableListOf<ClipRange>()
        val uris = mutableListOf<Uri>()
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun analyze(context: Context, uri: Uri, range: ClipRange, visual: Boolean): ClipAnalysisResult {
            ranges.add(range); uris.add(uri)
            gate?.let { withContext(NonCancellable) { it.await() } }
            return ClipAnalysisResult(ClipInterpretation(if (ranges.size == 1) "첫 번째 아기말" else "두 번째 아기말",
                "옹알이", "합성 관찰", "답해 주세요.", range.label(), false, basis = ExpressionBasis.AUDIO))
        }
    }
}
