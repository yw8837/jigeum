# EfficientAT mn04_as 로컬 비교 실행 계약

확인일 **2026-10-05**. 공식 저장소 `fschmid56/EfficientAT`의 고정 source **`a425fdce92572e602a1d5634799bd9f1f2efa806`**와 GitHub release API를 읽었다. source/서지 응답만 TEMP에 저장했다. 가중치 다운로드·설치·추론·학습·앱/기기 변경은 수행하지 않았다. 이 문서만 작성했다.

## 1. 다운로드할 정확한 asset

| 항목 | 공식 release API에서 확인한 값 |
|---|---|
| release | [v0.0.1](https://github.com/fschmid56/EfficientAT/releases/tag/v0.0.1), 최초 공개 2022-11-17 |
| 모델 key | **`mn04_as`**, ImageNet 뒤 AudioSet 학습. `mn04_im`은 AudioSet head가 학습된 동일 대안이 아니다. |
| asset 이름 | **`mn04_as_mAP_432.pt`** |
| asset ID | **84971629** |
| URL | [공식 파일](https://github.com/fschmid56/EfficientAT/releases/download/v0.0.1/mn04_as_mAP_432.pt) |
| size | **4,066,513 bytes**, decimal 약4.07MB |
| digest | GitHub API **`digest:null`**. 공개 SHA256이 있다는 뜻이 아니다. 부모가 실제 다운로드 뒤 SHA256을 계산·기록해야 한다. |
| updated_at | 2022-11-17T14:10:49Z |

[release metadata API](https://api.github.com/repos/fschmid56/EfficientAT/releases/tags/v0.0.1), [asset metadata API](https://api.github.com/repos/fschmid56/EfficientAT/releases/assets/84971629). 고정 [model source](https://github.com/fschmid56/EfficientAT/blob/a425fdce92572e602a1d5634799bd9f1f2efa806/models/mn/model.py#L24)가 `mn04_as`를 이 파일로 연결한다. release/tag URL은 asset 교체 가능성까지 제거하는 content hash가 아니므로 크기와 다운로드 SHA를 함께 고정한다.

## 2. 모델·출력 계약

- [README](https://github.com/fschmid56/EfficientAT/blob/a425fdce92572e602a1d5634799bd9f1f2efa806/README.md#L87): `width_mult=0.4`, 약 **0.983M parameters**, default spectrogram 기준 **0.11B MACs**, AudioSet 전체 **mAP43.2%**. 영아 정확도·Android 지연 수치가 아니다.
- [get_model](https://github.com/fschmid56/EfficientAT/blob/a425fdce92572e602a1d5634799bd9f1f2efa806/models/mn/model.py#L326): `num_classes=527`, `head_type="mlp"`, strides `(2,2,2,2)`, frequency128, default time1000, channel-only SE(`se_dims='c'`). `NAME_TO_WIDTH('mn04_as')=0.4`를 [helper](https://github.com/fschmid56/EfficientAT/blob/a425fdce92572e602a1d5634799bd9f1f2efa806/helpers/utils.py#L1)로 대조했다.
- 입력은 **`[batch,1,128,time_frames]` float mel tensor**, 반환은 `(logits,features)`. [공식 inference](https://github.com/fschmid56/EfficientAT/blob/a425fdce92572e602a1d5634799bd9f1f2efa806/inference.py)는 **`sigmoid(logits.float())`**를 쓴다. 527개 독립 tag 점수이며 softmax 단일 의도 분류가 아니다.
- [고정 label map](https://github.com/fschmid56/EfficientAT/blob/a425fdce92572e602a1d5634799bd9f1f2efa806/metadata/class_labels_indices.csv): index6 `Babbling`,17 `Baby laughter`,23 `Baby cry, infant cry`;16 일반 `Laughter`,22 일반 `Crying, sobbing`,0 `Speech`,3 `Child speech, kid speaking`,500 `Silence`. 결과 해석에서 일반 tag와 infant tag를 합치거나 욕구 참값으로 바꾸지 않는다.

## 3. waveform → mel: 16kHz가 아니라 **32kHz**

근거: [inference.py](https://github.com/fschmid56/EfficientAT/blob/a425fdce92572e602a1d5634799bd9f1f2efa806/inference.py), [AugmentMelSTFT](https://github.com/fschmid56/EfficientAT/blob/a425fdce92572e602a1d5634799bd9f1f2efa806/models/preprocess.py). 기존16kHz 입력을 그대로32kHz라고 표기해 넣지 말고 실제 resample한다.

| 단계 | 고정 기본값/계산 |
|---|---|
| decode | `librosa.load(...,sr=32000,mono=True)` → waveform `[B,N]`; PCM decode 시 float 단위·mono 정책도 동일하게 기록 |
| preemphasis | conv1d kernel `[-0.97,1]`, padding 없음 → 길이N−1 |
| STFT | FFT1024, window800 samples(**25ms**), hop320(**10ms**), Hann **periodic=False**, center=True, normalized=False. source는 `return_complex=False` 후 real/imag 제곱합(power) |
| mel |128 bands, Kaldi `get_mel_banks`, fmin0, default **fmax15000Hz** (`32000/2−2000/2`), vtln_low100/high−500/warp1; Nyquist 열에0 padding |
| log·normalization | **natural log** `(mel_power+0.00001).log()`, 뒤 **`(logmel+4.5)/5`**. dB 변환 또는 다른 정규화로 대체하지 않는다. |
| evaluation | **model.eval()와 mel.eval() 둘 다 필수**. mel training mode는 fmin/fmax random augmentation,FrequencyMasking48/TimeMasking192를 적용한다. |
| 시간 길이 | default 10초=320000 samples → 위 전처리에서 약1000 frames. 일반 inference는 clip 길이를 강제로10초로 자르지 않고 adaptive pooling을 사용한다. 다른 길이의 품질은 별도 비교해야 한다. |

현대 torch에서 legacy `return_complex=False` 호환 문제가 생기면 **`return_complex=True` 뒤 `.abs().square()`**는 같은 power 계산을 위한 후보 변환이다. 나머지 window/padding/mel 계산을 보존하고 실제 numerical parity를 검증해야 한다. 이번 작성자는 compatibility 실행을 하지 않았다.

## 4. weights_only=True 로컬 로딩 경로

고정 upstream loader는 URL 자동 다운로드 후 state dict를 사용하며, mismatch 때 `strict=False`로 재시도한다. 부모의 고정 실험은 auto-fetch와 부분 로딩을 피하도록 **미학습 구조 생성 → 로컬 파일 → strict 로딩**을 분리할 수 있다. 아래는 **실행하지 않은 계약 예시**다.

```python
from models.mn.model import get_model
from models.preprocess import AugmentMelSTFT
import torch
model = get_model(width_mult=0.4, pretrained_name=None, num_classes=527,
                  head_type="mlp", strides=(2, 2, 2, 2))
state = torch.load(local_weight_path, map_location="cpu", weights_only=True)
model.load_state_dict(state, strict=True)  # 실패하면 원인 기록; strict=False로 숨기지 않음
model = model.float().eval()
mel = AugmentMelSTFT(n_mels=128, sr=32000, win_length=800, hopsize=320).eval()
with torch.inference_mode():
    logits, features = model(mel(waveform_float32).unsqueeze(1))
    scores = torch.sigmoid(logits.float())
```

- **API 지원은 확인**: repository requirements의 torch1.13.0부터 [공식 torch serialization source](https://github.com/pytorch/pytorch/blob/v1.13.0/torch/serialization.py#L653)에 `weights_only` keyword가 있다.
- **이 asset의 실제 restricted deserialize 성공은 미검증**: 가중치를 받지 않았다. upstream이 flat tensor state dict처럼 접근한다는 source 근거가 있지만 실제 허용 object/type·shape는 부모가 다운로드 후 검사해야 한다. 실패 시 `weights_only=False`로 자동 재시도할 근거를 만들지 않는다.
- 원 README는 학습float16,CPUfloat32에서 약간의 성능 차이를 설명한다. inference source도 CPUfloat32 경로를 사용하고 bfloat16이 더 나쁘다고 주석 처리한다. CPUfloat32는 공식 비교 경로지만 이 기기/영아 표본 품질은 미검증이다.

## 5. 최소 의존·작업 디렉터리·스크립트 함정

- 모델 import에 **torch,torchvision,numpy**, 내부 `models/mn/{model,utils,block_types,attention_pooling}.py`, `helpers/utils.py`, **`metadata/class_labels_indices.csv`**가 필요하다. helper가 label CSV를 **cwd 상대경로**로 읽으므로 repo root에서 실행하거나 부모 전용 runner에서 label 경로를 명시해야 한다.
- 원 전처리에는 **torchaudio**, 원 audio decode에는 **librosa**가 필요하다. WAV를 이미 `[B,N]`32kHzfloat tensor로 만들면 모델/전처리 호출 자체에 librosa는 필요하지 않다.
- [전체 requirements](https://github.com/fschmid56/EfficientAT/blob/a425fdce92572e602a1d5634799bd9f1f2efa806/requirements.txt)는 Python3.10.8 기준 torch1.13.0/torchaudio0.13.0/torchvision0.14.0/numpy1.23.3/librosa0.9.2 외 av,h5py,scikit_learn,tqdm,wandb,pandas도 포함한다. 후자는 이 좁은 `mn04` tensor inference import에 모두 필요한 것은 아니다. 호환 torch/vision/audio 조합은 부모 환경에서 확인한다. README의 FFmpeg<4.4 안내를 tensor/WAV 추론의 절대 전제로 확대하지 않는다.
- [windowed_inference.py](https://github.com/fschmid56/EfficientAT/blob/a425fdce92572e602a1d5634799bd9f1f2efa806/windowed_inference.py#L8)는 `models.mn.model`에 없는 `get_ensemble_model`을 import한다. 또한 `ceil((N−window)/hop)+1`은 짧은 clip에서0/음수가 될 수 있다. **그 스크립트를 그대로 실행해 비교를 시작하지 않는다.** 별도 runner에서 window·hop·tail padding을 명시한다. CLI 기본은10초창/2.5초hop,method 기본은20초창/10초hop이므로 둘도 구분한다.

## 6. 권리와 비교 결과의 범위

[고정 LICENSE](https://github.com/fschmid56/EfficientAT/blob/a425fdce92572e602a1d5634799bd9f1f2efa806/LICENSE)는 **MIT,Copyright(c)2022 Florian Schmid**이며 사용·수정·배포·판매 허용과 copyright/license notice 보존을 명시한다. 읽은 README/release에서 asset별 별도 NC 조건은 찾지 못했다. repository software MIT와 원 ImageNet/AudioSet/YouTube 자료의 권리는 별개이며, 원 미디어 재배포 허가를 얻은 것은 아니다. weight별 별도 명시문은 미확보이므로 제품 재배포 판단과 로컬 공개 checkpoint 비교를 구분한다.

부모 완료 조건: 실제 asset bytes/로컬SHA256,restricted deserialize와 strict keys/shape,전처리 설정,527 label map,CPUdtype/실제 의존버전,window/padding 정책을 결과와 함께 고정한다. 이 작은 모델은 일반 sound tagging 비교 후보다. babble/cry/laugh tag 존재·4MB크기·전체AudioSetmAP를 영아검증 또는 Android통합완료로 보고하지 않는다.
