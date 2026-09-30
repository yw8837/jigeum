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
                         exporting: Boolean = false, onExportingChange: (Boolean) -> Unit = {}) {
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
                PrimaryButton(if (busy || exporting) "아기말북을 만들고 있어요" else if (premium) "이 달의 아기말북 PDF 만들기" else "아기말북 이용권 보기", onClick = {
                    if (!premium) onPremium()
                    else if (!busy && !exporting) {
                        busy = true
                        onExportingChange(true)
                        scope.launch {
                            try {
                                val file = MemoryArtifacts.album(context, records.toList(), selected, theme, includeNotes)
                                MemoryArtifacts.share(context, file, "application/pdf")
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
internal fun PremiumScreen(state: BillingUiState, onPurchase: (BillingPlan) -> Unit,
                           onRestore: () -> Unit, onRetry: () -> Unit, onBack: () -> Unit, onPrivacy: () -> Unit) {
    val context = LocalContext.current
    var managementError by rememberSaveable { mutableStateOf<String?>(null) }
    val processing = state.status in listOf(BillingStatus.CONNECTING, BillingStatus.PURCHASING, BillingStatus.VERIFYING)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            TextButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) { Text("닫기 · 기본 기능 계속 사용", color = Ink) }
            TopBar(if (state.isPremium) "아기말북을 함께 만들어요" else "쌓인 아기말을 한 권으로")
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text("월별 자동 구성 · 한글 PDF · 세 가지 표지\n기록은 휴대전화에만 남아요.", color = Muted, fontSize = 16.sp, lineHeight = 26.sp)
                Spacer(Modifier.height(24.dp))
                if (state.isPremium) {
                    Text("이용권을 확인했어요.", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp)); PrimaryButton("내 아기말북으로", onBack)
                } else if (state.options.isNotEmpty()) {
                    state.options.forEach { option ->
                        val monthly = option.plan == BillingPlan.MONTHLY
                        val enabled = state.status == BillingStatus.READY
                        OutlinedButton(onClick = { onPurchase(option.plan) }, enabled = enabled,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).heightIn(min = 86.dp),
                            shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Text(if (monthly) "월간 이용권" else "3개월 이용권", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                Text(option.formattedPrice, color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                Text(if (monthly) "매월 자동 갱신 · Play에서 해지 가능" else "1회 결제 · 자동 갱신 없음", color = Muted, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    Text(if (processing) "이용권 정보를 확인하고 있어요" else "지금은 이용권을 확인할 수 없어요", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Google Play 연결 또는 상품 확인이 되지 않으면 구매를 진행하지 않아요. 무료 기능은 그대로 쓸 수 있어요.",
                        color = Muted, modifier = Modifier.padding(top = 10.dp), lineHeight = 23.sp)
                }
                state.message?.let { Text(it, color = Muted, fontSize = 14.sp, lineHeight = 22.sp, modifier = Modifier.padding(vertical = 14.dp)) }
                if (processing) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.status in listOf(BillingStatus.ERROR, BillingStatus.UNAVAILABLE)) TextButton(onClick = onRetry) { Text("다시 확인하기") }
                Spacer(Modifier.height(16.dp))
                Text("결제는 Google Play 확인창에서 완료됩니다. 같은 Google Play 계정으로 구매를 복원할 수 있어요. 아기 기록은 별도의 백업 파일로 옮겨 주세요.", color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
                TextButton(onClick = onRestore, enabled = !processing) { Text("구매 복원", color = Ink) }
                TextButton(onClick = {
                    managementError = null
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?sku=jigeum_album&package=${context.packageName}"))) }
                        .onFailure { managementError = "구독 관리 화면을 열지 못했어요. Google Play 앱의 결제 및 정기 결제에서 확인해 주세요." }
                }) { Text("Google Play에서 구독 관리", color = Ink) }
                managementError?.let { Text(it, color = Muted, fontSize = 13.sp, lineHeight = 20.sp) }
                TextButton(onClick = onPrivacy) { Text("개인정보처리방침", color = Ink) }
                Text("기본 기능과 기존 기록은 결제·해지 여부와 관계없이 유지됩니다. 이용권은 결제한 Play 계정 기준이며 다른 가족 계정으로 자동 공유되지 않아요.", color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
            }
        }
    }
}
