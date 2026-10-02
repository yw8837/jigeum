package com.moa.babylanguage

import android.Manifest
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real 120-second hardware capture. Access substitution exists only in this test source set. */
class FullCaptureDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val visible = mutableStateOf(true)
    private var started = false

    @Before fun grantCapturePermissions() {
        CaptureFiles.cleanup(compose.activity)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(compose.activity.packageName, Manifest.permission.CAMERA)
        automation.grantRuntimePermission(compose.activity.packageName, Manifest.permission.RECORD_AUDIO)
    }

    @After fun cleanupOwnedMedia() {
        if (started) {
            compose.runOnIdle { visible.value = false }
            compose.waitForIdle()
        }
        CaptureFiles.cleanup(compose.activity)
    }

    @Test fun fullNativeAudioStopsAtTwoMinutesAndDecodesLateTwentySeconds() {
        val capture = LocalCapture(compose.activity)
        val done = CountDownLatch(1)
        var output: File? = null
        var error: String? = null
        try {
            compose.runOnUiThread {
                assertTrue(capture.startAudio(CaptureLimits.FULL) { file, issue ->
                    output = file
                    error = issue
                    done.countDown()
                })
            }
            assertTrue("Native maxDuration must finalize within 130 seconds without a UI stop", done.await(130, TimeUnit.SECONDS))
            assertNull(error)
            assertNotNull(output)
            val clip = CaptureFiles.inspect(compose.activity, Uri.fromFile(output!!), output, CaptureLimits.FULL)
            assertFalse(clip.hasVideo)
            assertTrue("Audio duration: ${clip.durationMs}", clip.durationMs in 119_000..120_500)
            assertLateTwentySecondsDecode(clip)
        } finally {
            compose.runOnUiThread { capture.discard() }
            output?.delete()
        }
    }

    @Test fun fullCameraStopsAtTwoMinutesAndKeepsLateAudioTrack() {
        started = true
        compose.setContent {
            if (visible.value) CaptureFlow(
                authorizeVisual = { true },
                onBack = {},
                saving = false,
                onSave = { _, _, _ -> },
                passContent = { Text("Unexpected access screen") }
            )
        }
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasText("녹화 시작") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("최대 2분 담고, 20초까지 골라요.").assertExists()
        compose.onNodeWithText("녹화 시작").assertIsDisplayed().performClick()
        compose.waitUntil(130_000) {
            compose.onAllNodesWithText("어느 순간을 들어볼까요?").fetchSemanticsNodes().isNotEmpty()
        }
        val file = File(compose.activity.cacheDir, "captures").listFiles()!!.single()
        val clip = CaptureFiles.inspect(compose.activity, Uri.fromFile(file), file, CaptureLimits.FULL)
        assertTrue(clip.hasVideo)
        assertTrue("Video duration: ${clip.durationMs}", clip.durationMs in 119_000..120_500)
        assertLateTwentySecondsDecode(clip)
        compose.onNodeWithTag("clip-play").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("재생 멈추기", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("다시 촬영하기").performScrollTo().performClick()
        assertFalse("Retake must remove the owned two-minute capture", file.exists())
    }

    private fun assertLateTwentySecondsDecode(clip: LocalClip) {
        val pcm = runBlocking { VideoAudioDecoder.decode(compose.activity, clip.uri, 99_000, 119_000) }
        assertNotNull("Recording must contain a decodable audio track beyond the old one-minute cap", pcm)
        try {
            assertTrue("Late 20-second PCM size: ${pcm!!.size}", pcm.size in 319_000..320_000)
            assertTrue("Late audio must be finite", pcm.all { it.isFinite() })
        } finally { pcm?.fill(0f) }
    }
}
