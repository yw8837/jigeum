package com.moa.babylanguage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import androidx.media3.common.MediaItem
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.inspector.frame.FrameExtractor
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*

/** Same dimensions, scene fit and caption renderer as the exported movie. */
internal object MomentCardRenderer {
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun frame(context: Context, record: Observation, clip: LocalClip?, range: ClipRange?): Bitmap {
        val card = TranslatedMedia.caption(record, clip?.hasVideo == true, context.resources.configuration.fontScale)
        if (clip?.hasVideo != true) return card
        var scene: Bitmap? = null
        try {
            val timeMs = ((range?.startMs ?: 0) + 100).coerceAtMost((range?.endMs ?: clip.durationMs) - 1)
            // Platform getScaledFrameAtTime returned null for a real HEVC gallery clip.
            // Use Media3's GL colour pipeline, bounded to an SDR poster at the selected time.
            val extractor = FrameExtractor.Builder(context.applicationContext, MediaItem.fromUri(clip.uri))
                .setMediaCodecSelector(MediaCodecSelector.DEFAULT)
                .setEffects(listOf(Presentation.createForWidthAndHeight(720, 640, Presentation.LAYOUT_SCALE_TO_FIT)))
                .build()
            try {
                val pending = extractor.getFrame(timeMs)
                try {
                    // Inspector retains/deduplicates its frame. Own a copy before closing it.
                    scene = checkNotNull(pending.get(10, TimeUnit.SECONDS).bitmap.copy(Bitmap.Config.ARGB_8888, false))
                } finally { pending.cancel(false) }
            } finally { extractor.close() }
            val source = checkNotNull(scene)
            val scale = minOf(720f / source.width, 640f / source.height)
            val width = source.width * scale; val height = source.height * scale
            val canvas = Canvas(card)
            canvas.drawRect(0f, 0f, 720f, 640f, android.graphics.Paint().apply { color = android.graphics.Color.BLACK })
            canvas.drawBitmap(source, null, android.graphics.RectF((720-width)/2, (640-height)/2,
                (720+width)/2, (640+height)/2), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            return card
        } catch (failure: Throwable) { card.recycle(); throw failure }
        finally { scene?.recycle() }
    }

    fun savedFrame(context: Context, record: Observation): Bitmap {
        val reader = MediaMetadataRetriever()
        try {
            reader.setDataSource(RecordMedia.file(context, record.id).path)
            return reader.getFrameAtTime(100_000, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: error("보관한 영상의 카드 이미지를 열지 못했어요.")
        } finally { reader.release() }
    }
}

@Composable
internal fun ResultMomentCard(record: Observation, clip: LocalClip?, range: ClipRange?) {
    val context = LocalContext.current
    val fontScale = LocalConfiguration.current.fontScale
    val height = remember(record.phrase, clip?.hasVideo, fontScale) {
        TranslatedMedia.captionHeight(record.phrase, clip?.hasVideo == true, fontScale)
    }
    var retry by remember(clip?.uri, range) { mutableIntStateOf(0) }
    var image by remember(record.phrase, record.basis, record.context, clip?.uri, range, fontScale, retry) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(record.phrase, record.basis, record.context, clip?.uri, range, fontScale, retry) { mutableStateOf(false) }
    LaunchedEffect(record.phrase, record.basis, record.context, clip?.uri, range, fontScale, retry) {
        var made: Bitmap? = null
        try {
            withContext(Dispatchers.IO) { made = MomentCardRenderer.frame(context, record, clip, range) }
            ensureActive(); image = made
        } catch (cancelled: CancellationException) { made?.recycle(); throw cancelled }
        catch (failure: Exception) {
            failed = true
            android.util.Log.w("AgattokResultPoster", "Poster unavailable: ${failure.javaClass.simpleName}")
        }
    }
    // Displayed bitmaps can still be referenced by Compose/RenderThread after disposal.
    // Let their managed lifetime end normally; only unpublished/cancelled bitmaps are recycled.
    val bitmap = image
    androidx.compose.foundation.layout.Column {
        if (bitmap == null) {
            Box(Modifier.fillMaxWidth().aspectRatio(720f / height), contentAlignment = Alignment.Center) {
                Text(record.phrase, color = Ink, fontSize = 25.sp, lineHeight = 37.sp,
                    modifier = Modifier.padding(16.dp).testTag("baby-phrase"))
            }
            if (clip?.hasVideo == true) {
                Text(if (failed) "장면 미리보기를 불러오지 못했어요. 원본 재생과 영상 저장을 이용할 수 있어요."
                    else "장면과 아기말을 함께 불러오고 있어요.", color = Muted, fontSize = 13.sp,
                    modifier = Modifier.testTag("scene-poster-status"))
                if (failed) androidx.compose.material3.TextButton(onClick = { retry++ }) { Text("장면 미리보기 다시 불러오기") }
            }
        } else Image(bitmap.asImageBitmap(), contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat()/bitmap.height).clip(RoundedCornerShape(24.dp))
                .testTag("baby-phrase").semantics {
                    text = AnnotatedString(record.phrase)
                    stateDescription = ExpressionBasis.label(record.basis)
                })
    }
}
