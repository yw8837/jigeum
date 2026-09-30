package com.moa.babylanguage

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.coroutines.coroutineContext

internal enum class AlbumTheme(val label: String, val accent: Int, val paper: Int) {
    SAGE("풀빛 하루", 0xFF52714C.toInt(), 0xFFF3F7F0.toInt()),
    APRICOT("살구빛 기억", 0xFF95582F.toInt(), 0xFFFFF3E9.toInt()),
    BLUE("하늘빛 편지", 0xFF446C80.toInt(), 0xFFF0F6F9.toInt())
}

internal object MemoryArtifacts {
    const val MAX_ALBUM_RECORDS = 300
    const val MAX_ALBUM_PAGES = 300
    private val ink = 0xFF24322E.toInt()
    private val muted = 0xFF526059.toInt()
    private val exportMutex = Mutex()

    fun month(id: Long): YearMonth = YearMonth.from(Instant.ofEpochMilli(id).atZone(ZoneId.systemDefault()))
    fun date(id: Long): String = Instant.ofEpochMilli(id).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M월 d일 · HH:mm"))

    private fun folder(context: Context): File = File(context.cacheDir, "exports").apply {
        check(isDirectory || mkdirs()) { "파일을 만들 공간이 부족해요. 휴대전화 저장 공간을 확인해 주세요." }
    }

    suspend fun cleanup(context: Context) = withContext(Dispatchers.IO) {
        exportMutex.withLock {
        File(context.cacheDir, "exports").listFiles()?.filter {
            it.isFile && it.name.startsWith("jigeum-") && System.currentTimeMillis() - it.lastModified() > 86_400_000L
        }?.forEach { it.delete() }
        }
    }

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        exportMutex.withLock {
            File(context.cacheDir, "exports").listFiles()?.filter {
                it.isFile && it.name.startsWith("jigeum-")
            }?.forEach { file -> check(file.delete() || !file.exists()) { "공유용 임시 파일을 지우지 못했어요." } }
        }
    }

    private fun layout(text: String, size: Float, width: Int, color: Int = ink, bold: Boolean = false): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = size
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(size * .3f, 1f)
            .setIncludePad(false).build()
    }

    private fun draw(canvas: Canvas, text: String, x: Float, y: Float, size: Float, width: Int,
                     color: Int = ink, bold: Boolean = false): Int {
        val content = layout(text, size, width, color, bold)
        canvas.save(); canvas.translate(x, y); content.draw(canvas); canvas.restore()
        return content.height
    }

    internal fun cardPhraseLayout(phrase: String): StaticLayout {
        require(phrase.isNotBlank() && phrase.length <= 160 && RecordTransfer.validUnicode(phrase)) {
            "공유할 문장은 160자 안에서 다시 확인해 주세요."
        }
        // A card is a single quotation: collapse manual line breaks without dropping words
        // or slicing surrogate pairs. PDF exports preserve the original line breaks.
        val text = "“${phrase.replace(Regex("\\s+"), " ").trim()}”"
        var size = 61f
        var content = layout(text, size, 850, ink, true)
        while (content.height > 630 && size > 20f) {
            size -= 2f
            content = layout(text, size, 850, ink, true)
        }
        check(content.height <= 630) { "문장을 카드에 담지 못했어요. 줄바꿈을 줄여 주세요." }
        return content
    }

    /** Exclusive line end that fits. Slicing by rendered lines preserves every code unit. */
    internal fun fittingLineEnd(content: StaticLayout, start: Int, available: Int): Int {
        val top = content.getLineTop(start)
        var end = start
        while (end < content.lineCount && content.getLineBottom(end) - top <= available) end++
        return end
    }

    suspend fun card(context: Context, record: Observation): File = withContext(Dispatchers.IO) {
        exportMutex.withLock {
        require(record.phrase.isNotBlank()) { "공유할 문장을 먼저 골라 주세요." }
        val file = File(folder(context), "jigeum-card-${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(0xFFFFF9F0.toInt())
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AlbumTheme.SAGE.paper }
            canvas.drawRoundRect(64f, 195f, 1016f, 1125f, 52f, 52f, p)
            draw(canvas, date(record.id), 84f, 90f, 30f, 900, muted)
            draw(canvas, "우리 아이의 한마디", 114f, 256f, 35f, 850, AlbumTheme.SAGE.accent, true)
            val phrase = cardPhraseLayout(record.phrase)
            canvas.save(); canvas.translate(114f, 374f); phrase.draw(canvas); canvas.restore()
            val metadata = "${record.sound} · ${record.context}".replace(Regex("\\s+"), " ").trim()
            draw(canvas, metadata, 114f, 1020f, 28f, 850, muted)
            draw(canvas, "보호자가 그 순간을 담아 쓴 표현이에요.", 84f, 1210f, 27f, 900, muted)
            coroutineContext.ensureActive()
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            file
        } catch (error: Throwable) {
            file.delete(); throw error
        } finally { bitmap.recycle() }
        }
    }

    suspend fun album(context: Context, records: List<Observation>, month: YearMonth,
                      theme: AlbumTheme, includeNotes: Boolean): File = withContext(Dispatchers.IO) {
        exportMutex.withLock {
        val taskContext = coroutineContext
        val selected = records.filter { MemoryArtifacts.month(it.id) == month }.sortedBy { it.id }
        require(selected.isNotEmpty()) { "이 달에 남긴 문장이 아직 없어요." }
        require(selected.size <= MAX_ALBUM_RECORDS) { "한 권에는 300개까지 담을 수 있어요. 전체 기록은 무료 백업으로 보관할 수 있어요." }
        val file = File(folder(context), "jigeum-album-$month-${UUID.randomUUID()}.pdf")
        val document = PdfDocument()
        var page: PdfDocument.Page? = null
        var count = 0
        var y = 0f
        fun finish() {
            page?.let {
                draw(it.canvas, "보호자가 남긴 표현과 기록 · ${count}쪽", 44f, 799f, 9f, 507, muted)
                document.finishPage(it)
            }
            page = null
        }
        fun nextPage() {
            require(count < MAX_ALBUM_PAGES) {
                "아기말북이 300쪽을 넘어요. 메모의 줄바꿈을 줄이거나 메모 담기를 꺼 주세요. 기록 원본은 그대로 있어요."
            }
            finish(); count++
            page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, count).create())
            val c = page!!.canvas
            c.drawColor(theme.paper)
            draw(c, "${month.year}년 ${month.monthValue}월의 아기말북", 44f, 40f, 25f, 507, theme.accent, true)
            draw(c, "우리 함께 남긴 ${selected.size}개의 순간", 44f, 80f, 11f, 507, muted)
            y = 122f
        }
        fun paragraph(text: String, size: Float, color: Int, bold: Boolean = false) {
            val content = layout(text, size, 467, color, bold)
            var line = 0
            while (line < content.lineCount) {
                taskContext.ensureActive()
                val end = fittingLineEnd(content, line, (766f - y).toInt())
                if (end == line) {
                    nextPage()
                    continue
                }
                val top = content.getLineTop(line)
                val height = content.getLineBottom(end - 1) - top
                val canvas = page!!.canvas
                canvas.save()
                canvas.clipRect(52f, y, 519f, y + height)
                canvas.translate(52f, y - top)
                content.draw(canvas)
                canvas.restore()
                y += height
                line = end
                if (line < content.lineCount) nextPage()
            }
        }
        try {
            nextPage()
            for (record in selected) {
                coroutineContext.ensureActive()
                val phrase = "“${record.phrase.ifBlank { "${record.sound} · ${record.context}" }}”"
                val note = if (includeNotes) record.note else ""
                // Keep the date with at least the first two phrase lines. The remaining
                // rendered lines flow onto subsequent pages, including newline-heavy notes.
                if (y + 100f > 766f) nextPage()
                val c = page!!.canvas
                val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.accent; strokeWidth = 3f }
                c.drawLine(40f, y, 40f, y + 24f, accent)
                paragraph("${date(record.id)} · ${record.context}", 10f, muted)
                y += 12
                paragraph(phrase, 16f, ink, true)
                if (note.isNotBlank()) {
                    y += 14
                    paragraph(note, 11f, muted)
                }
                y += 30
            }
            finish()
            coroutineContext.ensureActive()
            file.outputStream().use(document::writeTo)
            file
        } catch (error: Throwable) {
            page?.let { runCatching { document.finishPage(it) } }; page = null
            file.delete(); throw error
        } finally { document.close() }
        }
    }

    fun share(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime; putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("아기말 파일", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "가족에게 보내기"))
    }
}
