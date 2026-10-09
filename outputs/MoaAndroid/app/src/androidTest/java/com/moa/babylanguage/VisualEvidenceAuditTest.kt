package com.moa.babylanguage

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Failure reporting and actual bundled face-model execution; no baby intent/identity claim. */
@RunWith(AndroidJUnit4::class)
class VisualEvidenceAuditTest {
    private val range = ClipRange(8_000, 11_000)
    private val audio = SoundAnalysis(listOf(SoundCue("울음", .8f)))
    private val shape = AcousticShape(.75f, false, 1)

    @Test fun failedVisualModelKeepsAudioPhraseWithoutClaimingVisualSuccess() {
        val failed = VideoFrameSummary(3, 3, 0, 0, 0, true, false)
        val audioOnly = EvidenceInterpreter.interpret(audio, shape, range).interpretation!!
        val result = EvidenceInterpreter.interpret(audio, shape, range, failed.visualEvidence()).interpretation!!
        assertEquals(audioOnly.phrase, result.phrase)
        assertTrue(result.evidence.contains("표정 관찰을 완료하지 못했어요"))
        assertFalse("No visual inference completed; this must not be a visual success", result.visual)
        assertEquals("Failure must also be available outside collapsed evidence", failed.visualEvidence().notice, result.visualNotice)
        assertNotNull(result.visualNotice)
        assertEquals(VisualEvidenceStatus.FAILED, failed.visualEvidence().status)
    }

    @Test fun completedAnalysisWithNoFaceIsNotModelFailure() {
        val noFace = VideoFrameSummary(3, 3, 0, 0, 3, true, true)
        val result = EvidenceInterpreter.interpret(audio, shape, range, noFace.visualEvidence()).interpretation!!
        assertTrue("Looking for a face and finding none is a completed observation", result.visual)
        assertTrue(result.evidence.contains("얼굴을 찾지 못했어요"))
        assertFalse(result.evidence.contains("관찰을 완료하지 못했어요"))
        assertEquals(VisualEvidenceStatus.COMPLETE, noFace.visualEvidence().status)
        assertNull(result.visualNotice)
    }

    @Test fun partialModelFailureReportsOnlyActuallyAnalyzedFrames() {
        val partial = VideoFrameSummary(5, 5, 1, 1, 2, true, true)
        val evidence = partial.visualEvidence()
        val result = EvidenceInterpreter.interpret(audio, shape, range, evidence).interpretation!!
        assertEquals(VisualEvidenceStatus.PARTIAL, evidence.status)
        assertTrue(result.visual)
        assertTrue(result.evidence.contains("선택 구간 2개 장면 중 얼굴 1개"))
        assertEquals("전체 5개 장면 중 2개만 표정 관찰을 마쳤어요.", result.visualNotice)
    }

    @Test fun visualFailureCannotCreatePhraseWithoutAudioEvidence() {
        val failed = VideoFrameSummary(3, 3, 0, 0, 0, true, false)
        assertNull(EvidenceInterpreter.interpret(SoundAnalysis(emptyList()), shape, range,
            failed.visualEvidence()).interpretation)
    }

    @Test fun unreadableVideoReportsVisualFailureAndKeepsAvailableAudioInterpretation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val malformed = File(context.cacheDir, "visual-audit-invalid-${UUID.randomUUID()}.mp4")
        try {
            malformed.writeText("not a readable video")
            val evidence = withContext(Dispatchers.Default) {
                inspectVideoEvidence(context, Uri.fromFile(malformed), range.startMs, range.endMs)
            }
            assertEquals(VisualEvidenceStatus.FAILED, evidence.status)
            val result = EvidenceInterpreter.interpret(audio, shape, range, evidence).interpretation!!
            assertEquals(EvidenceInterpreter.interpret(audio, shape, range).interpretation!!.phrase, result.phrase)
            assertFalse(result.visual)
            assertNotNull(result.visualNotice)
        } finally { malformed.delete() }
    }

    @Test fun cancellationIsNotConvertedToVisualFailure() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var cancelled = false
        val request = launch {
            cancel()
            try {
                inspectVideoEvidence(context, Uri.EMPTY, range.startMs, range.endMs)
                fail("Cancellation must not return a failure result")
            } catch (_: CancellationException) { cancelled = true }
        }
        request.join()
        assertTrue(cancelled)
    }

    @Test fun actualSelectedVideoRangeRunsFaceModelAndReportsNoFace() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = File(context.cacheDir, "visual-audit-${UUID.randomUUID()}.mp4")
        try {
            instrumentation.context.assets.open("range-silence-tone.mp4").use { input ->
                fixture.outputStream().use { input.copyTo(it) }
            }
            val summary = withContext(Dispatchers.Default) {
                inspectVideo(context, Uri.fromFile(fixture), range.startMs, range.endMs)
            }
            assertEquals("Selected later interval is three seconds", 3, summary.durationSeconds)
            assertEquals(15, summary.sampleCount)
            assertEquals("Every sampled frame must actually reach the bundled model", 15, summary.analyzedFrames)
            assertTrue(summary.modelAvailable)
            assertEquals("Synthetic color/tone fixture has no face", 0, summary.faceFrames)
            assertEquals(0, summary.mouthOpenFrames)
            assertEquals(VisualEvidenceStatus.COMPLETE, summary.visualEvidence().status)
            assertTrue(summary.visualEvidence().evidence.contains("얼굴을 찾지 못했어요"))
        } finally { fixture.delete() }
    }
}
