package com.moa.babylanguage

/** Verifies the kept PCM follows the selection's clock. It never rewrites samples.
 * Decoder rounding within one source sample is tolerated, not a track offset/gap. */
internal class AudioTimestampAudit(private val selectionStartUs: Long) {
    private var keptSourceFrames = 0L
    private var sourceRate: Int? = null
    private var valid = true
    private var observed = false
    val aligned get() = observed && valid

    fun observe(ptsUs: Long, skippedFrames: Int, keptFrames: Int, rate: Int) {
        require(selectionStartUs >= 0 && skippedFrames >= 0 && keptFrames > 0 && rate in 8_000..96_000)
        observed = true
        // Product selections end at 120.5s. Reject impossible decoder timestamps
        // as timing evidence before doing arithmetic, including overflow values.
        if (ptsUs !in -1_000_000L..121_000_000L) { valid = false; return }
        if (sourceRate != null && sourceRate != rate) { valid = false; return }
        sourceRate = rate
        val firstUs = ptsUs + skippedFrames * 1_000_000L / rate
        // Compare to the entire kept PCM clock. Reanchoring to each actual PTS
        // would forgive a one-sample gap per buffer and allow unbounded drift.
        val expectedUs = selectionStartUs + keptSourceFrames * 1_000_000L / rate
        val toleranceUs = (1_000_000L + rate - 1) / rate + 2
        if (firstUs !in (expectedUs - toleranceUs)..(expectedUs + toleranceUs)) valid = false
        keptSourceFrames += keptFrames
    }
}
