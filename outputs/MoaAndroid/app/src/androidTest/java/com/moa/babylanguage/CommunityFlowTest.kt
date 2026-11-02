package com.moa.babylanguage

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Uses an agent-created colour card only. Never posts or reads gallery/record media. */
class CommunityFlowTest {
    private fun findWeb(view: View): WebView? = if (view is WebView) view else
        (view as? ViewGroup)?.let { group -> (0 until group.childCount).firstNotNullOfOrNull { findWeb(group.getChildAt(it)) } }

    private fun js(scenario: ActivityScenario<CommunityActivity>, expression: String): String {
        val latch = CountDownLatch(1); var result = ""
        scenario.onActivity { activity -> findWeb(activity.window.decorView)!!.evaluateJavascript(expression) { result = it; latch.countDown() } }
        check(latch.await(5, TimeUnit.SECONDS)) { "WebView script callback timed out" }
        return result
    }
    private fun awaitJs(scenario: ActivityScenario<CommunityActivity>, expression: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        do { if (js(scenario, expression) == "true") return; Thread.sleep(150) } while (System.nanoTime() < deadline)
        fail("Board condition was not reached: $expression")
    }
    private fun tapWeb(scenario: ActivityScenario<CommunityActivity>, selector: String) {
        val rect = org.json.JSONArray(js(scenario, "(() => { const r = document.querySelector('$selector').getBoundingClientRect(); return [r.x+r.width/2,r.y+r.height/2,innerWidth]; })()"))
        var x = 0f; var y = 0f
        scenario.onActivity { activity ->
            val web = findWeb(activity.window.decorView)!!
            val origin = IntArray(2); web.getLocationOnScreen(origin)
            val scale = web.width / rect.getDouble(2)
            x = (origin[0] + rect.getDouble(0) * scale).toFloat()
            y = (origin[1] + rect.getDouble(1) * scale).toFloat()
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val now = android.os.SystemClock.uptimeMillis()
        for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
            val event = android.view.MotionEvent.obtain(now, now + if(action == android.view.MotionEvent.ACTION_UP) 80 else 0, action, x, y, 0)
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
            check(automation.injectInputEvent(event, true)); event.recycle()
        }
    }
    private fun clickText(label: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        do {
            val node = automation.rootInActiveWindow?.findAccessibilityNodeInfosByText(label)?.firstOrNull { it.text?.toString()?.contains(label) == true }
            if (node != null && node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return
            Thread.sleep(150)
        } while (System.nanoTime() < deadline)
        fail("Control not found: $label")
    }
    @Test fun preparedCardRequiresSelectionAndReturnCleansOnlyDraft() {
        assertTrue("Run with configured HTTPS board", CommunitySharing.available)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val draft = File(context.cacheDir, "exports/community-${UUID.randomUUID()}.jpg")
        draft.parentFile!!.mkdirs()
        val bitmap = Bitmap.createBitmap(400, 500, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(186, 203, 165)) }
        draft.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
        try {
            ActivityScenario.launch<CommunityActivity>(Intent(context, CommunityActivity::class.java).putExtra("draft", draft.name)).use { scenario: ActivityScenario<CommunityActivity> ->
                awaitJs(scenario, "document.querySelector('input[type=file]') !== null")
                assertEquals("0", js(scenario, "document.querySelector('input[type=file]').files.length"))
                assertEquals("false", js(scenario, "document.body.innerText.includes('ChatGPT로 계속')"))
                tapWeb(scenario, ".file-pick")
                clickText("준비한 카드 첨부")
                awaitJs(scenario, "document.querySelector('.preview img') !== null && document.querySelector('.preview img').complete")
                assertTrue(draft.exists())
                assertEquals("true", js(scenario, "document.querySelector('button.full').disabled"))
                val capture = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                val evidence = File(context.getExternalFilesDir(null), "community-preview.png")
                evidence.outputStream().use { capture.compress(Bitmap.CompressFormat.PNG, 100, it) }; capture.recycle()
                clickText("아가톡으로")
                clickText("계속 보기")
                assertTrue(draft.exists())
                clickText("아가톡으로")
                clickText("돌아가기")
            }
            assertFalse("Finished activity must release its draft", draft.exists())
        } finally { draft.delete() }
    }
}
