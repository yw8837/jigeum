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
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

/** A bounded, local analysis of the selected time range of local media's audio track. */
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
    internal data class DecodedAudio(val samples: FloatArray, val selectionTimelineAligned: Boolean)
    private const val OUTPUT_RATE = 16_000
    private const val MAX_ENCODED_BYTES = 12 * 1024 * 1024L
    private const val MAX_SOURCE_BYTES = 200L * 1024L * 1024L
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

    internal suspend fun decode(context: Context, uri: Uri, startMs: Long = 0, endMs: Long = 10_000): FloatArray? =
        decodeWithTiming(context, uri, startMs, endMs)?.samples

    internal suspend fun decodeWithTiming(context: Context, uri: Uri, startMs: Long, endMs: Long): DecodedAudio? = decodeLock.withLock {
        require(startMs >= 0 && endMs <= 120_500 && endMs - startMs in 1_000..30_000)
        decodeOne(context, uri, startMs * 1000, endMs * 1000)
    }

    private suspend fun decodeOne(context: Context, uri: Uri, startUs: Long, endUs: Long): DecodedAudio? {
        val deadline = SystemClock.elapsedRealtime() + DECODE_DEADLINE_MS
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var codecStarted = false
        val maxOutput = ((endUs - startUs) * OUTPUT_RATE / 1_000_000).toInt()
        val resampler = BoundedPcmResampler(maxOutput)
        val timing = AudioTimestampAudit(startUs)
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
            // Preserve AAC priming at t=0. Later seeks decode a short preroll before
            // cropping by output timestamps, so codec delay cannot eat the chosen start.
            if (startUs > 250_000) extractor.seekTo(startUs - 250_000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
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
            while (!outputEnded && resampler.size < maxOutput) {
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
                        if (extractor.sampleTrackIndex < 0 || sampleTime >= endUs) {
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
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit // Retry within the existing bounded deadline.
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit // getOutputBuffer(index) reads the current buffer.
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
                            if (outputInfo.size > 0 && outputInfo.presentationTimeUs < endUs) {
                                val buffer = codec.getOutputBuffer(outputIndex)
                                    ?: throw UnsupportedAudioException()
                                val start = outputInfo.offset
                                val end = start + outputInfo.size
                                if (start < 0 || end > buffer.capacity() || outputInfo.size > 4 * 1024 * 1024)
                                    throw UnsupportedAudioException()
                                val frameBytes = channels * if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                                val frames = outputInfo.size / frameBytes
                                val skip = (((startUs - outputInfo.presentationTimeUs).coerceAtLeast(0) * rate + 999_999) / 1_000_000).coerceAtMost(frames.toLong()).toInt()
                                val keepUntil = (((endUs - outputInfo.presentationTimeUs).coerceAtLeast(0) * rate + 999_999) / 1_000_000).coerceAtMost(frames.toLong()).toInt()
                                if (keepUntil > skip) {
                                    timing.observe(outputInfo.presentationTimeUs, skip, keepUntil - skip, rate)
                                    buffer.position(start + skip * frameBytes)
                                    buffer.limit(start + keepUntil * frameBytes)
                                    resampler.add(buffer.slice().order(ByteOrder.LITTLE_ENDIAN), rate, channels, pcmEncoding)
                                }
                            }
                            if (outputInfo.presentationTimeUs >= endUs ||
                                outputInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0)
                                outputEnded = true
                        } finally { codec.releaseOutputBuffer(outputIndex, false) }
                    }
                }
            }
            return DecodedAudio(resampler.copyResult(), timing.aligned)
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

internal class UnsupportedAudioException : Exception()
private class DecodeTimeoutException : Exception()
