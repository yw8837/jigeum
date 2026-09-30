package com.moa.babylanguage

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

/** A bounded, local analysis of the first ten seconds of a selected video's audio track. */
internal data class VideoAudioAnalysis(
    val status: Status,
    val cues: List<SoundCue> = emptyList(),
    val problem: String? = null
) {
    enum class Status { NO_TRACK, CUES, NO_CUES, SILENT, UNSUPPORTED, FAILED, TIMEOUT }

    fun description(): String = problem ?: when (status) {
        Status.NO_TRACK -> "오디오 트랙이 없어요."
        Status.CUES -> "첫 10초에서 일반 소리 후보를 찾았어요. 직접 듣고 확인해 주세요."
        Status.NO_CUES -> "첫 10초에서 구분할 수 있는 소리 후보가 없어요. 직접 들어 주세요."
        Status.SILENT -> "첫 10초가 거의 무음이에요. 직접 들어 주세요."
        Status.UNSUPPORTED -> "이 영상의 오디오 형식은 분석할 수 없어요. 직접 들어 주세요."
        Status.FAILED -> "오디오 분석을 마치지 못했어요. 영상을 재생해 직접 들어 주세요."
        Status.TIMEOUT -> "오디오 분석이 오래 걸려 멈췄어요. 직접 들어 주세요."
    }
}

internal object VideoAudioDecoder {
    private const val WINDOW_US = 10_000_000L
    private const val OUTPUT_RATE = 16_000
    private const val MAX_OUTPUT = OUTPUT_RATE * 10
    private const val MAX_ENCODED_BYTES = 12 * 1024 * 1024L
    private const val MAX_SOURCE_BYTES = 50L * 1024L * 1024L
    private const val DECODE_DEADLINE_MS = 12_000L
    private const val CODEC_WAIT_US = 10_000L
    private val decodeLock = Mutex()

    suspend fun analyze(context: Context, uri: Uri): VideoAudioAnalysis {
        val decoded = try {
            decode(context, uri)
        } catch (cause: kotlinx.coroutines.CancellationException) {
            throw cause
        } catch (_: UnsupportedAudioException) {
            return VideoAudioAnalysis(VideoAudioAnalysis.Status.UNSUPPORTED)
        } catch (_: DecodeTimeoutException) {
            return VideoAudioAnalysis(VideoAudioAnalysis.Status.TIMEOUT)
        } catch (_: Exception) {
            return VideoAudioAnalysis(VideoAudioAnalysis.Status.FAILED)
        } catch (_: LinkageError) {
            return VideoAudioAnalysis(VideoAudioAnalysis.Status.UNSUPPORTED)
        }
        if (decoded == null) return VideoAudioAnalysis(VideoAudioAnalysis.Status.NO_TRACK)
        try {
            if (decoded.size < OUTPUT_RATE) return VideoAudioAnalysis(VideoAudioAnalysis.Status.NO_CUES)
            var sumSquares = 0.0
            for (sample in decoded) sumSquares += sample.toDouble() * sample
            if (sqrt(sumSquares / decoded.size) < 0.003)
                return VideoAudioAnalysis(VideoAudioAnalysis.Status.SILENT)
            val soundAnalysis = SoundAnalyzer.analyzeDetailed(context, decoded)
            if (soundAnalysis.problem != null)
                return VideoAudioAnalysis(VideoAudioAnalysis.Status.FAILED,
                    problem = soundAnalysis.problem)
            val cues = soundAnalysis.cues
            return VideoAudioAnalysis(
                if (cues.isEmpty()) VideoAudioAnalysis.Status.NO_CUES
                else VideoAudioAnalysis.Status.CUES,
                cues
            )
        } finally {
            decoded.fill(0f)
        }
    }

    internal suspend fun decode(context: Context, uri: Uri): FloatArray? = decodeLock.withLock {
        decodeOne(context, uri)
    }

    private suspend fun decodeOne(context: Context, uri: Uri): FloatArray? {
        val deadline = SystemClock.elapsedRealtime() + DECODE_DEADLINE_MS
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var codecStarted = false
        val resampler = BoundedPcmResampler()
        try {
            coroutineContext.ensureActive()
            val sourceSize = context.contentResolver.openFileDescriptor(uri, "r")
                ?.use { it.statSize } ?: -1L
            if (sourceSize <= 0L || sourceSize > MAX_SOURCE_BYTES)
                throw UnsupportedAudioException()
            // OpenDocument grants read access to this URI only; no copied media or persisted grant.
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return null
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw UnsupportedAudioException()
            if (mime == "audio/unknown") throw UnsupportedAudioException()
            val declaredRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val declaredChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            if (declaredRate !in 8_000..96_000 || declaredChannels !in 1..2)
                throw UnsupportedAudioException()
            extractor.selectTrack(track)
            codec = try { MediaCodec.createDecoderByType(mime) }
            catch (_: Exception) { throw UnsupportedAudioException() }
            codec.configure(format, null, null, 0)
            codec.start()
            codecStarted = true

            val outputInfo = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var encodedBytes = 0L
            var rate = declaredRate
            var channels = declaredChannels
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            while (!outputEnded && resampler.size < MAX_OUTPUT) {
                coroutineContext.ensureActive()
                if (SystemClock.elapsedRealtime() >= deadline) throw DecodeTimeoutException()
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(CODEC_WAIT_US)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex)
                            ?: throw UnsupportedAudioException()
                        input.clear()
                        val sampleTime = extractor.sampleTime
                        // AAC encoder priming can have a negative PTS. A negative timestamp
                        // is not EOS while a selected sample track still exists.
                        if (extractor.sampleTrackIndex < 0 || sampleTime >= WINDOW_US) {
                            codec.queueInputBuffer(inputIndex, 0, 0,
                                sampleTime.coerceAtLeast(0), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            val bytes = extractor.readSampleData(input, 0)
                            if (bytes < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, sampleTime,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            } else {
                                encodedBytes += bytes
                                if (encodedBytes > MAX_ENCODED_BYTES) throw UnsupportedAudioException()
                                codec.queueInputBuffer(inputIndex, 0, bytes, sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }
                val outputIndex = codec.dequeueOutputBuffer(outputInfo, CODEC_WAIT_US)
                when (outputIndex) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        val newRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val newChannels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        val newEncoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING))
                            outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        else AudioFormat.ENCODING_PCM_16BIT
                        if (newRate !in 8_000..96_000 || newChannels !in 1..2 ||
                            newEncoding != AudioFormat.ENCODING_PCM_16BIT &&
                            newEncoding != AudioFormat.ENCODING_PCM_FLOAT ||
                            resampler.size > 0 && newRate != rate)
                            throw UnsupportedAudioException()
                        rate = newRate
                        channels = newChannels
                        pcmEncoding = newEncoding
                    }
                    in 0..Int.MAX_VALUE -> {
                        try {
                            if (outputInfo.size > 0 && outputInfo.presentationTimeUs < WINDOW_US) {
                                val buffer = codec.getOutputBuffer(outputIndex)
                                    ?: throw UnsupportedAudioException()
                                val start = outputInfo.offset
                                val end = start + outputInfo.size
                                if (start < 0 || end > buffer.capacity() || outputInfo.size > 4 * 1024 * 1024)
                                    throw UnsupportedAudioException()
                                buffer.position(start)
                                buffer.limit(end)
                                resampler.add(buffer.slice().order(ByteOrder.LITTLE_ENDIAN),
                                    rate, channels, pcmEncoding)
                            }
                            if (outputInfo.presentationTimeUs >= WINDOW_US ||
                                outputInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0)
                                outputEnded = true
                        } finally { codec.releaseOutputBuffer(outputIndex, false) }
                    }
                }
            }
            return resampler.copyResult()
        } finally {
            resampler.clear()
            if (codec != null) {
                if (codecStarted) try { codec.stop() } catch (_: Exception) { }
                try { codec.release() } catch (_: Exception) { }
            }
            try { extractor.release() } catch (_: Exception) { }
        }
    }
}

private class UnsupportedAudioException : Exception()
private class DecodeTimeoutException : Exception()

/** Small streaming resampler, preserving at most ten seconds of mono 16 kHz PCM. */
internal class BoundedPcmResampler {
    private val output = FloatArray(160_000)
    var size: Int = 0
        private set
    private var sourceFrame = 0L
    private var previous = 0f

    fun add(buffer: ByteBuffer, sampleRate: Int, channels: Int, encoding: Int) {
        val bytesPerSample = when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> 2
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            else -> throw UnsupportedAudioException()
        }
        val frameBytes = bytesPerSample * channels
        if (buffer.remaining() % frameBytes != 0) throw UnsupportedAudioException()
        while (buffer.remaining() >= frameBytes && size < output.size) {
            var value = 0f
            repeat(channels) {
                value += if (encoding == AudioFormat.ENCODING_PCM_16BIT)
                    buffer.short / 32768f else buffer.float
            }
            value = (value / channels).coerceIn(-1f, 1f)
            if (!value.isFinite()) throw UnsupportedAudioException()
            if (sourceFrame == 0L) {
                output[size++] = value
            } else {
                // Interpolate at destination sample positions across each source frame pair.
                while (size < output.size && size.toLong() * sampleRate <= sourceFrame * 16_000L) {
                    val position = size.toLong() * sampleRate - (sourceFrame - 1) * 16_000L
                    val fraction = position.toFloat() / 16_000f
                    output[size++] = previous + (value - previous) * fraction
                }
            }
            previous = value
            sourceFrame++
        }
    }

    fun copyResult(): FloatArray = output.copyOf(size).also { output.fill(0f) }

    fun clear() {
        output.fill(0f)
        size = 0
        sourceFrame = 0L
        previous = 0f
    }
}
