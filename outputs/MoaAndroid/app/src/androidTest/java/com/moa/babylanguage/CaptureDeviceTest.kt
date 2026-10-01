package com.moa.babylanguage

import android.Manifest
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking

/** CameraX/MediaRecorder/MediaPlayer on the real emulator. No baby/intent accuracy claim. */
class CaptureDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val visible = mutableStateOf(true)
    private var started = false
    @Before fun grant() {
        // This harness launches ComponentActivity, bypassing MainActivity's cold-start
        // cleanup. A previous force-stopped UI probe may have left an owned capture.
        CaptureFiles.cleanup(compose.activity)
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        ui.grantRuntimePermission(compose.activity.packageName, Manifest.permission.CAMERA)
        ui.grantRuntimePermission(compose.activity.packageName, Manifest.permission.RECORD_AUDIO)
    }
    @After fun cleanup() {
        if (started) { compose.runOnIdle { visible.value = false }; compose.waitForIdle() }
        CaptureFiles.cleanup(compose.activity)
    }
    private fun start() {
        started = true
        compose.setContent { if (visible.value) CaptureFlow({ false }, {}, false, { _, _, _ -> }, { Text("이용권") }) }
    }
    private fun click(text: String) { compose.onNodeWithText(text).performScrollTo().performClick() }
    private fun waitText(text: String, timeout: Long = 12_000) {
        compose.waitUntil(timeout) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun realVideoRecordsAudioPlaysAndPrivateFileCannotBeShared() {
        start()
        compose.waitUntil(15_000) { compose.onAllNodes(hasText("녹화 시작") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        click("녹화 시작"); waitText("0:03 / 1:00")
        click("촬영 마치기"); waitText("어느 순간을 들어볼까요?", 20_000)
        val file = File(compose.activity.cacheDir, "captures").listFiles()!!.single()
        val clip = CaptureFiles.inspect(compose.activity, Uri.fromFile(file), file)
        assertTrue(clip.hasVideo)
        assertTrue(clip.durationMs in 2_000..10_000)
        assertTrue(file.length() > 0)
        val pcm = runBlocking { VideoAudioDecoder.decode(compose.activity, clip.uri, 0, 2_000) }
        assertNotNull("Camera recording must contain a decodable audio track", pcm)
        assertTrue("Recorded audio must cover the selected interval", pcm!!.size in 30_000..32_000)
        pcm.fill(0f)
        assertTrue(runCatching { FileProvider.getUriForFile(compose.activity, compose.activity.packageName + ".files", file) }.isFailure)
        compose.onNodeWithTag("clip-play").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("재생 멈추기", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        click("다시 촬영하기")
        assertFalse(file.exists())
    }
    @Test fun actualAudioFileCanBePlayedAndBackgroundCancelsAnotherCapture() {
        start(); click("카메라 없이 소리만 녹음"); click("녹음 시작")
        waitText("0:02 / 1:00"); click("촬영 마치기"); waitText("어느 순간을 들어볼까요?")
        val file = File(compose.activity.cacheDir, "captures").listFiles()!!.single()
        val clip = CaptureFiles.inspect(compose.activity, Uri.fromFile(file), file)
        assertFalse(clip.hasVideo)
        val player = MediaPlayer()
        try {
            player.setDataSource(file.absolutePath); player.prepare(); player.start()
            Thread.sleep(250)
            assertTrue(player.currentPosition > 0)
        } finally { player.release() }
        click("다시 촬영하기"); click("녹음 시작"); waitText("0:01 / 1:00")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitText("앱을 벗어나 촬영을 중단했어요. 다시 시작해 주세요.")
        assertTrue(File(compose.activity.cacheDir, "captures").listFiles().isNullOrEmpty())
    }
    @Test fun audioStopsAtOneMinuteWithoutUserTiming() {
        val capture = LocalCapture(compose.activity)
        val done = CountDownLatch(1)
        var output: File? = null
        var error: String? = null
        try {
            compose.runOnUiThread { assertTrue(capture.startAudio { file, issue -> output = file; error = issue; done.countDown() }) }
            assertTrue("Native maxDuration must finalize without a UI timer", done.await(68, TimeUnit.SECONDS))
            assertNull(error)
            val clip = CaptureFiles.inspect(compose.activity, Uri.fromFile(output!!), output)
            assertTrue("${clip.durationMs}", clip.durationMs in 59_000..60_500)
        } finally { compose.runOnUiThread { capture.discard() }; output?.delete() }
    }
    @Test fun videoStopsAtOneMinuteAndKeepsAudioTrack() {
        start()
        compose.waitUntil(15_000) { compose.onAllNodes(hasText("녹화 시작") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        click("녹화 시작")
        waitText("어느 순간을 들어볼까요?", 75_000)
        val file = File(compose.activity.cacheDir, "captures").listFiles()!!.single()
        val clip = CaptureFiles.inspect(compose.activity, Uri.fromFile(file), file)
        assertTrue("${clip.durationMs}", clip.durationMs in 59_000..60_500)
        val pcm = runBlocking { VideoAudioDecoder.decode(compose.activity, clip.uri, 55_000, 58_000) }
        assertNotNull("Late segment must still contain a decodable audio track", pcm)
        assertTrue("${pcm!!.size}", pcm.size in 47_000..48_000)
        pcm.fill(0f)
    }

    @Test fun authorizedEntryAndResumePrepareButNeverStartRecording() {
        start(); waitText("녹화 시작")
        compose.onNodeWithText("카메라·마이크 허용하기").assertDoesNotExist()
        compose.onNodeWithText("촬영 마치기").assertDoesNotExist()
        assertTrue(File(compose.activity.cacheDir, "captures").listFiles().isNullOrEmpty())
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitText("녹화 시작")
        compose.onNodeWithText("촬영 마치기").assertDoesNotExist()
        click("카메라 없이 소리만 녹음")
        compose.onNodeWithText("녹음 시작").assertIsEnabled()
        compose.onNodeWithText("마이크 허용하기").assertDoesNotExist()
        assertTrue(File(compose.activity.cacheDir, "captures").listFiles().isNullOrEmpty())
    }
}
