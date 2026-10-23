package com.moa.babylanguage

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.net.toUri
import android.os.Bundle
import java.util.UUID
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.FavoriteBorder
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

internal val Cream = Color(0xFFFFF9F0)
internal val Ink = Color(0xFF24322E)
internal val Muted = Color(0xFF65716B)
internal val Sage = Color(0xFFA8C6A0)
internal val Apricot = Color(0xFFF7B893)
internal val PaleSage = Color(0xFFEDF3E9)
internal val PaleApricot = Color(0xFFFFEEE1)
internal val Line = Color(0xFFE8E3D9)
internal val White = Color(0xFFFFFFFF)

internal fun captureAllowanceText(full: Boolean): String {
    val limits = CaptureLimits.forAccess(full)
    return "${limits.minutes}분 촬영 · ${limits.analysisSeconds}초 분석"
}

private enum class Page { HOME, CAPTURE, TIMELINE, GUIDE, PRIVACY, TERMS, ALBUM, PAYWALL, ABOUT }

class MainActivity : ComponentActivity() {
    private var stopCaptureOnBackground: (() -> Unit)? = null
    private var billing: BillingManager? = null
    private var observationStore: ObservationStore? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        CaptureFiles.cleanup(this)
        val store = ObservationStore(this).also { observationStore = it }
        val billingManager = BillingManager(this).also { billing = it }
        val accessBilling = PlayAccessBilling(billingManager)
        setContent {
            var showTitle by rememberSaveable { mutableStateOf(savedInstanceState == null) }
            if (showTitle) AppTitleScreen { showTitle = false }
            else JigeumApp(this, store, accessBilling,
                testDistribution = BuildConfig.FULL_ACCESS_PREVIEW || BuildConfig.FULL_ACCESS_CLOSED_TEST,
                registerBackgroundStop = { stopCaptureOnBackground = it })
        }
    }
    override fun onResume() { super.onResume(); billing?.refresh() }
    override fun onDestroy() {
        billing?.close()
        observationStore?.let { store -> kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { store.close() } }
        observationStore = null
        super.onDestroy()
    }
    override fun onStop() { stopCaptureOnBackground?.invoke(); super.onStop() }
}

@Composable
internal fun JigeumApp(
    activity: Activity,
    store: ObservationStore,
    billingManager: AccessBilling,
    registerBackgroundStop: (() -> Unit) -> Unit = {},
    captureAnalyzer: ClipAnalyzer = DeviceClipAnalyzer,
    captureSeed: LocalClip? = null,
    artifactExporter: ArtifactExporter = DeviceArtifactExporter,
    recordImporter: RecordImporter = DeviceRecordImporter,
    testDistribution: Boolean = false
) {
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    var observations by remember { mutableStateOf(emptyList<Observation>()) }
    var mediaIds by remember { mutableStateOf(emptySet<Long>()) }
    var viewingMediaId by rememberSaveable { mutableLongStateOf(-1L) }
    var captureSaveReceipt by remember { mutableStateOf<CaptureSaveReceipt?>(null) }
    var captureGalleryReceipt by remember { mutableStateOf<CaptureGalleryReceipt?>(null) }
    var ageBand by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var retryLoad by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var privacyReturn by rememberSaveable { mutableStateOf(Page.GUIDE) }
    var aboutReturn by rememberSaveable { mutableStateOf(Page.HOME) }
    var paywallReturn by rememberSaveable { mutableStateOf(Page.HOME) }
    var requestedFeature by rememberSaveable { mutableStateOf<AppFeature?>(null) }
    var paywallNotice by rememberSaveable { mutableStateOf<String?>(null) }
    val access = remember(billingManager, testDistribution) { AccessPolicy(billingManager::hasCurrentEntitlement, testDistribution) }
    DisposableEffect(access) { onDispose { access.invalidateMedia() } }
    val screenState = rememberSaveableStateHolder()
    val billingState by billingManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snacks = remember { SnackbarHostState() }
    val feedback = remember(snacks, scope) { LatestFeedback(snacks, scope) }
    fun notify(message: String) { feedback.show(message) }
    fun showPass(feature: AppFeature?, origin: Page = page, notice: String? = null) {
        access.invalidateMedia()
        requestedFeature = feature
        paywallReturn = when (origin) {
            Page.PAYWALL, Page.PRIVACY, Page.TERMS -> Page.HOME
            else -> origin
        }
        paywallNotice = notice
        page = Page.PAYWALL
        billingManager.refresh()
    }
    fun closePass() {
        page = paywallReturn
        requestedFeature = null
        paywallNotice = null
    }
    fun beginMutation(): Boolean {
        if (!loaded) return false
        if (busy) {
            notify("기록을 처리하고 있어요. 끝난 뒤 다시 시도해 주세요.")
            return false
        }
        busy = true
        return true
    }
    fun update(action: suspend () -> Unit, after: () -> Unit = {}, invalidateExports: Boolean = false) {
        if (!beginMutation()) return
        if (invalidateExports) MemoryArtifacts.invalidatePendingExports()
        scope.launch {
            try {
                action()
                val snapshot = store.snapshot()
                observations = snapshot.observations; ageBand = snapshot.ageBand; mediaIds = snapshot.mediaIds
                after()
                snapshot.warning?.let(::notify)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                notify(if (e is StoreException)
                    e.message ?: "저장하지 못했어요. 다시 시도해 주세요."
                    else "저장하지 못했어요. 저장 공간을 확인하고 다시 시도해 주세요.")
            } finally { busy = false }
        }
    }
    LaunchedEffect(retryLoad) {
        loadError = null
        try {
            store.cleanupMedia()
            val snapshot = store.snapshot()
            observations = snapshot.observations; ageBand = snapshot.ageBand; mediaIds = snapshot.mediaIds
            snapshot.warning?.let(::notify)
            withContext(Dispatchers.IO) { MemoryArtifacts.cleanup(activity); TranslatedMedia.cleanup(activity) }
            if (!GalleryMedia.cleanupInterrupted(activity)) notify("이전 앨범 저장을 정리하지 못했어요. 기존 기록은 볼 수 있어요. 다음 실행에서 다시 확인할게요.")
            withContext(Dispatchers.Main.immediate) { loaded = true }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            loadError = "기록을 열지 못했어요. 저장 공간을 확인하고 다시 시도해 주세요."
        }
    }
    fun goBack() {
        when (page) {
            Page.PRIVACY, Page.TERMS -> page = privacyReturn
            Page.ABOUT -> page = aboutReturn
            Page.PAYWALL -> closePass()
            else -> page = Page.HOME
        }
    }
    BackHandler(enabled = page != Page.HOME) { if (!busy) goBack() }
    val destinations = page in listOf(Page.HOME, Page.TIMELINE, Page.GUIDE, Page.ALBUM)
    MaterialTheme(colorScheme = lightColorScheme(primary = Ink, onPrimary = White,
        surface = Cream, onSurface = Ink, secondaryContainer = PaleSage, onSecondaryContainer = Ink,
        background = Cream, onBackground = Ink, outline = Muted)) {
        observations.find { it.id == viewingMediaId }?.let { record ->
            RecordMediaViewer(record, onClose = { viewingMediaId = -1L }, onMessage = ::notify)
        }
        Surface(color = Cream, modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
                val wide = maxWidth >= 840.dp
                Row(Modifier.fillMaxSize()) {
                    if (wide && destinations) AppNavigation(page, rail = true) { page = it }
                    Column(Modifier.weight(1f).fillMaxSize().imePadding()) {
                        if (testDistribution) Text("테스트 버전 · 전체 기능 열림", color = Ink, fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().background(PaleSage)
                                .padding(horizontal = 24.dp, vertical = 6.dp).testTag("test-distribution"))
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Ink)
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.widthIn(max = 720.dp).fillMaxSize().clipToBounds().testTag(if (loaded) "page-${page.name.lowercase()}" else "page-loading")) {
                                if (!loaded) {
                                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                                        Text(loadError ?: "기록을 불러오고 있어요", color = Ink)
                                        if (loadError != null) PrimaryButton("다시 열기", { retryLoad++ })
                                    }
                                } else when (page) {
                                    Page.HOME -> HomeScreen(observations,
                                        testDistribution = testDistribution, premium = billingState.isPremium,
                                        onRecord = { captureGalleryReceipt = null; page = Page.CAPTURE }, onTimeline = { page = Page.TIMELINE },
                                        onCommunity = if (CommunitySharing.available) ({ CommunitySharing.open(activity) }) else null,
                                        onPremium = { showPass(null, Page.HOME) },
                                        onAbout = { aboutReturn = Page.HOME; page = Page.ABOUT })
                                    Page.CAPTURE -> CaptureFlow(
                                        authorizeVisual = { access.canUse(AppFeature.VISUAL_ANALYSIS) },
                                        onBack = { page = Page.HOME }, saving = busy,
                                        analyzer = captureAnalyzer, initialClip = captureSeed,
                                        testDistribution = testDistribution,
                                        saveReceipt = captureSaveReceipt,
                                        galleryReceipt = captureGalleryReceipt,
                                        onOpenGallery = { receipt ->
                                            if (!busy && !sharing) scope.launch {
                                                try { GalleryMedia.open(activity, receipt.uri, receipt.video) }
                                                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                                catch (_: Exception) { notify("저장한 파일을 열지 못했어요. 갤러리의 아가톡 앨범에서 확인해 주세요.") }
                                            }
                                        },
                                        onViewSaved = { if (!busy && !sharing) viewingMediaId = it },
                                        onSaveVideo = { clip, range ->
                                            if (!busy && !sharing) {
                                                val generation = MemoryArtifacts.currentGeneration()
                                                update({
                                                    var rendered: java.io.File? = null
                                                    try {
                                                        rendered = TranslatedMedia.selectedVideo(activity, clip, range)
                                                        GalleryMedia.video(activity, rendered, generation)
                                                    } finally { withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { rendered?.delete() } }
                                                }, { notify("선택한 구간을 자막 없이 아가톡 앨범에 저장했어요.") })
                                            }
                                        },
                                        onCommunity = if (CommunitySharing.available) ({ result, phrase, clip, range ->
                                            if (!busy && !sharing) update({
                                                val record = Observation(System.currentTimeMillis(), result.sound, result.range, "", phrase,
                                                    basis = ExpressionBasis.forPhrase(result.basis, result.phrase, phrase))
                                                val prepared = CommunitySharing.prepare(activity, record, clip, range)
                                                try { CommunitySharing.open(activity, prepared) }
                                                catch (error: Exception) { withContext(Dispatchers.IO) { prepared.delete() }; throw error }
                                            })
                                        }) else null,
                                        onGallery = { result, phrase, clip, range, video ->
                                            if (!busy && !sharing) {
                                                captureGalleryReceipt = null
                                                var savedUri: Uri? = null
                                                val generation = MemoryArtifacts.currentGeneration()
                                                update({
                                                    val record = Observation(System.currentTimeMillis(), result.sound, result.range, "", phrase,
                                                        basis = ExpressionBasis.forPhrase(result.basis, result.phrase, phrase))
                                                    if (video) {
                                                        check(clip != null && range != null) { "원래 장면이 없어 영상으로 저장할 수 없어요. 카드 이미지로 남길 수 있어요." }
                                                        var rendered: java.io.File? = null
                                                        try {
                                                            rendered = TranslatedMedia.render(activity, clip, range, record)
                                                            savedUri = GalleryMedia.video(activity, rendered, generation)
                                                        } finally { withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { rendered?.delete() } }
                                                    } else {
                                                        var bitmap: android.graphics.Bitmap? = null
                                                        try {
                                                            withContext(Dispatchers.IO) { bitmap = MomentCardRenderer.frame(activity, record, clip, range) }
                                                            savedUri = GalleryMedia.image(activity, checkNotNull(bitmap), generation)
                                                        } finally { bitmap?.recycle() }
                                                    }
                                                }, {
                                                    captureGalleryReceipt = savedUri?.let { CaptureGalleryReceipt(it, clip?.uri, range, phrase, video) }
                                                    notify(if (video && clip?.hasVideo == false) "아가톡 앨범에 소리와 아가말 카드를 저장했어요."
                                                    else if (video) "아가톡 앨범에 영상 카드를 저장했어요." else "아가톡 앨범에 카드 이미지를 저장했어요.") })
                                            }
                                        },
                                        onSaveWithMedia = { result, phrase, note, clip, range ->
                                            var receipt: CaptureSaveReceipt? = null
                                            if (!busy) update({
                                                val usedIds = observations.mapTo(HashSet()) { it.id }
                                                var newId = System.currentTimeMillis()
                                                while (newId in usedIds) newId++
                                                val record = Observation(newId, result.sound, result.range, note, phrase,
                                                    if (result.visual) "VIDEO_FILE" else "AUDIO", result.sound,
                                                    ExpressionBasis.forPhrase(result.basis, result.phrase, phrase))
                                                var rendered: java.io.File? = null
                                                try {
                                                    rendered = TranslatedMedia.render(activity, clip, range, record)
                                                    store.save(record, rendered)
                                                    receipt = CaptureSaveReceipt(newId, phrase, note, true)
                                                } finally {
                                                    withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { rendered?.delete() }
                                                }
                                            }, { captureSaveReceipt = receipt; notify(if (clip.hasVideo) "장면과 아기말을 앱 기록에 저장했어요." else "소리와 아기말을 앱 기록에 저장했어요.") })
                                        },
                                        onSave = { result, phrase, note ->
                                            var receipt: CaptureSaveReceipt? = null
                                            if (!busy) update({
                                                val usedIds = observations.mapTo(HashSet()) { it.id }
                                                var newId = System.currentTimeMillis()
                                                while (newId in usedIds) newId++
                                                store.save(Observation(newId, result.sound, result.range, note, phrase,
                                                    if (result.visual) "VIDEO_FILE" else "AUDIO", result.sound,
                                                    ExpressionBasis.forPhrase(result.basis, result.phrase, phrase)))
                                                receipt = CaptureSaveReceipt(newId, phrase, note, false)
                                            }, { captureSaveReceipt = receipt; notify("아기말을 앱 기록에 저장했어요.") })
                                        },
                                        passContent = { close ->
                                            var policy by remember { mutableStateOf("") }
                                            when (policy) {
                                                "privacy" -> PrivacyPolicyScreen { policy = "" }
                                                "terms" -> ServiceTermsScreen { policy = "" }
                                                else -> PremiumScreen(billingState, { billingManager.purchase(activity, it) },
                                                    billingManager::restore, billingManager::refresh, AppFeature.VISUAL_ANALYSIS, null,
                                                    onContinue = close, freeContinuationLabel = "같은 구간 소리로 계속하기", onFree = close,
                                                    onBack = close, onPrivacy = { policy = "privacy" }, onTerms = { policy = "terms" },
                                                    onReviewActivate = billingManager::activateReviewAccess,
                                                    onReviewDeactivate = billingManager::deactivateReviewAccess,
                                                    testDistribution = testDistribution)
                                            }
                                        })
                                    Page.TIMELINE -> TimelineScreen(observations, mediaIds,
                                        onMedia = { if (!busy && !sharing) viewingMediaId = it.id },
                                        onDelete = { id ->
                                            update({ store.delete(id)
                                            try { MemoryArtifacts.clear(activity) }
                                            catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; notify("기록은 지웠어요. 임시 공유 파일을 정리하지 못했어요.") }
                                        }, invalidateExports = true) },
                                        onAlbum = { page = Page.ALBUM },
                                        onShare = { record -> if (!sharing && !busy) {
                                            val generation = MemoryArtifacts.currentGeneration()
                                            sharing = true
                                            scope.launch {
                                                try {
                                                    // An old UI callback cannot mint a new export generation for a deleted record.
                                                    val current = store.snapshot().observations.firstOrNull { it.id == record.id }
                                                    check(current != null && !busy && current == record && observations.any { it == record }) {
                                                        "기록이 변경되어 내보내기를 중단했어요. 다시 선택해 주세요."
                                                    }
                                                    val file = artifactExporter.card(activity, current, generation)
                                                    try {
                                                        coroutineContext.ensureActive()
                                                        artifactExporter.share(activity, file, "image/png", generation)
                                                    } finally { MemoryArtifacts.discardUnshared(activity, file) }
                                                }
                                                catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; notify(if (e is IllegalStateException) e.message ?: "카드를 만들지 못했어요." else "카드를 만들지 못했어요. 저장 공간을 확인해 주세요.") }
                                                finally { sharing = false }
                                            }
                                        } })
                                    Page.GUIDE -> GuideScreen(testDistribution = testDistribution,
                                        premium = billingState.isPremium, recordCount = observations.size,
                                        onPremium = { showPass(null, Page.GUIDE) },
                                        onAbout = { aboutReturn = Page.GUIDE; page = Page.ABOUT },
                                        onTerms = { privacyReturn = Page.GUIDE; page = Page.TERMS },
                                        onPrivacy = { privacyReturn = Page.GUIDE; page = Page.PRIVACY },
                                        onDeleteAll = {
                                            update({ store.deleteAll()
                                            try { MemoryArtifacts.clear(activity) }
                                            catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; notify("기록은 지웠어요. 임시 공유 파일을 정리하지 못했어요.") }
                                        }, {
                                            notify("기록을 지웠어요.")
                                        }, invalidateExports = true) },
                                        dataTools = { DataTransferPanel(store, observations, ageBand,
                                            onImported = { snapshot -> observations = snapshot.observations; ageBand = snapshot.ageBand; mediaIds = snapshot.mediaIds },
                                            onMessage = ::notify, operationScope = scope, importer = recordImporter,
                                            beginMutation = ::beginMutation, endMutation = { busy = false }) },
                                        onRestore = { showPass(null, Page.GUIDE); billingManager.restore() })
                                    Page.PRIVACY -> PrivacyPolicyScreen { goBack() }
                                    Page.TERMS -> ServiceTermsScreen { goBack() }
                                    Page.ABOUT -> AnalysisAboutScreen(onBack = ::goBack)
                                    Page.ALBUM -> screenState.SaveableStateProvider("album") {
                                        AlbumScreen(observations, testDistribution || billingState.isPremium,
                                            onPremium = { showPass(AppFeature.PDF, Page.ALBUM) }, onMessage = ::notify,
                                            operationScope = scope, exporting = sharing || busy, onExportingChange = { sharing = it },
                                            artifactExporter = artifactExporter,
                                            authorizeExport = { selectedRecords ->
                                                if (busy || sharing) false
                                                else if (selectedRecords != observations) {
                                                    notify("기록이 변경되어 내보내기를 중단했어요. 다시 선택해 주세요."); false
                                                }
                                                else if (access.canUse(AppFeature.PDF)) true
                                                else { showPass(AppFeature.PDF, Page.ALBUM, "PDF를 만들기 전에 이용권을 다시 확인해 주세요."); false }
                                            })
                                    }
                                    Page.PAYWALL -> PremiumScreen(billingState,
                                        onPurchase = { billingManager.purchase(activity, it) },
                                        onRestore = billingManager::restore, onRetry = billingManager::refresh,
                                        requestedFeature = requestedFeature, notice = paywallNotice,
                                        onContinue = {
                                            val feature = requestedFeature
                                            if (feature == null) closePass()
                                            else if (feature == AppFeature.PDF) page = Page.ALBUM else page = Page.CAPTURE
                                        },
                                        freeContinuationLabel = "무료로 촬영·소리 번역하기",
                                        onFree = { requestedFeature = null; paywallNotice = null; page = Page.CAPTURE },
                                        onBack = ::closePass,
                                        onPrivacy = { privacyReturn = Page.PAYWALL; page = Page.PRIVACY },
                                        onTerms = { privacyReturn = Page.PAYWALL; page = Page.TERMS },
                                        onReviewActivate = billingManager::activateReviewAccess,
                                        onReviewDeactivate = billingManager::deactivateReviewAccess,
                                        testDistribution = testDistribution)
                                }
                            }
                        }
                        SnackbarHost(snacks)
                        if (!wide && destinations) AppNavigation(page) { page = it }
                        if (wide || !destinations) Spacer(Modifier.navigationBarsPadding())
                    }
                }
            }
        }
    }
}

@Composable
internal fun TopBar(title: String, eyebrow: String? = null) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp)) {
        Text(title, color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)
    }
}

@Composable
private fun AppNavigation(current: Page, rail: Boolean = false, onSelect: (Page) -> Unit) {
    val tabs = listOf(Triple(Page.HOME, Icons.Outlined.Home, "오늘"),
        Triple(Page.TIMELINE, Icons.Outlined.History, "기록"),
        Triple(Page.ALBUM, Icons.AutoMirrored.Outlined.MenuBook, "아기말북"),
        Triple(Page.GUIDE, Icons.Outlined.FavoriteBorder, "돌봄"))
    if (rail) NavigationRail(containerColor = White) {
        tabs.forEach { (page, icon, label) -> NavigationRailItem(selected = current == page,
            onClick = { onSelect(page) }, icon = { Icon(icon, null) }, label = { Text(label) }) }
    } else NavigationBar(containerColor = White, tonalElevation = 0.dp) {
        tabs.forEach { (page, icon, label) -> NavigationBarItem(selected = current == page,
            onClick = { onSelect(page) }, icon = { Icon(icon, null) }, label = { Text(label) },
            colors = NavigationBarItemDefaults.colors(indicatorColor = PaleSage, selectedIconColor = Ink,
                selectedTextColor = Ink, unselectedIconColor = Muted, unselectedTextColor = Muted)) }
    }
}

@Composable
internal fun HomeScreen(
    observations: List<Observation>, onRecord: () -> Unit, onTimeline: () -> Unit,
    onPremium: () -> Unit, onAbout: () -> Unit, testDistribution: Boolean, premium: Boolean,
    onCommunity: (() -> Unit)? = null
) {
    Column(Modifier.fillMaxSize()) {
      LazyColumn(
        modifier = Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 20.dp)
      ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("아가톡", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onPremium, modifier = Modifier.heightIn(min = 48.dp).testTag("home-pass"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink)) {
                    Text(if (testDistribution) "테스트 이용 안내" else if (premium) "내 이용권" else "전체 이용권")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "지금 내 기분이 그래\n아기의 소리를 듣고, 한마디로 남겨요.",
                modifier = Modifier.padding(horizontal = 24.dp), color = Muted,
                fontSize = 15.sp, lineHeight = 23.sp
            )
            Spacer(Modifier.height(18.dp))
            HeroRecordCard()
            if (onCommunity != null) OutlinedButton(onClick = onCommunity,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).heightIn(min = 48.dp).testTag("home-community")) {
                Text("우리 아이 자랑 · 영상카드 함께 나누기", color = Ink)
            }
            TextButton(onClick = onAbout, modifier = Modifier.padding(horizontal = 12.dp).heightIn(min = 48.dp).testTag("home-about")) {
                Text("어떤 근거로 아기말을 만들까요?", color = Ink, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("최근 번역", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("전체 보기  →", color = Muted, fontSize = 13.sp, modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onTimeline).padding(12.dp))
            }
            Spacer(Modifier.height(10.dp))
            if (observations.isEmpty()) {
                EmptyObservations()
            } else {
                observations.take(2).forEach { ObservationCard(it, onDelete = null) }
            }
            Spacer(Modifier.height(22.dp))
            PrivacyNote()
        }
      }
      // The main action stays in thumb reach, independent of recent-record length.
      Surface(color = Cream, modifier = Modifier.fillMaxWidth()) {
          Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
              PrimaryButton("아기말 번역하기", onRecord)
              Text((if (testDistribution) "전체 기능 테스트" else if (premium) "전체 이용권" else "무료") +
                  " · " + captureAllowanceText(testDistribution || premium), color = Muted, fontSize = 12.sp,
                  textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("home-access-summary"))
          }
      }
    }
}

@Composable
private fun HeroRecordCard() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        .clip(RoundedCornerShape(28.dp)).background(PaleSage).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("아기의 소리를\n함께 들어봐요", color = Ink, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("촬영 → 구간 고르기 → 아기말", color = Muted, fontSize = 13.sp)
            }
            Image(painterResource(R.drawable.hero_touch), null, contentScale = ContentScale.Fit, modifier = Modifier.size(80.dp))
        }
    }
}

@Composable
private fun EmptyObservations() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(24.dp)).background(White).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("✦", color = Apricot, fontSize = 32.sp)
        Spacer(Modifier.height(7.dp))
        Text("아직 남긴 아기말이 없어요", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp))
        Text("첫 소리와 그 순간을 문장으로 만나보세요.", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun PrivacyNote() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(18.dp)).background(PaleApricot).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("♡", color = Ink, fontSize = 27.sp)
        Spacer(Modifier.width(14.dp))
        Text("실제 소리에서 찾은 단서를 아이의 말처럼 풀어드려요.\n울음의 이유나 감정, 건강 상태를 단정하지 않아요.", color = Ink, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
internal fun ChoiceGrid(choices: List<String>, selected: String, onSelect: (String) -> Unit) {
    choices.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { choice ->
                val active = choice == selected
                Box(
                    modifier = Modifier.weight(1f).heightIn(min = 54.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(if (active) Sage else White)
                        .border(1.dp, if (active) Sage else Line, RoundedCornerShape(15.dp))
                        .selectable(selected = active, role = Role.RadioButton) { onSelect(choice) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(choice, color = Ink, fontSize = 15.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
internal fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 57.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = White, disabledContainerColor = Line, disabledContentColor = Muted)
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun SecondaryButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(16.dp))
            .border(1.dp, Line, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun EvidenceCard(title: String, body: String) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(17.dp)).background(White).padding(16.dp)
    ) {
        Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun TimelineScreen(observations: List<Observation>, mediaIds: Set<Long>, onMedia: (Observation) -> Unit,
                           onDelete: (Long) -> Unit, onAlbum: () -> Unit, onShare: (Observation) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            TopBar("우리가 나눈 아기말", "기록")
            Text("함께 만든 문장을 한곳에 모았어요.", color = Muted, fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(23.dp))
        }
        item { WeeklySummary(observations, onAlbum) }
        if (observations.isEmpty()) {
            item { EmptyObservations() }
        } else {
            items(observations, key = { it.id }) { observation ->
                ObservationCard(observation, onDelete = { onDelete(observation.id) }, onShare = { onShare(observation) },
                    onMedia = if (observation.id in mediaIds) ({ onMedia(observation) }) else null)
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            PrivacyNote()
        }
    }
}

@Composable
private fun ObservationCard(observation: Observation, onDelete: (() -> Unit)?, onShare: (() -> Unit)? = null, onMedia: (() -> Unit)? = null) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(21.dp)).background(White).padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(PaleSage), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Mic, null, Modifier.size(23.dp), tint = Ink)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(observation.sound, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(formatDate(observation.id), color = Muted, fontSize = 12.sp)
            }
            Text(observation.context, color = Ink, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(PaleApricot).padding(horizontal = 10.dp, vertical = 7.dp))
        }
        if (observation.phrase.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text("“${observation.phrase}”", color = Ink, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
            Text(ExpressionBasis.label(observation.basis), color = Muted, fontSize = 12.sp, lineHeight = 19.sp,
                modifier = Modifier.padding(top = 6.dp))
        }
        if (observation.note.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(observation.note, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
        }
        if (onMedia != null) OutlinedButton(onClick = onMedia, modifier = Modifier.fillMaxWidth()) { Text("장면 보기 · 내려받기") }
        else if (onDelete != null) Text("문장만 보관된 기록이에요.", color = Muted, fontSize = 12.sp)
        if (onShare != null) TextButton(onClick = onShare) { Text("문장 카드 공유", color = Ink) }
        if (onDelete != null) {
            Spacer(Modifier.height(5.dp))
            Text(
                if (confirmDelete) "다시 누르면 기록과 보관한 장면을 삭제해요" else "기록 삭제",
                color = Muted, fontSize = 12.sp,
                modifier = Modifier.heightIn(min = 48.dp).clickable {
                    if (confirmDelete) onDelete() else confirmDelete = true
                }.padding(vertical = 5.dp)
            )
        }
    }
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("M월 d일 EEEE · a h:mm", Locale.KOREAN).format(Date(timestamp))

@Composable
private fun GuideScreen(onDeleteAll: () -> Unit, onPrivacy: () -> Unit, dataTools: @Composable () -> Unit,
                        onRestore: () -> Unit, onPremium: () -> Unit, onAbout: () -> Unit, onTerms: () -> Unit,
                        premium: Boolean, recordCount: Int, testDistribution: Boolean = false) {
    var confirmDelete by remember { mutableStateOf(false) }
    var tips by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    var supportError by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            TopBar("돌봄 · 내 정보")
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(if (testDistribution) "전체 기능 테스트 중" else if (premium) "전체 이용권 사용 중" else "무료로 사용 중",
                    color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(captureAllowanceText(testDistribution || premium) +
                    if (testDistribution || premium) " · 표정 관찰 · PDF" else " · 결과 저장", color = Muted, fontSize = 14.sp,
                    lineHeight = 22.sp, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(16.dp))
                PrimaryButton(if (testDistribution) "테스트 이용 안내" else "전체 이용권 · 결제", onPremium)
                if (!testDistribution) TextButton(onClick = onRestore, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("구매 복원", color = Ink)
                }
                Text(if (testDistribution) "이 설치본만 전체 기능이 열려 있어요. Play 테스트 참여 계정과 구매 권한은 별개예요."
                    else "앱 회원가입은 없어요. 결제·구매 복원은 Google Play 계정 기준이에요. 비공개 테스터 등록만으로 유료 기능이 열리지는 않아요.",
                    color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(24.dp))
                Text("내 기록 관리", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("이 기기에 남긴 순간 ${recordCount}개", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(8.dp))
            dataTools()
            Text("기기 안에 보관해요", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(8.dp))
            Text("번역 문장과 선택해 보관한 장면은 이 기기에 남아요. 저장한 장면은 기록에서 재생·내려받을 수 있어요. JSON·CSV에는 문장과 메모만 담겨요.",
                modifier = Modifier.padding(horizontal = 24.dp), color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(10.dp))
            Text("개인정보처리방침 전문 보기  →", color = Ink, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(15.dp)).background(PaleSage)
                    .clickable(onClick = onPrivacy).padding(17.dp))
            Spacer(Modifier.height(10.dp))
            Text(
                if (confirmDelete) "다시 누르면 모든 기록을 삭제해요" else "모든 기록 삭제",
                color = Ink, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(15.dp)).background(PaleApricot)
                    .clickable {
                        if (confirmDelete) {
                            onDeleteAll()
                            confirmDelete = false
                        } else confirmDelete = true
                    }.padding(17.dp)
            )
            Spacer(Modifier.height(24.dp))
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text("도움말과 문의", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onAbout, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("분석 원리 · 참고자료 보기", color = Ink)
                }
                TextButton(onClick = onTerms, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("서비스 이용 조건", color = Ink) }
                TextButton(onClick = {
                    supportError = null
                    runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, "mailto:bsfire1237@gmail.com".toUri())) }
                        .onFailure { supportError = "메일 앱을 열지 못했어요. bsfire1237@gmail.com으로 문의해 주세요." }
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("문의 · bsfire1237@gmail.com", color = Ink) }
                supportError?.let { Text(it, color = Muted, fontSize = 13.sp, lineHeight = 20.sp) }
                Text("운영자 최용우 · ${BuildConfig.VERSION_NAME}", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = { tips = !tips }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (tips) "아기와 대화하는 팁 접기" else "아기와 대화하는 팁 보기", color = Ink)
                }
            }
            if (tips) {
                GuideCard("01", "잠깐 멈추고 살펴보기", "표정, 몸짓, 주변 소리와 방금 있었던 일을 함께 살펴보세요.", PaleSage)
                GuideCard("02", "짧게 말로 답하기", "아기가 낸 소리를 따라 하거나 지금 보이는 것을 말해 주세요.", PaleApricot)
                GuideCard("03", "다시 반응할 시간 주기", "서두르지 않고 잠시 기다리면, 아기가 다시 소리나 몸짓으로 답할 수 있어요.", PaleSage)
                Text("걱정되는 증상이 있거나 평소와 다른 상태가 계속되면 소아청소년과 전문가에게 상담하세요.",
                    modifier = Modifier.padding(horizontal = 24.dp), color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val sections = listOf(
        "1. 서비스와 처리 범위" to
            "‘아가톡 - 지금 내 기분이 그래’는 6~18개월 아기의 보호자가 소리와 장면을 살펴보고 표현 예시를 만드는 앱입니다. " +
            "소리 유형, 선택 구간, 메모, 문장, 모델 단서와 이전 버전의 기록은 이 기기에만 저장합니다. " +
            "개인 분석·기록에는 외부 DB나 앱 회원가입이 없습니다. 우리 아이 자랑 게시판은 보호자가 직접 공개한 자료만 별도로 저장합니다.",
        "2. 마이크·카메라·선택한 영상" to
            "보호자가 시작할 때만 무료는 최대 1분, 전체 이용권은 최대 2분 녹화·녹음합니다. 다시 듣기와 구간 선택을 위해 앱의 비공개 임시 폴더에 보관하며 " +
            "화면 종료·재촬영 시 삭제하고, 비정상 종료 뒤 남은 파일은 다음 앱 실행 시 정리합니다. " +
            "가져온 영상은 읽기 권한으로 접근하며 원본을 복사하거나 수정하지 않습니다. 선택한 구간만 기기 안에서 분석합니다. 한 번에 무료는 최대 10초, 전체 이용권은 최대 20초입니다. " +
            "장면과 함께 저장하면 선택 구간의 영상·실제 소리·번역 자막을 MP4로 만들어 이 기기에 보관합니다. 기록에서 내려받거나 직접 공유할 수 있고 서버로 자동 전송하지 않습니다. JSON·CSV·PDF에는 미디어가 포함되지 않습니다. 얼굴 단서는 아기의 신원·감정·건강을 판정하지 않습니다.",
        "앨범에 직접 저장" to
            "보호자가 앨범 저장 버튼을 누르면 결과 카드 PNG 또는 영상·원음·번역 MP4 사본을 사진·동영상 앱의 아가톡 앨범에 저장합니다. Android 10 이상은 별도 저장소 읽기 권한 없이 앱이 만든 파일만 추가합니다. Android 8·9에서는 저장 버튼을 누를 때 저장 권한을 요청합니다. " +
            "앨범 사본은 앱 기록을 삭제하거나 앱을 제거해도 남으며 해당 사진 앱에서 관리·삭제합니다. 사진 앱의 클라우드 백업이 켜져 있다면 그 앱의 설정과 정책에 따라 백업될 수 있습니다.",
        "우리 아이 자랑 · 선택한 자료의 공개" to
            "게시판은 로그인 없이 읽고, 선택한 사진·영상카드와 설명을 미리 확인한 뒤 공개 버튼을 누를 때만 전송합니다. 개인 분석 원본·전체 기록을 자동으로 보내지 않습니다. " +
            "게시물·댓글·닉네임·관리 비밀번호 검증용 해시, 좋아요·차단·신고와 브라우저 무작위 식별값을 서버에 저장합니다. 비밀번호 원문은 저장하지 않으며 분실 시 자동 복구할 수 없습니다. " +
            "OpenAI Sites / Cloudflare 서버를 사용하며 해외에서 처리될 수 있습니다. 스팸 제한에는 일별 IP 해시를 사용하고 원문 IP를 게시판 DB에 저장하지 않습니다. 호스팅 제공자의 보안 로그에는 해당 제공자의 정책이 적용됩니다. " +
            "게시물 삭제 시 공개 접근을 즉시 차단하고 미디어를 제거합니다. 저장소 오류 시 다음 방문·운영자 확인 때 삭제를 재시도합니다. 보안상 삭제 상태와 신고는 남을 수 있습니다. 다른 사람이 이미 저장한 사본까지 회수할 수 없습니다. " +
            "게시판 공유용 기기 초안은 화면 종료 시 삭제하며, 비정상 종료로 남은 파일은 24시간이 지난 뒤 다음 게시판 사용 때 정리합니다. 앱 기록을 지워도 공개 게시물은 자동 삭제되지 않으므로 게시판에서 비밀번호로 별도 삭제해 주세요. " +
            "공유할 권리가 있는 자료만 올리고 아동의 노출·민감한 장면, 실명·주소·연락처는 올리지 마세요. 신고·작성자 차단 기능과 운영자 삭제 요청을 이용할 수 있습니다.",
        "3. 외부 전송과 제3자 제공" to
            "아기 소리·영상·기록은 서버로 자동 전송하지 않으며 광고·행동 분석 SDK를 사용하지 않습니다. " +
            "선택한 구독 구매·복원에는 Google Play와 결제 검증 통신이 필요합니다. 구매 토큰과 임의의 설치 식별자를 처리해 이용권을 확인하며 아기 기록·원본 미디어는 보내지 않습니다. " +
            "Play 심사 접근을 선택하면 심사 코드와 심사 전용 설치 식별자를 HTTPS로 확인하고, 코드는 기기 안에서 암호화해 보관하며 해제 시 삭제합니다. 서비스가 설정되지 않은 빌드는 이 통신을 하지 않습니다. " +
            "사용자가 직접 내보내거나 공유한 파일은 선택한 저장 위치·수신 앱의 정책이 적용됩니다.",
        "4. 검증 서비스와 접속 정보" to
            "결제·심사 확인 서비스는 Google Cloud 서울 리전에서 실행합니다. 구매 토큰·설치 식별자·심사 코드를 DB·파일·요청 본문 로그에 저장하지 않습니다. " +
            "확인과 요청 제한을 위한 일시 상태는 서버 메모리에서만 처리합니다. 아기 소리·영상·기록은 이 서비스로 보내지 않습니다. " +
            "Google Cloud Logging 접속 로그에는 IP 주소, 요청 경로, 응답 상태와 처리 시간이 포함될 수 있으며 30일 보관합니다. " +
            "요청 본문·구매 토큰·설치 식별자·심사 코드는 접속 로그에 쓰지 않습니다. 로그는 운영·장애·보안 대응에만 사용합니다. " +
            "Google의 서비스 운영과 기본 글로벌 로그 저장에 따라 정보가 국외에서 처리될 수 있습니다. 제공자는 Google LLC(미국)이며 해당 기능을 사용할 때 전송합니다. " +
            "관련 열람·삭제·처리 정지 문의는 운영자에게 접수할 수 있습니다. Google Play의 계정·결제 정보 보관에는 Google 정책이 적용됩니다.",
        "5. 보관과 삭제" to
            "문장·메모와 선택해 보관한 번역 영상은 보호자가 기록을 삭제하거나 앱을 제거할 때까지 기기에 남습니다. 기록 삭제 시 보관한 영상도 함께 삭제합니다. " +
            "기록 화면에서 항목을 삭제하거나 돌봄 화면에서 모든 기록 선택을 삭제할 수 있습니다. " +
            "아직 공유 화면에 전달하지 않은 파일은 기록 삭제 때 정리합니다. 이미 전달한 파일은 수신 앱이 읽을 수 있도록 따로 남기며, " +
            "전달 후 24시간이 지나면 다음 앱 시작·공유·기록 삭제 때 정리 대상입니다. 앱을 실행하지 않으면 그때까지 남고 Android가 캐시를 먼저 지울 수 있습니다. " +
            "최대 20개·64MiB가 차면 기존 파일을 보호하고 새 공유를 잠시 중단합니다. 앱 데이터 삭제·앱 제거는 이 캐시도 지웁니다. " +
            "외부에 보낸 파일은 해당 저장 위치에서 직접 지워 주세요. " +
            "Android 백업과 기기 간 데이터 전송에서도 앱 데이터가 제외되도록 설정했습니다.",
        "6. 보호자와 아기 정보" to
            "앱의 사용 대상은 아기의 부모 등 성인 보호자입니다. 아기 이름, 생년월일, 연락처나 계정을 요구하지 않습니다. " +
            "메모에는 보호자가 직접 입력한 내용이 포함될 수 있으므로 민감한 개인정보는 적지 마세요. " +
            "이 앱은 의료 진단이나 영아의 의도를 확정하는 서비스를 제공하지 않습니다.",
        "7. 문의와 변경" to
            "운영자 최용우 · 문의 bsfire1237@gmail.com. " +
            "처리 방식이 바뀌면 이 방침과 앱 내 안내를 업데이트합니다. " +
            "계정, 동기화, 광고, 서버 기능을 추가하기 전에는 처리 범위를 다시 고지합니다."
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            TextButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp).heightIn(min = 48.dp)) { Text("돌아가기", color = Ink) }
            TopBar("개인정보처리방침", "지금 내 기분이 그래 · 데이터 처리 안내")
            Text("2026년 10월 22일 개정 · 게시판 연결판의 처리 방식", color = Muted, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(20.dp))
        }
        sections.forEach { (title, body) ->
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(17.dp)).background(White).padding(17.dp)) {
                    Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(body, color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
                }
            }
        }
        item {
            Text("← 이전 화면으로", color = Ink, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 24.dp).heightIn(min = 48.dp).clickable(onClick = onBack).padding(10.dp))
        }
    }
}

@Composable
private fun GuideCard(number: String, title: String, body: String, background: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(21.dp)).background(background).padding(19.dp)
    ) {
        Text(number, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Text(body, color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}
