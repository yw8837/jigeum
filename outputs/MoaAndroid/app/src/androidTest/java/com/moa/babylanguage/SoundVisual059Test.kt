package com.moa.babylanguage

import org.junit.Assert.*
import org.junit.Test

class SoundVisual059Test {
    private val range = ClipRange(0, 10_000)
    private val shape = AcousticShape(.8f, false, 3)
    private fun audio(label: String) = SoundAnalysis(emptyList(), frames = (0..9).map {
        SoundFrame(it * 975L, listOf(SoundCue(label, .95f)))
    })
    private fun visual(mouth: Float? = .3f, smile: Float? = .8f, count: Int = 1) = VisualEvidence(
        VisualEvidenceStatus.COMPLETE, "얼굴 관찰", frames = listOf(500L, 1500L, 2500L).map {
            VisualFrameObservation(it, count, count == 1, mouth, .9f, smile)
        })

    @Test fun compatibleVisualChangesExpressionAndActualBasisOnly() {
        for (label in listOf("아기 웃음 후보", "옹알이 후보", "어린 말소리 후보")) {
            val plain = EvidenceInterpreter.interpret(audio(label), shape, range).interpretation!!
            val combined = EvidenceInterpreter.interpret(audio(label), shape, range, visual()).interpretation!!
            assertEquals("AUDIO", plain.basis)
            assertEquals("AUDIO_VISUAL", combined.basis)
            assertNotEquals(plain.phrase, combined.phrase)
        }
    }
    @Test fun missingMultipleFailedAndOutOfWindowFacesCannotChangeBasis() {
        val base = EvidenceInterpreter.interpret(audio("아기 웃음 후보"), shape, range).interpretation!!
        for (frames in listOf(VisualEvidence(VisualEvidenceStatus.COMPLETE, "얼굴 없음"), visual(count=2),
            visual().copy(status=VisualEvidenceStatus.FAILED),
            visual().copy(frames=visual().frames.map { it.copy(requestedOffsetMs=20_000) }),
            visual().copy(frames=visual().frames.take(1)),
            visual(mouth=Float.NaN, smile=Float.NaN))) {
            val result = EvidenceInterpreter.interpret(audio("아기 웃음 후보"), shape, range, frames).interpretation!!
            assertEquals(base.phrase, result.phrase)
            assertEquals("AUDIO", result.basis)
        }
    }
    @Test fun adultMusicSilenceAndMixedVoicesCannotBeRescuedByFace() {
        val entries = listOf(audio("말소리(화자 미확인)"), audio("배경 소리"),
            SoundAnalysis(emptyList(), "무음", issue=AnalysisIssue.SILENCE),
            SoundAnalysis(listOf(SoundCue("울음", .9f), SoundCue("아기 웃음 후보", .9f))))
        for (sound in entries) {
            val result = EvidenceInterpreter.interpret(sound, shape, range, visual())
            assertNull(result.interpretation)
            assertNotNull(result.problem)
            assertEquals(visual(), result.visualEvidence)
        }
    }
    @Test fun smileCannotTurnCryIntoHappinessAndClosedEyesDoNotImplySleep() {
        val result = EvidenceInterpreter.interpret(audio("울음"), shape, range, visual()).interpretation!!
        assertEquals("울음", result.sound)
        assertEquals("AUDIO", result.basis)
        for (word in listOf("웃", "잠들", "졸려", "배고", "행복")) assertFalse(result.phrase.contains(word))
        val neutral = EvidenceInterpreter.interpret(audio("옹알이 후보"), shape, range, visual(mouth=.01f, smile=.01f)).interpretation!!
        assertEquals("AUDIO", neutral.basis)
        assertFalse(neutral.phrase.contains("졸"))
    }
    @Test fun failureReasonsDistinguishBackgroundFromUnconfirmedVoice() {
        assertEquals(AnalysisIssue.BACKGROUND_DOMINANT, SoundTimeline.decide(audio("배경 소리")).issue)
        assertEquals(AnalysisIssue.INFANT_UNCONFIRMED, SoundTimeline.decide(audio("말소리(화자 미확인)")).issue)
        val mixed=SoundAnalysis(listOf(SoundCue("울음",.9f),SoundCue("아기 웃음 후보",.9f)))
        assertEquals(AnalysisIssue.MIXED_VOCAL, SoundTimeline.decide(mixed).issue)
    }
    @Test fun sampledFaceNeverClaimsBabyIdentityOrSpeechSynchronization() {
        val sound=SoundAnalysis(listOf(SoundCue("아기 웃음 후보",.95f)))
        val result=EvidenceInterpreter.interpret(sound,shape,range,visual()).interpretation!!
        assertEquals("AUDIO_VISUAL", result.basis)
        for (claim in listOf("입꼬리도", "입을 벌리고", "내 얼굴")) assertFalse(result.phrase.contains(claim))
        assertTrue(result.evidence.contains("동시에 움직였는지는 확인하지"))
        val duplicate=visual().copy(frames=List(3){visual().frames.first()})
        assertEquals("AUDIO", EvidenceInterpreter.interpret(sound,shape,range,duplicate).interpretation!!.basis)
    }
    @Test fun recommendationIsBoundedToSelectionAndDoesNotInventBabyIdentity() {
        val samples=FloatArray(320_000) { if(it in 192_000..255_999) .1f else 0f }
        val selection=ClipRange(20_000,40_000)
        val suggested=AudibleRange.suggest(samples,selection)!!
        assertTrue(suggested.startMs >= selection.startMs)
        assertTrue(suggested.endMs <= selection.endMs)
        assertEquals(5_000L,suggested.durationMs)
        assertTrue(suggested.startMs in 31_000..32_000)
        assertNull(AudibleRange.suggest(FloatArray(320_000),selection))
        assertNull(AudibleRange.suggest(FloatArray(320_000){.1f},selection))
        assertNull(AudibleRange.suggest(FloatArray(320_000){Float.NaN},selection))
        assertNull(AudibleRange.suggest(samples.take(80_000).toFloatArray(),ClipRange(0,5_000)))
    }
}
