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

/** Only derived observations are retained. The selected media URI and its pixels stay in this screen. */
data class ImportedVideoObservation(
    val heardSound: String,
    val manualNote: String?,
    val faceVisible: Boolean,
    val mouthOpen: Boolean,
    val evidence: String,
    val audioCues: List<SoundCue> = emptyList()
)

private data class VideoFrameSummary(
    val durationSeconds: Int,
    val sampleCount: Int,
    val faceFrames: Int,
    val mouthOpenFrames: Int,
    val analyzedFrames: Int,
    val hasAudioTrack: Boolean,
    val modelAvailable: Boolean,
    val audio: VideoAudioAnalysis = VideoAudioAnalysis(VideoAudioAnalysis.Status.NO_TRACK)
) {
    fun evidence(): String = buildString {
        append("선택한 영상 ${durationSeconds}초 중 ${sampleCount}개 프레임을 살펴봤어요. ")
        if (modelAvailable) {
            if (analyzedFrames < sampleCount) append("자동 관찰에 성공한 ${analyzedFrames}개 프레임 중 ")
            append("얼굴이 보인 프레임 ${faceFrames}개")
            if (mouthOpenFrames > 0) append(", 입이 열린 프레임 ${mouthOpenFrames}개")
            append(". ")
        } else append("자동 얼굴 관찰을 사용할 수 없어 보호자가 본 장면만 반영해요. ")
        append(audio.description())
        if (audio.cues.isNotEmpty()) {
            append(" 일반 소리 후보: ")
            append(audio.cues.joinToString(" · ") { it.label })
            append(". 영아의 뜻이나 화자는 알 수 없어요.")
        }
    }
}

@Composable
fun VideoImportScreen(onBack: () -> Unit, onTranslate: (ImportedVideoObservation) -> Unit,
                      authorize: () -> Boolean = { false }) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentAuthorize by rememberUpdatedState(authorize)
    val alive = remember { AtomicBoolean(true) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var summary by remember { mutableStateOf<VideoFrameSummary?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var heardSound by remember { mutableStateOf("") }
    var manualNote by remember { mutableStateOf<String?>(null) }
    var analysisJob by remember { mutableStateOf<Job?>(null) }
    var pickerPending by remember { mutableStateOf(false) }
    fun checkAccess(): Boolean {
        if (!alive.get()) return false
        if (currentAuthorize()) return true
        analysisJob?.cancel()
        selectedUri = null
        summary = null
        heardSound = ""
        manualNote = null
        return false
    }
    DisposableEffect(Unit) { onDispose { alive.set(false); analysisJob?.cancel() } }
    LaunchedEffect(Unit) { while (checkAccess()) delay(1000) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pickerPending = false
        if (!checkAccess()) return@rememberLauncherForActivityResult
        if (uri != null) {
            analysisJob?.cancel()
            selectedUri = null
            summary = null
            heardSound = ""
            manualNote = null
            error = null
            analyzing = true
            analysisJob = scope.launch {
                try {
                    if (!checkAccess()) return@launch
                    val result = withContext(Dispatchers.IO) {
                        // Close the face model and frame retriever before starting audio decoding.
                        val frames = inspectVideo(context, uri)
                        ensureActive()
                        val audio = VideoAudioDecoder.analyze(context, uri)
                        frames.copy(
                            hasAudioTrack = audio.status != VideoAudioAnalysis.Status.NO_TRACK,
                            audio = audio
                        )
                    }
                    if (checkAccess()) {
                        summary = result
                        selectedUri = uri
                    }
                } catch (cause: CancellationException) {
                    throw cause
                } catch (cause: IllegalArgumentException) {
                    error = cause.message ?: "영상을 확인하지 못했어요. 다른 파일을 골라 주세요."
                } catch (_: Exception) {
                    error = "이 영상을 열거나 분석하지 못했어요. 다른 파일을 골라 주세요."
                } finally {
                    analyzing = false
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFFFF9F0))
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("저장된 영상 살펴보기", color = Color(0xFF24322E),
                fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onBack) { Text("닫기") }
        }
        Text("기기에서 영상의 일부 장면과 첫 10초 소리를 살펴봐요. 아이의 기분이나 욕구를 판정하지 않아요.",
            color = Color(0xFF65716B))
        Button(
            onClick = choose@{
                if (!checkAccess() || pickerPending || analyzing) return@choose
                pickerPending = true
                try { picker.launch(arrayOf("video/*")) }
                catch (_: Exception) { pickerPending = false; error = "영상 선택기를 열지 못했어요. 잠시 후 다시 시도해 주세요." }
            }, enabled = !analyzing && !pickerPending,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF24322E)),
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (selectedUri == null) "영상 고르기" else "다른 영상 고르기") }
        Text("30초 이내 · 50MB 이하 · 원본은 앱에 복사하거나 저장하지 않아요.",
            color = Color(0xFF65716B))
        if (analyzing) Text("영상의 장면과 첫 10초 소리를 확인하고 있어요…", color = Color(0xFF24322E))
        error?.let { Text(it, color = Color(0xFF9A4438)) }
        val current = summary
        val uri = selectedUri
        if (current != null && uri != null) {
            VideoPlayback(uri)
            Text(current.evidence(), color = Color(0xFF24322E))
            Text("영상을 재생해 직접 들은 소리를 확인해 주세요. 모델 후보는 영아의 뜻이나 화자를 알려주지 않아요.",
                color = Color(0xFF65716B))
            val candidate = current.audio.cues.firstOrNull()?.let { cue ->
                when (cue.label) {
                    "울음", "웃음" -> cue.label
                    "옹알이 후보" -> "옹알이"
                    else -> null
                }
            }
            if (candidate != null) {
                OutlinedButton(
                    onClick = { heardSound = candidate },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (heardSound == candidate) "✓ 직접 들어서 $candidate 확인했어요"
                        else "직접 들어서 $candidate 확인하기")
                }
            }
            if (current.audio.cues.any { it.label == "말소리(화자 미확인)" }) {
                Text("말소리 후보의 화자는 알 수 없어요. 옹알이로 자동 선택하지 않아요.",
                    color = Color(0xFF65716B))
            }
            val soundChoices = if (current.hasAudioTrack)
                listOf("울음", "웃음", "옹알이", "기타 소리", "소리 확인 어려움")
            else listOf("소리 없음")
            soundChoices.forEach { choice ->
                OutlinedButton(
                    onClick = { heardSound = choice }, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) { Text(if (heardSound == choice) "✓ $choice" else choice) }
            }
            Text("화면에서 직접 본 몸짓 · 선택 사항", color = Color(0xFF65716B))
            listOf("입 움직임", "고개 돌림", "눈 맞춤").forEach { choice ->
                OutlinedButton(
                    onClick = { manualNote = if (manualNote == choice) null else choice },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                ) { Text(if (manualNote == choice) "✓ $choice" else choice) }
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = translate@{
                    if (!checkAccess()) return@translate
                    onTranslate(
                        ImportedVideoObservation(
                            heardSound = if (heardSound == "소리 없음" || heardSound == "소리 확인 어려움")
                                "영상 장면" else heardSound,
                            manualNote = manualNote,
                            faceVisible = current.faceFrames > 0,
                            mouthOpen = current.mouthOpenFrames > 0,
                            evidence = current.evidence() + " 보호자가 확인한 소리: $heardSound.",
                            audioCues = current.audio.cues
                        )
                    )
                },
                enabled = heardSound.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9CC4B3))
            ) { Text("이 영상으로 문장 만들기", color = Color(0xFF17231D)) }
            Text("다음 화면에서 그때의 상황을 골라야 문장이 나와요.", color = Color(0xFF65716B))
        }
    }
}

@Composable
private fun VideoPlayback(uri: Uri) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val video = remember(uri) {
        VideoView(context).apply {
            val controls = MediaController(context)
            controls.setAnchorView(this)
            setMediaController(controls)
            setVideoURI(uri)
        }
    }
    DisposableEffect(video, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && video.isPlaying) video.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            video.stopPlayback()
        }
    }
    AndroidView(factory = { video }, modifier = Modifier.fillMaxWidth().height(220.dp))
}

private suspend fun inspectVideo(context: Context, uri: Uri): VideoFrameSummary {
    val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
    if (size <= 0L || size > 50L * 1024L * 1024L) {
        throw IllegalArgumentException("50MB 이하의 크기를 확인할 수 있는 영상을 골라 주세요.")
    }
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: 0L
        if (durationMs <= 0L || durationMs > 30_000L)
            throw IllegalArgumentException("재생 시간이 30초 이내인 영상을 골라 주세요.")
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull() ?: 0
        if (width <= 0 || height <= 0)
            throw IllegalArgumentException("영상 화면을 읽을 수 없어요. 다른 파일을 골라 주세요.")
        if (Build.VERSION.SDK_INT < 27 && width.toLong() * height > 1920L * 1080L)
            throw IllegalArgumentException("이 기기에서는 FHD 이하의 영상을 골라 주세요.")
        val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
        val durationSeconds = ((durationMs + 999) / 1000).toInt()
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
                val timeUs = (durationMs * 1000L * (index + 1)) / (sampleCount + 1)
                val frame = if (Build.VERSION.SDK_INT >= 27) {
                    retriever.getScaledFrameAtTime(timeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 512, 512)
                } else retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
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
