# SpeechMaturity 후보의 로컬 추론 경로 검토

작성: 2026-10-04 · 읽기 전용 소스/메타데이터 검토 · 가중치 다운로드·설치·추론 없음

## 판단

**W2V2-LL4300-Pro-SM은 제한적인 로컬 비교를 준비할 구체적인 근거가 있는 새 후보다.** 저자 공개 저장소에 학습된 5-way head, task encoder, ASR encoder가 있고 forward를 소스로 추적할 수 있다. 기존 AST 실패의 같은 조건 재실행과 구분된다.

다만 **README 명령을 그대로 실행하거나 현재 Python 환경에서 바로 재현된다고 보장할 수는 없다.** 학습 진입점, 소표본 누락, batch=1 차원 처리, 전처리와 패딩, 오래된 로더를 먼저 통제해야 한다. 권고하는 다음 작업은 **3개 inference state checkpoint의 안전한 로딩·키/shape 감사 → 설정·매핑 확인 → 합성 입력 검증 → 한 번의 고정 로컬 비교**다. 첫 단계가 통과하지 않으면 모델 추론으로 넘어가지 않는다. 현 소스만으로 최신 Transformers로의 정확한 변환 완료를 선언하지 않는다.

이 후보는 소리 종류/발성 성숙도 분류다. Gemma의 영상·소리 결합 생성, 장면에 어울리는 감정·욕구 표현, Android 성능·배포 승인 문제를 해결한 결과가 아니다. 제품의 AST 정책이나 유료 권한, UI를 바꿀 근거도 아직 없다.

## 고정 출처

- 저자 GitHub commit: `68850104737ab271cf25f47908070b6b73f06b89`.
- 저자 HF revision: `9aac676c2b29c42d580b862a0294bc3807788847`.
- 이름과 task 범위는 [artifact 조사](20261004-speech-maturity-artifacts.md)를 따른다. 다른 연구인 voc2vec와 동일 모델로 취급하지 않는다.
- `agent-reach`의 GitHub CLI 경로로 공식 소스를 읽었다. HF의 Jina Reader 접근이 거절되어 HF 공식 API/고정 revision의 작은 텍스트 파일로 확인했다. 가중치 내용이나 저장 tensor는 아직 읽지 않았다.

## 1. 실제 forward와 라벨

고정 YAML과 `EmoIdBrain.compute_forward`의 경로는 다음과 같다.

```text
16kHz mono waveform [B, N]
 ├─ LL4300 Fairseq encoder, 12 transformer layer outputs
 │   → 각 layer 시간 평균 → 학습된 12개 weight의 softmax 가중 평균 → [B,768]
 └─ children-ASR Wav2Vec2 encoder, 마지막 hidden state
     → 시간 평균 → [B,768]
 concat → [B,1536]
 → Linear(1536,512) → BatchNorm1d → LeakyReLU → Dropout(.1)
 → bias 없는 Linear(512,5) → logits → softmax
```

- LL4300 경로: `output_all_hiddens=true`, `include_CNN_layer=false`, `encoder_dropout=0`, `output_norm=true`. 입력 정규화는 Fairseq checkpoint의 `cfg.task.normalize` 등을 읽는다. JSON config가 없는데 임의로 true/false를 정하면 저자 모델의 재현이 아니다.
- ASR 경로: YAML은 `facebook/wav2vec2-base` 구조를 만들고 `wav2vec_asr.ckpt`를 덮어쓴다. phoneme decoder를 실행하는 것이 아니라 encoder 특징을 사용한다. `output_norm=true`, spec augmentation은 wrapper 기본값 false다.
- `WeightedAverage`는 12개 weight를 softmax한 후 matmul한다. 단순 산술 평균으로 대체하면 학습된 head의 일부가 사라진다.
- DNN에는 BatchNorm의 running mean/variance도 있다. head의 Linear weight만 가져오면 완전한 복원이 아니다. 전체 모듈을 `eval()`로 두고 gradient를 끈다.

| ID | 저자 recipe 라벨 | 해석 범위 |
|---:|---|---|
| 0 | Junk | 기타/제외 |
| 1 | Non-Canonical | 비정형·미성숙 발성 범주 |
| 2 | Canonical | 정형·성숙 발성 범주 |
| 3 | Laughing | 웃음 |
| 4 | Crying | 울음 |

config 주석의 나열 순서가 아니라 README와 `label_pipeline_chi`의 명시적 ID 맵을 사용한다. `Canonical`을 특정 단어의 인식이나 욕구 정답으로 바꾸지 않는다. 이 5-way softmax는 AST의 multilabel 점수와 같지 않으므로 기존 `.30/.10` 기준을 이식하지 않는다.

## 2. 필요한 파일과 다운로드 범위

HF 고정 revision의 tree `size`와 `lfs.oid`를 읽었다. 아래 SHA256은 **Hub가 선언한 LFS digest**이며 로컬 바이트 해시 검증 결과는 아니다.

### 첫 단계: 실제 추론 state 3개

체크포인트는 `CKPT+2025-02-08+21-43-25+00` 한 개를 고정하는 것이 타당하다. 저자 `evaluate(min_key="error_rate_UAR")` 기준과 맞고, 공개 `CKPT.yaml`의 지표는 이 폴더가 .262, 다른 폴더가 .284다. YAML의 base64 float64를 builtin decode한 값이며 파이썬 객체 YAML loader를 실행하지 않았다. 서로 다른 seed라는 근거는 없으므로 **두 checkpoint**라고만 표현한다. 개인 영상 결과를 보고 checkpoint를 고르지 않는다.

| HF 상대 경로 | bytes | LFS SHA256 |
|---|---:|---|
| `CKPT+2025-02-08+21-43-25+00/model.ckpt` | 3,169,378 | `cbd674239f8f1f6b9fe7d7b4d604b8de9eea2ca3fa60633c1f84388a61d335e8` |
| `CKPT+2025-02-08+21-43-25+00/wav2vec2.ckpt` | 377,566,819 | `f778e63ca45d2e7e632244762162eb2772107c0f0846511be8818a8dce8bbd0e` |
| `wav2vec_asr.ckpt` | 377,572,016 | `f112f7506c754cd01d5c0b7163cc3b572e4edd4e6775277a600685ad03c7a540` |
| 합계 | **758,308,213** | 약 758.31 MB / 723.18 MiB |

이 셋은 source상 추론 학습 state를 담는 대상이다. **정확한 구조 설정까지 포함된 독립 실행 bundle이라고 확인한 것은 아니다.** tensor-only state인지, 모든 키가 예상과 맞는지, 새 라이브러리로 완전하게 옮길 수 있는지는 로딩 후 확인할 항목이다.

`model.ckpt`는 YAML의 `ModuleList([dnn_chi, output_mlp_chi, weighted_average_chi])` 전체다. `wav2vec2.ckpt`는 task fine-tuned Fairseq wrapper state이고, ASR state는 별도 pretrainer 경로다. 무작위 초기화된 새 head나 upstream encoder만 실행하면 이 후보 비교가 아니다.

### 공식 초기화에 추가로 쓰이는 파일

| 파일 | bytes | 용도/필요 조건 |
|---|---:|---|
| 저자 HF `checkpoint_best.pt` | 1,140,642,009 | 공식 Fairseq wrapper가 구조·cfg·초기 state를 로드한 뒤 task state를 덮어쓴다. LFS SHA256 `24b06d1835159d56be306d86760be56aa03f06dc7a4b865ffdfd02cc36e75c6b` |
| `facebook/wav2vec2-base`의 `config.json` | 1,842 | ASR Wav2Vec2 구조 정의 |
| 같은 repo `preprocessor_config.json` | 159 | 16kHz, `do_normalize=true` 등 |
| 같은 repo `pytorch_model.bin` | 380,267,417 | 저자 wrapper의 `from_pretrained`가 초기화할 때 받는 base weights. ASR state의 완전 strict 복원이 입증되면 직접 config 생성으로 생략 가능 |

위 base config 확인 revision은 `0b5b8e868dd84f03fd87d01f9c4ff0f080fecfe8`이다. 두 작은 JSON을 고정하고 `Wav2Vec2Model(config)`로 만든 뒤 ASR checkpoint가 전 키를 strict 복원한다면 base `.bin`은 필요 없다. 이 생략을 아직 검증하지 않았고 `.bin`의 다운로드를 권고하지 않는다.

공식 Fairseq bootstrap까지 포함한 저자 4개 weight 합계는 **1,898,950,222 bytes**다. base `.bin`과 작은 JSON까지 그대로 초기화하면 **2,279,219,640 bytes**다. 이는 다운로드 파일 합이며 추론 RAM이나 APK 크기가 아니다. optimizer, scheduler, brain, train dataloader state와 다른 checkpoint는 inference-only 비교에 필요하지 않다.

LL4300 구조 JSON은 저자와 해당 upstream tree에서 찾지 못했다. 3개 state만으로 cfg를 완전히 추론한다고 약속하지 않는다. 필요한 normalization/architecture 값이 미확정이면 bootstrap checkpoint의 **안전하게 읽힌** cfg 또는 저자가 공개한 동등한 설정을 확보해야 한다. 내부 pickle을 무제한 로드하는 우회는 하지 않는다.

## 3. recipe를 그대로 실행하면 생기는 문제

| 확인된 소스 사실 | 로컬 비교에서의 조치 |
|---|---|
| `__main__`에서 `fit()` 후 `evaluate()`를 호출한다. checkpoint가 있으면 반드시 학습을 건너뛴다는 코드 보장은 없다. | `Brain.fit`, optimizer, scheduler를 생성/호출하지 않는 별도 inference 진입점을 둔다. |
| test batch_size=16, `drop_last=true`다. | 평가한 입력 수를 반드시 manifest와 대조한다. batch=1·drop_last=false로 실행해 소표본이 0개가 되는 문제를 막는다. |
| ASR `avg_pool(...).squeeze_()`는 `[1,1,768]`을 `[768]`로 줄인다. 이후 `[1,768]`과 `cat(dim=1)`이 맞지 않는다. | singleton 시간 축만 제거하는 `.squeeze(1)` 또는 명시적 `[B,768]` reshape를 사용한다. 가중치/분류 기준 변경이 아닌 shape 수정이며 기록한다. |
| LL wrapper는 `F.layer_norm(wav,wav.shape)` 및 layer stack 전체 shape로 output norm을 한다. batch 축까지 포함될 수 있다. | batch 크기/구성이 출력에 영향을 줄 수 있음을 명시한다. 단일 사례 재현에는 B=1을 고정하되 논문 batch16과 수치 동등하다고 하지 않는다. |
| 두 encoder에 실제 길이 mask를 전달하지 않고 ASR pool에도 lens가 없다. | 서로 다른 길이를 배치 padding한 뒤 동일 결과라고 가정하지 않는다. 최초 비교는 동일한 길이, B=1로 제한한다. masking 수정은 별도 모델 동작 변경이므로 조용히 넣지 않는다. |
| vendored `StatisticsPooling`은 eval에서도 약 `1e-5` 수준의 작은 random noise를 평균에 더한다. | 매 실행의 seed·호출 순서를 고정하고 synthetic 반복에서 차이를 측정한다. noise를 몰래 제거한 평균 pooling을 원본과 동등하다고 쓰지 않는다. |
| 복원 hook은 OrderedDict의 맞는 key/shape만 옮겨 누락을 조용히 남길 수 있다. | 직접 `load_state_dict(strict=True)` 또는 전 키·shape 대응표를 검증한다. 의도치 않은 무작위 weight가 남으면 실패다. |
| 샘플 JSON에는 `Non-canonical`, 실제 맵에는 `Non-Canonical`이 있다. | 더미 라벨 기반의 자동 평가 지표를 산출하지 않는다. 실측 truth가 없으면 logits/5-way 결과만 보관한다. |

공식 script의 `if __name__ == "__main__"`는 확인했다. **단순 import가 곧바로 fit을 실행한다는 주장은 하지 않는다.** 다만 top-level에 SpeechBrain·Fairseq 등 import가 있어 현재 환경에서는 그대로 import하기도 어렵다. 필요한 forward와 작고 검토된 모듈 정의를 분리하는 편이 타당하다. 원본 main을 shell로 실행하지 않는다.

## 4. 버전과 안전한 로딩

저자 `environment.yaml`은 Python **3.9.19**, PyTorch/torchaudio **2.5.0 CPU**, Transformers **4.45.2**, fairseq **0.12.2**, speechbrain **1.0.2**, NumPy **1.24.4**, Hydra **1.0.7**, OmegaConf **2.0.6**, HyperPyYAML **1.2.2**, librosa **0.10.2.post1**를 지정한다. 별도 `requirements.txt`에는 NumPy **1.26.4**가 있어 하나의 일관된 lockfile도 아니다.

저자가 vendoring한 SpeechBrain `version.txt`는 **0.5.13**이고 custom `WeightedAverage`, `RABCBinaryMetricStats`, wrapper 경로를 포함한다. recipe-local과 vendored `fairseq_wav2vec.py`는 Git blob SHA가 동일하다. pip의 speechbrain1.0.2만 설치하면 이 구성이 그대로 생긴다고 가정하지 않는다.

부모가 확인한 기존 AST 환경은 Python3.13.14, torch2.13.0+cpu, Transformers5.14.1, NumPy2.4.6이고 Fairseq/SpeechBrain/torchaudio/HyperPyYAML이 없다. **기존 환경을 과거 버전으로 downgrade하지 않는다.** 처음에는 이미 있는 최신 torch에서 파일 검사와 `weights_only` 로딩을 별도 subprocess로 수행할 수 있다. 실제 추론 dependency 작업은 독립 환경 또는 순수 추론 wrapper에서 결정한다. 최신 환경이 Fairseq0.12.2를 지원한다는 검증은 없다.

안전한 로딩 절차는 다음과 같다.

1. 공식 고정 revision에서 정확한 3개 파일만 받고 크기·LFS SHA256을 로컬 바이트와 대조한다. HF repo 전체 snapshot을 무조건 받지 않는다.
2. 네트워크를 끈 제한된 subprocess에서 `torch.serialization.get_unsafe_globals_in_checkpoint(path)`를 먼저 조사한다. static 검사가 모든 동적 객체를 찾아주는 것은 아니다.
3. 각 파일을 `torch.load(path, map_location="cpu", weights_only=True)`로 명시적으로 연다. 일반 tensor dict/OrderedDict인지, dtype·shape·유한성·예상 키 범위를 검사한다. resource/time 상한도 둔다.
4. 제한 로딩이 실패하면 `weights_only=False`로 자동 재시도하지 않는다. 필요한 global을 실제 코드에서 검토하기 전에 일괄 allowlist하지 않는다. 특히 bootstrap Fairseq checkpoint의 cfg/Namespace/OmegaConf 객체 허용 가능성은 미확인이다.
5. `CKPT.yaml`에는 `!!python/object/apply`가 있고 저자 Checkpointer는 `yaml.Loader`를 쓴다. 이 loader로 자동 선택/복원하지 않는다. 파일을 명시적으로 고정하고, 확인된 scalar metadata만 data로 처리한다. HyperPyYAML recipe 전체를 가중치 검사에 필요 없이 실행하지 않는다.
6. 모든 weight가 대응되고 설정이 확정된 뒤 module별 strict 로드와 eval/inference_mode를 적용한다. 변환 산출물을 만든다면 원본 SHA·key mapping·전처리 계약을 함께 저장한다.

저자 환경의 torch2.5.0을 그대로 쓰는 것은 안전 로딩 목적과 맞지 않는다. PyTorch 공식 advisory `GHSA-53q9-r3pm-6pq6`는 **weights_only=True도 영향을 받는 ≤2.5.1 취약점**, 수정판2.6.0을 명시한다. 기존 최신 torch를 활용하는 이유다. `weights_only=True`도 메모리 고갈 등 모든 위험을 제거한다는 뜻은 아니다.

## 5. 현재 Transformers로의 순수 추론 wrapper 가능성

**가능한 구현 경로가 있으나 아직 변환 성공은 미확인이다.** ASR branch와 작은 head는 비교적 직접 복원할 수 있다. LL4300은 Fairseq state/config를 정확히 대응시키는 작업이 더 필요하다.

- ASR: pinned config로 `Wav2Vec2Model`을 생성하고 wrapper의 `model.` 등 실제 key prefix를 확인해 ASR state를 strict 대응시킨다. `from_pretrained("facebook/...")`의 숨은 다운로드·base weight 로딩을 피할 수 있는지는 전체 키 대조로 결정한다.
- LL4300: 공식 Transformers4.45.2 conversion source에 Fairseq→HF의 q/k/v, FFN, layer norm, feature projection/conv, positional conv weight norm mapping이 있다. 최신 모델의 `parametrizations.weight.original0/original1`와 구형 `weight_g/weight_v` 차이도 명시적으로 처리해야 한다.
- 그 공식 converter 자체는 Fairseq import/`load_model_ensemble_and_task`를 사용한다. 따라서 이를 그대로 실행하면 Fairseq 없는 경로가 되지 않는다. 검토된 tensor mapping 부분을 작은 wrapper에 옮기는 접근만 제안한다.
- CNN projection을 제외한 **12개 transformer layer**를 올바른 순서로 선택하고, Fairseq `layer_results`와 HF `hidden_states`의 pre/post layer norm 차이를 확인한다. 둘 다 길이가12라는 이유만으로 동등하다고 하지 않는다.
- norm/epsilon/conv stride·kernel·bias·activation·attention backend를 고정한다. 특히 LL4300 normalize flag, 전체 stack normalization, StatisticsPooling의 작은 noise를 기록한다.
- 메타데이터 단계에서는 LL4300 설정의 비텐서 필드를 모두 확정할 수 없었다. 따라서 **최신 Transformers에 base 기본값을 넣고 strict shape만 통과하면 재현 완료**라는 접근은 허용할 수 없다. 구조·전처리 의미가 확인되어야 한다.

가장 작은 다음 실행은 모델 추론 자체가 아니라 위 **3개 체크포인트의 제한 로딩 감사**다. 이 결과가 normal tensor state임을 확인하면 pure wrapper 작업의 불확실성이 크게 줄어든다. 이후 cfg가 실제 blocker이면 필요 파일 하나를 추가하는 단계로 진행할 수 있다. 안전한 cfg와 대응을 확보할 수 없다면 거기서 중단하고 그 구체적인 blocker를 보고한다. 고정 candidate를 반복 실행할 이유는 없다.

## 6. 10초·20초 원음과 논문 입력은 다르다

논문 §2.2는 발성 clip의 **modal length=500ms**, 16kHz mono로 바꾼 후 **9217 samples(약576.06ms)에 중앙 zero-padding**했다고 명시한다. 반면 공개 inference recipe는 `librosa.load(sr=16000,mono=True)` 후 **70ms보다 짧을 때 끝에 zero-pad**할 뿐 최대 길이나 중앙 패딩을 구현하지 않는다. 학습 데이터가 미리 처리됐기 때문일 수 있으나, 이 차이를 새 10/20초 입력에서 무시할 수 없다.

| 입력 방식 | 가능한 주장 | 불가능한 주장 |
|---|---|---|
| 논문에 맞춘 발성 단위 및9217샘플 입력 | 해당 후보의 발성 범주 비교 | 한국 아기의 욕구·의도 정답 |
|10/20초를 통째로 입력 | 별도 길이 스트레스/분포 밖 진단 | 논문 성능 재현, 선택 중 모든 사건 검출 |
|10/20초를 사전 고정500ms 창으로 나눠 중앙 pad | 모든 창을 보존하는 탐색적 시간 분포 비교 | diarization된 발성 단위 정답, 원본별 독립 표본 수 증가 |

전체10/20초를 넣으면 시간 평균 때문에 여러 발성·성인·배경이 하나의5-way 결과로 합쳐진다. 모델은 발성 주인 확인기나 시간 경계 검출기가 아니다. 장시간 transformer attention·activation 부담도 늘지만 실측 RAM/지연은 아직 없다.

최초 비교에서는 전체 원음을 임의로9217샘플로 잘라 “10초 분석”이라 부르지 않는다. 고정된500ms 비중첩 창과 마지막 remainder 처리·중앙 pad를 **결과를 보기 전에** 명시하고 전체 창의5-way 출력 분포를 연구용으로 보관하는 방법은 가능하다. 이 또한 논문 발성 단위와 다르므로 품질 통과로 세지 않는다. 원본10/20초 통째 추론은 추가 비용을 쓸 구체적 질문이 있을 때만 별도 진단으로 남긴다. threshold/창을 출력에 맞춰 고르지 않는다.

## 7. 최소 검증과 중단 조건

가중치 감사를 통과한 뒤의 작은 합성 검증은 실제 위험을 겨냥한다.

1. **B=1 shape:** 두 branch `[1,768]`, concat `[1,1536]`, logits `[1,5]`, 유한 softmax와 올바른 ID map.
2. **전 키 복원:** 사용하지 않은/누락 tensor가 없는지 검사한다. 허용되는 pretraining-only 잔여가 있다면 이름을 고정하고 근거를 남긴다. 랜덤 잔존·cfg 추측이면 중단한다.
3. **입력 계약:** mono16k·빈/비유한 입력 거부·500ms 및9217샘플 중앙 padding·마지막 잔여·sample 수 확인. 같은 입력을 같은 seed로 재실행했을 때 변동을 확인한다.
4. **batch/mask:** B=1과 동일 길이 B=2의 shape, 서로 다른 길이 padding의 오염 여부를 합성 데이터로 확인한다. 원본의 global norm 때문에 수치가 달라질 수 있으므로 batch invariance를 무근거로 기대하지 않는다. 최초 연구 경로는 B=1, 입력 길이 고정으로 제한한다.
5. **학습·누락·네트워크 없음:** optimizer/fit 미호출, 모든 manifest 항목이 출력됐는지, 오프라인 소켓 차단, 최대 시간/RAM·출력 크기를 확인한다.
6. **단회 로컬 비교:** 이미 보유한 입력을 source 단위로 묶어 고정 manifest대로 모두 평가한다. 같은 원본의 창을 독립 성공으로 세지 않는다. 독립 정답이 없으면 출력/보류 차이만 기록하며 accuracy나 회복 성공을 주장하지 않는다. 기존 울음/웃음 보호는 제품 정책 적용 없이 별도 대조한다.

새 원본을 더 받는 것이 실행 준비의 선행 조건은 아니다. 현재는 **안전한 state 로딩·cfg·완전한 key/전처리 매핑**이 구체적인 다음 증거다. 그 다음에 얻는 분류 차이가 실제 개선인지 판단하려면 독립 청취 근거가 필요하지만, 데이터 추가 자체가 모델 수정이나 해결책은 아니다.

## 8. Android와 권리의 경계

Android/ONNX artifact는 저자 경로에서 확인하지 못했다. 두 encoder의 추론 state만 약758MB이고 layer 출력12개·두 branch activation·포팅 copy가 추가되므로 이 후보를 경량 모바일 모델로 부를 근거가 없다. Fairseq/SpeechBrain/Python 코드를 앱에 그대로 넣는 경로도 아니다.

Android를 검토하려면 먼저 host 의미·수치 기준을 만들고, 두 encoder와 head의 변환, FP32 동등성, INT8 등 별도 후보의 변동, 모바일 operator/ABI·메모리·지연·발열·취소·기존 UI 응답을 순서대로 확인해야 한다. 원본의 global layer norm, pooling noise, dynamic time axes, positional convolution weight norm은 export 전 점검 대상이다. 이는 앞으로 필요한 기술 검증이며 이 문서에서 실행하거나 통과한 것이 아니다. 기종명 허용 목록은 제안하지 않는다.

명시적으로 확인된 권리 문구는 다음 범위다.

- 저자 README: **“Steps for running inference ... on your data.”** 로컬 추론 사용법과 인용 요청을 제공한다.
- 저자 HF README: **“The checkpoint folder is finetuned on SpeechMaturity corpus.”**와 두 upstream 출처를 적는다. 별도 task-weight license 문구는 없다.
- 두 upstream 카드에는 **`license: openrail`**이 있다. task weights에 대한 라이선스 승계나 구체적 조건 전체를 이 메타데이터만으로 확정하지 않는다.

따라서 제한된 내부 연구 평가를 기술적으로 준비할 근거는 있으며, 읽은 문구에서 **각 로컬 실험마다 별도 승인/허가를 받아야 한다는 의무는 찾지 못했다.** 반대로 명확한 상용 사용·재배포 허가가 확인됐다고도 하지 않는다. 법적 권리 공백을 기술적으로 실행 불가능하다는 결론으로 바꾸지 않는다. 이번 후보 비교는 학습 데이터셋을 다운로드하거나 재사용하는 작업이 아니므로 dataset 접근 조건을 그대로 weight 검사 금지로 전환하지 않는다. 상용 채택·모델 재배포의 조건은 여전히 미확정이다.

## 1차 출처 링크

1. [논문 v1 §2.2](https://arxiv.org/html/2506.08999v1): utterance task, modal500ms, 16kHz·9217샘플·중앙 padding.
2. [저자 inference README](https://github.com/spoglab-stanford/w2v2-pro-sm/blob/68850104737ab271cf25f47908070b6b73f06b89/speechbrain/recipes/W2V2-LL4300-Pro-SM/Readme.md).
3. [고정 YAML](https://github.com/spoglab-stanford/w2v2-pro-sm/blob/68850104737ab271cf25f47908070b6b73f06b89/speechbrain/recipes/W2V2-LL4300-Pro-SM/hparams/train_1_w2v2_2dnn_WA_LL4300_asr_bbcor_concat.yaml), [forward와 main](https://github.com/spoglab-stanford/w2v2-pro-sm/blob/68850104737ab271cf25f47908070b6b73f06b89/speechbrain/recipes/W2V2-LL4300-Pro-SM/train_1_w2v2_WA_2dnn_combine_asr_features_bbcor.py).
4. [Fairseq wrapper](https://github.com/spoglab-stanford/w2v2-pro-sm/blob/68850104737ab271cf25f47908070b6b73f06b89/speechbrain/speechbrain/lobes/models/fairseq_wav2vec.py), [HF wrapper](https://github.com/spoglab-stanford/w2v2-pro-sm/blob/68850104737ab271cf25f47908070b6b73f06b89/speechbrain/speechbrain/lobes/models/huggingface_wav2vec.py).
5. [WeightedAverage](https://github.com/spoglab-stanford/w2v2-pro-sm/blob/68850104737ab271cf25f47908070b6b73f06b89/speechbrain/speechbrain/nnet/linear.py), [StatisticsPooling](https://github.com/spoglab-stanford/w2v2-pro-sm/blob/68850104737ab271cf25f47908070b6b73f06b89/speechbrain/speechbrain/nnet/pooling.py), [DNN_Block](https://github.com/spoglab-stanford/w2v2-pro-sm/blob/68850104737ab271cf25f47908070b6b73f06b89/speechbrain/speechbrain/lobes/models/CRDNN.py), [checkpoint hooks](https://github.com/spoglab-stanford/w2v2-pro-sm/blob/68850104737ab271cf25f47908070b6b73f06b89/speechbrain/speechbrain/utils/checkpoints.py).
6. [저자 environment](https://github.com/spoglab-stanford/w2v2-pro-sm/blob/68850104737ab271cf25f47908070b6b73f06b89/speechbrain/recipes/W2V2-LL4300-Pro-SM/environment.yaml), [vendored version](https://github.com/spoglab-stanford/w2v2-pro-sm/blob/68850104737ab271cf25f47908070b6b73f06b89/speechbrain/speechbrain/version.txt).
7. [고정 HF 파일 tree](https://huggingface.co/spoglab/w2v2-pro-sm/tree/9aac676c2b29c42d580b862a0294bc3807788847), [공식 API의 size/LFS SHA](https://huggingface.co/api/models/spoglab/w2v2-pro-sm/tree/9aac676c2b29c42d580b862a0294bc3807788847?recursive=true&expand=false).
8. [ASR base config](https://huggingface.co/facebook/wav2vec2-base/blob/0b5b8e868dd84f03fd87d01f9c4ff0f080fecfe8/config.json), [preprocessor config](https://huggingface.co/facebook/wav2vec2-base/blob/0b5b8e868dd84f03fd87d01f9c4ff0f080fecfe8/preprocessor_config.json).
9. [공식 Transformers4.45.2 Fairseq conversion source](https://github.com/huggingface/transformers/blob/v4.45.2/src/transformers/models/wav2vec2/convert_wav2vec2_original_pytorch_checkpoint_to_pytorch.py), [Fairseq0.12.2 loader](https://github.com/facebookresearch/fairseq/blob/v0.12.2/fairseq/checkpoint_utils.py).
10. [PyTorch weights_only 문서](https://docs.pytorch.org/docs/stable/notes/serialization.html#torch-load-with-weights-only-true), [공식 security advisory](https://github.com/pytorch/pytorch/security/advisories/GHSA-53q9-r3pm-6pq6).
11. [LittleBeats upstream 카드](https://huggingface.co/lijialudew/wav2vec_LittleBeats_LENA/blob/b3250219066482f571ef3868413b0b0f1ad92502/README.md), [children-ASR upstream 카드](https://huggingface.co/lijialudew/wav2vec_children_ASR/blob/c0e5ca013970bd9332449c5219f0ad150c7753b9/README.md).

이번 작업은 이 문서만 추가했다. 모델·원음·파생 미디어·개별 관찰 결과를 외부로 전송하지 않았고 제품 코드·환경·원자료를 수정하지 않았다.
