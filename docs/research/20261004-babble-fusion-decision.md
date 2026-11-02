# AST 옹알이 보조 관찰: 동결한 실험 정책

2026-10-04. **배포 승인 아님. 기본 비활성.** INT8 모델의 수치 이식성과 의미 판정은 별도다. 이 문서는 추가 소스의 의미 점수를 보기 전에 후보 정책과 평가 분리를 고정한다. 모델 점수는 확률·음량·아기 신원이 아니다.

## 후보 v1 — 최초 동결본

- 기존 YAMNet 판정이 통과하면 그대로 보존한다. 울음·웃음을 AST로 치환하지 않는다. 입력/모델 오류, 무음, 기존 혼합 발성 보류는 해제하지 않는다.
- AST는 원음 16 kHz에 독립 실행한다. 정확한 `Babbling` 점수가 **.30 이상**, 또는 `Babbling >= .10`와 `Child speech, kid speaking >= .30`의 동시 지지를 요구한다. Child speech 단독은 충분하지 않다. .30 단독 분기는 옹알이가 어린 말소리 클래스와 반드시 함께 활성화되지 않는 경우를 위한 것이다. 약한 .10 분기는 같은 모델의 연관 클래스 지지이며 독립적인 두 모델 합의라고 부르지 않는다.
- 이는 보정된 수치가 아니라 기존 수치 민감도 표의 거친 후보값(.1/.3)을 고정한 실험이다. 이미 확인한 owner2에만 맞춰 이후 낮추지 않는다. 기존 7개월 자료도 개발 자료로 취급한다. 독립 평가가 실패하면 승격 보류하며 새 버전은 새 holdout이 필요하다.
- AST의 울음/웃음/비명 계열 중 하나가 .10 이상이며 해당 창 Babbling의 .60배 이상이면 충돌로 보류한다. 어느 창에서든 해당 계열 또는 Television이 .30 이상이면 전체 선택의 보조 승인을 보류한다. 기존 YAMNet의 infant 울음/웃음 >=.35 또는 generic 울음/웃음/비명 >=.65도 보조 승인에 대한 모순이다. AST가 그런 점수를 주었다는 이유로 울음이나 웃음을 새로 확정하지 않는다.
- Music, Speech, Hubbub 점수 단독으로 승인을 만들거나 옹알이를 기각하지 않는다. 음악과 옹알이의 클래스 점수 비교로 실제 음량의 우세를 판단하지 않는다. TV 검출이 낮아도 실제 TV와 현장 아기는 구분할 수 없다는 한계는 유지한다.
- 채택 창은 **start–end 전체 범위의 옹알이 후보**다. YAMNet 0.975초 프레임·정확한 시작점·반복 수로 변환하지 않는다. 서로 겹치는 창은 독립 표본도 반복 발성도 아니다. 창 근거만 있는 경우 전역 파형의 bursts/rising/pause를 아기 발성에 귀속하지 않는다. 길게 지속되는 아기말/잠깐 쉬고 다시 말함 문구를 열지 않는다.
- 런타임 진입점은 `BabbleSupportPolicy.ENABLED`로 기본 false. 평가에서는 `attachCandidate`를 명시적으로 호출한다. 실제 배포 활성화는 아래 의미·Android 게이트 후 별도 변경이다.

## 평가 분리: 새 점수 열람 전 고정

기존 AST/양자화 점수를 본 공개 자료와 owner2는 모두 개발 회귀다. owner2는 개선 여부를 반드시 확인하되 정확도·보정 분모에서 제외한다. 동일 저자/아기 가능성의 crop은 한 그룹으로 묶는다.

| 자료 | 역할 / 고정 구간 | 그룹과 해석 |
|---|---|---|
| 기존 public cry/laugh/babble/mixed/7개월/conversation/vacuum/piano/generic laugh, synthetic tone/silence/TTS | 개발 회귀, 이미 사용한 구간 | 기존 수치 확인 때문에 holdout 아님 |
| reinsamba 11개월 | 개발 회귀, 첫20초 | 기존 웃음과 같은 저자·아기 가능성, 독립 holdout 아님 |
| Sage Ross crib 6.5개월 | 잠정 source holdout, 전체15초 | 미리 수치/출력을 본 작업자가 있으면 개발로 강등. 파일 설명은 약한 라벨이며 청취 판정 필요 |
| keng 2개월 | 연령 외 보조 holdout, 첫20초 | 별도 저자. 대상6–18개월 성공 분모에서 제외 |
| stephan/PDSounds 5개월 혼합 | 연령 외 혼합 challenge, 첫20초 | 다른 stephan/PDSounds 녹음과 한 그룹. 순수 babble 양성으로 세지 않음 |
| Mayra | 미확정 challenge, 첫20초 | 연령/사건 불확실. 청취 전 정확도 분모 제외 |
| Manoj newborn cry | 연령 외 울음 challenge, 첫20초 | 새 babble 오승인 감시; 대상 연령 분모 제외 |
| cchenail male laugh | negative holdout, 첫20초 | 독립 source group, 실청 확인 필요 |
| ReallyInterestingPictureCo female laugh | negative holdout, 첫20초 | 독립 source group, 실청 확인 필요 |
| duducsepel short voice | negative challenge, 첫1초 | 단어 맥락이 잘림, 연령 설명 불충분. 확정 성인 분모 제외 |

평가 스코어를 본 뒤 구간·라벨·숫자를 바꾸지 않는다. 창별 원래527점수, 실제 정규화 PCM/선택 길이, INT8 모델 SHA, YAMNet base 판정, 최종 보조 판정을 로컬 ignored work에 보존한다. FP32와 INT8은 각자 v1 규칙을 평가한다. 동일 파일 여러 창을 별도 독립 성공으로 세지 않는다.

## 승격 전 반드시 필요한 증거

1. 기존 수락 cry/laugh 결과 불변; 혼합 소리의 단일 baby/cry 오승인 0건. 소스 라벨 불명은 별도 미확정으로 기록한다.
2. 독립 source의 명확한 대상 연령 babble 회복과 성인 말/웃음·음악·TV/재생 baby·무음 hard negatives를 실제 들어 라벨링하고 같은 고정 정책으로 실행한다. 현재 소스만으로 충분한 대상 연령 양성·TV/재생음 음성 분리가 확보되지 않았다. 단일 Sage 사례가 통과해도 일반 정확도 주장은 불가하다.
3. owner2 10초/20초 모두 앱 `DeviceClipAnalyzer` 경로에서 실제 원음으로 평가. 첫10초 broad 후보만 있을 때20초 전부 반복/지속한다고 쓰지 않고, 음악 고점수를 음량 우세로 보고하지 않아야 한다.
4. 실제 Android frontend/ONNX 수치, 오프라인, 지연·최고 메모리·취소 후 다음 분석·반복 실행·최종 signing/update를 검증한다. 단위 테스트는 이 실행 증거를 대신하지 못한다.

현재 기본 비활성은 임시 모델을 조용히 정식으로 승격하지 않기 위한 구현 경계다. 기존 누락이 해결됐거나 최종 생성/Play 배포가 완료됐다는 뜻이 아니다.

## 동결 후 첫 평가 — 승격 보류

부모 실행자가 문서의 사전 SHA를 `work/public-perception/frozen-policy-before-scoring.json`에 기록한 뒤 INT8 공개9소스24창을 한 번 실행했다. 출력은 `work/public-perception/int8-frozen-01/scores.json`, SHA-256 `e237671812ef6740ed7e74322abc10569ced9c84e0e0ae98bf65f104e885017b`다. 아래는 고정 규칙의 AST 측 후보 여부를 점수로 대조한 결과이며 **실제 YAMNet 합성 판정·사람 청취 정답·Android 통과가 아니다.**

| 그룹 | 동결 v1 관찰 | 해석 |
|---|---|---|
| reinsamba 11개월 | 3창 모두 후보 | 기존 저자 개발그룹이라 독립 회복 근거로 세지 않음 |
| Sage Ross 6.5개월 | 2창 모두 후보 미충족. 첫창 Babbling .0958814, Child .6844757 | 새 대상 연령 source의 회복 게이트 미통과. 결과를 보고 .10을 낮추지 않음 |
| stephan 5개월 | 첫창만 후보 | 연령 외·혼합 출처이며 정답 검수 전 성공으로 세지 않음 |
| keng 2개월 | 앞2창 후보이나 마지막창 generic cry .3410223으로 선택 전체 보류 | 새로운 울음 분류로 바꾸지 않음 |
| Mayra / Manoj | 보류 | 미확정/연령 외 challenge |
| cchenail / ReallyInterestingPictureCo | 후보 0 | 소규모 출처 설명 기반 negative 검사이며 일반 오탐률 아님 |
| duducsepel | 후보 0 | 실제1239ms 전부 실행. 위 first1초 계획과 다르므로 protocol deviation이 있는 별도 challenge로 기록 |

대상 연령의 독립 옹알이 회복이 확보되지 않았으므로 **ENABLED=false 유지**. 부모는 duducsepel을 원 단어 보존 목적의 `first <=20s` 규칙으로 별도 사전 고정했으나 본 문서와 일치하는 1초 실험으로 취급하지 않는다. 추가 검증은 새 소스/그룹과 입력을 먼저 고정한 뒤 진행한다. 같은 holdout을 반복 보정에 사용하지 않는다.

구현 회귀는 Android 계측 `BabbleSupportPolicyTest` 8개에 작성했다. 부모가 실제 정책·타임라인 소스와 DTO를 JVM에서 컴파일해 동일 중립+음악 fixture를 실행한 RED(`work/policy-jvm/red-01`, 기대1/실제0, exit1)와 GREEN(`green-01`, 기대1/실제1, exit0)을 확인했다. 이 단일 JVM 회귀는 전체8계측·Android·의미 품질 통과가 아니다. 실제 소스의 광범위 창 보존·기존 울음/웃음·모순 보류·손상 입력·출력 표현 검사는 부모 통합 계측 실행이 남았다.

## 추가 공개 자료와 Android 실행

새 점수를 보기 전에 clod11111개월 첫20초와 NoiseCollector9개월 전체15.812초를 고정했다. `work/public-perception/int8-target-age-01/scores.json`의2소스6창은 모두v1옹알이 조건 미충족이다. 명확한 독립 청취 라벨이 없어 정확도나 오류율로 계산하지 않는다. [원문·권리·고정 구간](20261004-target-age-babble-sources.md).

Android API35 x86_64에서 사용자 개발용 원본의10초/20초를 실제디코더→YAMNet→AST→평가용v1후보정책→해석기 순서로 실행했다. 두구간 모두 기본미확정에서 옹알이후보로 회복됐다. 소리없는 별도원본은보류됐다. 시각은선택10초20표본/20초40표본의실제PTS를보존했다. `work/ondevice-av/android-owner-probe-01`은평가opt-in결과이며 **기본 DeviceClipAnalyzer 활성화/일반화된회복/새문장생성의증거가아니다.** 개별문장과관찰은Git에추가하지않았다.

## 후보 v2 — 코드검토에서 발견한 충돌 누락 수정

후속SPEC검토는문서의“울음/웃음계열”과실제5개클래스목록이불일치함을발견했다. AST는독립sigmoid를출력하므로부모 `Laughter`가낮아도 `Giggle`이높을수있다. 모델의전체527클래스중 `Giggle`, `Snicker`, `Belly laugh`, `Chuckle, chortle`, `Whimper`, `Wail, moan`6개를기존모순검사에추가했다. 양성임계값과충돌임계값은변경하지않았다. 기존v1실험을v2성공으로재표기하지않는다.

실제 정책 소스 JVM 재현: `work/policy-jvm/contrary-red-01`에서 Babbling=.60, 각 세부 클래스=.90인6경우 모두 잘못 승인(exit1). 수정 후 `contrary-green-01`에서6경우 모두 보류(exit0). 정책9개를 포함한 기본116개·연구27개 Android 회귀도 통과했다. 기본 비활성을 유지하며 v2의 신규 일반화 평가는 아직 없다.

v2의 오프라인 Android 재검사에서는 같은 약국 개발 원본의10초/20초 후보 회복을 확인했다. 소리 트랙 없는 원본은 계속 보류했다. 이는 신규 독립 자료가 아니므로 일반화 평가 성공에 포함하지 않는다. [실행·정리 근거](../qa/ondevice-av-generation/ANDROID-AST-QA.md).

TV에서실제아기소리를재생하는경우는영아발성이없는negative와구분한다. 음향사건탐지만으로현장아기신원·현재얼굴과의관계·실제의도를확인했다고표시하지않는다. 완전한출처식별능력을새옹알이검출의전제조건으로삼지않는다. [실패원인과승인조건검토](20261004-generation-failure-review.md).
