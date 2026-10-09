package com.moa.babylanguage

import org.junit.Assert.*
import org.junit.Test

class IntervalEvidenceTest {
    private val range = ClipRange(0, 10_000)
    private val shape = AcousticShape(.8f, true, 7, strongestSecondRms = .1)
    private fun voice(at: Long) = SoundFrame(at, listOf(SoundCue("옹알이 후보", .5f)), .05)
    private fun conflict(at: Long) = SoundFrame(at,
        listOf(SoundCue("아기 웃음 후보", .8f), SoundCue("울음", .8f)), .05)
    private fun mixed() = SoundAnalysis(emptyList(), frames =
        listOf(voice(0), voice(975), conflict(1950), conflict(2925), conflict(3900)))
    private fun visual(start: Long = 0, end: Long = 10_000): VisualEvidence {
        val frames = (start until end step 200).map { at ->
            VisualFrameObservation(at, 1, true, if (at % 800 < 400) .04f else .3f,
                presentationOffsetMs = at)
        }
        return VisualEvidence(VisualEvidenceStatus.COMPLETE, "영상 관찰", frames = frames)
    }
    private fun interpret(audio: SoundAnalysis, frames: VisualEvidence? = null) =
        EvidenceInterpreter.interpret(audio, shape, range, frames)

    @Test fun confirmedPartSurvivesMixedSelectionWithoutInventingEmotionOrWholeClipRhythm() {
        val audio = mixed()
        assertEquals(AnalysisIssue.MIXED_VOCAL, SoundTimeline.decide(audio, 10_000).issue)
        val output = interpret(audio).interpretation!!
        assertEquals("아기 발성", output.sound)
        assertTrue(output.evidence.contains("일부 구간"))
        assertTrue(output.evidence.contains("다른 소리는 종류를 구분하지 못해"))
        assertTrue(output.evidence.contains("0:00–0:01 범위 아기 발성 후보"))
        for (word in listOf("울음", "웃음", "으앙", "응애", "배고", "졸려", "쉬었다", "커졌"))
            assertFalse(output.phrase, output.phrase.contains(word))
        assertFalse(output.evidence.contains("뒤쪽에서 소리가 더 커졌어요"))
    }
    @Test fun repeatedConflictAndOneStrongWindowRemainHeld() {
        val a = mixed().copy(frames = listOf(voice(0).copy(cues = listOf(SoundCue("옹알이 후보", .99f))),
            conflict(975), conflict(1950)))
        assertNull(interpret(a, visual()).interpretation)
    }
    @Test fun scatteredDuplicateOverlappingAndInvalidWindowsCannotMakeAPartialRun() {
        val source = mixed()
        val variants = listOf(
            listOf(voice(0), voice(1950), conflict(3900)),
            listOf(voice(0), voice(0), conflict(1950)),
            listOf(voice(0), voice(480), conflict(1950)),
            source.frames.reversed(), source.frames.map { it.copy(windowRms = null) },
            source.frames.map { it.copy(windowRms = .0029) },
            source.frames.map { it.copy(windowRms = Double.NaN) },
            source.frames.map { it.copy(cues = it.cues + SoundCue("unknown", Float.NaN)) },
            listOf(voice(0), voice(975), conflict(1500)))
        for (frames in variants) assertNull(SoundTimeline.partialObservation(source.copy(frames = frames), 10_000))
    }
    @Test fun childVoiceStillNeedsThreeAdjacentWindows() {
        fun child(at: Long) = voice(at).copy(cues = listOf(SoundCue("어린 말소리 후보", .7f)))
        assertNull(SoundTimeline.partialObservation(mixed().copy(frames =
            listOf(child(0), child(975), conflict(1950), conflict(2925))), 10_000))
        val audio = mixed().copy(frames = listOf(child(0), child(975), child(1950), conflict(2925), conflict(3900)))
        assertTrue(SoundTimeline.partialObservation(audio, 10_000)!!.partial)
    }
    @Test fun existingOutputAndAudioFailuresArePreserved() {
        val accepted = SoundAnalysis(emptyList(), frames = listOf(voice(0), voice(975)))
        assertNull(SoundTimeline.partialObservation(accepted, 10_000))
        assertEquals("옹알이", interpret(accepted).interpretation!!.sound)
        for (issue in listOf(AnalysisIssue.SILENCE, AnalysisIssue.MODEL_FAILURE, AnalysisIssue.INVALID_AUDIO)) {
            val result = interpret(mixed().copy(problem = "실패", issue = issue), visual())
            assertNull(result.interpretation); assertEquals(issue, result.issue)
        }
    }
    @Test fun mouthMotionCannotAuthorizeAdultAudioOrSilence() {
        for (label in listOf("말소리(화자 미확인)", "웃음(화자 미확인)", "배경 소리")) {
            val audio = SoundAnalysis(emptyList(), frames = (0..9).map {
                SoundFrame(it * 975L, listOf(SoundCue(label, .99f)), .1)
            })
            assertNull(interpret(audio, visual()).interpretation)
        }
        assertNull(interpret(SoundAnalysis(emptyList()), visual()).interpretation)
    }
    @Test fun broadBabbleUsesIntervalObservationWithoutInventingAnOnset() {
        val audio = SoundAnalysis(emptyList(), babbleWindows = listOf(BabbleSupportWindow(0, 10_000, .1)))
        val plain = interpret(audio).interpretation!!
        val result = interpret(audio, visual()).interpretation!!
        assertEquals("AUDIO_VISUAL", result.basis)
        assertTrue(result.phrase.startsWith(plain.phrase))
        assertTrue(result.evidence.contains("넓은 소리 후보 범위"))
        assertTrue(result.evidence.contains("화면의 사람이 소리를 냈다는 뜻은 아니에요"))
        assertFalse(result.evidence.contains("같은 시간대의 소리 후보 창"))
    }
    @Test fun twentySecondLateBroadWindowCannotBorrowEarlyMouthMotion() {
        val decision = SoundDecision(broadWindows = listOf(BabbleSupportWindow(10_000, 20_000)))
        assertTrue(VisualSoundAlignment.find(decision, visual(0, 8_000).frames, 20_000).isEmpty())
        assertTrue(VisualSoundAlignment.find(decision, visual(12_000, 20_000).frames, 20_000).isNotEmpty())
    }
    @Test fun broadWindowsCannotMergeIntoContinuousSpeechOrBridgeUnknownFace() {
        val d = SoundDecision(broadWindows = listOf(BabbleSupportWindow(0, 1000), BabbleSupportWindow(1000, 2000)))
        assertTrue(VisualSoundAlignment.find(d, visual(0, 2000).frames, 10_000).isEmpty())
        val broad = SoundDecision(broadWindows = listOf(BabbleSupportWindow(0, 10_000)))
        val isolated = visual().frames.map { if (it.requestedOffsetMs % 1000 == 0L) it.copy(valid = false) else it }
        assertTrue(VisualSoundAlignment.find(broad, isolated, 10_000).isEmpty())
    }
    @Test fun malformedVisualsAndUnalignedAudioDoNotAuthorizeCoObservation() {
        val audio = SoundAnalysis(emptyList(), babbleWindows = listOf(BabbleSupportWindow(0, 10_000)))
        val source = visual()
        for (frames in listOf(source.frames.reversed(), source.frames + source.frames.last(),
            source.frames.map { it.copy(mouthOpening = Float.NaN) },
            source.frames.map { it.copy(presentationOffsetMs = null) },
            source.frames.map { it.copy(faceCount = 2) },
            source.frames.map { it.copy(presentationOffsetMs = it.requestedOffsetMs + 10_000) })) {
            assertEquals("AUDIO", interpret(audio, source.copy(frames = frames)).interpretation!!.basis)
        }
        assertEquals("AUDIO", EvidenceInterpreter.interpret(audio, shape, range, source,
            audioTimelineAligned = false).interpretation!!.basis)
    }
}
