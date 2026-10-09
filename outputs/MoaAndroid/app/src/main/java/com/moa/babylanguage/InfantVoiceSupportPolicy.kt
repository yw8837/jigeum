package com.moa.babylanguage

/** A broad infant-vocalization clue, never a babbling/emotion label or event onset. */
data class InfantVoiceSupportWindow(val startMs: Long, val endMs: Long, val kindUncertain: Boolean = false)

internal object InfantVoiceSupportPolicy {
    private val contrary = setOf("Baby cry, infant cry", "Baby laughter", "Crying, sobbing", "Laughter",
        "Screaming", "Giggle", "Snicker", "Belly laugh", "Chuckle, chortle", "Whimper", "Wail, moan", "Television")

    /** An unresolved event type is not absence of an infant clue. Only actual,
     * audible YAMNet infant windows qualify for the separately corroborated path. */
    fun shouldCorroborate(audio: SoundAnalysis, durationMs: Long): Boolean {
        if (audio.problem != null || durationMs !in 1_000..20_000 ||
            audio.babbleWindows.isNotEmpty() || audio.infantVoiceWindows.isNotEmpty() ||
            SoundTimeline.decide(audio, durationMs).issue !in setOf(
                AnalysisIssue.INFANT_UNCONFIRMED, AnalysisIssue.MIXED_VOCAL) ||
            audio.frames.isEmpty() || audio.frames.any { frame ->
                frame.startMs !in 0 until durationMs || frame.cues.isEmpty() ||
                    frame.cues.any { !it.score.isFinite() || it.score !in 0f..1f } ||
                    frame.windowRms?.let { !it.isFinite() || it !in 0.0..1.0 } != false
            } || audio.frames.zipWithNext().any { (a, b) -> b.startMs <= a.startMs }) return false
        return infantFrames(audio).any { it.windowRms!! >= .003 }
    }

    private fun infantFrames(audio: SoundAnalysis) = audio.frames.filter {
        SoundTimeline.hasInfantCandidate(SoundAnalysis(emptyList(), frames = listOf(it)))
    }

    /** Frozen caption-weak public head. Scores are not calibrated infant accuracy.
     * Existing approvals win. AST emotion/TV guards still cover the entire selection. */
    fun attachCandidate(audio: SoundAnalysis, ast: AstAudioAnalysis, durationMs: Long): SoundAnalysis {
        if (SoundTimeline.decide(audio, durationMs).problem == null ||
            audio.infantVoiceWindows.isNotEmpty() ||
            ast.problem != null || durationMs !in 1_000..20_000 || ast.windows.isEmpty()) return audio
        val unopposed = BabbleSupportPolicy.shouldAnalyze(audio)
        if (!unopposed && !shouldCorroborate(audio, durationMs)) return audio
        val expected = if (durationMs <= 10_000) listOf(0L to durationMs) else {
            val starts = (0L..durationMs - 10_000 step 5_000).toMutableList()
            if (starts.last() != durationMs - 10_000) starts += durationMs - 10_000
            starts.map { it to it + 10_000 }
        }
        if (ast.windows.map { it.startMs to it.endMs } != expected || ast.windows.any { window ->
                !window.scores.keys.containsAll(contrary) || window.scores.values.any { !it.isFinite() || it !in 0f..1f } ||
                    window.infantPresence?.let { !it.isFinite() || it !in 0f..1f } != false ||
                    window.captionPresence?.let { !it.isFinite() || it !in 0f..1f } == true ||
                    window.strongestSecondRms?.let { !it.isFinite() || it !in 0.0..1.0 } != false
            }) return audio
        if (ast.windows.any { window -> contrary.any { window.scores.getValue(it) >= .30f } }) return audio
        if (!unopposed) {
            val anchors = infantFrames(audio)
            fun contains(window: AstAudioWindow, frame: SoundFrame) =
                frame.startMs >= window.startMs && minOf(frame.startMs + 975, durationMs) <= window.endMs
            // Both heads share an encoder: agreement is a conservative consistency
            // check, not independent proof. Never borrow identity or level from elsewhere.
            val supported = ast.windows.filter { window ->
                window.infantPresence!! >= .80f && (window.captionPresence ?: 0f) >= .80f &&
                    window.strongestSecondRms!! >= .003 && anchors.any {
                        it.windowRms!! >= .003 && contains(window, it)
                    }
            }
            if (supported.isEmpty() || anchors.any { frame -> supported.none { contains(it, frame) } }) return audio
            return audio.copy(infantVoiceWindows = supported.map {
                InfantVoiceSupportWindow(it.startMs, it.endMs, kindUncertain = true)
            })
        }
        // Audibility must be in the same broad window, not an unrelated louder
        // parent/music segment elsewhere. Reuse the existing input-silence floor.
        val audible = ast.windows.filter { it.strongestSecondRms!! >= .003 }
        val original = audible.filter { it.infantPresence!! >= .80f }
        // Preserve an already sufficient base-head range set. Extra ranges can
        // otherwise change deterministic phrases on previously accepted clips.
        val selected = if (original.isNotEmpty()) original else audible.filter { (it.captionPresence ?: 0f) >= .80f }
        val supported = selected.map { InfantVoiceSupportWindow(it.startMs, it.endMs) }
        return if (supported.isEmpty()) audio else audio.copy(infantVoiceWindows = supported)
    }
}
