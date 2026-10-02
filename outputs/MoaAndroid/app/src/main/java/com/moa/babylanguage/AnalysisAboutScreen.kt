package com.moa.babylanguage

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Public documentation only. Links never carry a recording, record, or purchase token. */
@Composable
internal fun AnalysisAboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var linkError by remember { mutableStateOf<String?>(null) }
    fun open(url: String) {
        linkError = null
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            .onFailure { linkError = "참고자료를 열지 못했어요. 인터넷 연결과 브라우저를 확인해 주세요." }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("analysis-about"), contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text("돌아가기", color = Ink) }
            Text("아기말을 만드는 근거", color = Ink, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
            Text("선택한 장면의 실제 소리를 먼저 살펴보고, 그 단서를 아이의 말처럼 풀어 써요.",
                color = Muted, fontSize = 16.sp, lineHeight = 25.sp, modifier = Modifier.padding(top = 12.dp))
        }
        item {
            AboutSection("이렇게 만들어요", "소리 구분 → 시간에 따른 흐름 → 아기말 표현\n" +
                "울음·웃음·옹알이 등의 단서와 소리의 변화를 살펴봐요. 단서가 약하거나 서로 맞지 않으면 특정 마음으로 채우지 않아요. 같은 구간에는 같은 표현을 만들며, 표현 후보는 다시 나올 수 있어요.")
        }
        item {
            AboutSection("앱에서 실제로 쓰는 도구", "YAMNet은 일반적인 소리 사건을 구분하는 모델이에요. MediaPipe AudioClassifier로 기기 안에서 실행해요.\n\n" +
                "MediaPipe Face Landmarker는 선택한 영상의 얼굴·입 열림 같은 형태를 관찰해요. 시각 관찰은 감정이나 욕구를 확정하지 않아요.")
            ReferenceLink("YAMNet 공식 자료", "https://github.com/tensorflow/models/tree/master/research/audioset/yamnet", ::open)
            ReferenceLink("MediaPipe 공식 자료", "https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker", ::open)
            ReferenceLink("기기 안 추론 방식 · LiteRT", "https://github.com/google-ai-edge/LiteRT", ::open)
        }
        item {
            AboutSection("연구를 참고하는 방식", "울음을 구분하는 것과 울음의 이유를 알아내는 것은 다른 과제예요. 공개 연구의 대상 연령·환경·평가 방법도 달라서, 논문의 성능 수치를 이 앱의 정확도로 옮기지 않아요.\n\n" +
                "voc2vec는 다음 모델을 비교할 연구 후보이고, librosa는 평가·전처리 도구 후보예요. 현재 앱에 별도의 번역 엔진으로 탑재한 것은 아니에요.")
            ReferenceLink("실제 환경의 울음 검출 연구 · Yao 외 (2022)", "https://pmc.ncbi.nlm.nih.gov/articles/PMC9609294/", ::open)
            ReferenceLink("울음의 정보와 한계 · Lockhart 외 (2023)", "https://www.nature.com/articles/s44271-023-00022-z", ::open)
            ReferenceLink("비언어 발성 연구 · voc2vec (2025)", "https://arxiv.org/abs/2502.16298", ::open)
            ReferenceLink("평가 도구 후보 · librosa", "https://github.com/librosa/librosa", ::open)
        }
        item {
            AboutSection("재미있게 보고, 맞는 순간은 간직해요", "아기말은 소리 단서에서 만든 표현 예시예요. 배고픔·졸림·건강 상태를 알아맞힌 결과나 의료·육아 상담으로 받아들이지 말아 주세요.\n\n" +
                "아기의 표정·몸짓·직전 상황과 함께 살펴보고, 잘 어울리는 순간은 원래 소리와 함께 저장해 보세요. 결과의 ‘어떤 소리를 들었나요?’에서 해당 구간의 관찰 근거를 볼 수 있어요.")
        }
        item {
            AboutSection("아기 자료는 기기 안에서", "분석을 위해 아기 소리·영상·기록을 서버에 보내지 않아요. 이용권 확인 통신은 별도이며, 아기 자료를 포함하지 않아요.")
            linkError?.let { Text(it, color = Ink, lineHeight = 22.sp, modifier = Modifier.padding(top = 12.dp)) }
            Text("참고자료 검토: 2026년 10월 · 개별 아기의 의도 해석 정확도는 검증되지 않았어요.",
                color = Muted, fontSize = 12.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun AboutSection(title: String, body: String) {
    Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    Text(body, color = Muted, fontSize = 14.sp, lineHeight = 23.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ReferenceLink(label: String, url: String, open: (String) -> Unit) {
    TextButton(onClick = { open(url) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(label, color = Ink, fontSize = 14.sp)
    }
}
