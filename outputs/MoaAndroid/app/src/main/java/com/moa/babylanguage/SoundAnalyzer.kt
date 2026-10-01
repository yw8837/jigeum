package com.moa.babylanguage

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

data class SoundCue(val label: String, val score: Float)
data class SoundAnalysis(val cues: List<SoundCue>, val problem: String? = null)

/** YAMNet recognizes general sound events. These are suggestions, never interpretations of needs. */
object SoundAnalyzer {
    private val inferenceLock = Mutex()

    /** Match the bundled model's labels exactly: "speech babble" is crowd noise, not a baby. */
    internal fun cueLabel(categoryName: String): String? = when (categoryName.lowercase(java.util.Locale.ROOT)) {
        "baby cry, infant cry" -> "울음"
        "baby laughter" -> "아기 웃음 후보"
        "babbling" -> "옹알이 후보"
        "laughter", "giggle", "chuckle, chortle" -> "웃음(화자 미확인)"
        "screaming" -> "비명(화자 미확인)"
        "crying, sobbing", "whimper" -> "울음(화자 미확인)"
        "speech", "child speech, kid speaking", "conversation", "hubbub, speech noise, speech babble" -> "말소리(화자 미확인)"
        else -> null
    }

    suspend fun analyze(context: Context, pcm: FloatArray): List<SoundCue> =
        analyzeDetailed(context, pcm).cues

    suspend fun analyzeDetailed(context: Context, pcm: FloatArray): SoundAnalysis = inferenceLock.withLock {
        coroutineContext.ensureActive()
        if (pcm.size < 16_000) return@withLock SoundAnalysis(emptyList(),
            "구간이 너무 짧아요. 아기 소리가 담긴 구간을 1초 이상 골라 주세요.")
        val rms = kotlin.math.sqrt(pcm.sumOf { it.toDouble() * it } / pcm.size)
        if (!rms.isFinite() || rms < 0.003) return@withLock SoundAnalysis(emptyList(),
            "선택한 구간이 거의 무음이에요. 다시 들어보고 소리가 있는 구간을 골라 주세요.")
        try {
            val options = AudioClassifier.AudioClassifierOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("yamnet.tflite").build())
                .setMaxResults(20)
                .build()
            AudioClassifier.createFromOptions(context, options).use { classifier ->
                val format = AudioData.AudioDataFormat.builder()
                    .setNumOfChannels(1)
                    .setSampleRate(16_000f)
                    .build()
                val audio = AudioData.create(format, pcm.size)
                audio.load(pcm)
                val scored = classifier.classify(audio).classificationResults()
                    .flatMap { it.classifications() }
                    .flatMap { it.categories() }
                    .mapNotNull { category ->
                        val label = cueLabel(category.categoryName())
                        label?.let { SoundCue(it, category.score()) }
                    }
                val cues = scored.groupBy { it.label }.map { (label, list) ->
                    SoundCue(label, list.maxOf { it.score })
                }.filter { it.score >= 0.20f }.sortedByDescending { it.score }
                coroutineContext.ensureActive()
                SoundAnalysis(cues)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            SoundAnalysis(emptyList(), "소리 분석을 마치지 못했어요. 잠시 후 다시 시도해 주세요.")
        } catch (_: LinkageError) {
            SoundAnalysis(emptyList(), "이 기기에서 소리 모델을 실행하지 못했어요. 앱을 다시 시작해 주세요.")
        }
    }
}
