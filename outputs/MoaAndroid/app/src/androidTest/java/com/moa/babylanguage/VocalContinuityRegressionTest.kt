package com.moa.babylanguage

import org.junit.Assert.*
import org.junit.Test

/** Real decision/interpreter regression. Observations exercise policy, not model accuracy. */
class VocalContinuityRegressionTest {
    private val range = ClipRange(0, 10_000)
    private val shape = AcousticShape(.75f, false, 1)
    private fun frame(at: Long, label: String, score: Float = .45f) =
        SoundFrame(at, listOf(SoundCue(label, score)))
    private fun result(frames: List<SoundFrame>) = EvidenceInterpreter.interpret(
        SoundAnalysis(emptyList(), frames = frames), shape, range)

    @Test fun adjacentNeutralLabelsRetainTheRecognizedVocalization() {
        val frames = listOf(frame(0, "어린 말소리 후보"), frame(975, "옹알이 후보"),
            frame(1_950, "어린 말소리 후보"))
        val audio = SoundAnalysis(emptyList(), frames = frames)
        val decision = SoundTimeline.decide(audio)
        assertNull("Three adjacent neutral observations were lost: ${decision.issue}", decision.problem)
        assertEquals(listOf(VocalEvent.CHILD_VOICE, VocalEvent.BABBLE, VocalEvent.CHILD_VOICE),
            decision.moments.map { it.event })
        val interpretation = result(frames).interpretation
        assertNotNull("Recognized neutral activity must reach the result", interpretation)
        for (word in listOf("울음", "슬퍼", "배고", "졸려", "아파", "화났"))
            assertFalse(interpretation!!.phrase, interpretation.phrase.contains(word))
        assertEquals(interpretation, result(frames).interpretation)
    }

    @Test fun sparseDuplicateAndGenericObservationsCannotEstablishNeutralContinuity() {
        val neutral = listOf("어린 말소리 후보", "옹알이 후보", "어린 말소리 후보")
        for (times in listOf(listOf(0L, 0L, 0L), listOf(0L, 4_000L, 8_000L),
            listOf(1_950L, 975L, 0L), listOf(11_000L, 11_975L, 12_950L))) {
            assertNull(result(times.zip(neutral).map { (at, label) -> frame(at, label) }).interpretation)
        }
        assertNull(result(listOf(frame(0, neutral[0]), frame(975, neutral[1]))).interpretation)
        assertNull(result(listOf(frame(0, neutral[0]), frame(975, "말소리(화자 미확인)", .99f),
            frame(1_950, neutral[1]))).interpretation)
        assertNull(result(List(4) { frame(it * 975L, "말소리(화자 미확인)", .99f) }).interpretation)
    }

    @Test fun fourStableRunsUseTheFullPaidSelectionWithoutInventingTheReason() {
        val labels = List(4) { "옹알이 후보" } + List(4) { "아기 웃음 후보" } +
            List(4) { "울음" } + List(4) { "어린 말소리 후보" }
        val audio = SoundAnalysis(emptyList(), frames = labels.mapIndexed { index, label -> frame(index * 975L, label, .85f) })
        val interpretation = EvidenceInterpreter.interpret(audio, shape, ClipRange(0, 20_000)).interpretation!!
        val words = listOf("옹알", "웃음", "울음", "말소리")
        val positions = words.map { interpretation.phrase.indexOf(it) }
        assertTrue(interpretation.phrase, positions.first() >= 0 && positions.zipWithNext().all { (a, b) -> b > a })
        assertTrue(interpretation.phrase, interpretation.phrase.length <= 200)
        assertTrue(interpretation.guide.contains("편안한지"))
        for (word in listOf("배고", "졸려", "아파", "기분이 풀", "놀아요"))
            assertFalse(interpretation.phrase, interpretation.phrase.contains(word))
    }

    @Test fun competingEmotionBackgroundAndInvalidScoresStillBlockContinuity() {
        val base = listOf(frame(0, "어린 말소리 후보"), frame(975, "옹알이 후보"),
            frame(1_950, "어린 말소리 후보"))
        for (cue in listOf(SoundCue("울음", .65f), SoundCue("아기 웃음 후보", .65f),
            SoundCue("비명(화자 미확인)", .95f), SoundCue("배경 소리", .99f))) {
            assertNull(result(base.map { it.copy(cues = it.cues + cue) }).interpretation)
        }
        assertNull(result(base + frame(2_925, "울음(화자 미확인)", .95f)).interpretation)
        for (score in listOf(Float.NaN, Float.POSITIVE_INFINITY, -.1f, 1.1f, .34f)) {
            assertNull(result(base.map { it.copy(cues = listOf(SoundCue(it.cues.single().label, score))) }).interpretation)
        }
    }

    @Test fun threeStableSoundRunsKeepTheirObservedOrderInThePhrase() {
        val labels = List(3) { "울음" } + List(3) { "옹알이 후보" } + List(3) { "아기 웃음 후보" }
        val frames = labels.mapIndexed { index, label -> frame(index * 975L, label, .85f) }
        val text = result(frames).interpretation!!.phrase
        val cry = text.indexOf("울음")
        val babble = text.indexOf("옹알")
        val laugh = text.indexOf("웃음")
        assertTrue("The observed three-stage flow was discarded: $text", cry >= 0 && babble > cry && laugh > babble)
        for (word in listOf("배고", "졸려", "아파", "화났", "기분이 풀", "놀아요", "신나"))
            assertFalse(text, text.contains(word))
        assertTrue(text, text.length <= 200)
    }

    @Test fun threeRunOrderIsNotInventedAcrossAnIgnoredSourceFrameOrSparseRuns() {
        val labels = List(2) { "울음" } + List(2) { "옹알이 후보" } + List(2) { "아기 웃음 후보" }
        val clean = labels.mapIndexed { index, label -> frame(index * 975L, label, .85f) }
        val conflicted = (clean + SoundFrame(2_500, listOf(SoundCue("울음", .85f),
            SoundCue("아기 웃음 후보", .85f)))).sortedBy { it.startMs }
        for (frames in listOf(conflicted, listOf(frame(0, "울음", .85f),
            frame(975, "옹알이 후보", .85f), frame(1_950, "아기 웃음 후보", .85f)))) {
            val text = result(frames).interpretation!!.phrase
            assertFalse(text, text.contains("뒤에") || text.contains("하다가") || text.contains("이어서"))
        }
    }
}
