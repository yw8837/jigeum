package com.moa.babylanguage

import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** UI substitutes supply model output only; editing, restoration and callbacks are real. */
class ExpressionBasisUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val visible = mutableStateOf(true)
    private lateinit var restoration: StateRestorationTester
    private val analyzer = StubAnalyzer()
    private var saved: Pair<ClipInterpretation, String>? = null
    private var gallery: Pair<ClipInterpretation, String>? = null
    private var started = false

    @After fun dispose() {
        if (started) {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
        }
    }

    private fun start() {
        val clip = LocalClip(Uri.fromFile(File(compose.activity.cacheDir, "missing-basis-ui-test.mp4")), 10_000, true)
        restoration = StateRestorationTester(compose)
        started = true
        restoration.setContent { MaterialTheme {
            if (visible.value) CaptureFlow({ true }, {}, false,
                onSave = { result, phrase, _ -> saved = result to phrase },
                passContent = { Text("이용권") }, analyzer = analyzer, initialClip = clip,
                testDistribution = true,
                onGallery = { result, phrase, _, _, _ -> gallery = result to phrase })
        } }
        await("어느 순간을 들어볼까요?")
    }

    private fun await(text: String) {
        compose.waitUntil(8_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }

    @Test fun actualBasisAndCaregiverEditSurviveRestoreGalleryAndSave() {
        start(); click("영상 + 소리로 번역하기")
        await(ExpressionBasis.label(ExpressionBasis.AUDIO_VISUAL))
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("expression-basis").assertTextEquals(ExpressionBasis.label(ExpressionBasis.AUDIO_VISUAL))
        assertEquals(1, analyzer.calls)
        click("기록에 메모·수정 남기기")
        compose.onNodeWithTag("draft-phrase-editor").performScrollTo().performTextReplacement("보호자가 고친 문장이에요.")
        click("기록 수정 접기")
        compose.onNodeWithTag("expression-basis").assertTextEquals(ExpressionBasis.label(ExpressionBasis.CAREGIVER))
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("expression-basis").assertTextEquals(ExpressionBasis.label(ExpressionBasis.CAREGIVER))
        click("카드 이미지 앨범에 저장")
        compose.runOnIdle {
            assertEquals(ExpressionBasis.CAREGIVER, gallery!!.first.basis)
            assertEquals("보호자가 고친 문장이에요.", gallery!!.second)
        }
        click("이 문장 저장하기")
        compose.runOnIdle {
            assertEquals(ExpressionBasis.CAREGIVER, saved!!.first.basis)
            assertEquals("보호자가 고친 문장이에요.", saved!!.second)
        }
    }

    @Test fun completedVisualObservationCannotRelabelAudioOnlySentence() {
        analyzer.basis = ExpressionBasis.AUDIO
        start(); click("영상 + 소리로 번역하기")
        await(ExpressionBasis.label(ExpressionBasis.AUDIO))
        compose.onNodeWithTag("expression-basis").assertTextEquals(ExpressionBasis.label(ExpressionBasis.AUDIO))
        click("이 문장 저장하기")
        compose.runOnIdle { assertEquals(ExpressionBasis.AUDIO, saved!!.first.basis) }
    }

    @Test fun withheldObservationAndSuggestedRangeDoNotCreateOrAutoAnalyzeSentence() {
        analyzer.withhold = true
        start(); click("영상 + 소리로 번역하기")
        await("아기 발성 종류를 아직 구분하지 못했어요.")
        compose.onNodeWithTag("withheld-visual-evidence").performScrollTo().assertTextEquals("같은 구간에서 얼굴을 관찰했어요.")
        compose.onNodeWithTag("baby-phrase").assertDoesNotExist()
        compose.onNodeWithText("이 문장 저장하기").assertDoesNotExist()
        compose.onNodeWithTag("apply-sound-range").performScrollTo().performClick()
        compose.onNodeWithTag("selected-range").assertTextEquals("0:02.0 – 0:05.0 · 3초")
        compose.onNodeWithTag("withheld-visual-evidence").assertDoesNotExist()
        compose.onNodeWithTag("apply-sound-range").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, analyzer.calls); assertNull(saved); assertNull(gallery) }
        click("영상 + 소리로 번역하기")
        await("아기 발성 종류를 아직 구분하지 못했어요.")
        compose.runOnIdle { assertEquals(2, analyzer.calls); assertEquals(ClipRange(2_000, 5_000), analyzer.lastRange) }
        compose.onNodeWithTag("clip-range-end").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(8_000f) }
        compose.onNodeWithTag("withheld-visual-evidence").assertDoesNotExist()
        compose.onNodeWithTag("apply-sound-range").assertDoesNotExist()
        compose.onNodeWithTag("baby-phrase").assertDoesNotExist()
    }

    private class StubAnalyzer : ClipAnalyzer {
        var calls = 0
        var lastRange: ClipRange? = null
        var basis = ExpressionBasis.AUDIO_VISUAL
        var withhold = false
        override suspend fun analyze(context: Context, uri: Uri, range: ClipRange, visual: Boolean): ClipAnalysisResult {
            calls++; lastRange = range
            return if (withhold) ClipAnalysisResult(problem = "아기 발성 종류를 아직 구분하지 못했어요.",
                visualEvidence = VisualEvidence(VisualEvidenceStatus.COMPLETE, "같은 구간에서 얼굴을 관찰했어요."),
                suggestedRange = ClipRange(2_000, 5_000))
            else ClipAnalysisResult(ClipInterpretation("히히, 내 소리 들려요?", "웃음", "합성 관찰", "답해 주세요.",
                range.label(), visual, basis = basis))
        }
    }
}
