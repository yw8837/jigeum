package com.moa.babylanguage

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReviewAccessUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val code = "synthetic-review-code-for-tests-only-0123456789"

    @Test fun defaultEntryIsCollapsedAndUnconfiguredServiceCannotActivate() {
        var calls = 0
        compose.setContent { MaterialTheme { ReviewAccessSection(BillingUiState(), { calls++ }, {}, {}) } }
        compose.onNodeWithText("심사 접근 코드").assertDoesNotExist()
        compose.onNodeWithText("Play 심사 접근").performClick()
        compose.onNodeWithTag("review-code-input").assertIsNotEnabled()
        compose.onNodeWithText("심사 접근 확인").assertIsNotEnabled()
        compose.onNodeWithText("심사 접근 서비스가 아직 설정되지 않았어요.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun reviewCodeIsNeverRestoredFromUiSavedState() {
        val restoration = StateRestorationTester(compose)
        var submitted: String? = null
        restoration.setContent { MaterialTheme {
            ReviewAccessSection(BillingUiState(reviewStatus = ReviewAccessStatus.INACTIVE), { submitted = it }, {}, {})
        } }
        compose.onNodeWithText("Play 심사 접근").performClick()
        compose.onNodeWithTag("review-code-input").performTextInput(code)
        compose.onNodeWithText("심사 접근 확인").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(code, submitted) }
        restoration.emulateSavedInstanceStateRestore()
        val editable = compose.onNodeWithTag("review-code-input").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertEquals("", editable)
        compose.onNodeWithText("심사 접근 확인").assertIsNotEnabled()
    }

    @Test fun activeReviewShowsSourceAndCanBeRemovedWithoutPurchase() {
        val state = mutableStateOf(BillingUiState(isPremium = true, entitlementSource = EntitlementSource.PLAY_REVIEW,
            status = BillingStatus.PREMIUM, reviewStatus = ReviewAccessStatus.ACTIVE))
        var purchases = 0
        var continues = 0
        var removals = 0
        compose.setContent { MaterialTheme {
            PremiumScreen(state.value, { purchases++ }, {}, {}, AppFeature.CAMERA, null,
                { continues++ }, "무료로 계속하기", {}, {}, {}, {}, {}, {
                    removals++
                    state.value = BillingUiState(isPremium = false, entitlementSource = EntitlementSource.NONE,
                        status = BillingStatus.UNAVAILABLE, reviewStatus = ReviewAccessStatus.INACTIVE)
                })
        } }
        compose.onNodeWithText("Play 심사 이용권으로 확인 중이에요.").assertIsDisplayed()
        compose.onNodeWithText("카메라로 계속하기").performClick()
        compose.onNodeWithText("심사 접근 해제").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, removals); assertEquals(1, continues); assertEquals(0, purchases) }
        compose.onNodeWithText("Play 심사 이용권으로 확인 중이에요.").assertDoesNotExist()
    }
}
