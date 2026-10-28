package com.moa.babylanguage

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Synthetic PCM contracts only. Does not instantiate a classifier or certify labels. */
class PublicAudioProbeContractTest {
    @Test fun modelFailureCannotBeCountedAsMeasuredAbstention() {
        PublicAudioProbeContract.requireCompletedAnalysis(SoundAnalysis(emptyList()))
        PublicAudioProbeContract.requireCompletedAnalysis(SoundAnalysis(emptyList(), issue = AnalysisIssue.SILENCE))
        var rejected = false
        try {
            PublicAudioProbeContract.requireCompletedAnalysis(SoundAnalysis(emptyList(), issue = AnalysisIssue.MODEL_FAILURE))
        } catch (_: IllegalStateException) { rejected = true }
        assertTrue("Runtime failure must fail the probe even if a later raw pass succeeds", rejected)
    }

    private fun pcm(samples: Int, eventStart: Int = samples): ByteArray =
        ByteBuffer.allocate(samples * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            repeat(samples) { putFloat(if (it >= eventStart) .25f else 0f) }
        }.array()

    private fun entry(bytes: ByteArray) = JSONObject().put("id", "synthetic")
        .put("pcm_sha256", PublicAudioProbeContract.sha256(bytes)).put("pcm_bytes", bytes.size)
        .put("pcm_samples", bytes.size / 4).put("duration_ms", (bytes.size / 4 * 1000L + 15999) / 16000)
        .put("sample_rate_hz", 16000).put("channels", 1).put("encoding", "float32le")

    private fun rejects(bytes: ByteArray, metadata: JSONObject = entry(bytes)) {
        var rejected = false
        try { PublicAudioProbeContract.decode(bytes, metadata) }
        catch (_: IllegalArgumentException) { rejected = true }
        assertTrue("Invalid fixture must be rejected before analysis", rejected)
    }

    @Test fun twentySecondTailEventKeepsFullRangeAndSamples() {
        val bytes = pcm(320000, 304000) // Event only in the final second.
        val decoded = PublicAudioProbeContract.decode(bytes, entry(bytes))
        assertEquals(ClipRange(0, 20000), decoded.range)
        assertEquals(320000, decoded.pcm.size)
        assertEquals(.25f, decoded.pcm.last(), 0f)
        assertTrue(decoded.range.endMs > 19000)
    }

    @Test fun overTwentySecondsAndTruncatedBytesAreRejected() {
        rejects(pcm(320016)) // 20001ms.
        val bytes = pcm(16000)
        rejects(bytes.copyOf(bytes.size - 1), entry(bytes))
        rejects(bytes.copyOf(bytes.size - 4), entry(bytes))
    }

    @Test fun nonfiniteAmplitudeAndHashMismatchAreRejected() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, 1.002f)) {
            val bytes = pcm(16000)
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putFloat(value)
            rejects(bytes)
        }
        val bytes = pcm(16000)
        rejects(bytes, entry(bytes).put("pcm_sha256", "0".repeat(64)))
    }

    @Test fun amplitudeToleranceAndPartialMillisecondTailArePreserved() {
        val bytes = pcm(16001)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putFloat(1.001f)
        val decoded = PublicAudioProbeContract.decode(bytes, entry(bytes))
        assertEquals(ClipRange(0, 1001), decoded.range)
        assertEquals(1.001f, decoded.pcm.first(), 0f)
    }
}
