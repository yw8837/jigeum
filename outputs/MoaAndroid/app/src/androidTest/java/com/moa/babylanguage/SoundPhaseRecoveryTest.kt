package com.moa.babylanguage

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

class SoundPhaseRecoveryTest {
    private fun audio(vararg frames: SoundFrame) = SoundAnalysis(emptyList(), frames = frames.toList())
    private fun frame(at: Long, vararg cues: Pair<String, Float>) =
        SoundFrame(at, cues.map { SoundCue(it.first, it.second) })
    private val empty = audio(frame(0), frame(975), frame(1950))
    private val shiftedCry = audio(frame(487, "울음" to .74f), frame(1462))

    @Test fun establishedAstBabbleWinsBeforeShiftedCryIsRequested() = runBlocking<Unit> {
        val labels = listOf("Babbling", "Child speech, kid speaking", "Television", "Baby cry, infant cry",
            "Baby laughter", "Crying, sobbing", "Laughter", "Screaming", "Giggle", "Snicker", "Belly laugh",
            "Chuckle, chortle", "Whimper", "Wail, moan")
        val ast = AstAudioAnalysis(listOf(AstAudioWindow(0, 2000,
            labels.associateWith { if (it == "Babbling") .8f else 0f })))
        var retries = 0
        val result = SoundPhaseRecovery.complete(empty, { original ->
            assertSame(empty, original)
            BabbleSupportPolicy.attachCandidate(original, ast, 2000)
        }, { retries++; shiftedCry })
        assertEquals(listOf(VocalEvent.BABBLE), SoundTimeline.decide(result).events)
        assertEquals(0, retries)
    }
    @Test fun unsuccessfulSupportRunsBeforeRecoveryAndRetryFailureKeepsBaseline() = runBlocking<Unit> {
        for (error in listOf(IllegalStateException("retry failed"), UnsatisfiedLinkError("retry failed"))) {
            val order = mutableListOf<String>()
            val result = SoundPhaseRecovery.complete(empty, { order += "support"; it }, {
                order += "phase"; throw error
            })
            assertSame(empty, result)
            assertEquals(listOf("support", "phase"), order)
        }
        assertSame(shiftedCry, SoundPhaseRecovery.complete(empty, { it }, { shiftedCry }))
    }
    @Test fun cancellationDuringRecoveryIsNeverReportedAsOrdinaryHold() = runBlocking<Unit> {
        val cancellation = CancellationException("cancel probe")
        try {
            SoundPhaseRecovery.complete(empty, { it }, { throw cancellation })
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) { assertSame(cancellation, actual) }
    }
    @Test fun mixedNoticeDoesNotInventLaughterOrMeasuredVolume() {
        val mixed = SoundTimeline.decide(audio(frame(0, "옹알이 후보" to .8f),
            frame(975, "울음(화자 미확인)" to .9f)))
        assertEquals(AnalysisIssue.MIXED_VOCAL, mixed.issue)
        assertFalse(mixed.problem!!.contains("웃음"))
        val background = SoundTimeline.decide(audio(frame(0, "배경 소리" to .9f)))
        assertEquals(AnalysisIssue.BACKGROUND_DOMINANT, background.issue)
        assertFalse(background.problem!!.contains("크게"))
    }

    @Test fun recoversStrongShiftedEventWithoutAddingDuplicateMoments() {
        val result = SoundPhaseRecovery.choose(empty, shiftedCry)
        assertSame(shiftedCry, result)
        assertEquals(listOf(VocalMoment(VocalEvent.CRY, 487)), SoundTimeline.decide(result).moments)
    }
    @Test fun moderateOverlappingVotesCannotPassAsTwoIndependentVotes() {
        val original = audio(frame(0, "울음" to .5f), frame(975))
        val shifted = audio(frame(487, "울음" to .5f), frame(1462))
        assertSame(original, SoundPhaseRecovery.choose(original, shifted))
    }
    @Test fun recognizedButInsufficientlyPersistentEventKeepsOriginalHold() {
        val original = audio(frame(0, "울음" to .4f), frame(975))
        assertFalse(SoundPhaseRecovery.shouldRetry(original))
        assertSame(original, SoundPhaseRecovery.choose(original, shiftedCry))
    }
    @Test fun originalResolvedOrMixedDecisionIsNeverReplaced() {
        val laugh = audio(frame(0, "아기 웃음 후보" to .8f))
        val mixed = audio(frame(0, "아기 웃음 후보" to .8f, "울음" to .85f))
        for (original in listOf(laugh, mixed)) {
            assertFalse(SoundPhaseRecovery.shouldRetry(original))
            assertSame(original, SoundPhaseRecovery.choose(original, shiftedCry))
        }
    }
    @Test fun unresolvedOppositeEventInOriginalStillBlocksRecovery() {
        val original = audio(frame(0, "아기 웃음 후보" to .45f), frame(975))
        assertSame(original, SoundPhaseRecovery.choose(original, shiftedCry))
    }
    @Test fun strongGenericOppositeInOriginalStillBlocksRecovery() {
        val original = audio(frame(0, "웃음(화자 미확인)" to .9f), frame(975))
        assertSame(original, SoundPhaseRecovery.choose(original, shiftedCry))
    }
    @Test fun genericAdultOrUnresolvedSecondPhaseCannotCreateBaby() {
        for (label in listOf("말소리(화자 미확인)", "웃음(화자 미확인)", "울음(화자 미확인)")) {
            val shifted = audio(frame(487, label to .99f), frame(1462, label to .99f))
            assertSame(empty, SoundPhaseRecovery.choose(empty, shifted))
        }
    }
    @Test fun secondPhaseMustHaveItsOwnThreeChildWindows() {
        val original = audio(frame(0, "어린 말소리 후보" to .42f), frame(975))
        val shifted = audio(frame(487, "어린 말소리 후보" to .42f), frame(1462, "어린 말소리 후보" to .42f))
        assertSame(original, SoundPhaseRecovery.choose(original, shifted))
    }
    @Test fun errorsOrBroadAstAttributionAreNotRetried() {
        for (issue in AnalysisIssue.entries) {
            val original = empty.copy(problem = "실패", issue = issue)
            assertFalse(SoundPhaseRecovery.shouldRetry(original))
            assertSame(original, SoundPhaseRecovery.choose(original, shiftedCry))
        }
        assertFalse(SoundPhaseRecovery.shouldRetry(empty.copy(babbleWindows = listOf(BabbleSupportWindow(0, 10000)))))
    }
    @Test fun duplicateShiftedTimestampsCannotInventPersistence() {
        val duplicate = audio(frame(487, "울음" to .5f), frame(487, "울음" to .5f))
        assertSame(empty, SoundPhaseRecovery.choose(empty, duplicate))
        assertSame(empty, SoundPhaseRecovery.choose(empty, shiftedCry.copy(frames = shiftedCry.frames.reversed())))
    }
}
