# SpeechMaturity checkpoint 3개 제한 로딩 감사

실행일: 2026-10-04 · host Python 파일 검사 · 실제 미디어/합성 입력 추론 없음

## 결과

[런타임 검토](20261004-speech-maturity-runtime-review.md)에 고정한 **공개 checkpoint 3개, 총758,308,213 bytes를 다운로드했다.** 모두 로컬 크기와 SHA256이 HF LFS metadata와 일치하고, `torch.serialization.get_unsafe_globals_in_checkpoint`에서 추가 global이 나오지 않았다. 이어 각 파일을 별도 오프라인 subprocess에서 **`torch.load(..., map_location="cpu", weights_only=True)`로 한 번씩** 읽었다. 세 subprocess 모두 exit0이다.

로드 결과는 모두 tensor-only `OrderedDict`다. 총431개 tensor(430 FP32, 1 int64)의 shape/dtype/유한성 검사를 통과했다. 각 매핑의 top-level에 `cfg`, `config`, `args`, `hparams`, `normalize`, `task` 설정 키는 없었다. **안전한 state 로딩 가능성은 확인됐고, 완전한 모델 복원·정규화 설정·추론 결과는 확인하지 않았다.**

제품 코드, 기존 AST 환경, 원음/파생 미디어를 변경하거나 읽지 않았다. 추가 bootstrap/base weight, 저장소 snapshot, 새 패키지는 받지 않았다. ADB·Gradle·학습·모델 forward·제품 배포를 실행하지 않았다.

## 파일과 검증 해시

저장소: `spoglab/w2v2-pro-sm`

고정 revision: `9aac676c2b29c42d580b862a0294bc3807788847`

| HF 상대 경로 | 실제 bytes | 로컬 SHA256 = 고정 LFS SHA256 |
|---|---:|---|
| `CKPT+2025-02-08+21-43-25+00/model.ckpt` | 3,169,378 | `cbd674239f8f1f6b9fe7d7b4d604b8de9eea2ca3fa60633c1f84388a61d335e8` |
| `CKPT+2025-02-08+21-43-25+00/wav2vec2.ckpt` | 377,566,819 | `f778e63ca45d2e7e632244762162eb2772107c0f0846511be8818a8dce8bbd0e` |
| `wav2vec_asr.ckpt` | 377,572,016 | `f112f7506c754cd01d5c0b7163cc3b572e4edd4e6775277a600685ad03c7a540` |

실제 파일은 ignored `work/speech-maturity-checkpoint-audit/weights/`에 위 경로 그대로 있다. 저장소 전체를 다운로드하지 않았다. HF CLI가 만든 작은 cache metadata도 같은 ignored 작업 경로 안에 있다.

## 실행 명령과 종료 상태

실행 cwd는 `C:/Users/최용우/.codex/worktrees/ondevice-av-probe/plugin-browser-openai-bundled-mention-tab`다. parent runner의 실제 실행 명령:

```powershell
& work/ast-portability/venv/Scripts/python.exe work/speech-maturity-checkpoint-audit/run.py
```

runner는 아래 argv 형식으로 **파일별 순차** 다운로드를 실행했다. `FILE`에는 위 표의 각 정확한 상대 경로를 하나씩 넣었고 `AUDIT`는 cwd 아래 `work/speech-maturity-checkpoint-audit`의 절대 경로다. 실제 전체 argv는 `summary.json`에 보존했다.

```text
C:/Users/최용우/AppData/Local/Programs/Python/Python313/Scripts/hf.EXE
  download spoglab/w2v2-pro-sm FILE
  --revision 9aac676c2b29c42d580b862a0294bc3807788847
  --local-dir AUDIT/weights --max-workers 1 --quiet
```

각 파일의 SHA와 ZIP 검사가 끝난 뒤 실제 worker argv:

```text
work/ast-portability/venv/Scripts/python.exe work/speech-maturity-checkpoint-audit/run.py --worker head
work/ast-portability/venv/Scripts/python.exe work/speech-maturity-checkpoint-audit/run.py --worker ll4300
work/ast-portability/venv/Scripts/python.exe work/speech-maturity-checkpoint-audit/run.py --worker asr
```

worker는 모델을 만들지 않는다. 정적 global 확인, CPU 제한 로딩, 키/shape/dtype/요소 수와 chunk별 finite 검사만 한다.

| 파일 역할 | download exit | 다운로드 초 | audit exit | 감사 subprocess 초 | unsafe globals |
|---|---:|---:|---:|---:|---:|
| head | 0 | 3.594 | 0 | 2.608 | 0 |
| LL4300 | 0 | 31.910 | 0 | 2.808 | 0 |
| ASR | 0 | 30.989 | 0 | 2.808 | 0 |

최종 runner exit0. pass 범위는 **파일 size/SHA, ZIP metadata 한계, weights_only CPU 로딩, tensor metadata·finite**다. 이 시간은 파일 다운로드/역직렬화·메타데이터 감사 시간이며 모델 추론 속도가 아니다.

**최초 명령 오류도 보존했다.** 처음 `hf download`에 `--local-dir`와 `--cache-dir`를 함께 지정했는데, 설치된 CLI1.26.0이 네트워크 다운로드 전에 인자 오류로 exit1을 반환했다. 두 옵션을 함께 못 쓰는 현 CLI 계약에 맞춰 `--cache-dir`를 제거한 뒤 실행했다. 원 오류는 `cli-argument-error-summary.json`과 `cli-argument-error.log`에 있다. 성공한 파일을 재다운로드하거나 이미 완료한 제한 로딩을 반복하지 않았다. 완료 `summary.json`이 존재하면 runner는 재실행을 거절한다.

## 제한 조건과 ZIP 검사

- 기존 Python3.13.14, torch2.13.0+cpu를 사용했다. torch thread2/inter-op1. 설치/다운그레이드 없음.
- 공개 자산 다운로드는 implicit token·telemetry·Xet을 비활성화하고 작업 경로에 HF_HOME을 뒀다. 파일별 외부 시도1회, 다운로드 subprocess 최대300초, HF read timeout30초/metadata timeout15초였다. CLI 내부 전송 retry 횟수를 별도로 계측하지 않았지만 전체 process timeout이 적용된다.
- 감사 worker는 HF/Transformers offline 환경과 `TORCH_FORCE_WEIGHTS_ONLY_LOAD=1`을 설정하고 Python socket의 connect/connect_ex/create_connection/getaddrinfo를 차단했다. 명시적으로 `weights_only=True`를 전달했으며 allowlist를 추가하지 않았다. 이는 OS 전체 네트워크 sandbox를 새로 구성한 것은 아니다.
- 파일별 감사 subprocess timeout90초, tensor 총요소 상한3억개, 키 길이512자, 유한성 검사 chunk65,536요소를 설정했다. timeout/제한로딩 실패 시 unsafe loading으로 재시도하지 않는다.
- ZIP은 추출하지 않고 central directory만 검사했다. member≤2000, 중복/절대/상위 경로·암호화 member 거부, 단일 member≤1GiB, 압축비≤100, pickle member≤8MiB, 전체 확장 bytes≤파일 크기×1.05+1MiB 기준이다. 모두 통과했다.

| 역할 | ZIP member 수 | 총 확장 bytes | 압축 방식 | pickle member |
|---|---:|---:|---|---|
| head | 13 | 3,167,410 | stored(0) | `model/data.pkl` |
| LL4300 | 215 | 377,525,834 | stored(0) | `wav2vec2/data.pkl` |
| ASR | 213 | 377,531,415 | stored(0) | `wav2vec2/data.pkl` |

**자식 프로세스 자원 제한은 미검증이다.** psutil이 감시한 Windows venv launcher PID의 표본은 약4MB로, 실제 torch 자식 메모리를 나타내지 않는다. timeout/2GiB 초과 시 `process.kill()`도 launcher만 대상으로 하므로 자식 종료까지 검증됐다고 할 수 없다. 이번 세 worker는 모두 정상 exit0이어서 timeout/종료 경로가 작동할 필요가 없었다. `sampled_peak_working_set`을 모델 RAM으로 사용하지 않는다. ZIP·tensor 검사 통과와 자원 게이트를 구분한다. 동일 파일을 재검사하지 않았다.

`summary.json`에 원래 측정값·로그를 보존하면서 사후 해석을 명시했다: `memory_measurement_valid=false`, `working_set_limit_verified=false`, `process_tree_termination_verified=false`, `runner_reuse_allowed=false`, `pass_scope`. one-off runner를 재사용하지 않으며 완료 가드를 유지한다. 향후 별도 실행기는 process tree 감시/종료 또는 Windows Job Object가 필요하다.

## Tensor와 모듈 구조

| 역할 | tensor 수 | 요소 수 | tensor storage bytes 합 | dtype | 비tensor mapping 값 |
|---|---:|---:|---:|---|---:|
| head | 9 | 791,565 | 3,166,264 | FP32 8개, int64 1개 | 0 |
| LL4300 | 211 | 94,371,712 | 377,486,848 | FP32 211개 | 0 |
| ASR | 211 | 94,371,712 | 377,486,848 | FP32 211개 | 0 |
| 합계 | **431** | **189,534,989** | **758,139,960** | FP32 430개, int64 1개 | **0** |

요소 수는 학습 parameter뿐 아니라 BatchNorm buffer 등을 포함한다. 파일 크기와 tensor storage 합, peak RAM은 서로 다른 양이다. 모든 tensor가 strided layout이며 floating tensor 전체의 finite 검사를 통과했다. 값 전체나 개별 weight 값을 출력하지 않았다.

### Head: 실제 9개 key

- `0.linear.w.weight [512,1536]`, `0.linear.w.bias [512]`: DNN Linear2개.
- `0.norm.norm.{weight,bias,running_mean,running_var} [512]`, `num_batches_tracked []`: BatchNorm5개.
- `1.w.weight [5,512]`: 5-way classifier1개. output bias 없음.
- `2.w.weight [1,12]`: 12-layer weighted average1개.

이 구조는 앞서 읽은 YAML ModuleList 순서와 맞는다. 그러나 아직 실제 module에 strict 로드해 실행한 것은 아니다.

### Encoder: 키 이름과 그룹

두 encoder 모두 transformer layer index **0…11**, 각16개 tensor, 합192개 tensor다. encoder별 나머지19개는 아래 구성이다.

| 구조 | LL4300 키 계열 | ASR 키 계열 | tensor 수 |
|---|---|---|---:|
| mask embedding | `model.mask_emb` | `model.masked_spec_embed` | 1 |
| CNN feature extractor | `model.feature_extractor.conv_layers` | 같은 상위 prefix, 하위는 `.conv/.layer_norm` | 9 |
| feature projection | `model.post_extract_proj` | `model.feature_projection.projection` | 2 |
| CNN output normalization | `model.layer_norm` | `model.feature_projection.layer_norm` | 2 |
| positional conv | `model.encoder.pos_conv.0` | `model.encoder.pos_conv_embed.conv` | 3 |
| encoder layer norm | `model.encoder.layer_norm` | 동일 | 2 |
| transformer layers | `.self_attn/.fc1/.fc2` 등 Fairseq 이름 | `.attention/.feed_forward` 등 HF 이름 | 192 |

주요 확인 shape는 CNN channel512, kernel `[10,3,3,3,3,2,2]`, projection `[768,512]`, attention q/k/v/out `[768,768]`, FFN `[3072,768]`와 `[768,3072]`다. positional conv는 두 쪽 모두 `weight_g [1,1,128]`, `weight_v [768,48,128]`, bias `[768]`이다. 최신 Torch/Transformers의 weight-norm parametrization 이름으로 옮길 때 명시적 대응이 필요하다.

두 encoder의 크기·shape 수가 같다고 값·학습 역할·동작이 같다는 뜻은 아니다. 양쪽 weight 값을 비교하거나 복사·변환하지 않았다.

## 이번 결과로 확인되지 않은 것

- cfg/config/args 등은 검사한 top-level tensor mapping에 없다. state dict 외부의 원 학습 환경·전처리 설정이 복원됐다는 뜻은 아니다. LL4300 입력 normalize, CNN stride, attention head 수, activation, norm 순서/epsilon 등은 tensor shape만으로 전부 확정할 수 없다.
- model architecture를 생성하지 않았으므로 `strict=True` 복원, state key 전수 mapping, hidden-state 의미, pooling/norm 동등성은 미검증이다.
- unsafe global 목록이 비었다는 사실은 파일이 모든 상황에서 안전하다는 인증이 아니다. 이번 제한 로더에서 실제 로딩이 완료됐다는 범위다.
- 9217샘플 학습 전처리, batch1/마스크/패딩 동작, 출력5-way 분류, 기존 음성 누락 회복, 정확도·한국어 일반화·실기기 성능은 실행하지 않았다.
- 다운로드 가능한 공개 weight를 검사한 결과이며 상용 채택·재배포 권리 확인을 완료한 것이 아니다. 기존 [권리·artifact 검토](20261004-speech-maturity-artifacts.md)의 범위를 유지한다.

## 다음 단계

**파일 무결성·제한 로딩·finite 단계는 통과했고 여기서 종료한다.** 다음 한 단계는 공개 소스/작은 config와 저장한 키 목록을 이용한 **strict mapping 명세 및 LL4300 비텐서 설정 확보**다. 이미 받은 checkpoint를 다시 다운로드하거나 같은 감사만 반복할 필요가 없다.

ASR의 HF형211키와 head9키는 고정 base config/저자 YAML로 직접 복원 명세를 만들 수 있다. LL4300은 Fairseq형211키를 공식 mapping과 대응시키되, **입력 normalize·CNN stride·attention head 수·activation·norm 순서/epsilon**을 tensor shape로 추측하지 않는다. 안전한 다음 경로는 공개된 작은 공식 config/고정 source를 읽어 각 필드 출처를 채우는 것이다. 확정할 수 없는 항목은 blocker로 남긴다. 추가1.14GB bootstrap, 새 dependency 설치, 모델 forward는 이번 단계에서 하지 않았다.

추후 추론을 준비하는 경우 먼저 완전한 tensor 대응·batch1 shape·학습/패딩/norm 계약·합성 입력 검증을 해야 한다. 실제 아기 자료 비교나 제품 반영은 이번 파일 검사 통과와 별개의 결과다.

## 보존 근거

모두 ignored `work/speech-maturity-checkpoint-audit/` 안에 있다.

- `run.py`: 실행한 one-off 다운로드·제한 로딩 runner.
- `summary.json`, `progress.json`: 실제 argv, size/SHA, ZIP, exit/time/상한 및 집계.
- `head-structure.json`, `ll4300-structure.json`, `asr-structure.json`: tensor key/shape/dtype/finite·module 그룹. weight 값은 없음.
- `*-download.log`, `*-audit.log`, `cli-argument-error*`: 실행 및 최초 CLI 인자 오류.
- `weights/`: 고정 공개 checkpoint3개와 HF 로컬 metadata.

Git ignore 대상임을 확인했다. binary/log를 추가·커밋하지 않았다. 공개 보고서는 이 문서 하나만 작성했다.
