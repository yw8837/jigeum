# EfficientSED TF 모델의 모바일 내보내기 조사

2026-10-05 · 고정 공식 소스·가중치의 실제 ONNX export · 합성 입력만 사용

**20.3MB 후보의 CNN+TF 분류망을 표준 ONNX로 내보내고 CPU 수치 일치를 확인했다.**
Android ORT CPU 후보로 패키징할 구체적 경로가 생겼다. PCM→mel 전처리의 전체 ONNX export는
complex STFT에서 실패했으므로 동일한 온디바이스 전처리를 별도로 구현·대조해야 한다.
Android 실행·속도·RAM·발열·새 영아 인식 성능과 제품 채택은 검증하지 않았다.

기존 [시간축 후보 실측](20261005-temporal-model-followup.md)의 정확한 모델을 사용했다.
source [e3449bfd1f4922b81d82410ed842653f462bfca8](https://github.com/theMoro/EfficientSED/tree/e3449bfd1f4922b81d82410ed842653f462bfca8),
official asset `fmn10+tf-256_advanced-kd-weak-strong.pt`, **20,304,913 bytes**, SHA256
`7d5326a485b742b323b33dd2bcb366606b614b2a5deaa5e452ef34ddedb9797c`.
전체5,028,286 parameters를 strict하게 로드했고 head를 재학습·임의 초기화하지 않았다.

## 실행 결과

| 항목 | 확인 결과 |
|---|---|
| 분류망 입력 | 고정 float32 mel **[1,1,128,1000]**, batch1/10초. 가변 길이/batch export 아님 |
| 출력 | raw strong logits **[1,447,250]**, raw weak logits **[1,447]**. 제품 점수로 쓰려면 strong logits에 sigmoid 필요 |
| export | PyTorch legacy exporter `dynamo=False`,opset17,constant folding. source의 constructor/forward를 변경하지 않음 |
| 생성 파일 | `run03/mel.onnx` **20,315,630 bytes**, 외부 weight sidecar 없이 단일 파일 |
| ONNX SHA256 | `0a1bf37323cb9069d26a63b0ca4486c9c027b29e3bff087e0acd7270022716fc` |
| checker·실행기 | ONNX1.20.1 checker 통과,ORT1.24.3 CPUExecutionProvider 실제 로드·실행 통과 |
| 그래프 | 표준 `ai.onnx` 도메인 **30종** 연산. 별도 Mamba/custom CUDA/PyTorch fallback 연산 없음 |
| PCM 전체 export | **실패**: `SymbolicValueError: STFT does not currently support complex types`. rotary/TF 연산 때문에 실패한 것은 아님 |
| 선택 INT8 시도 | MatMul/Gemm dynamic quantization의 native shape-inference 단계에서 `mel-inferred.onnx` **FileNotFoundError**. 같은 파일의 Win32 short path 재시도도 실패. INT8 모델·수치 보존·크기 절감은 미검증 |
| 최종 bounded worker | **7.433초**,peak Job committed **1,445,617,664 bytes**,180초/4GiB 한도 미도달,잔여자손0. export·checker·CPU 대조를 합친PC시간이며 Android 추론시간 아님 |

소스·weight 해시는 [pin manifest](../../tools/research/efficientsed-models.json)와 일치 확인했다.
runtime은 기존torch2.11.0+cpu/torchaudio2.11.0+cpu/torchvision0.26.0+cpu/einops0.8.2/
rotary-embedding-torch0.9.1을 사용했다. ORT1.24.3은 기존 설치본이다. 부족했던 ONNX1.20.1은
이미 cache에 있던16.4MB wheel, 추가 ml_dtypes0.6.0은439kB wheel로 설치했다.
새 모델·대형 런타임·유료 호출·온라인 추론은 없었다.

## 원본 출력과 실제 수치 대조

원본16kHz 합성 **무음**,amplitude.03의440Hz tone,seed17/amplitude.005 Gaussian noise를
각각10초로 만들었다. upstream `mel_forward`를 한 번 계산한 같은 mel을 PyTorch와 ORT에
넣었다. 출력2개×입력3개,모든shape·finite 통과. 수치 허용값은 실행 전에 고정한
`rtol=1e-3,atol=1e-4`; 모두 통과했다.

| 입력 | strong 최대 절대 logit 오차 | weak 최대 절대 logit 오차 |
|---|---:|---:|
| 무음 | 2.2888184e-5 | 1.1920929e-6 |
| 440Hz tone | 2.5749207e-5 | 1.0132790e-6 |
| Gaussian noise | 2.0980835e-5 | 1.2516975e-6 |

strong 평균 절대 오차는3.24–3.99e-6이었다. 이 결과는 float 그래프의 이식 근거이며
영아 사건검출 정확도·INT8 검증·Android 수치 보존의 대용 지표가 아니다. 사용자 음원,
파생 frame/audio/embedding과 threshold tuning은 사용하지 않았다.

## 모바일 경로와 남은 구체적 작업

[ORT 공식 mobile 개발 문서](https://onnxruntime.ai/docs/tutorials/mobile/)는 Android의
`onnxruntime-android` package에 **full feature/operator set**이 있고 모든 mobile target의
기본 실행기가CPU라고 명시한다. 이번 그래프의 표준 연산은 full CPU ORT가 실제 실행했다.
따라서 **동일 mel을 만들고 이 ONNX를 Android ORT CPU에 공급하는 경로**는 구체적인 후보다.
full package/runtime binary의 APK 증가량과 Android ABI/버전별 실행은 아직 측정하지 않았다.
작은 custom ORT build라면 별도 required-operator config가 필요하며 이번에 빌드하지 않았다.

[NNAPI supported ops](https://onnxruntime.ai/docs/execution-providers/NNAPI-ExecutionProvider.html)와
대조하면 `Cos/Erf/Expand/HardSwish/ReduceL2/ReduceMax/Shape` 등이 표에 없다.
`Constant`도 표에 없지만 compile-time 상수일 수 있으므로 단순 표 비교를 runtime 실패로
해석하지 않는다. ORT는 일부 partition을CPU로 실행할 수 있다. **NNAPI 전부 가속·NPU 지원·
XNNPACK 전체 가속을 확인했다고 보고하지 않는다**. 공식 문서도 partition과 device별 성능을
실측하도록 요구한다.

다음 단계는 upstream와 동일한 preemphasis/center reflection/nonperiodic Hann/FFT512/
hop160/Kaldi128mel/fmax7000/log/norm을 Kotlin/C++에서 구현하고 **mel 자체**를 대조하는 것이다.
10초 끝padding·40ms 출력격자·447라벨 순서·sigmoid·20초의두창 경계를 보존해야 한다.
그 후 실제ARM에서 cold/warm/취소/RAM/연속분석을 검사하고 알려진 발성·성인/TV/겹침 회귀를
확인한다. 이 문서는 그 후속 작업을 완료했다고 보고하지 않는다.

## 재현과 증거

- [synthetic export runner](../../tools/research/efficientsed_export_probe.py): 고정 로컬 source/weight만
  사용,Python socket 차단,Windows Job Object180초·4GiB. 신뢰된 연구 코드의 자원 경계이며
  native코드 전체 network sandbox를 주장하지 않음.
- runner 후속 검토에서 수치 기준을 기록만 하고 성공 종료를 막지 않는 결함을 수정했다.
  지금은 **필수 mel float export 성공 + 무음/tone/noise×strong/weak 6개 전체의 고정shape·finite·
  tolerance 통과**가 `float_parity_passed=true`의 조건이다. 실패하면 저장된 `status=failed`,
  `float_parity_passed=false`와 **worker exit2**를 반환한다. 부모 실행기도 exit/cleanup뿐 아니라
  저장된 flag와 원래6개 수치행을 다시 검사한다. 거짓 `completed`나 forged true flag만으로
  성공을 표시할 수 없다. PCM/INT8 선택 검사의 실패는 이 필수float gate와 별도로 비치명적이다.
- ignored `work/efficientsed-export-20261005/run03/`: `mel.onnx`, `result.json`, `process.json`,
  `export.log`,공식 mobile 문서 대조 `mobile-source-check.json`.
- 종료조건 보완은 모델을 재실행하지 않았다. 기존 실제 수치행을 이용한 parser8조건
  (정상/선택실패,export실패,nonfinite,tolerance실패,누락,shape오류,falseflag,forgedtrueflag)을
  확인했고 정상1개만 수락했다. 실제worker 마지막gate 코드를 AST로 분리해 tolerance실패를
  주입하자 저장status실패/flagfalse/exit2를 확인했다. 증거는 ignored
  `work/efficientsed-export-20261005/gate-tests.json`이다. 과거run03의 원본result에는 새gate
  flag가 없으므로 그 파일을 새runner에서 생성한 결과로 바꾸어 보고하지 않는다.
- run01의 export harness는 inference tensor를 trace에 주어 실패했다. `no_grad`로 바로잡은
  run02와run03은 **동일ONNX SHA·float 수치**를 확인했다. PCM STFT 실패는 세 번 모두 남았다.
  run02→03은 optional quantizer의 파일경로 대조이며 모델·threshold 변경이 아니다.
- INT8 shape-inference 파일 오류의 정확한 원인은 확정하지 않았다. Unicode Windows 경로가
  관여할 가능성이 있으나 short path도 사용자 이름을 그대로 남겼고 해결되지 않았다.
  이번 범위에서 추가 환경 설치·별도 native patch로 확장하지 않았다.

제품 코드·빌드·ADB·Play·기존 모델·임계값·사용자 미디어는 변경하지 않았다.
