package com.moa.babylanguage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.*
import java.io.File

/** Write only a document URI just returned by the user's save picker; clean failed documents. */
internal suspend fun downloadRecordMedia(context: Context, id: Long, destination: Uri, generation: Long) {
    var complete = false
    try {
        withContext(Dispatchers.IO) {
            check(MemoryArtifacts.currentGeneration() == generation) { "기록이 변경되어 내려받기를 중단했어요." }
            RecordMedia.file(context, id).inputStream().use { input ->
                (context.contentResolver.openOutputStream(destination, "wt")
                    ?: error("선택한 위치에 저장할 수 없어요.")).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        check(MemoryArtifacts.currentGeneration() == generation) { "기록이 변경되어 내려받기를 중단했어요." }
                        val count = input.read(buffer); if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            check(MemoryArtifacts.currentGeneration() == generation) { "기록이 변경되어 내려받기를 중단했어요." }
        }
        complete = true
    } finally {
        if (!complete) withContext(NonCancellable + Dispatchers.IO) {
            // This URI was created by this operation, never an imported original.
            val deleted = runCatching { DocumentsContract.deleteDocument(context.contentResolver, destination) }.getOrDefault(false)
            if (!deleted) withContext(NonCancellable + Dispatchers.Main.immediate) {
                android.widget.Toast.makeText(context.applicationContext,
                    "저장 위치에 미완성 파일이 남았을 수 있어요. 해당 위치에서 확인하고 삭제해 주세요.",
                    android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
internal fun RecordMediaViewer(record: Observation, onClose: () -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var media by remember(record.id) { mutableStateOf<LocalClip?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloaded by rememberSaveable(record.id) { mutableStateOf(false) }
    var galleryMessage by rememberSaveable(record.id) { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var stop by remember { mutableIntStateOf(0) }
    var pendingGeneration by rememberSaveable { mutableLongStateOf(-1) }
    var pickerPending by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { destination ->
        pickerPending = false
        if (destination != null) {
            working = true
            scope.launch {
                try {
                    downloadRecordMedia(context, record.id, destination, pendingGeneration)
                    downloaded = true
                    onMessage("번역 영상을 선택한 위치에 내려받았어요.")
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message ?: "내려받지 못했어요. 저장 공간과 위치를 확인해 주세요." }
                finally { working = false }
            }
        }
    }
    LaunchedEffect(record.id) {
        try { media = withContext(Dispatchers.IO) {
            CaptureFiles.inspect(context, Uri.fromFile(RecordMedia.file(context, record.id)), limits = CaptureLimits.FULL)
        } } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "보관한 장면을 열지 못했어요. 문장 기록은 그대로 남아 있어요." }
    }
    Dialog(onDismissRequest = { if (!working && !pickerPending) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Cream, modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp).fillMaxHeight(.94f)) {
            Column(Modifier.padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TextButton(onClick = onClose, enabled = !working && !pickerPending) { Text("기록으로 돌아가기") }
                Text("그때의 아기말", color = Ink, fontSize = 25.sp)
                Text("${MemoryArtifacts.date(record.id)} · ${record.context}", color = Muted)
                media?.let { clip -> ClipPlayerPane(clip, 0, clip.durationMs, stop, !working && !pickerPending) }
                Text(record.phrase, color = Ink, fontSize = 19.sp)
                Text(ExpressionBasis.label(record.basis), color = Muted, fontSize = 13.sp)
                Text("선택 구간 · 원래 소리 · 번역 자막이 함께 담긴 MP4예요.", color = Muted, fontSize = 13.sp)
                if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
                GallerySaveButton("앨범에 영상 저장", media != null && !working && !pickerPending, { error = it }) {
                    stop++; working = true; error = null; galleryMessage = null
                    val generation = MemoryArtifacts.currentGeneration()
                    scope.launch {
                        try {
                            GalleryMedia.video(context, RecordMedia.file(context, record.id), generation)
                            galleryMessage = "아가톡 앨범에 영상 카드를 저장했어요."
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { error = failure.message ?: "앨범에 영상을 저장하지 못했어요." }
                        finally { working = false }
                    }
                }
                GallerySaveButton("카드 이미지 앨범에 저장", media != null && !working && !pickerPending, { error = it }) {
                    stop++; working = true; error = null; galleryMessage = null
                    val generation = MemoryArtifacts.currentGeneration()
                    scope.launch {
                        var bitmap: android.graphics.Bitmap? = null
                        try {
                            withContext(Dispatchers.IO) { bitmap = MomentCardRenderer.savedFrame(context, record) }
                            GalleryMedia.image(context, checkNotNull(bitmap), generation)
                            galleryMessage = "아가톡 앨범에 카드 이미지를 저장했어요."
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { error = failure.message ?: "앨범에 이미지를 저장하지 못했어요." }
                        finally { bitmap?.recycle(); working = false }
                    }
                }
                PrimaryButton("번역 영상 내려받기", {
                    stop++; error = null; downloaded = false; pendingGeneration = MemoryArtifacts.currentGeneration(); pickerPending = true
                    try { picker.launch("아가톡-${record.id}.mp4") }
                    catch (_: Exception) { pickerPending = false; error = "파일 저장기를 열지 못했어요. 공유로 저장할 수 있어요." }
                }, enabled = media != null && !working && !pickerPending)
                OutlinedButton(onClick = {
                    stop++; working = true; error = null
                    val generation = MemoryArtifacts.currentGeneration()
                    scope.launch {
                        var file: File? = null
                        try {
                            file = MemoryArtifacts.media(context, record.id, generation)
                            MemoryArtifacts.share(context, file, "video/mp4", generation)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { error = failure.message ?: "영상을 공유하지 못했어요." }
                        finally { file?.let { MemoryArtifacts.discardUnshared(context, it) }; working = false }
                    }
                }, enabled = media != null && !working && !pickerPending, modifier = Modifier.fillMaxWidth()) { Text("번역 영상 공유") }
                if (downloaded) Text("번역 영상을 선택한 위치에 내려받았어요.", color = Ink)
                galleryMessage?.let {
                    val bring = remember { BringIntoViewRequester() }
                    LaunchedEffect(it) { bring.bringIntoView() }
                    Text(it, color = Ink, modifier = Modifier.bringIntoViewRequester(bring))
                }
                error?.let { Text(it, color = Ink) }
                Text("JSON·CSV·PDF 백업에는 영상과 소리가 포함되지 않아요. 보관하려면 이 영상도 따로 내려받아 주세요.", color = Muted, fontSize = 13.sp)
            }
        }
    }
}
