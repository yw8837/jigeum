package com.moa.babylanguage

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class AudioCleanupTest {
    private fun tone(hz: Int, amplitude: Float = .1f) = FloatArray(48000) { amplitude*sin(2*PI*hz*it/16000).toFloat() }
    private fun rms(pcm: FloatArray) = sqrt(pcm.drop(1600).sumOf { it.toDouble()*it }/(pcm.size-1600))
    @Test fun removesDcAndAttenuatesRumbleWithoutMutatingInput() {
        val dc=FloatArray(48000){.3f}
        assertEquals(0.0,rms(AudioCleanup.filter(dc)),1e-7)
        assertTrue(dc.all { it==.3f })
        val rumble=tone(40)
        assertTrue(rms(AudioCleanup.filter(rumble))/rms(rumble)<.26)
    }
    @Test fun preservesVocalBandQuietSoundsAndTimingWithoutGainPumping() {
        for (hz in listOf(250,440,1000,4000)) for (level in listOf(.1f,.01f)) {
            val signal=tone(hz,level); val copy=signal.copyOf()
            val output=AudioCleanup.filter(signal)
            assertEquals(signal.size,output.size)
            assertArrayEquals(copy,signal,0f)
            assertTrue(rms(output)/rms(signal) in .985..1.01)
        }
    }
    @Test fun clippingProtectionAndSilenceStayFinite() {
        val signal=FloatArray(48000){if (it%40<20) 1f else -1f}
        assertTrue(AudioCleanup.filter(signal).all { it.isFinite() && it in -1f..1f })
        assertTrue(AudioCleanup.filter(FloatArray(16000)).all { it==0f })
        assertTrue(runCatching { AudioCleanup.filter(FloatArray(16000){Float.NaN}) }.isFailure)
    }
    @Test fun removedDcCannotBecomeInfantRhythmOrVolumeEvidence() {
        val shape=AcousticShape.from(FloatArray(48000){.3f})
        assertEquals(0f,shape.activeFraction,0f)
        assertEquals(0,shape.bursts)
        assertFalse(shape.rising)
        val quiet=tone(440,.02f)
        val withDc=FloatArray(quiet.size){quiet[it]+.3f}
        val before=AcousticShape.from(quiet); val after=AcousticShape.from(withDc)
        assertEquals(before.activeFraction,after.activeFraction,.01f)
        assertEquals(before.bursts,after.bursts)
        assertEquals(before.rising,after.rising)
    }
}
