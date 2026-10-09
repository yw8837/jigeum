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
                                  val quietSpans: List<AcousticPause> = emptyList(),
                                  val strongestSecondRms: Double = 0.0) {
    companion object {
        fun from(pcm: FloatArray): AcousticShape {
            // Match the model's analysis filter. Removed hum/DC must not describe
            // an infant's volume or rhythm. Playback and the wording key use raw PCM.
            val cleaned = AudioCleanup.filter(pcm)
            val (strongestSecondRms, energy) = try {
                val level = AudioCleanup.strongestSecondRms(cleaned)
                level to (cleaned.indices step 1600).map { start ->
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
                first > 0.003 && last > first * 1.8, bursts, wordingKey, pauses, quietSpans, strongestSecondRms)
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
    /** A scene-level activity ratio is not a second sound classifier. For quiet
     * accepted input, repeat the unchanged event/persistence rules using only
     * audible model windows. Other speakers elsewhere cannot lend their volume.
     * This does not prove which person produced sound inside a model window. */
    private fun acceptedAudibleDecision(audio: SoundAnalysis, original: SoundDecision,
                                        durationMs: Long): SoundDecision? {
        fun audible(rms: Double?) = rms != null && rms.isFinite() && rms in .003..1.0
        if (original.broadWindows.isNotEmpty()) {
            return original.takeIf { it.broadWindows.all { window ->
                window.startMs >= 0 && window.endMs <= durationMs &&
                    window.endMs - window.startMs in 1_000..10_000 && audible(window.strongestSecondRms)
            } }
        }
        if (original.moments.isEmpty() || audio.frames.isEmpty() ||
            audio.frames.any { it.startMs !in 0 until durationMs } ||
            audio.frames.zipWithNext().any { (a, b) -> b.startMs <= a.startMs }) return null
        val measured = audio.copy(cues = emptyList(),
            frames = audio.frames.map { it.copy(cues = if (audible(it.windowRms)) it.cues else emptyList()) },
            babbleWindows = emptyList(), infantVoiceWindows = emptyList())
        val decision = SoundTimeline.decide(measured, durationMs)
        return decision.takeIf { it.problem == null && it.events.toSet() == original.events.toSet() }
    }

    // Conservative product gate, NOT a measured confidence/accuracy percentage.
    // Generic adult laughter/speech and a face alone cannot create a baby phrase.
    fun interpret(audio: SoundAnalysis, shape: AcousticShape, range: ClipRange,
                  visualEvidence: VisualEvidence? = null, audioTimelineAligned: Boolean = true): ClipAnalysisResult {
        if (audio.problem != null) return ClipAnalysisResult(problem = audio.problem, issue = audio.issue, visualEvidence = visualEvidence)
        val wholeDecision = SoundTimeline.decide(audio, range.durationMs)
        val originalDecision = if (wholeDecision.problem != null)
            SoundTimeline.partialObservation(audio, range.durationMs) ?: wholeDecision else wholeDecision
        originalDecision.problem?.let { return ClipAnalysisResult(problem = it, issue = originalDecision.issue, visualEvidence = visualEvidence) }
        // The existing input-silence gate measures the strongest second, while the
        // activity fraction is whole-scene energy. General presence has no localized
        // event rhythm; do not discard its audible clue with a second scene-level gate.
        // Reuse the unchanged .003 input floor, never lower model or infant thresholds.
        val audibleInput = shape.strongestSecondRms.isFinite() && shape.strongestSecondRms in .003..1.0
        val audibleGeneralPresence = (originalDecision.infantVoiceWindows.isNotEmpty() || originalDecision.partial) && audibleInput
        val decision = if (shape.activeFraction >= .04f || audibleGeneralPresence) originalDecision else
            if (audibleInput) acceptedAudibleDecision(audio, originalDecision, range.durationMs) else null
        if (!shape.activeFraction.isFinite() || shape.activeFraction !in 0f..1f || decision == null) return ClipAnalysisResult(
            problem = "선택한 구간이 너무 조용해요. 다시 들어보고 아기 소리가 있는 구간을 골라 주세요.",
            issue = AnalysisIssue.QUIET, visualEvidence = visualEvidence)
        val sound = decision.sound
        val broadOnly = decision.broadOnly
        // Whole-waveform rhythm may belong to a parent, music or another sound. A broad
        // AST event cannot attribute those bursts, rises or pauses to the baby.
        val vocalShape = if (broadOnly || decision.partial) shape.copy(activeFraction = 0f, rising = false, bursts = 0,
            pauses = 0, quietSpans = emptyList()) else shape
        val eligiblePhrases = BabyExpression.variants(decision.events.singleOrNull(), vocalShape, neutralOnly = decision.neutralOnly,
            infantPresence = decision.infantVoiceWindows.isNotEmpty() || decision.partial)
        val longPhrases = if (decision.partial) null else sustainedPhrases(audio, decision, shape, range)
        val phrases = longPhrases ?: eligiblePhrases
        val audioPhrase = (if (decision.partial) null else BabyExpression.orderedPhrase(audio, decision, range, shape.wordingKey))
            ?: phrases[Math.floorMod(shape.wordingKey, phrases.size)]
        val combined = VisualExpression.combine(audioPhrase, decision, range, visualEvidence, audioTimelineAligned)
        val rhythm = when {
            decision.partial && broadOnly -> "일부 시간창에서 아기 발성 단서를 찾았어요. 나머지는 소리 종류를 구분하지 못했어요. 반복 횟수나 전체 구간의 마음으로 해석하지 않았어요."
            decision.partial -> "일부 구간에서 아기 발성 단서가 반복됐어요. 다른 소리는 종류를 구분하지 못해 한 가지 소리나 마음으로 정하지 않았어요."
            broadOnly -> "넓은 시간창에서 ${if (decision.infantVoiceWindows.isEmpty()) "옹알이" else "아기 발성"} 단서를 살펴봤어요. 발성의 시작 시점과 반복 횟수는 확인하지 않았어요."
            shape.rising -> "뒤쪽에서 소리가 더 커졌어요."
            shape.bursts >= 3 -> "소리와 쉼이 여러 번 이어졌어요."
            shape.activeFraction >= 0.65f -> "소리가 비교적 길게 이어졌어요."
            else -> "짧게 들리는 소리를 살펴봤어요."
        }
        val timeline = if (decision.partial && !broadOnly) broadWindowEvidence(decision.moments.map {
            BabbleSupportWindow(it.startMs, minOf(it.startMs + SoundPhaseRecovery.FRAME_MS, range.durationMs))
        }, range, "아기 발성") else if (broadOnly) broadWindowEvidence(
            if (decision.infantVoiceWindows.isEmpty()) decision.broadWindows else decision.infantVoiceWindows.map {
                BabbleSupportWindow(it.startMs, it.endMs) }, range,
            if (decision.infantVoiceWindows.isEmpty()) "옹알이" else "아기 발성") else decision.moments.filterIndexed { index, moment ->
            index == 0 || decision.moments[index - 1].event != moment.event
        }.joinToString(" → ") { "${ClipRange.clock(range.startMs + it.startMs)} 부근 ${it.event.label} 후보" }
        return ClipAnalysisResult(ClipInterpretation(combined.phrase, sound,
            "${range.label()}에서 $sound 후보를 찾았어요. $rhythm\n$timeline" +
                (if (decision.infantVoiceWindows.any { it.kindUncertain })
                    "\n아기 발성 단서는 있지만, 웃음·울음·옹알이 중 어느 소리인지는 구분하지 않았어요." else "") +
                (visualEvidence?.let { "\n${it.evidence}" } ?: "") +
                (if (combined.usedVisual) "\n" + combined.evidence +
                    "\n화면의 입 모양을 함께 보고 답을 청하는 표현을 더했어요. 소리와 얼굴의 주인이 같거나 동시에 움직였는지는 확인하지 않아요." else "") +
                "\n소리 모델의 관찰을 아이 시점으로 풀어 쓴 표현이에요. 아기 신원이나 배고픔·졸림의 원인은 확인할 수 없어요.",
            responseGuide(if (VocalEvent.CRY in decision.events) "울음" else sound), range.label(),
            visualEvidence != null && visualEvidence.status != VisualEvidenceStatus.FAILED,
            visualEvidence?.notice, if (combined.usedVisual) "AUDIO_VISUAL" else "AUDIO"), visualEvidence = visualEvidence)
    }

    private fun broadWindowEvidence(windows: List<BabbleSupportWindow>, range: ClipRange, label: String): String {
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
            "${ClipRange.clock(range.startMs + it.startMs)}–${ClipRange.clock(range.startMs + it.endMs)} 범위 $label 후보"
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
        if (decision.broadOnly) return null
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
        return BabyExpression.variants(decision.events.singleOrNull(), shape, sustained = true, withPause = withPause,
            neutralOnly = decision.neutralOnly)
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
            val decoded = try { VideoAudioDecoder.decodeWithTiming(context, uri, range.startMs, range.endMs) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { decodeFailed = true; null }
                catch (_: LinkageError) { decodeFailed = true; null }
            val pcm = decoded?.samples
            try {
                var astForPresence: AstAudioAnalysis? = null
                val audio = if (decodeFailed) SoundAnalysis(emptyList(),
                    "이 파일의 소리를 읽지 못했어요. 다른 파일이나 형식으로 다시 시도해 주세요.", issue = AnalysisIssue.INVALID_AUDIO)
                    else if (pcm == null) SoundAnalysis(emptyList(),
                    "이 영상에는 읽을 수 있는 소리가 없어요. 아기 소리가 담긴 영상이나 녹음을 골라 주세요.", issue = AnalysisIssue.NO_AUDIO)
                    else SoundAnalyzer.analyzeWithSupport(context, pcm) { primaryAudio ->
                        if ((BabbleSupportPolicy.ENABLED || BuildConfig.AST_INTEGRATED_CANDIDATE ||
                            BuildConfig.AST_BABBLE_SUPPORT) && (BabbleSupportPolicy.shouldAnalyze(primaryAudio) ||
                            (BuildConfig.AST_INFANT_PRESENCE &&
                                InfantVoiceSupportPolicy.shouldCorroborate(primaryAudio, pcm.size / 16L)))) {
                            val ast = AstAudioAnalyzer.analyze(context, pcm)
                            if (BuildConfig.AST_INFANT_PRESENCE) astForPresence = ast
                            BabbleSupportPolicy.attachCandidate(primaryAudio, ast, pcm.size / 16L)
                        } else primaryAudio
                    }.let { recovered ->
                        // Preserve established AST and shifted YAMNet approvals first.
                        // General presence is the last supplement and reuses the same run.
                        if (astForPresence != null) InfantVoiceSupportPolicy.attachCandidate(recovered,
                            astForPresence!!, pcm.size / 16L) else recovered
                    }
                // Visual observation remains available on abstention, but never authorizes a phrase.
                val shape = if (pcm != null && audio.problem == null) AcousticShape.from(pcm) else AcousticShape(0f, false, 0)
                ensureActive()
                val frames = if (visual) inspectVideoEvidence(context, uri, range.startMs, range.endMs) else null
                ensureActive()
                val result = EvidenceInterpreter.interpret(audio, shape, range, frames,
                    audioTimelineAligned = decoded?.selectionTimelineAligned == true)
                if (pcm != null && audio.problem == null && result.interpretation == null) {
                    val cleaned = AudioCleanup.filter(pcm)
                    try {
                        val rms = AudioCleanup.strongestSecondRms(cleaned)
                        val quiet = result.issue == AnalysisIssue.INFANT_UNCONFIRMED && rms < .012
                        result.copy(problem = if (quiet) "소리가 작게 담겼어요. 아기 가까이에서 다시 찍어 보세요." else result.problem,
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
    data class Result(val phrase: String, val usedVisual: Boolean, val evidence: String = "")
    fun combine(phrase: String, decision: SoundDecision, range: ClipRange, visual: VisualEvidence?,
                audioTimelineAligned: Boolean = true): Result {
        if (visual == null || visual.status == VisualEvidenceStatus.FAILED || decision.problem != null || decision.partial) return Result(phrase, false)
        if (visual.frames.any { it.presentationOffsetMs != null }) {
            if (!audioTimelineAligned) return Result(phrase, false)
            val matches = VisualSoundAlignment.find(decision, visual.frames, range.durationMs)
            if (matches.isEmpty()) return Result(phrase, false)
            val cues = matches.map { it.cue }.distinct()
            val addition = when {
                cues.size > 1 -> " 얼굴도 마주 보면서 내 소리에 답해줄래요?"
                cues.single() == VisualSoundAlignment.Cue.CORNERS -> " 웃는 얼굴로 답해줄래요?"
                else -> " 입 모양도 보면서, 내 소리에 답해줄래요?"
            }
            fun seconds(at: Long) = "%.1f".format(java.util.Locale.ROOT, (range.startMs + at) / 1000.0)
                .trimEnd('0').trimEnd('.')
            val evidence = matches.joinToString(" · ") {
                "${seconds(it.startMs)}~${seconds(it.endMs)}초 ${it.cue.label}"
            } + if (decision.broadOnly)
                "을 넓은 소리 후보 범위 안에서 관찰했어요. 이 시각에 발성했거나 화면의 사람이 소리를 냈다는 뜻은 아니에요."
            else "을 같은 시간대의 소리 후보 창과 함께 살펴봤어요. 정확한 발성 시점·입술 동기는 확인하지 않아요."
            return Result(phrase + addition, true, evidence)
        }
        // Legacy observations lack decoded PTS. Preserve their old request-only
        // display rule, without manufacturing temporal correspondence evidence.
        if (decision.broadOnly) return Result(phrase, false)
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
