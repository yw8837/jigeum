package com.moa.babylanguage

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/** Presentation regressions use explicit model observations, not intent labels.
 * These tests do not establish classifier accuracy on real recordings. */
class LongExpressionTest {
    private val sustained = AcousticShape(.8f, false, 1, wordingKey = 0)
    private fun recording(durationMs: Long, cueAt: (Int) -> List<SoundCue>): SoundAnalysis {
        val frames = (0L until durationMs step 975L).mapIndexed { index, at -> SoundFrame(at, cueAt(index)) }
        return SoundAnalysis(frames.flatMap { it.cues }, frames = frames)
    }
    private fun cue(label: String) = listOf(SoundCue(label, .85f))
    private fun phrase(audio: SoundAnalysis, durationMs: Long, shape: AcousticShape = sustained) =
        EvidenceInterpreter.interpret(audio, shape, ClipRange(0, durationMs)).interpretation!!.phrase

    @Test fun sustainedFifteenTwentyAndThirtySecondEventsHaveLongerCoherentExpressions() {
        for (label in listOf("아기 웃음 후보", "울음", "옹알이 후보", "어린 말소리 후보")) {
            val short = phrase(recording(10_000) { cue(label) }, 10_000)
            for (duration in listOf(15_000L, 20_000L, 30_000L)) {
                val audio = recording(duration) { cue(label) }
                val long = phrase(audio, duration)
                assertTrue("$label: $long", long.length > short.length)
                assertTrue(long.length <= 160)
                for (forbidden in listOf("배고", "졸려", "아파", "슬퍼")) assertFalse(long.contains(forbidden))
                repeat(3) { assertEquals(long, phrase(audio, duration)) }
            }
        }
    }

    @Test fun twentyVersusThirtySecondsDoesNotInventNewMeaningForTheSamePattern() {
        val twentySeconds = recording(20_000) { cue("옹알이 후보") }
        val thirtySeconds = recording(30_000) { cue("옹알이 후보") }
        val paddedThirty = recording(30_000) { if (it * 975L < 20_000L) cue("옹알이 후보") else emptyList() }
        val twentyPhrase = phrase(twentySeconds, 20_000)
        assertEquals(twentyPhrase, phrase(thirtySeconds, 30_000))
        assertEquals(twentyPhrase, phrase(paddedThirty, 30_000, sustained.copy(activeFraction = .53f)))
        // A real measured pause changes the expression, not the paid duration.
        assertNotEquals(twentyPhrase, phrase(thirtySeconds, 30_000, sustained.copy(pauses = 1)))
    }

    @Test fun tenSecondExpressionsRemainCompleteAndDoNotRequirePaidAccess() {
        // There is no entitlement input to this interpreter: matching evidence
        // produces matching wording for free, preview and paid callers.
        val babble = recording(10_000) { cue("옹알이 후보") }
        assertEquals("옹알옹알, 내 목소리예요! 내 얘기도 들어봐요.", phrase(babble, 10_000))
        val laugh = recording(10_000) { cue("아기 웃음 후보") }
        assertEquals("헤헤! 나도 웃음이 나요. 같이 웃어요.", phrase(laugh, 10_000))
    }

    @Test fun oneSecondLaughPaddedWithSilenceOrOtherSoundCannotBecomeMonologue() {
        val single = SoundAnalysis(cue("아기 웃음 후보"))
        val short = phrase(single, 1_000)
        for (padding in listOf(emptyList(), cue("배경 소리"), cue("말소리(화자 미확인)"))) {
            val padded = recording(30_000) { if (it == 12) cue("아기 웃음 후보") else padding }
            assertEquals(short, phrase(padded, 30_000))
        }
    }

    @Test fun weakActivityAndInsufficientAcceptedDurationDoNotTriggerLengthening() {
        val cry = recording(30_000) { cue("울음") }
        val lowActivity = sustained.copy(activeFraction = .1f)
        assertEquals(phrase(SoundAnalysis(cue("울음")), 1_000, lowActivity), phrase(cry, 30_000, lowActivity))
        val onlyBeginning = recording(30_000) { if (it < 10) cue("울음") else emptyList() }
        assertEquals(phrase(SoundAnalysis(cue("울음")), 1_000), phrase(onlyBeginning, 30_000))
    }

    @Test fun duplicateSparseAndOutOfRangeTimestampsCannotFakeSustainedSpeech() {
        val short = phrase(SoundAnalysis(cue("옹알이 후보")), 1_000)
        for (times in listOf(List(40) { 0L }, listOf(0L, 5_000L, 10_000L, 15_000L, 20_000L, 25_000L),
            (0L..50_000L step 975L).filter { it == 0L || it >= 30_000L })) {
            val audio = SoundAnalysis(emptyList(), frames = times.map { SoundFrame(it, cue("옹알이 후보")) })
            assertEquals(short, phrase(audio, 30_000))
        }
    }

    @Test fun mixedTemporalSoundsRemainNeutralAndPreserveCareGuide() {
        for (reverse in listOf(false, true)) {
            val audio = recording(30_000) { index ->
                cue(if ((index < 15) xor reverse) "아기 웃음 후보" else "울음")
            }
            val result = EvidenceInterpreter.interpret(audio, sustained, ClipRange(0, 30_000)).interpretation!!
            assertEquals("여러 아기 소리", result.sound)
            assertTrue(result.phrase.length <= 160)
            for (word in listOf("응애", "헤헤", "까르르", "슬퍼", "신나", "놀자", "배고"))
                assertFalse(result.phrase.contains(word))
            assertTrue(result.guide.contains("편안한지"))
            assertTrue(result.evidence.contains("→"))
            assertEquals("나 여기 있어요. 이렇게도, 저렇게도 소리 내볼래요. 내 얘기 아직 남았어요. 조금만 더 들어줄래요?", result.phrase)
        }
    }

    @Test fun simultaneousConflictSilenceNoiseAndModelFailureStayWithheld() {
        for (audio in listOf(
            recording(30_000) { emptyList() },
            recording(30_000) { cue("배경 소리") },
            recording(30_000) { cue("말소리(화자 미확인)") },
            recording(30_000) { cue("울음") + cue("아기 웃음 후보") },
            SoundAnalysis(cue("울음"), problem = "모델 실패")
        )) {
            val result = EvidenceInterpreter.interpret(audio, sustained, ClipRange(0, 30_000))
            assertNull(result.interpretation)
            assertNotNull(result.problem)
        }
        assertNull(EvidenceInterpreter.interpret(recording(30_000) { cue("울음") },
            sustained.copy(activeFraction = 0f), ClipRange(0, 30_000)).interpretation)
    }

    @Test fun measuredInternalPausesChangeWordingButLeadingAndTrailingSilenceDoNot() {
        fun audio(samples: Int, audible: (Int) -> Boolean) = FloatArray(samples) {
            if (audible(it)) (.08 * sin(2 * PI * 440 * it / 16_000)).toFloat() else 0f
        }
        val continuous = AcousticShape.from(audio(240_000) { true })
        val paused = AcousticShape.from(audio(240_000) { it !in 96_000 until 112_000 })
        val padded = AcousticShape.from(audio(240_000) { it in 32_000 until 192_000 })
        assertEquals(0, continuous.pauses)
        assertEquals(1, paused.pauses)
        assertEquals(0, padded.pauses)
        val observations = recording(15_000) { cue("옹알이 후보") }
        assertTrue(phrase(observations, 15_000, paused.copy(wordingKey = 0)).contains("쉬었다가"))
        assertFalse(phrase(observations, 15_000, continuous.copy(wordingKey = 0)).contains("쉬었다가"))
    }

    @Test fun allWordingBranchesFitStoredLengthAndAreDeterministic() {
        val labels = listOf("울음", "아기 웃음 후보", "옹알이 후보", "어린 말소리 후보", "mixed")
        for (label in labels) for (key in listOf(Int.MIN_VALUE, -1, 0, 1, 2, Int.MAX_VALUE))
            for (pauses in listOf(0, 2)) for (rising in listOf(false, true)) {
                val audio = recording(30_000) {
                    cue(if (label == "mixed") if (it < 15) "울음" else "아기 웃음 후보" else label)
                }
                val shape = sustained.copy(rising = rising, wordingKey = key, pauses = pauses)
                val result = phrase(audio, 30_000, shape)
                assertTrue(result.isNotBlank())
                assertTrue("$label: ${result.length}", result.length <= 160)
                assertEquals(result, phrase(audio, 30_000, shape))
            }
    }

    @Test fun rangeSupportsLastThirtySecondsOfTwoMinuteCaptureButRejectsOverflow() {
        assertEquals(30_000L, ClipRange(90_000, 120_000).durationMs)
        assertEquals(30_000L, ClipRange(90_500, 120_500).durationMs)
        for ((start, end) in listOf(-1L to 1_000L, 0L to 999L, 0L to 30_001L, 119_501L to 120_501L))
            assertTrue(runCatching { ClipRange(start, end) }.isFailure)
    }
}
