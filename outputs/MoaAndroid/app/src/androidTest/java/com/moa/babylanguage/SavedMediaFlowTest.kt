package com.moa.babylanguage

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.app.ActivityOptionsCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

/** Real app callbacks and real exports. Only interpretation is supplied by a test analyzer. */
class SavedMediaFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val testName = org.junit.rules.TestName()
    private val id = "saved-media-${UUID.randomUUID()}"
    private lateinit var store: ObservationStore
    private lateinit var clip: LocalClip
    private val visible = mutableStateOf(true)
    private var started = false
    private var problem: String? = null
    private var failAnalysis = false
    private var pickedUri: Uri? = null
    private val analyzedUris = java.util.concurrent.CopyOnWriteArrayList<Uri>()
    private val before = mutableSetOf<Long>()
    private val resolver get() = compose.activity.contentResolver
    private val collection get() = MediaStore.Files.getContentUri("external")

    private fun rows(): Set<Long> = resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID),
        "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME}=?", arrayOf(compose.activity.packageName), null)!!.use {
        buildSet { while (it.moveToNext()) add(it.getLong(0)) }
    }

    @Before fun setup() {
        store = ObservationStore(compose.activity, "$id.db", id)
        before += rows()
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$id.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/AgattokQA/")
        })!!
        val asset = if (testName.methodName.startsWith("hdr")) "scene-hdr-pq.mp4" else "media-quadrants-rotated.mp4"
        InstrumentationRegistry.getInstrumentation().context.assets.open(asset).use { input ->
            resolver.openOutputStream(uri)!!.use { input.copyTo(it) }
        }
        clip = CaptureFiles.inspect(compose.activity, uri, limits = CaptureLimits.FULL)
    }

    @After fun cleanup() {
        if (started) { compose.runOnIdle { visible.value = false }; compose.waitForIdle() }
        runBlocking { store.deleteAll(); store.close(); GalleryMedia.cleanupInterrupted(compose.activity) }
        compose.activity.deleteDatabase("$id.db")
        (rows() - before).forEach { resolver.delete(ContentUris.withAppendedId(collection, it), null, null) }
        compose.activity.getSharedPreferences(id, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun start(preview: Boolean = true, recordVoice: Boolean = false) {
        if (recordVoice) InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            compose.activity.packageName, android.Manifest.permission.RECORD_AUDIO)
        val billing = object : AccessBilling {
            override val state = MutableStateFlow(BillingUiState(status = BillingStatus.READY))
            override fun hasCurrentEntitlement() = false
            override fun refresh() = Unit
            override fun restore() = Unit
            override fun purchase(activity: Activity, plan: BillingPlan) = Unit
        }
        val analyzer = object : ClipAnalyzer {
            override suspend fun analyze(context: Context, uri: Uri, range: ClipRange, visual: Boolean): ClipAnalysisResult {
                check(uri == clip.uri || uri == pickedUri)
                analyzedUris += uri
                if (failAnalysis) error("시험용 분석 실패")
                return if (problem != null) ClipAnalysisResult(null, problem = problem)
                else ClipAnalysisResult(ClipInterpretation(if (uri == clip.uri) "옹알옹알, 내 목소리가 들리나요?" else "새 영상의 웃는 소리예요.", "옹알이",
                    "시험용 관찰", "시험용 안내", range.label(), visual, basis = "AUDIO"))
            }
        }
        val registryOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                    dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(checkNotNull(pickedUri)))
                }
            }
        }
        started = true
        compose.setContent { CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
            if (visible.value) JigeumApp(compose.activity, store, billing,
                captureAnalyzer = analyzer, captureSeed = if (recordVoice) null else clip, testDistribution = preview)
        } }
        waitText("아기말 번역하기"); click("아기말 번역하기")
        if (recordVoice) {
            click("카메라 없이 소리만 녹음"); click("녹음 시작")
            waitText("0:03 / 1:00"); click("촬영 마치기"); waitText("어느 순간을 들어볼까요?")
            val file = java.io.File(compose.activity.cacheDir, "captures").listFiles()!!.single()
            clip = CaptureFiles.inspect(compose.activity, Uri.fromFile(file), file)
            assertFalse(clip.hasVideo)
        }
        val analyzeLabel = if (preview && !recordVoice) "영상 + 소리로 번역하기" else "이 구간 소리로 번역하기"
        waitText(analyzeLabel)
        click(analyzeLabel)
        compose.waitUntil(8_000) {
            compose.onAllNodesWithTag(if (problem != null || failAnalysis) "capture-message" else "baby-phrase")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitText(text: String) { compose.waitUntil(12_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }
    private fun newOutput(beforeSave: Set<Long>, mime: String): Uri {
        compose.waitUntil(45_000) { rows().any { it !in beforeSave } }
        val uri = ContentUris.withAppendedId(collection, (rows() - beforeSave).single())
        compose.waitUntil(45_000) { resolver.query(uri, arrayOf(MediaStore.MediaColumns.IS_PENDING), null, null, null)!!.use { it.moveToFirst() && it.getInt(0) == 0 } }
        assertEquals(mime, resolver.getType(uri))
        return uri
    }

    @Test fun galleryVideoResultSavesPngMp4AndSceneRecord() {
        start()
        val initial = rows()
        click("카드 이미지 앨범에 저장")
        val png = newOutput(initial, "image/png")
        val bitmap = resolver.openInputStream(png)!!.use(BitmapFactory::decodeStream)!!
        assertEquals(720, bitmap.width); bitmap.recycle()
        waitText("아가톡 앨범에 카드 이미지를 저장했어요.")
        val beforeVideo = rows()
        click("영상 카드 앨범에 저장")
        val mp4 = newOutput(beforeVideo, "video/mp4")
        val retriever = MediaMetadataRetriever()
        try { retriever.setDataSource(compose.activity, mp4); assertEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)) }
        finally { retriever.release() }
        waitText("아가톡 앨범에 영상 카드를 저장했어요.")
        click("장면과 함께 저장하기")
        compose.waitUntil(45_000) { runBlocking { store.snapshot().mediaIds.size == 1 } }
        assertTrue(resolver.openInputStream(clip.uri)!!.use { it.read() } >= 0)
    }

    /** The analyzer alone is substituted: microphone, AAC, renderer, gallery and SQLite are real. */
    @Test fun recordedVoiceResultExportsSoundAndCaptionWithoutSubscription() {
        start(preview = false, recordVoice = true)
        val initial = rows()
        click("소리+아가말 카드 앨범에 저장")
        val mp4 = newOutput(initial, "video/mp4")
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(compose.activity, mp4)
            assertEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            assertEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertTrue(kotlin.math.abs(duration - clip.durationMs) < 150)
            val frame = checkNotNull(retriever.getFrameAtTime(500_000))
            try { assertEquals(720, frame.width); assertTrue(frame.height >= 960) } finally { frame.recycle() }
        } finally { retriever.release() }
        waitText("아가톡 앨범에 소리와 아가말 카드를 저장했어요.")
        click("소리와 함께 기록하기")
        compose.waitUntil(45_000) { runBlocking { store.snapshot().mediaIds.size == 1 } }
        val record = runBlocking { store.snapshot() }.observations.single()
        assertEquals("AUDIO", record.basis)
        val saved = checkNotNull(RecordMedia.file(compose.activity, record.id))
        val stored = MediaMetadataRetriever()
        try {
            stored.setDataSource(saved.path)
            assertEquals("yes", stored.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            assertEquals("yes", stored.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
        } finally { stored.release() }
    }

    @Test fun withheldAnalysisCanSaveSelectedVideoWithoutInventingRecord() {
        problem = "아기 소리 단서가 부족해요."
        start(preview = false)
        compose.onNodeWithText("선택 구간 영상만 저장").assertExists()
        val beforeSave = rows()
        val button = compose.onNodeWithText("선택 구간 영상만 저장").performScrollTo()
        val action = button.fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        compose.runOnIdle { action(); action() }
        val output = newOutput(beforeSave, "video/mp4")
        waitText("선택한 구간을 자막 없이 아가톡 앨범에 저장했어요.")
        assertTrue(runBlocking { store.snapshot().observations.isEmpty() })
        assertPlainVideo(output, minOf(10_000, clip.durationMs))
    }

    @Test fun failedAnalysisStillSavesVideoWithoutSentence() {
        failAnalysis = true
        start()
        val beforeSave = rows()
        click("선택 구간 영상만 저장")
        val output = newOutput(beforeSave, "video/mp4")
        waitText("선택한 구간을 자막 없이 아가톡 앨범에 저장했어요.")
        assertTrue(runBlocking { store.snapshot().observations.isEmpty() })
        assertPlainVideo(output, minOf(10_000, clip.durationMs))
    }

    private fun assertPlainVideo(uri: Uri, duration: Long) {
        val reader = MediaMetadataRetriever()
        try {
            reader.setDataSource(compose.activity, uri)
            assertTrue(kotlin.math.abs(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong() - duration) < 180)
            assertEquals("yes", reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            val frame = reader.getFrameAtTime(100_000, MediaMetadataRetriever.OPTION_CLOSEST)!!
            // Synthetic source occupies the full frame; a 720x960 caption card would fail.
            assertEquals(clip.aspectRatio, frame.width.toFloat() / frame.height, .02f)
            frame.recycle()
        } finally { reader.release() }
    }

    @Test fun sceneSaveKeepsResultAndProvidesPlayback() {
        start()
        click("장면과 함께 저장하기")
        compose.waitUntil(45_000) { runBlocking { store.snapshot().mediaIds.size == 1 } }
        compose.waitForIdle()
        compose.onNodeWithTag("page-capture").assertExists()
        compose.onNodeWithTag("baby-phrase").assertExists()
        click("저장한 장면 보기")
        waitText("그때의 아기말")
        compose.onNodeWithTag("clip-video-preview").assertExists()
        assertTrue(resolver.openInputStream(clip.uri)!!.use { it.read() } >= 0)
    }

    @Test fun hdrImportedVideoHasScenePoster() {
        val row = Observation(System.currentTimeMillis(), "옹알이", "0:00–0:02", "", "옹알옹알, 내 목소리를 들어봐요.")
        val poster = MomentCardRenderer.frame(compose.activity, row, clip, ClipRange(0, 2_000))
        try {
            assertEquals(720, poster.width)
            // The supplied synthetic HDR scene has visible colour; the scene area must not be blank.
            val colours = (100..600 step 50).flatMap { x -> (100..540 step 40).map { y -> poster.getPixel(x, y) } }.toSet()
            assertTrue("HDR source scene must appear above the caption", colours.size > 3)
        } finally { poster.recycle() }
    }

    @Test fun hdrImportedVideoResultSavesThroughActualUi() = galleryVideoResultSavesPngMp4AndSceneRecord()

    @Test fun hdrImportedVideoCanRenderCaption() = runBlocking {
        val row = Observation(System.currentTimeMillis(), "옹알이", "0:00–0:02", "", "옹알옹알, 내 목소리를 들어봐요.")
        val output = TranslatedMedia.render(compose.activity, clip, ClipRange(0, 2_000), row)
        try { assertTrue(output.length() > 0) } finally { output.delete() }
    }

    @Test fun nextImportedVideoReplacesPriorResultAndCaregiverDraftImmediately() {
        start()
        click("기록에 메모·수정 남기기")
        compose.onNodeWithTag("draft-phrase-editor").performScrollTo().performTextReplacement("이전 영상에만 쓴 문장")
        val next = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$id-second.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/AgattokQA/")
        })!!
        InstrumentationRegistry.getInstrumentation().context.assets.open("media-quadrants.mp4").use { input ->
            resolver.openOutputStream(next)!!.use { input.copyTo(it) }
        }
        pickedUri = next
        click("다른 영상·녹음 고르기")
        waitText("영상 + 소리로 번역하기")
        compose.onNodeWithTag("baby-phrase").assertDoesNotExist()
        click("영상 + 소리로 번역하기")
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("baby-phrase").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("baby-phrase").assertTextEquals("새 영상의 웃는 소리예요.")
        compose.onNodeWithTag("draft-phrase-editor").assertDoesNotExist()
        assertEquals(listOf(clip.uri, next), analyzedUris.toList())
    }
}
