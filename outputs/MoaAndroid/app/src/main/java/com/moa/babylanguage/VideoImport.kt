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
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
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

/** A failed model run is not a completed observation, even when it has explanatory text. */
internal enum class VisualEvidenceStatus { COMPLETE, PARTIAL, FAILED }

/** Readable sampled times; the timeline itself retains integer millisecond PTS. */
private fun visualSeconds(ms: Long): String =
    "%.1f".format(java.util.Locale.ROOT, ms / 1000.0).trimEnd('0').trimEnd('.')

/** A sampled image observation, not face identity, emotion or a speaking/lip-sync verdict.
 * requestedOffsetMs is the cadence target; presentationOffsetMs is the actual decoded
 * PTS relative to the selected range. Legacy observations have no actual PTS.
 * mouthOpening is inner-lip height / mouth width, measured in image pixels.
 * eyeClosure and mouthCornerLift are paired blink/smile blendshape coefficients,
 * not probabilities of sleep or happiness. Missing coefficients stay unknown. */
internal data class VisualFrameObservation(
    val requestedOffsetMs: Long,
    val faceCount: Int,
    val valid: Boolean,
    val mouthOpening: Float? = null,
    val eyeClosure: Float? = null,
    val mouthCornerLift: Float? = null,
    val presentationOffsetMs: Long? = null
) {
    companion object {
        fun measure(requestedOffsetMs: Long, faces: List<List<NormalizedLandmark>>,
                    blendshapes: List<List<Category>>, width: Int, height: Int): VisualFrameObservation {
            val unavailable = VisualFrameObservation(requestedOffsetMs, faces.size, false)
            // A two-face model exposes ambiguity instead of silently selecting its first face.
            if (faces.size != 1 || width <= 0 || height <= 0) return unavailable
            val points = faces.single()
            if (points.size <= 291) return unavailable
            val mouth = listOf(points[13], points[14], points[61], points[291])
            if (mouth.any { !it.x().isFinite() || !it.y().isFinite() ||
                    it.x() !in 0f..1f || it.y() !in 0f..1f }) return unavailable
            fun distance(a: NormalizedLandmark, b: NormalizedLandmark): Double =
                hypot((a.x() - b.x()).toDouble() * width, (a.y() - b.y()).toDouble() * height)
            val mouthWidth = distance(mouth[2], mouth[3])
            if (mouthWidth < 2.0) return unavailable
            val opening = (distance(mouth[0], mouth[1]) / mouthWidth).toFloat()
            if (!opening.isFinite()) return unavailable
            val categories = blendshapes.singleOrNull().orEmpty()
            fun pair(first: String, second: String): Float? {
                fun score(name: String): Float? = categories.singleOrNull { it.categoryName() == name }
                    ?.score()?.takeIf { it.isFinite() && it in 0f..1f }
                val left = score(first) ?: return null
                val right = score(second) ?: return null
                return (left + right) / 2f
            }
            return VisualFrameObservation(requestedOffsetMs, 1, true, opening,
                pair("eyeBlinkLeft", "eyeBlinkRight"), pair("mouthSmileLeft", "mouthSmileRight"))
        }
    }
}

internal data class VisualEvidence(
    val status: VisualEvidenceStatus,
    val evidence: String,
    val notice: String? = null,
    val frames: List<VisualFrameObservation> = emptyList(),
    val timeline: VisualTimelineSummary? = null
) {
    companion object {
        fun failed(): VisualEvidence {
            val message = "표정 관찰을 완료하지 못했어요. 영상을 다시 선택해 시도해 주세요."
            return VisualEvidence(VisualEvidenceStatus.FAILED, message, message)
        }
    }
}

internal data class VideoFrameSummary(
    val durationSeconds: Int,
    val sampleCount: Int,
    val faceFrames: Int,
    val mouthOpenFrames: Int,
    val analyzedFrames: Int,
    val hasAudioTrack: Boolean,
    val modelAvailable: Boolean,
    val audio: VideoAudioAnalysis = VideoAudioAnalysis(VideoAudioAnalysis.Status.NO_TRACK),
    val frames: List<VisualFrameObservation> = emptyList(),
    val selectionDurationMs: Long = durationSeconds * 1000L
) {
    fun visualEvidence(): VisualEvidence {
        if (!modelAvailable || analyzedFrames == 0) {
            return VisualEvidence.failed()
        }
        val temporal = if (frames.any { it.presentationOffsetMs != null })
            VisualTimeline.summarize(frames, selectionDurationMs) else null
        val partial = analyzedFrames < sampleCount || temporal?.coversSelection == false
        val observation = if (faceFrames == 0)
            "선택 구간의 ${analyzedFrames}개 장면에서 얼굴을 찾지 못했어요. 얼굴이 잘 보이는 구간에서 다시 살펴볼 수 있어요."
        else "선택 구간 ${analyzedFrames}개 장면 중 얼굴 ${faceFrames}개 장면 · 입이 열린 장면 ${mouthOpenFrames}개를 관찰했어요. 얼굴의 주인이나 감정은 구분하지 않아요."
        val multipleFaces = frames.count { it.faceCount > 1 }
        val ambiguity = if (multipleFaces > 0)
            "${multipleFaces}개 장면에는 얼굴이 여럿 보여 한마디의 표정 단서에서 제외했어요." else null
        val sequence = temporal?.let { timeline ->
            val phases = timeline.phases.mapIndexed { index, phase ->
                "${listOf("초반", "중반", "후반")[index]}(${visualSeconds(phase.startMs)}~${visualSeconds(phase.endMs)}초): " +
                    "관찰 ${phase.samples}개, 단일 얼굴 ${phase.singleFaceSamples}개, 열린 입 ${phase.openMouthSamples}개"
            }.joinToString(" → ")
            val changes = timeline.mouthChanges.joinToString(" → ") {
                "${visualSeconds(it.startMs)}~${visualSeconds(it.endMs)}초 ${if (it.opening) "입 열림" else "입 닫힘"}"
            }
            listOf(phases, changes.takeIf { it.isNotEmpty() },
                "이어진 단일 얼굴 장면의 입 변화 ${timeline.mouthTransitions}회예요. 얼굴의 주인이나 발성 여부는 확인하지 않아요.")
                .filterNotNull().joinToString("\n")
        }
        val notice = when {
            analyzedFrames < sampleCount -> "전체 ${sampleCount}개 장면 중 ${analyzedFrames}개만 표정 관찰을 마쳤어요."
            temporal?.coversSelection == false -> "선택 구간 일부의 표정 관찰이 빠졌어요. 관찰 공백을 움직임으로 해석하지 않아요."
            else -> null
        }
        return VisualEvidence(if (partial) VisualEvidenceStatus.PARTIAL else VisualEvidenceStatus.COMPLETE,
            listOfNotNull(observation, sequence, ambiguity, notice).joinToString("\n"), notice, frames, temporal)
    }

}

/** Isolate optional visual failures from a sound interpretation already obtained for this clip. */
internal suspend fun inspectVideoEvidence(context: Context, uri: Uri, startMs: Long, endMs: Long): VisualEvidence {
    coroutineContext.ensureActive()
    return try {
        val evidence = inspectVideo(context, uri, startMs, endMs).visualEvidence()
        coroutineContext.ensureActive()
        evidence
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        VisualEvidence.failed()
    } catch (_: LinkageError) {
        VisualEvidence.failed()
    }
}

internal suspend fun inspectVideo(context: Context, uri: Uri, startMs: Long, endMs: Long): VideoFrameSummary {
    val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
    if (size <= 0L || size > CaptureLimits.FULL.fileBytes) {
        throw IllegalArgumentException("200MB 이하의 크기를 확인할 수 있는 영상을 골라 주세요.")
    }
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
        if (!CaptureLimits.FULL.acceptsDuration(durationMs))
            throw IllegalArgumentException("재생 시간이 1초 이상, 2분 이내인 영상을 골라 주세요.")
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: 0
        if (width <= 0 || height <= 0)
            throw IllegalArgumentException("영상 화면을 읽을 수 없어요. 다른 파일을 골라 주세요.")
        if (Build.VERSION.SDK_INT < 27 && width.toLong() * height > 1920L * 1080L)
            throw IllegalArgumentException("이 기기에서는 FHD 이하의 영상을 골라 주세요.")
        val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        require(startMs >= 0 && endMs <= durationMs && endMs > startMs && endMs - startMs <= 20_000L)
        val durationSeconds = ((endMs - startMs + 999) / 1000).toInt()
        val sampleCount = ((endMs - startMs + 499) / 500).toInt()
        var faceCount = 0
        var mouthCount = 0
        var decoded = 0
        var analyzedFrames = 0
        val observations = mutableListOf<VisualFrameObservation>()
        val landmarker = try {
            FaceLandmarker.createFromOptions(context,
                FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder().setModelAssetPath("face_landmarker.task").build())
                    .setRunningMode(RunningMode.VIDEO).setNumFaces(2)
                    .setOutputFaceBlendshapes(true).build())
        } catch (cause: Exception) {
            android.util.Log.e("MoaFaceInit", "Video face model initialization failed", cause)
            null
        } catch (cause: LinkageError) {
            android.util.Log.e("MoaFaceInit", "Video face model linkage failed", cause)
            null
        }
        try {
            VideoFrameDecoder.decode(context, uri, startMs, endMs) { frame ->
                coroutineContext.ensureActive()
                decoded++
                var observation = VisualFrameObservation(frame.requestedOffsetMs, 0, false,
                    presentationOffsetMs = frame.presentationOffsetMs)
                try {
                    if (landmarker != null) {
                        val result = BitmapImageBuilder(frame.bitmap).build().use { image ->
                            landmarker.detectForVideo(image, frame.presentationOffsetMs)
                        }
                        coroutineContext.ensureActive()
                        analyzedFrames++
                        observation = VisualFrameObservation.measure(frame.requestedOffsetMs,
                            result.faceLandmarks(), result.faceBlendshapes().orElse(emptyList()),
                            frame.bitmap.width, frame.bitmap.height)
                            .copy(presentationOffsetMs = frame.presentationOffsetMs)
                        if (observation.faceCount > 0) faceCount++
                        if (observation.valid && (observation.mouthOpening ?: 0f) > .12f) mouthCount++
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Keep the time gap visible, never bridge it as motion. */ }
                observations += observation
            }
            if (decoded == 0) throw IOException("No frames decoded")
            return VideoFrameSummary(durationSeconds, sampleCount, faceCount, mouthCount,
                analyzedFrames, hasAudio, analyzedFrames > 0, frames = observations, selectionDurationMs = endMs - startMs)
        } finally { landmarker?.close() }
    } finally { retriever.release() }
}
