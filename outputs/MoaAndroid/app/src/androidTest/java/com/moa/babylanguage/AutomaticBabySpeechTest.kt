package com.moa.babylanguage

import org.junit.Assert.*
import org.junit.Test

class AutomaticBabySpeechTest {
    private val range = ClipRange(0, 10_000)
    private fun audio(label: String) = SoundAnalysis(emptyList(),
        frames = List(10) { SoundFrame(it * 975L, listOf(SoundCue(label, .9f))) })
    private fun visual() = VisualEvidence(VisualEvidenceStatus.COMPLETE, "synthetic only",
        frames = (0L until 10_000 step 200).map { at ->
            val wide = at in 2_000..3_400
            VisualFrameObservation(at, 1, true, if (wide) .4f else .04f,
                if (wide) .8f else .1f, .1f, at)
        })
    private fun result(label: String, key: Int = 0, frames: VisualEvidence? = null, aligned: Boolean = true): ClipAnalysisResult {
        val sound = audio(label)
        val shape = AcousticShape(.75f, false, 1, wordingKey = key)
        val observed = EvidenceInterpreter.interpret(sound, shape, range, frames, aligned)
        return AutomaticBabySpeech.apply(observed, SoundTimeline.decide(sound, range.durationMs),
            shape, range, frames, aligned)
    }
    @Test fun automaticWordingNeedsNoSelectionAndDoesNotCarryOverPreviousContext() {
        val texts = (0 until 128).map { index ->
            val first = result("울음", index * 1152).interpretation!!
            result("아기 웃음 후보", Int.MAX_VALUE)
            assertEquals(first, result("울음", index * 1152).interpretation)
            assertEquals(ExpressionBasis.AUDIO_PLAYFUL, first.basis)
            assertEquals("울음", first.sound)
            assertTrue(first.phrase.length <= 160)
            assertFalse(first.phrase.contains("졸려"))
            first.phrase
        }
        assertEquals(128, texts.toSet().size)
        assertTrue(texts.any { "귀찮아" in it })
        assertTrue(texts.any { "싫어잉" in it })
        val laugh = (0 until 128).map { result("아기 웃음 후보", it * 1152).interpretation!!.phrase }
        assertTrue(laugh.none { "귀찮아" in it || "졸려" in it || "싫어잉" in it })
    }

    @Test fun acceptedBabbleAndChildVoiceAutomaticallyReceiveImaginationWithoutSleepGuessing() {
        for (label in listOf("옹알이 후보", "어린 말소리 후보")) {
            assertTrue(SoundTimeline.decide(audio(label), range.durationMs).neutralOnly)
            val texts = (0 until 128).map { index ->
                val generated = result(label, index * 1152).interpretation!!
                assertEquals(ExpressionBasis.AUDIO_PLAYFUL, generated.basis)
                assertFalse(generated.phrase.contains("졸려"))
                assertFalse(generated.phrase.contains("귀찮아"))
                generated.phrase
            }
            assertEquals(128, texts.toSet().size)
        }
    }

    @Test fun partialOrBroadAcceptanceCannotBecomeASpecificFeeling() {
        val sound = audio("옹알이 후보")
        val shape = AcousticShape(.75f, false, 1)
        val observed = EvidenceInterpreter.interpret(sound, shape, range)
        val decisions = listOf(
            SoundTimeline.decide(sound, 10_000).copy(partial = true),
            SoundDecision(broadWindows = listOf(BabbleSupportWindow(0, 10_000))),
            SoundDecision(infantVoiceWindows = listOf(InfantVoiceSupportWindow(0, 10_000)))
        )
        decisions.forEach {
            assertEquals(observed, AutomaticBabySpeech.apply(observed, it, shape, range, visual(), true))
        }
    }
    @Test fun sleepyWordingRequiresSequenceNotHashOneBlinkOrLaugh() {
        val sleepy = result("옹알이 후보", frames = visual()).interpretation!!
        assertEquals(ExpressionBasis.AUDIO_VISUAL_PLAYFUL, sleepy.basis)
        assertTrue(sleepy.phrase, "졸려" in sleepy.phrase)
        assertTrue(sleepy.evidence.contains("졸림·화자 일치는 확인한 것이 아니며"))
        for (key in listOf(Int.MIN_VALUE, -1152, 0, 1152, Int.MAX_VALUE)) {
            assertFalse(result("옹알이 후보", key).interpretation!!.phrase.contains("졸려"))
            assertFalse(result("옹알이 후보", key, visual(), false).interpretation!!.phrase.contains("졸려"))
            assertFalse(result("아기 웃음 후보", key, visual()).interpretation!!.phrase.contains("졸려"))
        }
        val blink = visual().copy(frames = visual().frames.map { f ->
            f.copy(mouthOpening = .04f, eyeClosure = if (f.requestedOffsetMs == 2_000L) .8f else .1f)
        })
        assertFalse(AutomaticBabySpeech.hasYawnLikeSequence(blink, 10_000))
    }
    @Test fun ambiguousMissingStaticOrInvalidFramesCannotSupplySleepyWording() {
        val frames = visual().frames
        val variants = listOf(
            visual().copy(status = VisualEvidenceStatus.FAILED),
            visual().copy(frames = frames.map { it.copy(presentationOffsetMs = null) }),
            visual().copy(frames = frames.reversed()),
            visual().copy(frames = frames + frames.last()),
            visual().copy(frames = frames.filterNot { it.requestedOffsetMs in 2_200..3_200 }),
            visual().copy(frames = frames.map { it.copy(mouthOpening = .4f, eyeClosure = .8f) }),
            visual().copy(frames = frames.map { it.copy(eyeClosure = null) }),
            visual().copy(frames = frames.map { if (it.requestedOffsetMs == 2_600L) it.copy(faceCount = 2) else it }),
            visual().copy(frames = frames.map { if (it.requestedOffsetMs == 2_600L) it.copy(valid = false) else it }),
            visual().copy(frames = frames.map { it.copy(eyeClosure = Float.NaN) })
        )
        variants.forEach { assertFalse(AutomaticBabySpeech.hasYawnLikeSequence(it, 10_000)) }
        assertFalse(AutomaticBabySpeech.hasYawnLikeSequence(visual(), 21_000))
    }
    @Test fun rejectedGeneralAndMixedSoundsDoNotAcquireInventedIntent() {
        val shape = AcousticShape(.75f, false, 1)
        val cases = listOf(
            SoundAnalysis(listOf(SoundCue("배경 소리", .9f))),
            SoundAnalysis(listOf(SoundCue("말소리(화자 미확인)", .9f))),
            audio("울음").copy(frames = audio("울음").frames.mapIndexed { i, f ->
                if (i > 4) f.copy(cues = listOf(SoundCue("아기 웃음 후보", .9f))) else f }),
            SoundAnalysis(listOf(SoundCue("말소리(화자 미확인)", .9f)),
                infantVoiceWindows = listOf(InfantVoiceSupportWindow(0, 10_000)))
        )
        cases.forEach { sound ->
            val observed = EvidenceInterpreter.interpret(sound, shape, range)
            assertEquals(observed, AutomaticBabySpeech.apply(observed, SoundTimeline.decide(sound, 10_000),
                shape, range, visual(), true))
        }
    }
    @Test fun allAuthoredCaptionsFitAndPreserveEvidenceCareGuideAndStorageProvenance() {
        val all = AutomaticBabySpeechCatalog.themes.flatMap { it.phrases }
        assertEquals(384, all.size); assertEquals(384, all.toSet().size)
        assertTrue(all.all { it.length in 15..100 })
        for (basis in listOf(ExpressionBasis.AUDIO_PLAYFUL, ExpressionBasis.AUDIO_VISUAL_PLAYFUL)) {
            assertEquals(basis, ExpressionBasis.normalize(basis))
            assertEquals(basis, ExpressionBasis.forPhrase(basis, "같은 문장", "같은 문장"))
            assertEquals(ExpressionBasis.CAREGIVER, ExpressionBasis.forPhrase(basis, "같은 문장", "고친 문장"))
        }
        for (label in listOf("울음", "옹알이 후보", "아기 웃음 후보")) for (key in -256..256) {
            val observed = EvidenceInterpreter.interpret(audio(label), AcousticShape(.75f, false, 1, wordingKey = key), range)
            val generated = result(label, key).interpretation!!
            assertEquals(observed.interpretation!!.guide, generated.guide)
            assertEquals(observed.interpretation!!.sound, generated.sound)
            assertEquals(observed.interpretation!!.range, generated.range)
            assertTrue(generated.phrase.startsWith(observed.interpretation!!.phrase))
            assertTrue(generated.phrase.length <= 160)
        }
    }
}
