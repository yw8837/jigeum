# EfficientSED 시간축 계약과 영아 발성 보완 평가 제안

2026-10-06 · research-only · 고정 upstream source revision `e3449bfd1f4922b81d82410ed842653f462bfca8`

## 범위와 고정 근거

대상은 `fmn10+tf-256_advanced-kd-weak-strong.pt`다. 로컬에 고정해 둔 공식 소스는 `C:/Users/최용우/.agent-reach/agattok-sed-20261005/source`이며, [`tools/research/efficientsed-models.json`](../../tools/research/efficientsed-models.json)의 revision과 파일 SHA256 manifest로 파일을 식별한다. 관련 앞선 기록은 [Kotlin frontend 비교](20261006-efficientsed-kotlin-frontend.md)와 [시간축 모델 후속 조사](20261005-temporal-model-followup.md)다.

이 문서는 upstream 코드가 정의한 입력·출력 처리와 그로부터 따르는 평가상 주의점을 고정한다. 아래 시간 간격은 모델 출력 인덱스를 초로 환산한 명목 격자다. 공식 자료는 이 격자가 실제 사건 onset/offset을 그 오차로 맞춘다는 보증을 하지 않는다. 아기 의도·욕구나 의료 상태의 판별 정확도도 주장하지 않는다.

## 공식 처리 계약

| 단계 | upstream에서 확인되는 동작 | 연구상 의미 |
|---|---|---|
| 입력 | `inference.py`는 오디오를 mono 16 kHz로 읽고 10초 단위로 나눈다. 각 완전 창은 160,000 samples다. | 재표본화·채널 혼합의 품질은 모델 출력 계약과 별도로 관리한다. 앱 frontend에는 정확한 10초 창을 넘긴다. |
| 짧은 마지막 창 | 마지막 창이 10초보다 짧으면 오른쪽을 0으로 채워 160,000 samples로 만든다. | padding 구간도 모델에 들어가므로, 실제 입력 길이와 padding 비율을 표본 메타데이터에 보존하고 지표는 원본 유효 길이에 한정해야 한다. |
| mel frontend | preemphasis `[-0.97, 1]`, 16 kHz, 400-sample Hann window, 512 FFT, 160-sample hop, 128 Kaldi mel bins, center padding 및 log-power. eval에서는 augmentation 범위를 끄며 `fmax=None`은 코드상 7000 Hz가 된다. | 160-sample hop은 10 ms frontend frame 간격이다. 동일한 frontend와 정규화를 고정해 비교해야 한다. 자세한 이식 비교와 수치 한계는 선행 frontend 문서 참조. |
| backbone과 sequence 길이 | FrameMN은 시간축 embedding sequence를 만들고 `PredictionsWrapper`는 이를 길이 250으로 맞춘다. 초과하면 adaptive average pooling, 부족하면 linear interpolation한다. `fmn10 + tf-256`의 TF sequence model은 이 길이의 각 10초 창 안에서 동작한다. | 250개 위치는 명목상 10초당 250칸, 즉 40 ms/칸이다. TF 문맥은 각 10초 호출 내부에 한정된다. 두 창을 이어 붙여도 TF가 20초를 한 번에 본 것은 아니다. |
| strong/weak heads | 기본 `linear` head에서 strong head는 각 위치의 447개 class logits를 내고, weak head는 시간축 평균 embedding에서 class logits를 낸다. wrapper는 strong logits를 sigmoid 처리하지 않고 반환한다. 공식 inference는 창들을 이어 붙인 뒤 strong 값에 sigmoid를 적용한다. | 앱/연구 어댑터가 strong에 sigmoid를 한 번만 적용하고, weak head를 시간별 strong 출력으로 오인하지 않는지 확인한다. 원시 logit과 확률을 명시적으로 구분한다. |
| labels | strong label 목록은 `as_strong_train_classes`이며 순서가 output channel 순서를 정한다. 목록에 `Babbling`, `Baby cry, infant cry`, `Baby laughter`, `Child speech, kid speaking`, `Laughter` 등이 각각 존재한다. | 라벨 문자열을 별도 임의 정렬하지 말고 pinned 목록 순서를 그대로 유지한다. 이들은 소리 종류 라벨이며 영아의 원인·감정·욕구를 나타내지 않는다. |
| 20초 입력 | 공식 inference는 10초씩 자르고 창별 prediction을 time axis로 concatenate한다. 20초 완전 입력은 창 두 개와 출력 250×2개가 된다. | 시간 인덱스는 창별 offset을 더해 순서대로 이어야 한다. 창 내부 모델 문맥은 경계에서 끊기며, 별도 overlap/blend는 upstream 예제 코드에 없다. |
| 마지막 출력 절단 | 공식 inference는 마지막 partial PCM 창은 zero-pad하지만, decoding에는 원래 `audio_len`을 담은 encoder를 전달한다. | 모델은 padding 구간 점수도 생성한다. 제품 결과/평가에 padding-only 시간대를 포함하지 않도록 실제 길이로 자른 뒤 집계한다. |

### 시간 인덱스를 읽을 때 주의할 점

- wrapper 출력 shape은 batch×447×250이다. 인덱스 `j`를 `창 시작 + 0.04×j`초의 중심점이라고 upstream이 정의하지 않는다. 출력 격자는 구간 탐색 해상도를 나타내는 참조로만 쓴다.
- 10초 경계에서는 인접 창의 40 ms 격자 칸들이 시간상 이어지지만, 입력 전처리와 TF 문맥은 창마다 다시 시작한다. 경계를 가로지르는 한 발성이 잘리거나 두 창에 다른 문맥으로 나타나는 조건을 따로 평가한다.
- 짧은 마지막 구간을 pad하면 모델은 해당 창의 전체 250칸을 계산한다. 10초 미만 단독 입력도 공식 코드에서는 마지막 창이므로 오른쪽을 0으로 채운다. 후처리는 원본 길이에서 자른다. 왼쪽 padding이나 가운데 배치를 새로 도입하면 upstream과 다른 정책이므로 별도 비교 항목으로 기록한다.
- 마지막 창 끝의 zero padding은 “무음인 실내 녹음” 표본과 같지 않다. padding 비율, 사건이 끝·경계에 닿는지, 마지막 유효 프레임과 padding-only 출력 위치를 기록해 길이 효과를 분리한다.
- 40 ms 격자는 격자 양자화, receptive field, pooling, TF 문맥을 포함한 사건 경계 정확도 보증이 아니다. 이 문서만으로 경계 오차의 수치를 주장할 수 없다.

## 연구 전용 평가 제안

목표는 기존 YAMNet/AST 경로에서 놓친 실제 영아 발성의 추가 회복이 있는지, 성인 TV·대화·웃음·울음 및 겹침에서 오인이 얼마나 늘어나는지 확인하는 것이다. 이미 살펴본 공개/약국 사례를 새로운 held-out 성공 표본으로 다시 세지 않는다. 원본·특징·점수는 온디바이스에 둔다. 외부 서비스로 보내지 않으며 Elice는 사용하지 않는다.

### 표본과 비교 설계

1. **실제 영아 회복:** 사전 등록한 독립 녹음에서 보호자가 구간을 확인한 babbling, cry, laughter를 기록한다. 같은 아기·같은 세션의 파생 구간은 독립 표본으로 세지 않는다. 기존 앱이 놓친 구간을 우선하되, 새 모델을 보고 표본이나 판정을 고르지 않는다. 앱 기존 detector와 후보를 같은 PCM 및 같은 유효 시간으로 비교한다.
2. **성인·TV 오인:** 성인 말소리·웃음·울음, TV/영상 속 아기 음성 및 비아기 소리를 포함한다. 실제 아기가 없는 부정 대조와 영상 속 아기 음성이 있는 별도 대조를 구분해 `Baby cry`/`Babbling` 등의 허위 활성 빈도를 보고한다. 방·기기·콘텐츠 반복을 독립 화자로 취급하지 않는다.
3. **겹침:** 아기+성인, 아기+TV, 아기+생활 소음처럼 실제 동시 발성이 있는 경우를 별도 층으로 둔다. 사건별 recall과 부정 class 활성, 기존 detector 대비 변화량을 함께 낸다.
4. **무음·일반 소리:** silence, fan/mechanical noise, music 및 일반 환경음을 포함하되, padding-only frame을 실제 녹음 무음 대조에 섞지 않는다.
5. **고정 판정법:** 후보 점수와 라벨을 확인하기 전에 event matching 허용폭, class-specific threshold 선택 규칙, 최소 사건 길이, 중복 사건 처리법을 문서화한다. threshold 최적화에 쓴 가족·세션은 최종 평가에 재사용하지 않는다. 공식 기본 threshold를 앱 기준으로 간주하지 않는다.

### 필수 시간축 분석

- **새 회복:** 누락 사건 중 후보가 추가로 검출한 비율과 precision/false activations를 class 및 표본 단위로 함께 보고한다. 전체 frame 수나 창 수를 표본 수처럼 쓰지 않는다.
- **성인 TV 오인:** 실제 아기 없는 TV·대화·웃음 장면에서 infant-label false activation을 오디오 길이당 및 독립 장면당 보고한다. 아기 음성이 TV에 포함된 장면은 별도 조건이다.
- **경계:** 같은 실제 발성을 10초 창 내부, 10초 경계 직전/직후, 두 창을 가로지르는 조건으로 구분한다. 원본의 녹음 시각 주석과 검출 구간 간 onset/offset 오차 분포를 측정하고, 40 ms를 예상 오차나 허용 오차로 선결하지 않는다.
- **padding:** 10초 정확 길이, 짧은 마지막 부분창, 사건이 유효 PCM 끝에 닿는 경우를 분리한다. 실제 유효 길이로 점수를 자른 결과와 padding 포함 집계가 다른지 기록한다. 각 case의 pad sample 수와 비율을 저장한다.
- **20초 stitching:** 10초 한 창, 같은 20초를 두 창으로 나눈 호출, 경계 위치만 달리한 crop을 비교한다. 두 번째 창 출력 offset, 중복·누락 위치, 경계 score jump와 event continuity를 조사한다. 이어 붙인 출력은 독립적인 20초 단일 문맥 결과라고 설명하지 않는다.
- **불확실성:** 아기/세션 단위 묶음 bootstrap 또는 이에 맞는 신뢰구간을 사용한다. 소수 사례에서의 point estimate를 일반 영아 성능으로 일반화하지 않는다.

### 결과 해석의 문턱

이 후보는 실제 새 영아 누락을 반복 가능하게 회복하고, 사전 고정한 성인 TV/겹침 대조에서 오인 비용이 수용 가능한 경우에만 연구상 보완 후보로 남긴다. 경계·padding 조건에서 이득과 손실을 따로 공개하고, 표본 수와 미해결 조건을 함께 기록한다. 온디바이스 실기기 성능, 앱 UX, 권리 검토, 제품 승인 및 별도의 보호자 표현 검토는 이 데이터셋 비교만으로 충족되지 않는다.

## 고정 upstream 원문 링크와 코드 위치

모든 GitHub 링크는 같은 commit `e3449bfd1f4922b81d82410ed842653f462bfca8`을 가리킨다. 줄 번호는 위에서 식별한 로컬 pinned copy에서 직접 확인했다.

| 원문 | 확인 위치 | 근거 |
|---|---|---|
| [`inference.py`](https://github.com/theMoro/EfficientSED/blob/e3449bfd1f4922b81d82410ed842653f462bfca8/inference.py#L62-L107) | 62–107 | eval mode, 16 kHz, 10초 segment, 마지막 우측 zero padding, 창별 strong 연결, sigmoid |
| [`fmn_wrapper.py`](https://github.com/theMoro/EfficientSED/blob/e3449bfd1f4922b81d82410ed842653f462bfca8/models/efficient_cnns/fmn/fmn_wrapper.py#L6-L40) | 6–40 | FMN mel frontend 설정, 128 bins, 16 kHz, hop 160, 400/512, 10초 입력 모델 경로 |
| [`preprocess.py`](https://github.com/theMoro/EfficientSED/blob/e3449bfd1f4922b81d82410ed842653f462bfca8/models/transformers/frame_passt/preprocess.py#L47-L73) | 47–73 | `fmax=None` 해석, hop, window, preemphasis 상수 |
| [`preprocess.py`](https://github.com/theMoro/EfficientSED/blob/e3449bfd1f4922b81d82410ed842653f462bfca8/models/transformers/frame_passt/preprocess.py#L88-L144) | 88–144 | eval augmentation 해제, STFT center 설정, power→Kaldi mel→log, 정규화 |
| [`prediction_wrapper.py`](https://github.com/theMoro/EfficientSED/blob/e3449bfd1f4922b81d82410ed842653f462bfca8/models/prediction_wrapper.py#L72-L98) | 72–98 | 기본 447 class/250 sequence, TF dimension/config defaults |
| [`prediction_wrapper.py`](https://github.com/theMoro/EfficientSED/blob/e3449bfd1f4922b81d82410ed842653f462bfca8/models/prediction_wrapper.py#L291-L306) | 291–306 | sequence model 없음 fallback과 strong/weak linear heads |
| [`prediction_wrapper.py`](https://github.com/theMoro/EfficientSED/blob/e3449bfd1f4922b81d82410ed842653f462bfca8/models/prediction_wrapper.py#L398-L432) | 398–432 | sequence length 강제, strong logits transpose, weak pooled head |
| [`audioset_classes.py`](https://github.com/theMoro/EfficientSED/blob/e3449bfd1f4922b81d82410ed842653f462bfca8/data_util/audioset_classes.py#L1-L125) | 1–125 | train label 목록 앞부분과 영아/아동 label 구분 |
| [`config.py`](https://github.com/theMoro/EfficientSED/blob/e3449bfd1f4922b81d82410ed842653f462bfca8/config.py#L50-L54) | 50–54 | advanced-KD weak/strong checkpoint명과 공식 asset 경로 |
| [`README.md`](https://github.com/theMoro/EfficientSED/blob/e3449bfd1f4922b81d82410ed842653f462bfca8/README.md#L172-L177) | 172–177 | advanced KD 모델과 AudioSet Strong PSDS1 결과의 맥락 |
| [`models/efficient_cnns/fmn/model.py`](https://github.com/theMoro/EfficientSED/blob/e3449bfd1f4922b81d82410ed842653f462bfca8/models/efficient_cnns/fmn/model.py#L181-L195) | 181–195 | FMN 출력이 batch×time×channel sequence임을 확인 |

## 보조 프로젝트 기록

- [EfficientSED Kotlin frontend 비교](20261006-efficientsed-kotlin-frontend.md): 합성 10초 입력의 mel 및 ONNX 출력 parity와 그 검증 범위. padding·경계 인식 품질은 그 parity가 다루지 않았다고 명시한다.
- [시간축 모델 후속 조사](20261005-temporal-model-followup.md): 후보 선정과 기존 CPU 스크리닝의 한계. 동일 원본/기존 개발 자료는 새 독립 영아 회복 증거가 아니다.
- [`efficientsed-models.json`](../../tools/research/efficientsed-models.json): source revision, 원문 파일 digest 및 공개 checkpoint asset의 고정 manifest.
