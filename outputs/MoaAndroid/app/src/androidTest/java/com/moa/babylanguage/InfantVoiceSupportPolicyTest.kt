package com.moa.babylanguage

import org.junit.Assert.*
import org.junit.Test

class InfantVoiceSupportPolicyTest {
    private val unresolved = SoundAnalysis(listOf(SoundCue("말소리(화자 미확인)", .9f)),
        frames = listOf(SoundFrame(0, listOf(SoundCue("말소리(화자 미확인)", .9f)))))
    private fun window(score: Float? = .8f, start: Long = 0, end: Long = 10_000, vararg extra: Pair<String, Float>) =
        AstAudioWindow(start, end, listOf("Baby cry, infant cry", "Baby laughter", "Crying, sobbing", "Laughter",
            "Screaming", "Giggle", "Snicker", "Belly laugh", "Chuckle, chortle", "Whimper", "Wail, moan",
            "Television", "Babbling", "Child speech, kid speaking").associateWith { 0f } + extra.toMap(), score, .01)
    private fun apply(vararg windows: AstAudioWindow, audio: SoundAnalysis = unresolved, duration: Long = 10_000) =
        InfantVoiceSupportPolicy.attachCandidate(audio, AstAudioAnalysis(windows.toList()), duration)

    @Test fun supplementalHeadCanAddSameWindowSupportWithoutRemovingBase() {
        assertEquals(1, apply(window(.7f).copy(captionPresence = .8f)).infantVoiceWindows.size)
        assertEquals(1, apply(window(.8f).copy(captionPresence = .7f)).infantVoiceWindows.size)
        assertEquals(unresolved, apply(window(.7f).copy(captionPresence = .799999f)))
        assertEquals(unresolved, apply(window(.7f).copy(captionPresence = .99f, strongestSecondRms = .002)))
        assertEquals(unresolved, apply(window(.7f, extra = arrayOf("Laughter" to .3f)).copy(captionPresence = .99f)))
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, -.01f, 1.01f))
            assertEquals(unresolved, apply(window(.7f).copy(captionPresence = value)))
        assertEquals(1, apply(window(.8f).copy(captionPresence = null)).infantVoiceWindows.size)
        // A new score cannot replace a missing base output or an existing accepted observation.
        assertEquals(unresolved, apply(window(null).copy(captionPresence = .99f)))
        val original = SoundAnalysis(listOf(SoundCue("아기 웃음 후보", .9f)))
        assertEquals(original, apply(window(.7f).copy(captionPresence = .99f), audio = original))
    }

    @Test fun baseHeadRangesAndExistingPhraseWinBeforeSupplementalRanges() {
        val windows = arrayOf(window(.9f),window(.7f,5_000,15_000),window(.7f,10_000,20_000))
        val prior = apply(*windows,duration=20_000)
        val supplemented = apply(*windows.map { it.copy(captionPresence=.99f) }.toTypedArray(),duration=20_000)
        assertEquals(prior,supplemented)
        val shape = AcousticShape(.1f,false,0,strongestSecondRms=.01)
        assertEquals(EvidenceInterpreter.interpret(prior,shape,ClipRange(0,20_000)),
            EvidenceInterpreter.interpret(supplemented,shape,ClipRange(0,20_000)))
    }

    @Test fun frozenBoundaryAddsGeneralPresenceWithoutEmotionBabbleOrMoments() {
        assertEquals(unresolved, apply(window(.799999f)))
        val audio = apply(window())
        val decision = SoundTimeline.decide(audio)
        assertNull(decision.problem)
        assertEquals("아기 발성", decision.sound)
        assertTrue(decision.events.isEmpty() && decision.moments.isEmpty() && decision.broadWindows.isEmpty())
        assertEquals(listOf(InfantVoiceSupportWindow(0, 10_000)), decision.infantVoiceWindows)
        assertFalse(SoundPhaseRecovery.shouldRetry(audio))
    }

    @Test fun invalidScoresMissingLabelsAndRuntimeProblemsNeverAddSupport() {
        for (score in listOf(null, Float.NaN, Float.POSITIVE_INFINITY, -.1f, 1.1f))
            assertEquals(unresolved, apply(window(score)))
        for (score in listOf(Float.NaN, Float.NEGATIVE_INFINITY, -.1f, 1.1f))
            assertEquals(unresolved, apply(window(extra = arrayOf("Speech" to score))))
        assertEquals(unresolved, apply(window().copy(scores = emptyMap())))
        assertEquals(unresolved, InfantVoiceSupportPolicy.attachCandidate(unresolved,
            AstAudioAnalysis(listOf(window()), "runtime unavailable"), 10_000))
        for (duration in listOf(999L, 20_001L)) assertEquals(unresolved, apply(window(), duration = duration))
    }

    @Test fun completeTwentySecondOverlapAndNonMultipleDurationKeepBroadRanges() {
        val windows = arrayOf(window(.2f), window(.9f, 5_000, 15_000), window(.85f, 10_000, 20_000))
        assertEquals(listOf(InfantVoiceSupportWindow(5_000, 15_000), InfantVoiceSupportWindow(10_000, 20_000)),
            apply(*windows, duration = 20_000).infantVoiceWindows)
        assertEquals(3, apply(window(), window(start = 5_000, end = 15_000),
            window(start = 7_173, end = 17_173), duration = 17_173).infantVoiceWindows.size)
        for (bad in listOf(windows.reversedArray(), windows + windows[0], windows.copyOfRange(0, 2),
            arrayOf(window(.9f, -1, 9_999)), arrayOf(window(.9f, 0, 10_001))))
            assertEquals(unresolved, apply(*bad, duration = 20_000))
    }

    @Test fun conflictAnywhereInSelectionAndUnresolvedYamnetEmotionStayHeld() {
        for (label in listOf("Baby cry, infant cry", "Baby laughter", "Crying, sobbing", "Laughter", "Screaming",
            "Giggle", "Snicker", "Belly laugh", "Chuckle, chortle", "Whimper", "Wail, moan", "Television")) {
            val audio = apply(window(.9f), window(.1f, 5_000, 15_000),
                window(.1f, 10_000, 20_000, label to .3f), duration = 20_000)
            assertEquals(label, unresolved, audio)
        }
        for ((label, score) in listOf("울음" to .35f, "아기 웃음 후보" to .35f,
            "울음(화자 미확인)" to .65f, "웃음(화자 미확인)" to .65f, "비명(화자 미확인)" to .65f)) {
            val audio = SoundAnalysis(listOf(SoundCue(label, score)))
            assertEquals(audio, apply(window(), audio = audio))
        }
    }

    @Test fun existingApprovalsProblemsAndBroadBabbleArePreservedByteForByte() {
        val originals = listOf("울음", "아기 웃음 후보", "옹알이 후보").map {
            SoundAnalysis(listOf(SoundCue(it, .9f)))
        } + unresolved.copy(babbleWindows = listOf(BabbleSupportWindow(0, 10_000))) +
            unresolved.copy(infantVoiceWindows = listOf(InfantVoiceSupportWindow(0, 10_000))) +
            SoundAnalysis(listOf(SoundCue("울음", .9f), SoundCue("아기 웃음 후보", .9f))) +
            AnalysisIssue.entries.map { unresolved.copy(problem = "held", issue = it) }
        for (original in originals) assertEquals(original, apply(window(.1f), audio = original))
    }

    @Test fun broadPresenceCannotInventRhythmOrOrderOrLipSynchronization() {
        val audio = apply(window())
        val range = ClipRange(0, 10_000)
        for (key in 0..30) {
            val result = EvidenceInterpreter.interpret(audio, AcousticShape(.9f, true, 8, key, 5,
                listOf(AcousticPause(1_000, 1_500))), range).interpretation!!
            assertEquals("아기 발성", result.sound)
            assertFalse(result.visual)
            for (word in listOf("으앙", "응애", "울음", "웃음", "옹알", "배고", "졸", "쉬었다", "이어졌", "커졌"))
                assertFalse(word + ": " + result.phrase, result.phrase.contains(word))
            assertTrue(result.evidence.contains("정확한 발성 위치와 지속 여부는 확인하지"))
            assertFalse(result.evidence.contains("소리와 쉼이 여러 번"))
        }
        assertTrue(VisualSoundAlignment.find(SoundTimeline.decide(audio), emptyList(), 10_000).isEmpty())
    }

    @Test fun presenceCannotTurnQuietSignalIntoACharacterExpression() {
        val result = EvidenceInterpreter.interpret(apply(window(.99f)), AcousticShape(.02f, false, 0), ClipRange(0, 10_000))
        assertNull(result.interpretation)
        assertEquals(AnalysisIssue.QUIET, result.issue)
    }

    @Test fun audibilityMustBelongToSupportedWindowAndBeFiniteAndPresent() {
        for (rms in listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -.1, 1.1, .002999))
            assertEquals(unresolved, apply(window(.9f).copy(strongestSecondRms = rms)))
        assertEquals(1, apply(window(.9f).copy(strongestSecondRms = .003)).infantVoiceWindows.size)
        val audio = apply(window(.9f).copy(strongestSecondRms = .001),
            window(.1f, 5_000, 15_000).copy(strongestSecondRms = .1),
            window(.1f, 10_000, 20_000).copy(strongestSecondRms = .1), duration = 20_000)
        assertEquals(unresolved, audio)
    }

    @Test fun generalPresenceUsesExistingInputSilenceFloorWithoutAttributingSceneRhythm() {
        val audio = apply(window(.9f))
        for (rms in listOf(Double.NaN, Double.POSITIVE_INFINITY, -.1, .002999, 1.1)) {
            val result = EvidenceInterpreter.interpret(audio,
                AcousticShape(.02f, true, 9, strongestSecondRms = rms), ClipRange(0, 10_000))
            assertNull(result.interpretation)
        }
        val result = EvidenceInterpreter.interpret(audio,
            AcousticShape(.02f, true, 9, strongestSecondRms = .003), ClipRange(0, 10_000)).interpretation!!
        assertEquals("아기 발성", result.sound)
        assertFalse(result.phrase.contains("울음") || result.phrase.contains("웃음") || result.phrase.contains("이어졌"))
        // Existing cry/babble gates keep their original whole-scene activity requirement.
        assertNull(EvidenceInterpreter.interpret(SoundAnalysis(listOf(SoundCue("울음", .9f))),
            AcousticShape(.02f, false, 0, strongestSecondRms = .1), ClipRange(0, 10_000)).interpretation)
        // A model/input error cannot be bypassed by a forged level or head score.
        val silent = audio.copy(problem = "silent input", issue = AnalysisIssue.SILENCE)
        assertNull(EvidenceInterpreter.interpret(silent, AcousticShape(.02f, false, 0,
            strongestSecondRms = .1), ClipRange(0, 10_000)).interpretation)
    }
}
