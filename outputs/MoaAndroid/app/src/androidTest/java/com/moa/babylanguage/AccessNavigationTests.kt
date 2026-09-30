package com.moa.babylanguage

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Real app composition/navigation with billing and sensor replies supplied only in androidTest. */
class AccessNavigationTests {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val id = "access-ui-${UUID.randomUUID()}"
    private lateinit var store: ObservationStore
    private lateinit var phrases: PhraseEngine
    private val billing = FakeBilling()
    private val media = FakeMedia()
    private val visible = mutableStateOf(true)
    private var started = false
    private lateinit var restoration: StateRestorationTester

    @Before fun setup() {
        store = ObservationStore(compose.activity, "$id.db", "$id-legacy")
        phrases = PhraseEngine(compose.activity, "$id-phrases")
    }

    @After fun cleanup() {
        if (started) { compose.runOnIdle { visible.value = false }; compose.waitForIdle() }
        runBlocking { store.close() }
        compose.activity.deleteDatabase("$id.db")
        for (name in listOf("$id-legacy", "$id-phrases")) {
            compose.activity.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun start(paid: Boolean = false, seed: Boolean = false) {
        billing.publishVerified(paid)
        if (seed) runBlocking {
            store.save(Observation(System.currentTimeMillis(), "옹알이", "놀이 중", "", "같이 놀아 줄래?"))
        }
        restoration = StateRestorationTester(compose)
        started = true
        restoration.setContent {
            if (visible.value) JigeumApp(compose.activity, store, billing, phrases, mediaScreens = media)
        }
        waitText("소리로 번역하기")
    }

    private fun waitPage(name: String) {
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("page-$name").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun waitText(text: String) {
        compose.waitUntil(8_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun click(text: String) { compose.onNodeWithText(text).performScrollTo().performClick() }

    @Test fun lockedMediaHasContextAndFreeExitWithoutOpeningSensors() {
        start()
        click("카메라로 살펴보기 · 전체 이용권")
        waitPage("paywall")
        compose.onNodeWithText("지금의 표정도 함께 살펴봐요").assertExists()
        assertEquals(0, media.cameraOpened)
        click("닫기 · 원래 화면으로")
        waitPage("home")
        click("저장된 영상 가져오기 · 전체 이용권")
        waitPage("paywall")
        compose.onNodeWithText("담아 둔 영상으로 이야기를 나눠요").assertExists()
        assertEquals(0, media.videoOpened)
        click("무료 소리·직접 입력으로 계속하기")
        waitPage("record")
    }

    @Test fun pendingPurchaseDoesNotUnlockAndVerifiedPurchaseWaitsForContinue() {
        start()
        click("카메라로 살펴보기 · 전체 이용권")
        click("월간 전체 이용권")
        waitPage("paywall")
        assertEquals(1, billing.purchases)
        assertEquals(BillingStatus.PENDING, billing.state.value.status)
        assertEquals(0, media.cameraOpened)
        compose.runOnIdle { billing.publishVerified(true) }
        waitText("카메라로 계속하기")
        compose.onNodeWithTag("page-paywall").assertExists()
        assertEquals(0, media.cameraOpened)
        click("카메라로 계속하기")
        waitPage("camera")
        assertEquals(1, media.cameraOpened)
    }

    @Test fun restoreFromGuideKeepsOriginThroughStateRestoration() {
        start()
        compose.onNodeWithText("돌봄").performClick()
        click("구매 복원 · 이용권 관리")
        waitPage("paywall")
        assertEquals(1, billing.restores)
        compose.runOnIdle { billing.publishVerified(true) }
        waitText("원래 화면으로 돌아가기")
        restoration.emulateSavedInstanceStateRestore()
        waitPage("paywall")
        click("원래 화면으로 돌아가기")
        waitPage("guide")
        assertEquals(0, media.cameraOpened + media.videoOpened)
    }

    @Test fun cameraExpiryDisposesAndRejectsOldResultEvenAfterRenewal() {
        start(paid = true)
        click("카메라로 살펴보기")
        waitPage("camera")
        val late = media.cameraResult!!
        compose.runOnIdle { billing.publishVerified(false) }
        waitPage("paywall")
        assertEquals(1, media.cameraClosed)
        compose.runOnIdle { late(CameraObservation(true, false, null)) }
        compose.onNodeWithTag("page-paywall").assertExists()
        compose.runOnIdle { billing.publishVerified(true) }
        click("카메라로 계속하기")
        waitPage("camera")
        compose.runOnIdle { late(CameraObservation(true, false, null)) }
        compose.onNodeWithTag("page-camera").assertExists()
        compose.onNodeWithTag("fake-camera-result").performClick()
        waitPage("translate")
    }

    @Test fun videoExpiryDisposesAndRejectsDelayedAnalysisReply() {
        start(paid = true)
        click("저장된 영상 가져오기")
        waitPage("video_import")
        val late = media.videoResult!!
        compose.runOnIdle { billing.publishVerified(false) }
        waitPage("paywall")
        assertEquals(1, media.videoClosed)
        compose.runOnIdle { late(ImportedVideoObservation("옹알이", null, true, false, "테스트 관찰")) }
        compose.onNodeWithTag("page-paywall").assertExists()
        click("닫기 · 원래 화면으로")
        waitPage("home")
    }

    @Test fun restoredProtectedRouteChecksLiveGrantInsteadOfSavedPremiumDisplay() {
        start(paid = true)
        click("카메라로 살펴보기")
        waitPage("camera")
        // No StateFlow update: simulate a grant becoming invalid between UI updates.
        compose.runOnIdle { billing.verified = false }
        restoration.emulateSavedInstanceStateRestore()
        waitPage("paywall")
        assertEquals(1, media.cameraOpened)
        assertEquals(1, media.cameraClosed)
    }

    @Test fun expiredCameraDraftKeepsEditsAcrossPaywallRestoreAndSavesForFree() {
        start(paid = true)
        click("카메라로 살펴보기")
        waitPage("camera")
        compose.onNodeWithTag("fake-camera-result").performClick()
        waitPage("translate")
        click("놀이 중")
        waitText("내가 직접 고치기")
        click("내가 직접 고치기")
        val edited = "나도 같이 놀고 싶어. 잠깐 내 손을 잡아 줄래?"
        compose.onNodeWithTag("draft-phrase-editor").performTextReplacement(edited)
        compose.runOnIdle { billing.publishVerified(false) }
        compose.onNodeWithTag("page-translate").assertExists()
        click("← 처음부터 다시 입력하기")
        waitPage("paywall")
        restoration.emulateSavedInstanceStateRestore()
        waitPage("paywall")
        click("닫기 · 원래 화면으로")
        waitPage("translate")
        compose.onNodeWithTag("draft-phrase-editor").assertTextContains(edited)
        click("← 처음부터 다시 입력하기")
        waitPage("paywall")
        click("만들던 문장 무료로 계속하기")
        waitPage("translate")
        waitText("이 문장 저장하기")
        compose.onNodeWithText("이 문장 저장하기").performClick()
        waitPage("timeline")
        assertEquals(edited, runBlocking { store.snapshot().observations.single().phrase })
    }

    @Test fun albumPreviewIsFreeAndSelectionSurvivesContextualPaywall() {
        start(seed = true)
        compose.onNodeWithText("아기말북").performClick()
        waitPage("album")
        click("살구빛 기억")
        click("전체 이용권 보기")
        waitPage("paywall")
        compose.onNodeWithText("쌓인 아기말을 한 권으로").assertExists()
        click("닫기 · 원래 화면으로")
        waitPage("album")
        compose.onNodeWithText("살구빛 기억").assertIsSelected()
        assertEquals(0, billing.purchases)
    }

    private class FakeBilling : AccessBilling {
        var verified = false
        var purchases = 0
        var restores = 0
        private val mutable = MutableStateFlow(BillingUiState(
            options = listOf(BillingOption(BillingPlan.MONTHLY, "test_product", "monthly", "TEST 4,900", "P1M", "AUTO_RENEWING")),
            status = BillingStatus.READY))
        override val state: StateFlow<BillingUiState> = mutable
        override fun hasCurrentEntitlement(): Boolean {
            if (mutable.value.isPremium != verified) mutable.value = mutable.value.copy(isPremium = verified)
            return verified
        }
        fun publishVerified(value: Boolean) {
            verified = value
            mutable.value = mutable.value.copy(isPremium = value, status = if (value) BillingStatus.PREMIUM else BillingStatus.READY)
        }
        override fun refresh() = Unit
        override fun restore() { restores++ }
        override fun purchase(activity: Activity, plan: BillingPlan) {
            purchases++
            mutable.value = mutable.value.copy(status = BillingStatus.PENDING, isPremium = false)
        }
    }

    private class FakeMedia : MediaScreens {
        var cameraOpened = 0
        var cameraClosed = 0
        var videoOpened = 0
        var videoClosed = 0
        var cameraResult: ((CameraObservation) -> Unit)? = null
        var videoResult: ((ImportedVideoObservation) -> Unit)? = null
        @Composable override fun Camera(authorize: () -> Boolean, onBack: () -> Unit, onTranslate: (CameraObservation) -> Unit) {
            SideEffect { cameraResult = onTranslate }
            DisposableEffect(Unit) { cameraOpened++; onDispose { cameraClosed++ } }
            Button(onClick = { onTranslate(CameraObservation(true, false, null)) }, modifier = Modifier.testTag("fake-camera-result")) {
                Text("테스트 장면 완료")
            }
        }
        @Composable override fun Video(authorize: () -> Boolean, onBack: () -> Unit, onTranslate: (ImportedVideoObservation) -> Unit) {
            SideEffect { videoResult = onTranslate }
            DisposableEffect(Unit) { videoOpened++; onDispose { videoClosed++ } }
            Text("테스트 영상 분석 중")
        }
    }
}
