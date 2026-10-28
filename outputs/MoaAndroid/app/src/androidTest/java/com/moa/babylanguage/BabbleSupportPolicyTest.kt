package com.moa.babylanguage

import org.junit.Assert.*
import org.junit.Test

class BabbleSupportPolicyTest {
    private val unresolved = SoundAnalysis(listOf(SoundCue("말소리(화자 미확인)", .9f)))
    private fun window(start: Long = 0, end: Long = 10_000, vararg scores: Pair<String, Float>): AstAudioWindow {
        val labels = listOf("Babbling", "Child speech, kid speaking", "Baby cry, infant cry", "Baby laughter",
            "Crying, sobbing", "Laughter", "Screaming", "Television", "Giggle", "Snicker",
            "Belly laugh", "Chuckle, chortle", "Whimper", "Wail, moan")
        return AstAudioWindow(start, end, labels.associateWith { 0f } + scores.toMap())
    }
    private fun apply(audio: SoundAnalysis = unresolved, vararg windows: AstAudioWindow) =
        BabbleSupportPolicy.attachCandidate(audio, AstAudioAnalysis(windows.toList()), 20_000)

    @Test fun corroboratedBabbleRecoversBroadObservationDespiteMusic() {
        val audio = apply(windows = arrayOf(window(scores = arrayOf("Babbling" to .15f,
            "Child speech, kid speaking" to .49f, "Music" to .95f))))
        assertEquals(listOf(BabbleSupportWindow(0, 10_000)), audio.babbleWindows)
        val decision = SoundTimeline.decide(audio)
        assertEquals(listOf(VocalEvent.BABBLE), decision.events)
        assertTrue(decision.moments.isEmpty()) // Never fabricate ten .975s detections.
    }

    @Test fun strongBabbleDoesNotRequireChildSpeechCoactivation() {
        assertEquals(1, apply(windows = arrayOf(window(scores = arrayOf("Babbling" to .60f)))).babbleWindows.size)
    }

    @Test fun genericSpeechMusicAndCrowdNeverBecomeBabble() {
        for (label in listOf("Speech", "Music", "Hubbub, speech noise, speech babble", "Child speech, kid speaking")) {
            assertTrue(apply(windows = arrayOf(window(scores = arrayOf(label to .99f)))).babbleWindows.isEmpty())
        }
        assertTrue(apply(windows = arrayOf(window(scores = arrayOf("Babbling" to .15f)))).babbleWindows.isEmpty())
    }

    @Test fun laughterCryScreamAndTvInAnyWindowPreventNewBabyAttribution() {
        val candidate = window(scores = arrayOf("Babbling" to .60f))
        for (label in listOf("Baby cry, infant cry", "Baby laughter", "Crying, sobbing", "Laughter", "Screaming", "Television")) {
            assertTrue(label, apply(windows = arrayOf(candidate,
                window(10_000, 20_000, label to .8f))).babbleWindows.isEmpty())
        }
        assertTrue(apply(windows = arrayOf(window(scores = arrayOf("Babbling" to .15f,
            "Child speech, kid speaking" to .49f, "Baby laughter" to .14f)))).babbleWindows.isEmpty())
    }

    @Test fun existingCryLaughAndMixedAbstentionArePreserved() {
        val candidate = window(scores = arrayOf("Babbling" to .8f))
        for (cue in listOf("울음", "아기 웃음 후보")) {
            val original = SoundAnalysis(listOf(SoundCue(cue, .9f)))
            assertEquals(SoundTimeline.decide(original), SoundTimeline.decide(apply(original, candidate)))
            assertTrue(apply(original, candidate).babbleWindows.isEmpty())
        }
        val mixed = SoundAnalysis(listOf(SoundCue("울음", .9f), SoundCue("아기 웃음 후보", .9f)))
        assertEquals(SoundTimeline.decide(mixed), SoundTimeline.decide(apply(mixed, candidate)))
    }

    @Test fun emotionalSubclassesCannotHideBehindLowParentClassScores() {
        val candidate = window(scores = arrayOf("Babbling" to .60f))
        for (label in listOf("Giggle", "Snicker", "Belly laugh", "Chuckle, chortle", "Whimper", "Wail, moan")) {
            assertTrue(label, apply(windows = arrayOf(window(scores = arrayOf(
                "Babbling" to .60f, label to .90f)))).babbleWindows.isEmpty())
            assertTrue(label, apply(windows = arrayOf(candidate,
                window(10_000, 20_000, label to .90f))).babbleWindows.isEmpty())
        }
    }

    @Test fun yamnetUnresolvedEmotionalCuesCannotBeErasedByAst() {
        for ((label, score) in listOf("울음" to .4f, "아기 웃음 후보" to .4f,
            "울음(화자 미확인)" to .8f, "웃음(화자 미확인)" to .8f, "비명(화자 미확인)" to .8f)) {
            assertTrue(apply(SoundAnalysis(listOf(SoundCue(label, score))),
                window(scores = arrayOf("Babbling" to .8f))).babbleWindows.isEmpty())
        }
    }

    @Test fun errorsInvalidScoresAndOutOfRangeWindowsCannotAuthorize() {
        val candidate = window(scores = arrayOf("Babbling" to .8f))
        for (issue in listOf(AnalysisIssue.SILENCE, AnalysisIssue.INVALID_AUDIO, AnalysisIssue.MODEL_FAILURE)) {
            assertTrue(apply(unresolved.copy(problem = "unavailable", issue = issue), candidate).babbleWindows.isEmpty())
        }
        assertTrue(BabbleSupportPolicy.attachCandidate(unresolved,
            AstAudioAnalysis(listOf(candidate), "failed"), 20_000).babbleWindows.isEmpty())
        for (score in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 2f)) {
            assertTrue(apply(windows = arrayOf(window(scores = arrayOf("Babbling" to score)))).babbleWindows.isEmpty())
        }
        for ((start, end) in listOf(-1L to 10_000L, 0L to 20_001L, 10_000L to 1_000L, 0L to 999L)) {
            assertTrue(apply(windows = arrayOf(window(start, end, "Babbling" to .8f))).babbleWindows.isEmpty())
        }
        assertTrue(apply(windows = arrayOf(AstAudioWindow(0, 10_000, mapOf("Babbling" to .8f)))).babbleWindows.isEmpty())
    }

    @Test fun broadWindowsCannotInventOnsetRepetitionOrSustainedBabyActivity() {
        val audio = apply(windows = arrayOf(window(scores = arrayOf("Babbling" to .6f)),
            window(5_000, 15_000, "Babbling" to .6f), window(10_000, 20_000, "Babbling" to .6f)))
        val result = EvidenceInterpreter.interpret(audio,
            AcousticShape(.95f, true, 12, pauses = 7), ClipRange(30_000, 50_000)).interpretation
        assertNotNull(result)
        assertFalse(result!!.evidence.contains("부근"))
        assertTrue(result.evidence.contains("0:30–0:50"))
        for (word in listOf("계속", "아직", "또", "쉬었다", "커졌", "여러 번", "길게 이어")) {
            assertFalse(word, result.phrase.contains(word))
            assertFalse(word, result.evidence.contains(word))
        }
    }
}
