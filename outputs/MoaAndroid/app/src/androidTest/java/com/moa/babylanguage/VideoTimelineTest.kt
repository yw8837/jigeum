package com.moa.babylanguage

import android.graphics.Color
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class VideoTimelineTest {
    private fun fixture(name: String): File {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        return File(instrumentation.targetContext.cacheDir, "timeline-${UUID.randomUUID()}.mp4").also { out ->
            instrumentation.context.assets.open(name).use { input -> out.outputStream().use { input.copyTo(it) } }
        }
    }

    @Test fun entireTwentySecondsIncludesBeginningTransitionAndTail() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = fixture("temporal-red-blue-20.mp4")
        val times = mutableListOf<Long>()
        val colors = mutableListOf<Int>()
        try {
            withContext(Dispatchers.Default) {
                VideoFrameDecoder.decode(context, Uri.fromFile(file), 0, 20_000) { frame ->
                    times += frame.presentationOffsetMs
                    colors += frame.bitmap.getPixel(frame.bitmap.width / 2, frame.bitmap.height / 2)
                }
            }
            assertEquals(40, times.size)
            assertEquals(0L, times.first())
            assertEquals(19_500L, times.last())
            assertTrue(times.zipWithNext().all { (a, b) -> b - a == 500L })
            assertTrue(Color.red(colors.first()) > 200 && Color.blue(colors.first()) < 40)
            assertTrue(Color.blue(colors[20]) > 200 && Color.red(colors[20]) < 40)
            assertTrue(Color.blue(colors.last()) > 200)
        } finally { file.delete() }
    }

    @Test fun nonzeroSelectionUsesDecodedPtsAndDoesNotPullEarlierFrames() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = fixture("temporal-red-blue-20.mp4")
        val times = mutableListOf<Long>()
        try {
            withContext(Dispatchers.Default) {
                VideoFrameDecoder.decode(context, Uri.fromFile(file), 250, 1750) { times += it.presentationOffsetMs }
            }
            assertEquals(listOf(50L, 550L, 1050L), times)
        } finally { file.delete() }
    }

    @Test fun cadenceRejectsBackwardsPtsAndNeverDuplicatesSparseFrames() {
        val plan = VideoFrameCadence(1_000_000, 3_000_000)
        assertNull(plan.select(900_000))
        assertEquals(0L, plan.select(1_100_000))
        assertNull(plan.select(1_100_000))
        assertEquals(500L, plan.select(2_100_000))
        assertNull(plan.select(2_200_000))
        try { plan.select(2_000_000); fail("Backwards PTS cannot be treated as temporal evidence") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun actualFaceVideoModeObservesFullSelectedRange() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = fixture("temporal-red-blue-20.mp4")
        try {
            val result = withContext(Dispatchers.Default) { inspectVideo(context, Uri.fromFile(file), 0, 20_000) }
            assertEquals(40, result.analyzedFrames)
            assertEquals(0L, result.frames.first().presentationOffsetMs)
            assertEquals(19_500L, result.frames.last().presentationOffsetMs)
            assertEquals(0, result.faceFrames)
            assertEquals(VisualEvidenceStatus.COMPLETE, result.visualEvidence().status)
        } finally { file.delete() }
    }

    @Test fun temporalMouthChangesDoNotBridgeMissingFaceOrLongGap() {
        fun frame(at: Long, mouth: Float, count: Int = 1) = VisualFrameObservation(
            at, count, count == 1, mouthOpening = mouth, presentationOffsetMs = at)
        val complete = VisualTimeline.summarize(listOf(frame(0, .04f), frame(500, .3f),
            frame(1000, .04f), frame(1500, .3f)), 2000)
        assertTrue(complete.coversSelection)
        assertEquals(3, complete.mouthTransitions)
        assertEquals(listOf(true, false, true), complete.mouthChanges.map { it.opening })
        assertEquals(listOf(0L, 500L, 1000L), complete.mouthChanges.map { it.startMs })
        assertEquals(listOf(2, 1, 1), complete.phases.map { it.samples })
        val missing = VisualTimeline.summarize(listOf(frame(0, .04f), frame(500, .3f, 0),
            frame(1000, .3f), frame(1500, .04f, 2)), 2000)
        assertEquals(0, missing.mouthTransitions)
        val gap = VisualTimeline.summarize(listOf(frame(0, .04f), frame(1500, .3f)), 2000)
        assertFalse(gap.coversSelection)
        assertEquals(0, gap.mouthTransitions)
        assertFalse(VisualTimeline.summarize(listOf(frame(500, .3f), frame(0, .04f)), 2000).ordered)
    }
    @Test fun rotationIsAppliedOnceAfterDecoderCrop() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = fixture("temporal-rotation-2.mp4")
        var count = 0
        try {
            withContext(Dispatchers.Default) {
                VideoFrameDecoder.decode(context, Uri.fromFile(file), 0, 2_000) { frame ->
                    assertEquals(32, frame.bitmap.width)
                    assertEquals(64, frame.bitmap.height)
                    val top = frame.bitmap.getPixel(16, 8)
                    val bottom = frame.bitmap.getPixel(16, 56)
                    assertTrue("Display matrix 90 degrees puts the blue right half at the top", Color.blue(top) > 200)
                    assertTrue(Color.red(bottom) > 200)
                    assertNotEquals(top, bottom)
                    count++
                }
            }
            assertEquals(4, count)
        } finally { file.delete() }
    }

    @Test fun cancellationReleasesBorrowedBitmapAndDecoderCanRunAgain() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = fixture("temporal-red-blue-20.mp4")
        var borrowed: android.graphics.Bitmap? = null
        try {
            try {
                withContext(Dispatchers.Default) {
                    VideoFrameDecoder.decode(context, Uri.fromFile(file), 0, 20_000) {
                        borrowed = it.bitmap
                        throw kotlinx.coroutines.CancellationException("intentional test cancellation")
                    }
                }
                fail("Cancellation must propagate")
            } catch (_: kotlinx.coroutines.CancellationException) { }
            assertTrue(borrowed!!.isRecycled)
            var frames = 0
            withContext(Dispatchers.Default) {
                VideoFrameDecoder.decode(context, Uri.fromFile(file), 0, 1000) { frames++ }
            }
            assertEquals(2, frames)
        } finally { file.delete() }
    }

    @Test fun temporalEvidenceNeedsActualChangesToAffectBabbleExpression() {
        val range = ClipRange(0, 2000)
        val sound = SoundAnalysis(emptyList(), frames = (0..4).map {
            SoundFrame(it * 400L, listOf(SoundCue("옹알이 후보", .95f)))
        })
        fun evidence(openings: List<Float>): VisualEvidence {
            val frames = openings.mapIndexed { index, opening ->
                VisualFrameObservation(index * 500L, 1, true, opening,
                    presentationOffsetMs = index * 500L)
            }
            return VisualEvidence(VisualEvidenceStatus.COMPLETE, "시간순 관찰", frames = frames,
                timeline = VisualTimeline.summarize(frames, 2000))
        }
        val static = EvidenceInterpreter.interpret(sound, AcousticShape(.8f, false, 3), range,
            evidence(listOf(.3f, .3f, .3f, .3f)))
        assertEquals("AUDIO", static.interpretation!!.basis)
        val moving = EvidenceInterpreter.interpret(sound, AcousticShape(.8f, false, 3), range,
            evidence(listOf(.04f, .3f, .04f, .3f)))
        assertEquals("AUDIO_VISUAL", moving.interpretation!!.basis)
    }

    @Test fun decoderCropExcludesCodecPadding() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = fixture("temporal-crop-1.mp4")
        var count = 0
        try {
            withContext(Dispatchers.Default) {
                VideoFrameDecoder.decode(context, Uri.fromFile(file), 0, 1000) { frame ->
                    assertEquals(62, frame.bitmap.width)
                    assertEquals(34, frame.bitmap.height)
                    assertTrue(Color.red(frame.bitmap.getPixel(4, 17)) > 200)
                    assertTrue(Color.blue(frame.bitmap.getPixel(58, 17)) > 200)
                    count++
                }
            }
            assertEquals(2, count)
        } finally { file.delete() }
    }

}
