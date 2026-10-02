package com.moa.babylanguage

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.util.UUID

/** Navigation, reach, and export choice tests: real app composition, no Play transaction. */
class UiFeedbackTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var store: ObservationStore
    private val database = "ux-feedback-${UUID.randomUUID()}.db"
    private val visible = mutableStateOf(true)
    private val billing = StubBilling()
    private var started = false

    @Before fun setup() { store = ObservationStore(compose.activity, database) }
    @After fun cleanup() {
        if (started) { compose.runOnIdle { visible.value = false }; compose.waitForIdle() }
        runBlocking { store.close() }; compose.activity.deleteDatabase(database)
    }
    private fun start(preview: Boolean = false) {
        started = true
        compose.setContent { if (visible.value) JigeumApp(compose.activity, store, billing, testDistribution = preview) }
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("page-home").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        if (!node.isDisplayed()) node.performScrollTo()
        node.performClick()
    }
    @Test fun primaryActionStaysNearBottomWhenRecentRecordsScroll() {
        val now = System.currentTimeMillis()
        runBlocking { repeat(8) { store.save(Observation(now - it, "옹알이", "0:00–0:10", "", "함께 남긴 작은 순간 $it")) } }
        start()
        val page = compose.onNodeWithTag("page-home").fetchSemanticsNode().boundsInRoot
        val before = compose.onNodeWithText("아기말 번역하기").fetchSemanticsNode().boundsInRoot
        assertTrue(before.center.y > page.top + page.height * .65f)
        compose.onNodeWithText("최근 번역").performTouchInput { swipeUp() }
        val after = compose.onNodeWithText("아기말 번역하기").fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 1f)
        compose.onNodeWithText("아기말 번역하기").assertIsDisplayed()
    }
    @Test fun homePassExplainsUnavailablePurchaseAndReturnsToHome() {
        start(); compose.onNodeWithTag("home-pass").performClick()
        compose.onNodeWithText("현재 결제를 준비 중이에요").performScrollTo().assertIsDisplayed()
        assertEquals(0, billing.purchases)
        click("닫기 · 원래 화면으로")
        compose.onNodeWithText("아기말 번역하기").assertIsDisplayed()
    }
    @Test fun careHasVisiblePassAndRestoreAndReturnsToCare() {
        start(); click("돌봄")
        compose.onNodeWithText("무료로 사용 중").assertIsDisplayed()
        click("전체 이용권 · 결제"); click("닫기 · 원래 화면으로")
        compose.onNodeWithText("돌봄 · 내 정보").assertIsDisplayed()
        click("구매 복원")
        assertEquals(1, billing.restores)
        click("닫기 · 원래 화면으로")
        compose.onNodeWithText("내 기록 관리").assertExists()
    }
    @Test fun aboutReturnsToItsOriginAndDoesNotImpersonateAResearchModel() {
        start(); click("어떤 근거로 아기말을 만들까요?")
        compose.onNodeWithTag("analysis-about").assertIsDisplayed()
        compose.onNodeWithText("앱에서 실제로 쓰는 도구").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("연구를 참고하는 방식").performScrollTo().assertIsDisplayed()
        click("돌아가기"); click("돌봄"); click("분석 원리 · 참고자료 보기")
        click("돌아가기"); compose.onNodeWithText("돌봄 · 내 정보").assertIsDisplayed()
        assertEquals(0, billing.purchases)
    }
    @Test fun previewShowsFullLimitsWithoutPretendingThereWasAPurchase() {
        start(preview = true)
        compose.onNodeWithTag("home-access-summary").assertTextEquals("전체 기능 테스트 · 2분 촬영 · 20초 분석")
        assertFalse(billing.hasCurrentEntitlement())
        compose.onNodeWithTag("home-pass").performClick()
        compose.onNodeWithText("이 설치본은 전체 기능 테스트용이에요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("현재 결제를 준비 중이에요").assertDoesNotExist()
        assertEquals(0, billing.purchases)
    }
    @Test fun coverGridHasTwoRowsAndSelectionSurvivesRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { AlbumScreen(emptyList(), false, {}, {}) }
        val sage = compose.onNodeWithTag("album-cover-sage").performScrollTo().fetchSemanticsNode().boundsInRoot
        val apricot = compose.onNodeWithTag("album-cover-apricot").fetchSemanticsNode().boundsInRoot
        assertEquals(sage.top, apricot.top, 1f); assertTrue(apricot.left > sage.left)
        val blue = compose.onNodeWithTag("album-cover-blue").performScrollTo().fetchSemanticsNode().boundsInRoot
        val rose = compose.onNodeWithTag("album-cover-rose").fetchSemanticsNode().boundsInRoot
        assertEquals(blue.top, rose.top, 1f); assertTrue(rose.left > blue.left)
        compose.onNodeWithTag("album-cover-rose").performClick().assertIsSelected()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("album-cover-rose").performScrollTo().assertIsSelected()
        compose.onNodeWithText("첫 문장을 남기면 이곳에 모여요.").assertExists()
    }
    private class StubBilling : AccessBilling {
        override val state = MutableStateFlow(BillingUiState(status = BillingStatus.UNAVAILABLE, isPremium = false))
        var restores = 0
        var purchases = 0
        override fun hasCurrentEntitlement() = false
        override fun refresh() = Unit
        override fun restore() { restores++ }
        override fun purchase(activity: Activity, plan: BillingPlan) { purchases++ }
    }
}
