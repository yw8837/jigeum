package com.moa.babylanguage

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Staged Android-compatible frontend. No Android/runtime dependency.
 * Input: mono float PCM /32768, 16kHz, exactly 1..10s. Output: flattened
 * [1,1024,128], frame-major. Raw fbank padding is zero before normalization.
 * Matches pinned HF NumPy AST frontend; not generic mel or YAMNet input.
 */
internal object AstFeatures {
    private const val FRAMES = 1024
    private const val BINS = 128
    private const val WINDOW = 400
    private const val HOP = 160
    private const val FFT = 512
    private const val MEAN = -4.2677393f
    private val denominator = (4.5689974 * 2).toFloat()
    private val hann = DoubleArray(WINDOW) { 0.5 - 0.5 * cos(2 * PI * it / (WINDOW - 1)) }
    private val filters = makeMelFilters()

    fun extract(samples: FloatArray, checkActive: () -> Unit = {}): FloatArray {
        require(samples.size in 16000..160000) { "AST window requires 1..10s of 16kHz mono PCM" }
        require(samples.all { it.isFinite() && it >= -1f && it <= 1f }) { "PCM must be finite normalized [-1,1]" }
        val output = FloatArray(FRAMES * BINS) { -MEAN / denominator }
        val real = DoubleArray(FFT)
        val imaginary = DoubleArray(FFT)
        val frame = DoubleArray(WINDOW)
        val powers = DoubleArray(FFT / 2 + 1)
        val frameCount = 1 + (samples.size - WINDOW) / HOP
        for (frameIndex in 0 until frameCount) {
            checkActive()
            var sum = 0.0
            val offset = frameIndex * HOP
            for (i in 0 until WINDOW) sum += samples[offset + i].toDouble()
            val average = sum / WINDOW
            for (i in 0 until WINDOW) frame[i] = samples[offset + i].toDouble() - average
            // Reverse traversal preserves the original previous sample.
            for (i in WINDOW - 1 downTo 1) frame[i] -= 0.97 * frame[i - 1]
            frame[0] *= 0.03
            real.fill(0.0)
            imaginary.fill(0.0)
            for (i in 0 until WINDOW) real[i] = frame[i] * hann[i]
            fft(real, imaginary)
            for (i in powers.indices) {
                // HF stores complex64 before promoting power computation.
                val re = real[i].toFloat().toDouble()
                val im = imaginary[i].toFloat().toDouble()
                powers[i] = re * re + im * im
            }
            for (mel in 0 until BINS) {
                var energy = 0.0
                for (i in powers.indices) energy += powers[i] * filters[mel][i]
                val fbank = ln(max(1.192092955078125e-7, energy)).toFloat()
                output[frameIndex * BINS + mel] = (fbank - MEAN) / denominator
            }
        }
        return output
    }

    private fun makeMelFilters(): Array<DoubleArray> {
        fun mel(f: Double) = 1127.0 * ln(1.0 + f / 700.0)
        val first = mel(20.0)
        val last = mel(8000.0)
        val edges = DoubleArray(BINS + 2) { first + (last - first) * it / (BINS + 1) }
        return Array(BINS) { index ->
            DoubleArray(FFT / 2 + 1) { frequencyBin ->
                val frequency = mel(frequencyBin * 16000.0 / FFT)
                max(0.0, min((frequency - edges[index]) / (edges[index + 1] - edges[index]),
                    (edges[index + 2] - frequency) / (edges[index + 2] - edges[index + 1])))
            }
        }
    }

    private fun fft(real: DoubleArray, imaginary: DoubleArray) {
        var reversed = 0
        for (i in 1 until FFT) {
            var bit = FFT shr 1
            while (reversed and bit != 0) {
                reversed = reversed xor bit
                bit = bit shr 1
            }
            reversed = reversed xor bit
            if (i < reversed) {
                val r = real[i]; real[i] = real[reversed]; real[reversed] = r
                val im = imaginary[i]; imaginary[i] = imaginary[reversed]; imaginary[reversed] = im
            }
        }
        var length = 2
        while (length <= FFT) {
            val angle = -2 * PI / length
            val stepR = cos(angle)
            val stepI = sin(angle)
            for (start in 0 until FFT step length) {
                var factorR = 1.0
                var factorI = 0.0
                for (offset in 0 until length / 2) {
                    val even = start + offset
                    val odd = even + length / 2
                    val r = real[odd] * factorR - imaginary[odd] * factorI
                    val im = real[odd] * factorI + imaginary[odd] * factorR
                    real[odd] = real[even] - r
                    imaginary[odd] = imaginary[even] - im
                    real[even] += r
                    imaginary[even] += im
                    val nextR = factorR * stepR - factorI * stepI
                    factorI = factorR * stepI + factorI * stepR
                    factorR = nextR
                }
            }
            length *= 2
        }
    }
}
