package com.moa.babylanguage

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@Composable
internal fun WeeklySummary(records: List<Observation>, onAlbum: () -> Unit) {
    val beginning = LocalDate.now().minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val now = System.currentTimeMillis()
    val week = records.filter { it.id in beginning..now }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)
        .clip(RoundedCornerShape(20.dp)).background(PaleSage).padding(20.dp)) {
        Text("최근 일주일의 작은 대화", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(if (week.isEmpty()) "기록이 생기면 여기서 함께 돌아봐요. 매일 남기지 않아도 괜찮아요."
            else "함께 남긴 문장 ${week.size}개 · 서로 다른 ${week.map { java.time.Instant.ofEpochMilli(it.id).atZone(ZoneId.systemDefault()).toLocalDate() }.distinct().size}일의 기억",
            color = Ink, fontSize = 14.sp, lineHeight = 22.sp)
        week.firstOrNull()?.let { Text("최근 남긴 말\n“${it.phrase}”", color = Muted,
            fontSize = 14.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 12.dp)) }
        TextButton(onClick = onAlbum) { Text("월별 아기말북 둘러보기", color = Ink) }
    }
}

@Composable
internal fun DataTransferPanel(store: ObservationStore, records: List<Observation>, ageBand: String,
                               onImported: (StoreSnapshot) -> Unit, onMessage: (String) -> Unit,
                               operationScope: CoroutineScope? = null) {
    val context = LocalContext.current
    val localScope = rememberCoroutineScope()
    val scope = operationScope ?: localScope
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    // Keep only the picker state in the saved bundle. Large record snapshots must not
    // enter savedInstanceState, and can be recovered from SQLite after recreation.
    var pickerPending by rememberSaveable { mutableStateOf(false) }
    fun write(uri: Uri?, csv: Boolean) {
        pickerPending = false
        if (uri == null || busy) return
        busy = true
        scope.launch {
            try {
                val snapshot = store.snapshot()
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri, "wt")
                        ?: error("선택한 위치에 파일을 만들지 못했어요.")
                    stream.use { if (csv) RecordTransfer.writeCsv(it, snapshot) else RecordTransfer.writeJson(it, snapshot) }
                }
                onMessage(if (csv) "표 파일로 내보냈어요." else "기록 백업을 저장했어요. 안전한 곳에 보관해 주세요.")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                onMessage("파일을 저장하지 못했어요. 저장 위치와 공간을 확인해 주세요.")
            } finally { busy = false }
        }
    }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { write(it, false) }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { write(it, true) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pickerPending = false
        if (uri != null && !busy) {
            busy = true
            scope.launch {
                try {
                    val existing = store.snapshot()
                    preview = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { RecordTransfer.parseImport(it, existing) }
                            ?: error("이 파일을 열지 못했어요.")
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    onMessage(if (e is IllegalArgumentException || e is StoreException)
                        e.message ?: "백업 형식을 확인해 주세요." else "백업 파일을 읽지 못했어요. JSON 백업 파일을 골라 주세요.")
                } finally { busy = false }
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text("기록을 안전하게 보관해요", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("앱 삭제나 휴대전화 분실 시 서버에서 복원할 수 없어요. 무료 백업 파일을 직접 보관해 주세요. 메모도 파일에 포함됩니다.",
            color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(enabled = !busy && !pickerPending && preview == null, onClick = {
            if (!busy && !pickerPending) {
                pickerPending = true
                try { exportJson.launch("아기말-백업-${LocalDate.now()}.json") }
                catch (_: Exception) { pickerPending = false; onMessage("파일 선택기를 열지 못했어요.") }
            }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("전체 기록 백업하기") }
        OutlinedButton(enabled = !busy && !pickerPending && preview == null, onClick = {
            if (!busy && !pickerPending) {
                pickerPending = true
                try { exportCsv.launch("아기말-기록-${LocalDate.now()}.csv") }
                catch (_: Exception) { pickerPending = false; onMessage("파일 선택기를 열지 못했어요.") }
            }
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("표로 내보내기 (CSV)") }
        OutlinedButton(enabled = !busy && !pickerPending && preview == null, onClick = {
            if (!busy && !pickerPending) {
                pickerPending = true
                try { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                catch (_: Exception) { pickerPending = false; onMessage("파일 선택기를 열지 못했어요.") }
            }
        },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("백업 파일 가져오기") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
    }
    preview?.let { imported ->
        AlertDialog(onDismissRequest = { if (!busy) preview = null },
            title = { Text("기록을 합칠까요?") },
            text = { Text("추가 ${imported.addedCount}개 · 같은 기록 ${imported.duplicateCount}개 · 날짜가 겹치는 다른 기록 ${imported.conflictCount}개\n\n기존 기록을 유지하면서 합칩니다. 같은 기록은 한 번만 남겨요. CSV는 가져오기용이 아니며 앱의 JSON 백업을 사용해 주세요.") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                if (busy) return@TextButton
                busy = true
                scope.launch {
                    try {
                        store.mergeImport(imported); onImported(store.snapshot()); preview = null
                        onMessage("백업의 기록을 합쳤어요.")
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        onMessage(if (e is StoreException) e.message ?: "합치지 못했어요. 기존 기록은 그대로 있어요."
                            else "합치지 못했어요. 기존 기록은 그대로 있어요.")
                    } finally { busy = false }
                }
            }) { Text("기록 합치기") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { preview = null }) { Text("취소") } })
    }
}

@Composable
internal fun AlbumScreen(records: List<Observation>, premium: Boolean, onPremium: () -> Unit,
                         onMessage: (String) -> Unit, operationScope: CoroutineScope? = null,
                         exporting: Boolean = false, onExportingChange: (Boolean) -> Unit = {},
                         authorizeExport: () -> Boolean = { false }) {
    val context = LocalContext.current
    val localScope = rememberCoroutineScope()
    val scope = operationScope ?: localScope
    val months = remember(records) { records.map { MemoryArtifacts.month(it.id) }.distinct().sortedDescending() }
    var chosen by rememberSaveable { mutableStateOf((months.firstOrNull() ?: YearMonth.now()).toString()) }
    var themeName by rememberSaveable { mutableStateOf(AlbumTheme.SAGE.name) }
    var includeNotes by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(months) {
        if (months.isNotEmpty() && chosen !in months.map { it.toString() }) chosen = months.first().toString()
    }
    val selected = YearMonth.parse(chosen)
    val monthRecords = records.filter { MemoryArtifacts.month(it.id) == selected }
    val theme = AlbumTheme.valueOf(themeName)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            TopBar("한 달의 말, 한 권의 기억")
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text("직접 고치고 남긴 아기말을 달마다 모아요. 미리보기는 무료예요.", color = Muted, fontSize = 15.sp, lineHeight = 23.sp)
                Spacer(Modifier.height(20.dp))
                if (months.isNotEmpty()) {
                    Text("어느 달을 담을까요?", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    ChoiceGrid(months.map { "${it.year}년 ${it.monthValue}월" }, "${selected.year}년 ${selected.monthValue}월") { label ->
                        chosen = months.first { "${it.year}년 ${it.monthValue}월" == label }.toString()
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(androidx.compose.ui.graphics.Color(theme.paper)).padding(24.dp)) {
                    Text("${selected.year}년 ${selected.monthValue}월", color = Muted, fontSize = 14.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("우리 아이의\n작은 이야기", color = Ink, fontSize = 30.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(20.dp))
                    Text(if (monthRecords.isEmpty()) "첫 문장을 남기면 이곳에 모여요." else "${monthRecords.size}개의 순간을 한 권에", color = Muted, fontSize = 14.sp)
                    monthRecords.take(2).forEach { record ->
                        Text("“${record.phrase}”", color = Ink, lineHeight = 24.sp,
                            modifier = Modifier.padding(top = 16.dp))
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("표지의 색을 골라요", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ChoiceGrid(AlbumTheme.entries.map { it.label }, theme.label) { label -> themeName = AlbumTheme.entries.first { it.label == label }.name }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = includeNotes,
                    role = androidx.compose.ui.semantics.Role.Checkbox, onValueChange = { includeNotes = it }),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = includeNotes, onCheckedChange = null)
                    Text("보호자 메모도 담기", color = Ink)
                }
                Text("PDF는 기기에서 만들어요. 만든 파일은 이용권이 끝나도 계속 볼 수 있어요.", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(16.dp))
                if (busy || exporting) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 12.dp))
                PrimaryButton(if (busy || exporting) "아기말북을 만들고 있어요" else if (premium) "이 달의 아기말북 PDF 만들기" else "전체 이용권 보기", onClick = export@{
                    if (!premium) onPremium()
                    else if (!busy && !exporting) {
                        if (!authorizeExport()) return@export
                        val generation = MemoryArtifacts.currentGeneration()
                        busy = true
                        onExportingChange(true)
                        scope.launch {
                            try {
                                val file = MemoryArtifacts.album(context, records.toList(), selected, theme, includeNotes, generation)
                                MemoryArtifacts.share(context, file, "application/pdf", generation)
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                onMessage(if (e is IllegalArgumentException) e.message ?: "기록을 확인해 주세요."
                                    else "파일을 만들지 못했어요. 저장 공간을 확인해 주세요.")
                            } finally { busy = false; onExportingChange(false) }
                        }
                    }
                }, enabled = !busy && !exporting && monthRecords.isNotEmpty() && monthRecords.size <= MemoryArtifacts.MAX_ALBUM_RECORDS)
                if (monthRecords.isEmpty()) Text("기록에서 첫 문장을 남긴 뒤 만들 수 있어요.", color = Muted, modifier = Modifier.padding(top = 8.dp))
                if (monthRecords.size > MemoryArtifacts.MAX_ALBUM_RECORDS) Text("한 권은 300개까지 담아요. 전체 기록은 무료 백업으로 보관해 주세요.", color = Muted)
                Spacer(Modifier.height(12.dp))
                Text("기본 문장 카드 공유와 기록 백업은 계속 무료예요.", color = Muted, fontSize = 13.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
internal fun PremiumScreen(
    state: BillingUiState,
    onPurchase: (BillingPlan) -> Unit,
    onRestore: () -> Unit,
    onRetry: () -> Unit,
    requestedFeature: AppFeature?,
    notice: String?,
    onContinue: () -> Unit,
    freeContinuationLabel: String,
    onFree: () -> Unit,
    onBack: () -> Unit,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit
) {
    val context = LocalContext.current
    var managementError by rememberSaveable { mutableStateOf<String?>(null) }
    val processing = state.status in listOf(BillingStatus.CONNECTING, BillingStatus.PURCHASING, BillingStatus.VERIFYING)
    val title = when (requestedFeature) {
        AppFeature.CAMERA -> "지금의 표정도 함께 살펴봐요"
        AppFeature.VIDEO_IMPORT -> "담아 둔 영상으로 이야기를 나눠요"
        AppFeature.PDF -> "쌓인 아기말을 한 권으로"
        else -> "전체 이용권을 확인해요"
    }
    val continueLabel = when (requestedFeature) {
        AppFeature.CAMERA -> "카메라로 계속하기"
        AppFeature.VIDEO_IMPORT -> "영상 가져오기로 계속하기"
        AppFeature.PDF -> "아기말북으로 계속하기"
        else -> "원래 화면으로 돌아가기"
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            TextButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp).heightIn(min = 48.dp)) {
                Text("닫기 · 원래 화면으로", color = Ink)
            }
            TopBar(title)
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text("하나의 전체 이용권", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("카메라 관찰 · 저장 영상 분석 · 월별 PDF\n세 가지 기능을 함께 사용해요.",
                    color = Muted, fontSize = 16.sp, lineHeight = 26.sp, modifier = Modifier.padding(top = 10.dp))
                if (!state.isPremium && notice != null) Text(notice, color = Muted,
                    lineHeight = 22.sp, modifier = Modifier.padding(top = 14.dp))
                Spacer(Modifier.height(18.dp))
                if (state.isPremium) {
                    Text("전체 이용권을 확인했어요.", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(continueLabel, onContinue)
                }
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(PaleSage).padding(18.dp)) {
                    Text("이용권으로 더할 수 있는 것", color = Ink, fontWeight = FontWeight.Bold)
                    Text("카메라로 지금의 얼굴과 움직임 살펴보기\n30초·50MB 이하의 저장 영상 살펴보기\n세 가지 표지로 월별 아기말북 PDF 만들기",
                        color = Ink, lineHeight = 25.sp, modifier = Modifier.padding(top = 10.dp))
                    Text("입력과 기록 방법을 넓혀요. 무료와 유료의 문장 품질은 같고, 아기의 마음을 더 정확히 판정하는 등급은 아니에요.",
                        color = Muted, fontSize = 13.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 12.dp))
                }
                Spacer(Modifier.height(22.dp))
                if (!state.isPremium && state.options.isNotEmpty()) {
                    state.options.forEach { option ->
                        val monthly = option.plan == BillingPlan.MONTHLY
                        OutlinedButton(onClick = { onPurchase(option.plan) }, enabled = state.status == BillingStatus.READY,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).heightIn(min = 86.dp),
                            shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Text(if (monthly) "월간 전체 이용권" else "3개월 전체 이용권", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Text(option.formattedPrice, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                Text(if (monthly) "매월 자동 갱신 · Play에서 해지 가능" else "3개월 1회 결제 · 자동 갱신 없음",
                                    color = Muted, fontSize = 13.sp)
                            }
                        }
                    }
                } else if (!state.isPremium) {
                    Text(if (processing) "이용권 정보를 확인하고 있어요" else "지금은 이용권을 확인할 수 없어요",
                        color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("Google Play 연결과 상품 정보가 확인되면 실제 가격을 보여 드려요. 무료 기능은 지금 사용할 수 있어요.",
                        color = Muted, modifier = Modifier.padding(top = 10.dp), lineHeight = 23.sp)
                }
                state.message?.let { Text(it, color = Muted, fontSize = 14.sp, lineHeight = 22.sp, modifier = Modifier.padding(vertical = 14.dp)) }
                if (processing) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.status in listOf(BillingStatus.ERROR, BillingStatus.UNAVAILABLE)) TextButton(onClick = onRetry) { Text("다시 확인하기") }
                Spacer(Modifier.height(20.dp))
                Text("계속 무료로 쓸 수 있어요", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("소리 녹음·직접 입력·문장 수정과 저장·기록 보기·PNG 공유·JSON 백업과 복원·주간 요약은 계속 무료예요. 이미 만든 문장과 기록도 유지돼요.",
                    color = Muted, lineHeight = 23.sp, modifier = Modifier.padding(vertical = 10.dp))
                SecondaryButton(freeContinuationLabel, onFree)
                Spacer(Modifier.height(20.dp))
                Text("결제는 Google Play 확인창에서 완료돼요. 같은 Play 계정으로 구매를 복원할 수 있어요. 기록 복원에는 직접 보관한 JSON 백업이 필요해요.",
                    color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
                TextButton(onClick = onRestore, enabled = !processing) { Text("구매 복원", color = Ink) }
                TextButton(onClick = {
                    managementError = null
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/account/subscriptions?sku=${PurchaseVerifier.PRODUCT_ID}&package=${context.packageName}"))) }
                        .onFailure { managementError = "구독 관리 화면을 열지 못했어요. Google Play 앱의 결제 및 정기 결제에서 확인해 주세요." }
                }) { Text("Google Play에서 구독 관리", color = Ink) }
                managementError?.let { Text(it, color = Muted, fontSize = 13.sp, lineHeight = 20.sp) }
                TextButton(onClick = onTerms) { Text("이용 조건", color = Ink) }
                TextButton(onClick = onPrivacy) { Text("개인정보처리방침", color = Ink) }
                Text("이용권은 결제한 Play 계정 기준이에요. 다른 가족 계정으로 자동 공유되지 않아요. 앱 삭제나 기록 삭제로 구독이 해지되지는 않아요.",
                    color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
internal fun ServiceTermsScreen(onBack: () -> Unit) {
    val sections = listOf(
        "표현 예시를 함께 만들어요" to "6~18개월 아기의 성인 보호자를 위한 서비스예요. 문장은 소리와 상황을 바탕으로 보호자가 확인하는 예시이며, 아기의 의도·감정·건강을 판정하거나 의료 진단을 하지 않아요. 표현 후보는 유한하므로 다시 나올 수 있어요.",
        "무료 기본과 전체 이용권" to "녹음·직접 입력·문장 수정·저장·기록·PNG 공유·JSON 백업과 복원·CSV 내보내기·주간 요약·앨범 미리보기는 무료예요. 전체 이용권은 카메라 관찰·저장 영상 분석·월별 PDF 생성을 함께 제공해요. 기록은 최대 10,000건, 영상은 30초·50MB, PDF는 한 권에 300개 기록·300쪽까지이며 추가 결제로 늘리는 한도가 아니에요.",
        "결제와 해지" to "가격과 기간은 Google Play 상품 화면과 결제 확인창에 표시된 내용을 따릅니다. 월간은 매월 자동 갱신하며 Play에서 해지할 수 있어요. 3개월은 선불 1회 결제이며 자동 갱신하지 않아요. 앱 삭제·기록 삭제는 구독 해지가 아닙니다. 환불·청약철회는 적용 법령과 Google Play 절차를 따르며 법정 권리를 제한하지 않아요.",
        "구매 복원과 기록 복원" to "앱 회원가입 없이 같은 Play 계정의 유효한 구매를 다시 확인할 수 있어요. 다른 가족 계정에 권한이 자동 공유되지는 않아요. 아기 기록은 기기에만 저장되며 구매 복원으로 되살아나지 않아요. 무료 JSON 백업을 안전하게 보관해 주세요.",
        "이용권 확인이 필요할 때" to "유효한 서명 이용권은 최대 24시간 또는 구독 만료 중 빠른 시각까지 사용할 수 있어요. 휴대전화를 재부팅하면 온라인에서 이용권을 다시 확인해야 해요. 오프라인에서는 새 환불을 즉시 알 수 없지만, 명시적인 권한 철회를 확인하면 새 유료 작업을 막아요. 카메라·영상 관찰은 중지하고, 이미 만든 문장의 무료 편집·저장과 기존 기록은 유지해요. 유효할 때 시작한 PDF 생성은 마칠 수 있으며 직접 보관한 PDF는 계속 볼 수 있어요.",
        "보관과 문의" to "앱 삭제·기기 분실 시 서버에서 기록을 복구할 수 없어요. 직접 내보내거나 공유한 파일은 해당 저장 위치에서 관리·삭제해 주세요. 운영자 최용우 · 문의 bsfire1237@gmail.com. 공개 약관과 정책의 시행일은 정식 배포 전에 고지합니다."
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp)) {
        item {
            TextButton(onClick = onBack) { Text("돌아가기", color = Ink) }
            Text("서비스 이용 조건", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        sections.forEach { (title, body) -> item {
            Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp))
            Text(body, color = Muted, lineHeight = 24.sp, modifier = Modifier.padding(top = 8.dp))
        } }
    }
}
