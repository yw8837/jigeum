package com.moa.babylanguage

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

/** Owner report: no microphone input must never reach a generated baby phrase. */
class InputRequiredRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun noInputCannotCreateBabyPhrase() {
        val store = ObservationStore(compose.activity, "input-regression.db", "input-regression")
        val billing = object : AccessBilling {
            override val state = MutableStateFlow(BillingUiState())
            override fun hasCurrentEntitlement() = false
            override fun refresh() = Unit
            override fun restore() = Unit
            override fun purchase(activity: Activity, plan: BillingPlan) = Unit
        }
        compose.setContent { JigeumApp(compose.activity, store, billing) }
        compose.waitUntil(8_000) { compose.onAllNodesWithText("아기말 번역하기").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("아기말 번역하기").performScrollTo().performClick()
        compose.onNodeWithText("녹음 없이 직접 번역").assertDoesNotExist()
        compose.onNodeWithText("들은 것").assertDoesNotExist()
        compose.onNodeWithText("이 문장 저장하기").assertDoesNotExist()
        compose.onNodeWithText("아기의 순간을 담아요").assertExists()
        compose.onNodeWithTag("baby-phrase").assertDoesNotExist()
        runBlocking { store.close() }
        compose.activity.deleteDatabase("input-regression.db")
    }
}
