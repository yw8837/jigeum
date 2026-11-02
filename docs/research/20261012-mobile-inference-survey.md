# Android 온디바이스 추론 엔진·시각 관찰 조사 — 2026-10-12

대상: 아가톡, 6~18개월 아기의 부모, Android/S25 Ultra 우선. 원본·파생 아기 데이터의 외부 업로드 없이 10/20초 분석과 200ms 시각 관찰을 개선하는 연구 메모다. 공식 지원을 확인한 것과 앱 성능을 검증한 것은 구분한다. 이 조사에서는 모델 다운로드·SDK 추가·빌드·ADB·Play 변경·클라우드 추론을 실행하지 않았다.

## 판단과 우선순위

1. **현재 모델·전처리를 고정하고 ORT CPU/XNNPACK 및 단계별 메모리·시간을 비교한다(추천).** 비교 비용이 가장 작고 엔진 변경과 의미 정확도 변경을 구분할 수 있다. AST가 사용한 attention/MatMul의 실제 차원·연산자에 따라 XNNPACK 위임이 제한될 수 있으므로 CPU보다 빨라진다고 가정하지 않는다.
2. **MediaPipe VIDEO의 시간 순서·추적 안정성과 시각·소리의 직렬/병렬 실행을 비교한다.** 저조도·가림·여러 얼굴에서 관찰 신뢰도를 평가한다. 엔진을 바꾸기 전에 비디오 디코딩·이미지 복사·세션 중복 생성·스레드 경쟁의 비용을 분리한다.
3. **QNN은 공개 Android AAR의 버전 조합·S25 SoC·연산자 지원을 확인한 뒤 별도 실험 대상으로 둔다.** 최신 개발 문서와 Maven 배포가 불일치한다. 초기 구현은 CPU fallback을 유지하고 실제 위임 비율을 남긴다.
4. **LiteRT CompiledModel은 기존 TFLite 모델 경로의 독립 비교 후보, ExecuTorch는 후순위다.** 둘 다 공식 Android 지원은 있으나 AST 재내보내기·전처리 일치·새 native runtime의 배포 부담을 정당화할 앱 실측은 없다.
5. **Gemma 3n은 장기 연구 후보다.** 모바일 멀티모달 지원과 영아 관찰의 정확성은 별개이며, 현재 소형 사건 분류기와 같은 RAM·지연 예산에 들어간다는 근거가 없다.

## 현재 앱에서 확인한 범위

`outputs/MoaAndroid/app/build.gradle.kts:346-350`의 검색 결과는 MediaPipe `tasks-vision:1.0.0`, `tasks-audio:1.0.0`와 AST 조건부 `onnxruntime-android:1.24.3`을 보여준다. `AstAudioAnalyzer.kt`에서 `OrtSession.SessionOptions`와 `SEQUENTIAL` 설정의 존재도 검색으로 확인했다. SessionOptions의 전체 초기화·실제 EP 목록은 이번 조사에서 재검증하지 않았다. Serena Kotlin LSP 초기화 실패 뒤 소스 읽기를 우회하지 않았다.

최신 `docs/HANDOFF.md`에는 실제 분석·배포 QA가 별도로 기록되어 있다. 기존 연구앱 직접 호출 결과와 서명 앱 UI 결과, ARM 실폰과 x86 에뮬레이터 결과는 서로 대신하지 않는다. 여기 제안한 비교를 실행한 것으로 기존 QA 수치를 재해석하지 않는다.

## 엔진별 근거와 적용 조건

| 경로 | 공식 자료로 확인한 지원 | 아가톡에 필요한 검증·비용 | 우선순위 |
|---|---|---|---|
| ORT CPU → XNNPACK | Android Maven의 XNNPACK EP, C/C++/Java 등록. 별도 XNNPACK 스레드풀. 지원 목록의 MatMul/Gemm은 2D 제한 | AST의 고비용 연산이 위임되는지, unsupported op의 CPU fallback, 모델·전처리 고정 시 출력 일치, ORT/MediaPipe 스레드 경쟁 | 1 |
| ORT NNAPI | 기존 Android EP가 존재하지만 플랫폼 NNAPI는 Android 15부터 deprecated | 기기 driver/연산자 분할에 따라 지연이 악화될 수 있음. 새 장기 투자 경로로 권장하지 않음. 과거 경로 비교가 필요한 경우에만 실험 | 낮음 |
| ORT QNN/QAIRT | Qualcomm HTP/GPU 경로. Android ARM64용 QNN EP AAR가 Maven에 실제 게시됨 | ORT·QNN EP·QAIRT·native 라이브러리·SoC·op/shape 조합 고정, HTP 위임/CPU fallback, 컴파일 첫 실행 비용·context cache·앱 크기·RAM 검증 | 3 |
| LiteRT CompiledModel | Google 공식 Android CPU/GPU/NPU API. 기존 Interpreter는 호환 API | 현재 MediaPipe Tasks 내부 런타임을 새 LiteRT API가 자동 대체하는 것은 아님. 동일 TFLite asset로 별도 harness를 만들어 수치·전처리·동시 실행을 비교 | 4 |
| ExecuTorch XNNPACK/QNN | PyTorch 모델 export/lowering과 Android backend 지원. QNN backend는 Hexagon/Adreno 사용 경로 설명 | 현재 ONNX 자산과 동일한 PyTorch 기준본 확보, `.pte` 변환·버전 고정·전처리 회귀·AAR/native 종속성. 기기 특화 최적화 전환의 이득 입증 필요 | 5 |

ORT XNNPACK 공식 권장 설정은 ORT intra-op spinning을 끄고 ORT intra-op을 1로 두며 XNNPACK 자체 풀을 설정하는 것이다. 공식 문서는 unsupported 고비용 연산이 CPU EP에 남으면 다른 설정이 더 나을 수 있다고 명시한다. 아가톡은 MediaPipe와 디코더도 함께 실행하므로 물리 코어 수를 그대로 채택하기보다 스레드 1/2/4를 비교할 실험안이다. XNNPACK 문서와 SDK 존재는 AST 가속의 증거가 아니다. [S1]

NNAPI의 deprecated는 즉시 모든 폰에서 실행 불가능하다는 의미가 아니다. 다만 Android가 새 production ML 경로를 다른 runtime으로 안내하므로, 신규 최적화 투자의 기본값으로 삼을 이유가 약하다. [S2]

### QNN: 개발 문서와 게시 아티팩트를 분리

2026-10-12에 확인한 공식 `onnxruntime/onnxruntime-qnn` 문서는 QNN EP 2.7.0, QAIRT 2.51.0, ORT 1.29.0 조합과 ORT >=1.24.1 호환 주장을 제시한다. Android Maven 좌표 `com.qualcomm.qti:onnxruntime-android-qnn`도 설명한다. 그러나 Maven Central 메타데이터의 실제 최신 release는 **2.6.0**, 게시 버전은 2.3.0~2.6.0, lastUpdated는 2026-09-11이다. 2.6.0 POM에는 AAR·MIT가 명시되지만 ORT/QNN runtime 의존성 버전은 없다. 따라서 **개발 문서의 2.7.0 검증 조합을 앱 ORT 1.24.3과 2.6.0 AAR의 검증 근거로 사용할 수 없다.** release tag/릴리스 노트·실제 배포 native library·SDK 조건을 확인해야 한다. [S3]

오래된 ORT QNN 페이지는 Android 자체빌드와 Windows prebuilt 중심으로 설명하지만 새로운 공식 저장소에 Android plugin 배포가 존재한다. “QNN은 Android에서 반드시 전체 ORT 자체빌드만 가능하다”는 설명은 현시점 근거와 맞지 않는다. 반대로 Android Maven 존재만으로 설치·동작·S25 성능을 확정할 수도 없다. [S3]

최신 문서는 QAIRT 2.35 이후 부동소수점 지원 SoC의 HTP float 연산이 FP16으로 실행될 수 있음을 설명한다. **QNN NPU는 모든 모델에 INT8 양자화가 필수라고 단정하지 않는다.** 부동소수점/정수 정밀도, 해당 SoC, 연산별 지원을 실제 사용 버전에서 확인해야 한다. 일반 ORT CPU가 처리한 fallback 연산을 HTP 가속으로 집계하지 않는다. [S3]

### LiteRT와 ExecuTorch의 현실적 범위

LiteRT Android 문서의 확인 시점 최신 2.x는 2.2.0, CompiledModel과 CPU 전용 Interpreter를 표기하며 최소 SDK23/NDK r26a를 안내한다. 별도 1.x Interpreter 계열도 남아 있다. 이는 기존 의존성을 무조건 2.x로 교체하라는 근거가 아니다. 동일 TFLite 그래프가 새 NPU compiler의 모든 연산·shape에서 지원되는지, MediaPipe의 입력/출력 계약을 별도 harness가 재현하는지 먼저 비교해야 한다. [S4]

ExecuTorch QNN 공식 문서는 host 환경으로 Ubuntu·Windows·WSL을, target SoC enum과 전용 build/export 경로를 안내한다. 예제에서 검증한 SoC는 SM8550/SM8450로 기재되어 있어 그 예제의 성공을 S25 Ultra 성공으로 바꿔 말하지 않는다. SDK·backend 버전·기기 enum·모델 op 지원은 별도 조건이다. 소형 CNN·Transformer의 측정 없는 상태에서 엔진 교체만으로 RAM/지연이 개선된다고 결론내리지 않는다. [S5]

## MediaPipe 얼굴·시간 관찰의 영아 적용

Face Landmarker는 얼굴 mesh·52 blendshape·변환 행렬을 내보낸다. Android VIDEO/LIVE_STREAM 모드는 추적으로 매 프레임 검출 비용을 줄일 수 있고, VIDEO에는 프레임 timestamp가 필요하다. `numFaces=1`일 때만 smoothing이 적용된다. VIDEO 호출은 현재 thread를 차단하므로 UI thread 밖에서 실행해야 한다. [S6]

Blendshape V2 공식 model card의 주 목적은 스마트폰 전면 카메라의 얼굴 표현·AR avatar다. 얼굴 인식/신원 확인을 제공하지 않으며, 심한 회전·부분 가림·작은 얼굴·조명·움직임·겹친 얼굴에서 정확도와 jitter가 악화될 수 있다. model card는 성별·피부톤 평가를 제시하지만 **6~18개월 연령별 성능이나 아기 욕구/동기 판독의 검증을 제시하지 않는다.** 평가에서 연령 성능 자료를 못 찾았다는 뜻이며 모든 영아를 배제해 학습했다는 뜻은 아니다. [S7]

아가톡에서는 다음을 관찰·품질 신호로 평가할 가치가 있다. 이것은 개발 제안이며 영아 데이터에서 검증된 능력이 아니다.

- 얼굴 존재·가림·유효 검출 지속률, 고개 방향 변화, 입 벌림/닫힘의 시간적 변화와 jitter.
- 같은 구간의 발성 시점과 입 변화의 겹침. 200ms 표본은 5Hz로 입의 짧은 움직임을 놓칠 수 있어, 음성 구간 주변에만 더 촘촘히 관찰하는 안을 비용과 함께 비교한다.
- 검출 실패·여러 얼굴·주인 불명·표본 간 긴 gap을 유지해 관찰을 보류하는 처리. `numFaces=1` 자체가 얼굴 주인 확인은 아니다.

입 움직임과 소리 시점이 겹쳐도 현장 아기가 발성했다는 증명이나 화자 동기 분류가 되지 않는다. TV/보호자 목소리·성인과 아기가 함께 나온 프레임을 평가에 포함하고, “얼굴이 보였다/입 변화가 관찰됐다”를 “배고프다/졸리다/아프다”로 변환하지 않는다. 여러 얼굴을 1개로 잘라 smoothing을 확보하는 방식은 얼굴 주인 혼동을 해결하지 않는다.

## 양자화·RAM·지연을 함께 검증하는 방법

ORT 공식 문서는 양자화가 손실을 만들 수 있고, 기준본과 양자화 그래프의 weight/activation 비교 및 calibration이 필요하다고 설명한다. Transformer의 dynamic quantization, CNN의 static quantization은 일반 출발점이지 해당 Android EP에서 가장 빠르다는 보장이 아니다. dynamic quantization의 런타임 scale 계산 비용과 static calibration의 분포 의존성을 함께 본다. QNN용 QDQ 정수 경로와 ORT CPU용 동적 정수 경로도 동일한 결과라고 가정하지 않는다. [S8]

계산상 FP32→FP16/INT8의 가중치 저장량은 각각 약 1/2·1/4이지만 이는 **가중치 부분**의 계산이다. activation·arena·input/output·디코딩 frame·runtime 라이브러리·compile cache·파일 mapping·중복 세션을 포함한 peak PSS/RSS가 같은 비율로 줄어들지는 않는다. 작은 파일이 더 빠르다는 보장도 없다. 부분 위임과 quantize/dequantize 경계가 새 비용을 만든다.

평가 데이터는 집/아기 단위로 calibration·threshold tuning·test를 분리한다. 작은 두 영상은 회귀 fixture로만 사용하고 일반화 정확도로 보고하지 않는다. 조용함/옹알이/울음/웃음과 보호자 말소리·TV·음악·환경음, 짧은 저음량·소리 겹침을 포함한다. 동일 threshold를 고정한 수치 비교를 먼저 하고, 재보정은 별도 결과로 남긴다. clip 단위뿐 아니라 event별 miss/false positive·시간 위치·보류율을 본다. 어떤 사건이 아닌지 불확실한 표본을 임의의 정답으로 만들지 않는다.

## Qualcomm AI Hub와 원본 처리 조건

AI Hub Workbench는 호스팅한 실제 기기에 compile/profile/inference job을 제출하는 서비스다. 공식 inference 안내는 입력 데이터 업로드·원격 실행·출력 다운로드를 명시한다. 그래서 “on-device 모델을 만든다”는 설명만으로 우리 앱의 아기 원본 온디바이스 정책을 충족한다고 볼 수 없다. 로컬 QNN runtime 실행과 AI Hub 클라우드 작업은 다른 경로다. [S9]

현재 작업은 문서 열람만 했다. 후속 검토에서 공개·권리 확인된 model과 synthetic 입력의 호스팅 profile을 쓰더라도 model 반출 권한·account/요금·SDK 약관 확인이 필요하다. 아기 PCM·프레임·embedding·calibration data를 AI Hub로 전송하는 구현은 제안하지 않는다. AI Hub의 per-layer/steady-state 결과는 디코딩·발성 문장화·UI를 포함한 아가톡 end-to-end 실측으로 취급하지 않는다.

## Gemma 3n·소형 멀티모달 모델의 위치

Gemma 3n 공식 개요는 audio/visual/text 입력, 모바일 지향 설계, PLE caching·MatFormer·모달리티별 조건부 로딩을 설명한다. E2B의 “2B”는 총 parameter 수가 아니라 effective parameter를 뜻하며, 표준 E2B 실행은 5B 이상의 parameter를 로드한다. 1.91B 수준의 effective memory load 설명은 PLE caching과 parameter skipping을 적용한 경우다. **audio·vision을 생략해 얻은 절감 수치를 두 모달리티를 모두 사용하는 아가톡의 총 RAM으로 보고하면 안 된다.** [S10]

참고 가중치 계산만 해도 2B×4bit는 약 1GB이며 scale·비활성 가중치 저장·encoder·activation·KV cache·동시 영상 분석을 포함하지 않는다. 이를 앱 총 RAM·S25 실측값으로 쓰지 않는다. 오디오/영상으로 일반 묘사를 생성할 수 있다는 사실이 영아 발성 종류·발성 주체·욕구를 신뢰할 수 있게 판독한다는 근거도 아니다. 우선 구조화 소리·시각 관찰을 검증하고, 생성 모델을 검토한다면 관찰 밖의 원인·욕구를 덧붙이는 비율, 무입력/성인/TV 영상의 허구 출력, 결정성, 한국어 표현, peak memory·first-token 지연을 별도 평가한다. Gemma open weights는 자체 Terms 적용이며 Apache/MIT 모델로 묶지 않는다. [S10]

## 후속 실험 계획 — 실행 전 고정할 계약

아래 숫자·반복 조건은 연구안이며 확정 제품 SLA나 이미 통과한 QA가 아니다.

| 순서 | 비교 | 고정할 항목 | 수집할 항목 | 진행 조건 |
|---|---|---|---|---|
| 0 | 현재 signed/research 기준본 계측 | APK/model SHA·Android/SoC·전처리·10/20초·시각 200ms·동일 권리 확인 fixture | load/decode/resample/log-mel/YAMNet/AST/face/postprocess/전체 시간, process peak PSS/RSS, thermal 상태 | 동일 경로 재현·모델 출력 baseline 확보 |
| 1 | ORT CPU와 XNNPACK, thread 1/2/4 | 모델/정밀도/threshold 동일. profiler 없는 end-to-end와 profiler 있는 별도 run 구분 | cold 첫 실행과 warm 반복, p50/p95, 위임 op·CPU fallback, 출력 차이, RAM | baseline event 회귀·정확도 보류 계약 유지, 유의미한 이득 확인 |
| 2 | 시각+소리 직렬/병렬; VIDEO/간격 실험 | 동일 decode 시점·프레임 timestamp, timeout·동시 세션 수 제한 | 복사/bitmap allocation, CPU 경쟁·UI 지연·100/50개 관찰 비용, 검출 지속·jitter·입 변화 누락 | 지연/RAM 개선과 얼굴 주인·가림 보류 유지 |
| 3 | 기존 FP 기준본↔dynamic INT8/허용 FP16↔QDQ | calibration/test 분리, 정밀도 외 입력 계약 고정 | baby event miss/FP, class별 score drift, 경계 flip·보류율, PSS/RSS·지연 | 허용 회귀 폭을 데이터 규모와 함께 정하고 통과 |
| 4 | QNN와 CPU fallback | 게시 release 조합·SoC·supported ops·shape·native ABI·16KB 적합성 | first compile와 cache warm 분리, HTP coverage, fallback 경계 비용, crash·RAM·thermal | 게시 SDK 조합과 권리 확인 후 독립 harness만 진행 |
| 5 | LiteRT/ExecuTorch | 원 framework FP 기준본·전처리·labels 동일 | 변환 출력 차이·runtime/app 크기·전체 pipeline 비용 | 단계1~4가 요구 성능을 못 충족하거나 별도 신규 모델에서 분명한 이득이 있을 때 |

cold는 새 프로세스/미생성 session·cache 조건을 정확히 남긴다. warm은 예열 3회 뒤 10회부터 시작해 분산이 큰 경우에만 반복을 늘린다. 소수 반복의 p95는 안정적 추정으로 주장하지 않고 원시값을 함께 남긴다. 20초 반복 열 누적은 별도 연속 run으로 측정하고 CPU/gpu/npu profile은 실제 app run과 분리한다. S25에서의 통과를 저사양·다른 SoC·모든 Android 기기로 일반화하지 않는다.

## 라이선스·운영 체크포인트

- ORT MIT, LiteRT/MediaPipe Apache-2.0는 공식 repository license에서 확인했다. ExecuTorch license 본문은 BSD 3-clause 형태이며 GitHub 자동 SPDX 응답은 `NOASSERTION`이었다. 자동 결과를 그대로 라이선스 부재로 해석하지 않았다.
- QNN EP 2.6.0 POM의 MIT 표시는 Qualcomm QAIRT/QNN runtime·firmware·SDK·재배포 조건 전체에 적용되는 허가가 아니다. 배포할 native artifact별 약관과 NOTICE를 확인해야 한다.
- runtime license와 모델 weight/dataset license는 별도다. AST/YAMNet/Face asset은 실제 탑재 hash→원 배포판→license/NOTICE 매핑이 있어야 한다. S7의 Blendshape card는 Apache-2.0를 표기하지만 임의의 다른 face bundle에도 같은 권리를 전이하지 않는다.
- 로컬 추론에는 클라우드 호출당 비용이 없지만 AI Hub 호스팅 계정/사용조건과 optional vendor SDK 조건은 별도다. 사용자 기록·원본 업로드·새 외부 아기 DB·로그인·Elice 호출을 추가하지 않는다.

## 1차 출처와 확인 기록

모든 링크는 **2026-10-12 열람**. SDK 3종은 Context7 resolve/query로 먼저 확인한 뒤 공식 페이지를 Agent Reach Jina/`gh api`로 읽었다. Exa는 무료 한도 응답으로 본문을 얻지 못해 purpose-built Search Service로 공식 URL을 찾았다. 검색 snippet만으로 성능 결론을 내리지 않았다. Agent Reach check-update는 v1.5.0 최신 응답이었다.

- **S1 — ORT XNNPACK 공식 문서:** https://onnxruntime.ai/docs/execution-providers/Xnnpack-ExecutionProvider.html — Android package·명시 등록·threadpool·op 제한.
- **S2 — Android NNAPI Migration:** https://developer.android.com/ndk/guides/neuralnetworks/migration-guide — Android15 deprecated. runtime 지연 수치는 없음.
- **S3 — 공식 QNN EP 문서와 배포 registry:** https://github.com/onnxruntime/onnxruntime-qnn/blob/fdeb857c26c26f79f29f1c126e52a8bd2bb3e490/docs/execution_providers/QNN-ExecutionProvider.md — 해당 파일 최근 commit 2026-10-07; 현재 개발 문서. https://repo.maven.apache.org/maven2/com/qualcomm/qti/onnxruntime-android-qnn/maven-metadata.xml 및 https://repo.maven.apache.org/maven2/com/qualcomm/qti/onnxruntime-android-qnn/2.6.0/onnxruntime-android-qnn-2.6.0.pom — 확인한 게시 release·AAR/license. old EP 문서: https://onnxruntime.ai/docs/execution-providers/QNN-ExecutionProvider.html . 문서와 게시 버전 불일치를 본문에 유지.
- **S4 — LiteRT Android 공식 지원표/API:** https://developers.google.com/edge/litert/android — CompiledModel·Interpreter·버전/SDK 조건.
- **S5 — ExecuTorch Qualcomm backend 공식 가이드:** https://docs.pytorch.org/executorch/stable/backends-qualcomm.html — host/SoC enum·export/quantizer·backend. Android/XNNPACK 전체 경로의 공식 저장소: https://github.com/pytorch/executorch . 예제 기기 성공은 S25 성공의 증거가 아님.
- **S6 — MediaPipe Face Landmarker Android:** https://developers.google.com/edge/mediapipe/solutions/vision/face_landmarker/android — tracking·timestamp·single-face smoothing·thread 계약.
- **S7 — MediaPipe Blendshape V2 Model Card:** https://storage.googleapis.com/mediapipe-assets/Model%20Card%20Blendshape%20V2.pdf — 2022-11-11 모델, 2023-05-12 공개 파일, intended/out-of-scope·얼굴/환경 제한·평가 범위·Apache2.0. 현재 앱 asset 동일성 검증 자료는 아님.
- **S8 — ORT Quantization 공식 가이드:** https://onnxruntime.ai/docs/performance/model-optimizations/quantization.html — 손실·calibration·동적/정적·activation 비교.
- **S9 — Qualcomm AI Hub Inference/Profiling:** https://workbench.aihub.qualcomm.com/docs/hub/inference_examples.html — 입력 업로드·호스팅 실제 기기·profile가 의미하는 범위.
- **S10 — Gemma3n 공식 개요:** https://ai.google.dev/gemma/docs/gemma-3n — 모달리티·effective parameter·PLE/조건부 로딩·모델 terms 연결.

라이선스 원문 별도 확인: https://github.com/microsoft/onnxruntime/blob/main/LICENSE ; https://github.com/google-ai-edge/LiteRT/blob/main/LICENSE ; https://github.com/google-ai-edge/mediapipe/blob/master/LICENSE ; https://github.com/pytorch/executorch/blob/main/LICENSE . 움직이는 branch의 약관은 실제 배포 버전으로 재고정해야 한다.
