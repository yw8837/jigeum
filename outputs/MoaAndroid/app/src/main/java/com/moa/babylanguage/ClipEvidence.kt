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
            // Match the model's analysis filter. Removed hum/DC must not describe
            // an infant's volume or rhythm. Playback and the wording key use raw PCM.
            val cleaned = AudioCleanup.filter(pcm)
            val energy = try {
                (cleaned.indices step 1600).map { start ->
                    val end = minOf(start + 1600, cleaned.size)
                    var sum = 0.0
                    for (i in start until end) sum += cleaned[i].toDouble() * cleaned[i]
                    sqrt(sum / (end - start)).toFloat()
                }
            } finally {
                cleaned.fill(0f)
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
        val decision = SoundTimeline.decide(audio)
        decision.problem?.let { return ClipAnalysisResult(problem = it) }
        if (shape.activeFraction < 0.04f) return ClipAnalysisResult(problem = "선택한 구간이 너무 조용해요. 다시 들어보고 아기 소리가 있는 구간을 골라 주세요.")
        val sound = decision.sound
        val eligiblePhrases = when (sound) {
            "울음" -> when {
                shape.rising -> listOf("으앙… 으아앙! 나 좀 봐주세요.", "으아앙… 여기 좀 봐줘요!", "응애! 잠깐 나 좀 봐줘요.")
                shape.activeFraction >= 0.65f -> listOf("으앙, 으앙… 나 좀 도와줘요.", "으앙… 잠깐 내 곁에 있어줘요.", "응애, 응애… 토닥토닥 해줄래요?")
                shape.bursts >= 3 -> listOf("응애… 응애… 여기 좀 봐주세요.", "으앙… 내 목소리 들려요?", "응애… 잠깐만 내 곁에 있어줘요.")
                else -> listOf("응애… 나 좀 봐주세요.", "으앙… 잠깐 나한테 와줄래요?", "응애… 여기 좀 봐줘요.")
            }
            "아기 웃음" -> if (shape.bursts >= 3) listOf("히히, 헤헤! 내 웃음 들려요?", "까르르! 웃음이 나왔어요.", "히히! 나랑 눈 맞추고 웃어요.")
                else listOf("헤헤! 같이 웃어요.", "히히, 웃음이 나요!", "헤헤, 나랑 같이 웃어줄래요?")
            "여러 아기 소리" -> listOf("내 소리 좀 들어줘요. 잠깐 곁에 있어줄래요?",
                "나 여기 있어요. 내 목소리에도 귀 기울여줘요.", "잠깐 나를 봐줘요. 내 소리, 들려요?")
            "어린 말소리" -> listOf("내 목소리 들려요? 나도 한마디 할래요.",
                "나도 소리 내볼래요. 잠깐 들어줄래요?", "내 얘기 들려요? 이번엔 내 차례예요.")
            else -> when {
                shape.bursts >= 3 -> listOf("아아, 옹알옹알! 내 얘기도 들어봐요.", "아아… 우우! 나도 얘기할래요.", "옹알옹알! 이번엔 내 차례예요.")
                shape.activeFraction >= 0.65f -> listOf("옹알옹알, 내 목소리예요!", "아아, 우우! 나도 소리 내볼래요.", "옹알옹알… 내 얘기 좀 들어봐요!")
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
        val timeline = decision.moments.filterIndexed { index, moment ->
            index == 0 || decision.moments[index - 1].event != moment.event
        }.joinToString(" → ") { "${ClipRange.clock(range.startMs + it.startMs)} 부근 ${it.event.label} 후보" }
        return ClipAnalysisResult(ClipInterpretation(phrase, sound,
            "${range.label()}에서 $sound 후보를 찾았어요. $rhythm\n$timeline" +
                (visualEvidence?.let { "\n${it.evidence}" } ?: "") +
                "\n일반 소리 모델의 관찰을 아이 시점으로 풀어 쓴 표현이에요. 아기 신원이나 배고픔·졸림의 원인은 확인할 수 없어요.",
            responseGuide(if (VocalEvent.CRY in decision.events) "울음" else sound), range.label(),
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
                // Invalid/empty decoder output must stop before rhythm filtering.
                audio.problem?.let { return@withContext ClipAnalysisResult(problem = it) }
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
