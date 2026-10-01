package com.moa.babylanguage

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

/** A generated two-minute MP4 proves duration, seeking and decoding limits.
 * It has silence for 100 seconds and a 997 Hz tone for the last 20 seconds.
 * This is media/model integration coverage, not infant interpretation accuracy. */
@RunWith(AndroidJUnit4::class)
class CaptureLimitsRegressionTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun fixture() = File(context.cacheDir, "qa-capture-limits-${UUID.randomUUID()}.mp4").also { output ->
        instrumentation.context.assets.open("capture-two-minute-tone.mp4").use { input ->
            output.outputStream().use { input.copyTo(it) }
        }
    }

    private fun rms(pcm: FloatArray, start: Int = 0, end: Int = pcm.size): Double {
        var squares = 0.0
        for (i in start until end) squares += pcm[i].toDouble() * pcm[i]
        return sqrt(squares / (end - start))
    }

    private fun assertTone(pcm: FloatArray, start: Int = 0, end: Int = pcm.size) {
        assertTrue("Audible decoded tone: ${rms(pcm, start, end)}", rms(pcm, start, end) in .03.. .4)
        var crossings = 0
        for (i in start + 1 until end) if (pcm[i - 1] < 0 && pcm[i] >= 0) crossings++
        assertEquals("Late range must contain the 997 Hz fixture tone", 997.0,
            crossings * 16_000.0 / (end - start), 8.0)
    }

    @Test fun freeAndFullCaptureBoundariesKeepFinalizationToleranceBounded() {
        assertSame(CaptureLimits.FREE, CaptureLimits.forAccess(false))
        assertSame(CaptureLimits.FULL, CaptureLimits.forAccess(true))
        assertEquals(60_000L, CaptureLimits.FREE.captureMs)
        assertEquals(120_000L, CaptureLimits.FULL.captureMs)
        for (limits in listOf(CaptureLimits.FREE, CaptureLimits.FULL)) {
            assertFalse(limits.acceptsDuration(999))
            assertTrue(limits.acceptsDuration(1_000))
            assertTrue(limits.acceptsDuration(limits.captureMs))
            assertTrue(limits.acceptsDuration(limits.captureMs + 500))
            assertFalse(limits.acceptsDuration(limits.captureMs + 501))
            assertTrue(limits.recordingBytes < limits.fileBytes)
        }
        assertFalse(CaptureLimits.FREE.acceptsDuration(120_000))
        assertTrue(CaptureLimits.FULL.acceptsDuration(120_000))
    }

    @Test fun freeTenAndFullTwentySecondPolicyIsSeparateFromThirtySecondEngineCapacity() {
        assertTrue(CaptureLimits.FREE.allowsAnalysis(ClipRange(0, 10_000)))
        assertFalse(CaptureLimits.FREE.allowsAnalysis(ClipRange(0, 10_001)))
        assertTrue(CaptureLimits.FULL.allowsAnalysis(ClipRange(100_000, 120_000)))
        assertFalse(CaptureLimits.FULL.allowsAnalysis(ClipRange(99_999, 120_000)))
        assertFalse(CaptureLimits.FULL.allowsAnalysis(ClipRange(90_000, 120_000)))
        // A retained long clip can still be edited to a free ten-second range
        // after access expires; the media import gate handles capture duration.
        assertTrue(CaptureLimits.FREE.allowsAnalysis(ClipRange(110_000, 120_000)))
        assertTrue(runCatching { ClipRange(90_000, 120_000) }.isSuccess)
        assertTrue(runCatching { ClipRange(89_999, 120_000) }.isFailure)
    }

    @Test fun actualTwoMinuteVideoIsRejectedForFreeAndReadableWithFullLimits() {
        val file = fixture()
        try {
            val uri = Uri.fromFile(file)
            val failure = runCatching { CaptureFiles.inspect(context, uri, limits = CaptureLimits.FREE) }
                .exceptionOrNull()
            assertTrue("Two-minute metadata must fail the free import limit", failure is IllegalArgumentException)
            assertTrue(failure!!.message.orEmpty().contains("1분"))
            val clip = CaptureFiles.inspect(context, uri, limits = CaptureLimits.FULL)
            assertTrue("Actual duration: ${clip.durationMs}", clip.durationMs in 119_000..120_500)
            assertTrue(clip.hasVideo)
            assertEquals(uri, clip.uri)
            assertTrue("Rejecting a free import must not delete the source", file.exists())
        } finally { file.delete() }
    }

    @Test fun lastTwentySecondsDecodeAtFullLengthAndReachTheActualSoundModel() = runBlocking {
        val file = fixture()
        try {
            val uri = Uri.fromFile(file)
            val early = VideoAudioDecoder.decode(context, uri, 0, 10_000)!!
            val late = VideoAudioDecoder.decode(context, uri, 100_000, 120_000)!!
            try {
                assertTrue("Early PCM length: ${early.size}", early.size in 159_000..160_000)
                assertTrue("Fixture beginning must remain silent: ${rms(early)}", rms(early) < .001)
                assertTrue("Late PCM length: ${late.size}", late.size in 319_000..320_000)
                assertTrue(late.all { it.isFinite() && abs(it) <= 1.001f })
                assertTone(late)
                val result = SoundAnalyzer.analyzeDetailed(context, late)
                assertNull("Twenty-second PCM must not fail model/format checks: ${result.problem}", result.problem)
                assertTrue("The real model must process the later part too", result.frames.any { it.startMs >= 15_000 })
            } finally { early.fill(0f); late.fill(0f) }
        } finally { file.delete() }
    }

    @Test fun internalThirtySecondComparisonProcessesTheLastHalfMinuteWithoutTruncation() = runBlocking {
        val file = fixture()
        try {
            val pcm = VideoAudioDecoder.decode(context, Uri.fromFile(file), 90_000, 120_000)!!
            try {
                assertTrue("Thirty-second PCM length: ${pcm.size}", pcm.size in 479_000..480_000)
                assertTrue(pcm.all { it.isFinite() && abs(it) <= 1.001f })
                assertTrue("Range must start with the fixture's silence", rms(pcm, 0, 8 * 16_000) < .001)
                assertTone(pcm, 20 * 16_000, pcm.size)
                val result = SoundAnalyzer.analyzeDetailed(context, pcm)
                assertNull("Internal thirty-second input must be supported: ${result.problem}", result.problem)
                assertTrue("No hidden twenty-second model truncation", result.frames.any { it.startMs >= 25_000 })
            } finally { pcm.fill(0f) }
        } finally { file.delete() }
    }
}
