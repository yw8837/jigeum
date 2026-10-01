package com.moa.babylanguage

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlin.math.hypot

internal data class VideoFrameSummary(
    val durationSeconds: Int,
    val sampleCount: Int,
    val faceFrames: Int,
    val mouthOpenFrames: Int,
    val analyzedFrames: Int,
    val hasAudioTrack: Boolean,
    val modelAvailable: Boolean,
    val audio: VideoAudioAnalysis = VideoAudioAnalysis(VideoAudioAnalysis.Status.NO_TRACK)
) {
    fun visualEvidence(): String = when {
        !modelAvailable -> "표정 관찰을 완료하지 못했어요. 이번 한마디는 소리 단서로만 만들었어요."
        faceFrames == 0 -> "선택 구간의 ${sampleCount}개 장면에서 얼굴을 찾지 못했어요. 이번 한마디는 소리 단서로만 만들었어요."
        else -> "선택 구간 ${sampleCount}개 장면 중 얼굴 ${faceFrames}개 · 입이 열린 장면 ${mouthOpenFrames}개를 관찰했어요. 얼굴의 주인이나 감정은 구분하지 않아요."
    }

}

internal suspend fun inspectVideo(context: Context, uri: Uri, startMs: Long, endMs: Long): VideoFrameSummary {
    val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
    if (size <= 0L || size > 100L * 1024L * 1024L) {
        throw IllegalArgumentException("100MB 이하의 크기를 확인할 수 있는 영상을 골라 주세요.")
    }
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
        if (durationMs <= 0L || durationMs > 60_500L)
            throw IllegalArgumentException("재생 시간이 1분 이내인 영상을 골라 주세요.")
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: 0
        if (width <= 0 || height <= 0)
            throw IllegalArgumentException("영상 화면을 읽을 수 없어요. 다른 파일을 골라 주세요.")
        if (Build.VERSION.SDK_INT < 27 && width.toLong() * height > 1920L * 1080L)
            throw IllegalArgumentException("이 기기에서는 FHD 이하의 영상을 골라 주세요.")
        val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        require(startMs >= 0 && endMs <= durationMs && endMs > startMs)
        val durationSeconds = ((endMs - startMs + 999) / 1000).toInt()
        val sampleCount = minOf(5, maxOf(1, durationSeconds))
        var faceCount = 0
        var mouthCount = 0
        var decoded = 0
        var analyzedFrames = 0
        val landmarker = try {
            FaceLandmarker.createFromOptions(context,
                FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
                    .setRunningMode(RunningMode.IMAGE).setNumFaces(1).build())
        } catch (cause: Exception) {
            android.util.Log.e("MoaFaceInit", "Video face model initialization failed", cause)
            null
        } catch (cause: LinkageError) {
            android.util.Log.e("MoaFaceInit", "Video face model linkage failed", cause)
            null
        }
        try {
            for (index in 0 until sampleCount) {
                coroutineContext.ensureActive()
                val timeUs = startMs * 1000 + ((endMs - startMs) * 1000L * (index + 1)) / (sampleCount + 1)
                val frame = if (Build.VERSION.SDK_INT >= 27) {
                    retriever.getScaledFrameAtTime(timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST, 512, 512)
                } else retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                if (frame != null) {
                    decoded++
                    var scaled = frame
                    try {
                        if (frame.width > 512 || frame.height > 512) {
                            val ratio = 512f / maxOf(frame.width, frame.height)
                            scaled = frame.scale(
                                (frame.width * ratio).toInt().coerceAtLeast(1),
                                (frame.height * ratio).toInt().coerceAtLeast(1), true)
                        }
                        if (landmarker != null) {
                            val landmarks = BitmapImageBuilder(scaled).build().use { image ->
                                landmarker.detect(image).faceLandmarks().firstOrNull()
                            }
                            analyzedFrames++
                            if (landmarks != null) {
                                faceCount++
                                if (landmarks.size > 291) {
                                    val upper = landmarks[13]
                                    val lower = landmarks[14]
                                    val left = landmarks[61]
                                    val right = landmarks[291]
                                    val opening = hypot((upper.x() - lower.x()).toDouble(),
                                        (upper.y() - lower.y()).toDouble())
                                    val mouthWidth = hypot((left.x() - right.x()).toDouble(),
                                        (left.y() - right.y()).toDouble())
                                    if (mouthWidth > 0.001 && opening / mouthWidth > 0.12) mouthCount++
                                }
                            }
                        }
                    } catch (_: Exception) { /* A frame may be corrupt; other frames still count. */ }
                    finally {
                        if (scaled !== frame && !scaled.isRecycled) scaled.recycle()
                        if (!frame.isRecycled) frame.recycle()
                    }
                }
            }
            if (decoded == 0) throw IOException("No frames decoded")
            return VideoFrameSummary(durationSeconds, decoded, faceCount, mouthCount,
                analyzedFrames, hasAudio, analyzedFrames > 0)
        } finally { landmarker?.close() }
    } finally { retriever.release() }
}
