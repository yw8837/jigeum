package com.moa.babylanguage

/** Automatic creative wording after sound acceptance. This is NOT an infant emotion
 * classifier. Keep observed sound, timing, care guide and provenance beside the fiction. */
internal object AutomaticBabySpeech {
    fun apply(observed: ClipAnalysisResult, decision: SoundDecision, shape: AcousticShape,
              range: ClipRange, visual: VisualEvidence?, audioTimelineAligned: Boolean): ClipAnalysisResult {
        val original = observed.interpretation ?: return observed
        if (decision.problem != null || decision.partial || decision.broadOnly) return observed
        // neutralOnly also includes localized, accepted babble/child voice. Those may
        // receive explicitly imaginative speech; general presence has no single event.
        // Preserve mixed-event ordering instead of assigning one feeling to a whole clip.
        val event = decision.events.singleOrNull() ?: return observed
        val sleepyShape = event in setOf(VocalEvent.BABBLE, VocalEvent.CHILD_VOICE) &&
            audioTimelineAligned && hasYawnLikeSequence(visual, range.durationMs)
        val themes = when {
            sleepyShape -> setOf("sleepy")
            event == VocalEvent.CRY -> setOf("pout", "bothered")
            event == VocalEvent.LAUGH -> setOf("excited")
            event in setOf(VocalEvent.BABBLE, VocalEvent.CHILD_VOICE) -> setOf("playful", "affection")
            else -> return observed
        }
        // A PCM-derived key varies authored wording inside an allowed sound context.
        // It never selects sleep or supplies an emotion confidence score.
        val candidates = AutomaticBabySpeechCatalog.themes.filter { it.id in themes }
            .flatMap { it.phrases }.filter { original.phrase.length + 1 + it.length <= 160 }
        if (candidates.isEmpty()) return observed
        val key = Math.floorDiv(shape.wordingKey, BabyExpressionCatalog.crying.phrases.size)
        val imagined = candidates[Math.floorMod(key, candidates.size)]
        val hasVisual = original.basis == ExpressionBasis.AUDIO_VISUAL || sleepyShape
        val note = if (sleepyShape)
            "같은 영상 구간에서 눈 감김 계수와 입 벌림이 함께 지속됐다가 돌아오는 모양을 관찰했어요. 하품·졸림·화자 일치는 확인한 것이 아니며, 이를 소재로 상상한 표현이에요."
        else "관찰한 소리 종류에 어울리는 장난·투정 말투를 자동으로 붙였어요. 귀찮음·기쁨 같은 실제 마음을 판별한 결과는 아니에요."
        return observed.copy(interpretation = original.copy(
            phrase = original.phrase + " " + imagined,
            basis = if (hasVisual) ExpressionBasis.AUDIO_VISUAL_PLAYFUL else ExpressionBasis.AUDIO_PLAYFUL,
            evidence = original.evidence + "\n" + note
        ))
    }

    /** A bounded image-shape rule, not a clinical yawn/sleep label. Require a full
     * single-face sequence, real increasing PTS and paired before/after observations.
     * A blink, still-open mouth, model gap or reused frame cannot satisfy it. */
    internal fun hasYawnLikeSequence(visual: VisualEvidence?, durationMs: Long): Boolean {
        if (visual?.status != VisualEvidenceStatus.COMPLETE || durationMs !in 3_000..20_000) return false
        val frames = visual.frames
        if (!VisualTimeline.summarize(frames, durationMs).coversSelection || frames.size < 11) return false
        fun coefficient(value: Float?) = value != null && value.isFinite() && value in 0f..1f
        if (frames.any { !it.valid || it.faceCount != 1 || !coefficient(it.eyeClosure) || !coefficient(it.mouthOpening) }) return false
        fun resting(frame: VisualFrameObservation) = frame.eyeClosure!! <= .35f && frame.mouthOpening!! <= .12f
        fun widened(frame: VisualFrameObservation) = frame.eyeClosure!! >= .65f && frame.mouthOpening!! >= .32f
        var start = 0
        while (start < frames.size) {
            if (!widened(frames[start])) { start++; continue }
            var end = start
            while (end + 1 < frames.size && widened(frames[end + 1])) end++
            val before = frames.subList(maxOf(0, start - 2), start)
            val after = frames.subList(end + 1, minOf(frames.size, end + 3))
            if (end - start + 1 >= 4 &&
                frames[end].presentationOffsetMs!! - frames[start].presentationOffsetMs!! >= 1_200 &&
                before.size == 2 && after.size == 2 && before.all(::resting) && after.all(::resting) &&
                frames[start].presentationOffsetMs!! - before.first().presentationOffsetMs!! <= 750 &&
                after.last().presentationOffsetMs!! - frames[end].presentationOffsetMs!! <= 750) return true
            start = end + 1
        }
        return false
    }
}
