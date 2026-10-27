package com.moa.babylanguage

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AccessCodeUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun codeIsMaskedNotSavedAndRegistrationIsExplicit() {
        val restore = StateRestorationTester(compose)
        var submitted: String? = null
        val code = "synthetic-access-ui-only-code-0123456789"
        restore.setContent { MaterialTheme { AccessCodeSection(BillingUiState(codeStatus = ReviewAccessStatus.INACTIVE), { submitted = it }, {}) } }
        compose.onNodeWithTag("access-code-input").assertDoesNotExist()
        compose.onNodeWithText("이용 코드 입력").performClick()
        compose.onNodeWithText("이용 코드 등록").assertIsNotEnabled()
        compose.onNodeWithTag("access-code-input").performTextInput(code)
        compose.onNodeWithTag("access-code-input").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
        compose.runOnIdle { assertNull(submitted) }
        compose.onNodeWithText("이용 코드 등록").performClick()
        compose.runOnIdle { assertEquals(code, submitted) }
        restore.emulateSavedInstanceStateRestore()
        assertEquals("", compose.onNodeWithTag("access-code-input").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
    }
    @Test fun discountShowsRenewalWithoutClaimingFullAccess() {
        compose.setContent { MaterialTheme { AccessCodeSection(BillingUiState(codeKind = AccessCodeKind.LAUNCH_OFFER, codeStatus = ReviewAccessStatus.ACTIVE), {}, {}) } }
        compose.onNodeWithText("출시 할인 코드가 등록됐어요").assertIsDisplayed()
        compose.onNodeWithText("신규 구독자 첫 2개월 월 2,450원, 이후 월 4,900원 자동 갱신. 아래 할인 상품과 Google Play 결제창에서 확인해 주세요.").assertIsDisplayed()
        compose.onNodeWithText("초대 이용권 · 기간 제한 없이 무료").assertDoesNotExist()
    }
}
