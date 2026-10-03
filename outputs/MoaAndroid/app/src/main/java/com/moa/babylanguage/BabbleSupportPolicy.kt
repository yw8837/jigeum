package com.moa.babylanguage

/** A broad classification interval; neither an onset nor continuous baby activity. */
data class BabbleSupportWindow(val startMs: Long, val endMs: Long)

internal object BabbleSupportPolicy {
    // Candidate v2 adds independent laughter/cry subclasses to the v1 conflict guard.
    // It is a stricter evaluation candidate, not calibrated infant accuracy.
    // Enable only after the independent source and Android gates in the decision note.
    const val ENABLED = false

    private val contraryLabels = setOf("Baby cry, infant cry", "Baby laughter",
        "Crying, sobbing", "Laughter", "Screaming", "Giggle", "Snicker", "Belly laugh",
        "Chuckle, chortle", "Whimper", "Wail, moan")
    private val requiredLabels = contraryLabels + setOf("Babbling", "Child speech, kid speaking", "Television")
    private val recoverableIssues = setOf(AnalysisIssue.INFANT_UNCONFIRMED, AnalysisIssue.BACKGROUND_DOMINANT)

    fun shouldAnalyze(audio: SoundAnalysis): Boolean {
        if (audio.problem != null) return false
        val original = audio.copy(babbleWindows = emptyList())
        if (SoundTimeline.decide(original).issue !in recoverableIssues) return false
        // Even an unresolved YAMNet emotional event must not be silently relabelled neutral.
        return (audio.frames.flatMap { it.cues } + audio.cues).none { cue ->
            cue.score.isFinite() && cue.score in 0f..1f && when (cue.label) {
                "울음", "아기 웃음 후보" -> cue.score >= .35f
                "울음(화자 미확인)", "웃음(화자 미확인)", "비명(화자 미확인)" -> cue.score >= .65f
                else -> false
            }
        }
    }

    /** Explicit opt-in for evaluation. Does not read ENABLED; production call sites do. */
    fun attachCandidate(audio: SoundAnalysis, ast: AstAudioAnalysis, durationMs: Long): SoundAnalysis {
        val original = audio.copy(babbleWindows = emptyList())
        if (!shouldAnalyze(original) || ast.problem != null || ast.windows.isEmpty() || durationMs !in 1_000..20_000)
            return original
        if (ast.windows.any { window ->
                window.startMs < 0 || window.endMs > durationMs || window.endMs - window.startMs !in 1_000..10_000 ||
                    !window.scores.keys.containsAll(requiredLabels) ||
                    window.scores.values.any { !it.isFinite() || it !in 0f..1f }
            }) return original
        // The complete selection can contain a conflicting event outside a candidate window.
        // This holds the new attribution; it never creates a cry/laugh classification.
        if (ast.windows.any { window ->
                contraryLabels.any { window.scores.getValue(it) >= .30f } || window.scores.getValue("Television") >= .30f
            }) return original
        val supported = ast.windows.filter { window ->
            val babble = window.scores.getValue("Babbling")
            val child = window.scores.getValue("Child speech, kid speaking")
            val contrary = contraryLabels.maxOf { window.scores.getValue(it) }
            (babble >= .30f || (babble >= .10f && child >= .30f)) &&
                !(contrary >= .10f && contrary >= babble * .60f)
        }.map { BabbleSupportWindow(it.startMs, it.endMs) }.distinct().sortedBy { it.startMs }
        return original.copy(babbleWindows = supported)
    }
}
