package com.moa.babylanguage

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

@RunWith(AndroidJUnit4::class)
class VideoAudioTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    private fun fixture(name: String): File = File(context.cacheDir, "qa-$name").also { out ->
        instrumentation.context.assets.open(name).use { source -> out.outputStream().use { source.copyTo(it) } }
    }

    private fun checkTone(name: String, hz: Int, expectedSamples: IntRange) = runBlocking {
        val file = fixture(name)
        try {
            val pcm = VideoAudioDecoder.decode(context, Uri.fromFile(file))!!
            try {
                assertTrue("sample count ${pcm.size}", pcm.size in expectedSamples)
                assertTrue(pcm.all { it.isFinite() && abs(it) <= 1.001f })
                val rms = sqrt(pcm.map { it.toDouble() * it }.average())
                assertTrue("decoded tone must be audible: $rms", rms in .03.. .14)
                val crossings = (1 until pcm.size).count { pcm[it-1] < 0 && pcm[it] >= 0 }
                val measuredHz = crossings * 16000.0 / pcm.size
                assertEquals("resampled frequency", hz.toDouble(), measuredHz, 8.0)
            } finally { pcm.fill(0f) }
        } finally { file.delete() }
    }

    @Test fun stereo48kIsMono16kAndCappedAtTenSeconds() {
        checkTone("tone48-stereo.mp4", 997, 159_900..160_000)
    }

    @Test fun mono44kKeepsDurationAndPitch() {
        checkTone("tone44-mono.mp4", 613, 47_900..48_500)
    }

    @Test fun absentTrackAndSilentTrackAreDifferent() = runBlocking {
        val missing = fixture("no-audio.mp4")
        val silence = fixture("silence.mp4")
        try {
            assertEquals(VideoAudioAnalysis.Status.NO_TRACK, VideoAudioDecoder.analyze(context, Uri.fromFile(missing)).status)
            assertEquals(VideoAudioAnalysis.Status.SILENT, VideoAudioDecoder.analyze(context, Uri.fromFile(silence)).status)
        } finally { missing.delete(); silence.delete() }
    }

    @Test fun malformedFileFallsBackAndNextDecodeStillWorks() = runBlocking {
        val bad = File(context.cacheDir, "qa-broken.mp4").apply { writeText("not a video") }
        try {
            assertEquals(VideoAudioAnalysis.Status.FAILED, VideoAudioDecoder.analyze(context, Uri.fromFile(bad)).status)
        } finally { bad.delete() }
        checkTone("tone44-mono.mp4", 613, 47_900..48_500)
    }

    @Test fun cancellationReleasesCodecForNextRun() = runBlocking {
        val file = fixture("tone48-stereo.mp4")
        try {
            repeat(3) {
                try {
                    withTimeout(20) { withContext(Dispatchers.Default) {
                        VideoAudioDecoder.decode(context, Uri.fromFile(file))?.fill(0f)
                    } }
                    fail("Expected cancellation")
                } catch (_: TimeoutCancellationException) { }
            }
        } finally { file.delete() }
        checkTone("tone44-mono.mp4", 613, 47_900..48_500)
    }

    @Test fun bundledModelAcceptsDecodedSyntheticVoice() = runBlocking {
        val file = fixture("generated-fuss.mp4")
        try {
            val result = withContext(Dispatchers.Default) { VideoAudioDecoder.analyze(context, Uri.fromFile(file)) }
            assertTrue("model/decode failure: ${result.status}",
                result.status == VideoAudioAnalysis.Status.CUES || result.status == VideoAudioAnalysis.Status.NO_CUES)
            android.util.Log.i("JigeumAudioTest", "Synthetic voice candidates: ${result.cues}")
            Unit
        } finally { file.delete() }
    }

    @Test fun veryShortCaptureShowsActionableProblem() = runBlocking {
        val result = SoundAnalyzer.analyzeDetailed(context, FloatArray(2000))
        assertTrue(result.cues.isEmpty())
        assertNotNull(result.problem)
    }

    @Test fun selectedRangeDecodesLaterSoundInsteadOfFirstSeconds() = runBlocking {
        val file = fixture("range-silence-tone.mp4")
        try {
            val first = VideoAudioDecoder.decode(context, Uri.fromFile(file), 0, 3_000)!!
            val later = VideoAudioDecoder.decode(context, Uri.fromFile(file), 8_000, 11_000)!!
            try {
                assertTrue(first.size in 47_900..48_000)
                assertTrue(later.size in 47_900..48_000)
                val quiet = sqrt(first.sumOf { it.toDouble() * it } / first.size)
                val tone = sqrt(later.sumOf { it.toDouble() * it } / later.size)
                assertTrue("Beginning must be silent: $quiet", quiet < .001)
                assertTrue("Selected later segment must contain tone: $tone", tone > .08)
                val crossings = (1 until later.size).count { later[it-1] < 0 && later[it] >= 0 }
                assertEquals(997.0, crossings * 16_000.0 / later.size, 8.0)
            } finally { first.fill(0f); later.fill(0f) }
        } finally { file.delete() }
    }

    @Test fun realModelCannotTurnSilenceOrToneIntoBabyPhrase() = runBlocking {
        for (name in listOf("silence.mp4", "tone44-mono.mp4", "no-audio.mp4")) {
            val file = fixture(name)
            try {
                val result = DeviceClipAnalyzer.analyze(context, Uri.fromFile(file), ClipRange(0, 2_000), false)
                assertNull("$name must not produce a baby phrase", result.interpretation)
                assertNotNull(result.problem)
            } finally { file.delete() }
        }
    }
}
