package com.moa.babylanguage

import android.media.AudioFormat
import java.nio.ByteBuffer
import kotlin.math.*

/** Analysis-only mono conversion. Suppress aliases BEFORE reducing the sample rate.
 * A bounded ring supplies a symmetric windowed-sinc kernel; waiting for future
 * samples does not move output timestamps. EOS extends edges and flushes the tail.
 * No speech gate/normalization is applied; native 16kHz PCM bypasses the filter.
 */
internal class BoundedPcmResampler(maxSamples: Int = 160_000) {
    init { require(maxSamples in 1..480_000) }
    private val output = FloatArray(maxSamples)
    var size: Int = 0
        private set
    private var rate = 0
    private var sourceCount = 0L
    private var first = 0f
    private var last = 0f
    private var radius = 0
    private var ring = FloatArray(0)
    // Lazily populated, bounded coefficient phases. Max phase error <=1/2048 of
    // one SOURCE sample. 48/32/96k integer-ratio decimation uses the exact phase0.
    private val kernels = arrayOfNulls<DoubleArray>(1024)
    private var finished = false

    fun add(buffer: ByteBuffer, sampleRate: Int, channels: Int, encoding: Int) {
        if (finished || sampleRate !in 8_000..96_000 || channels !in 1..2 ||
            rate != 0 && rate != sampleRate) throw UnsupportedAudioException()
        val bytesPerSample = when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> throw UnsupportedAudioException()
        }
        val frameBytes = bytesPerSample * channels
        if (buffer.remaining() % frameBytes != 0) throw UnsupportedAudioException()
        if (rate == 0) {
            rate = sampleRate
            radius = ceil(32 * max(1.0, rate / 16_000.0)).toInt()
            ring = FloatArray(2 * radius + 16)
        }
        while (buffer.remaining() >= frameBytes && size < output.size) {
            var sum = 0.0
            repeat(channels) {
                val value = if (encoding == AudioFormat.ENCODING_PCM_16BIT)
                    buffer.short / 32768f else buffer.float
                // Infinity must not become a valid full-scale sample via coerceIn.
                if (!value.isFinite()) throw UnsupportedAudioException()
                sum += value
            }
            val value = (sum / channels).coerceIn(-1.0, 1.0).toFloat()
            if (sourceCount == 0L) first = value
            last = value
            ring[(sourceCount % ring.size).toInt()] = value
            sourceCount++
            if (rate == 16_000) output[size++] = value
            else emitReady(flush = false)
        }
    }

    private fun emitReady(flush: Boolean) {
        val limit = minOf(output.size.toLong(), (sourceCount * 16_000 + rate - 1) / rate).toInt()
        while (size < limit) {
            val position = size.toLong() * rate
            var center = position / 16_000
            var phase = ((position % 16_000) * 1024.0 / 16_000).roundToInt()
            if (phase == 1024) { center++; phase = 0 }
            if (!flush && center + radius >= sourceCount) return
            val weights = kernels[phase] ?: makeKernel(phase / 1024.0).also { kernels[phase] = it }
            var value = 0.0
            for (i in weights.indices) {
                val index = center + i - radius
                val sample = when {
                    index < 0 -> first
                    index >= sourceCount -> last
                    else -> {
                        check(index >= sourceCount - ring.size) { "Resampler history overwritten" }
                        ring[(index % ring.size).toInt()]
                    }
                }
                value += weights[i] * sample
            }
            output[size++] = value.coerceIn(-1.0, 1.0).toFloat()
        }
    }

    private fun makeKernel(fraction: Double): DoubleArray {
        // Blackman transition centred below the destination Nyquist frequency.
        val cutoff = .94 * minOf(1.0, 16_000.0 / rate)
        val weights = DoubleArray(2 * radius + 1) { i ->
            val x = i - radius - fraction
            if (abs(x) > radius) 0.0 else {
                val sinc = if (abs(x) < 1e-12) cutoff else sin(PI * cutoff * x) / (PI * x)
                sinc * (.42 + .5 * cos(PI * x / radius) + .08 * cos(2 * PI * x / radius))
            }
        }
        val sum = weights.sum()
        for (i in weights.indices) weights[i] /= sum
        return weights
    }

    fun copyResult(): FloatArray {
        check(!finished) { "Result already consumed" }
        if (sourceCount > 0 && rate != 16_000) emitReady(flush = true)
        finished = true
        return output.copyOf(size).also {
            output.fill(0f)
            ring.fill(0f)
            first = 0f
            last = 0f
        }
    }

    fun clear() {
        output.fill(0f)
        ring.fill(0f)
        kernels.fill(null)
        size = 0
        sourceCount = 0
        first = 0f
        last = 0f
        rate = 0
        finished = false
    }
}
