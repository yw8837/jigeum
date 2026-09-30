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

/** Holds at most 10 seconds of mono PCM in memory. No recording file is created. */
class ShortAudioCapture {
    private val lock = Any()
    private val samples = ShortArray(160_000)
    private var count = 0
    private var recorder: AudioRecord? = null
    private var reader: Thread? = null
    @Volatile private var running = false

    @Suppress("MissingPermission")
    @Synchronized
    fun start(): Boolean {
        discard()
        return try {
            val min = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (min <= 0) return false
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC, 16_000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(min * 2, 4096)
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return false
            }
            synchronized(lock) { count = 0 }
            recorder = record
            record.startRecording()
            running = true
            reader = Thread {
                val chunk = ShortArray(2048)
                while (running) {
                    val n = try { record.read(chunk, 0, chunk.size) } catch (_: Exception) { -1 }
                    if (n <= 0) break
                    synchronized(lock) {
                        if (running) {
                            val remaining = samples.size - count
                            val write = minOf(n, remaining)
                            if (write > 0) {
                                chunk.copyInto(samples, count, 0, write)
                                count += write
                            }
                            if (count >= samples.size) running = false
                        }
                    }
                }
                chunk.fill(0)
            }.apply { name = "moa-audio-capture"; start() }
            true
        } catch (_: Exception) {
            discard()
            false
        }
    }

    @Synchronized
    fun finish(): FloatArray {
        running = false
        try { recorder?.stop() } catch (_: Exception) { }
        try {
            reader?.join(750)
            if (reader?.isAlive == true) {
                reader?.interrupt()
                reader?.join(250)
            }
        } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        try { recorder?.release() } catch (_: Exception) { }
        recorder = null
        reader = null
        return synchronized(lock) {
            val data = FloatArray(count) { index -> samples[index] / 32768f }
            samples.fill(0)
            count = 0
            data
        }
    }

    fun discard() { finish().fill(0f) }
}

/** YAMNet recognizes general sound events. These are suggestions, never interpretations of needs. */
object SoundAnalyzer {
    private val inferenceLock = Mutex()

    suspend fun analyze(context: Context, pcm: FloatArray): List<SoundCue> =
        analyzeDetailed(context, pcm).cues

    suspend fun analyzeDetailed(context: Context, pcm: FloatArray): SoundAnalysis = inferenceLock.withLock {
        coroutineContext.ensureActive()
        if (pcm.size < 16_000) return@withLock SoundAnalysis(emptyList(),
            "녹음이 너무 짧아요. 1초 이상 다시 녹음하거나 직접 들은 소리를 골라 주세요.")
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
                        val name = category.categoryName().lowercase()
                        val label = when {
                            "baby cry" in name || "infant cry" in name -> "울음"
                            "laughter" in name || "giggle" in name -> "웃음"
                            "babbl" in name -> "옹알이 후보"
                            "speech" in name -> "말소리(화자 미확인)"
                            else -> null
                        }
                        label?.let { SoundCue(it, category.score()) }
                    }
                val cues = scored.groupBy { it.label }.map { (label, list) ->
                    SoundCue(label, list.maxOf { it.score })
                }.filter { it.score >= 0.20f }.sortedByDescending { it.score }.take(3)
                coroutineContext.ensureActive()
                SoundAnalysis(cues)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            SoundAnalysis(emptyList(), "소리 분석을 사용할 수 없어요. 다시 시도하거나 직접 들은 소리를 골라 주세요.")
        } catch (_: LinkageError) {
            SoundAnalysis(emptyList(), "이 기기에서 소리 모델을 실행하지 못했어요. 직접 들은 소리를 골라 주세요.")
        }
    }
}
