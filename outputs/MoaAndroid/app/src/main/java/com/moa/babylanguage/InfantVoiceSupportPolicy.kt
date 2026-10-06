package com.moa.babylanguage

/** A broad infant-vocalization clue, never a babbling/emotion label or event onset. */
data class InfantVoiceSupportWindow(val startMs: Long, val endMs: Long)

internal object InfantVoiceSupportPolicy {
    private val contrary = setOf("Baby cry, infant cry", "Baby laughter", "Crying, sobbing", "Laughter",
        "Screaming", "Giggle", "Snicker", "Belly laugh", "Chuckle, chortle", "Whimper", "Wail, moan", "Television")

    /** Frozen caption-weak public head. This score is not calibrated infant accuracy.
     * Existing approvals win. Any conflict anywhere in the selection keeps attribution held. */
    fun attachCandidate(audio: SoundAnalysis, ast: AstAudioAnalysis, durationMs: Long): SoundAnalysis {
        if (SoundTimeline.decide(audio, durationMs).problem == null || !BabbleSupportPolicy.shouldAnalyze(audio) ||
            audio.infantVoiceWindows.isNotEmpty() ||
            ast.problem != null || durationMs !in 1_000..20_000 || ast.windows.isEmpty()) return audio
        val expected = if (durationMs <= 10_000) listOf(0L to durationMs) else {
            val starts = (0L..durationMs - 10_000 step 5_000).toMutableList()
            if (starts.last() != durationMs - 10_000) starts += durationMs - 10_000
            starts.map { it to it + 10_000 }
        }
        if (ast.windows.map { it.startMs to it.endMs } != expected || ast.windows.any { window ->
                !window.scores.keys.containsAll(contrary) || window.scores.values.any { !it.isFinite() || it !in 0f..1f } ||
                    window.infantPresence?.let { !it.isFinite() || it !in 0f..1f } != false ||
                    window.strongestSecondRms?.let { !it.isFinite() || it !in 0.0..1.0 } != false
            }) return audio
        if (ast.windows.any { window -> contrary.any { window.scores.getValue(it) >= .30f } }) return audio
        // Audibility must be in the same broad window, not an unrelated louder
        // parent/music segment elsewhere. Reuse the existing input-silence floor.
        val supported = ast.windows.filter { it.infantPresence!! >= .80f && it.strongestSecondRms!! >= .003 }
            .map { InfantVoiceSupportWindow(it.startMs, it.endMs) }
        return if (supported.isEmpty()) audio else audio.copy(infantVoiceWindows = supported)
    }
}
