package com.moa.babylanguage

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Analysis-only DC/low-frequency rumble filter, 16 kHz mono. Not speaker separation.
 * No noise gate, automatic gain boost or speech-focused suppressor: faint infant
 * vocalizations must not be removed just because they are quiet/non-speech. */
internal object AudioCleanup {
    /** The bundled MediaPipe YAMNet consumes 15,600 samples per observation.
     * Include its zero-padded tail in the denominator; a final click must not get
     * amplified by measuring only its few remaining real samples. No gain is applied. */
    fun classifierWindowRms(cleanedPcm: FloatArray, startMs: Long): Double? {
        if (cleanedPcm.isEmpty() || cleanedPcm.size > 480_000 || startMs < 0 ||
            startMs >= (cleanedPcm.size + 15L) / 16L) return null
        val start = (startMs * 16L).toInt()
        val end = minOf(start + 15_600, cleanedPcm.size)
        var energy = 0.0
        for (i in start until end) {
            val sample = cleanedPcm[i]
            if (!sample.isFinite() || sample !in -1f..1f) return null
            energy += sample.toDouble() * sample
        }
        return sqrt(energy / 15_600)
    }

    /** Presence/level only, not an infant classifier. Examine every one-second window
     * so surrounding silence cannot dilute a short sound. PCM and timestamps stay intact. */
    fun strongestSecondRms(cleanedPcm: FloatArray): Double {
        require(cleanedPcm.isNotEmpty() && cleanedPcm.size <= 480_000 && cleanedPcm.all { it.isFinite() })
        val width = minOf(16_000, cleanedPcm.size)
        var energy = 0.0
        for (i in 0 until width) energy += cleanedPcm[i].toDouble() * cleanedPcm[i]
        var maximum = energy
        for (i in width until cleanedPcm.size) {
            energy += cleanedPcm[i].toDouble() * cleanedPcm[i] -
                cleanedPcm[i - width].toDouble() * cleanedPcm[i - width]
            maximum = maxOf(maximum, energy)
        }
        return sqrt(maximum.coerceAtLeast(0.0) / width)
    }

    fun filter(pcm: FloatArray): FloatArray {
        require(pcm.isNotEmpty() && pcm.size <= 480_000 && pcm.all { it.isFinite() })
        val mean = pcm.sumOf { it.toDouble() } / pcm.size
        // Second-order Butterworth high-pass, 80 Hz (well below typical vocal harmonics).
        val omega = 2 * PI * 80 / 16_000
        val alpha = sin(omega) / sqrt(2.0)
        val a0 = 1 + alpha
        val b0 = (1 + cos(omega)) / (2 * a0)
        val b1 = -2 * b0
        val a1 = -2 * cos(omega) / a0
        val a2 = (1 - alpha) / a0
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        return FloatArray(pcm.size) { i ->
            val x = pcm[i] - mean
            val y = b0 * x + b1 * x1 + b0 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            y.coerceIn(-1.0, 1.0).toFloat()
        }
    }
}
