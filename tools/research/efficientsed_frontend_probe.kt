package research.efficientsed

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path

/** Host-only IO adapter. Keep this file out of Android source sets. */
object EfficientSedFrontendProbe {
    @JvmStatic fun main(args: Array<String>) {
        if (args.contentEquals(arrayOf("--self-test"))) {
            for (bad in listOf(FloatArray(159999), FloatArray(160001),
                FloatArray(160000) { Float.NaN }, FloatArray(160000) { Float.POSITIVE_INFINITY },
                FloatArray(160000) { 1.01f }, FloatArray(160000) { -1.01f },
                FloatArray(160000) { Float.MAX_VALUE })) {
                check(runCatching { EfficientSedFrontend.extract(bad) }.exceptionOrNull() is IllegalArgumentException)
            }
            var calls = 0
            check(runCatching { EfficientSedFrontend.extract(FloatArray(160000)) { ++calls == 3 } }
                .exceptionOrNull() is IllegalStateException && calls == 3)
            val silence = EfficientSedFrontend.extract(FloatArray(160000))
            check(silence.size == 128000 && silence.all { it.isFinite() && it == silence[0] })
            val boundaries = FloatArray(160000)
            boundaries[0] = -1f; boundaries[boundaries.lastIndex] = 1f
            check(EfficientSedFrontend.extract(boundaries).all { it.isFinite() })
            println("EfficientSED contracts passed: length, NaN, Infinity, range, cancellation, silence shape, +/-1")
            return
        }
        require(args.size == 2) { "Usage: EfficientSedFrontendProbe pcm.f32 mel.f32" }
        val input = ByteBuffer.wrap(Files.readAllBytes(Path.of(args[0]))).order(ByteOrder.LITTLE_ENDIAN)
        require(input.remaining() == 160000 * 4)
        val pcm = FloatArray(160000) { input.float }
        val started = System.nanoTime()
        val mel = EfficientSedFrontend.extract(pcm)
        val elapsed = (System.nanoTime() - started) / 1e6
        val output = ByteBuffer.allocate(mel.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (value in mel) output.putFloat(value)
        Files.write(Path.of(args[1]), output.array())
        println("{\"frontend_ms\":$elapsed}")
    }
}
