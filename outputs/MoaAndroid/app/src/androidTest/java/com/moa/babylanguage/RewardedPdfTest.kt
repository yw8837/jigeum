package com.moa.babylanguage

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.YearMonth
import java.util.concurrent.CopyOnWriteArrayList

class RewardedPdfTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun failureReturnsCreditAndSuccessfulPdfSpendsExactlyOne() {
        val rewards = TestRewardAccess().apply { earn(RewardKind.PDF) }
        var lease: RewardLease? = null
        var fail = true
        val completed = CopyOnWriteArrayList<Boolean>()
        val exporter = object : ArtifactExporter {
            override suspend fun card(context: Context, record: Observation, generation: Long): File = error("Unused")
            override suspend fun album(context: Context, records: List<Observation>, month: YearMonth,
                theme: AlbumTheme, includeNotes: Boolean, generation: Long): File {
                if (fail) error("Synthetic PDF rendering failure")
                return DeviceArtifactExporter.album(context, records, month, theme, includeNotes, generation)
            }
            override suspend fun share(context: Context, file: File, mime: String, generation: Long) {
                assertEquals("application/pdf", mime)
                assertTrue(file.length() > 100)
                assertEquals("%PDF", file.inputStream().use { String(it.readNBytes(4)) })
            }
        }
        compose.setContent { MaterialTheme {
            AlbumScreen(listOf(Observation(System.currentTimeMillis(), "옹알이", "시험", "", "함께 있는 순간")),
                premium = true, onPremium = {}, onMessage = {}, artifactExporter = exporter,
                authorizeExport = { rewards.reserve(RewardKind.PDF).also { lease = it } != null },
                onExportFinished = { success ->
                    check(rewards.finish(checkNotNull(lease), success)); lease = null; completed += success
                })
        } }
        fun click() { compose.onNodeWithText("이 달의 아기말북 PDF 만들기").performScrollTo().performClick() }
        click()
        compose.waitUntil(10_000) { completed.size == 1 }
        assertFalse(completed[0]); assertTrue(rewards.available(RewardKind.PDF))
        fail = false
        click()
        compose.waitUntil(10_000) { completed.size == 2 }
        assertTrue(completed[1]); assertFalse(rewards.available(RewardKind.PDF))
        click()
        compose.waitForIdle()
        assertEquals(2, completed.size)
    }
}
