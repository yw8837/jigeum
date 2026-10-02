package com.moa.babylanguage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.os.Build
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
    fun frame(context: Context, record: Observation, clip: LocalClip?, range: ClipRange?): Bitmap {
        val card = TranslatedMedia.caption(record, clip?.hasVideo == true, context.resources.configuration.fontScale)
        if (clip?.hasVideo != true) return card
        var scene: Bitmap? = null
        val reader = MediaMetadataRetriever()
        try {
            reader.setDataSource(context, clip.uri)
            val timeUs = ((range?.startMs ?: 0) + 100).coerceAtMost(clip.durationMs - 1) * 1000
            scene = if (Build.VERSION.SDK_INT >= 27) reader.getScaledFrameAtTime(timeUs,
                MediaMetadataRetriever.OPTION_CLOSEST, 720, 640)
                else reader.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            val source = scene ?: error("이 장면의 카드 이미지를 만들지 못했어요. 영상으로 저장하거나 다른 구간을 골라 주세요.")
            val scale = minOf(720f / source.width, 640f / source.height)
            val width = source.width * scale; val height = source.height * scale
            val canvas = Canvas(card)
            canvas.drawRect(0f, 0f, 720f, 640f, android.graphics.Paint().apply { color = android.graphics.Color.BLACK })
            canvas.drawBitmap(source, null, android.graphics.RectF((720-width)/2, (640-height)/2,
                (720+width)/2, (640+height)/2), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            return card
        } catch (failure: Throwable) { card.recycle(); throw failure }
        finally { reader.release(); scene?.recycle() }
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
    var image by remember(record.phrase, record.basis, record.context, clip?.uri, range, fontScale) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(record.phrase, record.basis, record.context, clip?.uri, range, fontScale) {
        var made: Bitmap? = null
        try {
            withContext(Dispatchers.IO) { made = MomentCardRenderer.frame(context, record, clip, range) }
            ensureActive(); image = made
        } catch (cancelled: CancellationException) { made?.recycle(); throw cancelled }
        catch (_: Exception) { /* The phrase remains readable even if a poster cannot be decoded. */ }
    }
    val bitmap = image
    DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
    if (bitmap == null) Box(Modifier.fillMaxWidth().aspectRatio(720f / height), contentAlignment = Alignment.Center) {
        Text(record.phrase, color = Ink, fontSize = 25.sp, lineHeight = 37.sp,
            modifier = Modifier.padding(16.dp).testTag("baby-phrase"))
    }
    else Image(bitmap.asImageBitmap(), contentDescription = null,
        modifier = Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat()/bitmap.height).clip(RoundedCornerShape(24.dp))
            .testTag("baby-phrase").semantics {
                text = AnnotatedString(record.phrase)
                stateDescription = ExpressionBasis.label(record.basis)
            })
}
