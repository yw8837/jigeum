# 기존 AST768 head — 첫 누락 단서 회복, 제품 승인 전

2026-10-07 KST. [사전 계약](20261007-ast-infant-head-contract.md), 실행 코드 `4f85c52`.

**기존 약국 누락 두 구간에서 처음으로 고정 support 기준을 통과했다.**
두 구간은 같은 원본에서 약2.827초 겹치는 개발 자료이며 독립 두 아기의 성공이 아니다.
새 head는 영아 발성의 존재 단서만 반환한다. 옹알이·울음·웃음의 세부 종류나 욕구를 판별하지 않는다.
현재 제품은 여전히0.5.18/code32이며 새 모델 적용·Android 검증·베타 배포는 없다.

## 실제 학습과 동결

기존 public35 MP3를 재사용했다. PCM16/16k/mono 첫20초를 비중첩10초 span 최대2개로 처리했다.
35clip/43span의 모든527 기존 logits는 원래 AST와 exact 동일/차이0이었다.
1초 미만 clip8개는 끝 waveform padding을 기록하고 실제 길이와 RMS를 보존했다.
특징파일130,402B, SHA `359083b64f4db2ac29059be5430e773ea99e8fc423952bbb0e5ecf1940e0c330`.

Train21/18source만 학습·선택에 사용했다. 영아는7clip/4source이며 모두 caption-weak다.
선택된 L2는 `.0001`; train-only18source macroNLL은 다음과 같다.

| L2 | source macroNLL |
|---:|---:|
| .0001 | .332219989207 |
| .001 | .344973392103 |
| .01 | .488711764764 |

768→1,769parameter/FP323076B. 저장 NPZ3270B,
SHA `81d7357c4c59f57222952a1542716f7d89e423d45f2987dd3917a3522bdfd510`.
학습·선택·head 저장 후 validation14를 읽었다. 기존 held 분할, 새9개 blocked 상태, 사용자 평가 전용을 유지했다.
FP64/저장FP32 최대 score 차이는 clip/span 모두 약1.0051e-7로5e-5 gate 안이다.
score≥.8/binary margin≥.4를 바꾸지 않았다. 이 score는 보정된 정답확률이 아니다.

## 누락과 반례의 첫 비교

| 조건 | 기존 YAMNet binary/MIL head | 새 AST768 head |
|---|---|---|
| 약국0–10초 | support0 | broad score .819784633, 기준 통과 |
| 같은 원본7.173–17.173초 | .975초 한 창 단서 | broad score .940027125, 기준 통과 |
| 다른 울음 대조8.790초 | 기존 binary/MIL 인접 단서 있음 | .641910019, **기준 미통과** |
| 영아 held18274/18276 | binary의창지원0 | clip .85/.95, 기준 통과 |
| 영아 held18277/18273 | binary의창지원0 | clip .79/.04, **기준 미통과** |
| held 성인/기타10개 | 비명384213 등 오인 잔존 | clip/span support0 |
| 기존 성인 웃음 cchenail/ripc | 앞선23head 연속 오인 | support0 |
| 대화·피아노·청소기·tone·noise·무음·rumble·DC | 앞선 대조 | support0 |
| public 짧은babble | 기존binary .89775초 단서 | .552458, **기준 미통과** |
| public babble_crib | 기존 단서 있음 | 두 span .982740/.989852, 기준 통과 |

YAMNet의975ms 창 지원과 AST의10초 문맥 지원은 동일 단위가 아니다. 새 head에 support_seconds를 만들지 않았다.
Validation 영아4개는 한15개월 출처다. negative10개도 caption/metadata이며 강한 사건 정답이 아니다.
기존 public33/51span은 개발·합성·중복 source 자료다.20개 조건에서 한 span 이상 기준을 통과했고13개는 보류했다.
연령 밖 자료·speaker 미확인 자료의 support를 대상6~18개월 정확도로 합산하지 않는다.

**AST만으로 교체하면 기존 울음/짧은 옹알이 지원을 잃을 수 있다.** 따라서 새 단서가 기존 엔진을 보완하는지
실제 통합 경로에서 별도 검사해야 한다. broad presence만으로 울음·웃음·옹알이·욕구를 확정하지 않는다.

## 실행 범위와 다음

| run | exit | 초 | peak committed bytes | 자손 |
|---|---:|---:|---:|---:|
| ast-features35-01 |0|78.474708|2,207,289,344|0|
| ast-head35-01 fit |0|1.179282|810,799,104|0|
| retained-ast-head01 |0|7.343227|2,193,641,472|0|
| public-ast-head01 |0|87.401161|2,196,828,160|0|

총97span에서 원본·표현 노출 session 각각 실행해194회 추론했다. 이는 PC의 두 session parity 연구 비용이며
휴대폰 한 번 분석의 시간·메모리·발열 측정이 아니다. Job180초/3GiB(features/eval),2GiB(fit), 종료·completion SHA 일치.
공개 음원 재취득·새 encoder 다운로드·파생 모델 파일 저장0이다.

다음은 동결된3076B head의 ONNX 그래프 통합과 기존527 logits/새 score 동등성, 새 source 평가다.
그 후 Android의 실제0–10/5–15/10–20초 중첩3창, 기존 승인 보존·성인/TV 반례·취소·메모리·ARM을 확인한다.
연구의 비중첩2span을 Android20초 전체 경로 검사로 치환하지 않는다.
유효 이득과 회귀·사용권·독립 리뷰를 만족한 변경만 비공개 beta로 적용한다. 전체goal은 active다.

로컬 증거는 ignored `work/infant-head-20261007/{ast-features35-01,ast-head35-01,retained-ast-head01,public-ast-head01}`.
원음·특징·head를 Git에 올리지 않았다. [연구 QA](../qa/ast-infant-head-20261007.md)와
[라벨 근거](20261007-infant-head-label-evidence.md)를 함께 따른다.
