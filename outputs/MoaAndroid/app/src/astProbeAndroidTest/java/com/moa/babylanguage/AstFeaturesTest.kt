package com.moa.babylanguage

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.round
import kotlin.math.sin

class AstFeaturesTest {
    @Test fun rejectsInvalidPcmAndPreservesCancellation() {
        for (input in listOf(FloatArray(15999), FloatArray(160001),
            FloatArray(16000) { Float.NaN }, FloatArray(16000) { 1.01f })) {
            assertThrows(IllegalArgumentException::class.java) { AstFeatures.extract(input) }
        }
        var checks = 0
        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            AstFeatures.extract(FloatArray(16000)) { if (++checks == 2) throw kotlinx.coroutines.CancellationException() }
        }
        assertEquals(2, checks)
    }

    @Test fun syntheticFeaturesMatchPinnedNumpyGolden() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val golden = JSONObject(context.assets.open("ast-feature-golden.json").bufferedReader().use { it.readText() })
        val cases = golden.getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val features = AstFeatures.extract(synthetic(case.getInt("duration_ms"), case.getString("kind")))
            assertEquals(1024 * 128, features.size)
            assertTrue(features.all { it.isFinite() })
            val expected = case.getJSONArray("selected_values")
            for (j in 0 until expected.length()) {
                val value = expected.getJSONObject(j)
                assertEquals(case.getString("id"), value.getDouble("value").toFloat(),
                    features[value.getInt("frame") * 128 + value.getInt("mel")], 0.00002f)
            }
        }
    }

    companion object {
        fun synthetic(durationMs: Int, kind: String): FloatArray = FloatArray(durationMs * 16) { index ->
            val t = index / 16000.0
            val value = when (kind) {
                "silence" -> 0.0
                "tone" -> 0.2 * sin(2 * PI * 440 * t)
                "sweep" -> 0.15 * sin(2 * PI * (90 * t + 70 * t * t)) + 0.025
                "impulse" -> if (index % 8001 == 0) 0.5 else 0.0
                else -> error("Unknown synthetic signal")
            }
            round(value * 32767).toInt().toShort().toFloat() / 32768f
        }
    }
}
