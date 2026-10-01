# 아가톡 — 상황에 어울리는 아기 한마디를 위한 추가 조사

조사일: **2026-10-02 KST**. 조사 기준 코드: `5362ae3`의 `SoundAnalyzer.kt`, `ClipEvidence.kt`. 아래는 원문 확인 결과와 구현·평가 제안이다. 실제 수정 후 성능은 해당 버전의 실행 QA 보고서에서 판정한다. 이 문서 자체는 앱의 해석 정확도 승인이나 실제 클립 청취 평가가 아니다.

## 1. 이번에 해결할 품질 문제

사용자 요구는 명확하다. **웃는 아기에게 슬픈 대사, 우는 아기에게 신나는 놀이 대사가 나오면 안 된다.** 그렇다고 모든 입력을 보류하면 유료 제품의 가치가 생기지 않는다. 따라서 상황 모순과 유용한 응답률을 함께 측정한다.

원본 연구는 이 목표를 지지한다. 실제로 관찰된 웃음과 울음은 영아의 다른 발성보다 긍정·부정 표정과 강하게 연결된다. 반면 높은 소리·거친 소리·모음 같은 옹알이는 여러 정서로 쓰인다. **웃음/울음의 소리 사건을 더 잘 구별하는 개선은 가능하며, 그것이 곧 배고픔·졸림·통증 원인을 번역하는 모델은 아니다.** [R1–R4]

이번 개발의 권장 순서:

1. 시간별 모델 출력을 보존하고, 한 번의 최고점이 전체 구간의 의미가 되지 않게 한다.
2. 웃음↔울음 충돌을 양방향으로 검사한다. 비명은 긍정/부정 어느 쪽으로도 자동 귀속하지 않는다.
3. 사건이 분명하면 짧고 자연스러운 해당 의미군의 아기 한마디를 낸다. 복잡한 사건 변화는 근거에 표시하고 감정·원인은 추가하지 않는다.
4. 기존 두 공개 음원 외에 출처가 다른 긍정/부정/경계/비아기 음원을 고정해 확인한다. 오답과 보류를 별도로 집계한다.

## 2. 원문과 현재 코드에서 확인한 사실

### 2.1 YAMNet·MediaPipe 출력 계약

| 확인 내용 | 근거 | 구현에 주는 의미 |
|---|---|---|
| YAMNet은 521개 AudioSet 사건의 logistic 다중라벨 점수다. 16 kHz mono, 공식 Keras 모델은 0.96초 패치를 50% 겹치며 첫 출력을 위해 실제 파형 0.975초가 필요하다. | 공식 README·inference.py [T1] | 서로 다른 클래스 점수를 합해서 의도 확률 100%로 만들지 않는다. |
| 공식 예제는 시간별 점수 평균으로 전체 clip top-5를 보여 주며, 시각화 예제는 frame별 출력을 보존한다. | [T1] | 평균은 예제의 전체 태깅 방식이지, 아기 감정 판정의 검증된 정답 집계법이 아니다. 전체 max도 동일하게 검증이 필요하다. |
| MediaPipe의 AUDIO_CLIPS 결과는 각 interval에 대한 분류이며 `ClassificationResult.timestampMs()`가 시작 시간을 제공한다. 공식 문서는 `maxResults < 0`을 전체 category 반환으로 설명하고 allowlist/threshold도 지원한다고 적는다. **앱의 `tasks-audio:1.0.0`에서 명시적 `-1` 설정은 실제로 거절됐다.** | 공식 Android 문서 [T2]와 아래 버전별 실행 확인 | `flatMap` 전에 시각을 보존한다. 현재 모델에서는 **`.setMaxResults(521)`**로 명시한다. Java `timestampMs()`는 `Optional<Long>`이므로 값 존재 여부를 처리한다. 공식 Keras의 0.48초 hop을 Android Tasks interval 간격으로 단정하지 않는다. |
| `Baby laughter`는 `Laughter`의 자식이다. `Giggle`, `Chuckle, chortle`, `Belly laugh` 등도 웃음 계열이다. | AudioSet ontology [T3] | `Laughter`와 `Baby laughter`가 함께 높다고 충돌은 아니다. 부모 점수가 높고 자식이 낮을 수도 있다. |
| `Screaming` 정의는 fear, pain, surprise, **joy**, anger 등을 포함한다. | [T4] | 높은 비명성 소리를 울음·슬픔·통증으로 자동 변환하지 않는다. |
| `Babbling` 정의는 conventional speech의 구조·의미가 없는 인간의 speech-like sound다. 아동 발달 단계라는 설명이 붙는다. | [T5] | `Hubbub, speech noise, speech babble`와 별도 class다. `Babbling`도 화자 신원을 인증하는 class는 아니다. |

MediaPipe는 resampling/buffering/framing을 담당하며 clip 추론은 호출 thread를 막는다고 명시한다. 현재처럼 UI thread 밖에서 실행하고, 새 시간 집계가 UI·취소·메모리 경계를 훼손하지 않는지도 회귀 시험해야 한다. [T2]

**SDK1.0.0 검증 보완:** 앱의 Gradle 의존성은 `com.google.mediapipe:tasks-audio:1.0.0`이다. 메인 QA의 실제 계측에서 `.setMaxResults(-1)`은 `IllegalArgumentException: If specified, maxResults must be > 0.`를 발생시켰다. 따라서 공식 웹 문서의 음수 설명을 그대로 실행 권장으로 쓰지 않고, 현재 YAMNet의521개 category에 맞춰 `.setMaxResults(521)`을 사용한다. 향후 모델의 출력 수가 바뀌면 이 값도 다시 확인한다. `timestampMs()` 역시 현재 Java API는 `Optional<Long>`을 반환하므로 숫자처럼 바로 사용하지 않는다. 값이 없으면 임의의 시각으로 채워 시간 근거를 생성하지 말고 명시적으로 처리한다. 실제15초 clip에서는 약0.975초 간격의16개 interval이 관찰됐다고 메인 QA가 전달했다. 이는 현재 SDK·모델의 실행 관측이며, 공식 Keras의0.48초 hop과 혼용하지 않는다. 이 조사 담당은 Gradle·ADB를 별도로 실행하지 않았다.

### 2.2 현재 코드의 구체적인 취약점

이 항목은 원본 코드에서 확인한 구조이며, 사용자의 실제 녹음에서 동일 문제가 재현됐다는 뜻은 아니다.

- `SoundAnalyzer`는 여러 interval의 category를 펼친 뒤 같은 label의 `maxOf(score)`만 남긴다. 따라서 앞부분 웃음과 뒷부분 울음, 지속적인 웃음과 순간적인 울음 오활성화를 구별할 정보가 사라진다.
- `EvidenceInterpreter`는 infant label 중 최고점을 먼저 고른다. 선택된 label이 **울음이 아닐 때만** 울음/일반 울음/비명 충돌을 검사한다. 반대 방향인 `울음 선택 + 강한 웃음` 검사가 빠져 있다.
- 이 구조는 웃음 후보에 약한 generic cry가 섞이면 보류하면서, 잠깐 baby cry가 최고점이면 웃음이 지속된 clip에도 울음 의미군을 허용할 수 있다. **실제 오분류 여부는 frame 출력과 원음으로 확인해야 한다.**
- 기존 실제 공개 probe에서는 아기 웃음 0.668, 일반 웃음 0.984, 일반 울음 0.332가 함께 나와 한마디가 보류됐다. 이것은 이미 관찰된 응답률 문제다. [L1]
- 전체 PCM의 RMS·burst·rising은 TV/어른/배경 소리까지 포함한다. 이 값을 곧바로 “아기가 계속 말한다”, “웃음이 안 멈춘다”의 근거로 쓰면 소리 사건과 표현이 어긋날 수 있다. 사건이 활성화된 시간과 음량 변화를 함께 확인해야 한다.

## 3. 추가 영아 발성 연구에서 적용할 것

| 원 연구 | 실제 관찰·방법 | 아가톡에 적용할 판단 | 확대 해석하지 않을 내용 |
|---|---|---|---|
| Oller et al., PNAS 2013 [R1] | 9 infants, 6,995 utterances. 소리 종류는 audio-only, 표정은 video-only로 분리해 판단. 울음은 대체로 부정, 웃음은 대체로 긍정 표정. squeal/vocant/growl은 여러 정서에 사용. 검수자 일치 κ는 cry/laugh 0.91, protophone 종류 0.60. | 분명한 laugh/cry는 반대 정서 문구 금지. squeal/옹알이는 감정 중립의 소리 놀이 표현을 기본으로 한다. 독립 소리 라벨과 문장 적합성 평가는 분리한다. | 사람의 κ를 YAMNet 정확도로 인용하거나, 9 infants 연구를 전 연령·가정의 보장치로 사용하지 않는다. |
| Li, Hasegawa-Johnson, McElwain, Speech Communication 2021 [R2] | 영아 cry/fuss/laugh/babble/screech와 부모 발성 유형을 분리. 가정·실험실 자료, acoustic/voice-quality features와 분류기 비교. CRIED 평가에는 subject를 나누는 LOSO 사용. 외부 자료가 항상 도움이 되지 않아 cross-corpus 차이를 고려했다. | 실제 목표 label에 맞는 데이터와 speaker 분리 평가가 필요하다. 부모 말소리와 높은 infant-directed speech를 hard negative에 포함한다. | MFCC/pitch/RMS 하나를 정서의 고정 규칙으로 쓰거나 외부 음원 증강이 자동으로 좋아진다고 가정하지 않는다. |
| Yoo et al., PLOS ONE 2024, Infant vocal category exploration [R3] | 130 infants의 첫해 가정 종단 녹음에서 squeal/growl category가 특정 시기에 군집하는 현상을 관찰. 논문은 세션 내부의 바로 연속하는 반복을 직접 분석한 연구가 아니라고 명시한다. | 높은 발성은 vocal exploration일 수 있다. 비명성 소리=울음으로 처리하지 않는다. 실제 시간 구조를 측정한 경우만 반복 표현을 쓴다. | 높은 소리 반복만으로 도움 요청·흥분·분노를 결정하지 않는다. 연구를 앱의 burst 검출 검증으로 부르지 않는다. |
| Acoustic Features of Emotional Expression in Preverbal Infant Vocalizations, Journal of Nonverbal Behavior 2025 [R4] | 첫해 발성의 acoustic profile·정서 valence·발달 차이를 분석. 저자는 experimenter/caregiver 해석에 의존하는 valence 분류 한계와 더 큰 청취자 평가·다문화 검증 필요를 명시한다. | 음향 특징은 추가 모델의 feature 후보로 유용하다. valence와 특정 욕구는 다른 label로 둔다. | 논문의 age 분류 정확도를 감정/의도 정확도로 바꾸지 않는다. 논문에 보고된 상관으로 현재 코드 임계값을 정당화하지 않는다. |

**FaceLandmarker는 이 간극을 자동으로 메우지 않는다.** 현재 공식 모델은 얼굴 기하·blendshape 관찰 도구이며, 영아 감정 ground truth로 검증된 head가 아니다. 오디오의 모호함을 `mouthSmile` 하나로 역전시키지 않는다. 유료 시각 관찰을 추가해도 무료 음성 결과의 사건을 마음대로 다시 쓰지 않는 기존 계약을 유지한다. 상세 모델 카드 검토는 기존 [L2]에 있다.

## 4. 권장 집계·문장 계약 — 제품 설계 제안

아래는 논문에서 검증된 완성 알고리즘을 복제한 것이 아니라 **현재 엔진에 적용해 검증할 개선안**이다. 계수·임계값은 개발셋에서 정하고 holdout에서 결과를 확인해야 한다.

### 4.1 시간 정보 보존

각 interval마다 `startMs`, `endMs`, 관심 class의 원 점수, 구간 RMS/유효길이를 보존한다. 적어도 baby cry, baby laughter, babbling, generic laughter family, generic crying/whimper, screaming, speech, music을 분리한다. 마지막 padding 구간은 실제 duration만 기여해야 한다.

각 의미군에 대해 다음을 계산한다.

- **최고점:** 짧은 실제 사건을 놓치지 않기 위한 보조값.
- **지속 근거:** 실제 유효시간을 가중한 평균 또는 활성시간. 무음 padding을 분모에 넣지 않는다.
- **국소 우세:** 같은 interval에서 경쟁 계열보다 충분히 우세한지, 다른 interval에서도 이어지는지.
- **전환/혼합:** 웃음과 울음이 서로 다른 시기에 각각 독립적으로 나타나는지, 같은 interval에서만 동시 활성인지.

단순히 max를 mean으로 교체하면 짧은 아기 웃음이 긴 무음 속에서 사라진다. 반대로 top-k 평균만 쓰면 적은 순간의 오류가 전체를 대표한다. **최고점·지속시간·경쟁 근거를 함께 사용하는 작은 규칙층**부터 비교할 만하다. 중첩 window가 두 개라는 이유로 독립 사건이 두 번 있었다고 세지 않는다.

### 4.2 양방향 판단

| 관찰 | 권장 결과 |
|---|---|
| Baby laughter가 충분하고 generic laughter도 같은 시간에 우세, generic cry는 약한 한두 교차 활성 | 웃음 의미군을 허용할 후보. 약한 cry 하나 때문에 무조건 보류하지 않는다. 실제 clip 검증 필요. |
| Baby cry가 충분하고 일반 울음도 같은 시간에 우세, 웃음 근거가 약함 | 울음 의미군. 도움/곁에 있기 범위의 한마디. |
| Generic laughter가 지배적인데 Baby cry는 순간적으로만 높음 | 우선 울음 단정을 막는다. 아기 웃음 근거까지 부족하면 ‘웃음 소리는 들리지만 아기 소리인지 확실하지 않아요’처럼 앱 관찰을 보여 줄 수 있다. generic laughter 자체로 아기 신원을 만들어 내지는 않는다. |
| Baby cry와 Baby laughter가 별도 시간대에서 각각 강함 | 사건 변화로 기록. 전체를 하나의 슬픔/기쁨으로 덮지 않는다. 구간 축소가 가능하고, 충분히 검증하면 감정 단정 없는 혼합 한마디를 별도 검수할 수 있다. |
| 같은 시간대에 두 계열이 비슷하게 강하고 분명한 우세가 없음 | 한마디 보류, 근거와 구간 변경을 제공. |
| Babbling + Screaming, 분명한 cry/laugh 없음 | ‘내 소리도 들어 봐요!’ 같은 중립 발성 표현 후보. Screaming 단독으로 아기 대사를 열지 않는다. |
| 성인 웃음/말소리, 음악, 진공청소기, 무음 | 아기 근거 부족. 만능 아기 대사로 채우지 않는다. |

위 결정은 **양방향이어야 한다.** ‘울음만 보호’하면 웃음이 슬픔으로 바뀌는 문제가 남고, ‘웃음만 보호’하면 실제 울음을 장난으로 오인한다. 일반 laughter로 baby cry를 무조건 덮는 것도 금지한다. 실제로 보호자 웃음과 아기 울음이 함께 녹음될 수 있다.

화자 귀속 역시 남는 한계다. 모노 녹음에서 앞부분 옹알이가 있었다고 뒷부분 성인 웃음까지 같은 아기 것으로 확정할 수 없다. TV에서 재생한 아기 울음도 소리만으로 현장의 아기와 구별하기 어렵다. 이번 알고리즘이 화자 분리·실재 아기 인증을 구현했다고 표현하지 않는다.

### 4.3 유용하면서 모순 없는 한마디

의미군을 먼저 고르고 표현을 고른다. 파일명·fingerprint·최근 사용횟수는 의미를 바꾸지 않는다. 같은 PCM·같은 구간·같은 분석 버전은 같은 결과를 유지한다.

| 의미군 | 자연스러운 후보 | 기본 제외 |
|---|---|---|
| 웃음 | ‘헤헤, 나랑 같이 웃어요!’, ‘까르르! 웃음이 나요.’, ‘히히, 내 웃음 들려요?’ | 슬프다/울고 있다/배고프다/특정 사람이 웃겼다 |
| 울음 | ‘으앙… 잠깐 내 곁에 있어줘요.’, ‘응애, 나 좀 봐줘요.’ | 신나요/그 놀이 또 해줘요/졸려서 울어요/아파요 |
| 옹알이 | ‘나도 한마디 할래요.’, ‘아아, 우우… 내 얘기 들려요?’, ‘내 소리에도 답해줄래요?’ | 자동 음절 전사/정확한 뜻/무조건 기쁨·슬픔 |
| 명확한 아기 발성이지만 정서 중립·혼합 | ‘내 소리도 들어 봐요.’ 등은 별도 검수 후에만. 무엇이 관찰됐는지 근거를 붙인다. | 신원·사건 근거가 없는데 무조건 이 문장으로 fallback |

‘계속’, ‘또’, ‘안 멈춰요’, ‘할 말이 많아요’는 전체 RMS만으로 붙이지 않는다. 실제 해당 사건의 반복·지속을 확인한 경우에 한정하거나 과장 없는 후보로 바꾼다. 후보 수보다 **반대 의미가 없는지, 실제 아기 목소리처럼 들리는지, 같은 틀처럼 보이지 않는지**를 평가한다.

## 5. 원문 확인한 추가 공개 QA 음원 8개

아래는 원 배포 페이지를 2026-10-02 네트워크로 읽어 저자·설명·라이선스·공개 preview 링크를 확인한 목록이다. **이 조사 담당은 미디어를 다운로드하거나 청취하지 않았다.** 제목/설명은 배포자 label이며 독립 정답이 아니다. 메인 QA는 모델 결과를 보기 전에 청취해 구간·expected set을 확정하고, 모호한 것은 억지로 정답화하지 않는다. 원본을 저장소·APK·QA ZIP에 재배포하지 않는다.

| ID / 저자 / 원 페이지 | 권리·길이 | 예상 사용 / 한계 | 페이지에 노출된 공개 preview |
|---|---|---|---|
| 575331 / Precipice_6 / [Baby Laugh and Babbel.wav](https://freesound.org/people/Precipice_6/sounds/575331/) | CC0 1.0, 71.104초 | laugh/babble/mixed holdout 후보. 저자 설명이 두 사건 모두 포함. 전체를 순수 웃음으로 label하지 않는다. 월령 미상. | https://cdn.freesound.org/previews/575/575331_3570210-lq.mp3 |
| 58308 / Erdie / [baby-laughing01.flac](https://freesound.org/people/Erdie/sounds/58308/) | CC BY 4.0, 131.889초 | 경계 holdout. 저자 표현이 **‘almost laughing’**이므로 모델 결과와 무관하게 웃음/옹알이/애매함 허용 집합을 청취로 정한다. 월령 미상. | https://cdn.freesound.org/previews/58/58308_118241-lq.mp3 |
| 139046 / keplar / [Babbling - Happy 2](https://freesound.org/people/keplar/sounds/139046/) | CC BY 4.0, 2.847초 | 개발용 옹알이. 딸의 studio babbling, Sony PCM-D50 설명. 제목의 happy를 실제 감정 정답으로 쓰지 않는다. 월령 미상. | https://cdn.freesound.org/previews/139/139046_316502-lq.mp3 |
| 410741 / simo170075 / [BABY CRYING.wav](https://freesound.org/people/simo170075/sounds/410741/) | CC0 1.0, 32.899초 | 별도 울음 holdout. 저자가 6개월 baby crying이라고 명시. 욕구 원인 정보 없음. | https://cdn.freesound.org/previews/410/410741_7118067-lq.mp3 |
| 33658 / moxobna / [laughter.wav](https://freesound.org/people/moxobna/sounds/33658/) | CC0 1.0, 62.621초 | 일반 웃음 음성 negative 후보. 원 설명은 ‘Several laughs, chuckles and giggles’; 댓글·기존 링크는 woman laughter로 소개하나 배포자 설명만으로 성인 나이를 인증하지 않는다. 청취 확정 필요. 과거 sagetyrtle 주소는 현재 moxobna로 연결. | https://cdn.freesound.org/previews/33/33658_43834-lq.mp3 |
| 324783 / miguel2613 / [conversation.wav](https://freesound.org/people/miguel2613/sounds/324783/) | CC0 1.0, 23.624초 | dinner에 관한 boy/girl 대화 설명. 비아기 말소리 negative, 실제 연령은 미상. speech babble 혼동 점검. | https://cdn.freesound.org/previews/324/324783_5589643-lq.mp3 |
| 50762 / RutgerMuller / [Vacuum Cleaner 1.wav](https://freesound.org/people/RutgerMuller/sounds/50762/) | CC0 1.0, 59.941초 | 비음성 가정 잡음, 순수 입력과 별도 혼합 perturbation에 사용 가능. | https://cdn.freesound.org/previews/50/50762_179538-lq.mp3 |
| 505990 / Rvgerxini / [simple piano loop.mp3](https://freesound.org/people/Rvgerxini/sounds/505990/) | CC0 1.0, 7.999초 | 음악 negative와 아기+배경음 혼합. 저자 제작 loop이며 영상 TV의 모든 경우를 대표하지는 않는다. | https://cdn.freesound.org/previews/505/505990_9453283-lq.mp3 |

CC BY 파일은 로컬 QA manifest에도 저자·원 URL·license URL·변환 내용을 남긴다. [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)은 저작자 표시와 변경 표시 등을 요구한다. [CC0](https://creativecommons.org/publicdomain/zero/1.0/)도 인물 관련 별도 권리를 자동 보증하지 않으므로 상용 모델 학습·광고 재사용 자료로 조용히 전환하지 않는다. 이번 범위는 공개 clip의 내부 동작 확인이다.

제외한 후보: Kinoton 371562는 sound 페이지에 CC0가 있으나 연결한 저자 프로필에 raw 재배포 금지 등 별도 조건도 명시돼 있다. 현재 목록에서 제외했다. waveletaudio 568121은 명시적 성인 여성 성우 설명을 읽었지만 이번 Reader 응답에서 라이선스·preview가 충분히 확인되지 않아 채택하지 않았다.

기존 개발 probe 47370/211528은 [L1]의 권리 기록을 유지한다. 그 결과를 이미 봤으므로 독립 holdout으로 재분류하지 않는다. 원본을 여러 구간/음량으로 변형한 것은 독립 아기 수가 늘어난 것이 아니다.

### 5.1 옹알이 표본 추가와 baseline의 한계

메인 QA 담당의 별도 실제 실행에서 139046은 Babbling peak가 약0.04 이하이고 일반 Laughter 약0.80/Baby laughter 약0.50인 frame이 나왔다고 전달받았다. 이 조사 담당이 독립 실행하거나 청취한 결과는 아니다. **제목이 Babbling이라는 이유로 순수 옹알이 정답을 강제하거나, 모델이 laughter를 골랐다는 이유로 배포자 label을 바꾸지 않는다.** 혼합/음원 label 오류/모델 오류 중 무엇인지 청취 검수를 남겨야 한다.

이후 검색에서 대상 월령이 명시된 별도 후보를 추가 확인했다. 첫8개의 후보와 별도로 보충하며, 실제 채택 여부·구간·성공률은 QA manifest가 기록한다.

| 원 페이지 | 원문·권리 | 공개 preview·주의 |
|---|---|---|
| [Islabonita471061, Baby_Babble1.wav](https://freesound.org/people/Islabonita/sounds/471061/) | ‘My **7 month old** babygirl babbling before sleep’, **CC0**, 158.997초 | https://cdn.freesound.org/previews/471/471061_2200015-lq.mp3 . 새로운 source. ‘before sleep’는 녹음 상황이며 아기가 졸렸다는 자동 정답으로 쓰지 않는다. |
| [reinsamba49728, baby_voice_11months(11).wav](https://freesound.org/people/reinsamba/sounds/49728/) | 딸 Charlotte **11개월**, 새로운 소리/음절/‘words’ 발달 설명, **CC BY4.0**,24.792초 | https://cdn.freesound.org/previews/49/49728_18799-lq.mp3 . 기존 웃음47370과 같은 저자·같은 아기일 수 있어 독립 아기 holdout으로 세지 않는다. |

기존 미확보 후보 [Sage Ross의 Infant babbling in crib](https://commons.wikimedia.org/wiki/File:Infant_babbling_in_crib.ogv)은 원문에 **6.5개월**, buh/muh 발성,15초 own-work, CC BY-SA3.0 선택이 명시돼 있다. 이번 조회에서 원문은 다시 확인했으나 미디어 다운로드는 수행하지 않았다. 과거 미디어429 제한을 ‘이번 다운로드 성공’으로 바꾸지 않는다.

## 6. 평가 계획과 완료 기준

### 6.1 이번 코드 개선의 작은 회귀셋

개발셋: 기존 웃음47370·울음211528, 새 옹알이139046, 일반 말소리/잡음의 일부. **holdout: 575331, 58308, 410741의 source ID를 먼저 고정**하고 결과를 보기 전에 expected event 집합을 기록한다. 한 source의 다른 crop·음량 변형을 train/dev/holdout에 나누지 않는다. Source의 아기 식별 정보가 없으면 source-level 분리라고만 부른다.

각 row에는 source/hash/구간/인코딩/라이선스/라벨 근거/허용 사건/금지 문장 의미군/모델 버전/전체 frame 출력/최종 상태/실행시간을 남긴다. 모델 결과를 본 뒤 expected set을 넓혀 통과시키지 않는다. 애매한 label의 이유는 먼저 기록한다.

필수 사례:

- 분명한 웃음, 분명한 울음, 옹알이, 높은 발성, 실제 혼합/전환.
- 아기 소리와 보호자 웃음/말소리·음악·청소기 혼합. 실제 가정 녹음을 대체한 것이 아니라 합성 강건성 점검이라고 표시.
- 무음, 작은 소리, 시작·끝 silence, 1초/2초/15초, 경계가 다른 crop, 포맷 재인코딩.
- 웃음에 약한 crying score, 울음에 약한 laughter score, 양쪽 강한 score를 **양방향**으로 주는 규칙층 회귀시험.
- 동일 PCM 반복 분석, 다른 source의 결과 의미군·표현 다양성.

### 6.2 함께 공개할 지표

| 지표 | 분모·해석 |
|---|---|
| 반대 상황 오류 | 정답이 명확한 laugh/cry clip 중 반대 의미군을 낸 개수 / 해당 clip 수. **0건이 이번 고정 회귀셋 통과선**이지만 일반 서비스 0% 보장이 아니다. |
| 응답한 결과의 오류 | 사건이 확정된 출력 중 잘못된 사건/의미군 수 / 응답 수. 보류를 분모에서 빼더라도 아래 응답률을 반드시 병기. |
| 사건별 유용 응답률 | 각 clear class에서 올바른 한마디가 나온 clip 수 / 그 class 전체. 모두 보류하면 0%. |
| 비아기 오생성 | 비아기·무음 입력에서 아기 한마디가 나온 개수 / negative 수. |
| 보류율·보류 이유 | 전체·사건별로 분리. 신원 부족, 사건 충돌, 저음량, 모델 실패를 구분. |
| 변화 대응 | 앞/뒤 crop의 사건이 다를 때 결과가 알맞게 달라지는지. 전체 mixed와 crop의 일관성. |
| 표현 품질 | 부모가 평가한 장면 어울림/아기다운 말투/반복감/저장 의향. 모델 score로 대신하지 않는다. |
| 속도·자원 | 동일 기기 cold/warm 시간, 최대 15초 분석, 취소, 메모리. 이전 실험과 같은 조건으로 비교. |

작은 공개셋에서는 **개수/분모와 source 수**를 먼저 보여 준다. 2개 중2개 성공을 ‘정확도100%’ 제품 주장으로 사용하지 않는다. 0/n 반대 오류도 모집단 상한을 작게 증명한 것이 아니다. 정확도를 주장할 별도 실사용 평가에는 대상 월령·한국어 가정·기기·거리·background 조건, 가족/세션 분리, 독립 청취자의 합의와 검수자 불일치 기록이 필요하다.

**현재 QA의 주장 범위:** 이 작업의 source label은 배포자 설명을 참고한 것으로, 독립 청취 정답이 확보된 상태가 아니다. source ID를 미리 holdout으로 떼어 놓았더라도 그 사실만으로 정답 label이나 독립 성능평가가 생기지 않는다. 현재 실행은 **공개 음원의 소스 설명에 대한 결과 일치·모순·보류를 보는 탐색적 회귀 QA**로 보고한다. 위의 ‘정답이 명확한 clip’ 지표는 청취 검수를 갖췄을 때 적용할 평가 설계이며, 지금의 결과를 일반 분류 정확도·아기 의도 해석 정확도·독립 검증 완료로 표현하지 않는다.

### 6.3 연구에서 실제 제품까지

후속 pilot은 동의한 가정의 아기별 여러 세션에서 clear/mixed/negative를 모으고 가족 단위로 완전히 분리한다. 발성 종류와 화면 문장의 어울림을 다른 평가자가 먼저 평가한다. Hunger/sleep/pain의 라벨을 보호자 추정 하나로 자동 ground truth로 만들지 않는다. 데이터를 외부로 수집·전송하는 기능은 현재 범위가 아니며, 서버 DB 없이도 자발적으로 제공받은 로컬 검수셋에서 시작할 수 있다.

접근/구매/촬영/가져오기/구간선택/재생/오프라인/기록/PDF 회귀는 이 평가와 함께 수행한다. 올바른 분류를 계산했어도 예전 문장을 캐시해 보여 주거나 다른 구간을 분석하면 사용자에게 같은 결함이다. **실제 최종 APK로 source→구간→분석→화면 문장→저장/재열기까지** 한 경로를 확인한다.

## 7. 제공한 엔진과 경쟁 서비스의 연결

### voc2vec

이번 조회에서도 저자 README는 비언어 발성 representation 모델, 10개 corpus/125.19h, 여러 task로 fine-tuning한 평가, 코드·모델 Apache-2.0이라고 명시한다. [T6] 평균 benchmark UAR/F1을 아가톡의 웃음·울음 정확도나 욕구 번역 정확도로 옮길 수 없다. 별도 label head·대상 데이터 평가·온디바이스 변환이 필요하다. README usage의 모델 이름과 실제 공개 checkpoint config도 검증 대상이다. 이번 Hugging Face Reader 재조회는 403 제한으로 실패했으며, 이전 config 직접 확인은 [L2]에 있다. 이번에 가중치를 다운로드하거나 통합하지 않았다.

지금 권장 경로는 **YAMNet 시간 집계 수정 → 검수 label로 YAMNet embedding 작은 head 비교 → voc2vec를 오프라인 후보/교사로 비교 → 실제 이득이 확인되면 경량화**다. 새 모델 이름을 나열하는 것으로 현재 잘못된 문장 선택을 덮지 않는다. 데이터가 부족한 상황에서 고정 공개 클립 8개로 새 classifier를 학습하지 않는다.

LiteRT는 배포 런타임, librosa는 특징 추출·평가 도구다. 이 둘을 추가 의미 분석 엔진으로 세지 않는다. 기존 [L2]의 역할 분담과 라이선스 검토를 유지한다.

### 서비스 참고

- **ChatterBaby 공식 FAQ:** crying 검출과 pain 등 세부 추정을 분리해 설명하고, minimal background noise를 권장하며, 보호자가 노래하거나 개 짖는 clip에는 작동하지 않는다고 명시한다. 추가 이유 label은 reliably different라는 증거가 생길 때 추가한다고 설명한다. 이것은 **개발사 주장과 사용 조건**이지 아가톡에 이전되는 90% 성능이 아니다. [C1]
- **MeowTalk 공식 tutorial:** 고양이별 수정 label을 여러 번 모아 학습·갱신한다고 설명한다. 이것은 제품의 피드백 loop 참고다. 아가톡이 고정 문구 pool만 바꾸면서 ‘우리 아기를 학습했다’고 표시할 근거는 아니다. [C2]
- 기존 경쟁사·말투 조사는 [L3]에 있다. 이번 수정의 제품 가치는 문구 숫자보다 **다른 소리·다른 구간에 실제로 다른 적합한 반응이 나오고, 부모가 원음을 바로 확인할 수 있는 것**에 둔다.

## 8. 출처와 조사 상태

원문은 agent-reach의 Exa 검색, Jina Reader, GitHub `gh api`로 확인했다. 검색 snippet만 읽고 primary source의 결론으로 쓰지 않았다. 사용자 원음·영상의 외부 업로드, 모델 다운로드, Gradle/ADB 실행은 이 조사에서 하지 않았다. Agent Reach 업데이트 확인 결과 v1.5.0 최신으로 표시됐다.

- **[T1]** TensorFlow YAMNet [README](https://github.com/tensorflow/models/blob/master/research/audioset/yamnet/README.md), [inference.py](https://github.com/tensorflow/models/blob/master/research/audioset/yamnet/inference.py). GitHub contents API raw 본문 확인.
- **[T2]** MediaPipe [Audio classification guide for Android](https://ai.google.dev/edge/mediapipe/solutions/audio/audio_classifier/android), Configuration/Handle and display results.
- **[T3]** Google AudioSet [Laughter ontology](https://research.google.com/audioset/ontology/laughter_1.html).
- **[T4]** Google AudioSet [Screaming ontology](https://research.google.com/audioset/ontology/screaming_1.html).
- **[T5]** Google AudioSet [Babbling ontology](https://research.google.com/audioset/ontology/babbling_1.html).
- **[T6]** [voc2vec 저자 저장소 README](https://github.com/koudounasalkis/voc2vec), [ICASSP 2025 논문](https://doi.org/10.1109/ICASSP49660.2025.10890672). 이번 fresh 확인은 README이며 논문 표·config 상세는 [L2]의 이전 검토와 구분한다.
- **[R1]** Oller et al. (2013), [Functional flexibility of infant vocalization and the emergence of language](https://pmc.ncbi.nlm.nih.gov/articles/PMC3631625/), [DOI](https://doi.org/10.1073/pnas.1300337110).
- **[R2]** Li et al. (2021), [Analysis of acoustic and voice quality features for the classification of infant and mother vocalizations](https://pmc.ncbi.nlm.nih.gov/articles/PMC9435967/), [DOI](https://doi.org/10.1016/j.specom.2021.07.010).
- **[R3]** [Infant vocal category exploration as a foundation for speech development](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0299140), PLOS ONE (2024), DOI10.1371/journal.pone.0299140.
- **[R4]** [Acoustic Features of Emotional Expression in Preverbal Infant Vocalizations](https://link.springer.com/article/10.1007/s10919-025-00498-9), Journal of Nonverbal Behavior, online2025-11-28. Abstract/Discussion/Limitations 원문 확인. 이 문서에서는 표의 수치를 전사하거나 모델 성능으로 인용하지 않았다.
- **[C1]** [ChatterBaby 공식 FAQ](https://chatterbaby.org/pages/faq), 2026-10-02 본문 확인.
- **[C2]** [MeowTalk 공식 tutorials](https://www.meowtalk.app/tutorials), 2026-10-02 본문 확인.
- **[L1]** [기존 실제 공개 음성 probe](20261001-public-audio-probes.md).
- **[L2]** [기존 엔진·논문·권리 검토](20261001-cry-evidence.md).
- **[L3]** [기존 경쟁사·한마디 검토](20261001-competitors-and-voice.md).

이 메모의 결론은 ‘근거가 없으니 개선을 멈춘다’가 아니다. **현재 확보한 근거로 바꿀 수 있는 시간 집계·반대 의미 차단·자연스러운 한마디를 먼저 개선하고, 실제 clip의 오답과 응답률을 함께 검증한다.** 새 규칙의 효과는 실행 결과로 보고한다.
