package com.moa.babylanguage

import org.junit.Assert.*
import org.junit.Test

class VideoCadenceTest {
    @Test fun shortRepeatedMouthChangesReachTheTemporalInterpreter() {
        val cadence = VideoFrameCadence(0, 2_000_000)
        val frames = (0 until 2000 step 20).mapNotNull { at ->
            val requested = cadence.select(at * 1000L) ?: return@mapNotNull null
            VisualFrameObservation(requested, 1, true,
                mouthOpening = if (at % 500 < 200) .25f else .04f,
                presentationOffsetMs = at.toLong())
        }
        val timeline = VisualTimeline.summarize(frames, 2000)
        assertTrue("Half-second samples alias this repeated motion into a static mouth",
            timeline.mouthTransitions >= 6)
        val audio = SoundAnalysis(emptyList(), frames = (0..4).map {
            SoundFrame(it * 400L, listOf(SoundCue("옹알이 후보", .95f)))
        })
        val result = EvidenceInterpreter.interpret(audio, AcousticShape(.8f, false, 3), ClipRange(0, 2000),
            VisualEvidence(VisualEvidenceStatus.COMPLETE, "시간순 관찰", frames = frames, timeline = timeline))
        assertEquals("AUDIO_VISUAL", result.interpretation!!.basis)
    }

    @Test fun extraSmileSamplesCannotShortenTheExistingHalfSecondPersistence() {
        val decision = SoundTimeline.decide(SoundAnalysis(emptyList(), frames = (0..4).map {
            SoundFrame(it * 400L, listOf(SoundCue("아기 웃음 후보", .95f)))
        }), 2000)
        assertNull(decision.problem)
        fun frame(at: Long) = VisualFrameObservation(at, 1, true, mouthOpening = .1f,
            mouthCornerLift = .8f, presentationOffsetMs = at)
        assertTrue(VisualSoundAlignment.find(decision, listOf(frame(0), frame(200)), 2000).isEmpty())
        assertTrue(VisualSoundAlignment.find(decision, listOf(frame(0), frame(200), frame(400), frame(600)), 2000).isNotEmpty())
    }

    @Test fun denseMouthSamplesCannotShortenTheExistingObservationRun() {
        val decision = SoundTimeline.decide(SoundAnalysis(emptyList(), frames = (0..4).map {
            SoundFrame(it * 400L, listOf(SoundCue("옹알이 후보", .95f)))
        }), 2000)
        fun frame(at: Long, opening: Float) = VisualFrameObservation(at, 1, true,
            mouthOpening = opening, presentationOffsetMs = at)
        val short = listOf(frame(0, .25f), frame(200, .04f), frame(400, .25f))
        assertTrue(VisualSoundAlignment.find(decision, short, 2000).isEmpty())
        assertTrue(VisualSoundAlignment.find(decision, short + listOf(frame(600, .25f),
            frame(800, .25f), frame(1000, .25f)), 2000).isNotEmpty())
    }
}
