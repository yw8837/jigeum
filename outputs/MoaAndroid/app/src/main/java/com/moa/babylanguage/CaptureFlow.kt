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
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.semantics.Role
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
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal object InterpretationState {
    fun encode(value: ClipInterpretation?): String = value?.let { v -> JSONObject().put("phrase", v.phrase).put("sound", v.sound).put("evidence", v.evidence)
        .put("guide", v.guide).put("range", v.range).put("visual", v.visual).put("visualNotice", v.visualNotice)
        .put("basis", ExpressionBasis.normalize(v.basis)).toString() } ?: ""
    fun decode(raw: String): ClipInterpretation? = runCatching { JSONObject(raw).let { ClipInterpretation(it.getString("phrase"), it.getString("sound"),
        it.getString("evidence"), it.getString("guide"), it.getString("range"), it.getBoolean("visual"),
        if (it.has("visualNotice") && !it.isNull("visualNotice")) it.getString("visualNotice") else null,
        ExpressionBasis.normalize(it.optString("basis"))) } }.getOrNull()
}

private val InterpretationSaver = Saver<ClipInterpretation?, String>(
    save = { InterpretationState.encode(it) }, restore = { InterpretationState.decode(it) }
)
private val SelectionSaver = Saver<ClosedFloatingPointRange<Float>, List<Float>>(
    save = { listOf(it.start, it.endInclusive) }, restore = { it[0]..it[1] }
)

internal data class CaptureSaveReceipt(val recordId: Long, val phrase: String, val note: String, val withMedia: Boolean)

/** One input and review flow. Test substitutes are supplied by androidTest, never persisted. */
@Composable
internal fun CaptureFlow(
    authorizeVisual: () -> Boolean,
    onBack: () -> Unit,
    saving: Boolean,
    onSave: (ClipInterpretation, String, String) -> Unit,
    passContent: @Composable (close: () -> Unit) -> Unit,
    analyzer: ClipAnalyzer = DeviceClipAnalyzer,
    initialClip: LocalClip? = null,
    testDistribution: Boolean = false,
    onSaveWithMedia: ((ClipInterpretation, String, String, LocalClip, ClipRange) -> Unit)? = null,
    onGallery: ((ClipInterpretation, String, LocalClip?, ClipRange?, Boolean) -> Unit)? = null,
    onSaveVideo: ((LocalClip, ClipRange) -> Unit)? = null,
    saveReceipt: CaptureSaveReceipt? = null,
    onViewSaved: ((Long) -> Unit)? = null,
    onCommunity: ((ClipInterpretation, String, LocalClip?, ClipRange?) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val capture = remember { LocalCapture(context.applicationContext) }
    val alive = remember { AtomicBoolean(true) }
    val sourceJobs = remember { Collections.synchronizedSet(mutableSetOf<Job>()) }
    fun trackSourceJob(task: Job): Job {
        sourceJobs.add(task)
        task.invokeOnCompletion { sourceJobs.remove(task) }
        return task
    }
    fun discardOwnedFile(file: File?) {
        if (file == null) return
        // Cancellation can outlive the UI while a decoder finishes native/IO work.
        val readers = synchronized(sourceJobs) { sourceJobs.toList() }
        if (readers.isEmpty()) file.delete()
        else {
            val remaining = AtomicInteger(readers.size)
            readers.forEach { task -> task.invokeOnCompletion { if (remaining.decrementAndGet() == 0) file.delete() } }
        }
    }
    val currentAuthorize by rememberUpdatedState(authorizeVisual)
    fun hasFullAccess() = testDistribution || currentAuthorize()
    fun liveLimits() = CaptureLimits.forAccess(hasFullAccess())
    var limits by remember { mutableStateOf(liveLimits()) }
    var recordingLimits by remember { mutableStateOf(limits) }
    var analyzingExtended by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) { limits = liveLimits(); delay(500) }
    }
    var clip by remember { mutableStateOf(initialClip) }
    var result by rememberSaveable(stateSaver = InterpretationSaver) { mutableStateOf<ClipInterpretation?>(null) }
    var audioOnly by rememberSaveable { mutableStateOf(false) }
    var visual by rememberSaveable { mutableStateOf(testDistribution && initialClip?.hasVideo == true) }
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
    var picking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var withheldAnalysis by remember { mutableStateOf<ClipAnalysisResult?>(null) }
    var selection by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf(0f..minOf(initialClip?.durationMs?.toFloat() ?: 10_000f, 10_000f)) }
    var editingResult by rememberSaveable { mutableStateOf(false) }
    var resultStart by rememberSaveable { mutableLongStateOf(0) }
    var resultEnd by rememberSaveable { mutableLongStateOf(10_000) }
    var pass by remember { mutableStateOf(false) }
    var discardConfirmation by remember { mutableStateOf(false) }
    var newCaptureConfirmation by remember { mutableStateOf(false) }
    var generation by remember { mutableLongStateOf(0) }
    var edit by rememberSaveable { mutableStateOf(false) }
    var draftEdited by rememberSaveable { mutableStateOf(false) }
    var phrase by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var stopPlayback by remember { mutableIntStateOf(0) }
    var keepScene by rememberSaveable { mutableStateOf(true) }
    var savedForResult by remember { mutableStateOf<CaptureSaveReceipt?>(null) }
    LaunchedEffect(saveReceipt) {
        if (result != null && !editingResult) savedForResult = saveReceipt
    }
    val contentScroll = rememberScrollState()
    val navigationLocked = saving || selecting || picking
    LaunchedEffect(clip?.uri, result != null && !editingResult) {
        // Each editor/result phase opens at its heading rather than inheriting the prior scroll.
        contentScroll.scrollTo(0)
    }

    fun clearDraft() { edit = false; draftEdited = false; phrase = ""; note = ""; savedForResult = null }

    fun permissionsGranted() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
        (audioOnly || context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)

    // Only prepare already-authorized hardware. Permission prompts and recording require a tap.
    LaunchedEffect(audioOnly, clip, result) {
        if (clip == null && result == null) hasPermission = permissionsGranted()
    }

    fun cancelAnalysis() { generation++; job?.cancel(); job = null; analyzing = false; analyzingVisual = false; analyzingExtended = false; selecting = false; withheldAnalysis = null }
    fun reset() {
        cancelAnalysis(); capture.discard(); recording = false; finishing = false
        discardOwnedFile(clip?.ownedFile); clip = null; result = null; hasPermission = false; cameraRecorder = null
        message = null; elapsed = 0; visual = false; editingResult = false; clearDraft()
    }
    fun exit() {
        if (navigationLocked) return
        if (recording || clip != null || result != null) { stopPlayback++; discardConfirmation = true }
        else onBack()
    }
    fun editAnotherRange() {
        if (navigationLocked || clip == null || result == null) return
        stopPlayback++; editingResult = true; message = null
    }
    fun returnToResult() {
        if (navigationLocked) return
        stopPlayback++; selection = resultStart.toFloat()..resultEnd.toFloat()
        editingResult = false; message = null; withheldAnalysis = null
    }
    fun requestNewCapture() {
        if (navigationLocked) return
        stopPlayback++
        if (result != null) newCaptureConfirmation = true else reset()
    }
    fun acceptFile(file: File?, error: String?) {
        recording = false; finishing = false
        if (!alive.get()) { file?.delete(); return }
        if (error != null || file == null) { message = error ?: "촬영을 마치지 못했어요."; return }
        selecting = true
        val request = generation
        job = trackSourceJob(scope.launch {
            var adopted = false
            try {
                val inspected = withContext(Dispatchers.IO) { CaptureFiles.inspect(context, Uri.fromFile(file), file, recordingLimits) }
                if (alive.get() && request == generation) {
                    clip = inspected; selection = 0f..minOf(inspected.durationMs.toFloat(), 10_000f)
                    visual = testDistribution && inspected.hasVideo
                    result = null; message = null; adopted = true
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (request == generation) message = e.message ?: "녹화 파일을 읽지 못했어요." }
            finally { if (!adopted) file.delete(); if (request == generation) selecting = false }
        })
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (alive.get() && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            hasPermission = permissionsGranted()
            permissionDenied = !hasPermission
            if (!hasPermission) message = if (audioOnly) "녹음에는 마이크 권한이 필요해요. 앱 설정에서 허용해 주세요."
                else "촬영에는 카메라와 마이크 권한이 필요해요. 소리만 녹음할 수도 있어요."
        }
    }
    fun prepare() {
        message = null
        val required = if (audioOnly) arrayOf(Manifest.permission.RECORD_AUDIO) else arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        hasPermission = required.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (!hasPermission) permissions.launch(required)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        picking = false
        if (uri != null && alive.get()) {
            cancelAnalysis(); selecting = true; message = null
            val request = generation
            job = trackSourceJob(scope.launch {
                try {
                    val importLimits = liveLimits()
                    val inspected = withContext(Dispatchers.IO) { CaptureFiles.inspect(context, uri, limits = importLimits) }
                    if (request == generation && alive.get()) {
                        discardOwnedFile(clip?.ownedFile); clip = inspected
                        visual = testDistribution && inspected.hasVideo
                        selection = 0f..minOf(inspected.durationMs.toFloat(), 10_000f); result = null
                        editingResult = false; clearDraft()
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { if (request == generation) message = e.message ?: "영상을 열지 못했어요. 기기에 저장된 다른 파일을 골라 주세요." }
                finally { if (request == generation) selecting = false }
            })
        }
    }
    fun chooseFile() {
        if (navigationLocked || analyzing || recording || finishing) return
        stopPlayback++; picking = true
        try { picker.launch(arrayOf("video/*", "audio/*")) }
        catch (_: Exception) { picking = false; message = "파일 선택기를 열지 못했어요. 현재 장면은 그대로 있어요." }
    }
    DisposableEffect(Unit) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && clip == null && result == null) {
                hasPermission = permissionsGranted()
                if (hasPermission) permissionDenied = false
            }
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
            discardOwnedFile(clip?.ownedFile); lifecycle.removeObserver(observer)
        }
    }
    LaunchedEffect(recording) {
        if (recording) while (recording) {
            val allowed = minOf(recordingLimits.captureMs, liveLimits().captureMs)
            elapsed = ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt()
            if (elapsed * 1000L >= allowed) { finishing = true; recording = false; capture.stop(); break }
            delay(100)
        }
    }
    LaunchedEffect(analyzing, analyzingVisual, analyzingExtended) {
        if (analyzing && (analyzingVisual || analyzingExtended)) while (analyzing) {
            if (!hasFullAccess()) {
                cancelAnalysis(); visual = false
                message = "이용권 확인이 필요해 분석을 멈췄어요. 10초 이내 구간의 소리 분석은 무료로 계속할 수 있어요."
                break
            }
            delay(500)
        }
    }
    LaunchedEffect(limits, clip, result, analyzing, editingResult) {
        if ((result == null || editingResult) && !analyzing && selection.endInclusive - selection.start > limits.analysisMs) {
            selection = selection.start..(selection.start + limits.analysisMs)
            stopPlayback++; withheldAnalysis = null
            message = "현재 이용 범위에 맞춰 ${limits.analysisSeconds}초 구간으로 조정했어요. 원본은 그대로 있어요."
        }
    }
    BackHandler {
        if (!navigationLocked) {
            if (pass) pass = false
            else if (analyzing) { cancelAnalysis(); message = "분석을 취소했어요. 구간을 다시 고를 수 있어요." }
            else if (editingResult && result != null) returnToResult()
            else exit()
        }
    }
    if (discardConfirmation) AlertDialog(onDismissRequest = { discardConfirmation = false },
        title = { Text("이 장면을 닫을까요?") }, text = { Text("임시 녹화·녹음과 아직 저장하지 않은 문장은 지워져요.") },
        confirmButton = { TextButton(onClick = { if (!navigationLocked) { reset(); discardConfirmation = false; onBack() } }, enabled = !navigationLocked,
            modifier = Modifier.testTag("confirm-close-capture")) { Text("닫기") } },
        dismissButton = { TextButton(onClick = { discardConfirmation = false }) { Text("계속 보기") } })
    if (newCaptureConfirmation) AlertDialog(onDismissRequest = { if (!navigationLocked) newCaptureConfirmation = false },
        title = { Text("새 장면을 담을까요?") },
        text = { Text("현재 임시 장면과 아직 저장하지 않은 문장·메모는 지워져요. 간직하려면 먼저 저장해 주세요.") },
        confirmButton = { TextButton(onClick = { if (!navigationLocked) { reset(); newCaptureConfirmation = false } }, enabled = !navigationLocked,
            modifier = Modifier.testTag("confirm-new-capture")) { Text("새 촬영·녹음 시작") } },
        dismissButton = { TextButton(onClick = { newCaptureConfirmation = false }, enabled = !navigationLocked) { Text("계속 보기") } })
    if (pass) { passContent { pass = false }; return }

    val current = clip
    val lockedResult = result.takeUnless { editingResult }
    fun startRecording() {
        // Permissions can change after preview preparation; recheck at the actual action.
        prepare()
        if (hasPermission) {
            generation++; message = null
            recordingLimits = liveLimits()
            val ok = if (audioOnly) capture.startAudio(recordingLimits, ::acceptFile)
                else cameraRecorder?.let { capture.startVideo(it, recordingLimits, ::acceptFile) } ?: false
            if (ok) { elapsed = 0; startedAt = SystemClock.elapsedRealtime(); recording = true }
            else message = "시작하지 못했어요. 권한이나 다른 앱의 카메라·마이크 사용을 확인해 주세요."
        }
    }
    fun analyzeSelected() {
        if (!alive.get() || current == null || navigationLocked) return
        val useVisual = visual && current.hasVideo
        if (useVisual && !hasFullAccess()) { visual = false; pass = true; return }
        val range = ClipRange(selection.start.toLong(), selection.endInclusive.toLong())
        if (!liveLimits().allowsAnalysis(range)) { limits = liveLimits(); pass = true; return }
        // A new explicit analysis replaces the prior result and its caregiver draft.
        result = null; editingResult = false; clearDraft()
        stopPlayback++; cancelAnalysis(); analyzingVisual = useVisual
        analyzingExtended = range.durationMs > CaptureLimits.FREE.analysisMs
        analyzing = true; message = null
        val request = generation
        job = trackSourceJob(scope.launch {
            try {
                val analyzed = withTimeout(45_000) { analyzer.analyze(context, current.uri, range, useVisual) }
                ensureActive()
                if (request == generation && alive.get()) {
                    if ((useVisual && !hasFullAccess()) || !liveLimits().allowsAnalysis(range)) {
                        visual = false; limits = liveLimits()
                        message = "이용권 확인이 필요해요. 10초 이내 구간의 소리 분석은 무료로 계속할 수 있어요."
                    } else {
                        result = analyzed.interpretation; resultStart = range.startMs; resultEnd = range.endMs; message = analyzed.problem
                        withheldAnalysis = analyzed.takeIf { it.interpretation == null }
                    }
                }
            } catch (_: TimeoutCancellationException) { if (request == generation) message = "분석이 오래 걸려 멈췄어요. 구간을 줄여 다시 시도해 주세요." }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (request == generation) message = "이 구간을 분석하지 못했어요. 다시 재생해 보고 다른 구간이나 파일을 골라 주세요." }
            catch (_: LinkageError) { if (request == generation) message = "이 기기에서 분석 모델을 실행하지 못했어요." }
            finally { if (request == generation) analyzing = false }
        })
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val compactResultFooter = maxHeight < 480.dp
    Column(Modifier.fillMaxSize().testTag("capture-flow")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                if (editingResult && result != null) returnToResult()
                else exit()
            }, enabled = !navigationLocked, modifier = Modifier.testTag("capture-back")) {
                Text(if (editingResult && result != null) "결과로" else "닫기", color = Muted)
            }
            Text(when { lockedResult != null -> "아기의 한마디"; current != null -> "구간 고르기"; else -> "아기말 번역" },
                modifier = Modifier.weight(1f), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (result != null) TextButton(onClick = ::exit, enabled = !navigationLocked,
                modifier = Modifier.testTag("capture-close")) { Text("닫기", color = Muted) }
        }
        Column(Modifier.weight(1f).verticalScroll(contentScroll).testTag("capture-content").padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (current == null && lockedResult == null) {
                Text(if (recording) "편하게 담아 주세요" else "아기의 순간을 담아요", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("최대 ${limits.minutes}분 담고, ${limits.analysisSeconds}초까지 골라요.", color = Muted, lineHeight = 23.sp)
                if (!audioOnly && hasPermission) {
                    CameraRecordingPreview(facing, onReady = { recorder, switch -> cameraRecorder = recorder; canSwitch = switch },
                        onError = { cameraRecorder = null; message = it })
                } else Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(24.dp)).background(PaleSage), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Mic, null, Modifier.size(64.dp), tint = Ink)
                }
                if (recording || finishing) {
                    Text(if (audioOnly) "마이크로 녹음 중이에요" else "영상과 소리를 함께 녹화 중이에요", color = Muted)
                } else {
                    if (permissionDenied) TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        "package:${context.packageName}".toUri())) }) { Text("앱 설정에서 권한 확인") }
                    if (hasPermission && !audioOnly && canSwitch) TextButton(onClick = {
                        cameraRecorder = null; facing = if (facing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    }) { Text("카메라 전환") }
                    TextButton(onClick = { audioOnly = !audioOnly; hasPermission = permissionsGranted(); cameraRecorder = null; message = null; permissionDenied = false }, enabled = !navigationLocked) {
                        Text(if (audioOnly) "영상과 소리 함께 담기" else "카메라 없이 소리만 녹음", color = Ink)
                    }
                    HorizontalDivider(color = Line)
                    TextButton(onClick = ::chooseFile, enabled = !navigationLocked) { Text("저장된 영상 가져오기", color = Ink) }
                    Text("무료는 1분 촬영·10초 분석, 전체 이용권은 2분 촬영·20초 분석이에요. 원본은 기기 안에서만 임시로 보관해요.", color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
                }
            }
            if (current != null) {
                ClipPlayerPane(current, selection.start.toLong(), selection.endInclusive.toLong(), stopPlayback,
                    enabled = !analyzing && !navigationLocked, showPreview = lockedResult == null)
            }
            if (lockedResult == null && current != null) {
                Text("어느 순간을 들어볼까요?", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                if (editingResult) Text("새 구간을 번역하면 이전 문장·메모가 바뀌어요. 결과로 돌아가면 바꾸던 구간은 취소되고, 이전 선택 구간과 수정 내용이 그대로 남아요.",
                    color = Muted, fontSize = 13.sp, lineHeight = 21.sp, modifier = Modifier.testTag("range-reanalysis-notice"))
                ClipRangeEditor(current, ClipRange(selection.start.toLong(), selection.endInclusive.toLong()),
                    maxSelectionMs = limits.analysisMs, enabled = !analyzing && !navigationLocked, onRangeChange = { proposed ->
                        selection = proposed.startMs.toFloat()..proposed.endMs.toFloat()
                        message = null; withheldAnalysis = null; stopPlayback++
                    })
                if (current.hasVideo) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = visual, enabled = !analyzing && !navigationLocked,
                        role = Role.Switch, onValueChange = {
                            stopPlayback++
                            withheldAnalysis = null; message = null
                            if (!it) visual = false
                            else if (hasFullAccess()) visual = true
                            else pass = true
                        }).testTag("visual-analysis"), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("표정도 함께 보기", color = Ink, fontWeight = FontWeight.SemiBold)
                            Text(if (testDistribution) "테스트에서 바로 사용 · 같은 구간의 얼굴·입 관찰"
                                else "전체 이용권 · 같은 구간의 얼굴·입 관찰", color = Muted, fontSize = 12.sp)
                        }
                        Switch(checked = visual, enabled = !analyzing && !navigationLocked, onCheckedChange = null)
                    }
                }
                if (!analyzing) {
                    OutlinedButton(onClick = ::chooseFile, enabled = !navigationLocked,
                        modifier = Modifier.fillMaxWidth().testTag("editor-other-file")) { Text("다른 영상·녹음 고르기") }
                    TextButton(onClick = ::requestNewCapture, enabled = !navigationLocked) { Text("다시 촬영하기", color = Muted) }
                }
            }
            if (lockedResult != null) {
                val cardPhrase = if (draftEdited) phrase.trim() else lockedResult.phrase
                val cardBasis = ExpressionBasis.forPhrase(lockedResult.basis, lockedResult.phrase, cardPhrase)
                Text(ExpressionBasis.label(cardBasis), color = Muted, fontSize = 14.sp, modifier = Modifier.testTag("expression-basis"))
                val cardRecord = Observation(1, lockedResult.sound, lockedResult.range, "", cardPhrase.ifBlank { lockedResult.phrase }, basis = cardBasis)
                val cardRange = current?.let { ClipRange(selection.start.toLong(), selection.endInclusive.toLong()) }
                ResultMomentCard(cardRecord, current, cardRange)
                Text("${lockedResult.range} · ${if (lockedResult.visual) "소리 + 표정 관찰" else "소리 관찰"}", color = Muted, fontSize = 13.sp)
                Column(Modifier.fillMaxWidth().background(PaleSage, RoundedCornerShape(20.dp)).padding(16.dp)
                    .testTag("result-next-analysis"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("다음 장면도 살펴볼까요?", color = Ink, fontWeight = FontWeight.SemiBold)
                    if (current != null) OutlinedButton(onClick = ::editAnotherRange, enabled = !navigationLocked,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("result-other-range")) {
                        Text(if (current.hasVideo) "같은 영상에서 다른 구간" else "같은 녹음에서 다른 구간")
                    }
                    OutlinedButton(onClick = ::chooseFile, enabled = !navigationLocked,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("result-other-file")) { Text("다른 영상·녹음 고르기") }
                    OutlinedButton(onClick = ::requestNewCapture, enabled = !navigationLocked,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("result-new-capture")) { Text("새로 촬영·녹음하기") }
                    Text("다른 파일을 열거나 새로 촬영하면 저장하지 않은 문장·메모가 바뀌어요. 간직하려면 먼저 저장해 주세요.",
                        color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
                }
                lockedResult.visualNotice?.let {
                    Text(it, color = Ink, lineHeight = 23.sp, modifier = Modifier.fillMaxWidth()
                        .background(PaleApricot, RoundedCornerShape(16.dp)).padding(16.dp).testTag("visual-notice"))
                }
                Text("우리 아기 모습과 함께 재미있게 살펴보세요. 마음에 드는 순간은 영상과 함께 간직해요. 실제 마음·욕구를 알아낸 번역은 아니에요.", color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
                if (onGallery != null) {
                    GallerySaveButton("카드 이미지 앨범에 저장", !navigationLocked && cardPhrase.isNotBlank(), { message = it }) {
                        stopPlayback++; onGallery(lockedResult.copy(basis = cardBasis), cardPhrase, current, cardRange, false)
                    }
                    if (current != null) GallerySaveButton(if (current.hasVideo) "영상 카드 앨범에 저장" else "소리+아가말 카드 앨범에 저장", !navigationLocked && cardPhrase.isNotBlank(), { message = it }) {
                        stopPlayback++; onGallery(lockedResult.copy(basis = cardBasis), cardPhrase, current, cardRange, true)
                    }
                    if (current != null && !current.hasVideo) Text("녹음한 소리와 아가말 카드를 하나의 MP4로 저장해요. 사진 앱의 아가톡 앨범에서 보고 들을 수 있어요.",
                        color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
                    Text("사진·동영상 앱의 ‘아가톡’ 앨범에 저장해요. 앱 기록 보관은 아래 저장 버튼을 눌러 주세요.", color = Muted, fontSize = 13.sp)
                }
                if (onCommunity != null) {
                    OutlinedButton(onClick = {
                        stopPlayback++; onCommunity(lockedResult.copy(basis = cardBasis), cardPhrase, current, cardRange)
                    }, enabled = !navigationLocked && cardPhrase.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("result-community")) {
                        Text("우리 아이 자랑에 올리기")
                    }
                    Text("공유할 카드를 기기에서 준비해요. 게시판에서 미리보고 공개 버튼을 눌러야 올라가요.",
                        color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
                }
                var expanded by remember { mutableStateOf(false) }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "관찰 근거 접기" else "어떤 소리를 들었나요?", color = Ink) }
                if (expanded) Text(lockedResult.evidence, color = Ink, lineHeight = 23.sp, modifier = Modifier.testTag("analysis-evidence"))
                Text("이렇게 응답해 주세요", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(lockedResult.guide, color = Muted, lineHeight = 23.sp)
                TextButton(onClick = { edit = !edit; if (edit && !draftEdited) phrase = lockedResult.phrase }, enabled = !navigationLocked) { Text(if (edit) "기록 수정 접기" else "기록에 메모·수정 남기기", color = Muted) }
                if (edit) {
                    OutlinedTextField(value = phrase, onValueChange = { if (it.length <= 160) { phrase = it; draftEdited = true } }, label = { Text("보호자가 남길 문장") },
                        modifier = Modifier.fillMaxWidth().testTag("draft-phrase-editor"), minLines = 2, enabled = !navigationLocked)
                    OutlinedTextField(value = note, onValueChange = { if (it.length <= 120) note = it }, label = { Text("보호자 메모 · 분석에 사용하지 않아요") },
                        modifier = Modifier.fillMaxWidth().testTag("draft-note-editor"), minLines = 2, enabled = !navigationLocked)
                }
                if (current != null && onSaveWithMedia != null) {
                    Row(Modifier.fillMaxWidth().toggleable(keepScene, enabled = !navigationLocked, role = Role.Checkbox,
                        onValueChange = { keepScene = it }).testTag("keep-scene"), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = keepScene, onCheckedChange = null, enabled = !navigationLocked)
                        Column(Modifier.weight(1f)) {
                            Text(if (current.hasVideo) "영상·소리 함께 보관" else "소리 함께 보관", color = Ink)
                            Text("선택 구간과 번역 자막을 이 기기에 저장해요. 기록에서 재생·내려받을 수 있어요.", color = Muted, fontSize = 13.sp)
                        }
                    }
                }
            }
            if (selecting) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("파일을 준비하고 있어요", color = Muted) }
            if (current?.hasVideo == true && onSaveVideo != null) {
                GallerySaveButton(if (saving) "영상 저장 중…" else "선택 구간 영상만 저장",
                    !navigationLocked && !analyzing, { message = it }) {
                    stopPlayback++
                    onSaveVideo(current, ClipRange(selection.start.toLong(), selection.endInclusive.toLong()))
                }
                Text("${ClipRangeEdits.time(selection.start.toLong())}–${ClipRangeEdits.time(selection.endInclusive.toLong())} 구간을 원래 소리와 함께, 아기말 자막 없이 앨범에 저장해요. 분석 결과가 없어도 가능해요.",
                    color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
            }
            message?.let { text ->
                val messageView = remember { BringIntoViewRequester() }
                LaunchedEffect(text) { messageView.bringIntoView() }
                Text(text, color = Ink, lineHeight = 23.sp, modifier = Modifier.fillMaxWidth()
                    .bringIntoViewRequester(messageView).background(PaleApricot, RoundedCornerShape(16.dp))
                    .padding(16.dp).testTag("capture-message"))
            }
            if (lockedResult == null && current != null && !analyzing) {
                withheldAnalysis?.visualEvidence?.let { observation ->
                    var showObservation by remember(withheldAnalysis) { mutableStateOf(false) }
                    TextButton(onClick = { showObservation = !showObservation },
                        modifier = Modifier.testTag("toggle-withheld-evidence")) {
                        Text(if (showObservation) "살펴본 장면 접기" else "살펴본 장면 보기")
                    }
                    if (showObservation) Text(observation.evidence, color = Muted, fontSize = 13.sp, lineHeight = 21.sp,
                        modifier = Modifier.testTag("withheld-visual-evidence"))
                }
                withheldAnalysis?.suggestedRange?.let { suggested ->
                    OutlinedButton(onClick = {
                        // A suggestion only moves the editor; analysis remains an explicit next tap.
                        val fitted = ClipRangeEdits.fit(suggested, current.durationMs, liveLimits().analysisMs)
                        selection = fitted.startMs.toFloat()..fitted.endMs.toFloat()
                        stopPlayback++; withheldAnalysis = null; message = "소리가 있는 구간으로 옮겼어요. 다시 듣고 번역해 주세요."
                    }, enabled = !navigationLocked, modifier = Modifier.fillMaxWidth().testTag("apply-sound-range")) {
                        Text("소리가 있는 구간 보기 · ${ClipRangeEdits.time(suggested.startMs)}–${ClipRangeEdits.time(suggested.endMs)}")
                    }
                }
            }
        }
        if (current == null && lockedResult == null) Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).testTag("capture-action")) {
            if (recording || finishing) {
                Text("${ClipRange.clock(elapsed * 1000L)} / ${ClipRange.clock(recordingLimits.captureMs)}", color = Ink,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
                PrimaryButton(if (finishing) "마무리 중…" else "촬영 마치기", {
                    finishing = true; recording = false; capture.stop()
                }, enabled = !finishing)
            } else if (!hasPermission) PrimaryButton(if (audioOnly) "마이크 허용하기" else "카메라·마이크 허용하기", { prepare() }, enabled = !navigationLocked)
            else PrimaryButton(if (audioOnly) "녹음 시작" else if (cameraRecorder == null) "카메라 연결 중…" else "녹화 시작",
                ::startRecording, enabled = !navigationLocked && (audioOnly || cameraRecorder != null))
            Text(if (testDistribution) "전체 기능 테스트 · 최대 2분 촬영 · 20초 분석" else "현재 이용 범위 · 최대 ${limits.minutes}분 촬영 · ${limits.analysisSeconds}초 분석",
                color = Muted, fontSize = 12.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp))
        }
        if (current != null && lockedResult == null) Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).testTag("analysis-action")) {
            if (analyzing) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("선택한 구간의 ${if (visual) "소리와 표정을" else "소리를"} 살펴보고 있어요…", color = Ink, modifier = Modifier.padding(top = 8.dp))
                TextButton(onClick = { cancelAnalysis(); message = "분석을 취소했어요. 구간을 다시 고를 수 있어요." }) { Text("분석 취소") }
            } else {
                Text("${ClipRangeEdits.time(selection.start.toLong())} – ${ClipRangeEdits.time(selection.endInclusive.toLong())} · ${ClipRangeEdits.seconds((selection.endInclusive - selection.start).toLong())}초 선택",
                    color = Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                PrimaryButton(if (visual && current.hasVideo) "영상 + 소리로 번역하기" else "이 구간 소리로 번역하기", ::analyzeSelected,
                    enabled = !navigationLocked)
            }
        }
        if (lockedResult != null) Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)
            .testTag("result-action"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val finalPhrase = if (draftEdited) phrase.trim() else lockedResult.phrase
            val savingScene = keepScene && current != null && onSaveWithMedia != null
            val saved = savedForResult?.takeIf { it.phrase == finalPhrase && it.note == note.trim() && it.withMedia == savingScene }
            if (saving) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp))
            if (saved != null) {
                Text(if (saved.withMedia && current?.hasVideo == false) "소리와 아기말을 앱 기록에 저장했어요."
                    else if (saved.withMedia) "장면과 아기말을 앱 기록에 저장했어요." else "아기말을 앱 기록에 저장했어요.",
                    color = Ink, fontSize = 13.sp, modifier = Modifier.testTag("capture-save-status"))
                if (saved.withMedia && onViewSaved != null) OutlinedButton(onClick = { stopPlayback++; onViewSaved(saved.recordId) },
                    enabled = !navigationLocked, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (current?.hasVideo == false) "저장한 소리와 카드 보기" else "저장한 장면 보기") }
            }
            if (saved == null && current != null && !compactResultFooter) OutlinedButton(onClick = ::editAnotherRange, enabled = !navigationLocked,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("result-other-range-fixed")) { Text("다른 구간 분석하기") }
            PrimaryButton(if (saving) "저장하고 있어요" else if (saved != null) "기록 저장 완료" else if (savingScene && current?.hasVideo == false) "소리와 함께 기록하기" else if (savingScene) "장면과 함께 저장하기" else "이 문장 저장하기", {
                stopPlayback++
                val savedResult = lockedResult.copy(basis = ExpressionBasis.forPhrase(lockedResult.basis, lockedResult.phrase, finalPhrase))
                if (keepScene && current != null && onSaveWithMedia != null)
                    onSaveWithMedia(savedResult, finalPhrase, note.trim(), current, ClipRange(selection.start.toLong(), selection.endInclusive.toLong()))
                else onSave(savedResult, finalPhrase, note.trim())
            }, enabled = saved == null && !navigationLocked && (!draftEdited || phrase.isNotBlank()))
            if (saving) Text("앱을 열어 둔 채 잠시 기다려 주세요.", color = Muted, fontSize = 12.sp)
        }
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
                val selector = if ((facing == CameraSelector.LENS_FACING_BACK && back) || !front) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }
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
internal fun ClipPlayerPane(clip: LocalClip, startMs: Long, endMs: Long, stopSignal: Int = 0, enabled: Boolean = true,
                            showPreview: Boolean = true) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(clip.uri) { MediaPlayer() }
    var prepared by remember(clip.uri) { mutableStateOf(false) }
    var playing by remember(clip.uri) { mutableStateOf(false) }
    var pendingPlay by remember(clip.uri) { mutableStateOf(false) }
    var seekReady by remember(clip.uri) { mutableStateOf(false) }
    var surfaceReady by remember(clip.uri) { mutableStateOf(false) }
    val playerAlive = remember(player) { AtomicBoolean(true) }
    var position by remember(clip.uri) { mutableLongStateOf(startMs) }
    var error by remember(clip.uri) { mutableStateOf<String?>(null) }
    val bounds by rememberUpdatedState(startMs..endMs)
    val canPlay by rememberUpdatedState(enabled)
    fun startWhenReady() {
        if (playerAlive.get() && pendingPlay && canPlay && seekReady && (!clip.hasVideo || surfaceReady)) {
            pendingPlay = false
            runCatching { player.start(); playing = true }.onFailure { error = "재생을 시작하지 못했어요." }
        }
    }
    DisposableEffect(player) {
        var disposed = false
        player.setOnPreparedListener { if (!disposed) { prepared = true; it.seekTo(bounds.first, MediaPlayer.SEEK_CLOSEST) } }
        player.setOnErrorListener { _, _, _ -> if (!disposed) { error = "이 파일은 재생하지 못했어요. 다른 파일을 골라 주세요."; pendingPlay = false; playing = false }; true }
        player.setOnCompletionListener { if (!disposed) { pendingPlay = false; playing = false } }
        player.setOnSeekCompleteListener {
            if (!disposed) { seekReady = true; startWhenReady() }
        }
        runCatching { player.setDataSource(context, clip.uri); player.prepareAsync() }.onFailure { error = "파일을 재생하지 못했어요." }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) {
            pendingPlay = false
            // pause() is invalid in Prepared state and sends MediaPlayer into Error.
            if (prepared) runCatching { if (player.isPlaying) player.pause() }
            playing = false
        } }
        lifecycle.addObserver(observer)
        onDispose { disposed = true; playerAlive.set(false); lifecycle.removeObserver(observer); player.release() }
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
    Column(Modifier.testTag("clip-player-pane"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (clip.hasVideo && (showPreview || playing || pendingPlay)) Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(20.dp)).background(Ink)
            .testTag("clip-video-preview"), contentAlignment = Alignment.Center) {
        AndroidView(factory = {
            SurfaceView(it).apply { holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    if (playerAlive.get()) runCatching {
                        player.setDisplay(holder); surfaceReady = true
                        // A reattached surface can reset the device decoder to zero. Seek only
                        // after attaching it, then let onSeekComplete start the selected range.
                        if (prepared) {
                            seekReady = false
                            player.seekTo(bounds.first, MediaPlayer.SEEK_CLOSEST)
                        } else startWhenReady()
                    }.onFailure { pendingPlay = false; playing = false; error = "영상을 재생하지 못했어요." }
                }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    surfaceReady = false
                    if (playerAlive.get()) runCatching { player.setDisplay(null) }
                }
            }) }
        }, modifier = Modifier.aspectRatio(clip.aspectRatio, matchHeightConstraintsFirst = clip.aspectRatio < 1f).fillMaxSize())
        }
        else if (showPreview) Row(Modifier.fillMaxWidth().background(PaleSage, RoundedCornerShape(20.dp)).padding(20.dp)
            .testTag("clip-audio-preview"), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Mic, null, tint = Ink); Spacer(Modifier.width(12.dp)); Text("녹음한 소리", color = Ink)
        }
        OutlinedButton(onClick = {
            if (prepared) runCatching {
                if (playing || pendingPlay) { pendingPlay = false; if (player.isPlaying) player.pause(); playing = false }
                else { seekReady = false; pendingPlay = true; player.seekTo(startMs, MediaPlayer.SEEK_CLOSEST) }
            }.onFailure { error = "재생을 시작하지 못했어요." }
        }, enabled = prepared && error == null && enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("clip-play")) {
            Text(if (playing) "재생 멈추기 · ${ClipRange.clock(position)}"
                else if (!showPreview && clip.hasVideo) "선택 구간 다시 보기·듣기"
                else if (!showPreview) "선택 구간 원음 다시 듣기"
                else "선택 구간 다시 듣기${if (clip.hasVideo) " · 영상 함께 보기" else ""}")
        }
        error?.let { Text(it, color = Ink, fontSize = 13.sp) }
    }
}
