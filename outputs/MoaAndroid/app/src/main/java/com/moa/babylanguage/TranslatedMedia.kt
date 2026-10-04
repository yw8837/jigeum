@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.moa.babylanguage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Size
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A bounded, entirely local render. Only the selected interval enters the saved movie. */
internal object TranslatedMedia {
    const val MAX_BYTES = 64L * 1024 * 1024
    private val renderLock = Mutex()
    private var sweptInterruptedFiles = false

    private fun sweepOnce(context: Context) {
        if (!sweptInterruptedFiles) {
            RecordMedia.cleanupInterruptedRenders(context)
            sweptInterruptedFiles = true
        }
    }

    suspend fun cleanup(context: Context) = renderLock.withLock {
        withContext(Dispatchers.IO) { sweepOnce(context) }
    }

    suspend fun render(context: Context, clip: LocalClip, range: ClipRange, record: Observation): File {
        var created: File? = null
        try { return renderLocked(context, clip, range, record) { created = it } }
        catch (error: Throwable) {
            // Also owns the output if cancellation wins at the successful return handoff.
            withContext(NonCancellable + Dispatchers.IO) { created?.delete() }
            if (error is TimeoutCancellationException) throw StoreException("영상 저장이 오래 걸려 멈췄어요. 문장만 저장하거나 다시 시도해 주세요.", error)
            if (error is Exception && error !is CancellationException && error !is StoreException)
                throw StoreException(if (error is IllegalStateException) error.message ?: "번역 영상을 저장하지 못했어요."
                    else "번역 영상을 저장하지 못했어요. 문장만 저장하거나 다시 시도해 주세요.", error)
            throw error
        }
    }

    private suspend fun renderLocked(context: Context, clip: LocalClip, range: ClipRange, record: Observation,
                                     track: (File) -> Unit): File = renderLock.withLock {
        withContext(Dispatchers.IO) { sweepOnce(context) }
        require(range.startMs >= 0 && range.endMs <= clip.durationMs && range.durationMs in 1_000..30_000)
        ObservationStore.validateRecord(record)
        val folder = File(context.cacheDir, "media-render").apply { check(isDirectory || mkdirs()) }
        // Keep the bounded render plus headroom within immediate free space.
        // Allocatable bytes are not free without allocation/cache eviction, which
        // a readiness check must not perform on the caregiver's behalf.
        check(folder.usableSpace > MAX_BYTES + 16L * 1024 * 1024) { "영상으로 남길 공간이 부족해요. 공간을 확보하거나 문장만 저장해 주세요." }
        val token = UUID.randomUUID().toString()
        val output = File(folder, "$token.mp4")
        track(output)
        val image = File(folder, "$token.png")
        var bitmap: Bitmap? = null
        var completed = false
        try {
            val overlay = withContext(Dispatchers.Default) { caption(record, clip.hasVideo, context.resources.configuration.fontScale) }
            bitmap = overlay
            val source = MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(range.startMs)
                    .setEndPositionMs(range.endMs).build()).build()
            val startExport: (Transformer) -> Unit
            if (clip.hasVideo) {
                val effects = listOf(SceneLayout(overlay.height), OverlayEffect(listOf(BitmapOverlay.createStaticBitmapOverlay(overlay))))
                val movie = EditedMediaItem.Builder(source).setEffects(Effects(emptyList(), effects)).build()
                // Single-item start infers the source tracks; a video without audio
                // must not gain the silence synthesized by an explicit audio sequence.
                startExport = { transformer -> transformer.start(movie, output.absolutePath) }
            } else {
                withContext(Dispatchers.IO) { image.outputStream().use { check(overlay.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
                val card = EditedMediaItem.Builder(MediaItem.Builder().setUri(Uri.fromFile(image))
                    .setImageDurationMs(range.durationMs).build()).setFrameRate(24).build()
                val audio = EditedMediaItem.Builder(source).setRemoveVideo(true).build()
                val composition = Composition.Builder(EditedMediaItemSequence.withVideoFrom(listOf(card)),
                    EditedMediaItemSequence.withAudioFrom(listOf(audio))).build()
                startExport = { transformer -> transformer.start(composition, output.absolutePath) }
            }
            withTimeout(120_000) { export(context, startExport) }
            coroutineContext.ensureActive()
            check(output.length() in 1..MAX_BYTES) { "영상 파일을 완성하지 못했어요. 문장만 저장하거나 다시 시도해 주세요." }
            completed = true
            output
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                image.delete()
                if (!completed) output.delete()
                bitmap?.recycle()
            }
        }
    }

    private suspend fun export(context: Context, startExport: (Transformer) -> Unit) = withContext(Dispatchers.Main.immediate) {
        var transformer: Transformer? = null
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                transformer = Transformer.Builder(context.applicationContext)
                    .setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setUsePlatformDiagnostics(false)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                            if (continuation.isActive) continuation.resumeWithException(
                                IllegalStateException("이 기기에서 번역 영상을 만들지 못했어요. 문장만 저장하거나 다른 파일로 다시 시도해 주세요.", exportException))
                        }
                    }).build()
                startExport(transformer!!)
            }
        } finally {
            // Transformer and its GL/codec resources must be released on its application looper,
            // before the caller deletes output or recycles the overlay bitmap.
            withContext(NonCancellable + Dispatchers.Main.immediate) { transformer?.cancel() }
        }
    }

    /** Fit the entire scene above the caption; never cover or crop the baby's face. */
    private class SceneLayout(private val outputHeight: Int) : MatrixTransformation {
        private val matrix = Matrix()
        override fun configure(inputWidth: Int, inputHeight: Int): Size {
            val scale = minOf(720f / inputWidth, 640f / inputHeight)
            matrix.setScale(inputWidth * scale / 720f, inputHeight * scale / outputHeight)
            matrix.postTranslate(0f, 1f - 640f / outputHeight)
            return Size(720, outputHeight)
        }
        override fun getMatrix(presentationTimeUs: Long) = matrix
    }

    private fun captionBody(phrase: String, fontScale: Float): StaticLayout {
        val text = phrase.replace(Regex("\\s+"), " ").trim()
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 56f * fontScale.coerceIn(1f, 2f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        fun bodyLayout() = StaticLayout.Builder.obtain(text, 0, text.length, bodyPaint, 624)
            .setIncludePad(false).setLineSpacing(5f, 1f).build()
        var body = bodyLayout()
        while (body.height > 2_700 && bodyPaint.textSize > 20f) { bodyPaint.textSize -= 1f; body = bodyLayout() }
        return body
    }

    private fun captionHeight(body: StaticLayout, video: Boolean): Int {
        val phraseTop = if (video) 708 else 390
        return maxOf(960, ((phraseTop + body.height + 170 + 1) / 2) * 2)
    }

    /** Reserve the exact card footprint before decoding the scene, avoiding moving controls. */
    internal fun captionHeight(phrase: String, video: Boolean, fontScale: Float = 1f): Int =
        captionHeight(captionBody(phrase, fontScale), video)

    internal fun caption(record: Observation, video: Boolean, fontScale: Float = 1f): Bitmap {
        val text = record.phrase.replace(Regex("\\s+"), " ").trim()
        val body = captionBody(text, fontScale)
        val height = captionHeight(body, video)
        val bitmap = createBitmap(720, height)
        val canvas = Canvas(bitmap)
        val paper = Paint().apply { color = 0xFFFFF9F0.toInt() }
        if (!video) canvas.drawColor(paper.color)
        else canvas.drawRect(0f, 640f, 720f, height.toFloat(), paper)
        val ink = 0xFF24322E.toInt()
        val muted = 0xFF65716B.toInt()
        fun draw(text: String, y: Float, size: Float, color: Int, bold: Boolean = false, maxHeight: Int = 960): Int {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; textSize = size
                typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            }
            fun measure() = StaticLayout.Builder.obtain(text, 0, text.length, paint, 624)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(5f, 1f).build()
            var layout = measure()
            while (layout.height > maxHeight && paint.textSize > 12f) {
                paint.textSize -= 1f
                layout = measure()
            }
            check(layout.height <= maxHeight) { "문장을 영상에 담기 어려워요. 문장을 조금 줄여 주세요." }
            canvas.withTranslation(48f, y) { layout.draw(this) }
            return layout.height
        }
        if (!video) {
            val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFA8C6A0.toInt() }
            canvas.drawCircle(360f, 218f, 78f, accent)
            val wave = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 10f; strokeCap = Paint.Cap.ROUND }
            listOf(18f, 36f, 48f, 30f, 16f).forEachIndexed { i, half ->
                canvas.drawLine(320f + i * 20f, 218f - half, 320f + i * 20f, 218f + half, wave)
            }
            draw("아가톡 · 소리로 남긴 순간", 330f, 26f, muted)
            draw(text, 390f, body.paint.textSize, ink, true, body.height)
            draw(record.context.replace(Regex("\\s+"), " "), height - 145f, 23f, muted, maxHeight = 70)
        } else {
            draw("아가톡 · 그때의 아기말", 666f, 21f, muted)
            val sage = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEDF3E9.toInt() }
            canvas.drawRoundRect(24f, 695f, 696f, 708f + body.height + 32f, 28f, 28f, sage)
            draw(text, 708f, body.paint.textSize, ink, true, body.height)
        }
        draw(ExpressionBasis.label(record.basis), height - 50f, 20f, muted)
        return bitmap
    }
}

/** Media belongs to a record ID; raw captured/imported sources never become shareable. IO only. */
internal object RecordMedia {
    private fun folder(context: Context) = File(context.filesDir, "record-media").apply { check(isDirectory || mkdirs()) }
    fun file(context: Context, id: Long): File {
        require(id in 1..ObservationStore.MAX_TIMESTAMP)
        return File(folder(context), "$id.mp4")
    }
    fun ids(context: Context, records: List<Observation>): Set<Long> = records.mapNotNullTo(HashSet()) {
        it.id.takeIf { id -> file(context, id).let { media -> media.isFile && media.length() > 0 } }
    }
    fun adopt(context: Context, id: Long, rendered: File) {
        check(rendered.parentFile?.canonicalFile == File(context.cacheDir, "media-render").canonicalFile)
        check(rendered.isFile && rendered.length() in 1..TranslatedMedia.MAX_BYTES)
        val target = file(context, id)
        check(!target.exists()) { "같은 기록의 영상이 이미 있어요. 다시 시도해 주세요." }
        check(rendered.renameTo(target)) { "번역 영상을 보관하지 못했어요. 저장 공간을 확인해 주세요." }
    }
    fun allIds(context: Context): Set<Long> = folder(context).listFiles().orEmpty()
        .filter { it.name.matches(Regex("[0-9]+\\.mp4")) }.mapNotNullTo(HashSet()) { it.nameWithoutExtension.toLongOrNull() }
    fun delete(context: Context, id: Long) {
        val file = file(context, id)
        check(!file.exists() || file.delete()) { "기록의 영상을 지우지 못했어요. 다시 시도해 주세요." }
    }
    fun cleanupInterruptedRenders(context: Context) {
        File(context.cacheDir, "media-render").listFiles()?.filter { it.isFile }?.forEach { it.delete() }
    }
}
