package com.moa.babylanguage

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

internal data class ClipRange(val startMs: Long, val endMs: Long) {
    val durationMs get() = endMs - startMs
    init {
        require(startMs >= 0 && endMs <= 60_500 && durationMs in 1_000..15_000)
    }
    fun label() = "${clock(startMs)}–${clock(endMs)}"
    companion object {
        fun clock(ms: Long): String = "%d:%02d".format(java.util.Locale.ROOT, ms / 60_000, ms / 1000 % 60)
    }
}

/** Acoustic observations, not inferred needs. No random/session-dependent rewriting. */
internal data class AcousticShape(val activeFraction: Float, val rising: Boolean, val bursts: Int, val wordingKey: Int = 0) {
    companion object {
        fun from(pcm: FloatArray): AcousticShape {
            val energy = (pcm.indices step 1600).map { start ->
                val end = minOf(start + 1600, pcm.size)
                var sum = 0.0
                for (i in start until end) sum += pcm[i].toDouble() * pcm[i]
                sqrt(sum / (end - start)).toFloat()
            }
            val active = energy.map { it >= 0.012f }
            val bursts = active.indices.count { active[it] && (it == 0 || !active[it - 1]) }
            val half = (energy.size / 2).coerceAtLeast(1)
            val first = energy.take(half).average()
            val last = energy.takeLast(half).average()
            // Deterministic surface wording only. This key is not a semantic/intent feature
            // and is neither stored nor sent anywhere. Equivalent phrases share one guard.
            var wordingKey = 17
            for (i in pcm.indices step 160) wordingKey = wordingKey * 31 + (pcm[i] * 32767).toInt()
            return AcousticShape(active.count { it }.toFloat() / active.size.coerceAtLeast(1),
                first > 0.003 && last > first * 1.8, bursts, wordingKey)
        }
    }
}

internal data class ClipInterpretation(
    val phrase: String,
    val sound: String,
    val evidence: String,
    val guide: String,
    val range: String,
    val visual: Boolean,
    val visualNotice: String? = null
)

internal data class ClipAnalysisResult(val interpretation: ClipInterpretation? = null, val problem: String? = null)

internal object EvidenceInterpreter {
    // Conservative product gate, NOT a measured confidence/accuracy percentage.
    // Generic adult laughter/speech and a face alone cannot create a baby phrase.
    fun interpret(audio: SoundAnalysis, shape: AcousticShape, range: ClipRange,
                  visualEvidence: VisualEvidence? = null): ClipAnalysisResult {
        if (audio.problem != null) return ClipAnalysisResult(problem = audio.problem)
        val infantLabels = setOf("울음", "옹알이 후보", "아기 웃음 후보")
        val cue = audio.cues.filter { it.label in infantLabels && it.score >= 0.35f }.maxByOrNull { it.score }
            ?: return ClipAnalysisResult(problem = "이 구간에서는 아기 소리 단서를 충분히 찾지 못했어요. 소리가 또렷한 구간을 다시 골라 주세요.")
        if (cue.label != "울음" && audio.cues.any {
                it.label in setOf("울음", "울음(화자 미확인)", "비명(화자 미확인)") && it.score >= 0.30f
            }) return ClipAnalysisResult(problem = "울음·높은 소리와 다른 소리가 섞여 있어 한마디로 표현하기 어려워요. 짧은 구간으로 다시 골라 주세요.")
        if (shape.activeFraction < 0.04f) return ClipAnalysisResult(problem = "선택한 구간이 너무 조용해요. 다시 들어보고 아기 소리가 있는 구간을 골라 주세요.")
        val sound = when (cue.label) { "옹알이 후보" -> "옹알이"; "아기 웃음 후보" -> "아기 웃음"; else -> "울음" }
        val eligiblePhrases = when (sound) {
            "울음" -> when {
                shape.rising -> listOf("으앙… 으아앙! 나 좀 봐주세요.", "으아앙… 여기 좀 봐줘요!", "응애! 잠깐 나 좀 봐줘요.")
                shape.activeFraction >= 0.65f -> listOf("으앙, 으앙… 나 좀 도와줘요.", "으앙… 잠깐 내 곁에 있어줘요.", "응애, 응애… 토닥토닥 해줄래요?")
                shape.bursts >= 3 -> listOf("응애… 응애… 여기 좀 봐주세요.", "으앙… 또 울음이 나와요.", "응애… 잠깐만 내 곁에 있어줘요.")
                else -> listOf("응애… 나 좀 봐주세요.", "으앙… 잠깐 나한테 와줄래요?", "응애… 여기 좀 봐줘요.")
            }
            "아기 웃음" -> if (shape.bursts >= 3) listOf("히히, 헤헤! 자꾸 웃음이 나요.", "까르르! 또 웃음이 나왔어요.", "히히! 내 웃음이 안 멈춰요.")
                else listOf("헤헤! 같이 웃어요.", "히히, 웃음이 나요!", "헤헤, 나랑 같이 웃어줄래요?")
            else -> when {
                shape.bursts >= 3 -> listOf("아아, 옹알옹알! 내 얘기도 들어봐요.", "아아… 우우! 나도 얘기할래요.", "옹알옹알! 이번엔 내 차례예요.")
                shape.activeFraction >= 0.65f -> listOf("옹알옹알, 할 말이 많아요!", "아아, 우우! 내 얘기 아직 안 끝났어요.", "옹알옹알… 내 얘기 좀 들어봐요!")
                else -> listOf("아아, 우우… 내 얘기 들려요?", "옹알… 나도 한마디 할래요.", "아아… 내 소리에도 답해줄래요?")
            }
        }
        val phrase = eligiblePhrases[Math.floorMod(shape.wordingKey, eligiblePhrases.size)]
        val rhythm = when {
            shape.rising -> "뒤쪽에서 소리가 더 커졌어요."
            shape.bursts >= 3 -> "소리와 쉼이 여러 번 이어졌어요."
            shape.activeFraction >= 0.65f -> "소리가 비교적 길게 이어졌어요."
            else -> "짧게 들리는 소리를 살펴봤어요."
        }
        return ClipAnalysisResult(ClipInterpretation(phrase, sound,
            "${range.label()}에서 $sound 후보를 찾았어요. $rhythm" +
                (visualEvidence?.let { "\n${it.evidence}" } ?: "") +
                "\n일반 소리 모델의 관찰을 아이 시점으로 풀어 쓴 표현이에요. 아기 신원이나 배고픔·졸림의 원인은 확인할 수 없어요.",
            responseGuide(sound), range.label(),
            visualEvidence != null && visualEvidence.status != VisualEvidenceStatus.FAILED,
            visualEvidence?.notice))
    }

    fun responseGuide(sound: String): String = if (sound == "울음")
        "먼저 편안한지, 수유·잠·기저귀 등 최근 돌봄을 살펴주세요. 곁에서 부드럽게 말을 걸어 주세요."
    else "아기 소리가 끝나면 잠깐 기다렸다가 눈을 맞추고 짧게 답해 주세요. 다시 소리를 내면 차례를 주고받아 보세요."

}

/** All decoders receive exactly the range previewed by the caregiver. */
internal interface ClipAnalyzer {
    suspend fun analyze(context: Context, uri: Uri, range: ClipRange, visual: Boolean): ClipAnalysisResult
}

internal object DeviceClipAnalyzer : ClipAnalyzer {
    override suspend fun analyze(context: Context, uri: Uri, range: ClipRange, visual: Boolean): ClipAnalysisResult =
        withContext(Dispatchers.Default) {
            val pcm = VideoAudioDecoder.decode(context, uri, range.startMs, range.endMs)
                ?: return@withContext ClipAnalysisResult(problem = "이 영상에는 소리가 없어요. 아기 소리가 담긴 영상이나 녹음을 골라 주세요.")
            try {
                val audio = SoundAnalyzer.analyzeDetailed(context, pcm)
                val shape = AcousticShape.from(pcm)
                val preliminary = EvidenceInterpreter.interpret(audio, shape, range)
                if (preliminary.interpretation == null) return@withContext preliminary
                ensureActive()
                val frames = if (visual) inspectVideoEvidence(context, uri, range.startMs, range.endMs) else null
                ensureActive()
                EvidenceInterpreter.interpret(audio, shape, range, frames)
            } finally { pcm.fill(0f) }
        }
}
