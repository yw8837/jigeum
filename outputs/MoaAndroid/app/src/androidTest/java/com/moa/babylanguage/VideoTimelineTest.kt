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
            assertEquals(100, times.size)
            assertEquals(0L, times.first())
            assertEquals(19_800L, times.last())
            assertTrue(times.zipWithNext().all { (a, b) -> b - a == 200L })
            assertTrue(Color.red(colors.first()) > 200 && Color.blue(colors.first()) < 40)
            assertTrue(Color.blue(colors[50]) > 200 && Color.red(colors[50]) < 40)
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
            assertEquals(listOf(50L, 250L, 450L, 650L, 850L, 1050L, 1250L, 1450L), times)
        } finally { file.delete() }
    }

    @Test fun cadenceRejectsBackwardsPtsAndNeverDuplicatesSparseFrames() {
        val plan = VideoFrameCadence(1_000_000, 3_000_000)
        assertNull(plan.select(900_000))
        assertEquals(0L, plan.select(1_100_000))
        assertNull(plan.select(1_100_000))
        assertEquals(200L, plan.select(2_100_000))
        assertNull(plan.select(2_150_000))
        try { plan.select(2_000_000); fail("Backwards PTS cannot be treated as temporal evidence") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun actualFaceVideoModeObservesFullSelectedRange() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = fixture("temporal-red-blue-20.mp4")
        try {
            val result = withContext(Dispatchers.Default) { inspectVideo(context, Uri.fromFile(file), 0, 20_000) }
            assertEquals(100, result.analyzedFrames)
            assertEquals(0L, result.frames.first().presentationOffsetMs)
            assertEquals(19_800L, result.frames.last().presentationOffsetMs)
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

    @Test fun gradualOpeningAndClosingRetainTheirOrderAndActualPts() {
        val frames = listOf(.04f, .12f, .24f, .12f, .04f, .12f, .24f, .12f, .04f)
            .mapIndexed { index, opening -> VisualFrameObservation(index * 500L, 1, true,
                opening, presentationOffsetMs = index * 500L) }
        val timeline = VisualTimeline.summarize(frames, 4500)
        assertTrue(timeline.coversSelection)
        assertEquals(4, timeline.mouthTransitions)
        assertEquals(listOf(true, false, true, false), timeline.mouthChanges.map { it.opening })
        assertEquals(listOf(0L, 1000L, 2000L, 3000L), timeline.mouthChanges.map { it.startMs })
        assertEquals(listOf(1000L, 2000L, 3000L, 4000L), timeline.mouthChanges.map { it.endMs })
    }

    @Test fun gradualChangeResetsAfterMissingAmbiguousInvalidOrDistantSamples() {
        fun frame(at: Long, opening: Float?, count: Int = 1, valid: Boolean = true) =
            VisualFrameObservation(at, count, valid, opening, presentationOffsetMs = at)
        val breaks = listOf(frame(1000, .12f, count = 0, valid = false),
            frame(1000, .12f, count = 2, valid = false), frame(1000, null),
            frame(1000, Float.NaN), frame(1000, .12f, valid = false))
        for (gap in breaks) {
            val timeline = VisualTimeline.summarize(listOf(frame(0, .04f), frame(500, .12f),
                gap, frame(1500, .12f), frame(2000, .24f)), 2500)
            assertEquals("Do not carry a closed state across an unusable sample", 0, timeline.mouthTransitions)
        }
        val distant = VisualTimeline.summarize(listOf(frame(0, .04f), frame(500, .12f),
            frame(1500, .12f), frame(2000, .24f)), 2500)
        assertFalse(distant.coversSelection)
        assertEquals(0, distant.mouthTransitions)
        val undecided = VisualTimeline.summarize(listOf(frame(0, .12f), frame(500, .24f)), 1000)
        assertEquals("An intermediate first sample does not establish a closed mouth", 0, undecided.mouthTransitions)
    }

    @Test fun gradualMouthMotionContributesToTheExistingBabbleExpression() {
        val range = ClipRange(0, 4500)
        val sound = SoundAnalysis(emptyList(), frames = (0..8).map {
            SoundFrame(it * 500L, listOf(SoundCue("옹알이 후보", .95f)))
        })
        val frames = listOf(.04f, .12f, .24f, .12f, .04f, .12f, .24f, .12f, .04f)
            .mapIndexed { index, opening -> VisualFrameObservation(index * 500L, 1, true,
                opening, presentationOffsetMs = index * 500L) }
        val visual = VisualEvidence(VisualEvidenceStatus.COMPLETE, "시간순 관찰", frames = frames,
            timeline = VisualTimeline.summarize(frames, range.durationMs))
        val result = EvidenceInterpreter.interpret(sound, AcousticShape(.8f, false, 3), range, visual)
        assertEquals("AUDIO_VISUAL", result.interpretation!!.basis)
    }

    @Test fun phaseTimeLabelsUseReadableSeconds() {
        val frames = (0..39).map { index -> VisualFrameObservation(index * 500L, 0, false,
            presentationOffsetMs = index * 500L) }
        val result = VideoFrameSummary(20, 40, 0, 0, 40, false, true, frames = frames,
            selectionDurationMs = 20_000).visualEvidence()
        assertTrue(result.evidence.contains("초반(0~6.7초)"))
        assertTrue(result.evidence.contains("중반(6.7~13.3초)"))
        assertTrue(result.evidence.contains("후반(13.3~20초)"))
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
            assertEquals(10, count)
        } finally { file.delete() }
    }

    @Test fun cancellationReleasesBorrowedBitmapAndDecoderCanRunAgain() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Keep the same H.264/PTS sequence at a phone-supported size. The 64px
        // color fixture can fail hardware configure before cancellation is reached.
        val file = fixture("temporal-red-blue-device-20.mp4")
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
            assertEquals(5, frames)
        } finally { file.delete() }
    }

    @Test fun cancellationFixtureHasSupportedHardwareDimensions() {
        val decoder = android.media.MediaCodec.createDecoderByType("video/avc")
        try {
            val caps = decoder.codecInfo.getCapabilitiesForType("video/avc").videoCapabilities
            android.util.Log.i("JigeumVideoTest", "decoder=${decoder.name}; " +
                "64x64=${caps.isSizeSupported(64, 64)}; 320x320=${caps.isSizeSupported(320, 320)}")
            assertTrue("Cancellation fixture must reach the decoder callback", caps.isSizeSupported(320, 320))
        } finally { decoder.release() }
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
            assertEquals(5, count)
        } finally { file.delete() }
    }

}
