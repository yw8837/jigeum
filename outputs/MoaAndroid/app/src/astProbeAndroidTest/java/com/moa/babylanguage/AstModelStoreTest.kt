package com.moa.babylanguage

import android.content.ContextWrapper
import android.content.res.AssetManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest

class AstModelStoreTest {
    @Test fun missingBundledAssetCannotResolveEvenWithAWorkingDirectory() {
        val directory = freshDirectory()
        try {
            val target = InstrumentationRegistry.getInstrumentation().targetContext
            // createPackageContext("android") can inherit instrumentation asset paths.
            // Resources.getSystem() is the framework-only asset set, with no app overlay.
            val missingAssetContext = object : ContextWrapper(target) {
                override fun getNoBackupFilesDir(): File = directory
                override fun getAssets(): AssetManager = android.content.res.Resources.getSystem().assets
            }
            assertThrows(java.io.IOException::class.java) { AstModelStore.resolve(missingAssetContext) {} }
            assertTrue(File(directory, "ast-model").listFiles().orEmpty().isEmpty())
        } finally { directory.deleteRecursively() }
    }

    @Test fun missingCorruptAndPartialModelsAreNeverVerified() {
        val directory = freshDirectory()
        try {
            val bytes = "public synthetic model storage fixture".toByteArray()
            val hash = sha(bytes)
            val target = File(directory, "synthetic.onnx")
            assertFalse(AstModelStore.verified(target, bytes.size.toLong(), hash))
            target.writeBytes(bytes.copyOfRange(0, 5))
            assertFalse(AstModelStore.verified(target, bytes.size.toLong(), hash))
            target.writeBytes(bytes.map { (it.toInt() xor 1).toByte() }.toByteArray())
            assertFalse(AstModelStore.verified(target, bytes.size.toLong(), hash))
            assertThrows(java.io.IOException::class.java) {
                AstModelStore.copyVerified(ByteArrayInputStream(bytes), target, bytes.size.toLong(), "0".repeat(64))
            }
            assertFalse(File(directory, "synthetic.onnx.partial").exists())
            assertFalse(AstModelStore.verified(target, bytes.size.toLong(), hash))
            AstModelStore.copyVerified(ByteArrayInputStream(bytes), target, bytes.size.toLong(), hash)
            assertTrue(AstModelStore.verified(target, bytes.size.toLong(), hash))
        } finally { directory.deleteRecursively() }
    }

    @Test fun interruptedCopyCannotPublishAndResumesWithFreshCopy() {
        val directory = freshDirectory()
        try {
            val bytes = ByteArray(150_000) { (it % 251).toByte() }
            val hash = sha(bytes)
            val target = File(directory, "synthetic.onnx")
            var checks = 0
            assertThrows(CancellationException::class.java) {
                AstModelStore.copyVerified(ByteArrayInputStream(bytes), target, bytes.size.toLong(), hash) {
                    if (++checks == 2) throw CancellationException("synthetic cancellation")
                }
            }
            assertFalse(target.exists())
            assertFalse(File(directory, "synthetic.onnx.partial").exists())
            AstModelStore.copyVerified(ByteArrayInputStream(bytes), target, bytes.size.toLong(), hash)
            assertTrue(AstModelStore.verified(target, bytes.size.toLong(), hash))
        } finally { directory.deleteRecursively() }
    }

    @Test fun bundledPinnedModelResolvesAndDetectsSameLengthCorruption() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val target = AstModelStore.resolve(context) {}
        assertEquals(AstModelStore.SIZE, target.length())
        assertTrue(AstModelStore.verified(target, AstModelStore.SIZE, AstModelStore.SHA256))
        // Exclusive test runs required; no other real analysis owns the cache at this point.
        try {
            java.io.RandomAccessFile(target, "rw").use { file ->
                file.seek(32)
                val original = file.readUnsignedByte()
                file.seek(32)
                file.write(original xor 1)
            }
            assertFalse(AstModelStore.verified(target, AstModelStore.SIZE, AstModelStore.SHA256))
        } finally {
            val restored = AstModelStore.resolve(context) {}
            assertTrue(AstModelStore.verified(restored, AstModelStore.SIZE, AstModelStore.SHA256))
        }
    }

    // Instrumentation executes with the target app UID; the test APK context's
    // cache directory belongs to a different package and may not be writable.
    private fun freshDirectory(): File = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
        "ast-store-test-${System.nanoTime()}").apply { check(mkdirs()) }

    private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}
