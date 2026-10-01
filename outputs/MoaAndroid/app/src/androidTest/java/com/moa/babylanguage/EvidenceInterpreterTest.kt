package com.moa.babylanguage

import org.junit.Assert.*
import org.junit.Test

class EvidenceInterpreterTest {
    private val range = ClipRange(12_000, 20_000)
    private val audible = AcousticShape(.75f, false, 1)
    private fun interpret(cues: List<SoundCue>, shape: AcousticShape = audible, visual: String? = null) =
        EvidenceInterpreter.interpret(SoundAnalysis(cues), shape, range,
            visual?.let { VisualEvidence(VisualEvidenceStatus.COMPLETE, it) })

    @Test fun crowdSpeechBabbleAndDogWhimperAreNotBabyVocalizations() {
        assertEquals("옹알이 후보", SoundAnalyzer.cueLabel("Babbling"))
        assertEquals("말소리(화자 미확인)", SoundAnalyzer.cueLabel("Hubbub, speech noise, speech babble"))
        assertNull(SoundAnalyzer.cueLabel("Whimper (dog)"))
        assertNull(SoundAnalyzer.cueLabel("Baby crying music remix"))
        assertEquals("아기 웃음 후보", SoundAnalyzer.cueLabel("Baby laughter"))
    }
    @Test fun distressLikeCuesCannotProducePlayfulBabblingOrLaughingPhrase() {
        for (label in listOf("울음", "울음(화자 미확인)", "비명(화자 미확인)")) {
            assertNull(interpret(listOf(SoundCue("옹알이 후보", .8f), SoundCue(label, .5f))).interpretation)
            assertNull(interpret(listOf(SoundCue("아기 웃음 후보", .8f), SoundCue(label, .5f))).interpretation)
        }
    }

    @Test fun noBabyEvidenceMeansNoPhraseEvenWithFaceEvidence() {
        listOf(emptyList(), listOf(SoundCue("말소리(화자 미확인)", .99f)),
            listOf(SoundCue("웃음(화자 미확인)", .99f)), listOf(SoundCue("울음", .2f))).forEach {
            assertNull(interpret(it).interpretation)
            assertNull(interpret(it, visual = "얼굴 5개 · 입 움직임 5개").interpretation)
            assertNotNull(interpret(it).problem)
        }
    }
    @Test fun silenceAndModelErrorsNeverProducePhrase() {
        assertNull(interpret(listOf(SoundCue("울음", .9f)), AcousticShape(0f, false, 0)).interpretation)
        assertNull(EvidenceInterpreter.interpret(SoundAnalysis(listOf(SoundCue("울음", .9f)), "모델 실패"), audible, range).interpretation)
    }
    @Test fun soundAndRhythmChangeWordingButSameEvidenceNeverRerolls() {
        val cry = listOf(SoundCue("울음", .8f))
        val stable = interpret(cry).interpretation!!
        repeat(20) { assertEquals(stable, interpret(cry).interpretation) }
        assertNotEquals(stable.phrase, interpret(listOf(SoundCue("옹알이 후보", .8f))).interpretation!!.phrase)
        assertNotEquals(stable.phrase, interpret(listOf(SoundCue("아기 웃음 후보", .8f))).interpretation!!.phrase)
        assertNotEquals(stable.phrase, interpret(cry, audible.copy(rising = true)).interpretation!!.phrase)
        assertNotEquals(stable.phrase, interpret(cry, AcousticShape(.4f, false, 4)).interpretation!!.phrase)
        assertTrue(stable.evidence.contains("0:12–0:20"))
        assertFalse(stable.phrase.contains("배고파"))
        assertFalse(stable.phrase.contains("졸려"))
    }
    @Test fun visualEvidenceAddsObservationNotInventedIntent() {
        val cue = listOf(SoundCue("울음", .8f))
        val audio = interpret(cue).interpretation!!
        val visual = interpret(cue, visual = "얼굴 2개").interpretation!!
        assertEquals(audio.phrase, visual.phrase)
        assertTrue(visual.evidence.contains("얼굴 2개"))
        assertTrue(visual.visual)
    }
    @Test fun wordingVariationKeepsMeaningFamilyAndIsStablePerClip() {
        val cry = listOf(SoundCue("울음", .8f))
        val variants = (0..2).map { interpret(cry, audible.copy(wordingKey = it)).interpretation!!.phrase }
        assertEquals(3, variants.distinct().size)
        variants.forEach { phrase ->
            listOf("놀자", "배고파", "졸려", "웃어요").forEach { assertFalse(phrase.contains(it)) }
        }
        val clip = FloatArray(32_000) { kotlin.math.sin(it * .23).toFloat() * .1f }
        assertEquals(AcousticShape.from(clip), AcousticShape.from(clip.copyOf()))
    }
    @Test fun acousticShapeMeasuresWaveformInsteadOfCaregiverChoices() {
        val flat = FloatArray(48_000) { .05f }
        val bursts = FloatArray(48_000) { if (it / 1600 % 3 == 0) .05f else 0f }
        val rising = FloatArray(48_000) { if (it < 24_000) .02f else .15f }
        assertEquals(1f, AcousticShape.from(flat).activeFraction, .01f)
        assertTrue(AcousticShape.from(bursts).bursts >= 5)
        assertTrue(AcousticShape.from(rising).rising)
    }
    @Test fun rangeCannotEscapeCapturedMinuteOrDecodeMoreThanFifteenSeconds() {
        for (pair in listOf(-1L to 1000L, 0L to 999L, 0L to 16_000L, 59_000L to 61_000L)) {
            assertTrue(runCatching { ClipRange(pair.first, pair.second) }.isFailure)
        }
        assertEquals(15_000, ClipRange(45_000, 60_000).durationMs.toInt())
    }
}
