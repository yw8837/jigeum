package com.moa.babylanguage

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private val InterpretationSaver = Saver<ClipInterpretation?, String>(
    save = { it?.let { v -> JSONObject().put("phrase", v.phrase).put("sound", v.sound).put("evidence", v.evidence)
        .put("guide", v.guide).put("range", v.range).put("visual", v.visual).put("visualNotice", v.visualNotice).toString() } ?: "" },
    restore = { raw -> runCatching { JSONObject(raw).let { ClipInterpretation(it.getString("phrase"), it.getString("sound"),
        it.getString("evidence"), it.getString("guide"), it.getString("range"), it.getBoolean("visual"),
        if (it.has("visualNotice") && !it.isNull("visualNotice")) it.getString("visualNotice") else null) } }.getOrNull() }
)

/** One input and review flow. Test substitutes are supplied by androidTest, never persisted. */
@Composable
internal fun CaptureFlow(
    authorizeVisual: () -> Boolean,
    onBack: () -> Unit,
    saving: Boolean,
    onSave: (ClipInterpretation, String, String) -> Unit,
    passContent: @Composable (close: () -> Unit) -> Unit,
    analyzer: ClipAnalyzer = DeviceClipAnalyzer,
    initialClip: LocalClip? = null
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val capture = remember { LocalCapture(context.applicationContext) }
    val alive = remember { AtomicBoolean(true) }
    val currentAuthorize by rememberUpdatedState(authorizeVisual)
    var clip by remember { mutableStateOf(initialClip) }
    var result by rememberSaveable(stateSaver = InterpretationSaver) { mutableStateOf<ClipInterpretation?>(null) }
    var audioOnly by rememberSaveable { mutableStateOf(false) }
    var visual by rememberSaveable { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var cameraRecorder by remember { mutableStateOf<Recorder?>(null) }
    var facing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var canSwitch by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0) }
    var elapsed by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }
    var analyzing by remember { mutableStateOf(false) }
    var analyzingVisual by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var selection by remember { mutableStateOf(0f..minOf(initialClip?.durationMs?.toFloat() ?: 10_000f, 10_000f)) }
    var pass by remember { mutableStateOf(false) }
    var discardConfirmation by remember { mutableStateOf(false) }
    var generation by remember { mutableLongStateOf(0) }
    var edit by rememberSaveable { mutableStateOf(false) }
    var draftEdited by rememberSaveable { mutableStateOf(false) }
    var phrase by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var stopPlayback by remember { mutableIntStateOf(0) }

    fun cancelAnalysis() { generation++; job?.cancel(); job = null; analyzing = false; analyzingVisual = false; selecting = false }
    fun reset() {
        cancelAnalysis(); capture.discard(); recording = false; finishing = false
        clip?.ownedFile?.delete(); clip = null; result = null; hasPermission = false; cameraRecorder = null
        message = null; elapsed = 0; visual = false; edit = false; draftEdited = false; phrase = ""; note = ""
    }
    fun exit() {
        if (recording || clip != null || result != null) { stopPlayback++; discardConfirmation = true }
        else onBack()
    }
    fun acceptFile(file: File?, error: String?) {
        recording = false; finishing = false
        if (!alive.get()) { file?.delete(); return }
        if (error != null || file == null) { message = error ?: "촬영을 마치지 못했어요."; return }
        selecting = true
        val request = generation
        job = scope.launch {
            var adopted = false
            try {
                val inspected = withContext(Dispatchers.IO) { CaptureFiles.inspect(context, Uri.fromFile(file), file) }
                if (alive.get() && request == generation) {
                    clip = inspected; selection = 0f..minOf(inspected.durationMs.toFloat(), 10_000f)
                    result = null; message = null; adopted = true
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (request == generation) message = e.message ?: "녹화 파일을 읽지 못했어요." }
            finally { if (!adopted) file.delete(); if (request == generation) selecting = false }
        }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (alive.get() && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            hasPermission = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                (audioOnly || context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            permissionDenied = !hasPermission
            if (!hasPermission) message = "촬영에는 카메라와 마이크 권한이 필요해요. 소리만 녹음할 수도 있어요."
        }
    }
    fun prepare() {
        message = null
        val required = if (audioOnly) arrayOf(Manifest.permission.RECORD_AUDIO) else arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        hasPermission = required.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (!hasPermission) permissions.launch(required)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && alive.get()) {
            cancelAnalysis(); selecting = true; message = null
            val request = generation
            job = scope.launch {
                try {
                    val inspected = withContext(Dispatchers.IO) { CaptureFiles.inspect(context, uri) }
                    if (request == generation && alive.get()) {
                        clip?.ownedFile?.delete(); clip = inspected
                        selection = 0f..minOf(inspected.durationMs.toFloat(), 10_000f); result = null
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { if (request == generation) message = e.message ?: "영상을 열지 못했어요. 기기에 저장된 다른 파일을 골라 주세요." }
                finally { if (request == generation) selecting = false }
            }
        }
    }
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                stopPlayback++
                if (recording || finishing) {
                    capture.discard(); recording = false; finishing = false
                    message = "앱을 벗어나 촬영을 중단했어요. 다시 시작해 주세요."
                }
                if (analyzing) { cancelAnalysis(); message = "분석을 잠시 멈췄어요. 선택한 구간에서 다시 시작할 수 있어요." }
                // Imported URIs arrive after the picker returns; do not invalidate that request.
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            alive.set(false); generation++; job?.cancel(); capture.discard()
            clip?.ownedFile?.delete(); lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(recording) {
        if (recording) while (recording) {
            elapsed = ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt().coerceAtMost(60)
            if (elapsed >= 60) { finishing = true; recording = false; capture.stop(); break }
            delay(100)
        }
    }
    LaunchedEffect(analyzing, analyzingVisual) {
        if (analyzing && analyzingVisual) while (analyzing) {
            if (!currentAuthorize()) {
                cancelAnalysis(); visual = false
                message = "이용권 확인이 필요해 표정 분석을 멈췄어요. 같은 구간의 소리 분석은 무료로 계속할 수 있어요."
                break
            }
            delay(500)
        }
    }
    BackHandler { if (pass) pass = false else exit() }
    if (discardConfirmation) AlertDialog(onDismissRequest = { discardConfirmation = false },
        title = { Text("이 장면을 닫을까요?") }, text = { Text("임시 녹화·녹음과 아직 저장하지 않은 문장은 지워져요.") },
        confirmButton = { TextButton(onClick = { reset(); discardConfirmation = false; onBack() }) { Text("닫기") } },
        dismissButton = { TextButton(onClick = { discardConfirmation = false }) { Text("계속 보기") } })
    if (pass) { passContent { pass = false }; return }

    val current = clip
    val lockedResult = result
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { exit() }) { Text("닫기", color = Muted) }
            Text(when { lockedResult != null -> "아기의 한마디"; current != null -> "구간 고르기"; else -> "아기말 번역" },
                color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (current == null && lockedResult == null) {
                Text(if (recording) "편하게 담아 주세요" else "아기의 순간을 담아요", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("최대 1분 담고, 아기 소리가 들리는 구간만 골라요.", color = Muted, lineHeight = 23.sp)
                if (!audioOnly && hasPermission) {
                    CameraRecordingPreview(facing, onReady = { recorder, switch -> cameraRecorder = recorder; canSwitch = switch },
                        onError = { cameraRecorder = null; message = it })
                } else Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(24.dp)).background(PaleSage), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Mic, null, Modifier.size(64.dp), tint = Ink)
                }
                if (recording || finishing) {
                    Text("${ClipRange.clock(elapsed * 1000L)} / 1:00", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(if (audioOnly) "마이크로 녹음 중이에요" else "영상과 소리를 함께 녹화 중이에요", color = Muted)
                    PrimaryButton(if (finishing) "마무리 중…" else "촬영 마치기", {
                        finishing = true; recording = false; capture.stop()
                    }, enabled = !finishing)
                } else {
                    if (!hasPermission) PrimaryButton(if (audioOnly) "마이크 준비하기" else "카메라 준비하기", { prepare() })
                    else PrimaryButton(if (audioOnly) "녹음 시작" else "녹화 시작", {
                        // Permissions can change after preview preparation.
                        prepare()
                        if (hasPermission) {
                            generation++; message = null
                            val ok = if (audioOnly) capture.startAudio(::acceptFile)
                                else cameraRecorder?.let { capture.startVideo(it, ::acceptFile) } ?: false
                            if (ok) { elapsed = 0; startedAt = SystemClock.elapsedRealtime(); recording = true }
                            else message = "시작하지 못했어요. 권한이나 다른 앱의 카메라·마이크 사용을 확인해 주세요."
                        }
                    }, enabled = !selecting && (audioOnly || cameraRecorder != null))
                    if (permissionDenied) TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"))) }) { Text("앱 설정에서 권한 확인") }
                    if (hasPermission && !audioOnly && canSwitch) TextButton(onClick = {
                        cameraRecorder = null; facing = if (facing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    }) { Text("카메라 전환") }
                    TextButton(onClick = { audioOnly = !audioOnly; hasPermission = false; cameraRecorder = null; message = null; permissionDenied = false }) {
                        Text(if (audioOnly) "영상과 소리 함께 담기" else "카메라 없이 소리만 녹음", color = Ink)
                    }
                    HorizontalDivider(color = Line)
                    TextButton(onClick = {
                        if (!selecting) runCatching { picker.launch(arrayOf("video/*")) }.onFailure { message = "영상 선택기를 열지 못했어요." }
                    }, enabled = !selecting) { Text("저장된 영상 가져오기", color = Ink) }
                    Text("촬영·재생·소리 분석은 무료예요. 원본은 기기 안에서만 임시로 보관해요.", color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
                }
            }
            if (current != null) {
                ClipPlayerPane(current, selection.start.toLong(), selection.endInclusive.toLong(), stopPlayback, enabled = !analyzing && !selecting)
            }
            if (lockedResult == null && current != null) {
                Text("어느 순간을 들어볼까요?", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("${ClipRange.clock(selection.start.toLong())} — ${ClipRange.clock(selection.endInclusive.toLong())} · ${((selection.endInclusive - selection.start)/1000).toInt()}초 선택",
                    color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("selected-range"))
                RangeSlider(value = selection, onValueChange = { proposed ->
                    val start = (proposed.start / 1000).toInt() * 1000f
                    val end = minOf(current.durationMs.toFloat(), kotlin.math.round(proposed.endInclusive / 1000) * 1000f)
                    if (end - start in 1000f..15000f) { selection = start..end; message = null; stopPlayback++ }
                }, valueRange = 0f..current.durationMs.toFloat(), enabled = !analyzing,
                    modifier = Modifier.testTag("clip-range"))
                Text("양 끝을 움직여 1–15초를 골라 주세요. 재생하면 선택한 구간만 들려요.", color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
                if (current.hasVideo) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("표정도 함께 보기", color = Ink, fontWeight = FontWeight.SemiBold)
                            Text("전체 이용권 · 같은 구간의 얼굴·입 관찰", color = Muted, fontSize = 12.sp)
                        }
                        Switch(checked = visual, enabled = !analyzing, onCheckedChange = {
                            stopPlayback++
                            if (!it) visual = false
                            else if (currentAuthorize()) visual = true
                            else pass = true
                        }, modifier = Modifier.testTag("visual-analysis").semantics { contentDescription = "표정도 함께 보기" })
                    }
                }
                if (analyzing) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("선택한 구간의 ${if (visual) "소리와 표정" else "소리"}를 살펴보고 있어요…", color = Ink)
                    TextButton(onClick = { cancelAnalysis(); message = "분석을 취소했어요. 구간을 다시 고를 수 있어요." }) { Text("분석 취소") }
                } else PrimaryButton(if (visual && current.hasVideo) "영상 + 소리로 번역하기" else "이 구간 소리로 번역하기", {
                    if (!alive.get()) return@PrimaryButton
                    val useVisual = visual && current.hasVideo
                    if (useVisual && !currentAuthorize()) { visual = false; pass = true; return@PrimaryButton }
                    stopPlayback++; cancelAnalysis(); analyzingVisual = useVisual; analyzing = true; message = null
                    val request = generation
                    val range = ClipRange(selection.start.toLong(), selection.endInclusive.toLong())
                    job = scope.launch {
                        try {
                            val analyzed = withTimeout(45_000) { analyzer.analyze(context, current.uri, range, useVisual) }
                            ensureActive()
                            if (request == generation && alive.get()) {
                                if (useVisual && !currentAuthorize()) {
                                    visual = false; message = "이용권이 만료됐어요. 소리 분석으로 다시 볼 수 있어요."
                                } else { result = analyzed.interpretation; message = analyzed.problem }
                            }
                        } catch (_: TimeoutCancellationException) { if (request == generation) message = "분석이 오래 걸려 멈췄어요. 구간을 줄여 다시 시도해 주세요." }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { if (request == generation) message = "이 구간을 분석하지 못했어요. 다시 재생해 보고 다른 구간이나 파일을 골라 주세요." }
                        catch (_: LinkageError) { if (request == generation) message = "이 기기에서 분석 모델을 실행하지 못했어요." }
                        finally { if (request == generation) analyzing = false }
                    }
                })
                if (!analyzing) TextButton(onClick = { reset() }) { Text("다시 촬영하기", color = Muted) }
            }
            if (lockedResult != null) {
                Text("소리를 바탕으로 상상한 아기말", color = Muted, fontSize = 14.sp)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(PaleSage).padding(24.dp)) {
                    Text(lockedResult.phrase, color = Ink, fontSize = 25.sp, lineHeight = 37.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("baby-phrase"))
                }
                Text("${lockedResult.range} · ${if (lockedResult.visual) "소리 + 표정 관찰" else "소리 관찰"}", color = Muted, fontSize = 13.sp)
                lockedResult.visualNotice?.let {
                    Text(it, color = Ink, lineHeight = 23.sp, modifier = Modifier.fillMaxWidth()
                        .background(PaleApricot, RoundedCornerShape(16.dp)).padding(16.dp).testTag("visual-notice"))
                }
                Text("아이의 의도를 확정한 번역은 아니에요. 실제 모습과 함께 살펴주세요.", color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
                var expanded by remember { mutableStateOf(false) }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "관찰 근거 접기" else "어떤 소리를 들었나요?", color = Ink) }
                if (expanded) Text(lockedResult.evidence, color = Ink, lineHeight = 23.sp, modifier = Modifier.testTag("analysis-evidence"))
                Text("이렇게 응답해 주세요", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(lockedResult.guide, color = Muted, lineHeight = 23.sp)
                TextButton(onClick = { edit = !edit; if (edit && !draftEdited) phrase = lockedResult.phrase }) { Text(if (edit) "기록 수정 접기" else "기록에 메모·수정 남기기", color = Muted) }
                if (edit) {
                    OutlinedTextField(value = phrase, onValueChange = { if (it.length <= 160) { phrase = it; draftEdited = true } }, label = { Text("보호자가 남길 문장") },
                        modifier = Modifier.fillMaxWidth().testTag("draft-phrase-editor"), minLines = 2)
                    OutlinedTextField(value = note, onValueChange = { if (it.length <= 120) note = it }, label = { Text("보호자 메모 · 분석에 사용하지 않아요") },
                        modifier = Modifier.fillMaxWidth(), minLines = 2)
                }
                TextButton(onClick = { result = null; edit = false; draftEdited = false; phrase = ""; note = ""; message = null }) { Text(if (current != null) "다른 구간 살펴보기" else "새로 촬영하기", color = Muted) }
            }
            if (selecting) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("파일을 준비하고 있어요", color = Muted) }
            message?.let { text ->
                val messageView = remember { BringIntoViewRequester() }
                LaunchedEffect(text) { messageView.bringIntoView() }
                Text(text, color = Ink, lineHeight = 23.sp, modifier = Modifier.fillMaxWidth()
                    .bringIntoViewRequester(messageView).background(PaleApricot, RoundedCornerShape(16.dp))
                    .padding(16.dp).testTag("capture-message"))
            }
        }
        if (lockedResult != null) Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)) {
            PrimaryButton(if (saving) "저장하고 있어요" else "이 문장 저장하기", {
                onSave(lockedResult, if (draftEdited) phrase.trim() else lockedResult.phrase, note.trim())
            }, enabled = !saving && (!draftEdited || phrase.isNotBlank()))
        }
    }
}

@Composable
private fun CameraRecordingPreview(facing: Int, onReady: (Recorder, Boolean) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val ready by rememberUpdatedState(onReady)
    val error by rememberUpdatedState(onError)
    val view = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    DisposableEffect(owner, facing) {
        var disposed = false
        var provider: ProcessCameraProvider? = null
        val preview = Preview.Builder().build()
        val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.SD, FallbackStrategy.higherQualityOrLowerThan(Quality.SD))).build()
        val video = VideoCapture.withOutput(recorder)
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!disposed) try {
                // Permission may be revoked while the provider future is waiting.
                check(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                val active = future.get().also { provider = it }
                val back = active.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                val front = active.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                val selector = if ((facing == CameraSelector.LENS_FACING_BACK && back) || !front)
                    CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                preview.setSurfaceProvider(view.surfaceProvider)
                active.bindToLifecycle(owner, selector, preview, video)
                if (!disposed) ready(recorder, back && front)
            } catch (_: Exception) { if (!disposed) error("카메라를 준비하지 못했어요. 소리만 녹음하거나 저장 영상을 골라 주세요.") }
        }, ContextCompat.getMainExecutor(context))
        onDispose { disposed = true; provider?.unbind(preview, video) }
    }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(24.dp)))
}

@Composable
internal fun ClipPlayerPane(clip: LocalClip, startMs: Long, endMs: Long, stopSignal: Int = 0, enabled: Boolean = true) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(clip.uri) { MediaPlayer() }
    var prepared by remember(clip.uri) { mutableStateOf(false) }
    var playing by remember(clip.uri) { mutableStateOf(false) }
    var pendingPlay by remember(clip.uri) { mutableStateOf(false) }
    var position by remember(clip.uri) { mutableLongStateOf(startMs) }
    var error by remember(clip.uri) { mutableStateOf<String?>(null) }
    val bounds by rememberUpdatedState(startMs..endMs)
    val canPlay by rememberUpdatedState(enabled)
    DisposableEffect(player) {
        var disposed = false
        player.setOnPreparedListener { if (!disposed) { prepared = true; it.seekTo(bounds.first, MediaPlayer.SEEK_CLOSEST) } }
        player.setOnErrorListener { _, _, _ -> if (!disposed) { error = "이 파일은 재생하지 못했어요. 다른 파일을 골라 주세요."; playing = false }; true }
        player.setOnCompletionListener { if (!disposed) playing = false }
        player.setOnSeekCompleteListener {
            if (!disposed && pendingPlay && canPlay) {
                pendingPlay = false
                runCatching { it.start(); playing = true }.onFailure { error = "재생을 시작하지 못했어요." }
            }
        }
        runCatching { player.setDataSource(context, clip.uri); player.prepareAsync() }.onFailure { error = "파일을 재생하지 못했어요." }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) {
            pendingPlay = false
            // pause() is invalid in Prepared state and sends MediaPlayer into Error.
            if (prepared) runCatching { if (player.isPlaying) player.pause() }
            playing = false
        } }
        lifecycle.addObserver(observer)
        onDispose { disposed = true; lifecycle.removeObserver(observer); player.release() }
    }
    LaunchedEffect(startMs, endMs, stopSignal, enabled) {
        pendingPlay = false
        if (prepared) { runCatching { if (player.isPlaying) player.pause(); player.seekTo(startMs, MediaPlayer.SEEK_CLOSEST) }; position = startMs }
        playing = false
    }
    LaunchedEffect(playing) {
        while (playing) {
            position = runCatching { player.currentPosition.toLong() }.getOrDefault(endMs)
            if (position >= endMs) { runCatching { player.pause() }; playing = false; position = endMs }
            delay(40)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (clip.hasVideo) Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(20.dp)).background(Ink), contentAlignment = Alignment.Center) {
        AndroidView(factory = {
            SurfaceView(it).apply { holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) { runCatching { player.setDisplay(holder) } }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                override fun surfaceDestroyed(holder: SurfaceHolder) { runCatching { player.setDisplay(null) } }
            }) }
        }, modifier = Modifier.aspectRatio(clip.aspectRatio, matchHeightConstraintsFirst = clip.aspectRatio < 1f).fillMaxSize())
        }
        else Row(Modifier.fillMaxWidth().background(PaleSage, RoundedCornerShape(20.dp)).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Mic, null, tint = Ink); Spacer(Modifier.width(12.dp)); Text("녹음한 소리", color = Ink)
        }
        OutlinedButton(onClick = {
            if (prepared) runCatching {
                if (playing || pendingPlay) { pendingPlay = false; if (player.isPlaying) player.pause(); playing = false }
                else { pendingPlay = true; player.seekTo(startMs, MediaPlayer.SEEK_CLOSEST) }
            }.onFailure { error = "재생을 시작하지 못했어요." }
        }, enabled = prepared && error == null && enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("clip-play")) {
            Text(if (playing) "재생 멈추기 · ${ClipRange.clock(position)}" else "선택 구간 다시 듣기${if (clip.hasVideo) " · 영상 함께 보기" else ""}")
        }
        error?.let { Text(it, color = Ink, fontSize = 13.sp) }
    }
}
