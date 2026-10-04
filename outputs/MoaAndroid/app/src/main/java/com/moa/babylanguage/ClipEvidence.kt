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
        require(startMs >= 0 && endMs <= 120_500 && durationMs in 1_000..30_000)
    }
    fun label() = "${clock(startMs)}–${clock(endMs)}"
    companion object {
        fun clock(ms: Long): String = "%d:%02d".format(java.util.Locale.ROOT, ms / 60_000, ms / 1000 % 60)
    }
}

/** Acoustic observations, not inferred needs. No random/session-dependent rewriting. */
internal data class AcousticPause(val startMs: Long, val endMs: Long)
internal data class AcousticShape(val activeFraction: Float, val rising: Boolean, val bursts: Int,
                                  val wordingKey: Int = 0, val pauses: Int = 0,
                                  val quietSpans: List<AcousticPause> = emptyList()) {
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
            // Count only a quiet stretch >=300 ms between audible stretches. A
            // classifier gap alone does not establish that the baby paused.
            var heardSound = false
            var quietWindows = 0
            var pauses = 0
            val quietSpans = mutableListOf<AcousticPause>()
            for ((index, isActive) in active.withIndex()) {
                if (isActive) {
                    if (heardSound && quietWindows >= 3) {
                        pauses++
                        quietSpans += AcousticPause((index - quietWindows) * 100L, index * 100L)
                    }
                    heardSound = true
                    quietWindows = 0
                } else if (heardSound) quietWindows++
            }
            val half = (energy.size / 2).coerceAtLeast(1)
            val first = energy.take(half).average()
            val last = energy.takeLast(half).average()
            // Deterministic surface wording only. This key is not a semantic/intent feature
            // and is neither stored nor sent anywhere. Equivalent phrases share one guard.
            var wordingKey = 17
            for (i in pcm.indices step 160) wordingKey = wordingKey * 31 + (pcm[i] * 32767).toInt()
            return AcousticShape(active.count { it }.toFloat() / active.size.coerceAtLeast(1),
                first > 0.003 && last > first * 1.8, bursts, wordingKey, pauses, quietSpans)
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
    val visualNotice: String? = null,
    val basis: String = "LEGACY"
)

internal data class ClipAnalysisResult(val interpretation: ClipInterpretation? = null, val problem: String? = null,
    val issue: AnalysisIssue? = null, val visualEvidence: VisualEvidence? = null,
    val suggestedRange: ClipRange? = null)

internal object EvidenceInterpreter {
    // Conservative product gate, NOT a measured confidence/accuracy percentage.
    // Generic adult laughter/speech and a face alone cannot create a baby phrase.
    fun interpret(audio: SoundAnalysis, shape: AcousticShape, range: ClipRange,
                  visualEvidence: VisualEvidence? = null): ClipAnalysisResult {
        if (audio.problem != null) return ClipAnalysisResult(problem = audio.problem, issue = audio.issue, visualEvidence = visualEvidence)
        val decision = SoundTimeline.decide(audio)
        decision.problem?.let { return ClipAnalysisResult(problem = it, issue = decision.issue, visualEvidence = visualEvidence) }
        if (!shape.activeFraction.isFinite() || shape.activeFraction < 0.04f) return ClipAnalysisResult(
            problem = "선택한 구간이 너무 조용해요. 다시 들어보고 아기 소리가 있는 구간을 골라 주세요.",
            issue = AnalysisIssue.QUIET, visualEvidence = visualEvidence)
        val sound = decision.sound
        val broadOnly = decision.moments.isEmpty() && decision.broadWindows.isNotEmpty()
        // Whole-waveform rhythm may belong to a parent, music or another sound. A broad
        // AST event cannot attribute those bursts, rises or pauses to the baby.
        val vocalShape = if (broadOnly) shape.copy(activeFraction = 0f, rising = false, bursts = 0,
            pauses = 0, quietSpans = emptyList()) else shape
        val eligiblePhrases = when (sound) {
            "울음" -> when {
                shape.rising -> listOf("응애… 으아앙! 내 소리 좀 들어줘요.", "으아앙… 나 여기 있어요! 잠깐 나 좀 봐줘요.", "응애! 나 여기 있어요. 내 곁에 와줄래요?")
                shape.activeFraction >= 0.65f -> listOf("으앙, 으앙… 나 여기 있어요. 잠깐 내 곁에 있어줘요.", "응애, 응애… 내 소리 들리죠? 토닥토닥 해줄래요?", "으앙… 아직 내 소리 들리죠? 부드럽게 말 걸어주세요.")
                shape.bursts >= 3 -> listOf("응애… 또 응애. 내 소리 좀 들어줘요.", "으앙… 내 목소리 들려요? 잠깐 나 좀 봐줘요.", "응애… 응애… 나 여기 있어요. 잠깐만 곁에 있어줘요.")
                else -> listOf("응애… 나 여기 있어요. 잠깐 나 좀 봐줘요.", "으앙… 내 소리 들리죠? 잠깐 나한테 와줄래요?", "응애… 여기 좀 봐줘요. 내 곁에 있어줄래요?")
            }
            "아기 웃음" -> if (shape.bursts >= 3) listOf("히히, 헤헤! 내 웃음 들려요? 나랑 같이 웃어요.", "까르르! 또 웃음이 나왔어요. 같이 웃어줄래요?", "히히! 웃음이 자꾸 나요. 나랑 눈 맞추고 웃어요.")
                else listOf("헤헤! 나도 웃음이 나요. 같이 웃어요.", "히히, 웃음이 나왔어요! 나랑 눈 맞춰줄래요?", "헤헤, 내 웃음 들리죠? 나랑 같이 웃어줄래요?")
            "여러 아기 소리" -> listOf("내 소리 좀 들어줘요. 잠깐 곁에 있어줄래요?",
                "나 여기 있어요. 내 목소리에도 귀 기울여줘요.", "잠깐 나를 봐줘요. 내 소리, 들려요?")
            "어린 말소리" -> listOf("내 목소리 들려요? 나도 한마디 할래요.",
                "나도 소리 내볼래요. 잠깐 들어줄래요?", "내 얘기 들려요? 이번엔 내 차례예요.")
            else -> when {
                vocalShape.bursts >= 3 -> listOf("아아, 옹알옹알! 내 얘기도 들어봐요. 이번엔 내 차례예요.", "아아… 우우! 나도 얘기할래요. 내 소리에도 답해줄래요?", "옹알옹알! 또 내 소리예요. 내 얘기도 들어봐요.")
                vocalShape.activeFraction >= 0.65f -> listOf("옹알옹알, 내 목소리예요! 내 얘기도 들어봐요.", "아아, 우우! 나도 소리 내볼래요. 이번엔 내 차례예요.", "옹알옹알… 아직 내 얘기 남았어요! 조금 더 들어줄래요?")
                else -> listOf("아아, 우우… 내 얘기 들려요? 나도 한마디 할래요.", "옹알… 이번엔 내 차례예요. 내 소리에도 답해줄래요?", "아아… 내 목소리 들리죠? 내 얘기도 들어봐요.")
            }
        }
        val longPhrases = sustainedPhrases(audio, decision, shape, range)
        val phrases = longPhrases ?: eligiblePhrases
        val audioPhrase = phrases[Math.floorMod(shape.wordingKey, phrases.size)]
        val combined = VisualExpression.combine(audioPhrase, decision, range, visualEvidence)
        val rhythm = when {
            broadOnly -> "넓은 시간창에서 옹알이 단서를 살펴봤어요. 발성의 시작 시점과 반복 횟수는 확인하지 않았어요."
            shape.rising -> "뒤쪽에서 소리가 더 커졌어요."
            shape.bursts >= 3 -> "소리와 쉼이 여러 번 이어졌어요."
            shape.activeFraction >= 0.65f -> "소리가 비교적 길게 이어졌어요."
            else -> "짧게 들리는 소리를 살펴봤어요."
        }
        val timeline = if (broadOnly) broadWindowEvidence(decision.broadWindows, range) else decision.moments.filterIndexed { index, moment ->
            index == 0 || decision.moments[index - 1].event != moment.event
        }.joinToString(" → ") { "${ClipRange.clock(range.startMs + it.startMs)} 부근 ${it.event.label} 후보" }
        return ClipAnalysisResult(ClipInterpretation(combined.phrase, sound,
            "${range.label()}에서 $sound 후보를 찾았어요. $rhythm\n$timeline" +
                (visualEvidence?.let { "\n${it.evidence}" } ?: "") +
                (if (combined.usedVisual) "\n화면의 입 모양을 함께 보고 답을 청하는 표현을 더했어요. 소리와 얼굴의 주인이 같거나 동시에 움직였는지는 확인하지 않아요." else "") +
                "\n일반 소리 모델의 관찰을 아이 시점으로 풀어 쓴 표현이에요. 아기 신원이나 배고픔·졸림의 원인은 확인할 수 없어요.",
            responseGuide(if (VocalEvent.CRY in decision.events) "울음" else sound), range.label(),
            visualEvidence != null && visualEvidence.status != VisualEvidenceStatus.FAILED,
            visualEvidence?.notice, if (combined.usedVisual) "AUDIO_VISUAL" else "AUDIO"), visualEvidence = visualEvidence)
    }

    private fun broadWindowEvidence(windows: List<BabbleSupportWindow>, range: ClipRange): String {
        // Merge overlap for readable ranges, never interpret overlapping windows as
        // repeated utterances or as uninterrupted speech over their union.
        val merged = mutableListOf<BabbleSupportWindow>()
        for (window in windows.sortedBy { it.startMs }) {
            val last = merged.lastOrNull()
            if (last != null && window.startMs <= last.endMs) {
                merged[merged.lastIndex] = last.copy(endMs = maxOf(last.endMs, window.endMs))
            } else merged += window
        }
        return merged.joinToString(" · ") {
            "${ClipRange.clock(range.startMs + it.startMs)}–${ClipRange.clock(range.startMs + it.endMs)} 범위 옹알이 후보"
        } + " (범위 내 정확한 발성 위치와 지속 여부는 확인하지 않아요.)"
    }

    fun responseGuide(sound: String): String = if (sound == "울음")
        "먼저 편안한지, 수유·잠·기저귀 등 최근 돌봄을 살펴주세요. 곁에서 부드럽게 말을 걸어 주세요."
    else "아기 소리가 끝나면 잠깐 기다렸다가 눈을 맞추고 짧게 답해 주세요. 다시 소리를 내면 차례를 주고받아 보세요."

    /** Presentation after the unchanged sound gate, never a reason to accept a clip.
     * A longer selection alone is insufficient: require repeated accepted windows
     * across most of its duration plus audible activity. Padding a short event with
     * silence or unrelated sound must keep its original short expression. These
     * are bounded character expressions, not a transcript or inferred needs. */
    private fun sustainedPhrases(audio: SoundAnalysis, decision: SoundDecision,
                                 shape: AcousticShape, range: ClipRange): List<String>? {
        if (decision.broadWindows.isNotEmpty()) return null
        if (range.durationMs <= 10_000 || !shape.activeFraction.isFinite() || shape.activeFraction < .35f) return null
        val frameStarts = audio.frames.map { it.startMs }
            .filter { it >= 0 && it < range.durationMs }.distinct().toSet()
        val supported = decision.moments.filter { it.startMs in frameStarts }
            .distinctBy { it.startMs }.sortedBy { it.startMs }
        val minimumWindows = maxOf(6, ((range.durationMs + 1_999) / 2_000).toInt())
        if (supported.size < minimumWindows || supported.size < frameStarts.size * .60f ||
            supported.last().startMs - supported.first().startMs <= 10_000 ||
            supported.last().startMs - supported.first().startMs < range.durationMs * .55f) return null

        // Whole-waveform silence may separate a parent from the first baby sound.
        // Require accepted baby observations immediately around the measured gap;
        // a global pause count or a classifier gap alone cannot support this wording.
        val orderedStarts = frameStarts.sorted()
        val supportedStarts = supported.map { it.startMs }.toSet()
        val withPause = shape.quietSpans.any { gap ->
            gap.startMs >= 0 && gap.endMs <= range.durationMs && gap.endMs - gap.startMs >= 300 &&
                orderedStarts.lastOrNull { it < gap.startMs } in supportedStarts &&
                orderedStarts.firstOrNull { it >= gap.endMs } in supportedStarts
        }
        return when (decision.events.singleOrNull()) {
            VocalEvent.LAUGH -> if (withPause) listOf(
                "히히! 잠깐 쉬었다가… 또 웃음이 나와요. 까르르, 나랑 같이 웃어줄래요?",
                "헤헤, 웃음이 나요! 조금 쉬고 또 히히. 내 웃음 들리죠? 같이 웃어요.")
            else listOf(
                "헤헤, 웃음이 자꾸 나와요! 히히, 또 웃었네. 나랑 눈 맞추고 같이 웃어줄래요?",
                "히히, 헤헤! 웃음이 계속 이어져요. 또 웃음이 나왔네. 내 얼굴 보면서 같이 웃어요!")
            VocalEvent.CRY -> when {
                withPause -> listOf(
                    "응애… 조금 쉬었다가 또 으앙. 내 목소리 들리죠? 잠깐 곁에서 토닥토닥 해줄래요?",
                    "으앙… 잠깐 조용해졌다가 또 소리가 나왔어요. 응애, 내 곁에 있어줘요. 부드럽게 말 걸어줄래요?")
                shape.rising -> listOf(
                    "응애… 으아앙! 소리가 점점 커졌네. 나 여기 있어요. 잠깐 내 곁에 있어줄래요?",
                    "으앙… 으아앙! 점점 더 크게 소리가 나와요. 내 얘기도 들어줘요. 곁에서 부드럽게 말 걸어줄래요?")
                else -> listOf(
                    "으앙, 으앙… 내 목소리 계속 들리죠? 아직 으아앙… 나 여기 있어요. 곁에서 부드럽게 말 걸어줄래요?",
                    "응애, 응애… 소리가 자꾸 이어져요. 내 얘기도 조금 더 들어줘요. 잠깐 곁에서 토닥토닥 해줄래요?")
            }
            VocalEvent.BABBLE -> if (withPause) listOf(
                "아아, 우우… 잠깐 쉬었다가 또 얘기할래요. 옹알옹알! 내 얘기 아직 안 끝났어요. 조금 더 들어줄래요?",
                "옹알옹알, 내 얘기 들어봐요! 조금 쉬고… 아아, 우우. 다시 내 차례예요. 내 소리가 끝나면 답해줄래요?")
            else listOf(
                "아아, 우우… 내 얘기 아직 안 끝났어요! 옹알옹알, 또 내 소리예요. 조금 더 들어줄래요? 다 듣고 나면 답해주세요.",
                "옹알옹알, 나도 할 얘기가 많아요! 아아, 우우… 아직 내 차례예요. 끝까지 듣고 나면 내 소리에 답해줄래요?")
            VocalEvent.CHILD_VOICE -> if (withPause) listOf(
                "내 얘기 들려요? 잠깐 쉬었다가 다시 얘기할래요. 아직 내 차례예요. 다 듣고 나면 이번엔 답해줄래요?",
                "나도 소리 내볼래요. 조금 쉬고, 또 내 목소리예요! 끝까지 들어주고 나면 같이 얘기해요.")
            else listOf(
                "내 목소리 들리죠? 나도 할 얘기가 많아요. 조금 더 들어줘요. 내 소리가 끝나면 답해줄래요?",
                "나도 한마디, 또 한마디 할래요. 내 얘기 아직 안 끝났어요! 조금 더 들어주고 나한테도 답해주세요.")
            null -> if (withPause) listOf(
                "내 얘기 들어봐요. 잠깐 쉬고… 다시 내 차례예요. 이렇게도, 저렇게도 소리 내볼래요. 끝까지 들어줄래요?",
                "내 소리 조금 더 들어줘요. 잠깐 쉬고… 또 소리를 내봐요. 이랬다가 저랬다가, 내 소리도 여러 가지예요.")
            else listOf(
                "나 여기 있어요. 이렇게도, 저렇게도 소리 내볼래요. 내 얘기 아직 남았어요. 조금만 더 들어줄래요?",
                "아직 내 차례예요. 이 소리도, 저 소리도 내볼래요. 내 얘기 조금 더 들어줘요. 다 듣고 나면 답해줄래요?")
        }
    }

}

/** All decoders receive exactly the range previewed by the caregiver. */
internal interface ClipAnalyzer {
    suspend fun analyze(context: Context, uri: Uri, range: ClipRange, visual: Boolean): ClipAnalysisResult
}

internal object DeviceClipAnalyzer : ClipAnalyzer {
    override suspend fun analyze(context: Context, uri: Uri, range: ClipRange, visual: Boolean): ClipAnalysisResult =
        withContext(Dispatchers.Default) {
            var decodeFailed = false
            val pcm = try { VideoAudioDecoder.decode(context, uri, range.startMs, range.endMs) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { decodeFailed = true; null }
                catch (_: LinkageError) { decodeFailed = true; null }
            try {
                val primaryAudio = if (decodeFailed) SoundAnalysis(emptyList(),
                    "이 파일의 소리를 읽지 못했어요. 다른 파일이나 형식으로 다시 시도해 주세요.", issue = AnalysisIssue.INVALID_AUDIO)
                    else if (pcm == null) SoundAnalysis(emptyList(),
                    "이 영상에는 읽을 수 있는 소리가 없어요. 아기 소리가 담긴 영상이나 녹음을 골라 주세요.", issue = AnalysisIssue.NO_AUDIO)
                    else SoundAnalyzer.analyzeDetailed(context, pcm)
                val audio = if ((BabbleSupportPolicy.ENABLED || BuildConfig.AST_INTEGRATED_CANDIDATE ||
                    BuildConfig.AST_BABBLE_SUPPORT) &&
                    pcm != null && BabbleSupportPolicy.shouldAnalyze(primaryAudio)) {
                    val ast = AstAudioAnalyzer.analyze(context, pcm)
                    BabbleSupportPolicy.attachCandidate(primaryAudio, ast, pcm.size / 16L)
                } else primaryAudio
                // Visual observation remains available on abstention, but never authorizes a phrase.
                val shape = if (pcm != null && audio.problem == null) AcousticShape.from(pcm) else AcousticShape(0f, false, 0)
                ensureActive()
                val frames = if (visual) inspectVideoEvidence(context, uri, range.startMs, range.endMs) else null
                ensureActive()
                val result = EvidenceInterpreter.interpret(audio, shape, range, frames)
                if (pcm != null && audio.problem == null && result.interpretation == null) {
                    val cleaned = AudioCleanup.filter(pcm)
                    try {
                        val rms = AudioCleanup.strongestSecondRms(cleaned)
                        val quiet = result.issue == AnalysisIssue.INFANT_UNCONFIRMED && rms < .012
                        result.copy(problem = if (quiet) "소리가 작게 담겼고 아기 발성 단서를 충분히 찾지 못했어요. 선택 구간을 다시 듣고, 다음 촬영은 조금 가까이에서 해주세요." else result.problem,
                            issue = if (quiet) AnalysisIssue.QUIET else result.issue,
                            suggestedRange = AudibleRange.suggest(cleaned, range))
                    } finally { cleaned.fill(0f) }
                } else result
            } finally { pcm?.fill(0f) }
        }
}

/** A display rule after the audio gate; neither face identity nor lip sync is inferred.
 * Frames are sparse observations of the selection, not synchronized speaking evidence.
 * Wording asks for a response; it never attributes the visible person's action to the baby. */
internal object VisualExpression {
    data class Result(val phrase: String, val usedVisual: Boolean)
    fun combine(phrase: String, decision: SoundDecision, range: ClipRange, visual: VisualEvidence?): Result {
        if (visual == null || visual.status == VisualEvidenceStatus.FAILED || decision.problem != null) return Result(phrase, false)
        val event = decision.events.singleOrNull() ?: return Result(phrase, false)
        // Multiple visible people anywhere in the selection make attribution too ambiguous.
        if (visual.frames.any { it.faceCount > 1 }) return Result(phrase, false)
        val supported = visual.frames.filter { frame ->
            frame.valid && frame.faceCount == 1 &&
                (frame.presentationOffsetMs ?: frame.requestedOffsetMs) in 0 until range.durationMs
        }.distinctBy { it.presentationOffsetMs ?: it.requestedOffsetMs }
        if (supported.size < 2) return Result(phrase, false)
        fun Float?.above(floor: Float) = this != null && isFinite() && this in floor..1f
        val addition = when {
            event == VocalEvent.LAUGH && supported.count { it.mouthCornerLift.above(.60f) } >= 2 -> " 웃는 얼굴로 답해줄래요?"
            event in setOf(VocalEvent.BABBLE, VocalEvent.CHILD_VOICE) &&
                (visual.timeline == null || (visual.timeline.coversSelection && visual.timeline.mouthTransitions >= 2)) &&
                supported.count { it.mouthOpening.above(.18f) } >= 2 -> " 입 모양도 보면서, 내 소리에 답해줄래요?"
            else -> return Result(phrase, false)
        }
        return Result(phrase + addition, true)
    }
}

/** Finds louder sound, not a baby. Uses only the selected PCM and never starts analysis. */
internal object AudibleRange {
    fun suggest(cleanedPcm: FloatArray, range: ClipRange): ClipRange? {
        if (range.durationMs < 6_000 || cleanedPcm.size < 96_000 || cleanedPcm.any { !it.isFinite() }) return null
        val energies = (cleanedPcm.indices step 1600).map { start ->
            val end = minOf(start + 1600, cleanedPcm.size)
            cleanedPcm.sliceArray(start until end).sumOf { it.toDouble() * it } / (end - start)
        }
        val width = 50 // Five seconds, within both free and full limits.
        if (energies.size < width) return null
        val sums = energies.windowed(width).map { it.sum() / width }
        val best = sums.indices.maxByOrNull { sums[it] } ?: return null
        if (sums[best] < .003 * .003 || sums[best] < energies.average() * 1.5) return null
        val start = range.startMs + best * 100L
        val end = minOf(start + 5_000, range.endMs)
        return if (end - start >= 1_000) ClipRange(start, end) else null
    }
}
