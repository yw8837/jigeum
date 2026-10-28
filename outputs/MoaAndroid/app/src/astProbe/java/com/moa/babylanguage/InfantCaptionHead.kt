package com.moa.babylanguage

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.exp
import kotlin.math.sqrt

/** Frozen caption-only head. Scores are broad clues, not calibrated probabilities. */
internal class InfantCaptionHead private constructor(private val weights: FloatArray, private val bias: Float) {
    fun score(embedding: FloatArray): Float {
        require(embedding.size == 768 && embedding.all { it.isFinite() })
        val norm = sqrt(embedding.sumOf { it.toDouble() * it })
        require(norm.isFinite() && norm > 1e-12) { "Invalid caption representation norm" }
        var value = bias.toDouble()
        for (index in embedding.indices) value += embedding[index].toDouble() / norm * weights[index]
        val result = if (value >= 0) 1.0 / (1.0 + exp(-value)) else {
            val exponent = exp(value); exponent / (1.0 + exponent)
        }
        return result.toFloat().also { check(it.isFinite() && it in 0f..1f) }
    }

    companion object {
        const val ASSET = "ast-caption-presence.f32"
        const val EMBEDDING = "/source/classifier/layernorm/LayerNormalization_output_0"
        const val SHA256 = "979e79448a5b27293c02642eb8a72ccc6fbf322fd16cc9ee43a467e7434c2df1"
        fun load(context: Context, checkActive: () -> Unit): InfantCaptionHead {
            checkActive()
            val bytes = context.assets.open(ASSET).use { input ->
                val bounded = ByteArray(3077)
                var total = 0
                while (total < bounded.size) {
                    checkActive()
                    val count = input.read(bounded, total, bounded.size - total)
                    if (count < 0) break
                    check(count > 0) { "Caption asset read stalled" }
                    total += count
                }
                try { bounded.copyOf(total) } finally { bounded.fill(0) }
            }
            try {
                checkActive()
                return decode(bytes)
            } finally { bytes.fill(0) }
        }
        internal fun decode(bytes: ByteArray): InfantCaptionHead {
            require(bytes.size == 3076)
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            require(sha == SHA256) { "Caption asset hash mismatch" }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            val weights = FloatArray(768).also { buffer.get(it) }
            val bias = buffer.get()
            require(weights.all { it.isFinite() } && bias.isFinite())
            return InfantCaptionHead(weights, bias)
        }
    }
}
