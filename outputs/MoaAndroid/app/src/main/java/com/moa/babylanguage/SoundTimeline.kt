package com.moa.babylanguage

/** Window start comes from MediaPipe, not an assumed Keras hop length. */
data class SoundFrame(val startMs: Long, val cues: List<SoundCue>)

internal enum class VocalEvent(val label: String, val cue: String) {
    CRY("울음", "울음"), LAUGH("아기 웃음", "아기 웃음 후보"), BABBLE("옹알이", "옹알이 후보"),
    CHILD_VOICE("어린 말소리", "어린 말소리 후보")
}

internal data class VocalMoment(val event: VocalEvent, val startMs: Long)
internal data class SoundDecision(val moments: List<VocalMoment> = emptyList(), val problem: String? = null) {
    val events get() = moments.map { it.event }.distinct()
    val sound get() = events.singleOrNull()?.label ?: "여러 아기 소리"
}

/** Event observations only. Scores are uncalibrated multilabel outputs, not intent probabilities.
 * Parent laughter/crying scores support a candidate but never establish infant identity alone. */
internal object SoundTimeline {
    private const val INFANT_FLOOR = .35f
    private const val COMPETITOR_FLOOR = .30f
    private const val COMPETITOR_RATIO = .60f
    private data class Vote(val event: VocalEvent?, val strong: Boolean = false, val conflict: Boolean = false)

    fun decide(audio: SoundAnalysis): SoundDecision {
        audio.problem?.let { return SoundDecision(problem = it) }
        // The fallback keeps callers with one already-aggregated observation conservative.
        val frames = audio.frames.ifEmpty { listOf(SoundFrame(0, audio.cues)) }
        val votes = frames.map { vote(it.cues) }
        val accepted = VocalEvent.entries.filter { event ->
            if (event == VocalEvent.CHILD_VOICE) votes.count { it.event == event } >= 3
            else votes.any { it.event == event && it.strong } ||
                votes.zipWithNext().any { (a, b) -> a.event == event && b.event == event }
        }.toSet()
        val moments = votes.mapIndexedNotNull { index, vote ->
            vote.event?.takeIf { it in accepted }?.let { VocalMoment(it, frames[index].startMs) }
        }
        val conflictCount = votes.count { it.conflict }
        if (moments.isEmpty() || conflictCount >= moments.size) {
            return SoundDecision(problem = if (conflictCount > 0)
                "웃음·울음이나 높은 소리가 겹쳐 한마디로 표현하기 어려워요. 다시 듣고 한 가지 소리가 또렷한 구간을 골라 주세요."
            else "이 구간에서는 아기 소리 단서를 충분히 찾지 못했어요. 소리가 또렷한 구간을 다시 골라 주세요.")
        }
        // A resolved event must not erase a different, unresolved infant event elsewhere.
        if (votes.any { it.event != null && it.event !in accepted }) return SoundDecision(problem =
            "중간에 다른 소리도 짧게 들려 한마디를 정하기 어려워요. 다시 듣고 구간을 조금 좁혀 주세요.")
        if (accepted.size == 1) {
            val event = accepted.single()
            val oppositeLabels = when (event) {
                VocalEvent.CRY -> setOf("아기 웃음 후보", "웃음(화자 미확인)")
                VocalEvent.LAUGH -> setOf("울음", "울음(화자 미확인)")
                VocalEvent.BABBLE, VocalEvent.CHILD_VOICE -> setOf("울음", "울음(화자 미확인)", "아기 웃음 후보", "웃음(화자 미확인)")
            }
            if (frames.indices.any { index -> votes[index].event == null && frames[index].cues.any {
                    it.label in oppositeLabels && it.score.isFinite() && it.score in .65f..1f
                } }) return SoundDecision(problem =
                "중간에 웃음이나 울음이 함께 들려요. 한 가지 소리가 또렷한 구간을 골라 주세요.")
        }
        return SoundDecision(moments = moments)
    }

    private fun vote(cues: List<SoundCue>): Vote {
        fun score(label: String) = cues.filter { it.label == label && it.score.isFinite() && it.score in 0f..1f }
            .maxOfOrNull { it.score } ?: 0f
        val infant = VocalEvent.entries.associateWith { score(it.cue) }
        val support = mapOf(
            VocalEvent.CRY to maxOf(infant.getValue(VocalEvent.CRY), score("울음(화자 미확인)")),
            VocalEvent.LAUGH to maxOf(infant.getValue(VocalEvent.LAUGH), score("웃음(화자 미확인)")),
            VocalEvent.BABBLE to infant.getValue(VocalEvent.BABBLE),
            VocalEvent.CHILD_VOICE to infant.getValue(VocalEvent.CHILD_VOICE))
        val eligible = infant.filterValues { it >= INFANT_FLOOR }.keys
        if (eligible.isEmpty()) return Vote(null)
        val chosen = eligible.maxBy { support.getValue(it) }
        val strength = support.getValue(chosen)
        // Child speech and babbling can describe the same neutral vocalization.
        val competing = support.filterKeys { it != chosen && !(it in neutralEvents && chosen in neutralEvents) }
            .values.maxOrNull() ?: 0f
        if (competing >= COMPETITOR_FLOOR && competing >= strength * COMPETITOR_RATIO)
            return Vote(null, conflict = true)
        // Screams can be joyful; they are not a negative-valence/cry label.
        val scream = score("비명(화자 미확인)")
        if (scream >= .65f && strength < scream * 1.3f) return Vote(null, conflict = true)
        val background = score("배경 소리")
        if (background >= .65f && background >= strength * 1.25f) return Vote(null)
        return Vote(chosen, strong = infant.getValue(chosen) >= .60f && strength >= .70f)
    }
    private val neutralEvents = setOf(VocalEvent.BABBLE, VocalEvent.CHILD_VOICE)
}
