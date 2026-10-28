# 현재 AST INT8 모델의 embedding 인터페이스 감사

2026-10-07. 현재 앱의 고정 AST ONNX **그래프를 읽은 정적 감사**다.
새 다운로드, 추론, 학습, PCM·개인 feature 읽기, 모델 복사·변형·저장, Android 빌드·ADB는 수행하지 않았다.
작성 파일은 이 문서 하나다. 표현 노출 가능성과 품질·수치 동등성·모바일 검증을 구분한다.

## 1. 결론과 정확한 모델

**현재 encoder에 작은 head를 붙일 구조적 경로는 있다.** 분류기 선형 연산 직전의
768차원 FLOAT 표현이 기존 그래프에 이미 존재한다. 새 encoder나 pretrained weight를 받을 필요는 없다.
현재 모델의 공개 output은 `logits` 하나여서 기존 ONNX Runtime 호출로 이 내부 텐서를 직접 받을 수는 없다.
후속 연구에서 graph output 선언을 추가한 메모리 내 파생 그래프 또는 별도 승인된 연구 산출물이 필요하다.
이번 감사에서는 그 변경이나 세션 생성도 하지 않았다. 수치·품질 동등성은 미검증이다.

| 항목 | 실제 관측 |
|---|---|
| 앱 자산 | `outputs/MoaAndroid/app/src/astProbe/assets/ast-dynamic-int8.onnx` |
| bytes / SHA256 | 91,134,123 / `3dff0cc2b374977ae1460258261608e33428f4767a6959cb8bc910b09433f71e` |
| 원 모델 / revision | `MIT/ast-finetuned-audioset-10-10-0.4593` / `f826b80d28226b62986cc218e5cec390b1096902` |
| 원 safetensors pin | 346,404,948B / `ae0c1e2ad4e1381d851fa9bf298ba13ebc9c5a914cdee2dbe427a6583869924d` (기존 출처 계약) |
| 감사 환경 | 기존 `work/ast-portability/venv`: Python 3.13.14, ONNX 1.20.1, ORT 1.24.4 |
| 그래프 | IR8 / ai.onnx opset17 / 852 nodes / external data 없음 / checker 통과 |
| input | `input_values`, FLOAT, `[1,1024,128]` |
| 현재 output | `logits`, FLOAT, `[1,527]` |

원 자산을 streaming SHA로 확인하고 `onnx.load(..., load_external_data=False)`와 checker로 읽었다.
ORT 버전 조회는 import뿐이며 `InferenceSession`이나 `run()`을 호출하지 않았다.
원 safetensors를 새로 읽거나 내려받지 않았다. 모델 크기는 현재 자산의 실제 측정값이다.

## 2. 실제 분류기 전단

다음 node index는 읽은 그래프의 **0-based 순서**다. 이름을 다른 모델에서 추측한 것이 아니다.

| node | op / 의미 | output / 기존 value_info |
|---|---|---|
| 840 | encoder 마지막 LayerNormalization | `/source/audio_spectrogram_transformer/layernorm/LayerNormalization_output_0`, FLOAT `[unk__3,1214,768]` |
| 841, 842 | axis1 Gather; 실제 Constant scalar index0, index1 | 각각 `/source/audio_spectrogram_transformer/Gather_output_0`, `Gather_1_output_0`, FLOAT `[unk__3,768]` |
| 843, 844 | 두 token Add 후 실제 Constant2.0으로 Div | `/source/audio_spectrogram_transformer/Div_output_0`, FLOAT `[unk__3,768]` |
| 845 | classifier LayerNormalization, axis−1 / epsilon≈1e−12 | `/source/classifier/layernorm/LayerNormalization_output_0`, FLOAT `[unk__3,768]` |
| 846 | DynamicQuantizeLinear | classifier 입력의 quantized tensor, scale, zero point |
| 847–851 | scale Mul → MatMulInteger → Cast → scale Mul → bias Add | `logits`, FLOAT `[1,527]` |

**우선 노출 후보:** `/source/classifier/layernorm/LayerNormalization_output_0`.
원 classifier가 양자화하기 직전 소비하는 표현이다. FLOAT 768개는 batch1에서 3,072B다.
현재 value_info의 batch는 `unk__3`이며 input/output과 연산 구조가 batch1을 지지한다.
후속 실행 시 실제 반환 shape `[1,768]`, dtype, finite 값을 확인해야 한다.

분류기 이름에는 `Gemm`이 남지만 현재 그래프에 Gemm op는 없다.
`/source/classifier/dense/Gemm_MatMul_quant`는 MatMulInteger이고,
상수 `source.classifier.dense.weight_quantized`는 INT8 `[768,527]`다.
layernorm weight/bias는 각각 FLOAT `[768]`, dense bias는 FLOAT `[527]`다.
이 표현을 INT8 quantized activation이나 527개 AudioSet 점수와 혼동하지 않는다.

encoder의 pooled `Div_output_0`도 구조상 후보지만 classifier layernorm 전이므로 별도 표현 계약이다.
둘 중 유리한 것을 사용자 평가 결과로 고르는 절차를 추가하지 않는다.
1214-token 전체 출력은 batch1 FLOAT만 3,729,408B이고 시간과 주파수 patch 및 특수 token을 포함한다.
1214개의 독립 시간창이나 975ms 사건 embedding으로 해석할 수 없다.

## 3. 후속 output 추가와 수치 검증 방법

추가 검증이 승인된 후에는 원 자산의 SHA를 먼저 확인하고, 기존 node·initializer·input과
`logits` output을 유지한 채 위 내부 텐서의 FLOAT graph output 선언만 추가하는 경로를 검토한다.
새 이름을 위한 Identity node보다 기존 텐서명을 그대로 노출하는 것이 변경 범위가 작다.
직렬화하면 bytes/SHA가 달라지므로 원 자산과 파생물 pin 및 변경 명세를 별도로 기록해야 한다.
변형한 파일을 기존 `3dff…` 모델로 보고하거나 `AstModelStore`의 기존 hash 검사에 끼워 넣지 않는다.

1. 동일 환경·provider·threads·graph optimization 설정에서 원 모델과 파생 모델을 별도 bounded 실행한다.
   우선 공개 합성 입력을 사용하고, 허용된 공개 개발 입력으로 확장한다. 사용자 자료는 평가 전용이다.
2. 같은 FLOAT `[1,1024,128]` input을 넣고 원 `logits`와 파생 `logits`의 527개 모두를 대조한다.
   먼저 exact equality를 기록하고 max/mean absolute error, 안정된 sigmoid 차이, 기존 판정 변화를 기록한다.
   같은 INT8 그래프의 output 추가이므로 FP32↔INT8의 과거 허용 오차를 새 parity 허가로 가져오지 않는다.
   불일치 시 optimization 비활성 대조로 원인을 구분하고 사전 계약 없이 허용값을 넓히지 않는다.
3. 표현의 dtype/shape/finite, 동일 입력 반복 동등성, 반환한 표현을 보관하지 않는 종료 경로를 확인한다.
   `logits`와 표현을 한 encoder run에서 함께 얻어 두 번째 전체 AST 추론을 만들지 않는다.
4. ORT optimizer가 graph output을 보존하면서 fusion·buffer lifetime을 바꿀 수 있다.
   output 선언만 추가했다는 사실은 수치·메모리 동등성의 실행 증거가 아니다.

`ondevice_ast_portability.py`는 원 FP32 export와 frontend parity,
`ondevice_ast_quantize.py`는 constant MatMul/Gemm dynamic QInt8 per-channel 변환과
FP32/INT8 비교를 다룬다. 둘 모두 지금 실행할 인터페이스 감사 도구는 아니다.
기존 `ondevice_ast_features.py`의 16kHz mono float /32768, 1–10초, 400sample frame,
160sample hop, 128mel, 1024frame와 **log-mel zero padding 후 normalize** 계약을 유지해야 한다.
gain/DC80Hz cleanup이나 짧은 PCM padding을 임의로 추가하면 별도 입력 실험이다.

## 4. 메모리·Android·시간축 조건

작은 head가 추가되어도 AST 91.13MB와 전체 encoder 연산은 남는다.
기존 `int8-03/int8-memory.json`의 host ORT1.24.4/4thread 측정은
after-inference RSS552,546,304B, peak working set563,396,608B다.
이는 **원 logits-only 모델의 과거 측정**이며 새 output 경로의 측정이 아니다.
과거 S25 합성 standalone10/20초의 PSS표본 약502/506MiB도 새 표현 경로의 검증으로 소급하지 않는다.

현재 Android `AstAudioAnalyzer.validateGraph()`는 outputNames가 정확히 `{logits}`인 모델만 허용한다.
`AstModelStore`도 현재 bytes/SHA를 고정한다. 파생 output 모델은 두 계약의 연구용 대응이 필요하다.
현재 runtime의 mutex, 취소 guard, 120초 deadline, 메모리 검사, session/result/input 닫기,
임시 버퍼 지우기와 기존 점수·판정 유지도 검사 대상이다. 새 경로의 ARM10/20초 시간·PSS·취소/복구·
전체 AV 예산·패키지/업데이트 검증이 완료되기 전 기존 Android 성공 이력을 그대로 적용하지 않는다.

현재 AST는 1–10초 입력을 분류하고, 20초는 0–10/5–15/10–20초로 처리한다.
pooled 768차원 표현은 그 AST 구간의 표현이며 YAMNet975ms 창의 순간 발성 위치를 입증하지 않는다.
희소 발성/MIL에 쓰려면 짧은 AST 입력과 실제 유효 길이·padding·source 분할을 먼저 계약해야 한다.
다른 차원 head도 예컨대 768→1+bias의 순수 FP32 parameter가 3,076B라는 산술값과
실제 serialized 크기·실행 비용·정확도를 구분한다.

## 5. 권리 정정 — 실제 AST weight는 BSD-3-Clause

**Apache weight라는 전제는 로컬 원문과 맞지 않는다.** 기존 원 model card
`work/ondevice-av/ast-model/README.md`의 YAML은 `license: bsd-3-clause`다.
앱 `AST_MODEL_NOTICE.txt`도 revision·파생 처리·그래프 SHA와 BSD3 원문을 포함한다.
`docs/research/20261003-ondevice-av-probe.md`의 기존 weight 계약 역시 BSD-3-Clause다.

- [고정 model card 원천](https://huggingface.co/MIT/ast-finetuned-audioset-10-10-0.4593/tree/f826b80d28226b62986cc218e5cec390b1096902).
  로컬 README1165B의 SHA는 `dbc8ce1fc5abd1635d073640d8cb032901bfe264a5e06507f363f10a6112cfc8`다.
- 기존 앱 고지2126B의 SHA는 `375ff3c8e91c859db0fd0ea38d780dd14b65a359c001a150c0146a0dcf2ec340`다.
  source/binary 재배포 시 저작권·조건·disclaimer 보존 및 endorsement 금지 조건이 있다.
- Transformers 구현이나 다른 의존성의 Apache 허락을 AST checkpoint 허락으로 치환하지 않는다.
  작은 head의 공개 학습 자료 권리·attribution과 사용자 평가 전용 경계는 별도로 유지한다.

이번에는 기존 로컬 원문만 읽었으며 외부 페이지 재조회·권리자 연락은 없다.
현재 표현의 영아 구별·성인 TV/겹침·욕구 정확도는 미검증이다.
**정적 구조는 후속 작은 head 비교를 가능하게 하지만 제품 채택이나 품질 개선을 입증하지 않는다.**
