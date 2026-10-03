package com.moa.babylanguage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/** Select the first decoded frame at/after each cadence boundary, never repeat a sparse frame. */
internal class VideoFrameCadence(private val startUs: Long, private val endUs: Long) {
    private var nextUs = startUs
    private var previousUs = Long.MIN_VALUE
    fun select(ptsUs: Long): Long? {
        require(ptsUs >= previousUs) { "Video presentation timestamps went backwards" }
        if (ptsUs == previousUs) return null
        previousUs = ptsUs
        if (ptsUs < nextUs || ptsUs >= endUs) return null
        val requestedMs = (nextUs - startUs) / 1000
        nextUs = startUs + ((ptsUs - startUs) / 500_000 + 1) * 500_000
        return requestedMs
    }
}

internal data class DecodedVideoFrame(val requestedOffsetMs: Long, val presentationOffsetMs: Long,
                                     val bitmap: Bitmap)

/** Eight-bit Y'CbCr matrix/range conversion; no HDR tone mapping or wide-gamut-to-sRGB transform.
 * MediaFormat documents Kr/Kb and full/limited component ranges. Buffer output
 * metadata wins; unspecified/unrecognized fields fall back to container metadata,
 * then the previous limited BT.601 behavior. No color properties are guessed from size.
 * https://developer.android.com/reference/android/media/MediaFormat#KEY_COLOR_RANGE
 * https://developer.android.com/reference/android/media/MediaFormat#KEY_COLOR_STANDARD */
internal class VideoYuvColor private constructor(fullRange: Boolean, kr: Double, kb: Double) {
    private val black = if (fullRange) 0 else 16
    private val lumaScale = if (fullRange) 1.0 else 255.0 / 219
    private val chromaScale = if (fullRange) 1.0 else 255.0 / 224
    private val kg = 1 - kr - kb
    private val redCr = 2 * (1 - kr) * chromaScale
    private val blueCb = 2 * (1 - kb) * chromaScale
    private val greenCb = 2 * kb * (1 - kb) / kg * chromaScale
    private val greenCr = 2 * kr * (1 - kr) / kg * chromaScale

    fun argb(y: Int, cb: Int, cr: Int): Int {
        val luma = (y - black) * lumaScale
        val u = cb - 128
        val v = cr - 128
        val r = (luma + redCr * v).roundToInt().coerceIn(0, 255)
        val g = (luma - greenCb * u - greenCr * v).roundToInt().coerceIn(0, 255)
        val b = (luma + blueCb * u).roundToInt().coerceIn(0, 255)
        return (255 shl 24) or (r shl 16) or (g shl 8) or b
    }

    companion object {
        fun fromFormats(output: MediaFormat, input: MediaFormat): VideoYuvColor {
            fun property(key: String, known: Set<Int>, fallback: Int): Int {
                fun read(format: MediaFormat): Int? = if (format.containsKey(key))
                    format.getInteger(key).takeIf { it in known } else null
                return read(output) ?: read(input) ?: fallback
            }
            val range = property(MediaFormat.KEY_COLOR_RANGE,
                setOf(MediaFormat.COLOR_RANGE_FULL, MediaFormat.COLOR_RANGE_LIMITED),
                MediaFormat.COLOR_RANGE_LIMITED)
            val standard = property(MediaFormat.KEY_COLOR_STANDARD,
                setOf(MediaFormat.COLOR_STANDARD_BT601_NTSC, MediaFormat.COLOR_STANDARD_BT601_PAL,
                    MediaFormat.COLOR_STANDARD_BT709, MediaFormat.COLOR_STANDARD_BT2020),
                MediaFormat.COLOR_STANDARD_BT601_NTSC)
            val (kr, kb) = when (standard) {
                MediaFormat.COLOR_STANDARD_BT709 -> .2126 to .0722
                MediaFormat.COLOR_STANDARD_BT2020 -> .2627 to .0593
                else -> .299 to .114
            }
            return VideoYuvColor(range == MediaFormat.COLOR_RANGE_FULL, kr, kb)
        }
    }
}

/** Sequential decoding preserves container PTS. The callback borrows a bitmap, valid only during the call. */
internal object VideoFrameDecoder {
    suspend fun decode(context: Context, uri: Uri, startMs: Long, endMs: Long,
                       observe: suspend (DecodedVideoFrame) -> Unit) {
        require(startMs >= 0 && endMs > startMs && endMs - startMs <= 20_000)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val tracks = (0 until extractor.trackCount).filter {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            }
            require(tracks.size == 1) { "Expected one video track" }
            val track = tracks.single()
            val format = extractor.getTrackFormat(track)
            val rotation = if (format.containsKey(MediaFormat.KEY_ROTATION)) format.getInteger(MediaFormat.KEY_ROTATION) else 0
            // Buffer output is rotated explicitly after applying the decoder's crop rectangle.
            format.setInteger(MediaFormat.KEY_ROTATION, 0)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            extractor.selectTrack(track)
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val decoder = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            val cadence = VideoFrameCadence(startMs * 1000, endMs * 1000)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var lastProgress = android.os.SystemClock.elapsedRealtime()
            while (!outputDone) {
                coroutineContext.ensureActive()
                check(android.os.SystemClock.elapsedRealtime() - lastProgress < 15_000) { "Video decoder stalled" }
                if (!inputDone) {
                    val input = decoder.dequeueInputBuffer(10_000)
                    if (input >= 0) {
                        val buffer = requireNotNull(decoder.getInputBuffer(input))
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(input, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                        lastProgress = android.os.SystemClock.elapsedRealtime()
                    }
                }
                val output = decoder.dequeueOutputBuffer(info, 10_000)
                if (output >= 0) {
                    try {
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val requestMs = cadence.select(info.presentationTimeUs)
                            if (requestMs != null) {
                                // API 21+: format for this exact buffer also handles midstream
                                // changes, without retaining stale INFO_OUTPUT_FORMAT_CHANGED state.
                                // https://developer.android.com/reference/android/media/MediaCodec#getOutputFormat(int)
                                val color = VideoYuvColor.fromFormats(decoder.getOutputFormat(output), format)
                                val bitmap = requireNotNull(decoder.getOutputImage(output)) { "Decoder has no YUV image output" }
                                    .use { toBitmap(it, rotation, color) }
                                try {
                                    coroutineContext.ensureActive()
                                    observe(DecodedVideoFrame(requestMs, (info.presentationTimeUs - startMs * 1000) / 1000, bitmap))
                                } finally { bitmap.recycle() }
                            }
                            if (info.presentationTimeUs >= endMs * 1000) outputDone = true
                        }
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    } finally { decoder.releaseOutputBuffer(output, false) }
                    lastProgress = android.os.SystemClock.elapsedRealtime()
                }
            }
        } finally {
            try { codec?.release() } finally { extractor.release() }
        }
    }

    private fun toBitmap(image: Image, rotation: Int, color: VideoYuvColor): Bitmap {
        require(image.format == ImageFormat.YUV_420_888 && image.planes.size == 3)
        val crop = image.cropRect
        require(crop.width() > 0 && crop.height() > 0)
        val scale = minOf(1.0, 512.0 / maxOf(crop.width(), crop.height()))
        val width = (crop.width() * scale).toInt().coerceAtLeast(1)
        val height = (crop.height() * scale).toInt().coerceAtLeast(1)
        val pixels = IntArray(width * height)
        val planes = image.planes
        fun sample(plane: Int, x: Int, y: Int): Int {
            val p = planes[plane]
            return p.buffer.get(p.buffer.position() + y * p.rowStride + x * p.pixelStride).toInt() and 255
        }
        for (y in 0 until height) for (x in 0 until width) {
            val sx = crop.left + x * crop.width() / width
            val sy = crop.top + y * crop.height() / height
            pixels[y * width + x] = color.argb(sample(0, sx, sy),
                sample(1, sx / 2, sy / 2), sample(2, sx / 2, sy / 2))
        }
        val original = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        if (rotation % 360 == 0) return original
        return try {
            Bitmap.createBitmap(original, 0, 0, width, height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        } finally { original.recycle() }
    }
}
