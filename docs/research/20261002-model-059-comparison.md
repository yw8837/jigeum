# YAMNet 원본 float 모델 비교 — 0.5.9 판단 근거

2026-10-02 KST. 사용자가 제공한 영상의 발성 인식 누락을 줄일 후보로, 현재 탑재 모델과 TensorFlow 공식 원본형 TFLite를 **실제 로컬 추론**으로 비교했다. 프로덕션 asset·임계값은 이 작업에서 바꾸지 않았다.

**결론: 이번 업데이트의 음성 모델은 현재 탑재본을 유지한다.** 원본 float 모델도 보고된 두 영상의 4개 구간을 모두 보류했고, 같은 판정 기준에서는 기존 공개 웃음·울음 입력이 새로 보류되는 회귀가 있었다. 겹치는 창을 추가하는 것만으로도 해당 누락은 회복되지 않았다. 이 결과를 모델 일반 정확도, 영아 의도 정확도, 모든 float 모델의 우열로 일반화하지 않는다.

## 비교 대상과 권리·호환성

| 항목 | 현재 classification 모델 | 공식 원본형 fixture |
|---|---|---|
| 출처 | 현재 앱 `yamnet.tflite` | TensorFlow TFLite Support의 `yamnet_tfhub.tflite` [1] |
| bytes | 4,126,810 | 16,096,668 |
| SHA-256 | `4d8b4a53282dc83ef04e3e7dbc4fbc98082e34e44ed798e16c3a0cdd4c584faf` | `141fba1cdaae842c816f28edc4937e8b4f0af4c8df21862ccc6b52dc567993c3` |
| 입력 | FLOAT32 `[15600]` | FLOAT32 동적 `[-1]`; 비교 시 `[15600]`으로 resize |
| 출력 | FLOAT32 `[1,521]` | scores `[N,521]`, embeddings `[N,1024]`, spectrogram `[M,64]` |
| 내부 tensors | FLOAT32 16, INT32 42, COMPLEX64 2, INT8 61 | FLOAT32 116, INT32 88; INT8 없음 |
| metadata | 음성·label 연결이 있는 현재 Tasks 호환 모델 | `min_runtime_version`만 있음. label ZIP·음성 metadata 없음 |
| 권리 근거 | 기존 모델의 Apache-2.0 표시 | 공식 저장소 LICENSE Apache-2.0 [2]. GitHub API의 fixture blob `f19a3fbcadc54ab4d3f24ff35a050f60d54a8467`, 크기와 다운로드 본문을 확인 |
| 앱 대체 호환 | 현재 MediaPipe Tasks에서 실행 중 | 동적 입력·3출력·metadata 차이 때문에 그대로 교체 불가. 이번에 Android 대체 로딩은 시험하지 않음 |

원본형의 연산자는 SHAPE, STRIDED_SLICE, MAXIMUM, SUB, CAST, DIV, CEIL, MUL, ADD, PACK, PAD, ZEROS_LIKE, SPLIT_V, RESHAPE, FLOOR_DIV, RANGE, GATHER, FULLY_CONNECTED, SQRT, LOG, CONCATENATION, CONV_2D, DEPTHWISE_CONV_2D, MEAN, LOGISTIC이다. 로컬 LiteRT CPU에서는 추가 custom operator 없이 실행됐다. 이것이 앱에 사용 중인 이전 Android 런타임에서의 호환 검증을 대신하지 않는다.

두 모델은 내부 정수 정밀도만 다른 동일 그래프가 아니다. classification 변형은 ReLU6·고정 길이 등 구조 차이가 있다. 자세한 기존 원천 분석은 [내부 양자화 조사](20261002-sound-appropriateness.md)를 따른다. 이번 결과에서 성능 차이 전체를 양자화 탓으로 돌리지 않는다.

## 고정한 조건과 교차 검증

- 로컬 Python 3.12 / `ai-edge-litert==2.2.0`, CPU XNNPACK, interpreter당 `num_threads=1`을 사용했다. 별도 가상환경은 Git 제외 `work/model-059/venv`에만 만들었다. Android·Gradle·ADB를 이 비교 담당에서 실행하지 않았다.
- 공개 원음 11개, 그 원음의 변형 및 합성 대조 11개, 제공 영상에서 이미 추출한 기준 구간 5개를 비교했다. **27개 파일은 27명의 아기나 독립 사례가 아니다.** 보호자가 보고한 4개 실패 구간의 화자·발성 종류는 독립 라벨링으로 확정되지 않았다.
- 같은 16 kHz mono PCM에 기존 앱과 같은 DC 제거·80 Hz Butterworth 필터를 적용했다. 추가 gain이나 새로운 노이즈 억제는 적용하지 않았다. 마지막 불완전 창은 0으로 채웠다.
- 기본 비교는 두 모델 모두 15,600 sample 입력, 15,600 sample 간격(975 ms)이다. label은 현재 asset의 521개 공식 label 순서를 사용했다. 시간 투표·활동량·보류 기준은 기존 `SoundTimeline`/`EvidenceInterpreter`의 Python 재현으로 고정했다. **새 모델에 적합하도록 임계값을 조정한 실험이 아니다.**
- 재현 신뢰도를 확인하려고 기존 Android 최종 probe 로그와 현재 모델 Python 결과를 대조했다. 공개/합성 22개 모두 사건·보류 판정이 같았고, 기존 로그에 기록된 집계 cue 점수의 최대 차이는 **0**이었다. 새 float 결과는 아직 Android 추론 결과가 아니다.
- 창 간격만 7,680 samples(480 ms)로 줄인 비교도 별도로 했다. 원본형의 동적 전체 파형 추론과 고정창 추론의 공통 frame은 27개 모두 521개 출력의 최대 절대 차이가 **0**이었다. 고정창 루프가 원본보다 끝부분 0-padding 창을 파일당 하나 더 만들었으며, 그 창을 제거해도 이 자료의 사건·보류 판정은 같았다. 다른 입력에서 같다고 일반화하지 않는다.

원음·파생 PCM·프레임별 점수·사용자 파일 경로·개별 미디어 해시는 Git에 넣지 않는다. 재현 스크립트와 상세 증거는 Git 제외 `work/model-059/`에 있다:

- `compare.py`, `comparison.json`, `scores.npz`: 두 모델 × 두 hop의 입력별 점수·판정.
- `check_baseline.py`, `baseline-validation.json`: 기존 Android와 22개 판정·집계 점수 비교.
- `check_native_overlap.py`, `native-overlap-validation.json`: 원본 동적 추론과 고정창 수치 비교.
- `timing.json`, `run.log`: 추론 시간 및 전체 실행 결과.

## 같은 975 ms 간격의 결과

| 공개 원음 | 현재 모델 | 원본 float | 읽는 방법 |
|---|---|---|---|
| `cry` | 울음 | 보류 | 높은 울음 점수 외에 해소되지 않은 다른 발성 후보가 생김 |
| `laugh` | 아기 웃음 | 보류 | 충분한 연속/강한 웃음 지지가 성립하지 않음 |
| `babble` | 보류 | 보류 | 이 옹알이 사례는 양쪽 모두 미회복 |
| `laugh_babble` | 보류 | 보류 | 다른 사건 후보가 함께 나타남 |
| `almost_laugh` | 여러 아기 소리 | 여러 아기 소리 | 혼합 사건으로 유지 |
| `cry_six_month` | 여러 아기 소리 | 울음 | 이 사례에서는 울음으로 좁아짐; 독립 개선율 아님 |
| `babble_seven_month` | 어린 말소리 | 어린 말소리 | 유지 |
| 일반 대화·청소기·피아노·일반 웃음 4개 | 모두 보류 | 모두 보류 | 이 4개 대조군에서 새 아기말 오생성 없음 |

현재 모델의 공개 원음 응답은 5/11, 원본 float는 3/11이었다. 이는 대조군까지 섞인 자료의 **응답 개수**로, 높을수록 좋은 정확도 지표가 아니다. 합성 무음·tone·noise·rumble·DC까지 합친 비아기 대조 9개는 두 모델·두 hop 모두 보류했다. 대조군 9개로 오생성률 0%를 보장할 수는 없다.

### 제공 영상의 기준 구간

개별 프레임이나 가정의 녹음 정보 없이 결과만 요약한다. 표의 점수는 4개 아기 관련 class(아기 울음·아기 웃음·옹알이·어린 말소리) 중 모든 창의 최고점이며, **신뢰 확률이나 의도 확률이 아니다.**

| 기준 구간 | 현재 모델 최고점 | float 최고점 | 현재/float 최종 결과 |
|---|---:|---:|---|
| 실패 영상 A 앞부분 | 0.0586 | 0.1759 | 보류 / 보류 |
| 실패 영상 A 뒷부분 | 0.0195 | 0.0076 | 보류 / 보류 |
| 실패 영상 B 앞부분 | 0.0039 | 0.1087 | 보류 / 보류 |
| 실패 영상 B 뒷부분 | 0.1094 | 0.2393 | 보류 / 보류 |
| 울음 비교 영상 | 0.9180 | 0.9329 | 울음 / 울음 |

480 ms 간격에서도 4개 실패 구간은 모두 보류했다. 원본 float의 앞부분 한 구간에서 어린 말소리 최고점이 0.3279까지 올라갔지만 단일 최고점을 근거로 통과시키지 않았다. 공개 웃음 원음의 일부 겹치는 창에서는 원본 float의 울음 최고점이 **0.9228**까지 나타났다. 기존 판정은 최종 울음 문장을 내지 않고 보류했지만, 단순 max와 낮은 임계값으로 바꾸면 반대 상황 출력 위험이 있음을 보여 준다.

## 비용·성능 한계

같은 로컬 CPU에서 창 하나의 `invoke()`만 잰 결과다. 모델 로딩·파일 디코딩·필터·Android UI·시각 분석은 포함하지 않는다. 첫 호출을 일부 포함한 진단 실행이며 ARM 실기기의 지연·발열을 예측하는 수치가 아니다.

| 같은 975 ms 간격, 각 247창 | 현재 모델 | 원본 float |
|---|---:|---:|
| invoke 중앙값 | 1.307 ms | 4.056 ms |
| invoke 95백분위 | 3.517 ms | 7.609 ms |
| 전체 invoke 합 | 0.406초 | 1.150초 |

원본 파일은 약 3.9배 크고, 이 호스트의 창당 중앙값은 약 3.1배였다. 480 ms 간격은 관찰 창 수를 247→481로 늘렸다. 누락 회복을 확인하지 못한 상태에서 앱 용량과 분석량을 늘릴 근거가 부족하다. 이 때문에 metadata 변환·대체 asset 배포는 진행하지 않았다.

## 다음으로 검증할 후보

1. **YAMNet 원본의 1,024차원 embedding + 영아 발성용 작은 head.** 이번 실제 파일에서 embedding 출력이 존재하고 실행되는 것을 확인했다. 일반 521-class head를 그대로 교체하는 실험과, 옹알이·작은 발성·끙끙거림을 별도로 학습하는 실험은 다르다. 상용 사용 조건이 명확한 라벨 자료와 가족·세션 단위 분할이 확보되면 가장 작은 추가 실험부터 할 수 있다. 현재 파일 27개로 head를 학습하고 같은 파일에서 성공했다고 보고하지 않는다.
2. **voc2vec를 오프라인 표현/교사 모델로 비교.** [기존 원문 검토](20261001-cry-evidence.md)는 저자의 코드·모델 Apache-2.0 명시, 아기 관련 자료를 포함한 비언어 발성 사전학습을 확인했다. 공개 `voc2vec-hubert-ls-pt`는 약 378 MB의 encoder이며 바로 사용하는 영아 class head나 배고픔 분류기는 아니다. 현재 Android 탑재·전이학습·정확도 개선을 실행한 모델로 세지 않는다.
3. **SpeechMaturity 계열의 영아/아동 발성 분류.** [직전 조사](20261002-private-video-diagnosis.md)의 2025년 연구는 울음·웃음·canonical/non-canonical 발성 분류 및 child-disjunct 평가가 이번 과제와 가깝다. 그러나 상용 배포 가능한 완성 checkpoint·head·자료 사용권을 이번에 새로 확보하지 않았다. 논문 결과만 보고 앱에 모델을 추가할 수 있다고 확정하지 않는다.

이 비교 범위에서 **지금 바로 상용 앱에 넣으면 두 실패 영상을 해결하는 영아 전용 pretrained 모델은 검증하지 못했다.** 다음 모델 채택 기준은 같은 소리를 무조건 한마디로 만드는 것이 아니라, 영아 발성 누락을 줄이면서 대화·음악·무음 및 웃음↔울음 반대 문장을 늘리지 않는 것이다. 이번 업데이트의 안내·영상 관찰·입력 구간 개선과 음성 인식 자체의 개선은 각각 실제 증거로 보고한다.

## 출처

- [1] TensorFlow TFLite Support [공식 원본형 TFLite fixture](https://github.com/tensorflow/tflite-support/blob/master/tensorflow_lite_support/metadata/python/tests/testdata/audio_classifier/yamnet_tfhub.tflite). GitHub API metadata와 다운로드 본문 SHA를 확인했다.
- [2] 같은 저장소의 [Apache-2.0 LICENSE](https://github.com/tensorflow/tflite-support/blob/master/LICENSE).
- [3] [공식 YAMNet 소스·class map·export](https://github.com/tensorflow/models/tree/master/research/audioset/yamnet). 모델 범위는 AudioSet 사건이며 아기 욕구 번역기를 뜻하지 않는다.
- [4] [YAMNet 전이학습 튜토리얼](https://www.tensorflow.org/tutorials/audio/transfer_learning_audio). embedding에서 작은 head를 학습하는 후속 경로. 이번 작업에서 추가 head 학습은 하지 않았다.

공개 원문은 agent-reach의 GitHub/gh 경로로 확인했다. 모델·패키지는 공개 배포처에서 내려받았고, 사용자 PCM은 로컬 실행에만 사용했다. 원본 또는 파생 음성을 외부 API로 보내지 않았다.
