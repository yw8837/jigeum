package com.moa.babylanguage

import org.junit.Assert.*
import org.junit.Test

/** Minimized policy reproductions of the actual frozen Android low-level probes.
 * Explicit scores exercise the final gate; they are not additional real babies. */
class AcceptedVocalAudibilityTest {
    private val range = ClipRange(0, 15_000)
    private val quietShape = AcousticShape(0f, false, 0, strongestSecondRms = .006)
    private val cry = listOf(SoundCue("울음", .4140625f), SoundCue("울음(화자 미확인)", .66796875f))
    private fun frames(rms: Double? = .006) = SoundAnalysis(emptyList(), frames = listOf(
        SoundFrame(0, cry, rms), SoundFrame(975, cry, rms)))

    @Test fun anAcceptedQuietCryMustNotBeDiscardedByTheWholeSceneActivityRatio() {
        val audio = frames()
        assertEquals(setOf(VocalEvent.CRY), SoundTimeline.decide(audio).events.toSet())
        assertNull(SoundTimeline.decide(audio).problem)
        val result = EvidenceInterpreter.interpret(audio, quietShape, range)
        assertNotNull("Accepted audible cry was lost at the final gate: ${result.issue}", result.interpretation)
        assertEquals("울음", result.interpretation!!.sound)
    }

    @Test fun anAcceptedBroadBabbleUsesItsOwnWindowLevelWithoutInventingTiming() {
        val audio = SoundAnalysis(emptyList(), babbleWindows = listOf(BabbleSupportWindow(5_000, 15_000, .00386)))
        assertNull(SoundTimeline.decide(audio).problem)
        val result = EvidenceInterpreter.interpret(audio, quietShape, range)
        assertNotNull("Accepted audible babble was lost at the final gate: ${result.issue}", result.interpretation)
        assertEquals("옹알이", result.interpretation!!.sound)
        for (word in listOf("쉬었다", "커졌", "계속", "배고", "졸려"))
            assertFalse(result.interpretation.phrase, result.interpretation.phrase.contains(word))
    }

    @Test fun louderUnacceptedFramesCannotSupplyTheAcceptedSoundsAudibility() {
        val audio = frames(.002).copy(frames = frames(.002).frames +
            SoundFrame(9_750, listOf(SoundCue("말소리(화자 미확인)", .95f)), .3))
        assertNull(SoundTimeline.decide(audio).problem)
        assertNull(EvidenceInterpreter.interpret(audio, quietShape.copy(strongestSecondRms = .3), range).interpretation)
        val broad = SoundAnalysis(emptyList(), babbleWindows = listOf(BabbleSupportWindow(5_000, 15_000, .002)))
        assertNull(EvidenceInterpreter.interpret(broad, quietShape.copy(strongestSecondRms = .3), range).interpretation)
    }

    @Test fun missingNonfiniteAndBelowFloorMeasurementsCannotOpenTheGate() {
        for (rms in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -.1, .002999, 1.01)) {
            assertNull(EvidenceInterpreter.interpret(frames(rms), quietShape, range).interpretation)
            val broad = SoundAnalysis(emptyList(), babbleWindows = listOf(BabbleSupportWindow(0, 10_000, rms)))
            assertNull(EvidenceInterpreter.interpret(broad, quietShape, range).interpretation)
        }
    }

    @Test fun audibleNoiseAndConflictingLabelsStillDoNotAuthorizeBabyWords() {
        for (cues in listOf(listOf(SoundCue("배경 소리", .9f)),
            listOf(SoundCue("말소리(화자 미확인)", .95f)),
            listOf(SoundCue("울음", .9f), SoundCue("아기 웃음 후보", .9f)))) {
            val audio = SoundAnalysis(emptyList(), frames = listOf(SoundFrame(0, cues, .5)))
            assertNull(EvidenceInterpreter.interpret(audio, quietShape.copy(strongestSecondRms = .5), range).interpretation)
        }
    }

    @Test fun audibleSubsetMustStillSatisfyPersistenceForEveryObservedEvent() {
        val oneWeakWindow = frames().copy(frames = listOf(SoundFrame(0, cry, .006), SoundFrame(975, cry, .002)))
        assertNull(SoundTimeline.decide(oneWeakWindow).problem)
        assertNull(EvidenceInterpreter.interpret(oneWeakWindow, quietShape, range).interpretation)
        val mixed = frames().copy(frames = frames().frames + SoundFrame(1_950,
            listOf(SoundCue("아기 웃음 후보", .9f)), .002))
        assertEquals(2, SoundTimeline.decide(mixed).events.size)
        assertNull(EvidenceInterpreter.interpret(mixed, quietShape, range).interpretation)
    }

    @Test fun invalidGeometrySceneMetricsAndInputErrorsDoNotGetAnException() {
        for (times in listOf(listOf(0L, 0L), listOf(975L, 0L), listOf(-975L, 0L), listOf(0L, 15_000L))) {
            val audio = SoundAnalysis(emptyList(), frames = times.map { SoundFrame(it, cry, .006) })
            assertNull(EvidenceInterpreter.interpret(audio, quietShape, range).interpretation)
        }
        for (fraction in listOf(Float.NaN, Float.POSITIVE_INFINITY, -.1f, 1.1f))
            assertNull(EvidenceInterpreter.interpret(frames(), quietShape.copy(activeFraction = fraction), range).interpretation)
        for (level in listOf(Double.NaN, Double.POSITIVE_INFINITY, -.1, .002999, 1.1))
            assertNull(EvidenceInterpreter.interpret(frames(), quietShape.copy(strongestSecondRms = level), range).interpretation)
        val failed = frames().copy(problem = "failed input", issue = AnalysisIssue.MODEL_FAILURE)
        assertEquals(AnalysisIssue.MODEL_FAILURE, EvidenceInterpreter.interpret(failed, quietShape, range).issue)
    }

    @Test fun previousNonquietInterpretationsDoNotRequireNewMeasurementMetadata() {
        val sound = frames(null)
        val active = quietShape.copy(activeFraction = .5f)
        assertEquals(EvidenceInterpreter.interpret(sound, active, range),
            EvidenceInterpreter.interpret(frames(.006), active, range))
        val broad = SoundAnalysis(emptyList(), babbleWindows = listOf(BabbleSupportWindow(0, 10_000)))
        val measured = broad.copy(babbleWindows = listOf(BabbleSupportWindow(0, 10_000, .006)))
        assertEquals(EvidenceInterpreter.interpret(broad, active, range), EvidenceInterpreter.interpret(measured, active, range))
    }
}
