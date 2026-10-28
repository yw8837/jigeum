# 영아 발성 존재와 감정 단서의 분리: 미탑재 연구 가정

확인일 **2026-10-07 KST**. 이 문서만 작성했다. 현재 정책/해석 코드와 기존 연구 결과를 읽고 AudioSet 공식 HTML2페이지를 확인했다. 제품 코드·threshold 변경, 재학습/calibration·새 모델/원음/feature body·청취/추론·외부 미디어·사용자 자료·기기·Git·빌드는0이다. **이 가정은 미탑재·비승인 연구 후보이며 안전하다고 확정하지 않는다.**

## 1. 질문에 대한 판단

**‘영아 발성 관련 넓은 단서’와 ‘울음/웃음 종류·화자 귀속’을 별도 축으로 기록하는 것은 학습 목표에 맞는 연구 가정이다. 그러나 높은 presence만으로 추가 아기 한마디의 안전성이 입증되지는 않는다.** cry/laugh가 있어도 presence가 높을 수 있으며, 그 조합은 같은 아기의 울음, 다른 화자의 울음, TV/재생, 모델 상관 오인 중 어느 것인지 결정하지 못한다.

따라서 고정 score replay에서는 기존 판정/감정/욕구/subtype/문장을 보존하고 별도 관찰 record의 `broad_presence_clue`, `emotional_cue_context`, `attribution=held`를 비교하는 경계가 타당하다. 이 record를 기존 neutral 승인으로 병합하거나 BABBLE/CRY/LAUGH·아이 시점 문장·얼굴 동기·onset으로 변환하는 것은 다른 제품 변경이다. **감정 단서를 숨기는 것이 아니라, 해당 단서를 아기에게 귀속할 근거가 부족함을 유지해야 한다.**

`mixed-emotional-context`는 실제 두 감정/화자가 존재한다는 정답이 아니라 **모델의 cry/laugh 계열 cue 때문에 종류·귀속을 보류하는 context flag**로만 정의할 수 있다. 울음·웃음은 음향 유형이며 슬픔/행복·욕구의 직접 정답이 아니다. context 없음도 neutral 감정이나 감정 부재를 입증하지 않는다.

## 2. 현재 코드의 실제 경계

| source | 확인한 동작·분리 연구의 주의점 |
|---|---|
|[InfantVoiceSupportPolicy.kt](../../outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/InfantVoiceSupportPolicy.kt)|12–15행 기존 승인 우선/`shouldAnalyze` 통과 요구.21–26행 정확 전체창·finite/range/필수 labels, selection 어디든 AST cry/laugh/scream/TV≥.30이면 보류.29행 같은 창 presence≥.80 및 cleaned strongest1s RMS≥.003. 기존 감정 guard를 제거하면 이 계약을 바꾸는 것임 |
|[BabbleSupportPolicy.kt](../../outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/BabbleSupportPolicy.kt)|`shouldAnalyze`: INFANT_UNCONFIRMED/BACKGROUND_DOMINANT만 recoverable. YAMNet 울음/아기웃음≥.35 또는 화자미확인 울음/웃음/비명≥.65면 거부. unresolved 감정 event를 neutral로 바꾸지 않는 의도 |
|[SoundTimeline.kt](../../outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/SoundTimeline.kt)|generic window는 VocalMoment가 아님.18–20행 generic+events.empty를 ‘아기 발성’, **neutralOnly=true**로 처리. 이 container에 mixed context를 단순 복사하면 neutrality가 소급 생김. CRY/LAUGH 판단은 infant cue와 기존 지속/경쟁 기준을 별도로 사용 |
|[ClipEvidence.kt /EvidenceInterpreter](../../outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/ClipEvidence.kt)|89행 이후 problem 우선, broadOnly는 waveform rhythm을 소거.112–113행 기존 BabyExpression에 generic/neutral flags를 전달.169행 broadOnly sustained 금지.253행 이후 visual 결합도 generic exact event를 만들지 않음. 기존 해석기로 context를 통과시키는 것만으로 귀속 문제가 해결되지 않음 |
|[같은 파일 /DeviceClipAnalyzer](../../outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/ClipEvidence.kt)|217–226행 **AST 실행 진입 자체도 shouldAnalyze(primaryAudio)에 종속**. 연구 sidecar attach만 바꿔도 감정 충돌 입력에 score가 항상 존재하는 것은 아님. missing score를 성공/0점으로 채우지 말고 연구 replay의 실제 availability를 기록 |
|[BabyExpression.kt](../../outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/BabyExpression.kt)|80행 presence면 generic 표현8개를 선택. request-only여도 ‘내 소리/한마디’가 아기 발성 귀속을 암시할 수 있음. 새 감정 context 연구를 이 문장군의 안전 승인으로 보고하지 않음 |

## 3. multilabel과 head의 학습 한계

[AudioSet 공식 dataset](https://research.google.com/audioset/dataset/index.html)은 사람이10초 segment 안에서 들린 sound 존재를 확인한 자료다. [공식 download 원문](https://research.google.com/audioset/download.html)은 segment마다 **one or more labels**, label co-occurrence, 한10초에 Keys jangling/Car가 함께 있는 CSV 예시를 설명한다. 따라서 서로 다른 label의 높은 score를 배타적 단일 상태/동일 화자/동시성으로 읽을 수 없다. segment start/end는 실제 cry/babble event의 경계가 아니다. 원문은 annotation confusability/label quality 한계도 적는다.

[기존 ontology/AST 원문 감사](20261007-infant-presence-android-evidence.md)의 Babbling/Baby cry/Baby laughter/Child speech 정의와 AST cls/dist pooling을 재사용한다. cry/laugh ontology class가 존재한다고 그 소리의 원인·감정·정확6–18개월 화자가 검증되는 것은 아니다.

[ast_infant_head_probe.py](../../tools/research/ast_infant_head_probe.py)155–177행은 train21/18source의 clip별 `weak_class=='infant_vocal'`을 이진 target으로 사용한다. **양성7clip/4source/40.7초**,768→1/769parameter, caption-weak, calibrated_probability=false다. cry/laugh/babble/의도·화자/time span target이 없다. pooled10초 표현과 clip supervision은 caption 파일의 호흡/잡음/성인 겹침에도 동일 bag target을 줄 수 있다.

연구에서도 head SHA `81d7357c4c59f57222952a1542716f7d89e423d45f2987dd3917a3522bdfd510`, support .8/binary margin .4·normalization 및 현재 음량/감정 cue 기준을 고정한다. margin .4는 화자/감정 두 축 간 구분 margin이나 확률 신뢰구간이 아니다.

**아기 호흡/배경 상관 위험:** 아기가 있는 녹음의 숨소리·마찰·폰 channel·방 acoustic·부모 말·목욕 물소리를 학습 shortcut으로 썼는지 사건 검수로 배제되지 않았다. 아기 호흡이 실제 있어도 speech-like 발성/옹알이는 아니다. 같은 창 RMS는 호흡/성인/물/음악이 충분히 크다는 사실만 보장할 수 있어 sound identity 검증이 아니다. 이런 오인을 실제로 관측했다고 주장하지 않으며 필요한 반례로 남긴다.

presence와 original AST527은 같은 encoder 표현에 의존하므로 높은 두 score가 통계적으로 독립인 검증2개가 아니다. YAMNet도 같은 음원/유사 사건 taxonomy의 오인 가능성을 공유한다. `presence AND cry`를 아기 울음 posterior로 곱하거나 둘의 score 우세로 화자를 택할 근거가 없다. Music/Breathing은 현재 AST contrary set에 없고 BACKGROUND_DOMINANT는 recoverable라, 감정/TV cue가 약한 상관 오인에는 기존 level gate도 충분한 방어가 아니다.

## 4. 최근 결과가 말하는 것과 말하지 않는 것

[동결 공개3source 후속 결과](20261007-public-infant-followup-results.md): bvaudio475589 마지막20초는 presence .9544/.9353 및 같은 창 RMS .03991을 통과했지만 YAMNet 울음.5/화자미확인울음.66797 때문에 generic 보완이 차단됐다. 첫20초는 head0/3 support, 중간은 기존 울음 승인이다. **마지막20초의 실제 울음·화자·자연 발성 정답은 미확보**이며 caption의 tail cry를 정답으로 가져오지 않는다. 이 한파일에서 추가 출력을 얻도록 guard/threshold를 조정하면 사후 선택 위험이 있다.

같은 결과의 기존 웃음2·중간 울음1은 exact 보존, 새5선택의 추가 한마디0이었다. [Android 결과](20261007-infant-presence-android-results.md)의 약국2개발 span 회복·기존 승인13개 보존과 공개33 새지원0, [extra9 결과](20261007-extra-babble-evaluation-results.md)의 caption support4/miss5를 함께 유지한다. 같은 약국 원본의 겹친 구간·소수 업로더·기존 회귀는 일반 오류율/가족 독립 정답 분모가 아니다. 이 문서는 실행/사용자 미디어를 재검증하지 않았다.

## 5. 필수 반례와 연구 실패 조건

| 사전 고정 반례/대조 | 보존해야 할 경계·실패 조건 |
|---|---|
|기존 승인 cry/laugh/babble·약국generic·공개3source의 모든 선택|기존 observation/문장/subtype exact 보존. 높은 head로 기존 울음/웃음을 neutral 또는 mixed로 덮으면 실패 |
|adult cry/laugh/scream만 있음·성인 아기흉내|head false support가 있어도 아기 화자/감정/한마디 승인으로 승격하면 실패. score replay의 논리 통과는 실제 음향 오인율 검증이 아님 |
|real infant babble+adult cry/laugh, 같은 창·서로 다른 창|generic context와 unresolved 감정 cue를 함께 보존. 모든 감정을 아기에게 묶거나 exact 동시/전후 사건을 만들면 실패 |
|infant cry만 있음·laugh만 있음·cry/babble 실제혼합|presence가 subtype을 뜻하지 않음. subtype held 상태에서도 crying을 neutral로 해석하거나 미지원 창을 숨기면 실패 |
|TV/폰에서 아기 울음·cartoon voice·TV+실제 아기|‘아기 같은 음향’과 현장 아기 존재가 다름. TV guard/재생 ambiguity를 generic출력으로 우회하면 실패 |
|아기/성인 호흡·snoring/panting·마찰·물·음악만 있음|높은 RMS와 caption-context correlation을 발성으로 승인하면 실패. Music/Breathing 미guard를 성공으로 간주하지 않음 |
|짧은 아기 단서와 큰 부모/음악/물, 분리·겹침·quiet prefix/tail|same-window audibility도 attribution이 아님. 다른 창 RMS를 가져오거나 실제 onset/연속시간/횟수를 만들면 실패 |
|missing/partial/NaN/shape·범위·정렬오류, unresolved MIXED_VOCAL|현재 strict availability·문제 보존. 감정 context를 포괄적 error bypass로 쓰거나 누락score를 채우면 실패 |

동결 score replay는 **semantics/기존 승인 보존/누락·오류 처리의 코드 계약**을 검증할 수 있다. 새로운 acoustic generalization·화자/감정 귀속·음향 반례는 모델 score와 독립인 사람 event 검수 및 허용된 실제 음원 평가가 필요하다. synthetic score 조합을 실제 성인/TV/호흡 검증으로 보고하지 않는다. 새 threshold fitting·재학습·아기 자료 calibration/augmentation은 계속 금지다.

## 6. 연구 출력의 최소 계약과 현재 상태

별도 sidecar는 원래 problem/subtype/문장을 유지하고 `scope=BROAD_CONTEXT`, 실제 입력창, source/head pin, fixed score, same-window level, context flag, `speaker=UNVERIFIED`, `emotion/subtype=HELD`, `onset=NONE`을 남긴다. “모델에서 아기 발성과 관련된 넓은 단서가 잡혔어요. 울음·웃음 여부와 소리의 주인은 확인하지 못했어요.”는 **관찰 문구 연구안**이며 실제 출력 승인/아기 한마디가 아니다. neutralOnly·기존 mixed 문장군·VisualSoundAlignment·responseGuide·저장/내보내기에 자동 병합하지 않는다.

외부 조회 **2/최대4**, 공식 HTML200 2/오류0: dataset351,300B/SHA256 `160a7259ab2fa64a2a33661c416ecc39ade288b2c6062099e793b720667d7880`; download17,572B/`6cd0105d0252dc872f8ea9430eac54626ee5fc90708736d912c1c6947cee0697`. 로컬 VisualExpression.kt 경로 조회 오류1은 실제 정의가 ClipEvidence.kt253행임을 찾아 해결했다. HTML/text·코드/doc 읽기만 수행했으며 모델·원음·feature·실행·제품 수정은0이다.

**결론:** 의미축 분리와 감정 귀속 보류의 연구 경계는 타당하다. 현재 neutral container를 재사용한 추가 아기 출력은 승인할 근거가 부족하다. 별도 frozen replay와 독립 반례를 통과하기 전 미탑재·비승인 후보로 유지한다.
