package com.moa.babylanguage

import org.junit.Assert.*
import org.junit.Test

/** Temporal observation correspondence, never face identity, emotion or lip sync. */
class VisualTemporalAlignmentTest {
    private val range = ClipRange(0, 10_000)
    private val shape = AcousticShape(.8f, false, 3)
    private fun sound(vararg moments: Pair<Long, String>) = SoundAnalysis(emptyList(), frames =
        moments.map { (at, label) -> SoundFrame(at, listOf(SoundCue(label, .95f))) })
    private fun visual(mouth: (Long) -> Float = { .04f }, smile: (Long) -> Float = { .01f },
                       missingAt: Long? = null) : VisualEvidence {
        val frames = (0..19).map { index ->
            val at = index * 500L
            VisualFrameObservation(at, if (at == missingAt) 0 else 1, at != missingAt,
                mouth(at), mouthCornerLift = smile(at), presentationOffsetMs = at)
        }
        return VisualEvidence(VisualEvidenceStatus.COMPLETE, "시간순 관찰", frames = frames,
            timeline = VisualTimeline.summarize(frames, range.durationMs))
    }
    private fun result(audio: SoundAnalysis, frames: VisualEvidence) =
        EvidenceInterpreter.interpret(audio, shape, range, frames).interpretation!!

    @Test fun lateSmileCannotDecorateAnEarlyLaugh() {
        val audio = sound(0L to "아기 웃음 후보", 975L to "아기 웃음 후보")
        val frames = visual(smile = { if (it in 5_000..6_000) .8f else .01f })
        assertEquals("AUDIO", result(audio, frames).basis)
    }
    @Test fun lateMouthMotionCannotDecorateEarlyBabble() {
        val audio = sound(0L to "옹알이 후보", 975L to "옹알이 후보")
        val frames = visual(mouth = { if (it == 5_500L || it == 6_500L) .3f else .04f })
        assertEquals("AUDIO", result(audio, frames).basis)
    }
    @Test fun orderedBabbleThenLaughCanUseBothCorrespondingVisualIntervals() {
        val audio = sound(0L to "옹알이 후보", 975L to "옹알이 후보", 1_950L to "옹알이 후보",
            5_850L to "아기 웃음 후보", 6_825L to "아기 웃음 후보")
        val frames = visual(mouth = { if (it == 500L || it == 1_500L) .3f else .04f },
            smile = { if (it in 6_000..7_500) .8f else .01f })
        val output = result(audio, frames)
        assertEquals("AUDIO_VISUAL", output.basis)
        assertTrue(output.evidence.contains("같은 시간대"))
        assertTrue(output.evidence.contains("입 변화"))
        assertTrue(output.evidence.contains("입꼬리"))
    }
    @Test fun isolatedSmileSamplesSeparatedByMissingFaceCannotCountAsARun() {
        val audio = sound(0L to "아기 웃음 후보", 975L to "아기 웃음 후보", 1_950L to "아기 웃음 후보")
        val frames = visual(smile = { if (it == 500L || it == 1_500L) .8f else .01f }, missingAt = 1000)
        assertEquals("AUDIO", result(audio, frames).basis)
    }
    @Test fun coincidingSmileKeepsCryAndDoesNotInventHappyOrSleepyBaby() {
        val output = result(sound(0L to "울음", 975L to "울음"), visual(smile = { .8f }))
        assertEquals("AUDIO", output.basis)
        assertEquals("울음", output.sound)
        for (word in listOf("행복", "졸려", "배고", "웃는")) assertFalse(output.phrase.contains(word))
    }
    @Test fun sameTimeLaughAndMouthMotionKeepTheirOriginalAudioEvents() {
        val laugh = result(sound(0L to "아기 웃음 후보", 975L to "아기 웃음 후보"),
            visual(smile = { if (it < 2000) .8f else .01f }))
        assertEquals("AUDIO_VISUAL", laugh.basis)
        assertEquals("아기 웃음", laugh.sound)
        val babble = result(sound(0L to "옹알이 후보", 975L to "옹알이 후보", 1_950L to "옹알이 후보"),
            visual(mouth = { if (it == 500L || it == 1_500L) .3f else .04f }))
        assertEquals("AUDIO_VISUAL", babble.basis)
        assertEquals("옹알이", babble.sound)
    }
    @Test fun unrelatedVisualGapDoesNotEraseLocallyCompleteMouthRun() {
        val frames = visual(mouth = { if (it == 500L || it == 1_500L) .3f else .04f }, missingAt = 8_000)
        val output = result(sound(0L to "옹알이 후보", 975L to "옹알이 후보", 1_950L to "옹알이 후보"),
            frames.copy(status = VisualEvidenceStatus.PARTIAL, notice = "후반 관찰 누락"))
        assertEquals("AUDIO_VISUAL", output.basis)
        assertEquals("후반 관찰 누락", output.visualNotice)
        assertTrue(output.evidence.contains("0~2.5초 입 변화"))
    }
    @Test fun malformedOrMissingPtsNeverBorrowTheSuppliedTimeline() {
        val audio = sound(0L to "아기 웃음 후보", 975L to "아기 웃음 후보")
        val source = visual(smile = { .8f })
        for (frames in listOf(source.frames.reversed(), source.frames + source.frames.last(),
            source.frames.mapIndexed { i, f -> if (i == 1) f.copy(presentationOffsetMs = null) else f },
            source.frames.mapIndexed { i, f -> if (i == 1) f.copy(presentationOffsetMs = -1) else f },
            source.frames.map { it.copy(presentationOffsetMs = it.presentationOffsetMs!! + 10_000) })) {
            assertEquals("AUDIO", result(audio, source.copy(frames = frames)).basis)
        }
    }
    @Test fun invalidSmileMultipleFacesAndFailedVisualCannotCreateMatch() {
        val audio = sound(0L to "아기 웃음 후보", 975L to "아기 웃음 후보")
        val source = visual(smile = { .8f })
        val candidates = listOf(source.copy(status = VisualEvidenceStatus.FAILED),
            source.copy(frames = source.frames.map { it.copy(mouthCornerLift = Float.NaN) }),
            source.copy(frames = source.frames.map { it.copy(mouthCornerLift = 1.2f) }),
            source.copy(frames = source.frames.map { it.copy(valid = false) }),
            source.copy(frames = source.frames.map { if (it.presentationOffsetMs == 8000L) it.copy(faceCount = 2) else it }))
        for (frames in candidates) assertEquals("AUDIO", result(audio, frames).basis)
    }
    @Test fun mouthChangesCannotBridgeAnUnacceptedSoundWindowOrFaceGap() {
        val audio = sound(0L to "옹알이 후보", 1_950L to "옹알이 후보")
        val frames = visual(mouth = { if (it == 500L || it == 2_500L) .3f else .04f })
        assertEquals("AUDIO", result(audio, frames).basis)
        val continuous = sound(0L to "옹알이 후보", 975L to "옹알이 후보", 1_950L to "옹알이 후보")
        assertEquals("AUDIO", result(continuous, visual(mouth = {
            if (it == 500L || it == 1_500L) .3f else .04f }, missingAt = 1000)).basis)
    }
    @Test fun broadAstWindowHasNoPreciseVocalOnsetToAlign() {
        val audio = SoundAnalysis(emptyList(), babbleWindows = listOf(BabbleSupportWindow(0, 10_000)))
        val frames = visual(mouth = { if (it == 500L || it == 1_500L) .3f else .04f })
        assertEquals("AUDIO", result(audio, frames).basis)
    }
    @Test fun faceNeverRescuesSilenceAdultSpeechOrBackground() {
        for (audio in listOf(sound(0L to "말소리(화자 미확인)", 975L to "말소리(화자 미확인)"),
            sound(0L to "배경 소리", 975L to "배경 소리"), SoundAnalysis(emptyList()),
            SoundAnalysis(emptyList(), "무음", issue = AnalysisIssue.SILENCE))) {
            assertNull(EvidenceInterpreter.interpret(audio, shape, range, visual(smile = { .8f })).interpretation)
        }
    }
    @Test fun smileExactlyAfterVocalWindowDoesNotCountAsCoincident() {
        val decision = SoundDecision(moments = listOf(VocalMoment(VocalEvent.LAUGH, 0)))
        val frames = listOf(975L, 1475L).map { at ->
            VisualFrameObservation(at, 1, true, .04f, mouthCornerLift = .8f, presentationOffsetMs = at)
        }
        assertTrue(VisualSoundAlignment.find(decision, frames, 2000).isEmpty())
    }
    @Test fun nonzeroTwentySecondSelectionUsesAbsoluteDisplayedVisualTimesAndIsDeterministic() {
        val selection = ClipRange(30_000, 50_000)
        val audio = sound(17_550L to "아기 웃음 후보", 18_525L to "아기 웃음 후보")
        val frames = (0..39).map { index ->
            val at = index * 500L
            VisualFrameObservation(at, 1, true, .04f, mouthCornerLift = if (at >= 18_000) .8f else .01f,
                presentationOffsetMs = at)
        }
        val visual = VisualEvidence(VisualEvidenceStatus.COMPLETE, "시간순 관찰", frames = frames,
            timeline = VisualTimeline.summarize(frames, selection.durationMs))
        val first = EvidenceInterpreter.interpret(audio, shape, selection, visual).interpretation!!
        assertEquals("AUDIO_VISUAL", first.basis)
        assertTrue(first.evidence.contains("48~49초 입꼬리"))
        assertEquals(first, EvidenceInterpreter.interpret(audio, shape, selection, visual).interpretation)
    }
    @Test fun mouthRatioAboveOriginalUpperBoundCannotCreateVisualBasis() {
        val frames = visual(mouth = { if (it == 500L || it == 1_500L) 1.2f else .04f })
        assertEquals("AUDIO", result(sound(0L to "옹알이 후보", 975L to "옹알이 후보",
            1_950L to "옹알이 후보"), frames).basis)
    }
    @Test fun unalignedAudioCannotClaimTemporalVisualCorrespondence() {
        val audio = sound(0L to "아기 웃음 후보", 975L to "아기 웃음 후보")
        val frames = visual(smile = { .8f })
        val plain = EvidenceInterpreter.interpret(audio, shape, range).interpretation!!
        val output = EvidenceInterpreter.interpret(audio, shape, range, frames,
            audioTimelineAligned = false).interpretation!!
        assertEquals("AUDIO", output.basis)
        assertEquals(plain.phrase, output.phrase)
        assertFalse(output.evidence.contains("같은 시간대"))
        assertEquals(frames, EvidenceInterpreter.interpret(audio, shape, range, frames,
            audioTimelineAligned = false).visualEvidence)
    }
}
