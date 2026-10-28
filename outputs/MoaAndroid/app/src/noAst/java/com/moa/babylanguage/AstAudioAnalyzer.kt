package com.moa.babylanguage

import android.content.Context
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Normal builds do not contain AST weights or its native research runtime. */
internal object AstAudioAnalyzer {
    @Suppress("UNUSED_PARAMETER")
    suspend fun analyze(context: Context, rawPcm: FloatArray): AstAudioAnalysis {
        coroutineContext.ensureActive()
        return AstAudioAnalysis(emptyList(), "AST research runtime is unavailable in this build")
    }
}
