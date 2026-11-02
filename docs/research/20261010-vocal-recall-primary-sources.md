# 영아 발성 존재와 종류 보류 분리: 원문 검토

2026-10-10 작업명. 확인 환경은 `codex/ondevice-av-probe`, 메인의 후속 기준 `e16f448`이다. 이 보고서는 원문·현재 소스·기존 조사만 읽었다. 실제 `mayra_context` 청취/추론, 제품 성능 측정, 학습, 빌드, ADB, 배포를 수행하지 않았다.

## 적용 추천

**generic 사건 veto를 통과한 supported 창만 남기는 부분 중립 관찰을 우선 추천한다.** 같은 시간창의 영아 단서·두 presence head·음량·전체 모델 실행 coverage는 유지하되, 채택하지 못한 다른 창 때문에 채택 가능한 발성 단서도 전부 숨기는 정책을 분리한다. 다른 창은 종류/화자 미확정으로 근거에 남긴다. AST의 `Baby cry, infant cry`와 `Baby laughter`는 영아 사건 라벨이다. 이 점수가 높다는 이유로 영아 존재 자체를 모두 부정할 필요는 없지만, 옹알이·웃음·울음·기분·욕구 확정의 근거도 아니다. [공식 아기 울음 정의](https://research.google.com/audioset/ontology/baby_cry_infant_cry_1.html), [아기 웃음 정의](https://research.google.com/audioset/ontology/baby_laughter_1.html).

새 중립 경로에서 아기 전용 두 사건을 **종류 불확실성**으로 처리하고, 일반 `Crying, sobbing`/`Laughter`/`Screaming`/`Whimper` 등은 해당 창의 veto로 우선 유지한다. `Television`은 선택 전체의 veto를 유지하는 편이 안전하다. 이는 논문 임계값이 아니라 제품의 보수적 경계를 유지하는 제안이다. 일반 웃음·비명이 성인 단독 입력에 붙을 수 있다는 위험을 두 head의 높은 값만으로 해소하지 못한다. 실제 대조가 실패하면 채택하지 않는다.

처음 제안한 baby-specific veto 두 개만 분리하는 변경은 메인이 제공한 아래 실제 관측에서 회복0이다. `Crying, sobbing`도 .30을 넘기 때문이다. 따라서 이 보고서의 최종 추천은 **창별 부분 관찰**이며 parent generic veto의 예외 해제는 후순위다.

이 추천의 범위는 **모델이 이미 가진 영아 존재 단서를 종류 보류와 분리**하는 것이다. 가청 신호의 모든 누락을 회복하는 새 detector는 아니다. 현재 `mayra_context`의 두 head 약 .99는 메인이 전달한 관측이며 이 조사에서 다시 실행하지 않았다. 해당 값만으로 실제 영아/비울음 옹알이/화자/정확시작/욕구를 확정할 수 없다.

### mayra_context 관측에 대한 판단

| 창 | 메인이 전달한 관측 | 부분 중립 후보 |
|---|---|---|
|0–10초|infant .9948/caption .9925, contrary 최대Laughter .198; 포함되는YAMNet7.8초 babycry .5/RMS .00846|기존.80/.80·.003·anchor 계약을 통과하는지 시험할 후보. 10초 전체가 깨끗하거나 연속 영아발성이라는 뜻은 아니다.|
|5–15초|두head 약.990/.990; Crying .3074/Babycry .2914|generic veto .30 때문에 보류를 남김. Babycry .2914를 새 threshold로 반올림하지 않음.|
|10–20초|두head 약.993/.991; Babycry .388/Crying .375;YAM15.6초babycry .668/RMS .00545와13.65초childvoice .5/RMS .00536|후반 단서는 보존하되 generic veto로 채택 보류. 앞창 결과의 존재만으로 후반을 설명하지 않음.|

0–10초와 보류된5–15초는5초 겹친다. broad score는 충돌 발생 시점을 알려주지 않으므로 앞창을 확정된 무충돌 시간구간이라고 부를 수 없다. **서로 다른 시간창의 모델 관찰·불확실성이 함께 남는 결과**로 제한한다. 이 표는 재추론·독립 청취 결과가 아니며 메인이 제공한 수치에 대한 정책 검토다.

### parent generic 사건을 baby-specific로 설명하는 방식의 위험

공식 ontology는 `Baby cry, infant cry`를 `Crying, sobbing`의 child, `Baby laughter`를 `Laughter`의 child로 둔다. 따라서 parent와child의 동시 score를 항상 서로 반대 사건이라고 해석하는 것은 부정확하다. [Crying ontology](https://research.google.com/audioset/ontology/crying_sobbing_1.html), [Laughter ontology](https://research.google.com/audioset/ontology/laughter_1.html), [공식 ontology JSON](https://raw.githubusercontent.com/audioset/ontology/master/ontology.json).

그러나 hierarchy는 소리 개념의 관계다. AST 출력은 parent≤child와 같은 제약을 강제하지 않으며 두 사건이 같은 화자에게서 발생했다고 알려주지 않는다. 같은10초창의 영아 울음+성인 울음, 아기녹음 재생+성인발성에서도 child와parent가 함께 높을 수 있다. 짧은 YAMNet anchor가 하나 있다고 넓은창 전체의 generic crying을 그 아기에게 귀속할 수 없다. **matching baby-specific+YAMNet anchor만으로 parent generic veto를 해제하는 변경은 현재 근거로 채택을 추천하지 않는다.** 별도 mixed-speaker/TV 사건정답·false-positive 검증을 마친 뒤의 연구 실험으로 남긴다.

## 1. 모델 라벨과 출력의 의미

| 원문 사실 | 현재 보류에 주는 의미 |
|---|---|
| YAMNet은 AudioSet 521개 일반 소리 사건의 MobileNet v1 모델이며 output은 logistic layer다. [공식 README](https://raw.githubusercontent.com/tensorflow/models/master/research/audioset/yamnet/README.md) | 점수는 아기 의도 분포나 한국 가정 영아에 대해 보정된 확률이 아니다. |
| AST의 학습 코드는 BCE 설정에서 `BCEWithLogitsLoss`를 사용하고 validation은 각 출력에 sigmoid를 적용한다. [공식 traintest.py](https://raw.githubusercontent.com/YuanGongND/ast/master/src/traintest.py#L51) | `Baby cry`/`Baby laughter`/`Babbling`가 함께 높아도 배타적 클래스 확률의 충돌이 아니다. 여러 실제 사건, 닮은 음향, 모델 오류 중 어느 것인지는 별도 검증이 필요하다. |
| AudioSet의 `Babbling`은 conventional speech의 구조·의미가 없는 인간의 speech-like sound이며 Speech 아래에 놓인다. [ontology](https://research.google.com/audioset/ontology/babbling_1.html) | 모든 영아 noncry 소리·옹알이 유형을 포괄하거나 6–18개월 전용임을 보장하지 않는다. |
| Babbling 자료는 전체862 videos/2.4h, balanced train60, eval60, unbalanced742이다. 페이지의9/10 라벨 검수는 모델 성능이 아니다. [공식 class breakdown](https://research.google.com/audioset/dataset/babbling.html) | 상대적으로 좁은 해당 클래스와 약한 사건 annotation은 누락의 가능한 배경이다. 이것이 현재 실패의 직접 원인이라는 인과 결론은 낼 수 없다. |
| `Child speech, kid speaking`은 성인과 다른 아동 목소리이며 연령 범위를 지정하지 않는다. [ontology](https://research.google.com/audioset/ontology/child_speech_kid_speaking_1.html) | 이 라벨 하나를 영아 신원 확인으로 쓰지 않는다. |
| `Hubbub, speech noise, speech babble`와 `Babbling`은 별도 output이다. [YAMNet class map](https://raw.githubusercontent.com/tensorflow/models/master/research/audioset/yamnet/yamnet_class_map.csv) | 부분 문자열 `babbl`로 묶거나 군중 말소리를 영아 옹알이로 승격하지 않는다. |

**추론:** 존재 여부를 학습한 head와 사건 종류를 학습한 head는 질문이 다르다. Baby-specific 사건의 불확실성은 중립적인 존재 관찰과 공존할 수 있다. 그러나 presence head의 높은 출력은 그 사건 점수를 교정하는 정답이 아니다. 두 앱 head는 AST encoder를 공유하므로 동의가 독립 센서/독립 모델 확인이 되지 않는다. 앱의 caption head에는 weak caption 자료라는 제한도 있다. [현재 보완 계약](20261009-corroborated-vocal-contract.md), [head 라벨 감사](20261007-infant-head-label-evidence.md).

## 2. 입력·시간창을 혼동하지 않을 것

**YAMNet 원본 Keras:** 16kHz mono, 25ms STFT/10ms hop, periodic Hann, magnitude→64 mel bins(125–7500Hz), `log(mel + .001)`, 96x64/0.96s patch. 첫 patch에는0.975s 파형이 필요하다. [README](https://raw.githubusercontent.com/tensorflow/models/master/research/audioset/yamnet/README.md), [features.py](https://raw.githubusercontent.com/tensorflow/models/master/research/audioset/yamnet/features.py). 원본 params의 기본 patch hop은0.48s이며 공식 주석은 hop 축소가 패치 수·연산을 늘리고 성능 변화 가능성이 있다고만 설명한다. 회복을 보장하지 않는다. [params.py](https://raw.githubusercontent.com/tensorflow/models/master/research/audioset/yamnet/params.py).

**현재 MediaPipe 경로:** 앱 dependency는 `tasks-audio:1.0.0`이다. 같은 태그의 graph는 모델 input의 sample count와 metadata의 sample rate로 `AudioToTensorCalculator`를 설정하며 overlap을 지정하지 않는다. [v1.0.0 graph](https://raw.githubusercontent.com/google-ai-edge/mediapipe/v1.0.0/mediapipe/tasks/cc/audio/audio_classifier/audio_classifier_graph.cc#L91). [같은 태그 proto](https://raw.githubusercontent.com/google-ai-edge/mediapipe/v1.0.0/mediapipe/calculators/tensor/audio_to_tensor_calculator.proto#L27)는 overlap 기본0, [calculator](https://raw.githubusercontent.com/google-ai-edge/mediapipe/v1.0.0/mediapipe/calculators/tensor/audio_to_tensor_calculator.cc#L254)는 overlap 미지정 시 `frame_step=num_samples`이며 tail을 zero-pad한다. 따라서 앱의 알려진15600-sample input이면975ms 비중첩 창이다. Keras 기본480ms hop을 앱 반환 timestamp 간격이라고 쓰지 않는다. 이 조사에서 AAR native binary를 재분해하지는 않았다.

**실행 가능한 창 실험:** baseline 전체 입력을 유지한 채 반창7800samples(487.5ms) 뒤에서 별도 phase로 한 번 재분류할 수 있다. 새 gain/필터/weight 없이 경계에 갈라진 사건을 다른 위치에서 보는 제한된 실험이다. 같은 phase 내부의 비중첩 창만 지속 조건에 사용하고, baseline과 shifted 창을 합쳐 반복 횟수·누적시간을 늘리지 않는다. baseline의 다른 발성/성인/TV 충돌은 버리지 않는다. 출력 시작 시각은 창의 시작이지 발성 onset이 아니다. Long ms 정수에서487.5ms→487ms는 truncation이며 정밀 동기 근거가 아니다.

**AST:** 공식 dataloader는 Kaldi fbank에서128mel·10ms frame shift·hanning·dither0를 사용하고1024 frames로 자르거나 raw fbank0을 pad한 뒤 `(fbank-mean)/(2*std)`로 정규화한다. [공식 source](https://raw.githubusercontent.com/YuanGongND/ast/master/src/dataloader.py#L117). 현재 upstream [HF processor config](https://huggingface.co/MIT/ast-finetuned-audioset-10-10-0.4593/raw/main/preprocessor_config.json)의16kHz/128mel/1024/mean−4.2677393/std4.5689974는 텍스트 GET으로 확인했다. web reader는 실패했지만 PowerShell GET은 성공했다. 현재 앱 `AstFeatures.kt`는 해당 고정 HF frontend를 구현하며 YAMNet feature와 호환되지 않는다. `AstAudioAnalyzer.windowRanges`는 최대10초창/5초간격에 마지막창을 추가하여20초 전체를 덮는다. 이는 clip 단위의 넓은 관찰이며100ms 발성 타임라인이 아니다.

## 3. 새 중립 경로의 구체적 계약

현재 [InfantVoiceSupportPolicy.kt](../../outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/InfantVoiceSupportPolicy.kt)는 전체 AST 창에서 contrary 중 하나라도 .30 이상이면 return한다. 이 때문에 baby-specific 사건도 generic adult/TV 경계와 같은 veto가 된다. 현재 코드 관찰이지 새 제품 결과가 아니다.

다음 조건을 묶어 기존 성공 경로 뒤에 제한적으로 시험한다.

1. 기존 성공 음성 문장·시간범위가 먼저 선택된다. 새 경로는 현재 `INFANT_UNCONFIRMED`/`MIXED_VOCAL` 대상 안에서만 동작한다.
2. actual YAMNet infant 후보 기존.35 이상이 유효한 창 안에 존재하고 해당 창의 RMS .003 이상이다. generic speech·face·mouth motion은 anchor를 만들지 않는다.
3. 같은 AST 창에서 infantPresence와captionPresence가 모두 기존.80 이상이고 finite이며 RMS .003 이상이다. 다른 시간의 높은 presence/성인 음량을 빌리지 않는다.
4. 기존 전체 AST 실행 coverage·순서·score 유효성은 유지한다. **부분결과에서는 모든 후보 anchor가 채택 범위에 포함돼야 하는 조건을, 채택 범위마다 실제 anchor가 있어야 하는 조건으로 명시적으로 바꾼다.** 채택하지 못한 anchor·창은 보류로 남긴다. 이것은 기존 계약 변화이므로 별도 회귀·반례 검증이 필요하다. AST 실패/취소는 존재 부재로 바꾸지 않는다.
5. baby-specific cry/laughter 높은 점수는 해당 신규 경로의 `kindUncertain=true`를 유지하는 근거로만 쓴다. 특정 종류·기분·욕구·반복횟수·전체 지속을 만들지 않는다.
6. generic crying/laughing/screaming 등의 기존 .30 veto는 우선 창별로 유지하여 그 창을 제외한다. TV는 전체 veto로 유지한다. parent generic 점수를 baby-specific anchor로 설명해 지우지 않는다.
7. 부모에게는 일부 시간창의 아기 발성 단서와 나머지 창의 종류 미확정을 함께 표시한다. 채택 창은 모델 평가 범위이며 전체 연속 발성/확정 무충돌 구간이 아님을 명시한다. 종류 불확실성을 숨긴 옹알이 결과로 바꾸지 않는다.

이 조건은 다른 강한 음성도 한 넓은 창 안에 존재할 때 정확한 화자를 증명하지 못한다. 영아가 존재하는 영상이나 playback된 baby sound도 real live infant 주체와 구분하지 못한다. 입 움직임을 음성 승인으로 대신하지 않는다.

## 4. 채택 전 검증

| 필요한 검증 | 실패하면 무엇이 남는가 |
|---|---|
| 같은 frozen 입력의 before/after 실제 추론. mayra_context와 기존 성공·보류53/28조건에서 모델 출력/원음 hash·baseline 문장 exact 비교 | 코드 정책 재생만으로 실제 가청 발성을 회복했다고 보고하지 않는다. 같은 원본 변형을 독립 아기 수로 세지 않는다. |
| Baby cry/laughter만 .30을 넘는 경우, generic contrary/TV .30 경계, 낮거나 누락된 head·RMS·다른 시간 anchor·불완전 coverage 단위 반례 | baby-specific 예외가 blanket veto 제거가 되지 않도록 확인한다. score 반례 통과는 실제 성인 오인0의 증거가 아니다. |
| 실제 adult speech/laugh/cry/scream, TV baby sound, music/noise/silence 대조의 신규 아기 출력0 | 두 head가 높아도 성인 오인이 생기면 정책을 채택하지 않는다. 특히 알려진 성인비명 오류를 보존한다. |
| 외부 정답이 없는 새 회복은 음원을 모델점수를 가린 사람이 사건·화자·불확실 구간으로 검수 | .99를99% 정확도로 표시하거나 noncry 정답으로 세지 않는다. |
| shifted phase를 시험하면 기존 성공/보조성공·취소/실패 복구 보존, 1초/tail 및 timestamp 확인 | 두 phase의 중첩 창을 독립 반복 증거로 세지 않는다. |

이 조사는 위 실행을 하지 않았다. 이번 판단을 일반 recall/precision, 실기기 성능, Play 제공, 의료·욕구 정확도의 입증으로 사용하지 않는다.

## 5. 대체 소형 모델의 신규성·권리 확인

광범위 모델 재조사를 반복하지 않고 기존 감사에 나온 공급의 ref/metadata만 확인했다. **새 즉시 실행 후보0개**다. 이 결론은 조사 범위의 결과이며 모든 후보 부재가 아니다.

| 후보 | 이번 확인·적용 제약 |
|---|---|
| LittleBeats | [HF metadata API](https://huggingface.co/api/models/lijialudew/wav2vec_LittleBeats_LENA?blobs=true)의 revision `b3250219066482f571ef3868413b0b0f1ad92502`가 이전과 동일. head3,588,884B 외에 encoder377,568,539B가 필요하며 card는 generic `openrail`. 새 tiny export·weight grant 미확보. [입력·권리 감사](20261007-littlebeats-input-license-followup.md)의 exact normalize flag/recipe/license blocker 유지. |
| W2V2-LB Long Transformer | [저자 repo](https://github.com/xulinfan/w2v_LB_Long_Transformer)의 현재 commit `a36fe68541db995c244a8133f9187e8ee7875b2e`가 기존과 동일. [선행 감사](20261005-infant-specialist-followup.md)의 큰 encoder·단일5-way head·weight grant/Android 미확인 유지. 새 검색에 나온 논문을 새 compact artifact로 세지 않았다. |
| VocalBaby | [현재 commit API](https://api.github.com/repos/arunps12/VocalBaby/commits/main)는 `8d214be476e9819322622cb08966faf00fdaeb3b`로 기존과 동일. 5.43MB XGBoost head만 작으며 eGeMAPS88/openSMILE pipeline이 필요하다. [openSMILE 공식 이용 조건](https://github.com/audeering/opensmile#licensing)은 commercial product에 별도 조건을 둔다. MIT code가 이를 해제하지 않는다. [head 감사](20261007-infant-head-label-evidence.md)의 median-imputer/feature order/label/split/weight 권리 계약 미해결. |
| voc2vec | [기존 공식 artifact 감사](20261005-infant-repos-audit.md)의378–380MB HuBERT/W2V2 representation은 infant noncry 전용 head가 아니다. 새 classifier/export 근거를 확보하지 못해 body를 취득하지 않았다. |

Quantization은 일반적으로 파일·연산을 줄일 수 있어도 새로운 영아 종류 라벨이나 독립 정답을 제공하지 않는다. 위 artifact의 정확한 frontend/head/권리를 갖추고 float 대비 calibration·adult 대조·기기 측정이 있어야 양자화된 모델의 적용 여부를 판단할 수 있다. XGBoost/eGeMAPS head 크기를 MobileNet int8 모델 크기로 환산하거나 encoder 없이 사용한다고 계획하지 않는다.

## 조사 경계

공식 Google/TensorFlow/MediaPipe/AST 소스·ontology, HF processor/모델 metadata, 저자 GitHub ref만 외부 조회했다. 일부 HF web-reader와 PMC 본문 조회는 실패했으며 실패 페이지 내용을 근거로 쓰지 않았다. 로컬 retained media/feature body·사용자 아기 자료를 열거나 업로드하지 않았다. 모델/음원 body 다운로드·새 대형 의존성·Elice·연락·빌드·ADB·git mutation·배포0. 소유 변경 파일은 이 문서 하나다.
