package com.moa.babylanguage

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

internal object CommunitySharing {
    val available: Boolean get() = CommunityContract.origin(BuildConfig.COMMUNITY_URL) != null
    fun open(context: Context, draft: File? = null) {
        check(available) { "게시판 연결을 준비하고 있어요." }
        context.startActivity(Intent(context, CommunityActivity::class.java).apply {
            draft?.let { putExtra("draft", it.name) }
        })
    }

    /** Retry cleanup after process death. A currently open draft survives rotation and long sessions. */
    fun cleanup(context: Context, keep: File? = null, now: Long = System.currentTimeMillis()) {
        File(context.cacheDir, "exports").listFiles()?.filter {
            it.isFile && Regex("community-[a-f0-9-]{36}\\.(mp4|jpg)").matches(it.name) &&
                it.canonicalFile != keep?.canonicalFile && now - it.lastModified() > 24 * 60 * 60 * 1000L
        }?.forEach { it.delete() }
    }

    suspend fun prepare(context: Context, record: Observation, clip: LocalClip?, range: ClipRange?): File {
        var owned: File? = null
        try {
            return withContext(Dispatchers.IO) {
            cleanup(context)
            val folder = File(context.cacheDir, "exports").apply { check(isDirectory || mkdirs()) }
            val output = File(folder, "community-${UUID.randomUUID()}.${if (clip?.hasVideo == true) "mp4" else "jpg"}")
            owned = output
            var rendered: File? = null
            try {
                if (clip?.hasVideo == true && range != null) {
                    rendered = TranslatedMedia.render(context, clip, range, record)
                    check(rendered.length() <= CommunityContract.MAX_BYTES) { "영상카드가 25MB를 넘어요. 더 짧은 구간으로 만들어 주세요." }
                    rendered.copyTo(output)
                } else {
                    val bitmap = MomentCardRenderer.frame(context, record, clip, range)
                    try { output.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it)) } }
                    finally { bitmap.recycle() }
                }
                check(output.length() in 1..CommunityContract.MAX_BYTES) { "공유할 카드를 준비하지 못했어요." }
                output
            } catch (error: Throwable) { output.delete(); throw error }
            finally { withContext(NonCancellable) { rendered?.delete() } }
            }
        } catch (error: Throwable) {
            // Also covers cancellation while Dispatchers.IO hands the file back to its caller.
            withContext(NonCancellable + Dispatchers.IO) { owned?.delete() }
            throw error
        }
    }
}

/** No JavaScript bridge, ambient gallery access, or automatic upload. */
class CommunityActivity : ComponentActivity() {
    private lateinit var web: WebView
    private var chooser: ValueCallback<Array<Uri>>? = null
    private var draft: File? = null
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        chooser?.onReceiveValue(uri?.let { arrayOf(it) }); chooser = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val origin = CommunityContract.origin(BuildConfig.COMMUNITY_URL) ?: run { finish(); return }
        val name = intent.getStringExtra("draft")
        if (name != null && Regex("community-[a-f0-9-]{36}\\.(mp4|jpg)").matches(name)) {
            val root = File(cacheDir, "exports").canonicalFile
            draft = File(root, name).canonicalFile.takeIf { it.parentFile == root && it.isFile && it.length() in 1..CommunityContract.MAX_BYTES }
        }
        CommunitySharing.cleanup(this, draft)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFFFFFAF3.toInt()) }
        ViewCompat.setOnApplyWindowInsetsListener(layout) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        val bar = LinearLayout(this)
        bar.addView(Button(this).apply { text = "아가톡으로"; setOnClickListener { confirmClose() } })
        bar.addView(TextView(this).apply { text = "우리 아이 자랑"; textSize = 18f; setPadding(12, 24, 0, 16) })
        layout.addView(bar)
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.mediaPlaybackRequiresUserGesture = true
            settings.setSupportMultipleWindows(false)
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (CommunityContract.allows(origin, request.url.toString())) return false
                    if (request.isForMainFrame && request.hasGesture() && request.url.scheme in listOf("https", "mailto")) {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url).addCategory(Intent.CATEGORY_BROWSABLE)) }
                    }
                    return true
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                    if (request.isForMainFrame) Toast.makeText(this@CommunityActivity, "게시판을 열지 못했어요. 연결을 확인하고 다시 열어 주세요.", Toast.LENGTH_LONG).show()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                    chooser?.onReceiveValue(null); chooser = null
                    if (!CommunityContract.allows(origin, view.url ?: "")) { callback.onReceiveValue(null); return true }
                    chooser = callback
                    val prepared = draft?.takeIf { it.isFile }
                    if (prepared == null) pick()
                    else AlertDialog.Builder(this@CommunityActivity).setTitle("어떤 파일을 첨부할까요?")
                        .setMessage("준비한 아가말 카드를 첨부한 뒤 미리보기를 확인해 주세요. 공개 버튼을 누르기 전에는 전송하지 않아요.")
                        .setPositiveButton("준비한 카드 첨부") { _, _ ->
                            val uri = FileProvider.getUriForFile(this@CommunityActivity, "$packageName.files", prepared)
                            chooser?.onReceiveValue(arrayOf(uri)); chooser = null
                        }.setNegativeButton("다른 사진·영상") { _, _ -> pick() }
                        .setOnCancelListener { chooser?.onReceiveValue(null); chooser = null }.show()
                    return true
                }
            }
        }
        layout.addView(web, LinearLayout.LayoutParams(-1, 0, 1f)); setContentView(layout)
        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) web.loadUrl(origin + if (draft != null) "/#compose" else "/")
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { confirmClose() }
        })
    }
    private fun pick() {
        try { picker.launch(arrayOf("image/*", "video/mp4")) }
        catch (_: Exception) { chooser?.onReceiveValue(null); chooser = null; Toast.makeText(this, "파일 선택기를 열지 못했어요.", Toast.LENGTH_SHORT).show() }
    }
    private fun confirmClose() {
        AlertDialog.Builder(this).setTitle("아가톡으로 돌아갈까요?").setMessage("아직 게시하지 않은 글은 지워져요. 기존 분석 결과는 그대로 남아요.")
            .setPositiveButton("돌아가기") { _, _ -> finish() }.setNegativeButton("계속 보기", null).show()
    }
    override fun onSaveInstanceState(outState: Bundle) { web.saveState(outState); super.onSaveInstanceState(outState) }
    override fun onPause() { if (::web.isInitialized) web.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); if (::web.isInitialized) web.onResume() }
    override fun onDestroy() {
        chooser?.onReceiveValue(null); chooser = null
        if (::web.isInitialized) { web.stopLoading(); web.destroy() }
        if (isFinishing) draft?.delete()
        super.onDestroy()
    }
}
