package com.moa.babylanguage

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.InstallErrorCode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlayUpdateNoticeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun prefs() = context.getSharedPreferences("qa-update-${System.nanoTime()}", Context.MODE_PRIVATE)

    @Test fun versionAndReminderRulesDoNotAnnounceEqualOlderOrJustDismissedBuilds() {
        val day = 86_400_000L
        assertFalse(PlayUpdateNotice.shouldPrompt(43, 43, -1, 0, day))
        assertFalse(PlayUpdateNotice.shouldPrompt(43, 42, -1, 0, day))
        assertTrue(PlayUpdateNotice.shouldPrompt(43, 44, -1, 0, day))
        assertFalse(PlayUpdateNotice.shouldPrompt(43, 44, 44, day, day + day - 1))
        assertTrue(PlayUpdateNotice.shouldPrompt(43, 44, 44, day, day + day))
        assertTrue(PlayUpdateNotice.shouldPrompt(43, 45, 44, day, day + 1))
        assertTrue(PlayUpdateNotice.shouldPrompt(43, 44, 44, day, day - 1))
    }

    @Test fun unavailableAndFailedPlayQueriesNeverBlockUse() {
        val fake = FakeAppUpdateManager(context)
        val notice = PlayUpdateNotice(fake, prefs(), 43)
        compose.setContent { MaterialTheme { StartupUpdatePrompt(notice, true, { error("not called") }, {}) } }
        compose.runOnUiThread { notice.check() }
        compose.waitForIdle()
        compose.onNodeWithTag("play-update-dialog").assertDoesNotExist()
        val failed = PlayUpdateNotice(fake, prefs(), 43)
        compose.runOnUiThread {
            fake.setInstallErrorCode(InstallErrorCode.ERROR_API_NOT_AVAILABLE)
            failed.check()
        }
        compose.waitForIdle()
        assertNull(failed.version.value)
        notice.close(); failed.close()
    }

    @Test fun noticeWaitsForHomeAndStoreFailureKeepsRetryAndLaterAvailable() {
        val fake = FakeAppUpdateManager(context)
        val notice = PlayUpdateNotice(fake, prefs(), 43)
        val eligible = mutableStateOf(false)
        var failures = 0
        var opens = 0
        compose.setContent { MaterialTheme { StartupUpdatePrompt(notice, eligible.value, { opens++; false }, { failures++ }) } }
        compose.runOnUiThread { fake.setUpdateAvailable(44); notice.check() }
        compose.waitUntil(5_000) { notice.version.value == 44 }
        compose.onNodeWithTag("play-update-dialog").assertDoesNotExist()
        compose.runOnIdle { eligible.value = true }
        compose.onNodeWithText("새로운 아가톡이 있어요").assertIsDisplayed()
        compose.onNodeWithText("업데이트").performClick()
        compose.runOnIdle { assertEquals(1, failures); assertEquals(1, opens) }
        compose.onNodeWithTag("play-update-dialog").assertIsDisplayed()
        compose.onNodeWithText("나중에").performClick()
        compose.onNodeWithTag("play-update-dialog").assertDoesNotExist()
        notice.close()
    }

    @Test fun dismissalSurvivesRestartButNewerReleaseCanPrompt() {
        val fake = FakeAppUpdateManager(context)
        val preferences = prefs()
        var time = 1_000L
        var tick = 0L
        val first = PlayUpdateNotice(fake, preferences, 43, { time }, { tick })
        compose.runOnUiThread { fake.setUpdateAvailable(44); first.check() }
        compose.waitUntil(5_000) { first.version.value == 44 }
        compose.runOnIdle { first.later(); first.close() }
        val restart = PlayUpdateNotice(fake, preferences, 43, { time }, { tick })
        compose.runOnUiThread { restart.check() }
        compose.waitForIdle()
        assertNull(restart.version.value)
        time += 1
        tick += 300_000
        compose.runOnUiThread { fake.setUpdateAvailable(45); restart.check() }
        compose.waitUntil(5_000) { restart.version.value == 45 }
        restart.close()
    }

    @Test fun openingStoreDismissesWithoutClaimingInstallationOrChangingEntitlement() {
        val fake = FakeAppUpdateManager(context)
        val notice = PlayUpdateNotice(fake, prefs(), 43)
        var requested = false
        compose.setContent { MaterialTheme { StartupUpdatePrompt(notice, true, { requested = true; true }, {}) } }
        compose.runOnUiThread { fake.setUpdateAvailable(44); notice.check() }
        compose.waitUntil(5_000) { notice.version.value == 44 }
        compose.onNodeWithText("업데이트").performClick()
        compose.runOnIdle { assertTrue(requested); assertNull(notice.version.value) }
        compose.onNodeWithTag("play-update-dialog").assertDoesNotExist()
        notice.close()
    }
}
