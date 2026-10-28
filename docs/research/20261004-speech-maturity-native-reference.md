# SpeechMaturity 공식 소스 계산 경로 대조 결과

2026-10-04 · 시작 HEAD `c60c000` · 실제 모델 실행 담당: 부모 작업

## 목적과 현재 상태

같은 비민감 입력에서 **Fairseq 원래 계산 경로와 HF 재구성 사이의 이식 오류**를 분리한다. 저자의 전체 설치환경이나 논문 성능을 재현했다는 주장은 하지 않는다. 아기 의도·영상/음성 맥락·한국어 문장 생성 목표도 이번 소리 분류 대조로 충족되지 않는다.

공식 소스를 고정하여 필요한 클래스/메서드51개를 추출하고 별도의 native exporter를 작성했다. 부모가 Windows Job 제한 아래 import smoke와 동결된 두 경로를 각1회 순차 실행했다. **고정 비민감 입력2개 × tensor8종 모두 사전에 정한 원소별 오차 기준을 통과했다.** 작성자는 실행 후 JSON 및 저장된 비민감 logits만 읽어 아래 결과를 확인했다. 추가 모델 실행은 하지 않았다.

이 결과는 **검사한 B1/9217·CPU FP32·동일 noise tape 범위에서의 공식 소스 계산 경로 대조 통과**다. 원저자 전체환경 parity, 다른 길이/배치/장치에서의 동등성, 분류 정확도를 검증한 것은 아니다.

## 왜 전체 저자 환경을 설치하지 않는가

저자 environment는 Python3.9.19, torch/torchaudio2.5.0, Transformers4.45.2, Fairseq0.12.2, Hydra1.0.7, OmegaConf2.0.6, HyperPyYAML1.2.2, librosa0.10.2.post1, NumPy1.24.4다. 별도 requirements에는 NumPy1.26.4가 있어 단일 lockfile도 아니다. vendored SpeechBrain0.5.13에는 custom module이 있으며 pip speechbrain1.0.2와 같다고 할 수 없다.

현재 Windows 환경은 Python3.13, torch2.13.0+cpu, Transformers5.14.1, NumPy2.4.6이다. Fairseq/legacy Hydra의 전체 import 및 배포 의존성, Windows build 문제를 해결하기 위해 기존 환경을 downgrade하지 않는다. 특히 원래 torch2.5를 설치하면 `weights_only` 취약점 수정판보다 오래된 버전으로 돌아간다. 새 패키지는 설치하지 않았다.

대안은 **공식 클래스의 실제 forward/attention/norm/pooling 본문을 AST로 추출하여 현재 torch에서 실행**하는 제한된 어댑터다. HF로 다시 쓴 LL 계산을 참조라고 부르지 않는다. 참조 LL은 Fairseq의 `Wav2Vec2Model`, `TransformerEncoder`, `MultiheadAttention`을 실행한다. 참조 ASR은 Transformers4.45.2의 `Wav2Vec2Model`과 그 encoder/attention 클래스다.

이 참조에 추가로 설치할 패키지는 없다. 이미 있는 torch와 NumPy만 필요하다. 원저자 전체환경 parity는 계속 미확정이다.

## 고정 소스와 정확한 파일

| 출처 | 고정 revision | 주요 파일 |
|---|---|---|
| 저자 |`68850104737ab271cf25f47908070b6b73f06b89` |`speechbrain/speechbrain/lobes/models/{fairseq_wav2vec,huggingface_wav2vec,CRDNN}.py`, `nnet/{pooling,linear,normalization,containers}.py`, task `train_1_w2v2_WA_2dnn_combine_asr_features_bbcor.py` |
| Fairseq0.12.2 |`4a388e64cd646ed7d7ad8de8fae55df2b8eea91d` |`fairseq/models/wav2vec/{wav2vec2,utils}.py`, `modules/{multihead_attention,layer_norm,fp32_group_norm,grad_multiply,same_pad,transpose_last,fairseq_dropout,quant_noise,gelu,transformer_sentence_encoder}.py`, `incremental_decoding_utils.py`, 필요한 `utils.py` 함수 |
| Transformers4.45.2 |`53fad641cfdb5105e2470bcf3ef17ea8e25cc300` |`src/transformers/models/wav2vec2/{modeling_wav2vec2,configuration_wav2vec2}.py`, `src/transformers/activations.py`의 `GELUActivation` |

Fairseq tag는 직접 commit을 가리킨다. Transformers annotated tag object `cba5c4a6fcd0f6b73bf591505337ccf08ded3550`를 위 commit까지 확인했다. GitHub 공식 `gh api`로만 파일을 읽었다. 모델/원음 파일은 새로 받지 않았다.

모든 원본은 ignored `work/speech-maturity-native-reference/sources/`에 보관한다. `sources-manifest.json`은 경로·revision·파일 SHA256, `extraction-manifest.json`은51개 원문 symbol의 줄번호·원본 AST hash·추출 AST hash·수정 목록을 갖는다. `generated-integrity.json`은 실행 전 검사할 생성 모듈과 manifest hash다. Fairseq MIT/Transformers Apache2.0 LICENSE 원문도 보관했다. 모델 weight의 상용 재배포 문제는 이 코드 라이선스와 별개다.

## 원문 보존 범위와 어댑터 변경

| 부분 | 적용 방식 및 한계 |
|---|---|
| Fairseq CNN/Transformer/attention |공식 클래스 본문 그대로. `MultiheadAttention`의 CPU 경로는 공식 코드가 호출하는 현재 torch `F.multi_head_attention_forward`를 실행한다. 이를 HF attention으로 교체하지 않는다. |
| norm/activation/dropout |공식 `Fp32GroupNorm`, `Fp32LayerNorm`, `LayerNorm`, `gelu`, `FairseqDropout`, `quant_noise`를 추출. CPU FP32/eval, dropout 비활성이다. |
| Fairseq `BaseFairseqModel` |package/train/distributed machinery 대신 `nn.Module`을 base로 공급한다. 학습/등록/체크포인트 기능은 실행하지 않는다. registration decorator는 identity다. |
| distributed/가속 |`fsdp_wrap`는 single-process identity, checkpoint-activation 경로는 명시 거부, Apex/DeepSpeed/xformers 미사용. xformers cfg는 `None`만 허용한다. |
| convolution expression |공식 생성자의 `eval(cfg.conv_feature_layers)` 한 줄을 이미 확인한7개 tuple literal로 바꾼다. 원식 평가를 실행하지 않는다. |
| Fairseq cfg |기본 공식 dataclass에 기존 bootstrap에서 확인한 primitive 필드를 덮어쓴다. task layerdrop0. 학습 전용 quantize_targets/target_glu는 생성하지 않고 `remove_pretraining_modules()`도 실행한다. quantize_input=false는 실제 cfg로 확인했다. 제거되는 pretraining module은 추론 출력/211키에 포함되지 않는다. |
| ASR config/model base |공식4.45.2 config/계산 클래스 사용. model base는 config 저장과 `nn.Module` 관리만 제공하고 `post_init` 랜덤 초기화 hook은 생략한다. 전 키 strict 로딩 없이는 실행하지 않는다. output container도 단순 attribute/tuple 어댑터다. |
| ASR attention |공식4.45.2 eager attention을 명시한다. 저자의 당시 자동 backend 선택까지 재현했다고 주장하지 않는다. |
| 저자 wrapper |checkpoint/download를 수행하는 constructor를 우회하여 확인된 module/flags만 주입한다. `forward`, `extract_features`, `aggregate_features`, `make_masks` 본문은 원문이다. |
| head/모듈 연결 |저자의 `Sequential`, `DNN_Block`, `Linear`, `BatchNorm1d`, `WeightedAverage`, `StatisticsPooling`, `EmoIdBrain.compute_forward`를 실행한다. `fit`, objective/optimizer/data loader/main은 추출하지 않는다. |
| batch1 수정 |`get_asr_features`의 `.squeeze_()` 한 곳을 `.squeeze(1)`로 변경하여 batch 축을 보존한다. 기존 재구성에서도 필요한 shape 수정이며 무수정 recipe 실행이 아니다. |
| 문서 decorators |HF의 docstring decorator만 제거했다. 계산 메서드 본문은 보존한다. |
| pooling RNG |모든 모델 load 후 `torch.randn` 공급원을 고정 tape로 잠시 바꾼다. `[1,768]`13회만 허용하며 noise 식은 원문 그대로 실행한다. 원저자 global RNG stream 재현은 아니다. |

전체 package import에 필요한 API를 모조 구현한 범용 실행기가 아니다. 확인한 B1/9217, CPU FP32, no-mask, post-LN, inference 경로에 한정한다. 조건이 달라지면 이 검증 범위도 달라진다.

## 사전 고정된 비교 계약

부모 소유 입력 디렉터리: `work/speech-maturity-parity/inputs/`.

- 비민감 `tone500`, `cry500` 각 PCM16LE8000샘플, mono16kHz. 부모가 고정한 hash를 확인한다. 분류 정답률 평가용 표본이라고 하지 않는다.
- float32 `/32768` 변환, 왼쪽608/오른쪽609 zero-pad로 `[1,9217]`. gain/필터/조건 탐색 없음.
- `pooling-noise.npy`: float32 `[13,1,768]`, NumPy PCG64 seed2025로 사전에 만든 Gaussian tape. ASR→LL0..11 순서, 입력마다 tape reset.
- 두 모델을 같은 프로세스 메모리에 동시에 올리지 않는다. 각 exporter가 `.npz`와 metadata를 저장하고 종료한 뒤 부모가 대조한다.
- **모든 원소 유한, shape 완전 일치, `abs(candidate-reference) <= 1e-4 + 1e-4*abs(reference)`를 모든 원소가 만족해야 통과**한다. top1 일치는 보조 지표다. 결과를 본 뒤 기준을 완화하지 않는다.

| 출력 key | shape | 포착 지점 |
|---|---|---|
| `ll_raw` |`[12,1,28,768]` |native encoder forward hook의 실제12개 layer_results |
| `ll_normalized` |`[12,1,28,768]` |저자 LL wrapper의 실제 반환값 |
| `asr_raw` |`[1,28,768]` |공식4.45.2 model의 실제 마지막 hidden |
| `asr_normalized` |`[1,28,768]` |저자 ASR wrapper의 실제 반환값 |
| `ll_pooled`, `asr_pooled` |각 `[1,768]` |실제 concat 입력을 분할 |
| `concat` |`[1,1536]` |DNN forward pre-hook의 실제 입력 |
| `logits` |`[1,5]` |저자 compute_forward 실제 반환값 |

현재 모델 exporter는 부모 소유 `work/speech-maturity-parity/export_current.py`다. 본 작업자는 수정하지 않았다.

## 실행 파일과 실제 순서

1. `build_extractions.py`: stdlib AST 추출/구문검사만 수행. 모델 생성/실행 없음. 이미 준비했다.
2. `generated_fairseq.py`, `generated_hf445.py`, `generated_author.py`: 부모가 모델 생성 없는 bounded import smoke를 수행해 통과했다. exit0, 2.460603초, peak Job committed828,432,384 bytes, termination=null, cleanup_complete=true, remaining_job_processes=0. 근거는 `work/speech-maturity-parity/import-supervised.log`, `import-supervision.json`이다.
3. `export_native.py`: import 확인 및 코드 동결 후 아래 명령을 부모가 Windows Job3GiB/300초 안에서 실행했다. 소스 integrity·weight size/SHA·weights_only CPU strict431키를 확인하며 network를 차단했다.

```powershell
work/ast-portability/venv/Scripts/python.exe work/speech-maturity-native-reference/export_native.py --inputs work/speech-maturity-parity/inputs --weights work/speech-maturity-checkpoint-audit/weights --cfg work/speech-maturity-reconstruction/confirmed-cfg.json --output work/speech-maturity-parity/native
```

출력 디렉터리가 이미 있으면 실행을 거부한다. 실패 시 원인을 확인하지 않은 묵시적 재실행, 조건/창길이/threshold 변경은 하지 않는다. 설치·unsafe pickle·bootstrap tensor loading·private media·제품 수정은 이 harness의 경로에 없다.

native 출력은 `work/speech-maturity-parity/native/`의 `tone500.npz`, `cry500.npz`, `metadata.json`이다. 현재 재구성 출력은 같은 상위 경로의 `current/`에 있다. 두 경로가 동일 입력 manifest/contract hash를 사용한 것을 대조했고, 부모가 저장된 tensor만 비교하여 `comparison.json`을 작성했다.

### 실행 전 읽기 검토에서 고친 포착 위치

Sol 검토에서 `Wav2Vec2Model.extract_features()`가 `self.forward()`를 직접 부르므로 모델 자체의 forward hook이 호출되지 않는다는 점을 발견했다. 모델 실행 전에 `ll_raw` hook을 실제 `__call__`로 호출되는 `ll.encoder`로 옮겼다. encoder의 반환 tuple에 있는 layer_results를 읽으며 계산 본문이나 출력값을 바꾸지 않는다. 이 문제 때문에 모델을 실행하거나 비교 조건을 변경한 적은 없다.

## 실측 결과

직접 읽은 근거는 `work/speech-maturity-parity/comparison.json`, `current/report.json`, `native/metadata.json`, `import/current/native-supervision.json`이다. 분류 top1은 저장된 비민감 `.npz`의 logits에서 확인했으며 모델을 재실행하지 않았다.

| 계산 대조 | 두 입력에 걸친 최대 절대 오차 | 판정 |
|---|---:|---|
| LL raw12층 stack |1.78813934e-6 |원소별 기준 통과 |
| LL normalized12층 stack |8.58306885e-6 |원소별 기준 통과 |
| ASR raw / normalized |0 / 0 |정확히 일치 |
| LL pooled |7.15255737e-7 |원소별 기준 통과 |
| ASR pooled |0 |정확히 일치 |
| concat |7.15255737e-7 |원소별 기준 통과 |
| logits |9.53674316e-7 |원소별 기준 통과 |

각 입력의8개 tensor 모두 shape/float32/finite 검사를 통과했고 원소별 허용오차 위반은0이다. 가장 큰 `error / (atol + rtol*abs(reference))`도 약0.04808이었다. 결과를 확인한 뒤 허용오차를 변경하지 않았다.

| 고정 입력 | 공식 소스 참조 top1 | 현재 재구성 top1 |
|---|---|---|
| 합성 tone500 |Non-Canonical |Non-Canonical |
| 공개 cry500 |Crying |Crying |

두 입력은 계산 대조 표본이며 정확도 분모가 아니다. 공개 파일명은 독립 청취 정답을 대신하지 않는다.

| 감독 실행 | 전체 경과 | Peak Job committed memory | 종료 |
|---|---:|---:|---|
| 모델 생성 없는 import |2.460603초 |828,432,384 bytes |exit0 |
| 현재 HF 재구성 |24.374923초 |3,089,428,480 bytes |exit0 |
| 공식 소스 native |5.275025초 |2,285,608,960 bytes |exit0 |

세 실행 모두 termination=null, cleanup_complete=true, remaining_job_processes=0, memory_limit_hit=false, output_truncated=false다. 메모리는3GiB Job committed 기준이며 RSS/휴대폰 RAM 수치가 아니다. 전체 경과에는 서로 다른 import/startup이 포함되므로 이 표를 두 구현의 추론 속도 비교로 사용하지 않는다. 원저자 weight 및 제품 정책은 변경하지 않았다.

## 결과 해석과 다음 경계

**이 고정 tone 입력의 Non-Canonical 출력을 LL→HF 계산 이식 오류로 설명할 근거는 약해졌다.** 원래 Fairseq 계산 경로, 이전 HF ASR 계산 경로, 저자 pooling/head를 통과한 참조도 같은 출력을 냈고 층별 오차는 작았다. 다만 이것은 모든 입력·모든 실행환경의 이식 오류가 없다는 증명은 아니다. 다른18구간 전체에 이번2개 입력의 동등성을 그대로 확장하지 않는다.

이 분류기는 주어진 소리를 정해진5범주 중 하나로 배정한다. `Junk`가 포함되어 있어도 **Non-Canonical/Canonical 출력 자체가 아기 발성의 존재나 발성 주인을 입증하지 않는다.** 사람이 말하지 않은 합성 tone에서 Non-Canonical이 나온 이번 반례는 그 출력을 제품의 아기 존재 판정으로 직접 연결할 수 없음을 보여 준다. 이미 확인된 아기 발성의 성숙도 분류라는 원래 사용 범위에서의 성능 전체를 부정하는 결과는 아니다.

새 임계값·창 길이 튜닝이나 이전18구간 재실행을 정당화하는 근거는 생기지 않았다. 제품 보완 기능 활성화·배포도 하지 않는다. 후속 AV 주체/발성 동기화 확인 후보 조사는 별도 단계이며, TalkNet 등의 주체 동기화 결과 역시 감정·욕구·의도 정답을 자동으로 제공하지 않는다. 최종 영상/의도 생성 목표는 계속 미달성이며 소리5종 분류로 축소하지 않는다.
