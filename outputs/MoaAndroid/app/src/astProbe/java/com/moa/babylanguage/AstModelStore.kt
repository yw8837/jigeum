package com.moa.babylanguage

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Only immutable bundled model bytes are copied; no audio is written or uploaded. */
internal object AstModelStore {
    const val BASE_SHA256 = "3dff0cc2b374977ae1460258261608e33428f4767a6959cb8bc910b09433f71e"
    val ASSET = if (BuildConfig.AST_INFANT_PRESENCE) "ast-infant-presence-candidate.onnx" else "ast-dynamic-int8.onnx"
    val SIZE = if (BuildConfig.AST_INFANT_PRESENCE) 91_138_597L else 91_134_123L
    val SHA256 = if (BuildConfig.AST_INFANT_PRESENCE)
        "4a378476dcbe5f6b13c5f67295797d59376dab3627fe3dbd56bfeeadbd7f802a"
        else BASE_SHA256

    // Called only while AstAudioAnalyzer's process-wide mutex is held.
    fun resolve(context: Context, checkActive: () -> Unit): File {
        val directory = File(context.noBackupFilesDir, "ast-model")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("AST model directory unavailable")
        val target = File(directory, "$SHA256.onnx")
        val stalePartial = File(directory, "${target.name}.partial")
        if (stalePartial.exists() && !stalePartial.delete()) throw IOException("AST incomplete model cleanup failed")
        // Require the bundle asset even when a prior verified local copy exists.
        context.assets.open(ASSET).use { input ->
            if (verified(target, SIZE, SHA256, checkActive)) return target
            copyVerified(input, target, SIZE, SHA256, checkActive)
        }
        return target
    }

    fun verified(file: File, size: Long, sha256: String, checkActive: () -> Unit = {}): Boolean {
        if (!file.isFile || file.length() != size) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                checkActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return hex(digest.digest()) == sha256
    }

    /** Streaming copy, fsync and hash precede same-directory atomic publication. */
    fun copyVerified(input: InputStream, target: File, size: Long, sha256: String,
                     checkActive: () -> Unit = {}) {
        val partial = File(target.parentFile, "${target.name}.partial")
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        try {
            FileOutputStream(partial).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    checkActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > size) throw IOException("AST model exceeds pinned size")
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
                if (total != size || hex(digest.digest()) != sha256) throw IOException("AST model integrity mismatch")
                output.fd.sync()
            }
            checkActive()
            Files.move(partial.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
        } finally {
            if (partial.exists()) partial.delete()
        }
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
