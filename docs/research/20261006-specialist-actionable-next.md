# 영아 전문 task의 실제 다음 비교 — VTC 1

확인일: 2026-10-06. 공개 원천의 metadata/source만 확인했다. **가중치 다운로드·추론·학습·설치·제품 변경은 하지 않았다.**
사용자 아기 원음/파생 feature를 외부로 보내지 않았다. 이 조사에서 파일로 저장한 임시 자료는 없다.

## 결론과 기존 조사와의 차이

다음 내부 로컬 비교는 **원래 VTC 1의 SincNet + LSTM 전문 checkpoint**로 좁힐 수 있다.
기존 [VTC-2/ChildVox 조사](20261004-additional-vocal-candidates.md)의 BabyHuBERT 대형 encoder와 다른 실제 artifact다.
약 4.53MB의 완성된 다섯 출력 모델, 공개 input/window/후처리 계약, 고정 source commit까지 확인했다.
보존 누락 구간의 **아이 목소리 단서 회복 여부**를 기존 YAMNet/AST와 비교하는 데 의미가 있다.
cry/laugh/babble 유형이나 아기 의도, 얼굴 주인, 휴대폰 영상 속 특정 아기를 판별하는 모델은 아니다.

원래 VTC repository의 `new_model` 및 `master`에는 weight를 허락하는 LICENSE가 없다.
하위 pyannote source의 MIT를 checkpoint 권리로 전파하지 않는다. 이 사실은 **제품 배포의 권리 gate**다.
부모 작업자는 사용자 승인에 따라 공개 artifact의 내부 로컬 연구 비교를 별도로 진행한다.
이 문서가 상업 사용/재배포 가능 또는 실제 성능 이득을 확인했다는 뜻은 아니다.

## 1. VTC 1 artifact와 source 고정

| 항목 | 원천에서 확인한 값 |
|---|---|
| 공식 repository | [MarvinLvn/voice-type-classifier](https://github.com/MarvinLvn/voice-type-classifier) — README가 VTC 1.0이라고 명시 |
| 현재 실제 model branch/commit | `new_model` / `9731a9aad21a85d7bc660193386de94f2fb19f76` |
| checkpoint 상대경로 | `model/train/X.SpeakerDiarization.BBT2_LeaveOneDomainOut_paido.train/weights/0100.pt` |
| 정확한 크기 | **4,532,569B** (Git tree metadata) |
| Git blob SHA-1 | `649293f96345748da846ecbdd46538838f1fae25` |
| SHA-256 | 아직 미확인. 다운로드 담당자가 bytes/Git blob 검증 후 계산해야 한다 |
| dependency repository | `MarvinLvn/pyannote-audio` |
| dependency 실제 gitlink | `85b84bc7ee3f44f1f112397f615873f348517541` — branch 최신값을 따라가지 않는다 |

- [고정 weight 직접 URL](https://raw.githubusercontent.com/MarvinLvn/voice-type-classifier/9731a9aad21a85d7bc660193386de94f2fb19f76/model/train/X.SpeakerDiarization.BBT2_LeaveOneDomainOut_paido.train/weights/0100.pt)
- [Git blob API](https://api.github.com/repos/MarvinLvn/voice-type-classifier/git/blobs/649293f96345748da846ecbdd46538838f1fae25)
- [architecture config](https://github.com/MarvinLvn/voice-type-classifier/blob/9731a9aad21a85d7bc660193386de94f2fb19f76/model/config.yml)
- [학습 specs](https://github.com/MarvinLvn/voice-type-classifier/blob/9731a9aad21a85d7bc660193386de94f2fb19f76/model/train/X.SpeakerDiarization.BBT2_LeaveOneDomainOut_paido.train/specs.yml)
- [출력/weight specs](https://github.com/MarvinLvn/voice-type-classifier/blob/9731a9aad21a85d7bc660193386de94f2fb19f76/model/train/X.SpeakerDiarization.BBT2_LeaveOneDomainOut_paido.train/weights/specs.yml)
- [공식 epoch/threshold params](https://github.com/MarvinLvn/voice-type-classifier/blob/9731a9aad21a85d7bc660193386de94f2fb19f76/model/train/X.SpeakerDiarization.BBT2_LeaveOneDomainOut_paido.train/validate_average_detection_fscore/X.SpeakerDiarization.BBT2_LeaveOneDomainOut_paido.development/params.yml)
- [dependency gitlink](https://github.com/MarvinLvn/voice-type-classifier/tree/9731a9aad21a85d7bc660193386de94f2fb19f76/pyannote-audio), [.gitmodules](https://github.com/MarvinLvn/voice-type-classifier/blob/9731a9aad21a85d7bc660193386de94f2fb19f76/.gitmodules)

Git blob 검증은 `SHA1(b"blob " + str(len(bytes)).encode() + b"\0" + bytes)`다.
위 SHA-1을 payload 자체의 SHA-1이나 SHA-256으로 잘못 비교하지 않는다.
`0100.optimizer.pt`와 `0100.domain.pt`는 inference weight가 아니며 받지 않아도 된다.

### 모델/입력 계약

정확한 고정 config + dependency 구현에 따르면 다음과 같다.

- 입력: 단일 채널 **16,000Hz float32 원음**, tensor `[batch, samples, 1]`.
  [read_audio/RawAudio](https://github.com/MarvinLvn/pyannote-audio/blob/85b84bc7ee3f44f1f112397f615873f348517541/pyannote/audio/features/utils.py)는
  `soundfile.read(dtype="float32", always_2d=True)`로 읽고 multi-channel은 산술 평균한다.
  기존 16kHz 보존 PCM을 사용하면 오래된 librosa resampling 경로를 피하면서 같은 입력을 고정할 수 있다.
- RawAudio 외부 peak/RMS 정규화, 고역 필터, noise reduction은 추가하지 않는다.
  모델 내부 `InstanceNorm1d(1, affine=True)`와 각 conv 뒤 InstanceNorm이 존재한다.
- [SincNet](https://github.com/MarvinLvn/pyannote-audio/blob/85b84bc7ee3f44f1f112397f615873f348517541/pyannote/audio/models/sincnet.py):
  output channels `[256,60,60]`, kernels `[251,5,5]`, stride `[1,1,1]`, padding0,
  maxpool `[3,3,3]`, min low/band50Hz. 첫 SincConv 뒤 absolute value → maxpool → affine InstanceNorm
  → LeakyReLU(slope0.2). waveform/internal normalization on, dropout0.
- [PyanNet/RNN/FF](https://github.com/MarvinLvn/pyannote-audio/blob/85b84bc7ee3f44f1f112397f615873f348517541/pyannote/audio/models/models.py):
  **3-layer bidirectional LSTM, hidden128**, concatenate false, time pooling 없음;
  FC128(tanh) → FC128(tanh) → linear5 → **Sigmoid**.
- **다중 독립 라벨** 순서는 `KCHI, CHI, MAL, FEM, SPEECH`다. categorical softmax/argmax로 바꾸지 않는다.
  KCHI는 녹음기를 착용한 key child, CHI는 other child, MAL/FEM은 성인 남/여 목소리다.
  SPEECH만 열리거나 목소리 유형과 SPEECH가 불일치할 수 있으며, 복수 유형도 동시에 열린다.
  [공식 disclaimer](https://github.com/MarvinLvn/voice-type-classifier/blob/9731a9aad21a85d7bc660193386de94f2fb19f76/docs/disclaimer.md).
- [Pretrained](https://github.com/MarvinLvn/pyannote-audio/blob/85b84bc7ee3f44f1f112397f615873f348517541/pyannote/audio/features/pretrained.py):
  훈련 창 **2.0s**, step ratio0.25 → **0.5s hop**. CPU device를 명시할 수 있다.
- [Model.slide](https://github.com/MarvinLvn/pyannote-audio/blob/85b84bc7ee3f44f1f112397f615873f348517541/pyannote/audio/train/model.py):
  마지막 창은 end-aligned; 중복 frame은 **동일 가중치 평균**한다. Hamming 가중치가 아니다.
  전체 길이가2초보다 짧으면 실제 support 길이를 사용한다. 공식과 다른 zero padding을 새로 만들지 않는다.
  model alignment는 `strict`다.
- source의 receptive-field 산술은 **325samples=20.3125ms**, frame step **27samples=1.6875ms**, resolution start0.
  32,000sample 창의 출력 길이는 산술상 **1,174frames**다. 이 값은 runtime에서 아직 검증하지 않았다.
  RF의 첫 middle은10.15625ms이며 경계/중복 평균은 pyannote.core의 crop 계약까지 보존해야 한다.

**논문과 배포 config의 차이:** [Interspeech2020 논문](https://arxiv.org/abs/2005.12656)의 Figure1은
2-layer BiLSTM을 설명하지만 이 고정 artifact config는 **3-layer**다. 논문 그림만 보고 재구성하지 않는다.
실제 state dict의 모든 key/shape와 strict load로 배포 구조를 확인해야 한다.

### 고정 후처리

| label | onset=offset |
|---|---:|
| KCHI | 0.3739 |
| CHI | 0.1570 |
| MAL | 0.1363 |
| FEM | 0.3583 |
| SPEECH | 0.4014 |

모두 min_duration_on/off **0.1s**, pad_onset/offset0이다.
[Binarize](https://github.com/MarvinLvn/pyannote-audio/blob/85b84bc7ee3f44f1f112397f615873f348517541/pyannote/audio/utils/signal.py)는
frame middle timestamp로 작동하며 `score > onset`에 열고 `< offset`에 닫는다.
먼저 duration **>0.1s** 활성 구간만 유지하고 이후 gap **<0.1s**를 채운다. 처리 순서를 바꾸지 않는다.
[Pipeline](https://github.com/MarvinLvn/pyannote-audio/blob/85b84bc7ee3f44f1f112397f615873f348517541/pyannote/audio/pipeline/labels_detection.py)은
각 출력 column을 별도로 thresholding한다. 부모 비교에서는 raw frame 확률도 보존해야 한다.

### 내부 비교를 구체화하는 순서

1. ignored research 작업경로에 source/config/specs/params + 단일 checkpoint만 고정한다.
   크기/Git blob 검증 후 자체 SHA256 기록. `torch.load(weights_only=True, map_location="cpu")`가
   단순 tensor state dict로 읽히는지 확인하고 strict load를 사용한다.
2. 공식 source reference 또는 필요한 source만 보존한 최소 local runner를 쓴다.
   wrapper의 [vtc.yml](https://github.com/MarvinLvn/voice-type-classifier/blob/9731a9aad21a85d7bc660193386de94f2fb19f76/vtc.yml)은
   Python3.8/NumPy1.23.5/옛 pyannote.core3.7.1을 요구한다. 현재 Windows runtime에서 그대로 동작한다고 가정하지 않는다.
   **공식 apply.sh는 repo 내부 rm/mv와 temporary config/output mutation을 포함**하므로 그대로 실행하지 않는다.
3. source reference와 최소 runner의 같은 2초 mono 입력에서 모든5 column을 비교한다.
   finite/range/shape, silence, short input, final end-aligned chunk, overlap frame clock/aggregation을 검증한다.
   source와 일치하지 않으면 입력 품질 실험에 들어가지 않는다. 긴2초 BiLSTM frame1174개의 비용은
   checkpoint 크기만으로 휴대폰에 적합하다고 판단할 수 없다.
4. 원래 보존3구간을 기존과 동일한16kHz PCM/hash/시간축으로 **처음** 비교한다.
   KCHI/CHI/SPEECH raw 확률·공식 구간, MAL/FEM과 겹침, 현재 제품 승인/보류 구간을 같은 표에 둔다.
   기존 두 누락이 한 원본의 겹치는 개발 구간이라는 제한을 유지한다. threshold 재조정/학습에 쓰지 않는다.
5. [공식 evaluation](https://github.com/MarvinLvn/voice-type-classifier/blob/9731a9aad21a85d7bc660193386de94f2fb19f76/docs/evaluations.md)은
   **TV/electronic speech ELE가 학습 label에 없다**고 명시한다. TV/adult/다른 아이 오승인과
   wearer 녹음→휴대폰 동영상 domain shift를 추가 독립 평가해야 한다. KCHI를 얼굴 주인 증거로 연결하지 않는다.
   내부 비교의 개선 근거가 있어도 권리/독립 평가/Android 실행 검증 전 제품에 넣지 않는다.

### 권리 상태를 source와 분리

[dependency LICENSE](https://github.com/MarvinLvn/pyannote-audio/blob/85b84bc7ee3f44f1f112397f615873f348517541/LICENSE)는 MIT(CNRS2016)이고
개별 model source에도 MIT header가 있다. VTC repository `new_model` 고정 tree 및 `master`
`72adda544fd3d77f848059a7921dd1e83c61bd5b`의 license/notice 파일은 발견되지 않았고 GitHub API license는 null이다.
공개 다운로드, 논문의 “open-source”, dependency MIT를 **task weight 상업/재배포 허락**이라고 보고하지 않는다.
BabyHuBERT의 별도 NC 고지를 이 원래 SincNet weight에 자동 적용하지도 않는다.

## 2. 나머지 새 원천 확인 — 비교 우선순위를 바꾸지 않는 이유

### Whisper-VTC2025: 정확한 별도 artifact, 더 크고 권리 미확정

[LAAC-LSCP/VTC-IS-25](https://github.com/LAAC-LSCP/VTC-IS-25/tree/acebb56ced2454fa92ceb1afbe46dc2ed3683bf4)
commit `acebb56ced2454fa92ceb1afbe46dc2ed3683bf4`는 실제 inference repo다.
[model/best.ckpt pointer](https://github.com/LAAC-LSCP/VTC-IS-25/blob/acebb56ced2454fa92ceb1afbe46dc2ed3683bf4/model/best.ckpt):
**378,022,248B**, SHA256 `a50d2ceff4cb72c180f2a824cfac92ff48a80395170e9b439f37573de92fbb40`.
[config](https://github.com/LAAC-LSCP/VTC-IS-25/blob/acebb56ced2454fa92ceb1afbe46dc2ed3683bf4/model/config.yml)은
Whisper-small encoder, weighted reduction,4-layer BiLSTM128, classifier256, KCHI/OCH/MAL/FEM,
4초16kHz 창을 명시한다. Python>=3.13/segma dependency; script는 mps 없으면 cuda를 선택하며 CPU fallback이 없다.
encoder 별도 준비 경로도 있고 source/weight license가 없다(GitHub null). 4.53MB VTC1보다 먼저 받을 근거가 없다.

### ComParE2018 CRIED / 2019 BabySounds

- [2018 공식 논문/PDF](https://www.isca-archive.org/interspeech_2018/schuller18_interspeech.html):
  CRIED는 건강 영아20명, **4~16주**, 발성5,587개. 0 neutral/positive,1 fussing,2 crying.
  44.1kHz stereo AAC 원녹음에서 vocal breathing group별 segmentation, vegetative sounds 제외;
  challenge 입력 mono16kHz/16bit PCM. train-LOSO2,838/test2,749. raw40ms CNN+GRU 및
  ComParE/BoAW/auDeep feature classifier baseline가 설명되지만 이 조사에서 실행 가능한 정확한 공개 task weight는 확인하지 못했다.
- [2019 공식 논문/PDF](https://www.isca-archive.org/interspeech_2019/schuller19_interspeech.html):
  **2~36개월46명**, canonical babbling / non-canonical / crying / laughing / junk-other.
  초기12,445 발성을36~500ms chunks(modal400ms)로 나누고 majority annotation 뒤11,304개 유지;
  70ms 미만은70ms로 pad. **baby-disjoint/age-balanced partition**이다.
  원음 제공 조건/상업 학습 허락/공개 exact head를 아직 확보하지 못했다.
- challenge site `compare.openaudio.eu`는 TLS 검증 오류 또는 경로404였고 우회하지 않았다.
  논문 접근권·윤리 승인·annotation schema는 오디오 다운로드/상업 학습 사용권을 대신하지 않는다.
  2018과2019를 같은5-label 데이터로 합치지 않는다. 2018은 앱 대상 월령에도 맞지 않는다.

### 작은 “baby cry” head 검색 결과는 발성 전문 라벨과 분리

[AmeerHesham/distilhubert-finetuned-baby_cry](https://huggingface.co/AmeerHesham/distilhubert-finetuned-baby_cry/tree/7b9a7700ef09ab81ea597cb4bbb87c283536ae2c)
는94,766,588B weight/APACHE2 metadata가 있으나 Donate-a-Cry 기반
belly_pain/burping/discomfort/hungry/tired5가지 **caregiver 원인 추정** task다. 누락 옹알이 detector가 아니다.
[ericcbonet/cry_baby_lite](https://huggingface.co/ericcbonet/cry_baby_lite/tree/c9f7051bcc09fb2af28adeaa90395bcef64ac5ff)는
5,491,332B tflite/MIT metadata만 있고 input/labels/training provenance가 없다.
[manfye/baby-cry-detector](https://huggingface.co/manfye/baby-cry-detector/tree/4495440c7aeb0b04f12b9b991df5d92c062f92a0)는
435,616B ONNX/CC BY-SA4.0 binary cry/non_cry,16kHz3.072s다. 카드가 unresolved third-party dataset lineage를
명시하므로 제품 사용권이 해소됐다고 볼 수 없다. 세 후보 모두 이번에 추론/다운로드하지 않았다.

## 3. VTC1에서도 이득이 없을 때의 작은 local head 경로와 정확한 blocker

새롭게 학습 가능한 데이터가 이미 완성됐다고 보고하지 않는다.
[FSD50K 공식 record4060432](https://zenodo.org/records/4060432)의 오디오는 file마다 CC0/CC-BY/CC-BY-NC/Sampling+가
섞여 있다. 전체 metadata/annotations는CC-BY이고 `dev_clips_info_FSD50K.json`/`eval_clips_info_FSD50K.json`에
clip license mapping이 있다. dev/eval은 uploader-disjoint다. **CC0/CC-BY를 정확히 선별하는 source-index 경로**는 있다.
단, 공식 record에 상업 사용 문의 요청도 있고 baby babble의 실제 license-qualified 수/가족/월령은 이 조사에서 확인하지 못했다.
dataset 전체 오디오를 상업 허락으로 묶거나 NC clip을 포함하지 않는다. ground-truth334,701B zip의 in-memory
read는325,856B에서 중단됐고 내용/라벨 수를 확인했다고 주장하지 않는다. 재시도/오디오 다운로드는 하지 않았다.

진행 가능한 학습 설계는 다음과 같다. **데이터 gate 이후에 실행**한다.

1. 원 recording/uploader/license URL/attribution/SHA256, 알려진 infant age/identity, event 시작·끝,
   label(`infant_vocal`, `adult_speech`, `other_child`, `TV_speech`, `other`) manifest를 로컬에 고정한다.
   여러 infant non-cry 원음과 adult/TV negative를 모아 공개 coarse label를 그대로 정답으로 삼지 않고 로컬 검토한다.
   월령/가족 불명은 따로 기록하며6~18개월 평가 증거로 세지 않는다. 충분한 babble family 수가 현재 blocker다.
2. 제품의 기존 고정 YAMNet **embedding1024**를 freeze하고 동일16kHz/window975ms decode 경로로 추출한다.
   score527 재조정과 달리 학습 representation 위에 **1024→5 linear head**(bias 포함5,125 FP32 params,
   20,500B pure tensor) 하나를 local training한다. YAMNet state/source/license/hash를 그대로 보존한다.
3. recording/family/uploader 단위로 train/dev/test를 먼저 분리하며 중복·분할·augmentation이 split을 건너지 않게 한다.
   parent-retained 두 누락 및 같은 원본은 전부 locked development probe로 제외한다.
   head/L2/class weighting/seed는 사전 고정하고 threshold는 dev에서만 고른다.
4. baby-disjoint test의 event recall, adult/TV false approval per minute, uncertainty/overlap 결과를
   원래 제품과 같이 비교한다. 분류5가지가 정확히 구분되지 않으면 보류한다.
   가족별 신뢰구간/Android local 속도/실제 월령 별 평가와 권리 gate 이후만 채택을 검토한다.

[AxonData 공개 card](https://huggingface.co/datasets/AxonData/infant-cry-detection-dataset/blob/122bb252952913b870cd8947a5a14b1981c46360/README.md)는
CC-BY metadata와 함께 public sample은evaluation/EDA/proof-of-concept, production training은별도 구매라고 적는다.
50시간 전체가 무료 공개 상업 training data라고 세지 않는다.
[hoyyu1 card](https://huggingface.co/datasets/hoyyu1/infant-cry-detection/blob/8f6e2830e0aa4b0f3896468f76fce915809abb07/README.md)는
CryCeleb2023/ESC50 등의혼합과5초16kHz를 밝히지만 별도 license가 없고 upstreamNC가 포함된다. 재포장으로 권리를 해소하지 않는다.
ChildVox/SMD/BabbleCor/W2V2-Pro-SM의 기존 접근/권리 문제는 [기존 followup](20261005-infant-specialist-followup.md) 그대로이며
새 발견으로 반복 계산하지 않는다.
