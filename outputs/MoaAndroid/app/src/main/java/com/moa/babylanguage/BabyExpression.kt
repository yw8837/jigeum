package com.moa.babylanguage

/** Authored character expressions after sound acceptance, not a desire classifier or transcript.
 * Wording keys only pick surface variants. They must never pick a hunger/sleep/pain hypothesis. */
internal object BabyExpression {
    private val infantVoice get() = BabyExpressionCatalog.infantVoice.phrases
    private val crying get() = BabyExpressionCatalog.crying.phrases
    private val laughing get() = BabyExpressionCatalog.laughing.phrases
    private val babbling get() = BabyExpressionCatalog.babbling.phrases
    private val talking get() = BabyExpressionCatalog.talking.phrases
    private val mixed get() = BabyExpressionCatalog.mixed.phrases
    private val neutral get() = BabyExpressionCatalog.neutral.phrases

    fun variants(event: VocalEvent?, shape: AcousticShape, sustained: Boolean = false,
                 withPause: Boolean = false, neutralOnly: Boolean = false, infantPresence: Boolean = false): List<String> {
        // General presence cannot identify cry, laugh, babbling, rhythm or a desire.
        if (infantPresence) return infantVoice
        val base = if (neutralOnly && event == null) neutral else when (event) {
            VocalEvent.CRY -> crying
            VocalEvent.LAUGH -> laughing
            VocalEvent.BABBLE -> babbling
            VocalEvent.CHILD_VOICE -> talking
            null -> mixed
        }
        // Rhythm changes presentation within the SAME meaning family; whole-waveform
        // rises/bursts never establish the baby's intent or exact utterance timing.
        val offset = if (shape.rising) 2 else if (shape.bursts >= 3) 4 else 0
        if (!sustained && offset == 0) return base
        val continuation = if (withPause) "잠깐 쉬었다가 소리를 이었어요. " else ""
        val ending = when (event) {
            VocalEvent.CRY -> " 울음으로 전하는 내 말을 서두르지 말고 받아 주세요."
            VocalEvent.LAUGH -> " 웃음이 이어지는 동안 나랑 함께해 주세요."
            VocalEvent.BABBLE -> " 옹알옹알, 내 방식으로 할 이야기가 많아요."
            VocalEvent.CHILD_VOICE -> " 내 소리가 끝나면 대답하며 주고받아 주세요."
            null -> " 내 반응을 따라 천천히 함께해 주세요."
        }
        return object : AbstractList<String>() {
            override val size = base.size
            override fun get(index: Int): String {
                require(index in indices)
                val phrase = base[(index + offset) % size]
                return if (sustained) continuation + phrase + ending else phrase
            }
        }
    }

    /** A short selection can contain two bouts of the same sound. A model gap alone
     * is insufficient: require measured quiet plus stable accepted windows on both sides. */
    fun pausedPhrase(audio: SoundAnalysis, decision: SoundDecision, shape: AcousticShape,
                     range: ClipRange): String? {
        if (decision.problem != null || decision.partial || decision.broadOnly || range.durationMs > 10_000) return null
        val event = decision.events.singleOrNull() ?: return null
        val frames = audio.frames
        if (frames.isEmpty() || frames.any { it.startMs !in 0 until range.durationMs } ||
            frames.zipWithNext().any { (a, b) -> b.startMs <= a.startMs }) return null
        val moments = decision.moments
        if (moments.size < 4 || moments.any { it.startMs !in frames.map { frame -> frame.startMs } }) return null
        val hasSupportedPause = shape.quietSpans.any { gap ->
            if (gap.startMs < 0 || gap.endMs > range.durationMs || gap.endMs - gap.startMs < 300) return@any false
            val before = moments.filter { it.startMs + SoundPhaseRecovery.FRAME_MS <= gap.startMs }.takeLast(2)
            val after = moments.filter { it.startMs >= gap.endMs }.take(2)
            before.size == 2 && after.size == 2 &&
                before[1].startMs - before[0].startMs in SoundPhaseRecovery.FRAME_MS..1_500L &&
                after[1].startMs - after[0].startMs in SoundPhaseRecovery.FRAME_MS..1_500L &&
                gap.startMs - (before.last().startMs + SoundPhaseRecovery.FRAME_MS) <= 500 &&
                after.first().startMs - gap.endMs <= 500 &&
                frames.filter { it.startMs in before.first().startMs..before.last().startMs }.map { it.startMs } == before.map { it.startMs } &&
                frames.filter { it.startMs in after.first().startMs..after.last().startMs }.map { it.startMs } == after.map { it.startMs }
        }
        if (!hasSupportedPause) return null
        val opening = when (event) {
            VocalEvent.CRY -> "잠깐 조용해졌다가 울음이 다시 나왔어요."
            VocalEvent.LAUGH -> "잠깐 조용해졌다가 웃음이 다시 나왔어요."
            VocalEvent.BABBLE -> "잠깐 조용해졌다가 옹알옹알, 소리를 다시 건네요."
            VocalEvent.CHILD_VOICE -> "잠깐 조용해졌다가 말소리를 다시 건네요."
        }
        val phrases = variants(event, shape)
        return "$opening ${phrases[Math.floorMod(shape.wordingKey, phrases.size)]}"
    }

    /** Two to four stable, adjacent runs establish a simple order. Do not turn an
     * aggregate/broad label, alternating guesses or duplicated timestamps into a story.
     * This describes observed sound order, never a change of mood or its cause. */
    fun orderedPhrase(audio: SoundAnalysis, decision: SoundDecision, range: ClipRange, wordingKey: Int): String? {
        if (decision.problem != null || decision.broadOnly || decision.events.size < 2) return null
        val moments = decision.moments
        if (moments.any { it.startMs !in 0 until range.durationMs } ||
            moments.zipWithNext().any { (a, b) -> b.startMs - a.startMs !in 1..1_500 }) return null
        // Retain the original timeline: a dropped conflict/duplicate observation must
        // not disappear into an apparently uninterrupted sequence.
        val sourceTimes = audio.frames.map { it.startMs }
        if (sourceTimes.isEmpty() || sourceTimes.any { it !in 0 until range.durationMs } ||
            sourceTimes.zipWithNext().any { (a, b) -> b <= a } ||
            sourceTimes.filter { it in moments.first().startMs..moments.last().startMs } != moments.map { it.startMs }) return null
        val runs = mutableListOf<MutableList<VocalMoment>>()
        for (moment in moments) {
            if (runs.isEmpty() || runs.last().last().event != moment.event) runs += mutableListOf(moment)
            else runs.last().add(moment)
        }
        // Babble/child-speech switches may be label uncertainty in one neutral vocalization.
        // They do not by themselves establish a behavioral change, even in long runs.
        if (runs.size !in 2..4 || decision.neutralOnly ||
            runs.any { it.size < 2 || it.last().startMs - it.first().startMs < 900 }) return null
        val first = runs[0][0].event
        val last = runs.last().first().event
        val sounds = runs.map { run ->
            when (run[0].event) {
                VocalEvent.CRY -> "울음 소리"
                VocalEvent.LAUGH -> "웃음 소리"
                VocalEvent.BABBLE -> "옹알이"
                VocalEvent.CHILD_VOICE -> "말소리"
            }
        }
        val initialOpenings = if (runs.size > 2) {
            listOf("${sounds.first()} 뒤에 ${sounds.drop(1).joinToString(", 이어서 ")}도 나왔어요.",
                "${sounds.joinToString(", 다음에는 ")}로 한마디씩 보탰어요.")
        } else when (first to last) {
            VocalEvent.BABBLE to VocalEvent.LAUGH -> listOf("옹알옹알 얘기하다가 웃음이 나왔어요.", "옹알이 뒤에는 웃음으로 한마디 보탰어요.")
            VocalEvent.LAUGH to VocalEvent.BABBLE -> listOf("웃음 뒤에 옹알옹알, 나만의 말을 보태요.", "웃음으로 인사하고 옹알이로 얘기를 건네요.")
            VocalEvent.CRY to VocalEvent.BABBLE -> listOf("울음 뒤에 옹알옹알, 내 소리를 보태 봤어요.", "울음 뒤에는 옹알이로 나만의 말을 꺼냈어요.")
            VocalEvent.BABBLE to VocalEvent.CRY -> listOf("옹알옹알 하다가 울음이 나왔어요.", "옹알이 뒤에는 울음이 나왔네요.")
            VocalEvent.CRY to VocalEvent.LAUGH -> listOf("울음 소리 뒤에 웃음 소리도 나왔어요.", "울음도 나왔는데, 뒤에는 웃음이 나왔네요.")
            VocalEvent.LAUGH to VocalEvent.CRY -> listOf("웃음 소리 뒤에 울음이 나왔어요.", "웃음도 나왔는데, 뒤에는 울음이 나왔네요.")
            VocalEvent.CHILD_VOICE to VocalEvent.LAUGH -> listOf("말소리 뒤에 웃음이 나왔어요.", "말소리로 인사하고 웃음도 보탰어요.")
            VocalEvent.LAUGH to VocalEvent.CHILD_VOICE -> listOf("웃음 뒤에는 말소리로 얘기를 보태요.", "웃음으로 인사하고 말소리를 건네요.")
            VocalEvent.CRY to VocalEvent.CHILD_VOICE -> listOf("울음 뒤에 말소리로 한마디 보탰어요.", "울음 뒤에는 말소리를 꺼내 봤어요.")
            VocalEvent.CHILD_VOICE to VocalEvent.CRY -> listOf("말소리 뒤에 울음이 나왔어요.", "말소리를 내다가 울음도 나왔네요.")
            else -> return null
        }
        // These connective words retain only the already-validated event order.
        // No change of mood, caregiver action or cause is implied by the key.
        val openings = initialOpenings + listOf(
            "${sounds.joinToString(", 그 뒤에는 ")}로 내 소리를 건넸어요.",
            "처음엔 ${sounds.first()}, 뒤에는 ${sounds.drop(1).joinToString(", 이어서 ")}도 보탰어요.",
            "${sounds.joinToString(", 다음 소리는 ")}였어요.",
            "내 소리 쪽지에는 ${sounds.joinToString(", 그다음 ")} 순서로 적혔어요.",
            "${sounds.joinToString(" 다음 ")} 순서로 소리를 냈어요.",
            "${sounds.first()}로 시작한 내 소리에 ${sounds.drop(1).joinToString(", 뒤이어 ")}도 나왔어요."
        )
        val initialEndings = when {
            VocalEvent.CRY in decision.events -> listOf(" 내 반응을 살피며 천천히 함께해 주세요.",
                " 내 손을 살며시 잡고 다정하게 말 걸어주세요.", " 서두르지 말고 곁에서 기다려 주세요.",
                " 내 속도에 맞춰 부드럽게 답해 줄래요?")
            last == VocalEvent.LAUGH -> listOf(" 우리 같이 웃으며 놀아요!", " 눈을 맞추고 웃어줄래요?",
                " 내 웃음에 미소로 답해 주세요!", " 나랑 마주 보고 재미있게 놀아요.")
            else -> listOf(" 나랑 한마디씩 주고받아 볼까요?", " 내 소리를 한번 흉내 내줄래요?",
                " 다정한 말로 인사를 건네주세요.", " 우리 목소리로 놀이해요.")
        }
        val moreEndings = when {
            VocalEvent.CRY in decision.events -> BabyExpressionCatalog.crying.replies
            last == VocalEvent.LAUGH -> BabyExpressionCatalog.laughing.replies
            else -> BabyExpressionCatalog.neutral.replies
        }
        val endings = (initialEndings + moreEndings.map { " $it" }).distinct().take(16)
        val variant = Math.floorMod(wordingKey, openings.size * endings.size)
        val firstIndex = variant % openings.size
        return openings[firstIndex] + endings[(firstIndex + variant / openings.size) % endings.size]
    }
}
