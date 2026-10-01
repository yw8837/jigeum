package com.moa.babylanguage

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.NonCancellable
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

internal fun interface ArtifactShareLauncher {
    fun launch(context: Context, chooser: Intent)
}

internal object AndroidArtifactShareLauncher : ArtifactShareLauncher {
    override fun launch(context: Context, chooser: Intent) { context.startActivity(chooser) }
}

/** Injectable app dependency; the public activity always uses real rendering and sharing. */
internal interface ArtifactExporter {
    suspend fun card(context: Context, record: Observation, generation: Long): File
    suspend fun album(context: Context, records: List<Observation>, month: YearMonth,
                      theme: AlbumTheme, includeNotes: Boolean, generation: Long): File
    suspend fun share(context: Context, file: File, mime: String, generation: Long)
}

internal object DeviceArtifactExporter : ArtifactExporter {
    override suspend fun card(context: Context, record: Observation, generation: Long) = MemoryArtifacts.card(context, record, generation)
    override suspend fun album(context: Context, records: List<Observation>, month: YearMonth,
                               theme: AlbumTheme, includeNotes: Boolean, generation: Long) =
        MemoryArtifacts.album(context, records, month, theme, includeNotes, generation)
    override suspend fun share(context: Context, file: File, mime: String, generation: Long) = MemoryArtifacts.share(context, file, mime, generation)
}

internal object MemoryArtifacts {
    const val MAX_ALBUM_RECORDS = 300
    const val MAX_ALBUM_PAGES = 300
    // A selected recipient can open the URI after this app resumes and source records are deleted.
    // Keep only already dispatched snapshots for this bounded window, independent of the source DB.
    const val SHARED_SNAPSHOT_RETENTION_MS = 86_400_000L
    const val MAX_SHARED_SNAPSHOTS = 20
    const val MAX_SHARED_SNAPSHOT_BYTES = 67_108_864L
    private val ink = 0xFF24322E.toInt()
    private val muted = 0xFF526059.toInt()
    private val exportMutex = Mutex()
    private val generationLock = Any()
    private var exportGeneration = 0L

    fun currentGeneration(): Long = synchronized(generationLock) { exportGeneration }

    /** Call synchronously before deleting source records, not after the IO operation. */
    fun invalidatePendingExports() = synchronized(generationLock) { exportGeneration++; Unit }

    private fun requireCurrentGeneration(generation: Long) {
        check(generation == currentGeneration()) { "기록이 변경되어 내보내기를 중단했어요. 다시 선택해 주세요." }
    }

    fun month(id: Long): YearMonth = YearMonth.from(Instant.ofEpochMilli(id).atZone(ZoneId.systemDefault()))
    fun date(id: Long): String = Instant.ofEpochMilli(id).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M월 d일 · HH:mm"))

    private fun folder(context: Context): File = File(context.cacheDir, "exports").apply {
        check(isDirectory || mkdirs()) { "파일을 만들 공간이 부족해요. 휴대전화 저장 공간을 확인해 주세요." }
    }

    private fun cleanupExpired(context: Context) {
        val now = System.currentTimeMillis()
        val root = File(context.cacheDir, "exports")
        for (directory in listOf(root, File(root, "dispatched"))) {
            directory.listFiles()?.filter {
                it.isFile && it.name.startsWith("jigeum-") && now - it.lastModified() >= SHARED_SNAPSHOT_RETENTION_MS
            }?.forEach { check(it.delete() || !it.exists()) { "임시 공유 파일을 정리하지 못했어요." } }
        }
    }

    suspend fun cleanup(context: Context) = withContext(Dispatchers.IO) {
        exportMutex.withLock {
            cleanupExpired(context)
        }
    }

    suspend fun clear(context: Context) {
        invalidatePendingExports()
        withContext(Dispatchers.IO) {
            exportMutex.withLock {
                File(context.cacheDir, "exports").listFiles()?.filter {
                    it.isFile && it.name.startsWith("jigeum-")
                }?.forEach { file -> check(file.delete() || !file.exists()) { "공유용 임시 파일을 지우지 못했어요." } }
                cleanupExpired(context)
            }
        }
    }

    /** Remove only our unshared output; dispatched URI snapshots have their own lifetime. */
    suspend fun discardUnshared(context: Context, file: File) = withContext(NonCancellable + Dispatchers.IO) {
        exportMutex.withLock {
            if (file.parentFile?.canonicalFile == File(context.cacheDir, "exports").canonicalFile && file.name.startsWith("jigeum-")) {
                check(file.delete() || !file.exists()) { "임시 공유 파일을 정리하지 못했어요." }
            }
        }
    }

    private suspend fun renderOutput(render: suspend ((File) -> Unit) -> File): File {
        var created: File? = null
        try {
            return withContext(Dispatchers.IO) { render { created = it } }
        } catch (error: Throwable) {
            // withContext can be cancelled at its return handoff, after a complete file was written.
            withContext(NonCancellable + Dispatchers.IO) {
                created?.let { if (!it.delete() && it.exists()) error.addSuppressed(IllegalStateException("임시 공유 파일을 정리하지 못했어요.")) }
            }
            throw error
        }
    }

    suspend fun media(context: Context, recordId: Long, generation: Long): File = renderOutput { track ->
        exportMutex.withLock {
            requireCurrentGeneration(generation)
            val source = RecordMedia.file(context, recordId)
            check(source.isFile && source.length() > 0) { "이 기록에는 보관한 장면이 없어요." }
            val target = File(folder(context), "jigeum-${UUID.randomUUID()}.mp4").also(track)
            source.inputStream().use { input -> target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    requireCurrentGeneration(generation)
                    val size = input.read(buffer); if (size < 0) break
                    output.write(buffer, 0, size)
                }
            } }
            requireCurrentGeneration(generation)
            target
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
        canvas.withTranslation(x, y) { content.draw(this) }
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

    suspend fun card(context: Context, record: Observation,
                     generation: Long = currentGeneration()): File = renderOutput { output ->
        exportMutex.withLock {
        requireCurrentGeneration(generation)
        require(record.phrase.isNotBlank()) { "공유할 문장을 먼저 골라 주세요." }
        val file = File(folder(context), "jigeum-card-${UUID.randomUUID()}.png")
        output(file)
        val bitmap = createBitmap(1080, 1350, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(0xFFFFF9F0.toInt())
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = AlbumTheme.SAGE.paper }
            canvas.drawRoundRect(64f, 195f, 1016f, 1125f, 52f, 52f, p)
            draw(canvas, date(record.id), 84f, 90f, 30f, 900, muted)
            draw(canvas, "우리 아이의 한마디", 114f, 256f, 35f, 850, AlbumTheme.SAGE.accent, true)
            val phrase = cardPhraseLayout(record.phrase)
            canvas.withTranslation(114f, 374f) { phrase.draw(this) }
            val metadata = "${record.sound} · ${record.context}".replace(Regex("\\s+"), " ").trim()
            draw(canvas, metadata, 114f, 1020f, 28f, 850, muted)
            draw(canvas, "보호자가 그 순간을 담아 쓴 표현이에요.", 84f, 1210f, 27f, 900, muted)
            coroutineContext.ensureActive()
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            requireCurrentGeneration(generation)
            file
        } catch (error: Throwable) {
            file.delete(); throw error
        } finally { bitmap.recycle() }
        }
    }

    suspend fun album(context: Context, records: List<Observation>, month: YearMonth,
                      theme: AlbumTheme, includeNotes: Boolean,
                      generation: Long = currentGeneration()): File = renderOutput { output ->
        exportMutex.withLock {
        requireCurrentGeneration(generation)
        val taskContext = coroutineContext
        val selected = records.filter { MemoryArtifacts.month(it.id) == month }.sortedBy { it.id }
        require(selected.isNotEmpty()) { "이 달에 남긴 문장이 아직 없어요." }
        require(selected.size <= MAX_ALBUM_RECORDS) { "한 권에는 300개까지 담을 수 있어요. 전체 기록은 무료 백업으로 보관할 수 있어요." }
        val file = File(folder(context), "jigeum-album-$month-${UUID.randomUUID()}.pdf")
        output(file)
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
                canvas.withClip(52f, y, 519f, y + height) {
                    withTranslation(52f, y - top) { content.draw(this) }
                }
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
            requireCurrentGeneration(generation)
            file
        } catch (error: Throwable) {
            page?.let { runCatching { document.finishPage(it) } }; page = null
            file.delete(); throw error
        } finally { document.close() }
        }
    }

    suspend fun share(context: Context, file: File, mime: String, generation: Long,
                      launcher: ArtifactShareLauncher = AndroidArtifactShareLauncher) {
        var retained: File? = null
        var dispatched = false
        try { exportMutex.withLock {
            withContext(Dispatchers.IO) { cleanupExpired(context) }
            withContext(Dispatchers.Main.immediate) {
                coroutineContext.ensureActive()
                // Deletion invalidation and chooser launch are ordered without a suspend point.
                synchronized(generationLock) {
                    requireCurrentGeneration(generation)
                    check(file.isFile && file.length() > 0) { "공유 파일이 없어졌어요. 다시 만들어 주세요." }
                    var host = context
                    while (host is ContextWrapper && host !is Activity) host = host.baseContext
                    check(host is Activity && !host.isFinishing && !host.isDestroyed &&
                        ((host as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false)) {
                        "앱으로 돌아온 뒤 다시 공유해 주세요."
                    }
                    check(file.parentFile?.canonicalFile == folder(context).canonicalFile && file.name.startsWith("jigeum-"))
                    val sharedFolder = File(folder(context), "dispatched").apply { check(isDirectory || mkdirs()) }
                    val leased = sharedFolder.listFiles()?.filter { it.isFile && it.name.startsWith("jigeum-") }.orEmpty()
                    check(leased.size < MAX_SHARED_SNAPSHOTS && leased.sumOf { it.length() } + file.length() <= MAX_SHARED_SNAPSHOT_BYTES) {
                        "이전에 공유한 파일을 보호하고 있어요. 하루 뒤 다시 공유해 주세요."
                    }
                    val sharedFile = File(sharedFolder, file.name)
                    check(file.renameTo(sharedFile)) { "공유 파일을 준비하지 못했어요." }
                    retained = sharedFile
                    check(sharedFile.setLastModified(System.currentTimeMillis()))
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", sharedFile)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = mime; putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri("아기말 파일", uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    launcher.launch(context, Intent.createChooser(intent, "가족에게 보내기"))
                    dispatched = true
                }
            }
        } } catch (error: Throwable) {
            // Once dispatched, cancellation cannot revoke a recipient's readable snapshot.
            if (!dispatched) {
                withContext(NonCancellable + Dispatchers.IO) { retained?.delete() }
                discardUnshared(context, file)
            }
            throw error
        }
    }
}
