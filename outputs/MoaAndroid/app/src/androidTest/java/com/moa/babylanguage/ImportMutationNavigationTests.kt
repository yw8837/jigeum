package com.moa.babylanguage

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Real import preview/parser/store and UI actions, with a controlled pre-commit suspension. */
class ImportMutationNavigationTests {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val id = "import-ui-${UUID.randomUUID()}"
    private lateinit var store: ObservationStore
    private lateinit var file: File
    private lateinit var importer: DelayedImporter
    private val visible = mutableStateOf(true)
    private var started = false
    private val original = Observation(System.currentTimeMillis(), "옹알이", "놀이 중", "", "같이 놀아 줄래?")
    private val imported = original.copy(id = original.id + 1_000, phrase = "나도 이야기하고 있어.")
    private val billing = object : AccessBilling {
        override val state: StateFlow<BillingUiState> = MutableStateFlow(BillingUiState(status = BillingStatus.READY))
        override fun hasCurrentEntitlement() = false
        override fun refresh() = Unit
        override fun restore() = Unit
        override fun purchase(activity: Activity, plan: BillingPlan) = Unit
    }

    @Before fun setup() {
        store = ObservationStore(compose.activity, "$id.db", "$id-legacy")
        runBlocking { store.save(original) }
        file = File(compose.activity.cacheDir, "$id.json")
        file.outputStream().use { RecordTransfer.writeJson(it, StoreSnapshot(listOf(imported), "")) }
        importer = DelayedImporter(Uri.fromFile(file))
    }
    @After fun cleanup() {
        if (started) { compose.runOnIdle { visible.value = false }; compose.waitForIdle() }
        runBlocking { store.close() }
        file.delete()
        compose.activity.deleteDatabase("$id.db")
        for (name in listOf("$id-legacy", "$id-phrases")) compose.activity.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun waitPage(name: String) {
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("page-$name").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }
    private fun click(text: String) = compose.onNodeWithText(text).performScrollTo().performClick()
    private fun startMerge() {
        started = true
        compose.setContent { if (visible.value) JigeumApp(compose.activity, store, billing, recordImporter = importer) }
        waitPage("home")
        compose.onNodeWithText("돌봄").performClick()
        click("백업 파일 가져오기")
        compose.waitUntil(8_000) { compose.onAllNodesWithText("기록 합치기").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("기록 합치기").performClick()
        compose.waitUntil(8_000) { importer.paused.get() }
    }
    private fun attemptDeletion(all: Boolean) {
        // Leave and re-enter: DataTransferPanel's local busy state is replaced, root reservation persists.
        compose.onNodeWithText("오늘").performClick()
        waitPage("home")
        compose.onNodeWithText(if (all) "돌봄" else "기록").performClick()
        waitPage(if (all) "guide" else "timeline")
        click(if (all) "모든 기록 삭제" else "기록 삭제")
        click(if (all) "다시 누르면 모든 기록을 삭제해요" else "다시 누르면 기록과 보관한 장면을 삭제해요")
        compose.waitUntil(8_000) {
            compose.onAllNodesWithText("기록을 처리하고 있어요. 끝난 뒤 다시 시도해 주세요.").fetchSemanticsNodes().isNotEmpty() ||
                runBlocking { store.snapshot().observations.isEmpty() }
        }
        assertEquals("Delete cannot pass a suspended import commit", listOf(original), runBlocking { store.snapshot().observations })
        compose.onNodeWithText("기록을 처리하고 있어요. 끝난 뒤 다시 시도해 주세요.").assertExists()
    }
    @Test fun deleteAllCannotCommitBeforeLateImportAfterNavigation() {
        startMerge(); attemptDeletion(all = true)
        compose.runOnIdle { importer.resume.complete(Unit) }
        compose.waitUntil(8_000) { importer.finished.get() && runBlocking { store.snapshot().observations.size == 2 } }
        compose.waitForIdle()
        click("모든 기록 삭제")
        click("다시 누르면 모든 기록을 삭제해요")
        compose.waitUntil(8_000) { runBlocking { store.snapshot().observations.isEmpty() } }
        assertTrue(importer.finished.get())
        assertTrue(runBlocking { store.snapshot().observations.isEmpty() })
    }
    @Test fun singleDeleteWaitsUntilImportSnapshotWasApplied() {
        startMerge(); attemptDeletion(all = false)
        compose.runOnIdle { importer.resume.complete(Unit) }
        compose.waitUntil(8_000) { importer.finished.get() && compose.onAllNodesWithText("“${imported.phrase}”").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(2, runBlocking { store.snapshot().observations.size })
    }
    @Test fun disposingAppBeforeImportCommitPreventsRepopulation() {
        startMerge()
        compose.runOnIdle { visible.value = false }
        compose.waitUntil(8_000) { importer.finished.get() }
        compose.runOnIdle { importer.resume.complete(Unit) }
        assertEquals(listOf(original), runBlocking { store.snapshot().observations })
    }
    private class DelayedImporter(private val uri: Uri) : RecordImporter {
        val paused = AtomicBoolean(false)
        val finished = AtomicBoolean(false)
        val resume = CompletableDeferred<Unit>()
        @Composable override fun rememberPick(onPicked: (Uri?) -> Unit): () -> Unit = { onPicked(uri) }
        override suspend fun preview(context: Context, uri: Uri, existing: StoreSnapshot) = DeviceRecordImporter.preview(context, uri, existing)
        override suspend fun merge(store: ObservationStore, imported: ImportPreview) {
            paused.set(true)
            try { resume.await(); DeviceRecordImporter.merge(store, imported) }
            finally { finished.set(true) }
        }
    }
}
