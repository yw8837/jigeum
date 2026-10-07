package com.moa.babylanguage

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/** Synthetic presence/abstention controls, not infant recognition ground truth.
 * At most nine inputs reach native YAMNet after the presence gate is fixed. */
class AudioPresenceRegressionTest {
    private val sampleRate = 16_000
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun quietTone() = FloatArray(2 * sampleRate) {
        (sqrt(2.0) * .006 * sin(2 * PI * 997 * it / sampleRate)).toFloat()
    }

    private fun withRumble(pcm: FloatArray) = FloatArray(pcm.size) {
        pcm[it] + (.004 * sin(2 * PI * 40 * it / sampleRate)).toFloat()
    }

    private fun padded(tone: FloatArray, startSeconds: Int) = FloatArray(20 * sampleRate).also {
        tone.copyInto(it, startSeconds * sampleRate)
    }

    private suspend fun analyzePreservingSource(name: String, pcm: FloatArray): SoundAnalysis {
        val source = pcm.copyOf()
        val audio = SoundAnalyzer.analyzeDetailed(context, pcm)
        assertArrayEquals("$name: analysis must preserve the selected source PCM", source, pcm, 0f)
        val cleaned = AudioCleanup.filter(pcm)
        try {
            for (frame in audio.frames) {
                // Actual native timestamps select the matching original PCM window.
                // Accumulate independently of the product helper and include padding.
                val start = frame.startMs.toInt() * 16
                val end = minOf(start + 15_600, cleaned.size)
                val energy = (start until end).sumOf { cleaned[it].toDouble() * cleaned[it] }
                assertNotNull("$name: measured frame level missing", frame.windowRms)
                assertEquals(sqrt(energy / 15_600), frame.windowRms!!, 1e-10)
            }
        } finally { cleaned.fill(0f) }
        assertNotEquals("$name: a model failure is not a measured abstention", AnalysisIssue.MODEL_FAILURE, audio.issue)
        assertNoInfantApproval(name, audio)
        return audio
    }

    private fun assertNoInfantApproval(name: String, audio: SoundAnalysis) {
        val decision = SoundTimeline.decide(audio)
        assertTrue("$name: synthetic non-infant sound must not approve an infant event", decision.events.isEmpty())
        assertNotNull("$name: synthetic controls must remain withheld", decision.problem)
    }

    private fun assertFullSelectionFrames(name: String, audio: SoundAnalysis, durationMs: Long) {
        assertNull("$name: presence must reach the classifier", audio.problem)
        assertFalse("$name: non-silent input must have classifier frames", audio.frames.isEmpty())
        val starts = audio.frames.map { it.startMs }
        assertEquals("$name: frames start at the selected PCM origin", 0L, starts.first())
        assertTrue("$name: all frame timestamps belong to the full selection", starts.all { it in 0 until durationMs })
        assertTrue("$name: frames retain increasing source-relative times", starts.zipWithNext().all { (a, b) -> a < b })
        assertTrue("$name: trailing silence must remain in classifier input", starts.last() >= durationMs - 1_000)
    }

    @Test fun theSameQuietSoundReachesClassificationAloneOrAnywhereInTwentySeconds() = runBlocking {
        val tone = quietTone()
        val fixtures = listOf("alone-2s" to tone) + listOf(0, 9, 18).map { start ->
            "padded-20s-at-${start}s" to padded(tone, start)
        }
        // Collect all eight results before checking presence so RED reports every padded case.
        val results = listOf(false, true).flatMap { rumble ->
            fixtures.map { (label, pcm) ->
                val name = if (rumble) "$label-with-40Hz-rumble" else label
                val input = if (rumble) withRumble(pcm) else pcm
                Triple(name, input.size * 1_000L / sampleRate, analyzePreservingSource(name, input))
            }
        }
        val missing = results.filter { it.third.issue == AnalysisIssue.SILENCE }.map { it.first }
        assertTrue("Identical .006 RMS sound must not vanish with silence padding: $missing", missing.isEmpty())
        results.forEach { (name, durationMs, audio) -> assertFullSelectionFrames(name, audio, durationMs) }
    }

    @Test fun pureTwentySecondSilenceDcAndLowFrequencyRumbleStaySilent() = runBlocking {
        val silence = FloatArray(20 * sampleRate)
        val fixtures = listOf(
            "silence-20s" to silence,
            "dc-20s" to FloatArray(20 * sampleRate) { .3f },
            "40Hz-rumble-20s" to withRumble(silence)
        )
        for ((name, pcm) in fixtures) {
            val audio = analyzePreservingSource(name, pcm)
            assertEquals("$name: removed noise must not open the model gate", AnalysisIssue.SILENCE, audio.issue)
            assertTrue("$name: silence must have no model frames", audio.frames.isEmpty())
        }
    }

    @Test fun anIsolatedClickCannotAuthorizeAnInfantEvent() = runBlocking {
        val pcm = FloatArray(20 * sampleRate).also { it[10 * sampleRate] = .8f }
        val audio = analyzePreservingSource("isolated-click-20s", pcm)
        assertNoInfantApproval("isolated-click-20s", audio)
    }
}
