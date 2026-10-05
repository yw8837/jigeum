package com.moa.babylanguage

import org.junit.Assert.*
import org.junit.Test

/** Actual interpreter output tests. Synthetic observations do not measure model accuracy. */
class ExpressionVarietyTest {
    private val range = ClipRange(0, 10_000)
    private val shape = AcousticShape(.75f, false, 1)
    private fun cue(label: String) = listOf(SoundCue(label, .85f))
    private fun recording(labels: List<String>, times: List<Long> = labels.indices.map { it * 975L }) =
        SoundAnalysis(emptyList(), frames = labels.zip(times).map { (label, time) -> SoundFrame(time, cue(label)) })
    private fun phrase(audio: SoundAnalysis, key: Int = 0) =
        EvidenceInterpreter.interpret(audio, shape.copy(wordingKey = key), range).interpretation!!.phrase

    @Test fun cryDoesNotRequireAnOnomatopoeiaOrAnAttentionRequest() {
        val audio = SoundAnalysis(cue("울음"))
        val variants = (0 until 12).map { phrase(audio, it) }.distinct()
        assertTrue("Only ${variants.size} expressions", variants.size >= 6)
        assertTrue(variants.count { it.startsWith("으앙") || it.startsWith("응애") } <= variants.size / 3)
        assertTrue(variants.count { it.contains("봐줘") || it.contains("들려") || it.contains("들어줘") } <= variants.size / 3)
        for (text in variants) for (word in listOf("배고", "졸려", "아파", "놀자", "신나"))
            assertFalse(text, text.contains(word))
    }

    @Test fun broadBabbleCanOfferSoundPlayWithoutInventingRhythmOrHunger() {
        val audio = SoundAnalysis(emptyList(), babbleWindows = listOf(BabbleSupportWindow(0, 10_000)))
        val variants = (0 until 12).map { phrase(audio, it) }.distinct()
        assertTrue(variants.size >= 6)
        assertTrue(variants.any { it.contains("흉내") })
        assertTrue(variants.any { it.contains("소리 놀이") })
        for (text in variants) for (word in listOf("으앙", "응애", "계속", "아직", "쉬었다", "커졌", "배고", "졸려"))
            assertFalse(text, text.contains(word))
    }

    @Test fun stableObservedOrderChangesTheExpressionWithoutChangingAcceptance() {
        val orders = listOf("옹알이 후보" to "아기 웃음 후보", "아기 웃음 후보" to "옹알이 후보",
            "울음" to "옹알이 후보", "옹알이 후보" to "울음", "울음" to "아기 웃음 후보", "아기 웃음 후보" to "울음")
        val results = orders.map { (a, b) -> phrase(recording(List(4) { a } + List(4) { b })) }
        assertEquals(6, results.distinct().size)
        assertTrue(results[0], results[0].indexOf("옹알") < results[0].indexOf("웃음"))
        assertTrue(results[1], results[1].indexOf("웃음") < results[1].indexOf("옹알"))
        assertTrue(results[2].contains("울음 뒤"))
        assertTrue(results[3].contains("울음이"))
        results.forEach { text ->
            assertTrue(text.length <= 160)
            for (word in listOf("배고", "졸려", "아파", "화났", "기분이 풀")) assertFalse(text, text.contains(word))
        }
    }

    @Test fun duplicateSparseAlternatingOrOutOfRangeObservationsCannotInventAnOrderedStory() {
        val neutral = phrase(recording(listOf("울음", "아기 웃음 후보")))
        val labels = listOf("울음", "울음", "아기 웃음 후보", "아기 웃음 후보")
        val cases = listOf(
            recording(labels, listOf(0, 0, 975, 975)),
            recording(labels, listOf(0, 975, 7_000, 7_975)),
            recording(labels, listOf(-975, 0, 975, 10_000)),
            recording(List(8) { if (it % 2 == 0) "울음" else "아기 웃음 후보" }))
        cases.forEach { assertEquals(neutral, phrase(it)) }
    }

    @Test fun broadAstDoesNotPromoteWaveformMotionIntoTheBabysTimeline() {
        val audio = SoundAnalysis(emptyList(), babbleWindows = listOf(BabbleSupportWindow(0, 10_000)))
        val stable = phrase(audio)
        val other = EvidenceInterpreter.interpret(audio, shape.copy(rising = true, bursts = 20,
            pauses = 3, quietSpans = listOf(AcousticPause(2_000, 4_000))), range).interpretation!!.phrase
        assertEquals(stable, other)
    }

    @Test fun childSpeechNeverBecomesBabblingInAnOrderedExpression() {
        for (other in listOf("울음", "아기 웃음 후보")) for (reverse in listOf(false, true)) {
            val speech = List(3) { "어린 말소리 후보" }
            val event = List(3) { other }
            val audio = recording(if (reverse) event + speech else speech + event)
            val text = phrase(audio)
            assertTrue(text, text.contains("말소리"))
            assertFalse(text, text.contains("옹알"))
        }
        val threeRuns = recording(List(3) { "어린 말소리 후보" } + List(3) { "옹알이 후보" } + List(3) { "아기 웃음 후보" })
        assertEquals("내 소리가 여러 모습으로 나왔어요. 한 가지 마음으로 정하지 말고 천천히 함께해 주세요.", phrase(threeRuns))
    }

    @Test fun anIgnoredConflictingSourceFrameCannotDisappearFromTheSequence() {
        val clean = recording(listOf("울음", "울음", "아기 웃음 후보", "아기 웃음 후보"))
        for (at in listOf(975L, 1_500L)) {
            val frames = (clean.frames + SoundFrame(at, cue("울음") + cue("아기 웃음 후보"))).sortedBy { it.startMs }
            val audio = SoundAnalysis(emptyList(), frames = frames)
            assertNull(SoundTimeline.decide(audio).problem)
            assertEquals("내 소리가 여러 모습으로 나왔어요. 한 가지 마음으로 정하지 말고 천천히 함께해 주세요.", phrase(audio))
        }
    }

    @Test fun allKeysAreBoundedStableAndNeverTurnNoiseIntoWords() {
        val keys = listOf(Int.MIN_VALUE, -1, 0, 1, 99, Int.MAX_VALUE)
        for (label in listOf("울음", "아기 웃음 후보", "옹알이 후보")) for (key in keys) {
            val audio = SoundAnalysis(cue(label))
            val text = phrase(audio, key)
            assertTrue(text.length in 15..160)
            assertEquals(text, phrase(audio, key))
        }
        for (audio in listOf(SoundAnalysis(cue("배경 소리")), SoundAnalysis(cue("말소리(화자 미확인)")),
            SoundAnalysis(cue("울음") + cue("아기 웃음 후보"))))
            assertNull(EvidenceInterpreter.interpret(audio, shape, range).interpretation)
    }
}
