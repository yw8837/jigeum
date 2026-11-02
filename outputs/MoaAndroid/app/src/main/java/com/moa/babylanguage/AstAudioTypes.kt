package com.moa.babylanguage

/** General multilabel scores over the complete window, never localized event timestamps.
 * Shared with the dormant candidate policy; no model/runtime dependency belongs here. */
internal data class AstAudioWindow(val startMs: Long, val endMs: Long, val scores: Map<String, Float>,
                                   val infantPresence: Float? = null, val strongestSecondRms: Double? = null,
                                   val captionPresence: Float? = null)
internal data class AstAudioAnalysis(val windows: List<AstAudioWindow>, val problem: String? = null)
