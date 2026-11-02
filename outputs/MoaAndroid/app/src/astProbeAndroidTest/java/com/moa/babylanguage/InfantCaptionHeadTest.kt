package com.moa.babylanguage

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import kotlin.math.sin

class InfantCaptionHeadTest {
    private fun head(): InfantCaptionHead {
        assumeTrue("Explicit caption research variant only", BuildConfig.AST_INFANT_CAPTION)
        return InfantCaptionHead.load(InstrumentationRegistry.getInstrumentation().targetContext) {}
    }

    @Test fun frozenHeadMatchesIndependentNumpyOnSyntheticEmbeddings() {
        val head = head()
        val sine = FloatArray(768) { sin(it.toDouble() * .07).toFloat() }
        val basis = FloatArray(768).apply { this[0] = 1f }
        assertEquals(.2479956366183243, head.score(sine).toDouble(), 5e-5)
        assertEquals(.19929681503934196, head.score(basis).toDouble(), 5e-5)
        assertEquals(head.score(sine).toDouble(), head.score(FloatArray(768) { sine[it] * 3f }).toDouble(), 5e-5)
    }

    @Test fun missingInvalidEmbeddingAndCorruptAssetCannotCreateScore() {
        val head = head()
        for (value in listOf(FloatArray(767), FloatArray(768), FloatArray(768) { Float.NaN },
            FloatArray(768) { Float.POSITIVE_INFINITY })) {
            try { head.score(value); fail("Invalid embedding accepted") } catch (_: IllegalArgumentException) { }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bytes = context.assets.open(InfantCaptionHead.ASSET).use { it.readBytes() }
        try {
            for (value in listOf(bytes.copyOf(3075), bytes.copyOf(3077), bytes.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() })) {
                try { InfantCaptionHead.decode(value); fail("Corrupt head accepted") } catch (_: IllegalArgumentException) { }
            }
        } finally { bytes.fill(0) }
    }

    @Test fun loadingKeepsCancellationObservable() {
        head()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        try {
            InfantCaptionHead.load(context) { throw CancellationException("owned test") }
            fail("Cancellation swallowed")
        } catch (_: CancellationException) { }
    }
}
