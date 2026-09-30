package com.moa.babylanguage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.text.StaticLayout
import android.text.TextPaint
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class MemoryArtifactsTests {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun record(id: Long = System.currentTimeMillis(), note: String = "") = Observation(
        id, "옹알이", "놀이 중", note,
        "나도 같이 이야기하고 있어. 내 작은 소리에 한 번 답해 줄래?", "AUDIO")

    @Test fun freeCardCreatesReadableSizedPng() = runBlocking {
        val file = MemoryArtifacts.card(context, record())
        try {
            val bitmap = BitmapFactory.decodeFile(file.path)
            assertNotNull(bitmap)
            assertEquals(1080, bitmap.width); assertEquals(1350, bitmap.height)
            assertTrue(file.length() > 1000)
            bitmap.recycle()
        } finally { file.delete() }
    }

    @Test fun monthlyPdfPaginatesLongKoreanNotesAndAllThemes() = runBlocking {
        val now = System.currentTimeMillis()
        val records = (0 until 12).map { record(now-it*1000L, "우리 아이와 함께 남긴 대화예요. ".repeat(20)) }
        for (theme in AlbumTheme.entries) {
            val file = MemoryArtifacts.album(context, records, MemoryArtifacts.month(now), theme, true)
            try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        assertTrue(renderer.pageCount >= 3)
                        renderer.openPage(0).use { page ->
                            val bitmap = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
                            try {
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                assertTrue(bitmap.getPixel(5, 5) != 0)
                            } finally { bitmap.recycle() }
                        }
                    }
                }
                assertTrue(file.length() > 3000)
            } finally { file.delete() }
        }
    }

    @Test fun emptyAndOverLimitMonthFailsWithoutPartialOutput() = runBlocking {
        val before = java.io.File(context.cacheDir, "exports").listFiles()?.map { it.name }?.toSet().orEmpty()
        for (records in listOf(emptyList(), List(301) { record() })) {
            var failed = false
            try { MemoryArtifacts.album(context, records, YearMonth.now(), AlbumTheme.SAGE, false) }
            catch (_: IllegalArgumentException) { failed = true }
            assertTrue(failed)
        }
        val after = java.io.File(context.cacheDir, "exports").listFiles()?.map { it.name }?.toSet().orEmpty()
        assertEquals(before, after)
    }

    @Test fun newlineHeavyCardKeepsEveryWordAndEmojiInsideQuotationArea() {
        val phrase = "안녕😀\n".repeat(25)
        val content = MemoryArtifacts.cardPhraseLayout(phrase)
        assertTrue(content.height <= 630)
        assertEquals("“${phrase.replace(Regex("\\s+"), " ").trim()}”", content.text.toString())
        assertTrue(RecordTransfer.validUnicode(content.text.toString()))
    }

    @Test fun renderedLinePaginationPreservesEveryCharacterIncludingLastEmoji() {
        val note = "😀\n".repeat(240) + "마지막 문장도 모두 남아요 🥰"
        val paint = TextPaint().apply { textSize = 11f }
        val content = StaticLayout.Builder.obtain(note, 0, note.length, paint, 467)
            .setIncludePad(false).setLineSpacing(3.3f, 1f).build()
        val restored = StringBuilder()
        var start = 0
        var pages = 0
        while (start < content.lineCount) {
            val end = MemoryArtifacts.fittingLineEnd(content, start, 644)
            assertTrue("Every page must make progress", end > start)
            assertTrue(content.getLineBottom(end - 1) - content.getLineTop(start) <= 644)
            val chunk = note.substring(content.getLineStart(start), content.getLineEnd(end - 1))
            assertTrue(RecordTransfer.validUnicode(chunk))
            restored.append(chunk)
            start = end
            pages++
        }
        assertTrue(pages > 4)
        assertEquals(note, restored.toString())
    }

    @Test fun newlineHeavyPdfRendersThroughTheLastPage() = runBlocking {
        val row = record(note = "\n".repeat(980) + "마지막 문장 🥰")
        val file = MemoryArtifacts.album(context, listOf(row), MemoryArtifacts.month(row.id), AlbumTheme.SAGE, true)
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    assertTrue(renderer.pageCount >= 20)
                    renderer.openPage(renderer.pageCount - 1).use { page ->
                        val bitmap = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
                        try {
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val background = bitmap.getPixel(5, 5)
                            var bodyInk = false
                            for (y in 122 until 766) {
                                for (x in 52 until 519) {
                                    if (bitmap.getPixel(x, y) != background) { bodyInk = true; break }
                                }
                                if (bodyInk) break
                            }
                            assertTrue("Final note line is rendered in the body area", bodyInk)
                        } finally { bitmap.recycle() }
                    }
                }
            }
        } finally { file.delete() }
    }

    @Test fun deletionClearsOwnedSharedArtifactsAndPreservesUnrelatedCache() = runBlocking {
        val file = MemoryArtifacts.card(context, record())
        val unrelated = java.io.File(file.parentFile, "other-module-cache.txt")
        unrelated.writeText("keep")
        try {
            MemoryArtifacts.clear(context)
            assertFalse(file.exists())
            assertTrue(unrelated.exists())
        } finally { unrelated.delete(); file.delete() }
    }

    @Test fun pathologicalNotesHitPageCapWithoutPartialPdf() = runBlocking {
        val directory = java.io.File(context.cacheDir, "exports")
        val before = directory.listFiles()?.map { it.name }?.toSet().orEmpty()
        val now = System.currentTimeMillis()
        val rows = List(16) { record(now + it, "\n".repeat(980) + "마지막") }
        var rejected = false
        try {
            MemoryArtifacts.album(context, rows, MemoryArtifacts.month(now), AlbumTheme.SAGE, true)
        } catch (e: IllegalArgumentException) {
            rejected = true
            assertTrue(e.message.orEmpty().contains("300쪽"))
        }
        assertTrue(rejected)
        assertEquals(before, directory.listFiles()?.map { it.name }?.toSet().orEmpty())
    }
}
