package com.moa.babylanguage

import org.junit.Assert.*
import org.junit.Test

class AudioTimestampAuditTest {
    @Test fun contiguousBuffersAndNonzeroSelectionKeepTheirClock() {
        for (rate in listOf(8000, 16000, 44100, 48000, 96000)) {
            val timing = AudioTimestampAudit(0)
            for (index in 0..19) timing.observe(index * 1024L * 1_000_000 / rate, 0, 1024, rate)
            assertTrue("Contiguous $rate Hz", timing.aligned)
        }
        val later = AudioTimestampAudit(8_000_000)
        later.observe(7_990_000, 480, 480, 48000)
        later.observe(8_010_000, 0, 480, 48000)
        assertTrue(later.aligned)
    }
    @Test fun delayedTrackAndDroppedOrOverlappedBuffersStayUnaligned() {
        val delayed = AudioTimestampAudit(0)
        delayed.observe(3_000_000, 0, 480, 48000)
        delayed.observe(3_010_000, 0, 480, 48000)
        assertFalse(delayed.aligned)
        for (pts in listOf(5_000L, 20_000L, -10_000L, Long.MAX_VALUE, Long.MIN_VALUE)) {
            val timing = AudioTimestampAudit(0)
            timing.observe(0, 0, 480, 48000)
            timing.observe(pts, 0, 480, 48000)
            assertFalse("Invalid timestamp $pts", timing.aligned)
        }
        assertFalse(AudioTimestampAudit(0).aligned)
    }
    @Test fun primingCropAndOneSampleRoundingAreNotTrackOffsets() {
        val timing = AudioTimestampAudit(0)
        timing.observe(-21_333, 1024, 1024, 48000)
        timing.observe(21_333, 0, 1024, 48000)
        assertTrue(timing.aligned)
        val rounding = AudioTimestampAudit(0)
        rounding.observe(20, 0, 480, 48000)
        assertTrue(rounding.aligned)
    }
}
