package com.moa.babylanguage

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Geometric/blendshape validity only. These fixtures do not validate infant emotions. */
@RunWith(AndroidJUnit4::class)
class VisualFeatures059Test {
    private fun face(): List<NormalizedLandmark> = MutableList(478) {
        NormalizedLandmark.create(.5f, .5f, 0f)
    }.apply {
        this[13] = NormalizedLandmark.create(.5f, .45f, 0f)
        this[14] = NormalizedLandmark.create(.5f, .55f, 0f)
        this[61] = NormalizedLandmark.create(.3f, .5f, 0f)
        this[291] = NormalizedLandmark.create(.7f, .5f, 0f)
    }

    private fun category(name: String, score: Float) = Category.create(score, -1, name, "")

    private fun blends() = listOf(
        category("eyeBlinkLeft", .8f), category("eyeBlinkRight", .6f),
        category("mouthSmileLeft", .9f), category("mouthSmileRight", .7f))

    @Test fun singleFaceCarriesMeasuredFeaturesAndRequestedOffsetWithoutEmotionClaim() {
        val observed = VisualFrameObservation.measure(2_500, listOf(face()), listOf(blends()), 400, 800)
        assertTrue(observed.valid)
        assertEquals(1, observed.faceCount)
        assertEquals(2_500L, observed.requestedOffsetMs)
        assertEquals(.5f, observed.mouthOpening!!, .0001f)
        assertEquals(.7f, observed.eyeClosure!!, .0001f)
        assertEquals(.8f, observed.mouthCornerLift!!, .0001f)
    }

    @Test fun mouthRatioUsesImageAspectInsteadOfNormalizedCoordinatesAlone() {
        val portrait = VisualFrameObservation.measure(1_000, listOf(face()), emptyList(), 400, 800)
        val square = VisualFrameObservation.measure(1_000, listOf(face()), emptyList(), 400, 400)
        assertEquals(.5f, portrait.mouthOpening!!, .0001f)
        assertEquals(.25f, square.mouthOpening!!, .0001f)
    }

    @Test fun multipleFacesCannotBorrowFirstFacesMouthOrBlendshapes() {
        val observed = VisualFrameObservation.measure(2_500, listOf(face(), face()),
            listOf(blends(), blends()), 512, 512)
        assertEquals(2, observed.faceCount)
        assertFalse(observed.valid)
        assertNull(observed.mouthOpening)
        assertNull(observed.eyeClosure)
        assertNull(observed.mouthCornerLift)
    }

    @Test fun missingFaceNeverProvidesFeaturesEvenWithStrayBlendshapes() {
        val observed = VisualFrameObservation.measure(1_000, emptyList(), listOf(blends()), 512, 512)
        assertEquals(0, observed.faceCount)
        assertFalse(observed.valid)
        assertNull(observed.mouthOpening)
        assertNull(observed.eyeClosure)
        assertNull(observed.mouthCornerLift)
    }

    @Test fun missingBlendshapesStayUnknownInsteadOfBecomingOpenEyesOrNoSmile() {
        val observed = VisualFrameObservation.measure(1_000, listOf(face()), emptyList(), 512, 512)
        assertTrue(observed.valid)
        assertNotNull(observed.mouthOpening)
        assertNull(observed.eyeClosure)
        assertNull(observed.mouthCornerLift)
    }

    @Test fun oneSidedAndInvalidCoefficientsAreNotPairedOrClampedIntoEvidence() {
        val categories = listOf(category("eyeBlinkLeft", .9f),
            category("mouthSmileLeft", 1.2f), category("mouthSmileRight", .8f))
        val observed = VisualFrameObservation.measure(1_000, listOf(face()), listOf(categories), 512, 512)
        assertTrue(observed.valid)
        assertNull(observed.eyeClosure)
        assertNull(observed.mouthCornerLift)
    }

    @Test fun duplicateAndNonfiniteCoefficientsStayUnknown() {
        val categories = listOf(category("eyeBlinkLeft", .9f), category("eyeBlinkLeft", .5f),
            category("eyeBlinkRight", .8f), category("mouthSmileLeft", Float.NaN),
            category("mouthSmileRight", .8f))
        val observed = VisualFrameObservation.measure(1_000, listOf(face()), listOf(categories), 512, 512)
        assertNull(observed.eyeClosure)
        assertNull(observed.mouthCornerLift)
    }

    @Test fun croppedCorruptAndCollapsedMouthLandmarksCannotContribute() {
        val invalidFaces = listOf(
            face().toMutableList().apply { this[13] = NormalizedLandmark.create(-.1f, .5f, 0f) },
            face().toMutableList().apply { this[14] = NormalizedLandmark.create(.5f, Float.NaN, 0f) },
            face().toMutableList().apply { this[291] = this[61] },
            face().take(200))
        for (invalid in invalidFaces) {
            val observed = VisualFrameObservation.measure(1_000, listOf(invalid), listOf(blends()), 512, 512)
            assertFalse(observed.valid)
            assertNull(observed.mouthOpening)
            assertNull(observed.eyeClosure)
            assertNull(observed.mouthCornerLift)
        }
    }

    @Test fun structuredFramesSurviveSummaryAndMultipleFaceExclusionIsExplained() {
        val frames = listOf(
            VisualFrameObservation.measure(1_000, listOf(face()), listOf(blends()), 512, 512),
            VisualFrameObservation.measure(2_000, listOf(face(), face()), listOf(blends(), blends()), 512, 512))
        val evidence = VideoFrameSummary(3, 3, 2, 1, 2, true, true, frames = frames).visualEvidence()
        assertEquals(VisualEvidenceStatus.PARTIAL, evidence.status)
        assertEquals(frames, evidence.frames)
        assertTrue(evidence.evidence.contains("얼굴이 여럿"))
        assertTrue(evidence.evidence.contains("단서에서 제외"))
        assertNotNull(evidence.notice)
    }

    @Test fun failureAndNoFaceMessagesDoNotInventAnAudioPhraseThatMayNotExist() {
        val failure = VisualEvidence.failed()
        val noFace = VideoFrameSummary(3, 3, 0, 0, 3, true, true).visualEvidence()
        for (evidence in listOf(failure, noFace)) {
            assertFalse(evidence.evidence.contains("한마디는"))
            assertFalse(evidence.evidence.contains("만들었어요"))
        }
        assertTrue(failure.frames.isEmpty())
    }

    @Test fun actualBundledModelSupportsMultiFaceAndBlendshapeOptionsOnSelectedRange() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val fixture = File(context.cacheDir, "visual-059-${UUID.randomUUID()}.mp4")
        try {
            instrumentation.context.assets.open("range-silence-tone.mp4").use { input ->
                fixture.outputStream().use { input.copyTo(it) }
            }
            val summary = withContext(Dispatchers.Default) {
                inspectVideo(context, Uri.fromFile(fixture), 8_000, 11_000)
            }
            assertTrue("Model initialization must work with both new options", summary.modelAvailable)
            assertEquals(3, summary.analyzedFrames)
            assertEquals(3, summary.frames.size)
            assertEquals(listOf(750L, 1_500L, 2_250L), summary.frames.map { it.requestedOffsetMs })
            assertTrue("Color/tone fixture must not invent faces or features", summary.frames.all {
                it.faceCount == 0 && !it.valid && it.mouthOpening == null &&
                    it.eyeClosure == null && it.mouthCornerLift == null
            })
            assertEquals(VisualEvidenceStatus.COMPLETE, summary.visualEvidence().status)
        } finally { fixture.delete() }
    }
}
