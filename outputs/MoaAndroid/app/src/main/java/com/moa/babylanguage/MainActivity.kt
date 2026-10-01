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

private enum class Page { HOME, RECORD, TRANSLATE, TIMELINE, GUIDE, CAMERA, VIDEO_IMPORT, PRIVACY, TERMS, ALBUM, PAYWALL }
private fun AppFeature.destination(): Page = when (this) {
    AppFeature.CAMERA -> Page.CAMERA
    AppFeature.VIDEO_IMPORT -> Page.VIDEO_IMPORT
    AppFeature.PDF -> Page.ALBUM
    else -> Page.HOME
}
private enum class CapturePhase { READY, RECORDING, ANALYZING, REVIEW }
private enum class Source { AUDIO, CAMERA, VIDEO_FILE }

private data class BabyExpression(
    val source: Source,
    val sound: String,
    val context: String,
    val note: String = "",
    val faceVisible: Boolean? = null,
    val mouthOpen: Boolean? = null,
    val audioCues: List<SoundCue> = emptyList(),
    val videoEvidence: String = "",
    val sessionId: String = UUID.randomUUID().toString()
)

// Only the small derived draft is saved across process recreation, never PCM or media URIs.
private val ExpressionSaver = Saver<BabyExpression?, String>(
    save = { value -> value?.let {
        JSONObject().apply {
            put("source", it.source.name); put("sound", it.sound); put("context", it.context)
            put("note", it.note); put("face", it.faceVisible); put("mouth", it.mouthOpen)
            put("evidence", it.videoEvidence); put("session", it.sessionId)
            put("cues", JSONArray().apply { it.audioCues.forEach { cue ->
                put(JSONObject().put("label", cue.label).put("score", cue.score))
            } })
        }.toString()
    } ?: "" },
    restore = { raw -> runCatching {
        if (raw.isBlank()) null else JSONObject(raw).let { value ->
            val cues = value.optJSONArray("cues") ?: JSONArray()
            BabyExpression(Source.valueOf(value.getString("source")), value.getString("sound"),
                value.getString("context"), value.optString("note"),
                if (value.has("face") && !value.isNull("face")) value.getBoolean("face") else null,
                if (value.has("mouth") && !value.isNull("mouth")) value.getBoolean("mouth") else null,
                (0 until cues.length()).map { i -> cues.getJSONObject(i).let {
                    SoundCue(it.getString("label"), it.getDouble("score").toFloat()) } },
                value.optString("evidence"), value.optString("session").ifBlank { UUID.randomUUID().toString() })
        }
    }.getOrNull() }
)

class MainActivity : ComponentActivity() {
    private var stopCaptureOnBackground: (() -> Unit)? = null
    private var billing: BillingManager? = null
    private var observationStore: ObservationStore? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        val store = ObservationStore(this).also { observationStore = it }
        val billingManager = BillingManager(this).also { billing = it }
        val phraseEngine = PhraseEngine(this)
        val accessBilling = PlayAccessBilling(billingManager)
        setContent {
            JigeumApp(this, store, accessBilling, phraseEngine,
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
    phraseEngine: PhraseEngine,
    registerBackgroundStop: (() -> Unit) -> Unit = {},
    mediaScreens: MediaScreens = DeviceMediaScreens,
    artifactExporter: ArtifactExporter = DeviceArtifactExporter,
    recordImporter: RecordImporter = DeviceRecordImporter
) {
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    var observations by remember { mutableStateOf(emptyList<Observation>()) }
    var ageBand by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var retryLoad by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var privacyReturn by rememberSaveable { mutableStateOf(Page.GUIDE) }
    var paywallReturn by rememberSaveable { mutableStateOf(Page.HOME) }
    var requestedFeature by rememberSaveable { mutableStateOf<AppFeature?>(null) }
    var paywallNotice by rememberSaveable { mutableStateOf<String?>(null) }
    val access = remember { AccessPolicy(billingManager::hasCurrentEntitlement) }
    DisposableEffect(access) { onDispose { access.invalidateMedia() } }
    var mediaSession by remember { mutableStateOf<MediaAccessSession?>(null) }
    val screenState = rememberSaveableStateHolder()
    var draft by rememberSaveable(stateSaver = ExpressionSaver) { mutableStateOf<BabyExpression?>(null) }
    val billingState by billingManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snacks = remember { SnackbarHostState() }
    val feedback = remember(snacks, scope) { LatestFeedback(snacks, scope) }
    fun notify(message: String) { feedback.show(message) }
    fun showPass(feature: AppFeature?, origin: Page = page, notice: String? = null) {
        access.invalidateMedia()
        mediaSession = null
        requestedFeature = feature
        paywallReturn = when (origin) {
            Page.CAMERA, Page.VIDEO_IMPORT, Page.PAYWALL, Page.PRIVACY, Page.TERMS -> Page.HOME
            else -> origin
        }
        paywallNotice = notice
        page = Page.PAYWALL
        billingManager.refresh()
    }
    fun closePass() {
        page = if (paywallReturn == Page.TRANSLATE && draft == null) Page.HOME else paywallReturn
        requestedFeature = null
        paywallNotice = null
    }
    fun enterPaid(feature: AppFeature, origin: Page = page) {
        if (feature == AppFeature.PDF) {
            if (access.canUse(feature)) page = Page.ALBUM else showPass(feature, origin)
        } else {
            val session = access.beginMedia(feature)
            if (session == null) showPass(feature, origin)
            else { mediaSession = session; page = feature.destination() }
        }
    }
    fun authorizeMedia(session: MediaAccessSession): Boolean {
        // Old activity results and callbacks cannot replace a newer screen/session.
        if (page != session.feature.destination() || mediaSession != session) return false
        if (access.isCurrent(session)) return true
        showPass(session.feature, Page.HOME,
            "이용권을 다시 확인해야 해서 관찰을 멈췄어요. 이미 만든 문장과 기록은 그대로 있어요.")
        return false
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
                observations = snapshot.observations; ageBand = snapshot.ageBand
                after()
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
            val snapshot = store.snapshot()
            observations = snapshot.observations; ageBand = snapshot.ageBand; loaded = true
            snapshot.warning?.let(::notify)
            withContext(Dispatchers.IO) { MemoryArtifacts.cleanup(activity) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            loadError = "기록을 열지 못했어요. 저장 공간을 확인하고 다시 시도해 주세요."
        }
    }
    LaunchedEffect(page, draft) { if (page == Page.TRANSLATE && draft == null) page = Page.HOME }
    LaunchedEffect(page, billingState.isPremium) {
        val feature = when (page) {
            Page.CAMERA -> AppFeature.CAMERA
            Page.VIDEO_IMPORT -> AppFeature.VIDEO_IMPORT
            else -> null
        }
        if (feature == null) {
            if (mediaSession != null) { access.invalidateMedia(); mediaSession = null }
        } else {
            val current = mediaSession
            if (current == null || current.feature != feature) {
                // A saved route alone never restores permission to use a sensor/model.
                val restored = access.beginMedia(feature)
                if (restored == null) showPass(feature, Page.HOME, "이 기능을 사용하려면 전체 이용권을 확인해 주세요.")
                else mediaSession = restored
            } else authorizeMedia(current)
        }
    }
    fun goBack() {
        when (page) {
            Page.PRIVACY, Page.TERMS -> { page = privacyReturn }
            Page.PAYWALL -> closePass()
            Page.TRANSLATE -> when (draft?.source) {
                Source.CAMERA -> enterPaid(AppFeature.CAMERA, Page.TRANSLATE)
                Source.VIDEO_FILE -> enterPaid(AppFeature.VIDEO_IMPORT, Page.TRANSLATE)
                else -> { page = Page.RECORD }
            }
            else -> { page = Page.HOME }
        }
    }
    BackHandler(enabled = page != Page.HOME) { goBack() }
    val destinations = page in listOf(Page.HOME, Page.TIMELINE, Page.GUIDE, Page.ALBUM)
    MaterialTheme(colorScheme = lightColorScheme(primary = Ink, onPrimary = White,
        surface = Cream, onSurface = Ink, secondaryContainer = PaleSage, onSecondaryContainer = Ink,
        background = Cream, onBackground = Ink, outline = Muted)) {
        Surface(color = Cream, modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
                val wide = maxWidth >= 840.dp
                Row(Modifier.fillMaxSize()) {
                    if (wide && destinations) AppNavigation(page, rail = true) { page = it }
                    Column(Modifier.weight(1f).fillMaxSize().imePadding()) {
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Ink)
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            Box(Modifier.widthIn(max = 720.dp).fillMaxSize().clipToBounds().testTag(if (loaded) "page-${page.name.lowercase()}" else "page-loading")) {
                                if (!loaded) {
                                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                                        Text(loadError ?: "기록을 불러오고 있어요", color = Ink)
                                        if (loadError != null) PrimaryButton("다시 열기", { retryLoad++ })
                                    }
                                } else when (page) {
                                    Page.HOME -> HomeScreen(observations, ageBand, billingState.isPremium,
                                        onAgeBand = { update({ store.saveAgeBand(it) }) },
                                        onRecord = { page = Page.RECORD }, onCamera = { enterPaid(AppFeature.CAMERA) },
                                        onVideoImport = { enterPaid(AppFeature.VIDEO_IMPORT) }, onTimeline = { page = Page.TIMELINE })
                                    Page.RECORD -> RecordScreen({ registerBackgroundStop(it) },
                                        onBack = { page = Page.HOME }, onTranslate = { sound, context, note, cues ->
                                            draft?.let { screenState.removeState("translation-${it.sessionId}") }
                                            draft = BabyExpression(Source.AUDIO, sound, context, note, audioCues = cues)
                                            page = Page.TRANSLATE
                                        })
                                    Page.TRANSLATE -> draft?.let { expression -> screenState.SaveableStateProvider("translation-${expression.sessionId}") {
                                        TranslationScreen(expression, phraseEngine, busy, onBack = { goBack() },
                                            onSave = { final, phrase ->
                                                if (!busy && draft != null) update({
                                                    val usedIds = observations.mapTo(HashSet()) { it.id }
                                                    var newId = System.currentTimeMillis()
                                                    while (newId in usedIds) newId++
                                                    store.save(Observation(newId, final.sound,
                                                        final.context, final.note, phrase, final.source.name,
                                                        final.audioCues.firstOrNull()?.label ?: ""))
                                                }, {
                                                    screenState.removeState("translation-${expression.sessionId}")
                                                    draft = null; page = Page.TIMELINE; notify("우리 아이의 한마디를 남겼어요.")
                                                })
                                            })
                                    } }
                                    Page.TIMELINE -> TimelineScreen(observations,
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
                                    Page.GUIDE -> GuideScreen(onPrivacy = { privacyReturn = Page.GUIDE; page = Page.PRIVACY },
                                        onDeleteAll = {
                                            update({ store.deleteAll()
                                            try { MemoryArtifacts.clear(activity) }
                                            catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; notify("기록은 지웠어요. 임시 공유 파일을 정리하지 못했어요.") }
                                            withContext(Dispatchers.IO) { phraseEngine.reset() }
                                        }, {
                                            draft?.let { screenState.removeState("translation-${it.sessionId}") }
                                            draft = null; notify("기록과 월령을 지웠어요.")
                                        }, invalidateExports = true) },
                                        dataTools = { DataTransferPanel(store, observations, ageBand,
                                            onImported = { snapshot -> observations = snapshot.observations; ageBand = snapshot.ageBand },
                                            onMessage = ::notify, operationScope = scope, importer = recordImporter,
                                            beginMutation = ::beginMutation, endMutation = { busy = false }) },
                                        onRestore = { showPass(null, Page.GUIDE); billingManager.restore() })
                                    Page.CAMERA -> {
                                        val session = mediaSession
                                        if (billingState.isPremium && session?.feature == AppFeature.CAMERA) {
                                            mediaScreens.Camera(authorize = { authorizeMedia(session) },
                                                onBack = { page = Page.HOME }, onTranslate = { cue ->
                                                    if (authorizeMedia(session)) {
                                                        draft?.let { screenState.removeState("translation-${it.sessionId}") }
                                                        draft = BabyExpression(Source.CAMERA, "영상 장면", "", cue.manualNote ?: "", cue.faceVisible, cue.mouthOpen)
                                                        page = Page.TRANSLATE
                                                    }
                                                })
                                        } else Text("이용권을 확인하고 있어요", Modifier.padding(24.dp), color = Muted)
                                    }
                                    Page.VIDEO_IMPORT -> {
                                        val session = mediaSession
                                        if (billingState.isPremium && session?.feature == AppFeature.VIDEO_IMPORT) {
                                            mediaScreens.Video(authorize = { authorizeMedia(session) },
                                                onBack = { page = Page.HOME }, onTranslate = { observed ->
                                                    if (authorizeMedia(session)) {
                                                        draft?.let { screenState.removeState("translation-${it.sessionId}") }
                                                        draft = BabyExpression(Source.VIDEO_FILE, observed.heardSound, "", observed.manualNote ?: "",
                                                            observed.faceVisible, observed.mouthOpen, observed.audioCues, observed.evidence)
                                                        page = Page.TRANSLATE
                                                    }
                                                })
                                        } else Text("이용권을 확인하고 있어요", Modifier.padding(24.dp), color = Muted)
                                    }
                                    Page.PRIVACY -> PrivacyPolicyScreen { goBack() }
                                    Page.TERMS -> ServiceTermsScreen { goBack() }
                                    Page.ALBUM -> screenState.SaveableStateProvider("album") {
                                        AlbumScreen(observations, billingState.isPremium,
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
                                            else enterPaid(feature, paywallReturn)
                                        },
                                        freeContinuationLabel = if (paywallReturn == Page.TRANSLATE && draft != null)
                                            "만들던 문장 무료로 계속하기" else "무료 소리·직접 입력으로 계속하기",
                                        onFree = {
                                            if (paywallReturn == Page.TRANSLATE && draft != null) closePass()
                                            else { requestedFeature = null; paywallNotice = null; page = Page.RECORD }
                                        },
                                        onBack = ::closePass,
                                        onPrivacy = { privacyReturn = Page.PAYWALL; page = Page.PRIVACY },
                                        onTerms = { privacyReturn = Page.PAYWALL; page = Page.TERMS },
                                        onReviewActivate = billingManager::activateReviewAccess,
                                        onReviewDeactivate = billingManager::deactivateReviewAccess)
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
private fun HomeScreen(
    observations: List<Observation>, ageBand: String, hasPass: Boolean, onAgeBand: (String) -> Unit,
    onRecord: () -> Unit, onCamera: () -> Unit,
    onVideoImport: () -> Unit, onTimeline: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            TopBar("지금 아기는 뭐라고 할까요?", "지금 내 기분이 그래 · 우리 아이의 작은 표현")
            Text(
                "옹알이와 표정에 그때의 상황을 더해\n아이의 말처럼 따뜻한 문장으로 만나보세요.",
                modifier = Modifier.padding(horizontal = 24.dp), color = Muted,
                fontSize = 15.sp, lineHeight = 23.sp
            )
            Spacer(Modifier.height(18.dp))
            HeroRecordCard(onRecord, onCamera, onVideoImport, hasPass)
            Spacer(Modifier.height(22.dp))
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Text("우리 아기는 몇 개월인가요?", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                BoxWithConstraints {
                    val narrow = maxWidth < 360.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f
                    val choices = listOf("6~8개월", "9~12개월", "13~18개월")
                    @Composable fun AgeOption(value: String) {
                        Text(
                            value, color = Ink, fontSize = 12.sp,
                            fontWeight = if (ageBand == value) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                .background(if (ageBand == value) Sage else White)
                                .heightIn(min = 48.dp).selectable(selected = ageBand == value, role = Role.RadioButton) { onAgeBand(value) }
                                .padding(horizontal = 12.dp, vertical = 11.dp)
                        )
                    }
                    if (narrow) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            choices.forEach { AgeOption(it) }
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            choices.forEach { AgeOption(it) }
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text("월령은 기기에만 저장되며 문장 판정에는 사용하지 않아요.", color = Muted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(20.dp))
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
}

@Composable
private fun HeroRecordCard(
    onRecord: () -> Unit, onCamera: () -> Unit, onVideoImport: () -> Unit, hasPass: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(28.dp)).background(PaleSage).padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("아이의 작은 소리,", color = Muted, fontSize = 13.sp)
                Text("아기말 번역하기", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("10초 녹음 · 아이 시점의 한 문장", color = Muted, fontSize = 13.sp)
            }
            Image(
                painter = painterResource(R.drawable.hero_touch),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(88.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton("소리로 번역하기", onRecord)
        Text("녹음·직접 입력은 횟수 제한 없이 무료예요.", color = Muted, fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(10.dp))
        SecondaryButton(if (hasPass) "저장된 영상 가져오기" else "저장된 영상 가져오기 · 전체 이용권", onVideoImport)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCamera, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (hasPass) "카메라로 살펴보기" else "카메라로 살펴보기 · 전체 이용권", color = Ink)
        }
        Spacer(Modifier.height(15.dp))
        Text("그 순간의 소리와 상황을 아이의 말처럼 풀어드려요. 원본은 저장하지 않아요.", color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
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
        Text("이 문장은 보호자가 고른 단서를 말로 풀어 쓴 예시예요.\n울음의 이유나 감정, 건강 상태를 단정하지 않아요.", color = Ink, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun RecordScreen(
    registerBackgroundStop: (() -> Unit) -> Unit,
    onBack: () -> Unit,
    onTranslate: (String, String, String, List<SoundCue>) -> Unit
) {
    val context = LocalContext.current
    val recorder = remember { ShortAudioCapture() }
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf(CapturePhase.READY) }
    var elapsed by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var sound by remember { mutableStateOf("") }
    var soundConfirmed by remember { mutableStateOf(false) }
    var recentContext by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var soundCues by remember { mutableStateOf<List<SoundCue>>(emptyList()) }
    var recorded by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var analysisJob by remember { mutableStateOf<Job?>(null) }

    fun finishCapture() {
        if (phase != CapturePhase.RECORDING) return
        phase = CapturePhase.ANALYZING
        analysisJob = scope.launch {
            val analysis = withContext(Dispatchers.Default) {
                val pcm = recorder.finish()
                try { SoundAnalyzer.analyzeDetailed(context.applicationContext, pcm) }
                finally { pcm.fill(0f) }
            }
            soundCues = analysis.cues
            message = analysis.problem ?: ""
            sound = soundCues.firstOrNull()?.takeIf { it.score >= 0.50f }?.let {
                when (it.label) {
                    "울음" -> "울음"
                    "웃음" -> "웃음"
                    else -> ""
                }
            } ?: ""
            soundConfirmed = false
            recorded = true
            phase = CapturePhase.REVIEW
        }
    }

    fun startCapture() {
        if (phase != CapturePhase.READY) return
        if (recorder.start()) {
            elapsed = 0
            message = ""
            soundCues = emptyList()
            sound = ""
            soundConfirmed = false
            recorded = false
            phase = CapturePhase.RECORDING
        } else {
            message = "마이크를 시작하지 못했어요. 다른 앱이 사용 중인지 확인해 주세요."
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            permissionDenied = false
            startCapture()
        } else {
            permissionDenied = true
            message = "마이크 권한이 있어야 10초 관찰을 시작할 수 있어요."
        }
    }

    DisposableEffect(Unit) {
        registerBackgroundStop {
            if (phase == CapturePhase.RECORDING || phase == CapturePhase.ANALYZING) {
                analysisJob?.cancel()
                recorder.discard()
                phase = CapturePhase.READY
                message = "앱을 벗어나 녹음을 중단했어요. 음성은 저장되지 않았어요."
            }
        }
        onDispose {
            analysisJob?.cancel()
            recorder.discard()
            registerBackgroundStop { }
        }
    }

    LaunchedEffect(phase) {
        if (phase == CapturePhase.RECORDING) {
            while (elapsed < 10) {
                delay(1000)
                elapsed += 1
            }
            finishCapture()
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            TopBar("지금, 잠깐 들어볼까요?", "10초 소리 관찰")
            Text(
                "아기 가까이에서 조용히 녹음해 주세요.\n소리는 기기 안에서 분석하고 저장하지 않아요.",
                color = Muted, fontSize = 15.sp, lineHeight = 23.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(29.dp))
            CaptureCircle(phase, elapsed)
            Spacer(Modifier.height(24.dp))
            when (phase) {
                CapturePhase.READY -> {
                    Column(Modifier.padding(horizontal = 24.dp)) {
                        PrimaryButton("녹음 시작", onClick = {
                            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startCapture()
                            else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        })
                        if (permissionDenied) {
                            Spacer(Modifier.height(12.dp))
                            SecondaryButton("앱 설정에서 마이크 권한 켜기", onClick = {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    "package:${context.packageName}".toUri()))
                            })
                        }
                        Spacer(Modifier.height(12.dp))
                        SecondaryButton("녹음 없이 직접 번역", onClick = {
                            recorded = false; sound = ""; soundConfirmed = false; phase = CapturePhase.REVIEW
                        })
                    }
                }
                CapturePhase.RECORDING -> {
                    Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        PrimaryButton("여기서 마치기", onClick = { finishCapture() })
                        Spacer(Modifier.height(9.dp))
                        Text("최대 10초 뒤 자동으로 멈춰요", color = Muted, fontSize = 13.sp)
                    }
                }
                CapturePhase.ANALYZING -> {
                    Text("소리 단서를 살펴보는 중이에요…", color = Muted, fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), textAlign = TextAlign.Center)
                }
                CapturePhase.REVIEW -> ReviewForm(
                    sound = sound, onSound = { sound = it; soundConfirmed = true },
                    soundConfirmed = soundConfirmed,
                    soundCues = soundCues, recorded = recorded,
                    recentContext = recentContext, onContext = { recentContext = it },
                    note = note, onNote = { note = it },
                    onSave = { onTranslate(sound, recentContext, note.trim(), soundCues) }
                )
            }
            if (phase == CapturePhase.REVIEW || phase == CapturePhase.ANALYZING) {
                Spacer(Modifier.height(12.dp))
                Column(Modifier.padding(horizontal = 24.dp)) {
                    SecondaryButton(if (phase == CapturePhase.ANALYZING) "분석 취소하고 다시 듣기" else "다시 녹음하기") {
                        analysisJob?.cancel()
                        recorder.discard()
                        phase = CapturePhase.READY
                        soundCues = emptyList(); sound = ""; soundConfirmed = false
                        recorded = false; message = ""; elapsed = 0
                    }
                }
            }
            AnimatedVisibility(message.isNotEmpty()) {
                Text(message, color = Ink, fontSize = 13.sp, lineHeight = 19.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(14.dp)).background(PaleApricot).padding(14.dp))
            }
            if (phase == CapturePhase.READY) {
                Spacer(Modifier.height(20.dp))
                Text("← 오늘 화면으로", color = Muted, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 24.dp).clickable(onClick = onBack).padding(8.dp))
            }
        }
    }
}

@Composable
private fun CaptureCircle(phase: CapturePhase, elapsed: Int) {
    val transition = rememberInfiniteTransition(label = "recording pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.86f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse"
    )
    val active = phase == CapturePhase.RECORDING
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(194.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    color = if (active) Apricot.copy(alpha = 0.25f) else PaleApricot,
                    radius = size.minDimension * (if (active) pulse else 0.93f) / 2f
                )
                drawCircle(
                    color = if (active) Apricot else Sage,
                    radius = size.minDimension * 0.36f
                )
                if (active) {
                    drawArc(color = Ink.copy(alpha = 0.45f), startAngle = -90f,
                        sweepAngle = 360f * (elapsed / 10f), useCenter = false,
                        topLeft = Offset(size.width * 0.05f, size.height * 0.05f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.9f, size.height * 0.9f),
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
                }
            }
            if (phase == CapturePhase.REVIEW) Text("✓", color = Ink, fontSize = 48.sp, fontWeight = FontWeight.Light)
            else SoundWave(Modifier.size(55.dp), Ink)
        }
        Spacer(Modifier.height(15.dp))
        Text(
            when (phase) {
                CapturePhase.READY -> "준비되면 시작해요"
                CapturePhase.RECORDING -> "듣는 중 · ${elapsed}초 / 10초"
                CapturePhase.ANALYZING -> "소리 단서 살펴보는 중"
                CapturePhase.REVIEW -> "원본 소리는 남기지 않았어요"
            },
            color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold
        )
        if (phase == CapturePhase.REVIEW) {
            Spacer(Modifier.height(5.dp))
            Text("이제 보호자가 들은 소리와 상황을 표시해 주세요.", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SoundWave(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val heights = listOf(0.30f, 0.63f, 0.98f, 0.55f, 0.36f)
        heights.forEachIndexed { index, height ->
            val x = size.width * (0.1f + index * 0.2f)
            drawLine(color, Offset(x, size.height * (1-height)/2), Offset(x, size.height * (1+height)/2),
                strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun ReviewForm(
    sound: String, onSound: (String) -> Unit,
    soundConfirmed: Boolean,
    soundCues: List<SoundCue>, recorded: Boolean,
    recentContext: String, onContext: (String) -> Unit,
    note: String, onNote: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(27.dp))
        Text("들은 것", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("보호자가 들은 유형을 골라 주세요. 모델 단서는 참고만 해요.", color = Muted, fontSize = 13.sp)
        if (sound.isNotEmpty() && !soundConfirmed) {
            Spacer(Modifier.height(8.dp))
            Text("‘$sound’이 추천되어 있어요. 맞으면 해당 칸을 눌러 확인해 주세요.",
                color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        if (recorded) {
            Spacer(Modifier.height(12.dp))
            val cueText = if (soundCues.isEmpty()) {
                "일반 소리 분류가 불확실했어요. 직접 들은 소리를 골라 주세요."
            } else {
                "기기에서 잡은 일반 소리 후보 · " + soundCues.joinToString(" · ") { it.label } +
                    "\n영아에게서 검증된 번역 결과가 아니에요. 직접 들은 소리를 골라 주세요."
            }
            Text(cueText, color = Ink, fontSize = 13.sp, lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(PaleSage).padding(14.dp))
        }
        Spacer(Modifier.height(12.dp))
        ChoiceGrid(listOf("울음", "웃음", "옹알이", "기타 소리"), sound, onSound)
        Spacer(Modifier.height(24.dp))
        Text("보호자가 확인할 것", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("직전에 무엇을 했나요? 기억나는 상황 하나를 골라 주세요.", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        ChoiceGrid(listOf("수유 전", "수유 후", "잠들기 전", "잠에서 깬 후", "놀이 중", "기타 상황"), recentContext, onContext)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = note, onValueChange = { if (it.length <= 120) onNote(it) },
            label = { Text("메모 · 선택 사항") },
            placeholder = { Text("예: 낮잠에서 깬 지 20분 정도") },
            modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3,
            shape = RoundedCornerShape(16.dp)
        )
        Spacer(Modifier.height(21.dp))
        PrimaryButton("아기말 문장 보기", onClick = onSave,
            enabled = sound.isNotEmpty() && soundConfirmed && recentContext.isNotEmpty())
        Spacer(Modifier.height(8.dp))
        Text("소리의 뜻이나 원인은 여기서 판단하지 않아요.", color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
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
private fun TranslationScreen(
    initial: BabyExpression,
    phraseEngine: PhraseEngine,
    saving: Boolean,
    onBack: () -> Unit,
    onSave: (BabyExpression, String) -> Unit
) {
    var sound by rememberSaveable(initial) { mutableStateOf(initial.sound) }
    var recentContext by rememberSaveable(initial) { mutableStateOf(initial.context) }
    var candidate by rememberSaveable(initial) { mutableIntStateOf(0) }
    var isEditing by rememberSaveable(initial) { mutableStateOf(false) }
    var editedPhrase by rememberSaveable(initial) { mutableStateOf("") }
    val inputsReady = sound.isNotEmpty() && recentContext.isNotEmpty()
    val phraseKey = "$sound|$recentContext"
    var phraseState by remember(initial.sessionId) { mutableStateOf("" to emptyList<String>()) }
    var phraseError by remember { mutableStateOf(false) }
    var phraseRetry by remember { mutableIntStateOf(0) }
    LaunchedEffect(initial.sessionId, phraseKey, phraseRetry) {
        phraseError = false
        if (inputsReady) {
            try {
                val result = withContext(Dispatchers.IO) {
                    phraseEngine.candidates(sound, recentContext, initial.sessionId)
                }
                phraseState = phraseKey to result
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                phraseError = true
            }
        }
    }
    val phrases = if (phraseState.first == phraseKey) phraseState.second else emptyList()
    val ready = inputsReady && phrases.size == 3
    val selectedPhrase = if (isEditing) editedPhrase.trim() else phrases.getOrElse(candidate) { "" }

    Column(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            TopBar("아기말, 이렇게 들려요", "아기말 번역")
            Text(
                when (initial.source) {
                    Source.CAMERA -> "카메라에서 본 장면과 보호자가 고른 상황을 문장으로 풀어봐요."
                    Source.VIDEO_FILE -> "선택한 영상은 저장하지 않았어요. 확인한 장면과 직접 들은 소리를 문장으로 풀어봐요."
                    Source.AUDIO -> "원본 소리는 저장하지 않아요. 보호자가 고른 단서를 문장으로 풀어봐요."
                },
                modifier = Modifier.padding(horizontal = 24.dp), color = Muted,
                fontSize = 14.sp, lineHeight = 21.sp
            )
            Spacer(Modifier.height(23.dp))
            if (inputsReady && !ready) {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(if (phraseError) "문장을 불러오지 못했어요. 저장 공간을 확인해 주세요." else "그 순간에 어울리는 문장을 고르고 있어요.", color = Muted)
                    if (phraseError) TextButton(onClick = { phraseRetry++ }) { Text("다시 불러오기") }
                }
            }
            if (!inputsReady) {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text("먼저 그때의 상황을 알려주세요", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text("영상만으로 아이의 마음이나 욕구를 알 수 없어요.", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    ChoiceGrid(listOf("수유 전", "수유 후", "잠들기 전", "잠에서 깬 후", "놀이 중", "기타 상황"), recentContext) {
                        recentContext = it
                        candidate = 0
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            if (ready) {
                Column(Modifier.padding(horizontal = 24.dp)) {
                    Text("가능한 한마디", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(27.dp))
                            .background(PaleSage).padding(23.dp)
                    ) {
                        Text("“", color = Ink.copy(alpha = .55f), fontSize = 38.sp, lineHeight = 35.sp)
                        if (isEditing) {
                            OutlinedTextField(
                                value = editedPhrase,
                                onValueChange = { if (it.length <= 160) editedPhrase = it },
                                modifier = Modifier.fillMaxWidth().testTag("draft-phrase-editor"),
                                label = { Text("우리 아이 말로 고치기") },
                                minLines = 3, maxLines = 5,
                                shape = RoundedCornerShape(15.dp)
                            )
                        } else {
                            Text(phrases[candidate], color = Ink, fontSize = 23.sp,
                                lineHeight = 34.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(13.dp))
                        Text("이렇게 말하는 걸까요?", color = Muted, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(23.dp))
                    Text("다른 말로도 볼 수 있어요", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(9.dp))
                    phrases.forEachIndexed { index, phrase ->
                        if (index != candidate || isEditing) {
                            Text(
                                phrase,
                                color = Ink,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                                    .clip(RoundedCornerShape(15.dp)).background(White)
                                    .clickable { candidate = index; isEditing = false }
                                    .padding(16.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (isEditing) "추천 문장으로 돌아가기" else "내가 직접 고치기",
                        color = Muted, fontSize = 14.sp,
                        modifier = Modifier.heightIn(min = 48.dp).clickable {
                            if (isEditing) isEditing = false
                            else { editedPhrase = phrases[candidate]; isEditing = true }
                        }.padding(vertical = 10.dp)
                    )
                    Spacer(Modifier.height(19.dp))
                    var showEvidence by rememberSaveable(initial.sessionId) { mutableStateOf(false) }
                    TextButton(onClick = { showEvidence = !showEvidence }) {
                        Text(if (showEvidence) "관찰 단서 접기" else "관찰 단서와 안내 보기", color = Ink)
                    }
                    if (showEvidence) EvidenceCard("이렇게 문장으로 풀었어요", buildString {
                        append("보호자가 ‘$sound’, ‘$recentContext’을 확인했어요. ")
                        if (initial.videoEvidence.isNotBlank()) append(initial.videoEvidence + " ")
                        append("소리·표정 단서는 뜻이나 욕구를 판정하지 않아요. 실제 모습과 최근 돌봄을 함께 살펴주세요.")
                    })
                    Spacer(Modifier.height(18.dp))
                    Text("상황 바꾸기", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    ChoiceGrid(listOf("수유 전", "수유 후", "잠들기 전", "잠에서 깬 후", "놀이 중", "기타 상황"), recentContext) {
                        recentContext = it
                        candidate = 0
                        isEditing = false
                    }
                    if (initial.source == Source.AUDIO || initial.source == Source.VIDEO_FILE) {
                        Spacer(Modifier.height(8.dp))
                        Text("들은 소리 고치기", color = Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        ChoiceGrid(listOf("울음", "웃음", "옹알이", "기타 소리"), sound) {
                            sound = it
                            candidate = 0
                            isEditing = false
                        }
                    }
                    Spacer(Modifier.height(17.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("문장은 보호자 입력을 바탕으로 만든 표현 예시이며 감정·욕구·건강 판정이 아니에요.",
                        color = Muted, fontSize = 12.sp, lineHeight = 18.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 16.dp)
                .heightIn(min = 48.dp)) {
                Text("← 처음부터 다시 입력하기", color = Muted, fontSize = 14.sp)
            }
        }
    }
    if (ready) Column(Modifier.fillMaxWidth().background(Cream).padding(horizontal = 24.dp, vertical = 10.dp)) {
        PrimaryButton(if (saving) "저장하고 있어요" else "이 문장 저장하기", onClick = {
            if (!saving) onSave(initial.copy(sound = sound, context = recentContext), selectedPhrase)
        }, enabled = selectedPhrase.isNotBlank() && !saving)
    }
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
private fun TimelineScreen(observations: List<Observation>, onDelete: (Long) -> Unit, onAlbum: () -> Unit, onShare: (Observation) -> Unit) {
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
                ObservationCard(observation, onDelete = { onDelete(observation.id) }, onShare = { onShare(observation) })
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            PrivacyNote()
        }
    }
}

@Composable
private fun ObservationCard(observation: Observation, onDelete: (() -> Unit)?, onShare: (() -> Unit)? = null) {
    var confirmDelete by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(21.dp)).background(White).padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(PaleSage), contentAlignment = Alignment.Center) {
                SoundWave(Modifier.size(23.dp), Ink)
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
        }
        if (observation.note.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(observation.note, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
        }
        if (onShare != null) TextButton(onClick = onShare) { Text("문장 카드 공유", color = Ink) }
        if (onDelete != null) {
            Spacer(Modifier.height(5.dp))
            Text(
                if (confirmDelete) "다시 누르면 이 기록을 삭제해요" else "기록 삭제",
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
private fun GuideScreen(onDeleteAll: () -> Unit, onPrivacy: () -> Unit, dataTools: @Composable () -> Unit, onRestore: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
        item {
            TopBar("아기와 대화하는 시간", "돌봄 힌트")
            Text("6~18개월 아기를 위한 대화 힌트예요. 소리를 맞히려 하기보다 신호에 천천히 반응해 보세요.",
                color = Muted, fontSize = 15.sp, lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(25.dp))
            GuideCard("01", "잠깐 멈추고 살펴보기", "표정, 몸짓, 주변 소리와 방금 있었던 일을 함께 살펴보세요.", PaleSage)
            GuideCard("02", "짧게 말로 답하기", "아기가 낸 소리를 따라 하거나 지금 보이는 것을 말해 주세요.", PaleApricot)
            GuideCard("03", "다시 반응할 시간 주기", "서두르지 않고 잠시 기다리면, 아기가 다시 소리나 몸짓으로 답할 수 있어요.", PaleSage)
            Spacer(Modifier.height(10.dp))
            Text("걱정되는 증상이 있거나 평소와 다른 상태가 계속되면 소아청소년과 전문가에게 상담하세요.",
                modifier = Modifier.padding(horizontal = 24.dp), color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(35.dp))
            dataTools()
            TextButton(onClick = onRestore, modifier = Modifier.padding(horizontal = 12.dp)) { Text("구매 복원 · 이용권 관리", color = Ink) }
            Text("내 데이터", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(8.dp))
            Text("번역 문장과 관찰 기록은 이 기기에만 남아요. 원본 음성·영상은 저장하지 않아요.",
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
        }
    }
}

@Composable
private fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val sections = listOf(
        "1. 서비스와 처리 범위" to
            "‘지금 내 기분이 그래’는 6~18개월 아기의 보호자가 소리와 장면을 살펴보고 표현 예시를 만드는 앱입니다. " +
            "보호자가 선택한 월령 범위, 소리 유형, 직전 상황, 메모, 문장, 모델 단서 이름은 이 기기에만 저장합니다. " +
            "아기 기록을 저장하는 외부 DB나 앱 회원가입은 없습니다.",
        "2. 마이크·카메라·선택한 영상" to
            "마이크는 보호자가 녹음을 시작할 때 최대 10초 동안 사용합니다. 소리는 기기 메모리에서만 임시로 분석하며 " +
            "원본 녹음 파일은 만들지 않습니다. 카메라는 보호자가 시작한 화면에서만 사용하며 프레임이나 얼굴 정보 파일을 저장하지 않습니다. " +
            "영상 가져오기는 보호자가 시스템 파일 선택기에서 고른 파일만 읽어 기기에서 최대 5개 장면을 살펴봅니다. " +
            "영상의 첫 최대 10초 소리는 기기 메모리에서만 임시로 일반 소리 후보를 분석하고, 보호자가 확인합니다. 원본 영상은 앱에 복사하거나 서버로 보내지 않습니다. " +
            "다른 사진 서비스에 보관된 영상을 선택하면 그 서비스의 동기화와 접근에는 해당 제공자의 정책이 적용됩니다.",
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
            "문장과 메모는 보호자가 삭제하거나 앱을 제거할 때까지 기기에 남습니다. " +
            "기록 화면에서 항목을 삭제하거나 돌봄 화면에서 모든 기록과 월령 선택을 삭제할 수 있습니다. " +
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
            TopBar("개인정보처리방침", "지금 내 기분이 그래 · 데이터 처리 안내")
            Text("시행일 2026년 10월 1일 · 현재 Android 앱의 처리 방식", color = Muted, fontSize = 13.sp,
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
