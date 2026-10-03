package com.moa.babylanguage

import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class VideoColorConversionTest {
    private fun format(range: Int, standard: Int) = MediaFormat().apply {
        setInteger(MediaFormat.KEY_COLOR_RANGE, range)
        setInteger(MediaFormat.KEY_COLOR_STANDARD, standard)
    }

    private fun assertRgb(expected: List<Int>, pixel: Int, tolerance: Int = 0) {
        val actual = listOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
        expected.zip(actual).forEach { (want, got) ->
            assertTrue("Expected $expected, got $actual", kotlin.math.abs(want - got) <= tolerance)
        }
    }

    @Test fun fullRangeBt709PreservesDarkNeutralLuma() {
        val color = VideoYuvColor.fromFormats(format(MediaFormat.COLOR_RANGE_FULL,
            MediaFormat.COLOR_STANDARD_BT709), MediaFormat())
        assertRgb(listOf(18, 18, 18), color.argb(18, 128, 128))
        assertRgb(listOf(0, 0, 0), color.argb(0, 128, 128))
        assertRgb(listOf(255, 255, 255), color.argb(255, 128, 128))
    }

    @Test fun limitedRangePreservesVideoBlackAndWhite() {
        val color = VideoYuvColor.fromFormats(format(MediaFormat.COLOR_RANGE_LIMITED,
            MediaFormat.COLOR_STANDARD_BT709), MediaFormat())
        assertRgb(listOf(0, 0, 0), color.argb(16, 128, 128))
        assertRgb(listOf(255, 255, 255), color.argb(235, 128, 128))
    }

    @Test fun declaredMatrixChangesChromaticPixels() {
        // Hand-derived R'G'B' for Y=100,Cb=90,Cr=180 using the documented Kr/Kb.
        val standards = listOf(MediaFormat.COLOR_STANDARD_BT601_NTSC,
            MediaFormat.COLOR_STANDARD_BT601_PAL, MediaFormat.COLOR_STANDARD_BT709,
            MediaFormat.COLOR_STANDARD_BT2020)
        val expected = listOf(listOf(173, 76, 33), listOf(173, 76, 33),
            listOf(182, 83, 29), listOf(177, 77, 29))
        standards.zip(expected).forEach { (standard, rgb) ->
            assertRgb(rgb, VideoYuvColor.fromFormats(format(MediaFormat.COLOR_RANGE_FULL,
                standard), MediaFormat()).argb(100, 90, 180))
        }
    }

    @Test fun outputBufferMetadataWinsAndUnknownValuesUseExplicitFallback() {
        val input = format(MediaFormat.COLOR_RANGE_FULL, MediaFormat.COLOR_STANDARD_BT709)
        val output = format(MediaFormat.COLOR_RANGE_LIMITED, MediaFormat.COLOR_STANDARD_BT601_NTSC)
        assertRgb(listOf(2, 2, 2), VideoYuvColor.fromFormats(output, input).argb(18, 128, 128))
        assertRgb(listOf(18, 18, 18), VideoYuvColor.fromFormats(format(0, 0), input).argb(18, 128, 128))
        assertRgb(listOf(182, 83, 29), VideoYuvColor.fromFormats(format(999, 999), input).argb(100, 90, 180))
        assertRgb(listOf(2, 2, 2), VideoYuvColor.fromFormats(format(999, 999), MediaFormat()).argb(18, 128, 128))
        val partialOutput = format(MediaFormat.COLOR_RANGE_FULL, 0)
        assertRgb(listOf(182, 83, 29), VideoYuvColor.fromFormats(partialOutput,
            format(MediaFormat.COLOR_RANGE_LIMITED, MediaFormat.COLOR_STANDARD_BT709)).argb(100, 90, 180))
    }

    @Test fun decodedFullRangeBt709FixtureKeepsDarkGrayAndChromaticColor() = runBlocking {
        // Own synthetic fixture: 64x64, 10fps/1s, lossless libx264 qp=0 from raw
        // yuvj420p. Left Y/Cb/Cr=18/128/128, right=100/90/180; VUI full +
        // BT.709 matrix, primaries and transfer. Android COLOR_STANDARD combines
        // matrix/primaries; a matrix-only tag is not a declared BT.709 standard.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = File(context.cacheDir, "color-${UUID.randomUUID()}.mp4")
        instrumentation.context.assets.open("temporal-bt709-full-1.mp4").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        try {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, Uri.fromFile(file), null)
                val declared = extractor.getTrackFormat(0)
                assertEquals(declared.toString(), MediaFormat.COLOR_STANDARD_BT709,
                    declared.getInteger(MediaFormat.KEY_COLOR_STANDARD))
                assertEquals(declared.toString(), MediaFormat.COLOR_RANGE_FULL,
                    declared.getInteger(MediaFormat.KEY_COLOR_RANGE))
            } finally { extractor.release() }
            var frames = 0
            withContext(Dispatchers.Default) {
                VideoFrameDecoder.decode(context, Uri.fromFile(file), 0, 1000) { frame ->
                    assertRgb(listOf(18, 18, 18), frame.bitmap.getPixel(16, 32), tolerance = 3)
                    assertRgb(listOf(182, 83, 29), frame.bitmap.getPixel(48, 32), tolerance = 3)
                    frames++
                }
            }
            assertEquals(2, frames)
        } finally { file.delete() }
    }
}
