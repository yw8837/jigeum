# CC0 영아 후보 2개: 사건 검수 준비 상태

확인일 **2026-10-07 KST**, 조회 시작 **2026-10-06 20:01:15 UTC /10-07 05:01:15 KST**. `agent-reach` SKILL 및 references/dev/search/web를 읽고 공식 Freesound HTML과 Jina Reader를 통한 공식 FAQ/약관/CC0 원문만 확인했다. 이 문서만 작성했다. 음원·preview body 다운로드·청취·추론·학습·calibration·ADB·Git·빌드·키·사용자 미디어 접근은 **0**이다.

## 1. 이번 판단

**정확한 파일2개의 공식 preview 경로와 CC0 표시는 살아 있다. 실제 자연 영아6–18개월 사건 검수 완료 자료는0개다.** 두 파일은 [앞선 후보 조사](20261007-next-infant-head-validation.md)의 기존 FSD50K eval 재확인이며 새 source 발견이 아니다. 둘 다 **`training_blocked=true`, `approved_training_label=null`**, eval 보류를 유지한다.

약국 사례의 generic recognition 보완을 평가할 별도 metadata 업로더 후보가 될 수 있지만, 현재 근거는 웃음/목욕 장면이다. **약국 옹알이 누락을 재현하는 새 자연 옹알이·보챔 자료를 확보한 상태는 아니다.** 모델 점수도 읽지 않았다. 현재 caption은 정확한 월령·발성 정답·가족 독립성이나 일반 정확도의 증거가 아니다.

## 2. current 원문으로 다시 확인한 사항

| 항목 | amccoy711 /388407 | carameans /135185 |
|---|---|---|
| 공식 페이지·title |[Baby Giggles.wav](https://freesound.org/people/amccoy711/sounds/388407/) |[Twins Bathtime, Audio.wav](https://freesound.org/people/carameans/sounds/135185/) |
| source identity |`FSD50K:eval:amccoy711`; preview owner7293523 |`FSD50K:eval:carameans`; preview owner2456349 |
| 현재 설명 |자신의15개월 아들이 giggling하는 iPhone 녹음이라고 설명 |한 살 쌍둥이 딸들의 목욕이라고 설명 |
| target age 근거 |15개월 caption. 독립 연령 확인 없음 |one-year-old→12개월 caption. 독립 연령 확인 없음 |
| 발성 근거의 강도 |웃음은 설명·title·tags 및 기존 FSD `Baby_laughter`의 근거. spontaneous/nonacted는 명시되지 않음 |웃음은 tags 및 기존 FSD `Baby_laughter` 근거. 설명 자체는 목욕만 말함. 발성 사건·자발성은 확정 불가 |
| 자연성·혼합 |자기 아들/iPhone 설명이 원천 단서. 실제 infant laugh·성인/말소리 겹침·편집 여부는 미청취 |생활 장면 단서지만 복수 아동/물소리/성인 겹침 여부는 미청취. 쌍둥이2명을 가족2개로 세지 않음 |
| 원음 format metadata |WAV10.913초,44.1kHz,16bit,mono,940.0KB |WAV21.988초,48kHz,24bit,**6channels**,18.1MB |
| 현재 권리·접근 |CC0 표시·license link. 원음 버튼은 `Login to download` |CC0 표시·license link. 원음 버튼은 `Login to download` |

format/duration/size는 페이지 metadata이며 실제 음원 header를 읽은 값이 아니다. **6채널 원음을 MP3 preview에서 독립 채널로 들을 수 있다고 가정하지 않는다.** preview의 duration·sample rate·channel/downmix·원음과의 내용 일치는 미확인이다. bath 혼합을 아기 단독 발성으로 승인하지 않는다. giggle 설명의 joy를 감정·요구 정답으로 가져오지 않는다.

현재 페이지 설명에는 두 파일 모두 별도 재배포 제한이나 외부 원작자 링크가 없다. 앞선 source 비교에서 실행train21/18source·held14/11source·extra9/3업로더와 기록된 이름/preview ID가 겹치지 않았다는 제한적 결과를 재사용한다. 이번에 가족/세션/재게시 관계를 추가 검증한 것은 아니다.

### 이번 HTML snapshot

| ID | HTTP·bytes | HTML SHA256 |
|---|---|---|
|388407|200 /45,028|fdb42c511179cb1396b7322b200dc1a198bea0e4b704c2d0e95b267a4a97816a|
|135185|200 /37,492|dd8d058ee0626cf7e0758c7344488716964bb3f3a30376802fdaacb28c7839b4|

HTML hash는 음원 hash가 아니다. 앞선 HTML hash와의 차이는 동적 페이지 변화일 수 있으며 음원 변경으로 단정하지 않는다.

## 3. preview를 통한 인간 검수 경로와 권리 조건

후속 검수 담당자는 정상 공식 페이지의 player로 시작할 수 있다. 페이지가 제공한 exact 경로는 다음과 같다. 이번에는 아래 URL에 GET/HEAD/재생을 하지 않았다.

- 388407: [공식 MP3 preview](https://cdn.freesound.org/previews/388/388407_7293523-lq.mp3)
- 135185: [공식 MP3 preview](https://cdn.freesound.org/previews/135/135185_2456349-lq.mp3)

**CC0의 저작권 허락 표시와 취득 경로는 확인됐지만, 실제 body 접근/완전 재생 성공은 미확인이다.** 원음은 로그인 버튼으로 구분된다. 향후 정상 player가 실패하거나 접근을 요구하면 실패를 기록하고 우회·계정/키 탐색을 하지 않는다. 실제 재생은 이번 작업의 금지 범위를 벗어나므로 별도 실행 담당자가 수행한다.

[Freesound 공식 FAQ](https://freesound.org/help/faq/#licenses)는 sound별 CC 조건 준수를 요구하고 CC0의 폭넓은 이용을 설명한다. user upload의 불법/권리 침해 가능성도 인정한다. [공식 약관](https://freesound.org/help/tos_web/)은 uploader가 필요한 권리를 보유했다고 보증하도록 요구하며 sound/metadata의 outbound 권리는 각 CC 표시를 따른다. 웹 서비스 사용 조건과 파일 권리는 구분해서 읽어야 한다. 약관의 계정/웹 portal 상업 사용 제한 문구를 파일 CC0 허락이 무조건 해제한다고 보고하지 않는다.

[CC0 공식 legal code](https://creativecommons.org/publicdomain/zero/1.0/legalcode.en)는 affirmer가 보유한 권리의 포기/대체 허락이며 제3자 권리·필요 동의의 정리까지 보장하지 않는다. 따라서 CC0 표시를 보호자 신원/모든 아동 권리/원작자 lineage의 독립 검증으로 표현하지 않는다. 공개된 소량 preview를 통한 로컬 사건 관찰의 경로만 고정하며 배포·재게시·음성복제·학습 승인으로 확장하지 않는다. FAQ의 Gen AI preference는 생성 모델 학습 관련 별도 조건이고 이번 인간 검수와 다르다. 해당 preference를 조회하거나 학습 허락을 추가 확인하지 않았다.

## 4. 점수를 보지 않는 사건 검수 절차

다음은 실행 가능한 후속 절차이며 검수 완료 보고가 아니다.

1. **388407과135185 전체를 사전 고정**한다. 모델의 결과에 따라 파일·좋은 부분만 선택하지 않는다. 가능한2명의 리뷰어에게 score/승인 여부를 숨기고 임의 review ID를 부여한다. 가능하면 caption의 laugh/joy 태그도 숨긴다. 공식 페이지 직접 열람으로 caption이 노출되면 `caption_exposure=true`를 기록하고 caption 비공개 검수로 보고하지 않는다. 월령 caption은 검수 후 provenance에 연결한다.
2. 정상 preview를 전체 청취한 뒤 실제 재생된 duration을 기록한다. 파일 경로·원음/preview 구분·실제 취득 bytes/hash가 있으면 별도 기록한다. 플레이어 시각으로 표시한 onset/offset는 **수동 추정**임을 남기고 frame/sample 정밀도로 보고하지 않는다. 재생 실패·일부만 재생·preview 내용 불명도 기록한다.
3. 들리는 사건을 **babble / laugh / cry / fuss / speech / other / uncertain**로 기록하고 `mix`는 별도 flag로 둔다. `infant_like`, `adult_overlap`, `multiple_children_possible`, `water_or_household_noise`, `playback_or_edit_uncertain`를 보조 관찰로 남긴다. 청취로 정확한 나이·개별 아동을 증명할 수 없으므로 `age_status=caption_only`, `speaker_identity=unverified`를 유지한다.
4. laugh는 실제 웃음 발성 구간만 표시한다. babble은 사람이 관찰한 비언어 발성/음절형태 단서로만 분류하고 나이·욕구를 추론하지 않는다. 짧은 cry를 FUS로 자동 치환하지 않는다. cry/fuss/연기·재생을 구분할 수 없거나 물소리/성인 발성이 겹치면 `uncertain/mix`로 둔다. bath preview의 채널 혼합을 개별 아동 정답으로 나누지 않는다.
5. 리뷰어 각자의 label/시작·끝/불확실성/짧은 관찰 설명을 **모델 실행 전에 고정**한다. 불일치 구간은 합의와 원판독을 함께 남긴다. 단일 리뷰어라면 독립 합의 검수로 표현하지 않는다. 결과 JSON/CSV에는 `approved_training_label=null`, `training_blocked=true`, `evaluation_role=existing_eval_pending_review`를 유지한다. 이번에 이 파일들을 만들지는 않았다.
6. 부모 작업자가 별도 평가를 진행할 때 현재 동결 head/기존 gate를 유지하고 review와 모델 결과를 사후 비교한다. 두 파일의 support/miss 및 mix/uncertain를 모두 보고한다. 웃음 결과를 약국 옹알이 회복·6–18개월 일반 정확도·의도 번역 성공으로 확장하지 않는다. 원음 권리/재생 확인이 부족하면 `not_acquired/not_reviewed` 상태를 유지한다.

권장 사건 기록 필드: `review_id`, `source_clip_id`, `material_kind`, `actual_played_duration_sec`, `event_start_sec_estimate`, `event_end_sec_estimate`, `heard_label`, `mix_flags`, `reviewer_id`, `certainty`, `disagreement`, `caption_exposure`, `age_status`, `speaker_identity`, `notes`, `training_blocked`, `approved_training_label`. 수동 annotation은 모델 출력으로 채우지 않는다.

## 5. 남은 blocker와 실제 조회 수

**새 옹알이/보챔 source 확보 blocker:** 이 두 후보는 이미 색인된 eval이며 웃음/목욕 caption이다. 실제 infant babble/fuss가 있는지 미청취이고, 독립 월령/자연성/가족/원저작 관계가 검증되지 않았다. 원음은 로그인 다운로드 경로이며 preview와 원음의 동등성도 불명이다. 이번 권한은 metadata 연구만 허용하므로 실제 확보·사건 검수를 진행할 수 없다. 새 대형 모델 검색·나열을 하지 않았다.

| 이번 수행 | 정확한 수 |
|---|---|
| 공식 sound HTML metadata GET |2요청/2 HTTP200 |
| Jina Reader로 공식 원문 조회 |3요청/3 HTTP200: Freesound FAQ·terms·CC0 legal code |
| 외부 조회 총계 |**5**; 오류0·삭제0·empty0. 이는 발행한 GET 수이며 Jina 내부 요청/redirect 수는 세지 않음 |
| 검색 query·GitHub/API repo 조회 |0/0 |
| 음원/preview GET·HEAD·download·청취 |모두0 |
| feature/weight·추론·학습·calibration·ADB·Git·빌드·키·사용자 미디어·외부 연락 |모두0 |

Jina text snapshot: FAQ51,073B/SHA2567c473014a3da11f536154fec5a472c91c25aadc573fad02609ad629acc7adc5d; terms17,238B/bdec92e88888ae34bb5150676d3c3a6d60e34dcd629e10a5199e2cafc829cddc; CC0 legal code9,393B/e8fae5b85b4dd241761a54ae7540b021d9030f173b141d46fc1b3279dbb03282. 모두 metadata/원문 text이며 음원 snapshot이 아니다.

**완료:** current CC0·age/scene caption·format/혼합 한계·preview 경로·점수 비공개 검수 절차 재확인. **미완료:** 실제 취득/청취·사건 정답·독립 자연6–18개월 source 확보·모델 개선 검증. 학습 차단과 기존 eval 역할을 유지한다.
