package com.moa.babylanguage

/** Authored character expressions after sound acceptance, not a desire classifier or transcript.
 * Wording keys only pick surface variants. They must never pick a hunger/sleep/pain hypothesis. */
internal object BabyExpression {
    private val infantVoice = listOf(
        "작은 목소리로 인사를 건네요. 눈을 맞추고 다정하게 답해 줄래요?",
        "내 소리에 귀 기울여 주세요. 무슨 뜻인지 서두르지 말고 함께해요.",
        "나만의 소리로 한마디 보태요. 편안한 목소리로 말을 걸어주세요.",
        "말은 아직 서툴러요. 내 반응을 살피며 천천히 답해 주세요.",
        "내 방식으로 소리를 내봤어요. 곁에서 다정하게 받아 줄래요?",
        "작은 목소리를 건네요. 눈을 맞추고 함께 이야기해요.",
        "내 소리에 한마디 답해줄래요? 편안하게 기다려 주세요.",
        "소리로 인사를 보태요. 부드럽게 말을 걸며 함께해 주세요."
    )
    private val crying = listOf(
        "말로 다 못하겠어요. 토닥토닥, 다정하게 달래줄래요?",
        "포근하게 나를 안아줄래요? 서두르지 말고 천천히 함께해 주세요.",
        "큰 말 대신 울음이 나왔어요. 곁에서 다정한 목소리를 들려주세요.",
        "내 마음을 말로 꺼내기가 어려워요. 조금만 다정하게 기다려 주세요.",
        "응애… 말은 서툴러도 전하고 싶은 게 있어요. 살며시 토닥여 줄래요?",
        "나에게 부드럽게 인사해 줄래요? 작은 목소리로 말을 걸어주세요.",
        "오늘의 내 말은 울음으로 나왔어요. 나에게 맞춰 천천히 함께해 주세요.",
        "나를 달래주는 목소리를 들려줄래요? 따뜻한 인사부터 해 줘요."
    )
    private val laughing = listOf(
        "웃음이 퐁퐁 나와요. 우리 같이 웃으며 놀아요!",
        "히히, 웃음이 나요. 나랑 즐겁게 놀아줘요!",
        "입 밖으로 웃음이 나왔어요. 이 순간을 함께 즐겨요!",
        "내 작은 웃음을 나눠 줄게요. 눈을 맞추고 웃어줄래요?",
        "말 대신 웃음으로 인사할게요. 반가운 미소로 답해 주세요!",
        "웃는 소리로 한마디 보태요. 우리 웃음 소리도 닮았을까요?",
        "헤헤, 웃음이 나왔네! 나랑 마주 보고 재미있게 놀아요.",
        "작은 웃음 한 조각 받아요! 내 웃음에 미소로 답해 줄래요?"
    )
    private val babbling = listOf(
        "내 입에서 소리가 나왔어요. 우리 소리 놀이 해볼까요?",
        "말은 서툴러도 나만의 말이 있어요. 이 소리, 한번 흉내 내줄래요?",
        "옹알옹알, 나도 대화에 끼고 싶어요. 한마디씩 주고받아 볼까요?",
        "내 목소리로 인사를 건넬게요. 우리 눈을 맞추고 얘기해요.",
        "이건 나만의 작은 수다예요. 알아듣기 어려워도 다정하게 대답해 줘요.",
        "입으로 소리를 만들어 봐요. 나랑 번갈아 해볼까요?",
        "나도 한마디 보태 볼게요. 오늘은 우리 목소리로 놀아요.",
        "하고 싶은 말은 내 방식으로 꺼내 볼래요. 소리로 주고받는 인사예요."
    )
    private val talking = listOf(
        "나만의 말로 한마디 보태 볼게요. 우리 대화에 나도 끼워 주세요.",
        "말을 건네 보는 중이에요. 무슨 말인지 서둘러 맞히지 않아도 괜찮아요.",
        "내 목소리로 인사할게요. 반가운 말로 답해 줄래요?",
        "나도 말을 주고받고 싶어요. 천천히 한마디씩 해봐요.",
        "목소리로 놀아 볼까요? 내 소리를 한번 흉내 내주세요.",
        "작은 수다를 건네 볼게요. 눈을 맞추고 다정하게 받아 주세요.",
        "내 방식으로 얘기해 볼래요. 나랑 소리 놀이 해요.",
        "말 한마디를 보태고 싶어요. 편안하게 대화해 볼까요?"
    )
    private val mixed = listOf(
        "내 소리가 여러 모습으로 나왔어요. 한 가지 마음으로 정하지 말고 천천히 함께해 주세요.",
        "이런 소리도, 저런 소리도 나왔네요. 내 속도에 맞춰 다정하게 반응해 주세요.",
        "말로 담기 어려운 순간이에요. 내 소리를 하나씩 받아 줄래요?",
        "소리가 한 가지로만 나오지는 않네요. 지금은 천천히 함께해 주세요.",
        "여러 소리로 나만의 말을 꺼냈어요. 서두르지 않고 주고받아 볼까요?",
        "내 작은 목소리에도 여러 모습이 있어요. 다정한 한마디로 답해 주세요.",
        "내 소리를 하나로 설명하기는 어렵네요. 곁에서 편안하게 함께해 주세요.",
        "다양한 소리가 나왔어요. 내 반응을 살피며 천천히 말을 걸어줄래요?"
    )
    private val neutral = listOf(
        "작은 목소리로 내 말을 건네요. 서툴러도 다정하게 답해 주세요.",
        "이런 소리도 저런 소리도 만들어 봐요. 우리 한마디씩 주고받아 볼까요?",
        "나만의 말로 대화에 끼고 싶어요. 내 소리에 천천히 답해 줄래요?",
        "목소리로 이야기를 건네 봐요. 눈을 맞추고 내 차례를 기다려 주세요.",
        "말은 아직 서툴지만 소리로 인사할게요. 반가운 한마디로 답해 주세요.",
        "내 방식으로 작은 수다를 건네요. 우리 번갈아 목소리로 놀아요.",
        "소리를 만들어 보는 중이에요. 내 목소리를 한번 흉내 내줄래요?",
        "나도 한마디 보태 볼래요. 무슨 말인지 서두르지 말고 다정하게 받아 주세요."
    )

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
        val ordered = base.drop(offset) + base.take(offset)
        if (!sustained) return ordered
        val continuation = if (withPause) "잠깐 쉬었다가 소리를 이었어요. " else ""
        val ending = when (event) {
            VocalEvent.CRY -> " 울음으로 전하는 내 말을 서두르지 말고 받아 주세요."
            VocalEvent.LAUGH -> " 웃음이 이어지는 동안 나랑 함께해 주세요."
            VocalEvent.BABBLE -> " 옹알옹알, 내 방식으로 할 이야기가 많아요."
            VocalEvent.CHILD_VOICE -> " 내 소리가 끝나면 대답하며 주고받아 주세요."
            null -> " 내 반응을 따라 천천히 함께해 주세요."
        }
        return ordered.map { continuation + it + ending }
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
        val openings = if (runs.size > 2) {
            val sounds = runs.map { run ->
                when (run[0].event) {
                    VocalEvent.CRY -> "울음 소리"
                    VocalEvent.LAUGH -> "웃음 소리"
                    VocalEvent.BABBLE -> "옹알이"
                    VocalEvent.CHILD_VOICE -> "말소리"
                }
            }
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
        val endings = when {
            VocalEvent.CRY in decision.events -> listOf(" 내 반응을 살피며 천천히 함께해 주세요.",
                " 내 손을 살며시 잡고 다정하게 말 걸어주세요.", " 서두르지 말고 곁에서 기다려 주세요.",
                " 내 속도에 맞춰 부드럽게 답해 줄래요?")
            last == VocalEvent.LAUGH -> listOf(" 우리 같이 웃으며 놀아요!", " 눈을 맞추고 웃어줄래요?",
                " 내 웃음에 미소로 답해 주세요!", " 나랑 마주 보고 재미있게 놀아요.")
            else -> listOf(" 나랑 한마디씩 주고받아 볼까요?", " 내 소리를 한번 흉내 내줄래요?",
                " 다정한 말로 인사를 건네주세요.", " 우리 목소리로 놀이해요.")
        }
        val variant = Math.floorMod(wordingKey, openings.size * endings.size)
        return openings[variant % openings.size] + endings[variant / openings.size]
    }
}
