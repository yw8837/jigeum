package com.moa.babylanguage

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Actual app callbacks, real renderers and provider URIs; delays exist only in androidTest. */
class ArtifactSharingNavigationTests {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val id = "sharing-${UUID.randomUUID()}"
    private lateinit var store: ObservationStore
    private var exporter = ControlledExporter()
    private val billing = TestBilling()
    private val visible = mutableStateOf(true)
    private var started = false
    private val owned = mutableListOf<File>()
    private val record = Observation(System.currentTimeMillis(), "옹알이", "놀이 중", "합성 시험 메모", "같이 놀아 줄래?")

    @Before fun setup() {
        store = ObservationStore(compose.activity, "$id.db", "$id-legacy")
        runBlocking { store.save(record) }
    }
    @After fun cleanup() {
        if (started) { compose.runOnIdle { visible.value = false }; compose.waitForIdle() }
        runBlocking { store.close() }
        (owned + exporter.files + exporter.dispatchedFiles).forEach { it.delete() }
        compose.activity.deleteDatabase("$id.db")
        for (name in listOf("$id-legacy", "$id-phrases")) compose.activity.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun start(paid: Boolean = false) {
        billing.publish(paid)
        started = true
        compose.setContent { if (visible.value) JigeumApp(compose.activity, store, billing, artifactExporter = exporter) }
        waitPage("home")
    }
    private fun waitPage(page: String) {
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("page-$page").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
    private fun click(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
    private fun share(paid: Boolean = false) {
        compose.onNodeWithText(if (paid) "아기말북" else "기록").performClick()
        click(if (paid) "이 달의 아기말북 PDF 만들기" else "문장 카드 공유")
    }
    private fun delete(all: Boolean) {
        compose.onNodeWithText(if (all) "돌봄" else "기록").performClick()
        waitPage(if (all) "guide" else "timeline")
        click(if (all) "모든 기록 삭제" else "기록 삭제")
        click(if (all) "다시 누르면 모든 기록을 삭제해요" else "다시 누르면 이 기록을 삭제해요")
        compose.waitUntil(8_000) { runBlocking { store.snapshot().observations.isEmpty() } }
        compose.waitForIdle()
    }

    @Test fun pngDeletedBeforeRendererStartsCannotRecreateOrDispatch() {
        exporter = ControlledExporter(pauseBefore = true)
        start(); share()
        compose.waitUntil(8_000) { exporter.paused.get() }
        delete(all = false)
        compose.runOnIdle { exporter.resume.complete(Unit) }
        compose.waitUntil(8_000) { exporter.finished.get() }
        assertTrue(exporter.files.isEmpty())
        assertTrue(exporter.uris.isEmpty())
    }

    @Test fun pdfGeneratedBeforeDeleteAllIsCleanedWithoutDispatch() {
        exporter = ControlledExporter(pauseAfter = true)
        start(paid = true); share(paid = true)
        compose.waitUntil(8_000) { exporter.paused.get() }
        val file = exporter.files.single()
        assertTrue(file.exists())
        delete(all = true)
        compose.runOnIdle { exporter.resume.complete(Unit) }
        compose.waitUntil(8_000) { exporter.finished.get() }
        compose.waitUntil(8_000) { !file.exists() }
        assertTrue(exporter.uris.isEmpty())
    }

    @Test fun capturedShareCallbackAfterDeletionCannotMintFreshGeneration() {
        start()
        compose.onNodeWithText("기록").performClick()
        val oldShare = compose.onNodeWithText("문장 카드 공유").performScrollTo().fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        delete(all = false)
        compose.runOnIdle { oldShare.invoke() }
        compose.waitUntil(8_000) { compose.onAllNodesWithText("기록이 변경되어 내보내기를 중단했어요. 다시 선택해 주세요.").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, exporter.renderCalls.get())
        assertTrue(exporter.uris.isEmpty())
    }

    @Test fun capturedAlbumCallbackAfterDeleteAllCannotMintFreshGeneration() {
        start(paid = true)
        compose.onNodeWithText("아기말북").performClick()
        val oldExport = compose.onNodeWithText("이 달의 아기말북 PDF 만들기").performScrollTo().fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        delete(all = true)
        compose.runOnIdle { oldExport.invoke() }
        compose.waitUntil(8_000) { compose.onAllNodesWithText("기록이 변경되어 내보내기를 중단했어요. 다시 선택해 주세요.").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, exporter.renderCalls.get())
        assertTrue(exporter.uris.isEmpty())
    }

    @Test fun dispatchedPngUriRemainsReadableAfterSingleDeletionAndThenExpires() {
        start(); share()
        compose.waitUntil(8_000) { exporter.uris.size == 1 }
        val uri = exporter.uris.single()
        val before = read(uri)
        val file = exporter.dispatchedFiles.single()
        assertEquals("dispatched", file.parentFile!!.name)
        delete(all = false)
        assertArrayEquals(before, read(uri))
        assertTrue(file.setLastModified(System.currentTimeMillis() - MemoryArtifacts.SHARED_SNAPSHOT_RETENTION_MS - 1))
        runBlocking { MemoryArtifacts.cleanup(compose.activity) }
        assertFalse(file.exists())
    }

    @Test fun dispatchedPdfUriRemainsReadableAfterDeleteAll() {
        start(paid = true); share(paid = true)
        compose.waitUntil(8_000) { exporter.uris.size == 1 }
        val uri = exporter.uris.single()
        val before = read(uri)
        assertEquals("application/pdf", exporter.mime)
        delete(all = true)
        assertArrayEquals(before, read(uri))
    }

    @Test fun authorizedPdfCompletesAfterGrantExpires() {
        exporter = ControlledExporter(pauseBefore = true)
        start(paid = true); share(paid = true)
        compose.waitUntil(8_000) { exporter.paused.get() }
        compose.runOnIdle { billing.publish(false); exporter.resume.complete(Unit) }
        compose.waitUntil(8_000) { exporter.uris.size == 1 }
        assertTrue(read(exporter.uris.single()).isNotEmpty())
    }

    @Test fun disposedAppCleansRenderedButUndispatchedFile() {
        exporter = ControlledExporter(pauseAfter = true)
        start(); share()
        compose.waitUntil(8_000) { exporter.paused.get() }
        val file = exporter.files.single()
        compose.runOnIdle { visible.value = false }
        compose.waitUntil(8_000) { exporter.finished.get() && !file.exists() }
        assertTrue(exporter.uris.isEmpty())
    }

    @Test fun backgroundActivityRejectsDispatchAndCleansUnsharedFile() {
        val generation = MemoryArtifacts.currentGeneration()
        val file = runBlocking { MemoryArtifacts.card(compose.activity, record, generation) }.also { owned.add(it) }
        var rejected = false
        try {
            compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
            runBlocking {
                try { MemoryArtifacts.share(compose.activity, file, "image/png", generation, ArtifactShareLauncher { _, _ -> fail("Background chooser must not launch") }) }
                catch (_: IllegalStateException) { rejected = true }
            }
            assertTrue(rejected)
            assertFalse(file.exists())
        } finally { compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED) }
    }

    @Test fun fullSnapshotCountRejectsNewShareWithoutRevokingExistingUris() {
        val folder = File(compose.activity.cacheDir, "exports/dispatched").apply { mkdirs() }
        val existing = folder.listFiles()?.count { it.isFile && it.name.startsWith("jigeum-") } ?: 0
        repeat((MemoryArtifacts.MAX_SHARED_SNAPSHOTS - existing).coerceAtLeast(0)) {
            owned += File(folder, "jigeum-count-$id-$it.png").apply { writeText("keep $it") }
        }
        assertCapacityRejectsNewShare()
        assertTrue(owned.all { it.exists() && it.readText().startsWith("keep") })
    }

    @Test fun fullSnapshotBytesRejectsNewShareWithoutDeletingExistingSnapshot() {
        val folder = File(compose.activity.cacheDir, "exports/dispatched").apply { mkdirs() }
        val existingBytes = folder.listFiles()?.filter { it.isFile && it.name.startsWith("jigeum-") }?.sumOf { it.length() } ?: 0L
        val remaining = (MemoryArtifacts.MAX_SHARED_SNAPSHOT_BYTES - existingBytes).coerceAtLeast(0)
        owned += File(folder, "jigeum-bytes-$id.pdf").apply {
            RandomAccessFile(this, "rw").use { it.setLength(remaining) }
        }
        assertCapacityRejectsNewShare()
        assertEquals(remaining, owned.single().length())
    }

    private fun assertCapacityRejectsNewShare() {
        start(); share()
        compose.waitUntil(8_000) { compose.onAllNodesWithText("이전에 공유한 파일을 보호하고 있어요. 하루 뒤 다시 공유해 주세요.").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(exporter.uris.isEmpty())
        assertTrue(exporter.files.none { it.exists() })
    }
    private fun read(uri: Uri): ByteArray = compose.activity.contentResolver.openInputStream(uri)!!.use { it.readBytes() }

    private class ControlledExporter(private val pauseBefore: Boolean = false, private val pauseAfter: Boolean = false) : ArtifactExporter {
        val paused = AtomicBoolean(false)
        val finished = AtomicBoolean(false)
        val resume = CompletableDeferred<Unit>()
        val renderCalls = AtomicInteger()
        val files = CopyOnWriteArrayList<File>()
        val dispatchedFiles = CopyOnWriteArrayList<File>()
        val uris = CopyOnWriteArrayList<Uri>()
        var mime: String? = null
        private suspend fun render(context: Context, action: suspend () -> File): File {
            renderCalls.incrementAndGet()
            var file: File? = null
            try {
                if (pauseBefore) { paused.set(true); resume.await() }
                file = action().also { files.add(it) }
                if (pauseAfter) { paused.set(true); resume.await() }
                return file
            } catch (error: Throwable) {
                file?.let { MemoryArtifacts.discardUnshared(context, it) }
                throw error
            } finally { finished.set(true) }
        }
        override suspend fun card(context: Context, record: Observation, generation: Long) = render(context) { MemoryArtifacts.card(context, record, generation) }
        override suspend fun album(context: Context, records: List<Observation>, month: YearMonth, theme: AlbumTheme, includeNotes: Boolean, generation: Long) =
            render(context) { MemoryArtifacts.album(context, records, month, theme, includeNotes, generation) }
        override suspend fun share(context: Context, file: File, mime: String, generation: Long) {
            MemoryArtifacts.share(context, file, mime, generation, ArtifactShareLauncher { _, chooser ->
                val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
                val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
                assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                assertEquals(uri, send.clipData!!.getItemAt(0).uri)
                context.contentResolver.openInputStream(uri).use { assertTrue(it != null && it.read() >= 0) }
                dispatchedFiles.add(File(context.cacheDir, "exports/dispatched/${file.name}"))
                this.mime = mime
                uris.add(uri)
            })
        }
    }
    private class TestBilling : AccessBilling {
        private val mutable = MutableStateFlow(BillingUiState(status = BillingStatus.READY))
        override val state: StateFlow<BillingUiState> = mutable
        fun publish(paid: Boolean) { mutable.value = mutable.value.copy(isPremium = paid) }
        override fun hasCurrentEntitlement() = mutable.value.isPremium
        override fun refresh() = Unit
        override fun restore() = Unit
        override fun purchase(activity: Activity, plan: BillingPlan) = Unit
    }
}
