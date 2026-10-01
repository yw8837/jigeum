package com.moa.babylanguage

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ClipRangeEditorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun endpointsClampAtMinimumAndCurrentPlanMaximum() {
        val initial = ClipRange(30_000, 40_000)
        for (maximum in listOf(10_000L, 15_000L, 20_000L, 30_000L)) {
            assertEquals(ClipRange(40_000 - maximum, 40_000), ClipRangeEdits.start(initial, 0, 120_000, maximum))
            assertEquals(ClipRange(39_000, 40_000), ClipRangeEdits.start(initial, 80_000, 120_000, maximum))
            assertEquals(ClipRange(30_000, 30_000 + maximum), ClipRangeEdits.end(initial, 120_000, 120_000, maximum))
            assertEquals(ClipRange(30_000, 31_000), ClipRangeEdits.end(initial, 0, 120_000, maximum))
        }
    }

    @Test fun wholeWindowTranslationPreservesLengthAtBothEdges() {
        for (length in listOf(1_000L, 15_000L, 30_000L)) {
            val initial = ClipRange(40_000, 40_000 + length)
            assertEquals(ClipRange(0, length), ClipRangeEdits.move(initial, Long.MIN_VALUE, 120_500, 30_000))
            assertEquals(ClipRange(120_500 - length, 120_500), ClipRangeEdits.move(initial, Long.MAX_VALUE, 120_500, 30_000))
            assertEquals(ClipRange(40_500, 40_500 + length), ClipRangeEdits.move(initial, 500, 120_500, 30_000))
        }
    }

    @Test fun oneSecondClipAndPlanReductionStayValid() {
        val shortest = ClipRange(0, 1_000)
        assertEquals(shortest, ClipRangeEdits.start(shortest, 800, 1_000, 15_000))
        assertEquals(shortest, ClipRangeEdits.end(shortest, 100, 1_000, 15_000))
        assertEquals(shortest, ClipRangeEdits.move(shortest, 50_000, 1_000, 15_000))
        assertEquals(ClipRange(90_000, 105_000), ClipRangeEdits.fit(ClipRange(90_000, 120_000), 120_000, 15_000))
        assertEquals(ClipRange(45_500, 60_500), ClipRangeEdits.fit(ClipRange(60_000, 90_000), 60_500, 15_000))
    }

    @Test fun selectionMovesAndResizesAcrossTheEntireTwoMinuteSource() {
        val source = 120_500L
        for (maximum in listOf(15_000L, 30_000L)) {
            for (at in 0L..120_000L step 500) {
                val initial = ClipRangeEdits.move(ClipRange(0, maximum), at, source, maximum)
                for (next in listOf(
                    ClipRangeEdits.start(initial, at - 5_000, source, maximum),
                    ClipRangeEdits.start(initial, at + 35_000, source, maximum),
                    ClipRangeEdits.end(initial, at - 5_000, source, maximum),
                    ClipRangeEdits.end(initial, at + 35_000, source, maximum)
                )) {
                    assertTrue(next.startMs >= 0)
                    assertTrue(next.endMs <= source)
                    assertTrue(next.durationMs in 1_000..maximum)
                }
            }
        }
    }

    @Test fun talkBackEndpointsAndFineAdjustmentUseSameLimits() {
        val selected = mutableStateOf(ClipRange(10_000, 20_000))
        compose.setContent { MaterialTheme {
            ClipRangeEditor(missingClip(60_000), selected.value, 15_000, true) { selected.value = it }
        } }
        compose.onNodeWithTag("clip-range-end").performSemanticsAction(SemanticsActions.SetProgress) { it(60_000f) }
        compose.runOnIdle { assertEquals(ClipRange(10_000, 25_000), selected.value) }
        compose.onNodeWithTag("clip-range-start").performSemanticsAction(SemanticsActions.SetProgress) { it(24_000f) }
        compose.runOnIdle { assertEquals(ClipRange(24_000, 25_000), selected.value) }
        compose.onNodeWithTag("clip-fine-toggle").performClick()
        compose.onNodeWithTag("clip-start-plus").assertIsNotEnabled()
        compose.onNodeWithTag("clip-start-minus").performClick()
        compose.runOnIdle { assertEquals(ClipRange(23_500, 25_000), selected.value) }
    }

    @Test fun wholeWindowDragKeepsDurationWhileShiftingInTime() {
        val selected = mutableStateOf(ClipRange(10_000, 25_000))
        compose.setContent { MaterialTheme {
            ClipRangeEditor(missingClip(60_000), selected.value, 15_000, true) { selected.value = it }
        } }
        compose.onNodeWithTag("clip-range-move").performTouchInput {
            swipe(Offset(width * .35f, height / 2f), Offset(width * .7f, height / 2f), 400)
        }
        compose.runOnIdle {
            assertTrue(selected.value.startMs > 10_000)
            assertEquals(15_000L, selected.value.durationMs)
            assertTrue(selected.value.endMs <= 60_000)
        }
    }

    @Test fun timelineCenterDragMovesBothEnds() {
        val selected = mutableStateOf(ClipRange(10_000, 25_000))
        compose.setContent { MaterialTheme {
            ClipRangeEditor(missingClip(60_000), selected.value, 15_000, true) { selected.value = it }
        } }
        compose.onNodeWithTag("clip-range").performTouchInput {
            swipe(Offset(width * 17.5f / 60f, height / 2f), Offset(width * 27.5f / 60f, height / 2f), 400)
        }
        compose.runOnIdle {
            assertTrue(selected.value.startMs > 10_000)
            assertEquals(15_000L, selected.value.durationMs)
        }
    }

    @Test fun timelineHandleDragResizesOnlyTheSelectedEnd() {
        val selected = mutableStateOf(ClipRange(10_000, 20_000))
        compose.setContent { MaterialTheme {
            ClipRangeEditor(missingClip(60_000), selected.value, 15_000, true) { selected.value = it }
        } }
        compose.onNodeWithTag("clip-range").performTouchInput {
            swipe(Offset(width / 6f, height / 2f), Offset(width / 6f + width * .05f, height / 2f), 400)
        }
        compose.runOnIdle {
            assertTrue(selected.value.startMs > 10_000)
            assertEquals(20_000L, selected.value.endMs)
            assertTrue(selected.value.durationMs >= 1_000)
        }
    }

    @Test fun disabledEditorIgnoresDragAndAccessibilityEdits() {
        val selected = mutableStateOf(ClipRange(70_000, 100_000))
        compose.setContent { MaterialTheme {
            ClipRangeEditor(missingClip(120_000), selected.value, 30_000, false) { selected.value = it }
        } }
        compose.onNodeWithTag("clip-range-start").assertIsNotEnabled()
        compose.onNodeWithTag("clip-range-start").performSemanticsAction(SemanticsActions.SetProgress) { assertFalse(it(85_000f)) }
        compose.onNodeWithTag("clip-range-move").performTouchInput {
            swipe(Offset(width * .2f, height / 2f), Offset(width * .8f, height / 2f), 400)
        }
        compose.runOnIdle { assertEquals(ClipRange(70_000, 100_000), selected.value) }
    }

    @Test fun zoomChangesTheViewWithoutChangingTheSelection() {
        val selected = mutableStateOf(ClipRange(85_000, 115_000))
        compose.setContent { MaterialTheme {
            ClipRangeEditor(missingClip(120_000), selected.value, 30_000, true) { selected.value = it }
        } }
        compose.onNodeWithTag("clip-zoom-4").performClick()
        compose.onNodeWithTag("clip-timeline-position").assertExists()
        compose.onNodeWithTag("clip-timeline-position").performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.runOnIdle { assertEquals(ClipRange(85_000, 115_000), selected.value) }
    }

    @Test fun previewUsesActualQuietAndLoudPartsOfOriginalAudio() = runBlocking {
        val file = File(compose.activity.cacheDir, "qa-range-editor-preview.mp4")
        InstrumentationRegistry.getInstrumentation().context.assets.open("range-silence-tone.mp4").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        try {
            val clip = CaptureFiles.inspect(compose.activity, Uri.fromFile(file))
            val preview = TimelinePreviewLoader.load(compose.activity, clip)
            assertNotNull(preview.waveform)
            val wave = preview.waveform!!
            assertTrue("First two seconds are silent", wave.take(20).maxOrNull()!! < .001f)
            assertTrue("Later tone must appear at its real time", wave.slice(80 until 110).average() > .08)
            if (android.os.Build.VERSION.SDK_INT >= 27) {
                assertEquals(8, preview.thumbnails.size)
                assertTrue(preview.thumbnails.all { it.width <= 160 && it.height <= 120 })
            }
        } finally { file.delete() }
    }

    @Test fun unavailableMediaDoesNotInventAWaveform() = runBlocking {
        val preview = TimelinePreviewLoader.load(compose.activity, missingClip(60_000))
        assertNull(preview.waveform)
        assertTrue(preview.thumbnails.isEmpty())
        assertNotNull(preview.notice)
    }

    private fun missingClip(duration: Long) = LocalClip(Uri.parse("content://com.moa.missing/range-editor-test"), duration, false)
}
