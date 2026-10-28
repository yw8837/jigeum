# SpeechMaturity 설정 확인과 연구용 재구성

검토: 2026-10-04 · 기준 HEAD `c1b94d9` · 대상 `W2V2-LL4300-Pro-SM` 한 개

## 현재 상태

저장된 tensor metadata의 **LL4300 211개 키를 ASR/HF형 211개 키에 일대일 대응**했다. 추가로 승인된 bootstrap 한 파일에서 pickle을 실행하지 않고 primitive cfg를 확인하여 입력 정규화·stride·head 수·norm 순서의 미확정 사항을 해소했다.

연구용 `tools/research/speech_maturity_runtime.py`와 focused synthetic gate `tools/research/test_speech_maturity_runtime.py`를 작성했다. **부모 작업이 수정된 Windows supervisor 아래 실제 strict 복원431키와 synthetic forward를 실행해 통과했다.** 세부 수치와 검증 경계는 §5에 기록했다.

이 작업자는 원음·파생 미디어를 읽거나 실행하지 않았다. 기존 세 weight의 재다운로드·독립 재감사, 패키지 설치/변경, 학습 recipe, ADB/Gradle, 제품 코드/임계값 변경도 하지 않았다. 실제 모델 로딩 시 고정 파일의 size/SHA를 다시 확인하는 것은 로딩 출처 검증이다.

## 1. 고정 근거와 bootstrap 확인

- 저자 source commit: `68850104737ab271cf25f47908070b6b73f06b89`.
- task/bootstrap HF revision: `9aac676c2b29c42d580b862a0294bc3807788847`.
- ASR base config/preprocessor revision: `0b5b8e868dd84f03fd87d01f9c4ff0f080fecfe8`. base weight는 받지 않았다.
- 기존 세 state의 구조는 [checkpoint 감사](20261004-speech-maturity-checkpoint-audit.md)의 저장 JSON을 재사용했다. 기존 감사의 Windows 자식 프로세스/RAM 측정 한계는 여전히 유효하며 원래 runner를 재사용하지 않는다.

bootstrap은 정확히 `spoglab/w2v2-pro-sm/checkpoint_best.pt` 한 파일이다.

| 확인 | 결과 |
|---|---|
| 실제 파일 크기 |1,140,642,009 bytes |
| 실제 SHA256 |`24b06d1835159d56be306d86760be56aa03f06dc7a4b865ffdfd02cc36e75c6b` |
| ZIP |228 members, expanded 1,140,597,100 bytes |
| 읽은 pickle metadata |`archive/data.pkl`, 61,778 bytes; 8MiB 상한 이내 |
| 정적 분석 |`pickletools` 11,524 opcodes, 실행하지 않음 |
| GLOBAL 표기 |`collections OrderedDict`, `torch FloatStorage`, `torch._utils _rebuild_tensor_v2` |

`args=None`, `cfg`의 model/task는 plain dict 영역이다. 저장한 opcode JSON에서 **고정 offset의 primitive key/value만** 대조해 추출했다. 일반 pickle VM, `pickle.load`, `torch.load`, allowlist, expression `eval`을 사용하지 않았다. 위 GLOBAL 이름의 존재를 객체 실행의 안전성 증명으로 삼지 않는다. bootstrap tensor/optimizer는 복원하지 않았다.

ignored 근거: `work/speech-maturity-reconstruction/bootstrap-metadata.json`, `bootstrap-all-opcodes.json`, `bootstrap-field-opcodes.json`, `confirmed-cfg.json`. 마지막 파일에 primitive opcode offset과 출처 hash를 보관했다.

## 2. 확정 cfg와 전처리

| 항목 | LL4300 | ASR |
|---|---|---|
| waveform norm |**`cfg.task.normalize=false`**, 적용하지 않음 |pinned preprocessor `do_normalize=true`; `[T]`에 `F.layer_norm`, eps1e-5 |
| sample rate |16,000Hz |16,000Hz |
| CNN channels/kernel/stride |512×7 / `[10,3,3,3,3,2,2]` / `[5,2,2,2,2,2,2]` |동일, pinned base config |
| CNN norm/bias/activation |`extractor_mode=default`: 첫 층 groupnorm / false / GELU |group / false / GELU |
| encoder |12 layers, dim768, FFN3072, 12 heads, GELU |동일 |
| transformer |`layer_type=transformer`, `layer_norm_first=false` |`do_stable_layer_norm=false` |
| positional conv |kernel128, groups16, depth1 |kernel128, groups16 |
| feature crop / required multiple |1 / 2 |별도 Fairseq padding 없음 |
| quantize_input |false |사용하지 않음 |
| output norm |12-layer stack 전체 `[12,B,T,768]`에 layer norm |사례별 마지막 hidden `[T,768]`에 layer norm |
| pooling |각 layer 시간 평균+작은 noise 후 학습된 12-layer softmax weight |마지막 hidden 시간 평균+작은 noise |

LL convolution 표현식은 `[(512, 10, 5)] + [(512, 3, 2)] * 4 + [(512,2,2)] + [(512,2,2)]`였으며 실행하지 않고 위 상수 배열로 전사했다. LayerNorm/functional epsilon은 공식 소스 기본1e-5다. bootstrap의 encoder layerdrop .05는 저자 task wrapper에서0으로 덮어쓰며 본 모델은 eval 모드다. SpecAugment는 비활성화한다.

### 고정 길이 계약

- API는 CPU float32 `[1,9217]`만 받는다. 16kHz에서 약576.06ms이며 CNN 출력28 frames는 Fairseq required multiple2를 이미 만족한다. 따라서 이 길이에서는 추가 feature padding이 필요 없다.
- `center_pad_utterance`는 1차원1120..9217 samples를 중앙 zero-pad한다. 8000 samples라면 왼쪽608, 오른쪽609다. 자동 truncate/resample/amplitude 조정은 없다.
- **70ms(1120 samples)는 확인된 학습 최소 길이가 아니다.** 저자 공개 inference recipe가 그보다 짧은 입력을 끝에 pad하는 보호 길이다. 본 helper는 짧은 remainder 처리를 임의 확장하지 않도록 로컬 하한으로 두었다. caller는 미지원 구간과 coverage를 기록해야 한다.
- 논문은 modal500ms 발성과 중앙9217 padding을 설명한다. 10/20초 전체 녹음은 이 API의 입력이 아니다. 500ms 고정창 분할도 수동 발성 분할과 동일하다고 볼 수 없다.

## 3. tensor mapping과 strict 복원

`map_metadata.py`는 저장된 `head/ll4300/asr-structure.json`만 읽었으며 torch를 import하지 않았다. exit0, LL4300 source211 / HF형 target211, 누락·중복·shape/dtype 불일치0이다. `tensor-mapping.json`에 입력 JSON hash와 모든 대응을 저장했다. 이것은 실제 설치 module에 대한 strict load 결과가 아니다.

아래 표의 공통 `model.` prefix는 bare HF module 로딩 때 한 번 제거한다.

| LL4300 source | HF canonical target | 개수 |
|---|---|---:|
| `mask_emb` |`masked_spec_embed` |1 |
| `feature_extractor.conv_layers.j.0.weight` |`feature_extractor.conv_layers.j.conv.weight` |7 |
| `feature_extractor.conv_layers.0.2.{weight,bias}` |`feature_extractor.conv_layers.0.layer_norm.{weight,bias}` |2 |
| `post_extract_proj.{weight,bias}` |`feature_projection.projection.{weight,bias}` |2 |
| `layer_norm.{weight,bias}` |`feature_projection.layer_norm.{weight,bias}` |2 |
| `encoder.pos_conv.0.{bias,weight_g,weight_v}` |`encoder.pos_conv_embed.conv.{bias,weight_g,weight_v}` |3 |
| `encoder.layer_norm.{weight,bias}` |동일 |2 |
| `encoder.layers.i.self_attn.{k_proj,v_proj,q_proj,out_proj}.{weight,bias}` |`encoder.layers.i.attention.{k_proj,v_proj,q_proj,out_proj}.{weight,bias}` |96 |
| `encoder.layers.i.self_attn_layer_norm.{weight,bias}` |`encoder.layers.i.layer_norm.{weight,bias}` |24 |
| `encoder.layers.i.fc1.{weight,bias}` |`encoder.layers.i.feed_forward.intermediate_dense.{weight,bias}` |24 |
| `encoder.layers.i.fc2.{weight,bias}` |`encoder.layers.i.feed_forward.output_dense.{weight,bias}` |24 |
| `encoder.layers.i.final_layer_norm.{weight,bias}` |동일 |24 |

ASR211키는 위 의미 변환 없이 공통 prefix를 제거한다. 설치 module이 modern weight norm을 쓰면 두 branch의 `weight_g/v`는 각각 `parametrizations.weight.original0/original1`로 대응한다. module의 실제 key set과 전체 shape/dtype를 대조하고 누락·중복·잉여를 거부한다.

Head9키는 `0.linear.w`2개, `0.norm.norm`5개(BatchNorm running buffers 포함), `1.w.weight [5,512]`, `2.w.weight [1,12]`다. Linear1536→512, BatchNorm, LeakyReLU, dropout, bias-free Linear512→5와 학습된 layer weight를 모두 복원한다. `strict=False`나 무작위 잔여 parameter 허용은 없다. 모든 state는 `weights_only=True`, CPU로만 읽도록 구현했다.

라벨 순서: **0 Junk, 1 Non-Canonical, 2 Canonical, 3 Laughing, 4 Crying**. 5-way logits/softmax이며 AST multilabel 기준을 이식하지 않는다.

## 4. 설치된 Transformers 소스 대조

현재 환경의 Transformers5.14.1 `modeling_wav2vec2.py`와 `configuration_wav2vec2.py`를 파일로 읽었다. torch/transformers import나 model 생성 없이 검사했다.

- Wav2Vec2Model forward는 입력을 직접 CNN에 넘긴다. waveform normalize를 암묵적으로 추가하지 않는다.
- post-LN encoder는 첫 layer 입력과 각 layer 출력을 모아13개를 반환한다. **`hidden_states[1:]`12개**가 저자 Fairseq layer_results의 각 layer 직후 위치에 대응한다. LL cfg가 post-LN으로 확인되었으므로 pre-LN의 마지막 outer norm 차이는 해당하지 않는다.
- positional conv는 weight norm dim2, modern parametrization original0/1, even kernel128의 마지막1 timestep 제거를 사용한다.
- 첫 CNN은512 group의 GroupNorm, 이후 CNN에는 norm이 없다. feature projection과 transformer LayerNorm eps는 명시한 설정을 따른다.
- eager attention을 명시한다. 모델의 중요 architecture 필드는 명시적 config로 생성하며 `.from_pretrained()`를 사용하지 않는다.

설치 경로는 `C:/Users/최용우/AppData/Local/Programs/Python/Python313/Lib/site-packages/transformers/models/wav2vec2/`다. 검토 source SHA256:

| 파일 | SHA256 |
|---|---|
| modeling_wav2vec2.py |`c6ee256c01c9c640f7e00dabe6bb480d6e6f8aae532671f46acbbe95c198cd2d` |
| configuration_wav2vec2.py |`0980b1c75eae8565d19acdfd66239f52be9eaaa34b48d5f72266dacfc06b7e55` |

소스 대응은 저자 환경과의 수치적 동등성 시험을 대신하지 않는다. Fairseq/HF kernel 연산 순서와 환경 차이에 대한 native cross-runtime 비교는 미실행이다.

### pooling RNG 제한

저자 StatisticsPooling의 eval 중 noise 식(`randn→min/max normalize→1e-5*((1-9)*noise+9)`)을 유지한다. 호출 순서는 ASR pool, LL layer0..11이다. 본 wrapper는 **전용 CPU generator와 고정 `pooling_seed`**를 쓴다. HF encoder는 eval에서도 layerdrop 검사용 global random draw를 하므로 저자 global RNG stream과 bitwise 같다고 주장하지 않는다. 같은 입력/같은 seed의 정확한 출력 재현은 합성 gate에서 확인했다.

## 5. 실제 synthetic 실행과 검증 경계

부모가 수정된 Windows supervisor(3GiB Job committed memory / 300초) 아래 다음 child command를 실행했다. 기존 one-off 감사 runner는 사용하지 않았다.

```powershell
work/ast-portability/venv/Scripts/python.exe tools/research/test_speech_maturity_runtime.py --weights work/speech-maturity-checkpoint-audit/weights --output work/speech-maturity-reconstruction/synthetic-result.json
```

focused gate는 네트워크 접근을 차단하고 offline 환경에서 synthetic500ms sweep만 만든다. 확인 대상은 431 state keys의 strict 복원, 중앙 padding, B1/9217→5 logits shape, finite, softmax 합, 동일 seed 출력 재현, gradient 없음, batch2·10초·NaN 거부다. 출력 파일이 이미 있으면 자동 반복을 거부한다. CPU threads2/inter-op1이다.

| 실제 실행 결과 | 값 |
|---|---|
| 환경 |torch2.13.0+cpu, Transformers5.14.1 |
| strict 복원 |head9 / LL4300 211 / ASR211 |
| 출력 |`[1,5]`, finite, softmax 합 검사 통과 |
| 같은 입력/seed 2회 |`torch.equal=true` |
| 잘못된 입력 |batch2, 10초, NaN 모두3개 거부 |
| trainable parameter |없음 |
| 모델 load |3.338387초 |
| synthetic forward |0.372552 / 0.249538초 |
| supervisor 전체 경과 |31.490483초, exit0 |
| peak Job committed memory |3,087,216,640 bytes; cap3,221,225,472 bytes; limit hit=false |
| 종료 정리 |cleanup_complete=true, remaining_job_processes=0, termination=null |

근거는 ignored `work/speech-maturity-reconstruction/synthetic-result.json`, `synthetic-supervision.json`, `synthetic-supervised.log`다. 이 RAM 수치는 프로세스 트리의 **Job committed memory**이며 모델 tensor 크기나 working set/RSS와 같지 않다. 로컬2회 synthetic 시간은 모바일 성능 지표가 아니다.

API:

```python
model = load_frozen_model(weights_directory)
waveform = center_pad_utterance(float32_mono_tensor)
logits = model(waveform, pooling_seed=2025)  # [1,5], CPU, no gradients
```

문법 검사와 diff 검사도 통과했다. 로컬 synthetic 성공만으로 아기 발성 분류 품질, 발성 주인, 영상·감정/욕구 생성, Android 동작, 제품 준비 완료를 선언하지 않는다. 원음 비교는 부모의 별도 [비교 보고서](20261004-speech-maturity-comparison.md) 범위다. 상용 재배포 라이선스 미해결 사항은 [runtime 검토](20261004-speech-maturity-runtime-review.md)에 남아 있다.

## 출처

- [저자 task YAML](https://github.com/spoglab-stanford/w2v2-pro-sm/blob/68850104737ab271cf25f47908070b6b73f06b89/speechbrain/recipes/W2V2-LL4300-Pro-SM/hparams/train_1_w2v2_2dnn_WA_LL4300_asr_bbcor_concat.yaml), [forward](https://github.com/spoglab-stanford/w2v2-pro-sm/blob/68850104737ab271cf25f47908070b6b73f06b89/speechbrain/recipes/W2V2-LL4300-Pro-SM/train_1_w2v2_WA_2dnn_combine_asr_features_bbcor.py).
- [Fairseq wrapper](https://github.com/spoglab-stanford/w2v2-pro-sm/blob/68850104737ab271cf25f47908070b6b73f06b89/speechbrain/speechbrain/lobes/models/fairseq_wav2vec.py), [HF wrapper](https://github.com/spoglab-stanford/w2v2-pro-sm/blob/68850104737ab271cf25f47908070b6b73f06b89/speechbrain/speechbrain/lobes/models/huggingface_wav2vec.py), [pooling](https://github.com/spoglab-stanford/w2v2-pro-sm/blob/68850104737ab271cf25f47908070b6b73f06b89/speechbrain/speechbrain/nnet/pooling.py).
- [ASR config](https://huggingface.co/facebook/wav2vec2-base/blob/0b5b8e868dd84f03fd87d01f9c4ff0f080fecfe8/config.json), [preprocessor](https://huggingface.co/facebook/wav2vec2-base/blob/0b5b8e868dd84f03fd87d01f9c4ff0f080fecfe8/preprocessor_config.json).
- [Fairseq0.12.2 Wav2Vec2](https://github.com/facebookresearch/fairseq/blob/v0.12.2/fairseq/models/wav2vec/wav2vec2.py), [LayerNorm](https://github.com/facebookresearch/fairseq/blob/v0.12.2/fairseq/modules/layer_norm.py).
- [bootstrap 고정 파일](https://huggingface.co/spoglab/w2v2-pro-sm/blob/9aac676c2b29c42d580b862a0294bc3807788847/checkpoint_best.pt).
