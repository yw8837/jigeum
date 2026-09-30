package com.moa.babylanguage

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import java.nio.ByteBuffer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.hypot

/**
 * A short, local-only observation. Nothing is recorded, saved, uploaded, or classified as
 * a baby's emotion or need. Face Landmarker supplies visible geometry only.
 */
data class CameraObservation(
    val faceVisible: Boolean,
    val mouthOpen: Boolean,
    val manualNote: String?
)

@Composable
fun CameraScanScreen(
    onBack: () -> Unit,
    onTranslate: ((CameraObservation) -> Unit)? = null,
    authorize: () -> Boolean = { false }
) {
    val context = LocalContext.current
    val currentAuthorize by rememberUpdatedState(authorize)
    val alive = remember { AtomicBoolean(true) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var active by remember { mutableStateOf(false) }
    var observation by remember { mutableStateOf(FaceObservation.WAITING) }
    var error by remember { mutableStateOf<String?>(null) }
    var warning by remember { mutableStateOf<String?>(null) }
    var cameraReady by remember { mutableStateOf(false) }
    var requestedCamera by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var activeCamera by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var canSwitchCamera by remember { mutableStateOf(false) }
    var manualNote by remember { mutableStateOf<String?>(null) }
    fun checkAccess(): Boolean {
        if (!alive.get()) return false
        if (currentAuthorize()) return true
        active = false
        cameraReady = false
        observation = FaceObservation.WAITING
        manualNote = null
        return false
    }
    DisposableEffect(Unit) { onDispose { alive.set(false) } }
    LaunchedEffect(Unit) {
        while (checkAccess()) delay(1000)
    }
    val permissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!checkAccess()) return@rememberLauncherForActivityResult
        hasPermission = granted
        active = granted
        if (!granted) error = "카메라 권한이 없어 관찰을 시작할 수 없어요."
    }
    LaunchedEffect(active, cameraReady) {
        if (active && !cameraReady) {
            delay(7000)
            if (active && !cameraReady && error == null) error = "카메라 화면을 열지 못했어요. 관찰을 끝내고 다시 시작해 주세요."
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFFFFF9F0))
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("잠깐 살펴보기", color = Color(0xFF24322E), fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { active = false; cameraReady = false; onBack() },
                border = BorderStroke(1.dp, Color(0xFFA8C6A0)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF24322E))) {
                Text("닫기")
            }
        }
        Text(
            "얼굴과 입의 움직임만 화면에 표시해요. 아이의 기분이나 울음의 뜻을 판정하지 않아요.",
            color = Color(0xFF65716B), style = MaterialTheme.typography.bodyMedium
        )

        Box(
            modifier = Modifier.fillMaxWidth().height(280.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFFDEE8D9), RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (active && hasPermission) {
                CameraObservationPreview(
                    requestedCamera = requestedCamera,
                    onObservation = { observation = it },
                    onError = { error = it; cameraReady = false },
                    onWarning = { warning = it },
                    onReady = { facing, canSwitch ->
                        if (checkAccess()) {
                            activeCamera = facing
                            canSwitchCamera = canSwitch
                            cameraReady = true
                            error = null
                        }
                    }
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)) {
                    Text("카메라가 꺼져 있어요", color = Color(0xFF24322E),
                        style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("시작을 누르면 이 화면에서만 후면 카메라를 사용해요. 후면 카메라가 없으면 전면 카메라로 열어요.",
                        color = Color(0xFF65716B), textAlign = TextAlign.Center)
                }
            }
        }

        if (active && hasPermission) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (cameraReady) {
                        if (activeCamera == CameraSelector.LENS_FACING_BACK) "후면 카메라" else "전면 카메라"
                    } else "카메라 연결 중",
                    color = Color(0xFF65716B), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                if (cameraReady && canSwitchCamera) {
                    OutlinedButton(
                        onClick = switch@{
                            if (!checkAccess()) return@switch
                            cameraReady = false
                            canSwitchCamera = false
                            observation = FaceObservation.WAITING
                            manualNote = null
                            error = null
                            warning = null
                            requestedCamera = if (activeCamera == CameraSelector.LENS_FACING_BACK)
                                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                        },
                        border = BorderStroke(1.dp, Color(0xFFA8C6A0)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF24322E))
                    ) { Text("전후면 전환") }
                }
            }
            Text(
                if (warning != null) "화면을 보고 장면을 직접 골라 주세요" else when (observation) {
                    FaceObservation.WAITING -> "얼굴을 화면에 맞춰 주세요"
                    FaceObservation.NO_FACE -> "얼굴이 보이지 않아요"
                    FaceObservation.FACE_VISIBLE -> "얼굴이 보여요"
                    FaceObservation.MOUTH_OPEN -> "얼굴이 보이고 입이 벌어져 있어요"
                },
                color = Color(0xFF24322E), style = MaterialTheme.typography.titleMedium
            )
        }
        error?.let {
            Text(it, color = Color(0xFF9A4438), style = MaterialTheme.typography.bodyMedium)
        }
        warning?.let {
            Text(it, color = Color(0xFF65716B), style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!active) {
                Button(
                    onClick = start@{
                        if (!checkAccess()) return@start
                        error = null
                        warning = null
                        cameraReady = false
                        observation = FaceObservation.WAITING
                        requestedCamera = CameraSelector.LENS_FACING_BACK
                        canSwitchCamera = false
                        if (hasPermission) active = true
                        else permissionRequest.launch(Manifest.permission.CAMERA)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0BB83))
                ) { Text("카메라 시작", color = Color(0xFF1F2525)) }
            } else {
                Button(onClick = { active = false; cameraReady = false; observation = FaceObservation.WAITING },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0BB83))) {
                    Text("관찰 끝내기", color = Color(0xFF1F2525))
                }
            }
        }
        if (onTranslate != null) {
            Button(
                onClick = translate@{
                    if (!checkAccess()) return@translate
                    val cue = CameraObservation(
                        faceVisible = observation == FaceObservation.FACE_VISIBLE ||
                            observation == FaceObservation.MOUTH_OPEN,
                        mouthOpen = observation == FaceObservation.MOUTH_OPEN,
                        manualNote = manualNote
                    )
                    active = false
                    onTranslate(cue)
                },
                enabled = active && hasPermission && cameraReady && error == null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF9CC4B3),
                    contentColor = Color(0xFF17231D),
                    disabledContainerColor = Color(0xFFDEE8D9),
                    disabledContentColor = Color(0xFF65716B)
                ),
                modifier = Modifier.fillMaxWidth()
            ) { Text("이 장면으로 문장 만들기") }
        }
        Text("직접 본 것을 골라 두세요. 이 화면을 닫으면 선택 내용은 사라져요.",
            color = Color(0xFF65716B), style = MaterialTheme.typography.bodySmall)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("입 움직임", "고개 돌림", "눈 맞춤").forEach { note ->
                OutlinedButton(onClick = { manualNote = if (manualNote == note) null else note },
                    border = BorderStroke(1.dp, Color(0xFFA8C6A0)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF24322E)),
                    modifier = Modifier.fillMaxWidth()) {
                    Text(if (manualNote == note) "✓ $note" else note)
                }
            }
        }
    }
}

private enum class FaceObservation { WAITING, NO_FACE, FACE_VISIBLE, MOUTH_OPEN }

@Composable
private fun CameraObservationPreview(
    requestedCamera: Int,
    onObservation: (FaceObservation) -> Unit,
    onError: (String) -> Unit,
    onWarning: (String) -> Unit,
    onReady: (facing: Int, canSwitch: Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentObservation by rememberUpdatedState(onObservation)
    val currentError by rememberUpdatedState(onError)
    val currentWarning by rememberUpdatedState(onWarning)
    val currentReady by rememberUpdatedState(onReady)
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(lifecycleOwner, previewView, requestedCamera) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val worker = Executors.newSingleThreadExecutor()
        val disposed = AtomicBoolean(false)
        val preview = Preview.Builder().build()
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        var provider: ProcessCameraProvider? = null
        var landmarker: FaceLandmarker? = null
        var lastInferenceMs = 0L

        worker.execute {
            val model = try {
                val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(BaseOptions.builder()
                        .setModelAssetPath("face_landmarker.task").build())
                    .setRunningMode(RunningMode.VIDEO)
                    .setNumFaces(1)
                    .build()
                FaceLandmarker.createFromOptions(context, options)
            } catch (cause: Exception) {
                android.util.Log.e("MoaFaceInit", "Camera face model initialization failed", cause)
                mainExecutor.execute {
                    if (!disposed.get()) currentWarning("얼굴 분석은 사용할 수 없어요. 화면을 보고 직접 장면을 골라 주세요.")
                }
                null
            } catch (cause: LinkageError) {
                mainExecutor.execute {
                    if (!disposed.get()) currentWarning("이 기기에서는 얼굴 분석을 지원하지 않아요. 직접 본 장면으로 진행할 수 있어요.")
                }
                null
            }
            landmarker = model
            if (disposed.get()) {
                model?.close()
                landmarker = null
                return@execute
            }

            analysis.setAnalyzer(worker) { image ->
                try {
                    val now = SystemClock.uptimeMillis()
                    if (model != null && now - lastInferenceMs >= 180) {
                        lastInferenceMs = now
                        val bitmap = image.toUprightBitmap()
                        val result = try {
                            // MPImage.close() also recycles its BitmapImageContainer bitmap.
                            BitmapImageBuilder(bitmap).build().use { mpImage ->
                                model.detectForVideo(mpImage, now)
                            }
                        } finally {
                            // Covers a failed MPImage build without recycling an already closed image twice.
                            if (!bitmap.isRecycled) bitmap.recycle()
                        }
                        val landmarks = result.faceLandmarks().firstOrNull()
                        val state = when {
                            landmarks == null -> FaceObservation.NO_FACE
                            landmarks.size <= 291 -> FaceObservation.FACE_VISIBLE
                            else -> {
                                val upper = landmarks[13]
                                val lower = landmarks[14]
                                val left = landmarks[61]
                                val right = landmarks[291]
                                val opening = hypot(
                                    (upper.x() - lower.x()).toDouble(),
                                    (upper.y() - lower.y()).toDouble()
                                )
                                val width = hypot(
                                    (left.x() - right.x()).toDouble(),
                                    (left.y() - right.y()).toDouble()
                                )
                                if (width > 0.001 && opening / width > 0.12)
                                    FaceObservation.MOUTH_OPEN
                                else FaceObservation.FACE_VISIBLE
                            }
                        }
                        mainExecutor.execute {
                            if (!disposed.get()) currentObservation(state)
                        }
                    }
                } catch (cause: Exception) {
                    mainExecutor.execute {
                        if (!disposed.get()) currentError("얼굴 분석을 잠시 사용할 수 없어요.")
                    }
                } finally {
                    image.close()
                }
            }

            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                if (disposed.get()) return@addListener
                try {
                    provider = providerFuture.get()
                    val backAvailable = provider!!.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                    val frontAvailable = provider!!.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                    val actualCamera = when (requestedCamera) {
                        CameraSelector.LENS_FACING_BACK -> when {
                            backAvailable -> CameraSelector.LENS_FACING_BACK
                            frontAvailable -> CameraSelector.LENS_FACING_FRONT
                            else -> throw IllegalStateException("No camera available")
                        }
                        else -> when {
                            frontAvailable -> CameraSelector.LENS_FACING_FRONT
                            backAvailable -> CameraSelector.LENS_FACING_BACK
                            else -> throw IllegalStateException("No camera available")
                        }
                    }
                    val selector = if (actualCamera == CameraSelector.LENS_FACING_BACK)
                        CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                    preview.setSurfaceProvider(previewView.surfaceProvider)
                    provider?.bindToLifecycle(
                        lifecycleOwner, selector, preview, analysis
                    )
                    currentReady(actualCamera, backAvailable && frontAvailable)
                } catch (cause: Exception) {
                    currentError("카메라를 열지 못했어요. 기기 카메라를 확인해 주세요.")
                }
            }, mainExecutor)
        }

        onDispose {
            disposed.set(true)
            analysis.clearAnalyzer()
            provider?.unbind(preview, analysis)
            worker.execute { landmarker?.close(); landmarker = null }
            worker.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

private fun ImageProxy.toUprightBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        val plane = planes[0]
        val source = plane.buffer.duplicate()
        source.rewind()
        val rowBytes = width * 4
        if (plane.rowStride == rowBytes && plane.pixelStride == 4) {
            bitmap.copyPixelsFromBuffer(source)
        } else {
            val pixels = ByteArray(rowBytes * height)
            if (plane.pixelStride == 4) {
                for (row in 0 until height) {
                    source.position(row * plane.rowStride)
                    source.get(pixels, row * rowBytes, rowBytes)
                }
            } else {
                for (row in 0 until height) {
                    for (column in 0 until width) {
                        source.position(row * plane.rowStride + column * plane.pixelStride)
                        source.get(pixels, row * rowBytes + column * 4, 4)
                    }
                }
            }
            bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(pixels))
        }
        val rotation = imageInfo.rotationDegrees
        if (rotation == 0) return bitmap
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val upright = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
        if (upright !== bitmap) bitmap.recycle()
        return upright
    } catch (cause: Throwable) {
        if (!bitmap.isRecycled) bitmap.recycle()
        throw cause
    }
}
