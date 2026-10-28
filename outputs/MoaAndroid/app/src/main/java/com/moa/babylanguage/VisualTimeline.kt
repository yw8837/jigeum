package com.moa.babylanguage

/** Temporal shape of sampled visual evidence, never an identity or speaking verdict. */
internal data class VisualTimelineSummary(val ordered: Boolean, val coversSelection: Boolean,
    val mouthTransitions: Int, val observedStartMs: Long?, val observedEndMs: Long?,
    val longestGapMs: Long, val phases: List<VisualPhase> = emptyList(),
    val mouthChanges: List<VisualMouthChange> = emptyList())

internal data class VisualPhase(val startMs: Long, val endMs: Long, val samples: Int,
    val singleFaceSamples: Int, val openMouthSamples: Int, val ambiguousSamples: Int)
internal data class VisualMouthChange(val startMs: Long, val endMs: Long, val opening: Boolean)

internal object VisualTimeline {
    fun summarize(frames: List<VisualFrameObservation>, durationMs: Long): VisualTimelineSummary {
        val times = frames.map { it.presentationOffsetMs }
        val known = times.filterNotNull()
        val ordered = known.size == frames.size && known.zipWithNext().all { (a, b) -> b > a } &&
            known.all { it in 0 until durationMs }
        val gaps = if (ordered && known.isNotEmpty()) listOf(known.first()) +
            known.zipWithNext().map { (a, b) -> b - a } + (durationMs - known.last()) else emptyList()
        val longest = gaps.maxOrNull() ?: durationMs
        val coverage = ordered && known.isNotEmpty() && longest <= 750
        val transitions = mutableListOf<VisualMouthChange>()
        if (ordered) {
            var previousAt: Long? = null
            var stableAt: Long? = null
            var open: Boolean? = null
            for (frame in frames) {
                val at = frame.presentationOffsetMs!!
                val opening = frame.mouthOpening
                if (!frame.valid || frame.faceCount != 1 || opening == null ||
                    !opening.isFinite() || opening < 0f) {
                    previousAt = null; stableAt = null; open = null
                    continue
                }
                if (previousAt?.let { at - it > 750 } == true) {
                    stableAt = null; open = null
                }
                // Intermediate samples retain the last established state, but only
                // while every adjacent observation is usable and close in time.
                // The times bound observed states, not an interpolated movement.
                val nextOpen = when {
                    opening <= .08f -> false
                    opening >= .18f -> true
                    else -> null
                }
                if (nextOpen != null) {
                    if (open != null && open != nextOpen) {
                        transitions += VisualMouthChange(stableAt!!, at, nextOpen)
                    }
                    open = nextOpen
                    stableAt = at
                }
                previousAt = at
            }
        }
        val phases = if (!ordered) emptyList() else (0..2).map { index ->
            val start = durationMs * index / 3
            val end = durationMs * (index + 1) / 3
            val section = frames.filter { it.presentationOffsetMs!! in start until end }
            VisualPhase(start, end, section.size, section.count { it.valid && it.faceCount == 1 },
                section.count { it.valid && it.faceCount == 1 && (it.mouthOpening ?: 0f) >= .18f },
                section.count { it.faceCount > 1 })
        }
        return VisualTimelineSummary(ordered, coverage, transitions.size, known.firstOrNull(), known.lastOrNull(),
            longest, phases, transitions)
    }
}
