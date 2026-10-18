package com.moa.babylanguage

import org.junit.Assert.*
import org.junit.Test

/** Enumerates actual interpreter outputs, not just entries in a text catalog. */
class ExpressionExpansionTest {
    private val range = ClipRange(0, 10_000)
    private val shape = AcousticShape(.75f, false, 1)
    private fun cue(label: String) = listOf(SoundCue(label, .85f))
    private fun recording(labels: List<String>) = SoundAnalysis(emptyList(),
        frames = labels.mapIndexed { i, label -> SoundFrame(i * 975L, cue(label)) })
    private fun result(audio: SoundAnalysis, key: Int, observed: AcousticShape = shape) =
        EvidenceInterpreter.interpret(audio, observed.copy(wordingKey = key), range).let {
            checkNotNull(it.interpretation) { "Invalid accepted fixture: $audio; ${it.issue}" }
        }
    private fun families() = listOf(
        SoundAnalysis(cue("울음")), SoundAnalysis(cue("아기 웃음 후보")),
        recording(List(3) { "옹알이 후보" }), recording(List(3) { "어린 말소리 후보" }),
        recording(listOf("울음", "아기 웃음 후보")),
        recording(listOf("옹알이 후보", "어린 말소리 후보", "옹알이 후보")),
        SoundAnalysis(cue("말소리(화자 미확인)"), infantVoiceWindows = listOf(InfantVoiceSupportWindow(0, 10_000)))
    )

    @Test fun everyMeaningFamilyHasHundredsOfReachableCompleteExpressions() {
        val all = mutableSetOf<String>()
        for (audio in families()) {
            val outputs = (0 until 512).map { result(audio, it) }
            assertEquals("${outputs.first().sound} needs 512 reachable choices", 512, outputs.map { it.phrase }.toSet().size)
            assertEquals(1, outputs.map { it.copy(phrase = "") }.toSet().size)
            assertTrue(outputs.all { it.phrase.length in 15..100 && it.phrase.last() in ".!?" })
            assertTrue(outputs.map { it.phrase.substringBefore('.').substringBefore('!').substringBefore('?') }.toSet().size >= 32)
            all.addAll(outputs.map { it.phrase })
        }
        assertEquals("No duplicated full expressions across families", 3584, all.size)
    }

    @Test fun pausePathCanReachTheExpandedVocabularyToo() {
        val audio = SoundAnalysis(emptyList(), frames = listOf(0L, 975, 3900, 4875).map { SoundFrame(it, cue("옹알이 후보")) })
        val measured = shape.copy(quietSpans = listOf(AcousticPause(2000, 3600)))
        val plain = result(audio, 0)
        val variants = (0 until 512).map { result(audio, it, measured) }
        assertEquals(512, variants.map { it.phrase }.toSet().size)
        assertTrue(variants.all { it.sound == plain.sound && it.phrase.contains("조용해졌다가") })
        assertTrue(variants.all { it.phrase.length <= 130 })
    }

    @Test fun supportedOrderGetsManyExpressionsWithoutReversingTheEvents() {
        val audio = recording(List(3) { "옹알이 후보" } + List(3) { "아기 웃음 후보" })
        val phrases = (0 until 128).map { result(audio, it).phrase }
        assertEquals(128, phrases.toSet().size)
        for (text in phrases) {
            assertTrue(text, text.indexOf("옹알") in 0 until text.indexOf("웃음"))
            assertTrue(text.length <= 160)
        }
    }

    @Test fun expandedKeysStayDeterministicAndDoNotChangeEvidenceOrInventNeeds() {
        val forbidden = listOf("배고", "졸려", "아파", "화났", "슬퍼", "기분이 풀", "엄마가", "아빠가")
        for (audio in families()) for (key in (-512 until 512) + listOf(Int.MIN_VALUE, Int.MAX_VALUE)) {
            val first = result(audio, key)
            assertEquals(first, result(audio, key))
            forbidden.forEach { assertFalse(first.phrase, first.phrase.contains(it)) }
        }
        val general = families().last()
        for (key in 0 until 512) {
            val text = result(general, key).phrase
            for (word in listOf("울음", "웃음", "옹알", "쉬었다", "커졌", "계속")) assertFalse(text, text.contains(word))
        }
        for (key in listOf(Int.MIN_VALUE, -1, 0, 511, Int.MAX_VALUE))
            for (label in listOf("배경 소리", "말소리(화자 미확인)"))
                assertNull(EvidenceInterpreter.interpret(SoundAnalysis(cue(label)), shape.copy(wordingKey = key), range).interpretation)
    }

    @Test fun differentPcmKeysCanActuallyReachTheLargerPoolWithoutSessionState() {
        val audio = SoundAnalysis(cue("옹알이 후보"))
        val phrases = (0 until 256).map { seed ->
            val random = kotlin.random.Random(seed)
            val pcm = FloatArray(16_000) { (random.nextFloat() - .5f) * .2f }
            val first = AcousticShape.from(pcm)
            assertEquals(first.wordingKey, AcousticShape.from(pcm.copyOf()).wordingKey)
            result(audio, first.wordingKey).phrase
        }
        assertTrue("Synthetic PCM reached only ${phrases.toSet().size} choices", phrases.toSet().size >= 150)
        println("SYNTHETIC_PCM_VARIETY=${phrases.toSet().size}/256 (wording selection only)")
    }

    @Test fun enumerateAllSupportedExpressionBranchesAndVisualAdditions() {
        val groups = linkedMapOf<String, MutableSet<String>>()
        val visualGroups = linkedMapOf<String, MutableSet<String>>()
        val withVisual = mutableSetOf<String>()
        val forbidden = listOf("배고", "졸려", "아파", "화났", "기분이 풀")
        fun audit(name: String, audio: SoundAnalysis, duration: Long, observed: AcousticShape, keys: Int) {
            val selected = ClipRange(0, duration)
            val decision = SoundTimeline.decide(audio, duration)
            assertNull(decision.problem)
            // The display rule only appends these accepted suffixes. Evaluate it on
            // real visual observations for each sound context, then enumerate text.
            val additions = mutableSetOf("")
            for (mouth in listOf(false, true)) for (corners in listOf(false, true)) {
                val frames = (0 until duration step 200).map { at ->
                    VisualFrameObservation(at, 1, true,
                        if (mouth && at % 800 >= 400) .35f else .04f,
                        mouthCornerLift = if (corners) .8f else .1f, presentationOffsetMs = at)
                }
                additions += VisualExpression.combine("", decision, selected,
                    VisualEvidence(VisualEvidenceStatus.COMPLETE, "synthetic", frames = frames)).phrase
            }
            val outputs = groups.getOrPut(name) { mutableSetOf() }
            for (key in 0 until keys) {
                val text = EvidenceInterpreter.interpret(audio, observed.copy(wordingKey = key), selected).interpretation!!.phrase
                assertTrue(text, text.length <= 160)
                assertFalse(text, text.contains("  "))
                forbidden.forEach { assertFalse(text, text.contains(it)) }
                outputs += text
                for (suffix in additions) {
                    val combined = text + suffix
                    assertTrue("Length ${combined.length}: $combined", combined.length <= 160)
                    withVisual += combined
                    visualGroups.getOrPut(name) { mutableSetOf() } += combined
                }
            }
        }
        val basic = families().toMutableList()
        // The same mixed catalog can have both mouth and smile observations.
        basic += recording(listOf("울음", "아기 웃음 후보", "옹알이 후보", "옹알이 후보",
            "울음", "아기 웃음 후보", "옹알이 후보", "옹알이 후보", "울음"))
        basic.forEach { audit("basic", it, 10_000, shape, 512) }
        val longPatterns = VocalEvent.entries.map { listOf(it.cue) } + listOf(
            listOf("울음", "아기 웃음 후보", "옹알이 후보", "옹알이 후보"), listOf("옹알이 후보", "어린 말소리 후보"))
        for (pattern in longPatterns) for (pause in listOf(false, true)) {
            val audio = recording(List(20) { pattern[it % pattern.size] })
            audit("sustained", audio, 20_000, shape.copy(quietSpans =
                if (pause) listOf(AcousticPause(6000, 7000)) else emptyList()), 512)
        }
        for (event in VocalEvent.entries) {
            val audio = SoundAnalysis(emptyList(), frames = listOf(0L, 975, 3900, 4875).map { SoundFrame(it, cue(event.cue)) })
            audit("pause", audio, 10_000, shape.copy(quietSpans = listOf(AcousticPause(2000, 3600))), 512)
        }
        var orders = 0
        fun sequences(prefix: List<VocalEvent>) {
            if (prefix.size >= 2 && prefix.any { it == VocalEvent.CRY || it == VocalEvent.LAUGH }) {
                orders++
                val audio = recording(prefix.flatMap { event -> List(3) { event.cue } })
                audit("ordered", audio, if (prefix.size == 4) 20_000 else 10_000, shape, 128)
            }
            if (prefix.size < 4) for (event in VocalEvent.entries)
                if (prefix.lastOrNull() != event) sequences(prefix + event)
        }
        sequences(emptyList())
        assertEquals(150, orders)
        val audioOnly = groups.values.flatten().toSet()
        val expected = mapOf("basic" to 3584, "sustained" to 6144, "pause" to 2048, "ordered" to 19200)
        assertEquals(expected, groups.mapValues { it.value.size })
        assertEquals(30976, audioOnly.size)
        println("VISUAL_GROUP_COUNTS=${visualGroups.mapValues { it.value.size }}")
        assertEquals(90624, withVisual.size)
        val allSentences = groups.getValue("basic").flatMap { it.split(Regex("(?<=[.!?]) +")) }.toSet()
        val basicWords = groups.getValue("basic").flatMap { it.split(Regex("\\s+")) }
            .map { it.trim('.', '!', '?', ',', '…') }.filter { it.isNotBlank() }.toSet()
        println("EXPRESSION_AUDIT={\"basic\":${groups.getValue("basic").size},\"sustained\":${groups.getValue("sustained").size}," +
            "\"pause\":${groups.getValue("pause").size},\"ordered\":${groups.getValue("ordered").size},\"eventOrders\":$orders," +
            "\"audioOnly\":${audioOnly.size},\"withVisual\":${withVisual.size},\"authoredSentenceUnits\":${allSentences.size}," +
            "\"basicWhitespaceWords\":${basicWords.size},\"basicMaxChars\":${groups.getValue("basic").maxOf { it.length }}," +
            "\"audioMaxChars\":${audioOnly.maxOf { it.length }},\"visualMaxChars\":${withVisual.maxOf { it.length }}}")
    }
}
