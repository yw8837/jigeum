package com.moa.babylanguage

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.UUID

/** Publishes only complete, user-requested copies. Baby originals are never uploaded. */
internal object GalleryMedia {
    private val lock = Mutex()
    private fun journal(context: Context) = context.getSharedPreferences("gallery-pending", Context.MODE_PRIVATE)
    // Synchronous commit on IO is required before the next MediaStore mutation or crash boundary.
    // Both journal operations need commit's Boolean; KTX edit returns Unit.
    @SuppressLint("ApplySharedPref")
    private fun remember(context: Context, token: String, uri: Uri?, legacy: File?, written: Boolean) {
        check(journal(context).edit().putString(token, JSONObject().put("uri",uri?.toString() ?: "")
            .put("legacy",legacy?.absolutePath ?: "").put("written",written).toString()).commit()) {
            "앨범 저장 상태를 남기지 못했어요. 저장 공간을 확인해 주세요."
        }
    }
    @SuppressLint("ApplySharedPref")
    private fun forget(context: Context, token: String) {
        check(journal(context).edit().remove(token).commit()) { "앨범 저장 상태를 정리하지 못했어요." }
    }

    @Suppress("DEPRECATION")
    private fun includingPending(uri: Uri): Uri =
        if (Build.VERSION.SDK_INT >= 29) MediaStore.setIncludePending(uri) else uri

    private fun validName(name: String, token: String) = name.matches(Regex("아가톡-[0-9]+-$token\\.(mp4|png)"))

    @Suppress("DEPRECATION")
    private fun legacyFile(path: String, token: String): File? {
        if (path.isEmpty()) return null
        val file = File(path)
        check(validName(file.name, token) && listOf(Environment.DIRECTORY_MOVIES, Environment.DIRECTORY_PICTURES).any {
            file.parentFile?.canonicalFile == File(Environment.getExternalStoragePublicDirectory(it), "아가톡").canonicalFile
        }) { "Invalid gallery recovery path" }
        return file
    }

    /** An absent row is already cleaned. A failed or foreign-row query never authorizes deletion. */
    @Suppress("DEPRECATION")
    private fun requireOwnedRow(context: Context, uri: Uri, token: String, legacy: File?): Boolean {
        check(uri.scheme == "content" && uri.authority == "media" &&
            uri.pathSegments.let { it.size == 4 && it[0] in (setOf("external") +
                if (Build.VERSION.SDK_INT >= 29) MediaStore.getExternalVolumeNames(context) else emptySet()) &&
                it[1] in listOf("images", "video") && it[2] == "media" && it[3].toLongOrNull() != null })
        val columns = mutableListOf(MediaStore.MediaColumns.DISPLAY_NAME)
        if (Build.VERSION.SDK_INT >= 29) columns += MediaStore.MediaColumns.OWNER_PACKAGE_NAME
        if (legacy != null) columns += MediaStore.MediaColumns.DATA
        val cursor = context.contentResolver.query(includingPending(uri), columns.toTypedArray(), null, null, null)
            ?: error("Gallery provider unavailable")
        cursor.use {
            if (!it.moveToFirst()) return false
            check(validName(it.getString(0), token)) { "Foreign gallery row" }
            if (Build.VERSION.SDK_INT >= 29) check(it.getString(it.getColumnIndexOrThrow(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)) == context.packageName)
            if (legacy != null) check(File(it.getString(it.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))).canonicalFile == legacy.canonicalFile)
            return true
        }
    }

    /** The DATA path and UUID also recover the legacy insert-to-journal crash window. */
    @Suppress("DEPRECATION")
    private fun removeInterrupted(context: Context, token: String, uri: Uri?, legacy: File?): Boolean {
        if (uri != null) {
            if (requireOwnedRow(context, uri, token, legacy)) check(context.contentResolver.delete(uri, null, null) == 1)
        } else if (legacy != null) {
            val collection = if (legacy.extension == "mp4") MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val cursor = context.contentResolver.query(includingPending(collection), arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DATA}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?", arrayOf(legacy.absolutePath, legacy.name), null)
                ?: error("Gallery provider unavailable")
            val rows = cursor.use { buildList { while (it.moveToNext()) add(android.content.ContentUris.withAppendedId(collection, it.getLong(0))) } }
            for (row in rows) {
                requireOwnedRow(context, row, token, legacy)
                check(context.contentResolver.delete(row, null, null) == 1)
            }
        }
        return legacy == null || !legacy.exists() || legacy.delete()
    }

    /** Serialized with saves; only this app's unfinished gallery items are considered. */
    suspend fun cleanupInterrupted(context: Context): Boolean = lock.withLock { withContext(Dispatchers.IO) {
        var complete = true
        val protectedRows = mutableSetOf<Uri>()
        for ((token,value) in journal(context).all) {
            currentCoroutineContext().ensureActive()
            runCatching {
                check(runCatching { UUID.fromString(token).toString() == token }.getOrDefault(false))
                val state = JSONObject(value as String)
                val uri = state.optString("uri").takeIf { it.isNotEmpty() }?.let(Uri::parse)
                val legacy = legacyFile(state.optString("legacy"), token)
                if (state.optBoolean("written") && uri != null) {
                    protectedRows += uri
                    val exists = requireOwnedRow(context, uri, token, legacy)
                    if (exists && Build.VERSION.SDK_INT >= 29) check(context.contentResolver.update(uri,
                        ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) == 1)
                    forget(context, token)
                } else if (removeInterrupted(context, token, uri, legacy)) forget(context, token)
                else complete = false
            }.onFailure { complete = false }
        }
        if (Build.VERSION.SDK_INT >= 29) for (collection in listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,MediaStore.Video.Media.EXTERNAL_CONTENT_URI)) runCatching {
            val cursor = context.contentResolver.query(includingPending(collection),arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME}=? AND ${MediaStore.MediaColumns.IS_PENDING}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                arrayOf(context.packageName,"1","아가톡-%"),null) ?: error("Gallery provider unavailable")
            val orphanRows = cursor.use { buildList {
                while (it.moveToNext()) {
                    val row = android.content.ContentUris.withAppendedId(collection, it.getLong(0))
                    if (row !in protectedRows && it.getString(1).matches(Regex("아가톡-[0-9]+-[0-9a-f-]{36}\\.(mp4|png)"))) add(row)
                }
            } }
            for (row in orphanRows) {
                currentCoroutineContext().ensureActive()
                check(context.contentResolver.delete(row, null, null) == 1)
            }
        }.onFailure { if (it is CancellationException) throw it else complete = false }
        complete
    } }
    suspend fun image(context: Context, bitmap: Bitmap, generation: Long): Uri = publish(context, false, generation) { out ->
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "카드 이미지를 만들지 못했어요." }
    }

    suspend fun video(context: Context, file: File, generation: Long): Uri {
        check(file.isFile && file.length() in 1..TranslatedMedia.MAX_BYTES) { "보관한 영상을 열지 못했어요." }
        return publish(context, true, generation) { out ->
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive(); requireGeneration(generation)
                    val size = input.read(buffer); if (size < 0) break
                    out.write(buffer, 0, size)
                }
            }
        }
    }

    /** Opens the published copy only after the user's tap; never shares the temporary source. */
    suspend fun open(context: Context, uri: Uri, video: Boolean) {
        withContext(Dispatchers.IO) {
            checkNotNull(context.contentResolver.openFileDescriptor(uri, "r")).use {
                check(it.statSize != 0L)
            }
        }
        withContext(Dispatchers.Main.immediate) {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, if (video) "video/mp4" else "image/png")
                clipData = android.content.ClipData.newRawUri("아가톡 저장 파일", uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }
    }

    private fun requireGeneration(generation: Long) {
        check(generation == MemoryArtifacts.currentGeneration()) { "기록이 변경되어 앨범 저장을 중단했어요." }
    }

    internal suspend fun publish(context: Context, video: Boolean, generation: Long,
                                 write: suspend (OutputStream) -> Unit): Uri = lock.withLock {
        var created: Uri? = null
        var legacyFile: File? = null
        val token = UUID.randomUUID().toString()
        try {
            withContext(Dispatchers.IO) {
                requireGeneration(generation); ensureActive()
                val extension = if (video) "mp4" else "png"
                val name = "아가톡-${System.currentTimeMillis()}-$token.$extension"
                val directory = if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, if (video) "video/mp4" else "image/png")
                    put(MediaStore.Images.ImageColumns.DATE_TAKEN, System.currentTimeMillis())
                    if (Build.VERSION.SDK_INT >= 29) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/아가톡/")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    } else {
                        check(context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                            "앨범에 저장하려면 저장 권한을 허용해 주세요. 파일 위치 선택으로도 보관할 수 있어요."
                        }
                        @Suppress("DEPRECATION")
                        val folder = File(Environment.getExternalStoragePublicDirectory(directory), "아가톡")
                        check(folder.isDirectory || folder.mkdirs()) { "앨범을 만들지 못했어요. 저장 공간을 확인해 주세요." }
                        legacyFile = File(folder, name)
                        @Suppress("DEPRECATION")
                        put(MediaStore.MediaColumns.DATA, legacyFile!!.absolutePath)
                    }
                }
                remember(context,token,null,legacyFile,false)
                val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val uri = context.contentResolver.insert(collection, values) ?: error("앨범에 저장할 위치를 만들지 못했어요.")
                created = uri
                remember(context,token,uri,legacyFile,false)
                (context.contentResolver.openOutputStream(uri, "w") ?: error("앨범에 파일을 쓸 수 없어요.")).use {
                    write(it); it.flush()
                }
                ensureActive(); requireGeneration(generation)
                remember(context,token,uri,legacyFile,true)
                if (Build.VERSION.SDK_INT >= 29) check(context.contentResolver.update(uri,
                    ContentValues().apply {
                        put(MediaStore.Images.ImageColumns.DATE_TAKEN, System.currentTimeMillis())
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }, null, null) == 1) {
                    "앨범에 파일을 표시하지 못했어요. 다시 시도해 주세요."
                }
                ensureActive(); requireGeneration(generation)
                forget(context,token)
                uri
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) {
                val uri = created
                // Restore abort intent even if cancellation happened after forgetting a successful write.
                // If MediaProvider is unavailable, startup must retry deletion, never publication.
                runCatching { remember(context, token, uri, legacyFile, false) }
                val deleted = runCatching { removeInterrupted(context, token, uri, legacyFile) }.getOrDefault(false)
                if (!deleted) withContext(Dispatchers.Main.immediate) {
                    android.widget.Toast.makeText(context.applicationContext,
                        "앨범에 미완성 파일이 남았을 수 있어요. 아가톡 앨범에서 확인해 주세요.", android.widget.Toast.LENGTH_LONG).show()
                }
                if (deleted) runCatching { forget(context,token) }
            }
            throw failure
        }
    }
}

@Composable
internal fun GallerySaveButton(label: String, enabled: Boolean, onMessage: (String) -> Unit, onSave: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val save by rememberUpdatedState(onSave)
    val allowed by rememberUpdatedState(enabled)
    val message by rememberUpdatedState(onMessage)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && allowed) save()
        else if (!granted) message("앨범 저장 권한을 허용하지 않았어요. 파일 위치를 선택하거나 공유로 보관할 수 있어요.")
    }
    OutlinedButton(onClick = {
        if (Build.VERSION.SDK_INT <= 28 && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else save()
    }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(label) }
}
