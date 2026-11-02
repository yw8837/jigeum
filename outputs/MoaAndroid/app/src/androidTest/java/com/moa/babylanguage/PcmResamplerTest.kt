package com.moa.babylanguage

import android.media.AudioFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

@RunWith(AndroidJUnit4::class)
class PcmResamplerTest {
    private fun tone(rate: Int, hz: Double, seconds: Int = 1) =
        FloatArray(rate * seconds) { (.25 * sin(2 * PI * hz * it / rate)).toFloat() }

    private fun convert(pcm: FloatArray, rate: Int, block: Int = 1024, channels: Int = 1): FloatArray {
        val converter = BoundedPcmResampler(480_000)
        try {
            for (start in pcm.indices step block) {
                val end = minOf(start + block, pcm.size)
                val bytes = ByteBuffer.allocate((end - start) * channels * 4).order(ByteOrder.LITTLE_ENDIAN)
                for (i in start until end) repeat(channels) { bytes.putFloat(pcm[i]) }
                bytes.flip()
                converter.add(bytes, rate, channels, AudioFormat.ENCODING_PCM_FLOAT)
            }
            return converter.copyResult()
        } finally { converter.clear() }
    }

    private fun interiorRms(pcm: FloatArray): Double =
        sqrt((160 until pcm.size - 160).sumOf { pcm[it].toDouble().pow(2) } / (pcm.size - 320))

    @Test fun ultrasonicEnergyDoesNotFoldIntoInfantVocalBand() {
        for (rate in listOf(44_100, 48_000, 96_000)) {
            val output = convert(tone(rate, 12_000.0), rate)
            val ratio = interiorRms(output) / (.25 / sqrt(2.0))
            assertTrue("$rate Hz: 12 kHz aliased into 4 kHz, ratio=$ratio", ratio < .0032)
        }
    }

    @Test fun vocalBandKeepsGainWithoutPitchOrTimelineShift() {
        for (rate in listOf(44_100, 48_000, 96_000)) for (hz in listOf(300.0, 1_000.0, 4_000.0, 6_000.0)) {
            val output = convert(tone(rate, hz), rate)
            assertEquals(16_000, output.size)
            val reference = tone(16_000, hz)
            val error = sqrt((160 until 15_840).sumOf { (output[it] - reference[it]).toDouble().pow(2) } / 15_680)
            assertTrue("$rate/$hz gain/time error=$error", error < .002)
        }
    }

    @Test fun arbitraryCodecBlocksDoNotChangeSamples() {
        val pcm = tone(44_100, 997.0)
        val reference = convert(pcm, 44_100, pcm.size)
        for (block in listOf(1, 127, 1024, 2049)) {
            assertArrayEquals(reference, convert(pcm, 44_100, block, 2), 0f)
        }
    }

    @Test fun selectedTenAndTwentySecondIntervalsRetainTheirTail() {
        for (rate in listOf(8_000, 16_000, 44_100, 48_000, 96_000)) for (seconds in listOf(10, 20)) {
            val pcm = FloatArray(rate * seconds) { .125f }
            val output = convert(pcm, rate)
            assertEquals("$rate/$seconds length", seconds * 16_000, output.size)
            assertTrue(output.all { abs(it - .125f) < 1e-6 })
        }
    }

    @Test fun native16kRemainsBitIdentical() {
        val pcm = tone(16_000, 773.0)
        assertArrayEquals(pcm, convert(pcm, 16_000), 0f)
    }

    @Test fun nonfinitePcmIsRejectedBeforeClamping() {
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            try {
                convert(floatArrayOf(invalid), 48_000)
                fail("Nonfinite samples must fail")
            } catch (_: Exception) { }
        }
    }

    @Test fun transitionBandAndImpulseRemainBounded() {
        for (hz in listOf(8_100.0, 8_500.0, 9_000.0)) {
            val output = convert(tone(48_000, hz), 48_000)
            assertTrue("transition $hz", interiorRms(output) / (.25 / sqrt(2.0)) < .015)
        }
        val impulse = FloatArray(48_000).also { it[24_000] = .75f }
        val output = convert(impulse, 48_000)
        assertEquals(8_000, output.indices.maxBy { abs(output[it]) })
        assertTrue(output.all { it.isFinite() && abs(it) <= 1 })
    }

    @Test fun resultCapClearAndPcm16StereoWork() {
        val converter = BoundedPcmResampler(100)
        repeat(2) {
            val bytes = ByteBuffer.allocate(4 * 1_000).order(ByteOrder.LITTLE_ENDIAN)
            repeat(1_000) { bytes.putShort(4096); bytes.putShort(12288) }
            bytes.flip()
            converter.add(bytes, 48_000, 2, AudioFormat.ENCODING_PCM_16BIT)
            val result = converter.copyResult()
            assertEquals(100, result.size)
            assertTrue(result.all { abs(it - .25f) < 1e-6 })
            converter.clear()
            assertEquals(0, converter.size)
        }
    }

    @Test fun formatCannotChangeWhileFutureSamplesAreBuffered() {
        val converter = BoundedPcmResampler()
        try {
            val bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(.1f)
            bytes.flip()
            converter.add(bytes, 48_000, 1, AudioFormat.ENCODING_PCM_FLOAT)
            assertEquals(0, converter.size)
            bytes.rewind()
            try {
                converter.add(bytes, 44_100, 1, AudioFormat.ENCODING_PCM_FLOAT)
                fail("Rate changed with buffered history")
            } catch (_: UnsupportedAudioException) { }
        } finally { converter.clear() }
    }
}
