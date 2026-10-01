package com.moa.babylanguage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID

internal data class LocalClip(val uri: Uri, val durationMs: Long, val hasVideo: Boolean, val ownedFile: File? = null,
                              val aspectRatio: Float = 1f)

/** Private temporary files are never included in the FileProvider's exports path. */
internal object CaptureFiles {
    fun create(context: Context, video: Boolean): File {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        check(dir.isDirectory && dir.usableSpace > 40L * 1024 * 1024) { "저장 공간이 부족해요. 공간을 확보한 뒤 다시 촬영해 주세요." }
        return File(dir, UUID.randomUUID().toString() + if (video) ".mp4" else ".m4a")
    }
    fun cleanup(context: Context) {
        File(context.cacheDir, "captures").listFiles()?.filter { it.isFile }?.forEach { it.delete() }
    }
    fun inspect(context: Context, uri: Uri, ownedFile: File? = null): LocalClip {
        val size = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1
        require(size in 1..100L * 1024 * 1024) { "100MB 이하의 기기에 저장된 파일을 골라 주세요." }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            require(duration in 1_000..60_500) { "1초 이상, 1분 이내의 영상이나 녹음이 필요해요." }
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 1f
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 1f
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val ratio = if (rotation % 180 != 0) height / width else width / height
            return LocalClip(uri, duration, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes", ownedFile,
                ratio.takeIf { it.isFinite() && it > 0 } ?: 1f)
        } finally { retriever.release() }
    }
}

/** Owns one capture. A late finalize can never resurrect a discarded screen/session. Main thread only. */
internal class LocalCapture(private val context: Context) {
    private var audio: MediaRecorder? = null
    private var video: Recording? = null
    private var file: File? = null
    private var generation = 0L
    private var audioCallback: ((File?, String?) -> Unit)? = null

    @Suppress("MissingPermission", "DEPRECATION")
    fun startAudio(onFinished: (File?, String?) -> Unit): Boolean {
        discard()
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        return try {
            val output = CaptureFiles.create(context, false).also { file = it }
            val recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
            audio = recorder; audioCallback = onFinished
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioSamplingRate(44_100)
            recorder.setAudioEncodingBitRate(96_000)
            recorder.setMaxDuration(60_000)
            recorder.setOutputFile(output.absolutePath)
            recorder.setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) stop() }
            recorder.setOnErrorListener { _, _, _ ->
                val callback = audioCallback
                discard()
                callback?.invoke(null, "녹음을 마치지 못했어요. 마이크와 저장 공간을 확인해 주세요.")
            }
            recorder.prepare(); recorder.start()
            true
        } catch (_: Exception) { discard(); false }
    }

    @Suppress("MissingPermission")
    fun startVideo(recorder: Recorder, onFinished: (File?, String?) -> Unit): Boolean {
        discard()
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return false
        val current = generation
        return try {
            val output = CaptureFiles.create(context, true).also { file = it }
            video = recorder.prepareRecording(context, FileOutputOptions.Builder(output)
                .setDurationLimitMillis(60_000).setFileSizeLimit(90L * 1024 * 1024).build())
                .withAudioEnabled().start(ContextCompat.getMainExecutor(context)) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        if (current != generation) { output.delete(); return@start }
                        video?.close(); video = null; file = null
                        val acceptable = !event.hasError() || event.error in listOf(VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED,
                            VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED)
                        if (acceptable && output.length() > 0) onFinished(output, null)
                        else { output.delete(); onFinished(null, "촬영을 마치지 못했어요. 카메라와 마이크를 확인하고 다시 시도해 주세요.") }
                    }
                }
            true
        } catch (_: Exception) { discard(); false }
    }

    fun stop() {
        video?.let { it.stop(); return }
        val recorder = audio ?: return
        val output = file; val callback = audioCallback
        audio = null; file = null; audioCallback = null
        val success = runCatching { recorder.stop() }.isSuccess
        runCatching { recorder.release() }
        if (success && output != null && output.length() > 0) callback?.invoke(output, null)
        else { output?.delete(); callback?.invoke(null, "녹음이 너무 짧거나 중단됐어요. 다시 녹음해 주세요.") }
    }

    fun discard() {
        generation++
        val staleFile = file
        video?.close(); video = null
        audio?.let { runCatching { it.stop() }; runCatching { it.release() } }
        audio = null; audioCallback = null; file = null
        staleFile?.delete()
    }
}
