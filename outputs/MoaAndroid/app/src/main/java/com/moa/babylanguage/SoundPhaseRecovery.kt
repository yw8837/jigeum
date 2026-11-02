package com.moa.babylanguage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Separate, non-overlapping phase; never combine votes to qualify an event. */
internal object SoundPhaseRecovery {
    const val OFFSET_SAMPLES = 7_800
    const val FRAME_MS = 975L
    private val recoverable = setOf(AnalysisIssue.INFANT_UNCONFIRMED, AnalysisIssue.BACKGROUND_DOMINANT)

    /** Supplemental recognition always sees the baseline, never a shifted replacement. */
    suspend fun complete(original: SoundAnalysis, support: suspend (SoundAnalysis) -> SoundAnalysis,
        shifted: suspend () -> SoundAnalysis): SoundAnalysis {
        coroutineContext.ensureActive()
        val primary = support(original)
        coroutineContext.ensureActive()
        if (!shouldRetry(primary)) return primary
        return try {
            val candidate = shifted()
            coroutineContext.ensureActive()
            choose(primary, candidate)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            coroutineContext.ensureActive()
            primary
        } catch (_: LinkageError) {
            coroutineContext.ensureActive()
            primary
        }
    }

    fun shouldRetry(audio: SoundAnalysis): Boolean = audio.problem == null &&
        audio.babbleWindows.isEmpty() && audio.infantVoiceWindows.isEmpty() && audio.frames.isNotEmpty() &&
        !SoundTimeline.hasInfantCandidate(audio) &&
        SoundTimeline.decide(audio).issue in recoverable

    fun choose(original: SoundAnalysis, shifted: SoundAnalysis): SoundAnalysis {
        if (!shouldRetry(original) || shifted.problem != null || shifted.babbleWindows.isNotEmpty() || shifted.infantVoiceWindows.isNotEmpty() ||
            shifted.frames.isEmpty() || shifted.frames.first().startMs != OFFSET_SAMPLES / 16L ||
            shifted.frames.zipWithNext().any { (a, b) -> b.startMs - a.startMs < FRAME_MS }) return original
        // Persistence is tested on the second phase alone, whose windows do not overlap.
        val candidate = SoundTimeline.decide(shifted)
        if (candidate.problem != null || candidate.events.isEmpty()) return original
        // Original unresolved/opposite sounds must still be heard. The combined series is
        // rejection-only: never return it or count it as repeated/longer infant activity.
        val combined = SoundTimeline.decide(original.copy(
            frames = (original.frames + shifted.frames).sortedBy { it.startMs }))
        if (combined.problem != null || combined.events.toSet() != candidate.events.toSet()) return original
        return shifted
    }
}
