package com.moa.babylanguage

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/** Acoustic timing is measured from synthetic PCM. Explicit model observations
 * isolate presentation bugs; tones are not claimed to classify as baby/adult. */
class PaddingExpressionRegressionTest {
    private fun sample(index: Int, hz: Int) = (.08 * sin(2 * PI * hz * index / 16_000)).toFloat()
    private fun observations(durationMs: Long, cueAt: (Long) -> List<SoundCue>): SoundAnalysis =
        SoundAnalysis(emptyList(), frames = (0L until durationMs step 975L).map { SoundFrame(it, cueAt(it)) })
    private val babyCue = listOf(SoundCue("옹알이 후보", .85f))

    @Test fun tenSecondsOfBabySoundPlusTwoSecondsOfSilenceCannotLengthenTheExpression() {
        val tenSeconds = FloatArray(160_000) { sample(it, 440) }
        val padded = tenSeconds.copyOf(192_000)
        try {
            // The same 11 accepted starts (0..9750 ms); only silent frames are added.
            val shortAudio = observations(10_000) { babyCue }
            val paddedAudio = observations(12_000) { if (it < 10_000) babyCue else emptyList() }
            val shortShape = AcousticShape.from(tenSeconds).copy(wordingKey = 0)
            val paddedShape = AcousticShape.from(padded).copy(wordingKey = 0)
            assertEquals(11, SoundTimeline.decide(shortAudio).moments.size)
            assertEquals(SoundTimeline.decide(shortAudio).moments, SoundTimeline.decide(paddedAudio).moments)
            assertEquals(0, paddedShape.pauses)
            val short = EvidenceInterpreter.interpret(shortAudio, shortShape, ClipRange(0, 10_000)).interpretation!!
            val longerSelection = EvidenceInterpreter.interpret(paddedAudio, paddedShape, ClipRange(0, 12_000)).interpretation!!
            assertEquals("Adding trailing silence adds no baby evidence and must not unlock a longer phrase",
                short.phrase, longerSelection.phrase)
        } finally { tenSeconds.fill(0f); padded.fill(0f) }
    }

    @Test fun pauseBetweenAnAdultAndTheFirstBabySoundMustNotBecomeTheBabysPause() {
        // 0..2.5s adult sound, 2.5..3s silence, 3..20s continuous baby sound.
        // There is no quiet stretch between any two accepted baby moments.
        val pcm = FloatArray(320_000) { index -> when {
            index < 40_000 -> sample(index, 660)
            index < 48_000 -> 0f
            else -> sample(index, 440)
        } }
        try {
            val audio = observations(20_000) { at -> when {
                at < 2_500 -> listOf(SoundCue("말소리(화자 미확인)", .99f))
                at < 3_000 -> emptyList()
                else -> babyCue
            } }
            val shape = AcousticShape.from(pcm).copy(wordingKey = 0)
            assertEquals("The old whole-waveform pause counter sees the adult-to-baby gap", 1, shape.pauses)
            val accepted = SoundTimeline.decide(audio).moments
            assertTrue(accepted.isNotEmpty())
            assertTrue(accepted.all { it.startMs >= 3_000 })
            val result = EvidenceInterpreter.interpret(audio, shape, ClipRange(0, 20_000)).interpretation!!
            for (unsupportedPause in listOf("쉬었다", "쉬고", "쉬기", "조용해졌다"))
                assertFalse("Only another speaker paused; do not attribute that pause to the baby: ${result.phrase}",
                    result.phrase.contains(unsupportedPause))
        } finally { pcm.fill(0f) }
    }
}
