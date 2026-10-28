# AST 연구 빌드 계약

2026-10-04. AST 후보의 품질 승격은 보류한다. `BabbleSupportPolicy.ENABLED=false`와 연구용 런타임 포함 여부는 별개다.

기본 빌드는 AST 모델·고지·ONNX Runtime 의존성을 포함하지 않는다. 모델이 없는 깨끗한 checkout에서도 debug/beta/release 빌드를 구성할 수 있다. 공통 `AstAudioWindow`/`AstAudioAnalysis` 타입은 main에 두고, 작은 `src/noAst/java` 구현이 명시적 unavailable 결과를 반환한다. 후보 정책의 기존 테스트는 기본 계측에 유지한다.

`-PagattokAstProbe=true`를 명시한 debug 연구 빌드만 실제 AST 런타임·모델·고지·AST 계측과 합성 golden을 포함한다. 이 속성으로 release/beta/preview 변형의 작업을 실행하면 중단한다. 연구 빌드를 Play 배포에 사용하지 않는다. 속성은 `true`/`false`만 받으며 생략 시 false다.

| 경로 | 포함 조건 |
|---|---|
| `src/main/java/.../AstAudioTypes.kt` | 모든 빌드 |
| `src/noAst/java` | 기본 빌드 |
| `src/astProbe/java` | 연구 속성 true |
| `src/astProbe/assets` | 연구 속성 true |
| `src/astProbeAndroidTest/java`, `assets` | 연구 속성 true의 계측 APK |

실제 runtime 또는 unavailable 구현 중 한 디렉터리만 main source set에 추가한다. AST 연구 계측 4개 클래스는 기본 `androidTest` 디렉터리에서 옮겼으며, 기본 계측 APK에는 들어가지 않는다.

저장소 루트에서 기존 검증 모델을 준비한다. 이 helper는 다운로드나 추론을 하지 않는다.

```powershell
python tools/research/setup_android_ast.py --model work/ast-portability/int8-03/ast-dynamic-int8.onnx
```

`outputs/MoaAndroid`에서 연구 debug APK와 계측 APK를 만든다.

```powershell
.\gradlew.bat -PagattokAstProbe=true :app:assembleDebug :app:assembleDebugAndroidTest
```

연구 assets 병합은 `verifyAstModel`을 반드시 실행한다. 원본 크기 91,134,123 bytes와 SHA256 `3dff0cc2b374977ae1460258261608e33428f4767a6959cb8bc910b09433f71e`가 일치해야 한다. 명시적으로 `:app:verifyAstModel`을 호출하면 속성과 관계없이 연구 경로의 모델을 검증하지만 기본 assembly/bundle은 이 작업에 의존하지 않는다. 모델과 준비 중 부분 파일은 Git에서 제외한다.

ORT Android는 공식 `com.microsoft.onnxruntime:onnxruntime-android:1.24.3`이다. Python 기준 1.24.4와 별도 버전이므로 연구 계측의 synthetic logits parity를 계속 검사한다.

로컬 인식 평가기는 이 연구 계측 APK에서 `-e ast_perception_probe true`를 추가로 명시해야 실행된다. 빌드 속성만으로 평가를 자동 실행하거나 제품 판정 gate를 켜지 않는다. 원본과 결과는 앱 내부 `filesDir/qa-ast-perception`에 머물며, 실행 완료는 품질 통과가 아니다.

검증 담당자는 기본 APK/AAB의 파일 목록과 의존성에서 AST 모델·고지·ORT가 없는지 확인하고, 연구 debug APK에는 모델/고지가 있는지 확인한다. 연구 속성 true의 beta/release/preview 작업 거부, 연구 모델 누락/손상 시 assets 병합 실패, 기본 빌드에서 모델 미요구도 확인한다. 이 계약 수정자의 가벼운 Kotlin 컴파일과 실제 Gradle/ADB 검증은 별도 증거로 기록한다.

위 패키지 구성·Gradle6게이트·기본116개 및 연구27개 계측을 2026-10-04 실행해 모두 통과했다. [실행 검증](ANDROID-AST-QA.md). 연구용 APK는 Play에 제출하지 않았고 후보 정책도 활성화하지 않았다.
