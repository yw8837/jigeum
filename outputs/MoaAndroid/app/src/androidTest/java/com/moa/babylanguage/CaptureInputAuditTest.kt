package com.moa.babylanguage

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

/** Real metadata/size boundaries. All inputs are synthetic, local and removed in finally. */
class CaptureInputAuditTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun malformedEmptyOversizedAndMissingFilesAreRejectedThenValidImportWorks() {
        val file = File(context.cacheDir, "input-audit-${UUID.randomUUID()}.mp4")
        try {
            assertTrue(runCatching { CaptureFiles.inspect(context, Uri.fromFile(file)) }.isFailure)
            file.writeBytes(byteArrayOf())
            assertTrue(runCatching { CaptureFiles.inspect(context, Uri.fromFile(file)) }.isFailure)
            file.writeText("not a video")
            assertTrue(runCatching { CaptureFiles.inspect(context, Uri.fromFile(file)) }.isFailure)
            RandomAccessFile(file, "rw").use { it.setLength(100L * 1024 * 1024 + 1) }
            val tooLarge = runCatching { CaptureFiles.inspect(context, Uri.fromFile(file)) }.exceptionOrNull()
            assertTrue(tooLarge is IllegalArgumentException)
            assertTrue(tooLarge!!.message!!.contains("100MB"))
            instrumentation.context.assets.open("range-silence-tone.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
            val valid = CaptureFiles.inspect(context, Uri.fromFile(file))
            assertTrue(valid.hasVideo)
            assertTrue("Import never adopts/deletes provider-owned files", valid.ownedFile == null)
            assertTrue(file.exists())
        } finally { file.delete() }
    }

    @Test fun clipsBelowOneSecondAndAboveOneMinuteAreRejected() {
        for (name in listOf("under-second.mp4", "over-minute.mp4")) {
            val file = File(context.cacheDir, "input-audit-${UUID.randomUUID()}.mp4")
            try {
                instrumentation.context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
                val error = runCatching { CaptureFiles.inspect(context, Uri.fromFile(file)) }.exceptionOrNull()
                assertTrue("$name: $error", error is IllegalArgumentException)
                assertTrue(error!!.message!!.contains("1초 이상, 1분 이내"))
            } finally { file.delete() }
        }
    }
}
