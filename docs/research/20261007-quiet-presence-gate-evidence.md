# 작은 영아 발성 단서와 중복 QUIET gate — 코드 근거와 남은 반례

검토일 **2026-10-07 KST**. 담당 파일은 이 문서 하나다. 현재 Android 소스·기존 연구/QA 문서만 읽었다. 사용자 원본/PCM·model/weight를 열거나 추론·학습·threshold fitting·다운로드·빌드·ADB·Git·외부 연락을 수행하지 않았다. 본문의 계산 예시는 코드의 정의로부터 도출한 값이며 실행한 음원·모델 결과가 아니다. 다른 작업자의 구현 변경을 보존했다.

## 1. 판단

**generic infant presence에 한해, 이미 사용하는 cleaned sliding1초 RMS≥.003을 재사용하고 전체 장면의 activeFraction<.04로 그 단서를 다시 지우지 않는 수정은 코드상 근거가 있다.** 이를 약국 파일에 맞춘 새 모델 threshold나 새로운 음량 threshold의 선택이라고 부를 이유는 없다. 원래 무음 기준 .003, head support .8/.4, 원527logits와 성인/감정/TV 충돌 보호를 유지해야 한다.

다만 이것은 **일반 영아 recall 개선이나 오인 방지의 입증이 아니다**. strongest RMS는 어떤 소리든 통과할 수 있는 입력 가용성 검사다. 그 소리가 아기 발성인지, 같은 broad창의 어느 순간인지, 성인/음악/잡음인지 구분하지 않는다. 실제 성인·음악·low-level·DC·click·short+20초padding 회귀와 전체 경로 검증 전에는 개선 완료/배포 품질로 보고할 근거가 부족하다.

메인 보고 및 [Android 후보 계약](20261007-infant-presence-android-contract.md)은 첫 약국10초의 .8197847이 generic support 뒤 whole-scene activity<.04에서 QUIET가 됐고 후속10초 .9400272는 한마디로 회복했다고 기록한다. 이는 같은 원본의 겹치는 개발 구간이며 이 조사자가 원음이나 개인 실행 결과를 재확인한 것은 아니다. [현재 QA 문서](../qa/infant-presence-android-20261007.md)의 진행/실패/미실행 표와 실제 후속 로그를 구분해야 한다.

## 2. 서로 다른 두 gate의 실제 정의

| 코드 원천 | 실제 측정 | 용도와 한계 |
|---|---|---|
| [AudioCleanup.strongestSecondRms](../../outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/AudioCleanup.kt) | cleaned PCM의 연속16,000sample 에너지합을 **1sample씩 이동**한 최대값의 RMS.1초 미만 배열이면 실제 길이를 분모로 사용 | 앞뒤 무음이 짧은 소리를 희석하지 않도록 만든 입력 level 검사. 아기/음성 classifier가 아님 |
| [SoundAnalyzer.analyzePcm](../../outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/SoundAnalyzer.kt) | 입력1~30초·유한성·범위 검수 → DC/80Hz cleanup → strongestSecondRms .003 검사 | .003 미만이면 SILENCE로 끝나고 YAMNet/support callback을 실행하지 않음 |
| [AcousticShape.from](../../outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/ClipEvidence.kt) | 동일 cleanup 후 **비중첩100ms** 단위 RMS≥.012를 active로 계산. active100ms창 수 / 전체창 수 | 전체 장면의 활동 비율·burst·rise·pause용 음향 요약. 화자 분리나 아기 발성 duration 정답이 아님 |
| 수정 전 EvidenceInterpreter | sound decision 성공 후 activeFraction 비유한 또는<.04이면 QUIET | 이미 모델 단계에 도달한 작은/짧은 sound도 두 번째 문턱에서 소거 가능 |

두 RMS 문턱은 측정 폭도 다르고 .012는 .003의4배다. 동일 duration에서 RMS의 제곱 에너지로는16배지만 서로 다른 창이므로 동일 민감도 검사라고 취급하지 않는다.

`activeFraction=.04`는10초에서100ms창4개(약400ms),20초에서8개(약800ms)가 .012 이상이어야 한다는 뜻이다. 마지막 부분창은 실제 sample 수로 RMS를 구하되 fraction에는 다른창과 같은1개로 들어가므로 비율을 정밀한 발성시간으로 바꾸면 안 된다. 주변 무음이 늘어나면 동일 발성의 fraction도 줄어든다.

### 계산만으로 확인되는 불일치

- 이미 cleaned된 zero-mean 소리가1초 동안 RMS .006이고 나머지9초가0이면 strongest1초는 .006으로 .003을 넘는다. 모든100ms RMS가 .006이면 .012 active창은0개이고 activeFraction=0이다. 수정 전에는 모델 score가 .8을 넘어도 QUIET로 끝날 수 있다.
- cleaned된 큰 소리가200ms만 있고 나머지가 무음인10초에서 해당100ms창2개만 active면 fraction=.02로 보류된다. 충분한 에너지를 가진 소리가 짧다는 이유로 탈락할 수 있다.
- 같은1초 소리에19초 무음을 붙이면 전체 RMS는 더 작아지지만 strongest1초의 의미는 유지된다. **고정된 cleaned 배열**에0을 붙이고 원배열이1초 이상이면 sliding1초 maximum은 동일하다. raw PCM을 다시 cleanup하면 전체 mean·filter 초기 상태와 경계가 달라질 수 있으므로 raw 두 조건의 bit동일성까지 이 계산으로 보장하지 않는다.

이 예시들은 .006이나200ms를 새 제품 threshold로 제안하는 것이 아니다. 현재 두 gate가 서로 다른 질문을 한다는 반증이다. 실제 모델은 이런 입력을 영아라고 지원할 수도, 보류할 수도 있으며 여기서는 그 값을 계산하지 않았다.

## 3. 검토 중 메인이 반영한 현재 소스 상태

첫 소스 조회에는 기존 activeFraction gate만 있었고, 후속 조회에서는 아래 변경이 이미 들어 있었다. **수정 자체를 이 조사자가 수행한 것은 아니다.** 현재 소스 읽기를 실행 통과·최종 고정 SHA의 증거로 대체하지 않는다.

1. `AcousticShape.strongestSecondRms: Double`에 `from`의 cleaned 배열에서 실제 계산한 값을 보존한다. 이는 Float로 반올림한 값·원본 raw RMS·scene 평균·모델 score의 대용 값이 아니다. cleaned 배열은 기존 finally에서 지운다.
2. `EvidenceInterpreter`는 audio.problem·sound decision.problem을 먼저 처리한다. generic infantVoiceWindows가 있고 strongestSecondRms가 finite/.003..1.0이면 낮은 activeFraction을 이유로 추가 QUIET를 만들지 않는다. 비유한 activeFraction은 계속 보류하고 기존 cry/laugh/babble gate는 유지한다.
3. `AstAudioWindow`에도 nullable `strongestSecondRms: Double`을 추가했다. candidate runtime은 **각 실제 AST span**을 별도로 cleanup하여 level을 구하고, AST features에는 기존 raw slice를 그대로 넣는다. 원 AST 입력·정규화·logits를 바꾼 것은 아니다.
4. `InfantVoiceSupportPolicy`는 각 level의 존재/finite/0..1을 검사하고 **같은 broad span의 head≥.80 AND level≥.003**인 창만 support로 연결한다. 다른 창의 큰 성인/음악 소리로 quiet창의 지원을 정당화하는 경로를 줄였다. 원 score .8/.4 계약은 유지한다. 현재 policy의 `.8` 조건은 binary margin≥.4도 만족하지만 이 논리 사실을 새 calibration으로 해석하지 않는다.
5. broadOnly는 기존처럼 shape의 activeFraction/rising/bursts/pauses/quietSpans를 아기 표현에 사용하지 않는다. 긴발성·ordered phase·visual time alignment의 정확 사건 근거로도 승격하지 않는다.

### 현재 코드가 보장하지 않는 부분

**같은 span에서 충분한 level이 있다는 사실도 그 level이 아기 발성의 에너지라는 증거는 아니다.** 부모 말/TV/악기/click이 같은10초창에 있으면 그 소리가 level을 채울 수 있다. per-span level 검사는 다른 quiet창이 빌려 쓰는 문제를 줄이지만 화자·event alignment를 해결하지 않는다.

각 AST span에서 `AudioCleanup.filter(slice)`를 새로 시작하므로 whole-selection cleanup을 잘라낸 값과 mean/filter 초기상태가 정확히 같다고 보장하지 않는다. 동일 함수·동일 .003 기준을 사용하는 계약이지 동일한 cleaned buffer를 공유한 사실은 아니다. 원 graph numerical parity와 별도로 경계/DC/짧은입력 level 검사를 확인해야 한다.

현재 `InfantVoiceSupportPolicy.contrary`에는 cry/laugh/scream 계열과 Television이 있지만 **Music은 없다**. `BabbleSupportPolicy.shouldAnalyze`도 BACKGROUND_DOMINANT를 recoverable로 허용한다. 그래서 음악/잡음이 YAMNet background로 우세하더라도 감정/TV 충돌 없이 head가 높으면 generic support가 가능한 구조다. 이것은 이미 관측한 음악 오인이라는 뜻은 아니다. 현재 반례 범위 밖의 오인 가능성을 level 수정으로 해결했다고 주장하면 안 된다. 이번 문서는 음악 score에 맞춘 새 threshold를 제안하지 않는다.

## 4. 유지할 판정 순서와 입력 계약

| 항목 | 유지할 조건 | 피해야 할 우회 |
|---|---|---|
| 입력 가용성 | 실제 PCM≥1초, 현재 최대길이·유한성·범위, NO_AUDIO/INVALID/SILENCE/TOO_SHORT/MODEL_FAILURE 보호 | face·shape·높은 head로 오류/무음을 승인 |
| level | 같은 cleanup 정의·실제 sample 분모·Double·.003 inclusive·finite/0..1. quiet AST창은 support제외 | 전체 평균/raw DC/타창 큰 소리/임의gain을 새아기 근거로 사용 |
| score | fixed head/graph·frontend·support .8/.4. 결측/NaN/Inf/범위밖은 보류 | .799999를 반올림해 승인하거나 원본에 맞춰 임계값 선택 |
| 충돌 | 선택 전체의 기존 YAMNet 감정/혼합·AST cry/laugh/scream/TV .30보호·기존승인우선 | head가 높은 창만 남겨 다른창의 충돌을 숨김 |
| coverage | 실제1~20초에 맞는 complete·ordered·unique expected AST창 | duplicate/역순/범위밖/누락창·padding을 실제긴녹음으로 표시 |
| 출력 | generic infant-like clue, broad범위, 정확시점/반복/화자/욕구 없음 | 낮은 sceneactivity를 bypass한 뒤 scene rise/burst를 아기에게 귀속 |

강한 level만 있는 입력은 계속 보류되어야 한다. 충분한 level과 높은 모델 score가 있어도 충돌/입력 오류가 있으면 새 generic 지원을 만들면 안 된다. `.003`은 물리적인 소음 floor·SNR·귀로 들리는 크기·아기 발성 정답으로 검증된 값이 아니며, **기존 내부 입력 기준을 일관되게 적용하는 값**이다.

`VideoAudioDecoder.analyze`에는 legacy whole-array RMS<.003 shortcut이 별도로 있다. 현재 DeviceClipAnalyzer는 `decodeWithTiming` 후 SoundAnalyzer를 호출하므로 이 legacy shortcut의 변경을 자동으로 포함했다고 말하면 안 된다. 이번 수정 범위를 여러 entry point의 모든 작은 소리 문제 해결로 확장하지 않는다.

## 5. 반례와 필요한 검증 — 결과를 임의 예측하지 않음

아래는 이번 조사에서 실행하지 않은 검증 계약이다. 기존 소스의 test가 존재해도 실행 로그가 없는 항목을 통과로 세지 않는다. synthetic policy test와 실제 모델/전체 decoder/UI 결과를 각각 기록해야 한다.

| 반례 | 확인할 실패 가능성 | 보존/통과 기준 |
|---|---|---|
| 완전무음·.003 아래 입력 | 기본 높은/변조된 score가 silence를 넘김 | SoundAnalyzer에서 model/support미실행 또는 명시적인 SILENCE 유지. fake highscore 단위검사는 입력오류를 우회 못함 |
| 경계 .002999 / .003, NaN/Inf/음수/>1 level | Float 반올림·결측/위조값으로 gate통과 | 실제 Double 계산과 동일 비교. policy의 결측/invalid창 보류. exact boundary의 단위 결과와 real PCM 결과 구분 |
| 작은 tone·low-level 정적잡음·white/pink/noise | energy가 classifier의 infant정답으로 쓰임 | energy-only는 승인없음. realhead의 score/support·충돌·최종문장 기록. “level을 넘으니 발성”이라고 라벨링하지 않음 |
| DC constant·DC+quiet voice·40Hz rumble | raw 큰 DC/저역잡음이inputlevel를 채움 | raw level대신 cleanup값; pureDC는0, rumble감쇠·finite 유지. 실제 transient/filter경계도 확인하며 필터가 모든잡음을 없앴다고 보고하지 않음 |
| 1sample click·100ms click·impulse/train | 최대 RMS는 충격음을 쉽게 통과시킬 수 있음 | 같은 .003 level만으로 분류하지 않음. click의 realhead가 높으면 오류를 기록하고 broad승인품질 보류; 새로운 duration threshold를 사후조정하지 않음 |
| 성인 말·웃음·비명·울음·아기흉내 | quiet성인도 두번째음량문턱 면제됨 | 기존전체선택 충돌보호 유지, 기존public controls의 새문장승인 증가 여부 확인. 그 결과로 모든adult/흉내 차단을 주장하지 않음 |
| 음악·피아노·TV·장난감·부모+작은소리 | 같은span의 다른소리가level를 제공하고 head오인을 감춤 | 원YAMNet/AST scores·conflicts·head·samewindowlevel·최종결과를 함께 기록. Music hardguard 부재와 TV모델누락한계 유지 |
| 20초 앞의 큰 부모말+뒤quietspan | whole-selection strongest를 quietspan이 빌림 | 높은head라도 같은quietspan level<.003이면 support제외. 다르게 배치한3중첩창 전체기록, 가장좋은창만보고하지 않음 |
| 짧은소리+20초앞/뒤0padding | activeFraction희석·overlap 중복·부당한긴발성 | original real duration과padding 구분, broad범위만 제공. onset/실제발성시간/longphrase/scene상승으로 확대하지 않음 |
| 1초미만·빈입력·20초초과 | padding으로 원래 input제약우회 | 현재 TOO_SHORT/INVALID/범위보류 유지. 테스트용0padding과실제20초녹음 구분 |
| 초반/끝/비정렬 burst·마지막sample | disjoint1초 구현이 짧은sound를 놓침 | sliding 모든sample·finalsample 포함. cleaned실제값 계산, raw PCM변경없음. timestamp/onset 추정으로 쓰지 않음 |
| model/memory/cancel 실패·잘못된window | level새필드가 runtime실패를 은폐 | 기존 modelstore/shape/finite/cancel/deadline·whole selection 조건 보존. 정상재개와자손정리는 실행QA로만보고 |

1sample amplitude1의 energy를1초분모16,000으로 계산하면 RMS≈.007906으로 .003을 넘는다. amplitude .1이면≈.0007906이다. **이는 strongest RMS 단독으로click을 영아에서 분리할 수 없다는 계산 예시**이며 cleanup 후 impulse 응답·head score·최종결과를 실행한 사실은 아니다. click을 level로 완전히 차단했다고 주장하면 틀리다.

짧은소리에0을 추가해도 graph의inputcontext가 바뀌거나 cleanup경계가 달라질 수 있으므로 모든 padded headscore가 동일하다고 약속하지 않는다. 요구사항은 “padding 때문에 실제발성이 길었다고 만들지 않을 것”, “각 실제span level을 확인할 것”, “추가quiet창을 support로 승인하지 않을 것”이다. weak broad모델이 짧은 발성 자체를 놓치면 현재 누락으로 남기고 threshold를 낮춰 정답을 맞추지 않는다.

## 6. QA 근거의 현재 범위

[AudioCleanupTest.kt](../../outputs/MoaAndroid/app/src/androidTest/java/com/moa/babylanguage/AudioCleanupTest.kt)에는 비정렬/끝sample sliding검사, invalidPCM, DC·40Hz·보컬대역·clipping·shape attribution 검사가 있다. [InfantVoiceSupportPolicyTest.kt](../../outputs/MoaAndroid/app/src/androidTest/java/com/moa/babylanguage/InfantVoiceSupportPolicyTest.kt)에는 fixed.8 boundary, invalidscore/window, whole-selection conflict, existingapprovals, norhythm/onset/lipsync와 새level검사가 작성되어 있다. **test 소스를 읽었다는 사실만으로 실제 통과를 보고하지 않는다.**

메인의 첫 Android36조건은 parent보고/계약의 실측 이력으로 인용한다. 이 문서는 해당private run log를 열지 않았고 수정 후 Android/전체AV/20초/기록/실ARM을 실행하지 않았다. 단위 gate 통과는 모델 성능 검사가 아니며 emulator와PC의 메모리/속도는 실폰 성능·발열의 증거가 아니다.

이 수정은 앞선 `.8198` 관측을 표시에 연결할 **구현 일관성 보완**으로 설명할 수 있다. 작은 소리 일반 recall·성인/음악 false-positive rate·6~18개월 정확도·욕구번역 향상으로 수치를 약속할 근거는 없다. 알려진 개발원본 한 개의 두 겹치는10초 성공으로 검증을 끝내면 부족하다.

## 7. 참조와 완료 상태

- 로컬 일차 근거: [AudioCleanup.kt](../../outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/AudioCleanup.kt)의 filter/strongestSecondRms, [SoundAnalyzer.kt](../../outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/SoundAnalyzer.kt)의 analyzePcm, [ClipEvidence.kt](../../outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/ClipEvidence.kt)의 AcousticShape/EvidenceInterpreter/DeviceClipAnalyzer, [AstAudioAnalyzer.kt](../../outputs/MoaAndroid/app/src/astProbe/java/com/moa/babylanguage/AstAudioAnalyzer.kt)의 candidate windowlevel/rawfeatures, [AstAudioTypes.kt](../../outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/AstAudioTypes.kt)의 level필드.
- 판정 일차 근거: [InfantVoiceSupportPolicy.kt](../../outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/InfantVoiceSupportPolicy.kt), [BabbleSupportPolicy.kt](../../outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/BabbleSupportPolicy.kt), [SoundTimeline.kt](../../outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/SoundTimeline.kt). 현재Music guard부재·recoverable background를 직접 확인했다.
- entry point 구분: [VideoAudioDecoder.kt](../../outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/VideoAudioDecoder.kt)의 legacy analyze와 decodeWithTiming.
- broad 모델의 공식 근거는 [앞선 일차 자료 조사](20261007-infant-presence-android-evidence.md)의 공식AST forward/AudioSet10초/FSDclip weaklabels에 있다. 이 문서에서는 외부원문/음원/weight를 새로 취득하지 않았다. modelclassification이 inputlevel/현장아기/onset정답을 대신하지 않는다는 범위를 유지한다.
- 개발 관측/승인범위: [Android 후보 계약](20261007-infant-presence-android-contract.md), [진행중QA](../qa/infant-presence-android-20261007.md), [extra9결과](20261007-extra-babble-evaluation-results.md), [최신HANDOFF](../HANDOFF.md). 원음·threshold/학습·배포상태를 이 문서로 변경하지 않는다.

**완료:** 중복gate의 구조적 불일치, 현재generic-only수정의근거, samewindowlevel의효과/한계, 유지할보호·반례·QA범위 검토. **미완료:** 수정후 실제원음/공개반례/전체경로/ARM검증과배포. 코드상 근거는 있지만 일반 성능 채택 근거는 아직 부족하다.
