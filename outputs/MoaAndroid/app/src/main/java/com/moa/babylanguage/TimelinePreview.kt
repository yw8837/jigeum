package com.moa.babylanguage

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlin.math.sqrt

/** Small, memory-only views of the local original. Neither PCM nor thumbnails are persisted. */
internal data class TimelinePreview(
    val thumbnails: List<Bitmap> = emptyList(),
    val waveform: FloatArray? = null,
    val notice: String? = null
)

internal object TimelinePreviewLoader {
    private const val SAMPLE_RATE = 16_000
    private const val BUCKET_MS = 100L
    // Stay within the same bounded decoder used by analysis. Never decode two minutes at once.
    private const val CHUNK_MS = 20_000L
    private const val FRAME_COUNT = 8

    suspend fun load(context: Context, clip: LocalClip): TimelinePreview = withContext(Dispatchers.IO) {
        val frames = mutableListOf<Bitmap>()
        var frameFailure = false
        if (clip.hasVideo && Build.VERSION.SDK_INT >= 27) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, clip.uri)
                repeat(FRAME_COUNT) { index ->
                    coroutineContext.ensureActive()
                    // Scaled extraction avoids allocating full 4K frames just for the timeline.
                    val timestamp = (index + .5) * clip.durationMs / FRAME_COUNT
                    val frame = retriever.getScaledFrameAtTime((timestamp * 1000).toLong(),
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 160, 120)
                    if (frame != null) frames += frame else frameFailure = true
                }
            } catch (cause: CancellationException) { throw cause }
            catch (_: Exception) { frameFailure = true }
            finally { runCatching { retriever.release() } }
        } else if (clip.hasVideo) frameFailure = true

        var waveform: FloatArray? = null
        var soundFailure = false
        try {
            val buckets = FloatArray(ceil(clip.durationMs.toDouble() / BUCKET_MS).toInt())
            var start = 0L
            while (start < clip.durationMs) {
                coroutineContext.ensureActive()
                val end = minOf(start + CHUNK_MS, clip.durationMs)
                // A short last tail borrows preroll but never exceeds the selected-range cap.
                val decodeStart = minOf(start, end - 1_000).coerceAtLeast(0)
                val pcm = VideoAudioDecoder.decode(context, clip.uri, decodeStart, end)
                if (pcm == null) { soundFailure = true; break }
                try {
                    val firstBucket = (start / BUCKET_MS).toInt()
                    val lastBucket = ceil(end.toDouble() / BUCKET_MS).toInt()
                    for (bucket in firstBucket until lastBucket) {
                        coroutineContext.ensureActive()
                        val from = (((bucket * BUCKET_MS - decodeStart) * SAMPLE_RATE) / 1000)
                            .coerceIn(0, pcm.size.toLong()).toInt()
                        val until = (((minOf((bucket + 1) * BUCKET_MS, end) - decodeStart) * SAMPLE_RATE) / 1000)
                            .coerceIn(from.toLong(), pcm.size.toLong()).toInt()
                        var squares = 0.0
                        for (sample in from until until) squares += pcm[sample].toDouble() * pcm[sample]
                        buckets[bucket] = if (until > from) sqrt(squares / (until - from)).toFloat() else 0f
                    }
                } finally { pcm.fill(0f) }
                start = end
            }
            if (!soundFailure) waveform = buckets
        } catch (cause: CancellationException) { throw cause }
        catch (_: Exception) { soundFailure = true }
        // Displayed Bitmaps remain immutable and are GC-owned: recycling during a Compose
        // draw can invalidate a frame still held by the renderer. At most eight small images.
        TimelinePreview(
            // A partial sequence would map images to the wrong times. Fall back honestly.
            thumbnails = if (frames.size == FRAME_COUNT && !frameFailure) frames else emptyList(),
            waveform = waveform,
            notice = when {
                soundFailure -> "소리 파형을 읽지 못했어요. 재생하며 구간을 골라 주세요."
                frameFailure -> "영상 미리보기는 재생으로 확인해 주세요."
                else -> null
            }
        )
    }
}
