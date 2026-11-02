package com.moa.babylanguage

/** The evidence used for this sentence, independently of its recording/media source. */
internal object ExpressionBasis {
    const val AUDIO = "AUDIO"
    const val AUDIO_VISUAL = "AUDIO_VISUAL"
    const val CAREGIVER = "CAREGIVER"
    const val AUDIO_PLAYFUL = "AUDIO_PLAYFUL"
    const val AUDIO_VISUAL_PLAYFUL = "AUDIO_VISUAL_PLAYFUL"
    const val LEGACY = "LEGACY"

    fun normalize(value: String?): String = when (value) {
        AUDIO, AUDIO_VISUAL, AUDIO_PLAYFUL, AUDIO_VISUAL_PLAYFUL, CAREGIVER -> value
        else -> LEGACY
    }

    fun label(value: String?): String = when (normalize(value)) {
        AUDIO -> "소리를 바탕으로 예상한 아기말"
        AUDIO_VISUAL -> "소리와 표정을 바탕으로 예상한 아기말"
        CAREGIVER -> "보호자가 수정한 아기말"
        AUDIO_PLAYFUL -> "소리를 참고해 상상한 아가말"
        AUDIO_VISUAL_PLAYFUL -> "소리와 장면을 참고해 상상한 아가말"
        else -> "이전에 남긴 아기말 · 분석 근거 미기록"
    }

    fun forPhrase(basis: String, original: String, displayed: String): String =
        if (original.trim() == displayed.trim()) normalize(basis) else CAREGIVER
}
