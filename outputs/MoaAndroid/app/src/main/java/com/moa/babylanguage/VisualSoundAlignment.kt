package com.moa.babylanguage

/** Shared observation windows, not a speaking-person or lip-sync verdict. */
internal object VisualSoundAlignment {
    enum class Cue(val label: String) { MOUTH("입 변화"), CORNERS("입꼬리 올라간 모양") }
    data class Match(val startMs: Long, val endMs: Long, val cue: Cue)

    // The bundled YAMNet consumes 15,600 samples at 16 kHz. A timestamp is
    // a classifier window start, not the exact onset of the sound in that window.
    private const val WINDOW_MS = 975L
    private const val MAX_GAP_MS = 750L
    private val neutral = setOf(VocalEvent.BABBLE, VocalEvent.CHILD_VOICE)

    fun find(decision: SoundDecision, frames: List<VisualFrameObservation>, durationMs: Long): List<Match> {
        if (decision.problem != null || decision.broadWindows.isNotEmpty() || frames.any { it.faceCount > 1 }) return emptyList()
        val moments = decision.moments
        if (moments.isEmpty() || moments.any { it.startMs !in 0 until durationMs } ||
            moments.zipWithNext().any { (a, b) -> b.startMs <= a.startMs }) return emptyList()
        if (!VisualTimeline.summarize(frames, durationMs).ordered) return emptyList()

        fun usable(frame: VisualFrameObservation) = frame.valid && frame.faceCount == 1 &&
            frame.mouthOpening?.let { it.isFinite() && it in 0f..1f } == true
        fun supported(at: Long, events: Set<VocalEvent>) = moments.any {
            it.event in events && at >= it.startMs && at - it.startMs < WINDOW_MS
        }
        fun covered(start: Long, end: Long, events: Set<VocalEvent>): Boolean {
            var through = start
            for (moment in moments) {
                if (moment.event !in events || moment.startMs > through) continue
                if (through >= moment.startMs && through - moment.startMs < WINDOW_MS) {
                    through = minOf(durationMs, moment.startMs + WINDOW_MS)
                    if (through > end) return true
                }
            }
            return false
        }

        val matches = mutableListOf<Match>()
        // A smile-shaped coefficient must persist on adjacent usable frames in
        // the accepted laugh window. Two distant snapshots cannot create a run.
        val smileRun = mutableListOf<VisualFrameObservation>()
        fun finishSmile() {
            if (smileRun.size >= 2) matches += Match(smileRun.first().presentationOffsetMs!!,
                smileRun.last().presentationOffsetMs!!, Cue.CORNERS)
            smileRun.clear()
        }
        for (frame in frames) {
            val at = frame.presentationOffsetMs!!
            val smile = frame.mouthCornerLift
            if (!usable(frame) || smile == null || !smile.isFinite() || smile !in .60f..1f ||
                !supported(at, setOf(VocalEvent.LAUGH))) {
                finishSmile()
                continue
            }
            if (smileRun.isNotEmpty()) {
                val before = smileRun.last().presentationOffsetMs!!
                if (at - before > MAX_GAP_MS || !covered(before, at, setOf(VocalEvent.LAUGH))) finishSmile()
            }
            smileRun += frame
        }
        finishSmile()

        val mouthRun = mutableListOf<VisualFrameObservation>()
        fun finishMouth() {
            if (mouthRun.count { it.mouthOpening!! >= .18f } >= 2 &&
                VisualTimeline.summarize(mouthRun, durationMs).mouthTransitions >= 2) {
                matches += Match(mouthRun.first().presentationOffsetMs!!,
                    mouthRun.last().presentationOffsetMs!!, Cue.MOUTH)
            }
            mouthRun.clear()
        }
        for (frame in frames) {
            val at = frame.presentationOffsetMs!!
            if (!usable(frame) || !supported(at, neutral)) {
                finishMouth()
                continue
            }
            if (mouthRun.isNotEmpty()) {
                val before = mouthRun.last().presentationOffsetMs!!
                if (at - before > MAX_GAP_MS || !covered(before, at, neutral)) finishMouth()
            }
            mouthRun += frame
        }
        finishMouth()
        return matches.sortedBy { it.startMs }
    }
}
