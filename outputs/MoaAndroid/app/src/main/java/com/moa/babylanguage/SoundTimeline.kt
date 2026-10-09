package com.moa.babylanguage

/** Window start comes from MediaPipe, not an assumed Keras hop length. */
data class SoundFrame(val startMs: Long, val cues: List<SoundCue>, val windowRms: Double? = null)

internal enum class VocalEvent(val label: String, val cue: String) {
    CRY("울음", "울음"), LAUGH("아기 웃음", "아기 웃음 후보"), BABBLE("옹알이", "옹알이 후보"),
    CHILD_VOICE("어린 말소리", "어린 말소리 후보")
}

internal data class VocalMoment(val event: VocalEvent, val startMs: Long)
internal data class SoundDecision(val moments: List<VocalMoment> = emptyList(), val problem: String? = null,
                                  val issue: AnalysisIssue? = null,
                                  val broadWindows: List<BabbleSupportWindow> = emptyList(),
                                  val infantVoiceWindows: List<InfantVoiceSupportWindow> = emptyList(),
                                  val partial: Boolean = false) {
    val broadOnly get() = moments.isEmpty() && (broadWindows.isNotEmpty() || infantVoiceWindows.isNotEmpty())
    val events get() = (moments.map { it.event } + if (broadWindows.isEmpty()) emptyList() else listOf(VocalEvent.BABBLE)).distinct()
    val sound get() = if (partial || (infantVoiceWindows.isNotEmpty() && events.isEmpty())) "아기 발성" else events.singleOrNull()?.label ?: "여러 아기 소리"
    val neutralOnly get() = (events.isNotEmpty() && events.all { it == VocalEvent.BABBLE || it == VocalEvent.CHILD_VOICE }) ||
        (infantVoiceWindows.isNotEmpty() && events.isEmpty())
}

/** Event observations only. Scores are uncalibrated multilabel outputs, not intent probabilities.
 * Parent laughter/crying scores support a candidate but never establish infant identity alone. */
internal object SoundTimeline {
    private const val INFANT_FLOOR = .35f
    private const val COMPETITOR_FLOOR = .30f
    private const val COMPETITOR_RATIO = .60f
    private data class Vote(val event: VocalEvent?, val strong: Boolean = false, val conflict: Boolean = false)

    /** Preserve repeated, audible observations inside an otherwise mixed selection.
     * This is not permission to call the whole clip happy/sad or to erase its holds.
     * The original decision and supplementation policies remain unchanged. */
    fun partialObservation(audio: SoundAnalysis, durationMs: Long): SoundDecision? {
        if (audio.problem != null || durationMs !in 1_000..20_000 ||
            decide(audio, durationMs).issue != AnalysisIssue.MIXED_VOCAL) return null
        val frames = audio.frames
        if (frames.isEmpty() || frames.any { frame ->
                frame.startMs !in 0 until durationMs || frame.cues.isEmpty() ||
                    frame.cues.any { !it.score.isFinite() || it.score !in 0f..1f } ||
                    frame.windowRms?.let { !it.isFinite() || it !in 0.0..1.0 } != false
            } || frames.zipWithNext().any { (a, b) -> b.startMs <= a.startMs }) return null
        val votes = frames.map { vote(it.cues) }
        val supported = mutableListOf<VocalMoment>()
        val run = mutableListOf<Int>()
        fun finish() {
            val event = run.firstOrNull()?.let { votes[it].event }
            if (event != null && run.size >= if (event == VocalEvent.CHILD_VOICE) 3 else 2)
                supported += run.map { VocalMoment(event, frames[it].startMs) }
            run.clear()
        }
        for (index in frames.indices) {
            val frame = frames[index]
            val end = minOf(frame.startMs + SoundPhaseRecovery.FRAME_MS, durationMs)
            // A shifted window may overlap a conflicting observation. It cannot
            // borrow that time as clean evidence merely because its own vote passed.
            val overlapsConflict = frames.indices.any { other ->
                votes[other].conflict && frames[other].startMs < end &&
                    frames[other].startMs + SoundPhaseRecovery.FRAME_MS > frame.startMs
            }
            if (votes[index].event == null || frame.windowRms!! < .003 ||
                end - frame.startMs < SoundPhaseRecovery.FRAME_MS || overlapsConflict) {
                finish(); continue
            }
            if (run.isNotEmpty() && (votes[run.last()].event != votes[index].event ||
                frame.startMs - frames[run.last()].startMs !in SoundPhaseRecovery.FRAME_MS..1_500L)) finish()
            run += index
        }
        finish()
        return supported.takeIf { it.isNotEmpty() }?.let { SoundDecision(moments = it, partial = true) }
    }

    /** A recognized but insufficiently persistent event is a deliberate hold, not a
     * window that missed every infant class. Reuse the existing identity floor. */
    fun hasInfantCandidate(audio: SoundAnalysis): Boolean =
        (audio.frames.flatMap { it.cues } + audio.cues).any { cue ->
            VocalEvent.entries.any { it.cue == cue.label } &&
                cue.score.isFinite() && cue.score in INFANT_FLOOR..1f
        }

    fun decide(audio: SoundAnalysis, durationMs: Long = 30_000): SoundDecision {
        val original = decideYamnet(audio, durationMs)
        val corroboratedUncertainty = original.issue == AnalysisIssue.MIXED_VOCAL &&
            audio.infantVoiceWindows.isNotEmpty() && audio.infantVoiceWindows.all { it.kindUncertain }
        if ((!corroboratedUncertainty && original.issue !in setOf(AnalysisIssue.INFANT_UNCONFIRMED, AnalysisIssue.BACKGROUND_DOMINANT)) ||
            audio.problem != null) return original
        if (audio.babbleWindows.isEmpty()) {
            if (audio.infantVoiceWindows.isEmpty() || durationMs < 1_000 || audio.infantVoiceWindows.any {
                    it.startMs < 0 || it.endMs > minOf(durationMs, 20_000) || it.endMs - it.startMs !in 1_000..10_000
                } || audio.infantVoiceWindows.zipWithNext().any { (a, b) -> b.startMs <= a.startMs }) return original
            return SoundDecision(infantVoiceWindows = audio.infantVoiceWindows)
        }
        if (audio.babbleWindows.any { it.startMs < 0 || it.endMs > 20_000 || it.endMs - it.startMs !in 1_000..10_000 })
            return original
        // Broad AST intervals are intentionally not VocalMoments: no exact onset, frame
        // persistence, pause attribution or repeated-baby-event count follows from them.
        return SoundDecision(broadWindows = audio.babbleWindows)
    }

    private fun decideYamnet(audio: SoundAnalysis, durationMs: Long): SoundDecision {
        audio.problem?.let { return SoundDecision(problem = it, issue = audio.issue) }
        // The fallback keeps callers with one already-aggregated observation conservative.
        val frames = audio.frames.ifEmpty { listOf(SoundFrame(0, audio.cues)) }
        val votes = frames.map { vote(it.cues) }
        val neutralSupport = neutralContinuity(frames, votes, durationMs)
        val accepted = VocalEvent.entries.filter { event ->
            if (event == VocalEvent.CHILD_VOICE) votes.count { it.event == event } >= 3
            else votes.any { it.event == event && it.strong } ||
                votes.zipWithNext().any { (a, b) -> a.event == event && b.event == event }
        }.toSet()
        val moments = votes.mapIndexedNotNull { index, vote ->
            vote.event?.takeIf { it in accepted || index in neutralSupport }?.let { VocalMoment(it, frames[index].startMs) }
        }
        val conflictCount = votes.count { it.conflict }
        if (moments.isEmpty() || conflictCount >= moments.size) {
            val backgroundDominant = frames.count { frame ->
                val background = frame.cues.filter { it.label == "배경 소리" && it.score.isFinite() && it.score in 0f..1f }
                    .maxOfOrNull { it.score } ?: 0f
                val voice = frame.cues.filter { it.label != "배경 소리" && it.score.isFinite() && it.score in 0f..1f }
                    .maxOfOrNull { it.score } ?: 0f
                background >= .65f && background > voice * 1.25f
            } > frames.size / 2
            return SoundDecision(problem = if (conflictCount > 0)
                "여러 소리가 섞여 구분하기 어려워요. 구간을 조금 줄여 보세요."
            else if (backgroundDominant) "주변 소리 단서가 더 뚜렷해요. 아기 소리가 또렷한 구간을 골라 주세요."
            else "아기 소리인지 구분하기 어려워요. 다른 구간으로 다시 해보세요.",
                issue = if (conflictCount > 0) AnalysisIssue.MIXED_VOCAL else if (backgroundDominant)
                    AnalysisIssue.BACKGROUND_DOMINANT else AnalysisIssue.INFANT_UNCONFIRMED)
        }
        // A resolved event must not erase a different, unresolved infant event elsewhere.
        if (votes.indices.any { votes[it].event != null && votes[it].event !in accepted && it !in neutralSupport }) return SoundDecision(problem =
            "서로 다른 소리가 들려요. 구간을 조금 줄여 보세요.", issue = AnalysisIssue.MIXED_VOCAL)
        val observedEvents = moments.map { it.event }.toSet()
        if (observedEvents.size == 1 || observedEvents.all { it in neutralEvents }) {
            val oppositeLabels = when (observedEvents.singleOrNull()) {
                VocalEvent.CRY -> setOf("아기 웃음 후보", "웃음(화자 미확인)")
                VocalEvent.LAUGH -> setOf("울음", "울음(화자 미확인)")
                VocalEvent.BABBLE, VocalEvent.CHILD_VOICE, null -> setOf("울음", "울음(화자 미확인)", "아기 웃음 후보", "웃음(화자 미확인)")
            }
            if (frames.indices.any { index -> votes[index].event == null && frames[index].cues.any {
                    it.label in oppositeLabels && it.score.isFinite() && it.score in .65f..1f
                } }) return SoundDecision(problem =
                "다른 발성 단서가 함께 보여요. 다른 구간으로 다시 해보세요.", issue = AnalysisIssue.MIXED_VOCAL)
        }
        return SoundDecision(moments = moments)
    }

    /** Neutral labels may fluctuate within one audible run. Require at least the
     * child-voice count on distinct, adjacent non-overlapping windows. Keep each
     * original label; generic speech, conflicts and distant spikes cannot fill a gap. */
    private fun neutralContinuity(frames: List<SoundFrame>, votes: List<Vote>, durationMs: Long): Set<Int> {
        val supported = mutableSetOf<Int>()
        val run = mutableListOf<Int>()
        fun finish() {
            if (run.size >= 3 && frames[run.last()].startMs - frames[run.first()].startMs >= 1_800 &&
                run.map { votes[it].event }.distinct().size >= 2) supported.addAll(run)
            run.clear()
        }
        for (index in votes.indices) {
            if (votes[index].event !in neutralEvents || frames[index].startMs !in 0 until durationMs) {
                finish()
                continue
            }
            if (run.isNotEmpty() && frames[index].startMs - frames[run.last()].startMs !in 900..1_500) finish()
            run += index
        }
        finish()
        return supported
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
