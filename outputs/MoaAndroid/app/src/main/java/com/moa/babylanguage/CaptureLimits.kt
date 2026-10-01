package com.moa.babylanguage

/** Capture/import allowance and per-analysis allowance are separate; neither changes model quality. */
internal class CaptureLimits private constructor(
    val captureMs: Long,
    val analysisMs: Long,
    val fileBytes: Long
) {
    val minutes get() = captureMs / 60_000
    val analysisSeconds get() = analysisMs / 1000
    // Leave room for container finalization below the import cap.
    val recordingBytes get() = fileBytes * 9 / 10
    fun acceptsDuration(durationMs: Long) = durationMs in 1_000..(captureMs + 500)
    fun allowsAnalysis(range: ClipRange) = range.durationMs <= analysisMs

    companion object {
        val FREE = CaptureLimits(60_000, 10_000, 100L * 1024 * 1024)
        val FULL = CaptureLimits(120_000, 20_000, 200L * 1024 * 1024)
        fun forAccess(hasAccess: Boolean) = if (hasAccess) FULL else FREE
    }
}
