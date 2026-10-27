package com.moa.babylanguage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun AccessCodeSection(state: BillingUiState, onActivate: (String) -> Unit, onDeactivate: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    val verifying = state.codeStatus == ReviewAccessStatus.VERIFYING
    val active = state.codeKind != AccessCodeKind.NONE
    LaunchedEffect(active) { if (active) code = "" }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(PaleSage).padding(18.dp)) {
        Text("이용 코드", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("초대받은 코드나 출시 할인 코드를 여기에 등록해요.", color = Muted, fontSize = 13.sp,
            lineHeight = 21.sp, modifier = Modifier.padding(top = 6.dp))
        if (state.codeKind == AccessCodeKind.INVITATION) {
            Text("초대 이용권 · 기간 제한 없이 무료", color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Text("자동 결제 없이 전체 기능을 이용해요. 마지막 확인 후 최대 24시간까지 오프라인으로 이용하며, 이후 또는 기기 재시작 후 인터넷으로 다시 확인해요.", color = Muted, fontSize = 13.sp)
            Text("기존 유료 구독은 코드 등록으로 해지되지 않아요. 자동 결제를 멈추려면 Google Play에서 구독을 해지해 주세요.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        } else if (state.codeKind == AccessCodeKind.LAUNCH_OFFER) {
            Text("출시 할인 코드가 등록됐어요", color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
            Text("신규 구독자 첫 2개월 월 2,450원, 이후 월 4,900원 자동 갱신. 아래 할인 상품과 Google Play 결제창에서 확인해 주세요.",
                color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
        }
        if (!active) TextButton(onClick = { expanded = !expanded; if (!expanded) code = "" }) {
            Text(if (expanded) "코드 입력 닫기" else "이용 코드 입력", color = Ink)
        }
        if (expanded && !active) {
            OutlinedTextField(value = code, onValueChange = { if (it.length <= 256) code = it },
                label = { Text("이용 코드") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                enabled = !verifying && state.codeStatus != ReviewAccessStatus.UNAVAILABLE,
                modifier = Modifier.fillMaxWidth().testTag("access-code-input"))
            Text("할인 코드는 신규 구독자용이며 처음 등록한 기기에 연결돼요. 재설치·기기 변경 전 등록 상태를 확인해 주세요. 코드 확인에는 인터넷이 필요하며 코드와 무작위 설치 식별자만 전송해요.",
                color = Muted, fontSize = 12.sp, lineHeight = 19.sp, modifier = Modifier.padding(vertical = 8.dp))
            OutlinedButton(onClick = { onActivate(code.trim()) },
                enabled = !verifying && state.codeStatus != ReviewAccessStatus.UNAVAILABLE && validReviewCode(code.trim()),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (verifying) "코드 확인 중" else "이용 코드 등록", color = Ink)
            }
        }
        state.codeMessage?.let { Text(it, color = Muted, fontSize = 13.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 8.dp)) }
        if (active) TextButton(onClick = onDeactivate) { Text("이 기기에서 코드 해제", color = Ink) }
    }
}
