package com.moa.babylanguage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Visible reviewer entry. No secret tap, local Boolean, saved-state code or purchase impersonation. */
@Composable
internal fun ReviewAccessSection(
    state: BillingUiState,
    onActivate: (String) -> Unit,
    onDeactivate: () -> Unit,
    onRetry: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Credentials never go to rememberSaveable, navigation state, logs, or generated records.
    var code by remember { mutableStateOf("") }
    val active = state.reviewStatus == ReviewAccessStatus.ACTIVE || state.entitlementSource == EntitlementSource.PLAY_REVIEW
    val verifying = state.reviewStatus == ReviewAccessStatus.VERIFYING
    LaunchedEffect(active) { if (active) code = "" }
    TextButton(onClick = { expanded = !expanded; if (!expanded) code = "" }, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(if (expanded) "Play 심사 접근 닫기" else "Play 심사 접근", color = Muted, fontSize = 13.sp)
    }
    if (expanded || active) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Play 심사자 전체 접근", color = Ink, fontWeight = FontWeight.Bold)
            Text("Play Console의 앱 접근 안내에 제공된 코드로 확인해요. 결제 없이 카메라·영상·PDF를 포함한 모든 기능을 검토할 수 있어요.",
                color = Muted, fontSize = 13.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp))
            Text("코드 확인에는 인터넷이 필요해요. 접근 코드는 기기 키로 암호화하고 서명된 심사 이용권과 함께 저장해요. 아기 소리·영상·기록은 보내지 않아요.",
                color = Muted, fontSize = 12.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(12.dp))
            if (!active) {
                OutlinedTextField(value = code, onValueChange = { if (it.length <= 256) code = it },
                    label = { Text("심사 접근 코드") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                    enabled = !verifying && state.reviewStatus != ReviewAccessStatus.UNAVAILABLE,
                    modifier = Modifier.fillMaxWidth().testTag("review-code-input"))
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { onActivate(code) },
                    enabled = !verifying && state.reviewStatus != ReviewAccessStatus.UNAVAILABLE && validReviewCode(code.trim()),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (verifying) "심사 접근 확인 중" else "심사 접근 확인", color = Ink)
                }
            }
            state.reviewMessage?.let { Text(it, color = Muted, fontSize = 13.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 10.dp)) }
            if (state.reviewStatus == ReviewAccessStatus.UNAVAILABLE) Text("심사 접근 서비스가 아직 설정되지 않았어요.",
                color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
            if (active) TextButton(onClick = onDeactivate) { Text("심사 접근 해제", color = Ink) }
            if (!verifying && state.reviewStatus == ReviewAccessStatus.ERROR) TextButton(onClick = onRetry) { Text("심사 이용권 다시 확인", color = Ink) }
        }
    }
}
