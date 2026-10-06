package research.efficientsed

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.ln
import kotlin.math.max

/** Fixed official EfficientSED eval frontend. No Android, ORT, or file dependency.
 * Input is exactly 160000 finite mono float samples at 16kHz within [-1,1].
 * Output is mel-major [1,1,128,1000]. Resampling and short/long clip policy belong
 * to the caller. A 10s PCM window loses one sample to valid conv preemphasis;
 * center-reflected STFT then produces exactly 1000 frames, with no mel padding.
 * Constants are generated from the pinned official torch/torchaudio eval tensors.
 */
object EfficientSedFrontend {
    const val SAMPLES = 160000
    const val FRAMES = 1000
    const val MELS = 128
    private const val FFT = 512
    private const val WINDOW = 400
    private const val HOP = 160

    fun extract(samples: FloatArray, cancelled: () -> Boolean = { false }): FloatArray {
        require(samples.size == SAMPLES) { "EfficientSED requires a fixed 10s 16kHz window" }
        require(samples.all { it.isFinite() && it in -1f..1f }) { "PCM must be finite and normalized within [-1,1]" }
        check(!cancelled()) { "EfficientSED feature extraction cancelled" }
        val emphasized = FloatArray(samples.size - 1) {
            // torch conv1d uses cross correlation: x[n+1] - .97*x[n].
            (-0.97f.toDouble() * samples[it] + samples[it + 1]).toFloat()
        }
        val output = FloatArray(MELS * FRAMES)
        val real = DoubleArray(FFT)
        val imaginary = DoubleArray(FFT)
        val power = FloatArray(FFT / 2 + 1)
        for (frame in 0 until FRAMES) {
            check(!cancelled()) { "EfficientSED feature extraction cancelled" }
            real.fill(0.0)
            imaginary.fill(0.0)
            // torch pads the 400 window to 512 at offset 56, then centers FFT
            // on frame*hop. Reflect excludes the boundary sample itself.
            for (i in 0 until WINDOW) {
                var index = frame * HOP - FFT / 2 + (FFT - WINDOW) / 2 + i
                if (index < 0) index = -index
                if (index >= emphasized.size) index = 2 * emphasized.size - 2 - index
                real[(FFT - WINDOW) / 2 + i] =
                    (emphasized[index] * EfficientSedTables.hann[i]).toDouble()
            }
            fft(real, imaginary)
            for (i in power.indices) {
                val r = real[i].toFloat()
                val im = imaginary[i].toFloat()
                power[i] = r * r + im * im
            }
            for (mel in 0 until MELS) {
                val bins = EfficientSedTables.bins[mel]
                val weights = EfficientSedTables.weights[mel]
                var energy = 0.0
                for (j in bins.indices) energy += power[bins[j]].toDouble() * weights[j]
                val log = ln(max(1e-7f, energy.toFloat()).toDouble()).toFloat()
                output[mel * FRAMES + frame] = (log + 4.5f) / 5f
            }
        }
        check(output.all { it.isFinite() }) { "EfficientSED produced nonfinite features" }
        return output
    }

    private fun fft(real: DoubleArray, imaginary: DoubleArray) {
        var reversed = 0
        for (i in 1 until FFT) {
            var bit = FFT shr 1
            while (reversed and bit != 0) { reversed = reversed xor bit; bit = bit shr 1 }
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
                    real[odd] = real[even] - r; imaginary[odd] = imaginary[even] - im
                    real[even] += r; imaginary[even] += im
                    val nextR = factorR * stepR - factorI * stepI
                    factorI = factorR * stepI + factorI * stepR; factorR = nextR
                }
            }
            length *= 2
        }
    }
}
