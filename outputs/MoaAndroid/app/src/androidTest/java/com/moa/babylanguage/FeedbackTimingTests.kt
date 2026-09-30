package com.moa.babylanguage

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
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

/** Actual app routes and store; permission replies exist only in androidTest. */
class FeedbackTimingTests {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val id = "feedback-${UUID.randomUUID()}"
    private lateinit var store: ObservationStore
    private lateinit var phrases: PhraseEngine
    private val visible = mutableStateOf(true)
    private var started = false
    private val expiredExportMessage = "기록이 변경되어 내보내기를 중단했어요. 다시 선택해 주세요."

    @Before fun setup() {
        store = ObservationStore(compose.activity, "$id.db", "$id-legacy")
        phrases = PhraseEngine(compose.activity, "$id-phrases")
        runBlocking { store.save(Observation(System.currentTimeMillis(), "옹알이", "놀이 중", "", "같이 놀아 줄래?")) }
    }

    @After fun cleanup() {
        val context = compose.activity.applicationContext
        if (started) { compose.runOnIdle { visible.value = false }; compose.waitForIdle() }
        runBlocking { store.close() }
        context.deleteDatabase("$id.db")
        for (name in listOf("$id-legacy", "$id-phrases")) context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun start() {
        started = true
        compose.setContent { if (visible.value) JigeumApp(compose.activity, store, PaidBilling(), phrases) }
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("page-home").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun latestExportFailureIsVisibleWhileDeleteConfirmationWouldStillBeShowing() {
        start()
        compose.onNodeWithText("아기말북").performClick()
        val oldExport = compose.onNodeWithText("이 달의 아기말북 PDF 만들기").performScrollTo()
            .fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        compose.onNodeWithText("돌봄").performClick()
        compose.onNodeWithText("모든 기록 삭제").performScrollTo().performClick()
        compose.onNodeWithText("다시 누르면 모든 기록을 삭제해요").performScrollTo().performClick()
        // This deliberately overlaps two real feedback events. The newest failure
        // must not wait for a four-second confirmation (or multiple queued copies).
        compose.waitUntil(8_000) { compose.onAllNodesWithText("기록과 월령을 지웠어요.").fetchSemanticsNodes().isNotEmpty() }
        compose.runOnIdle { oldExport.invoke(); oldExport.invoke(); oldExport.invoke() }
        compose.waitUntil(2_000) { compose.onAllNodesWithText(expiredExportMessage).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(runBlocking { store.snapshot().observations.isEmpty() })
    }

    private class PaidBilling : AccessBilling {
        override val state: StateFlow<BillingUiState> = MutableStateFlow(BillingUiState(isPremium = true, status = BillingStatus.PREMIUM))
        override fun hasCurrentEntitlement() = true
        override fun refresh() = Unit
        override fun restore() = Unit
        override fun purchase(activity: Activity, plan: BillingPlan) = Unit
    }
}
