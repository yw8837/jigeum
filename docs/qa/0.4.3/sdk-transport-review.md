# SDK DataTransport 전송 경로 검토

검토일: 2026-09-30 · 대상: `outputs/MoaAndroid` · 방법: 캐시된 AAR의 `classes.jar`를 JDK 21 `javap -c -p`로 읽고, 앱 소스·병합 매니페스트·기존 DEX·보존 실행 로그·추출 DB 요약과 대조했다. 이 검토에서 빌드, ADB, 새 네트워크 시험, 실제 Play 거래를 실행하지 않았다. 앱/SDK 코드와 권한도 변경하지 않았다.

## 결론과 적용 범위

1. MediaPipe Tasks 1.0.0은 내부에서 사용 횟수·시간·성능 진단 이벤트를 만들고 Google DataTransport의 CCT 백엔드로 전달한다. 로거가 없거나 공식 설정으로 꺼졌다고 표현할 수 없다.
2. 검토한 MediaPipe Java 로깅 경로의 실제 설정 필드는 앱 식별/버전, task 종류/모드, 호출 수, 지연시간, 경과시간, 오류 enum이다. 이 경로에서 원본 PCM·이미지·영상, 얼굴 좌표, 분류 결과, 문장, 메모, 앱 `Observation` 기록을 payload로 받거나 설정하는 코드는 발견하지 않았다. 이 결과만으로 원본 업로드 결함이 있다고 판정할 근거는 없다.
3. Billing 9.1.0에도 별도 `PLAY_BILLING_LIBRARY` 진단 transport가 있다. MediaPipe의 진단과 구매 SDK의 진단을 구분해야 한다.
4. 앱 매니페스트는 의도적으로 `ACCESS_NETWORK_STATE`를 제거한다. 검토한 CCT 3.1.0/3.1.8은 `decorate()` 첫 단계에서 이 권한을 요구한다. 보존 로그의 실패는 **이벤트 저장 전에 발생**한다. 저장했다가 transaction rollback 된다는 설명은 잘못이다.
5. 관찰한 DB는 비어 있다. DB의 존재는 SDK 런타임 초기화와 일치하지만, 빈 DB만으로 과거 전송이 한 번도 없었다고 증명할 수는 없다. 실패 로그와 코드의 호출 순서가 추가 근거다.
6. 이 권한 제거는 검토 버전에서 작동하는 전송 차단 조건이며, SDK가 제공하는 공개 opt-out API가 아니다. `INTERNET`은 남아 있다. 모든 SDK 네트워크·네이티브 코드·Google Play 서비스 통신이 없다는 인증으로 확대하면 안 된다. 향후 SDK가 권한 조회를 생략하면 이 조건의 효과도 바뀔 수 있다.

## 버전 근거와 실행 시점 구분

현재 앱 의존성 선언(`app/build.gradle.kts:88–92`):

| 구성 요소 | 선언/현재 빌드 확인 버전 |
|---|---|
| `com.google.mediapipe:tasks-vision` | 1.0.0 |
| `com.google.mediapipe:tasks-audio` | 1.0.0 |
| `com.google.mediapipe:tasks-core` | 1.0.0 |
| `com.android.billingclient:billing` | 9.1.0 |
| `com.google.android.datatransport:transport-api` | 3.0.0 |
| `com.google.android.datatransport:transport-runtime` | 3.1.8 |
| `com.google.android.datatransport:transport-backend-cct` | 3.1.8 |

현재 runtime/backend 3.1.8은 POM 선택 추정에만 의존하지 않는다. 다음 기존 빌드 파일을 읽었다.

- `app/build/intermediates/incremental/lintAnalyzeRelease/release-artifact-dependencies.xml:527–534`: backend/runtime 3.1.8, API 3.0.0. 파일 수정 시각 22:20:29 KST.
- release `app/build/intermediates/external_libs_dex/release/mergeExtDexRelease/classes3.dex`: `Lcom/google/android/datatransport/runtime/BuildConfig;`의 static `VERSION_NAME` 값 `3.1.8`. 파일 수정 시각 20:54:53 KST.
- debug `app/build/intermediates/dex/debug/mergeExtDexDebug/classes3.dex`: 동일 static 값 `3.1.8`. 파일 수정 시각 20:54:05 KST.
- 현재 `app/build/outputs/apk/debug/app-debug.apk`를 읽어 SHA256이 아래 package gate의 값과 같음을 확인했다. APK 안 `classes5.dex`의 위 BuildConfig static `VERSION_NAME`도 `3.1.8`이며, 그 DEX의 SHA256은 debug external `classes3.dex`와 같다.
- Billing 9.1.0 POM은 runtime/backend 3.1.8을 요청하고, MediaPipe core 1.0.0 POM은 3.1.0을 요청한다.

**시점 주의:** `app/build/outputs/sdk-dependencies/release/sdkDependencies.txt`는 12:15:02 KST 산출물이며 runtime/backend 3.1.0을 적고 있다. 이를 최신 DEX의 버전 근거로 쓰면 안 된다. logcat의 `13:32`는 기기 출력 시각이며, 시간대 확인 없이 Windows 파일 수정 시각 KST와 비교해 과거 실행이라고 판정하지 않는다.

통합 실행 담당 root는 0.4.3 첫 package gate의 debug APK를 `upgrade.py verify`에서 `install -r`한 뒤 앱 APK 변경/재설치 없이 test APK만 재설치했고, 최종 package gate도 같은 SHA256임을 보고했다. 보존된 `evidence/package-verdict.json:25`와 현재 APK의 직접 hash 계산은 모두 **`137bc3154f9ebf947a5da078e6450bbd56f512f8b6be22e73537889a89d8ff35`**다. 따라서 담당자의 설치 연속성 보고와 APK 직접 검사에 근거해 cycle2 실행의 앱 APK와 runtime 3.1.8을 연결한다. 설치 이력을 이 검토자가 새 ADB 실행으로 재검증한 것은 아니다. 과거 3.1.0도 비교 검토했으며 관련 `DefaultScheduler` class는 3.1.8과 SHA256까지 같고 양쪽 CCT `decorate()`는 같은 첫 권한 조회 순서를 가진다.

## MediaPipe: 앱 진입점과 로거 생성

앱 소스의 진입점:

- `SoundAnalyzer.kt:110–114`: 로컬 모델 asset으로 `AudioClassifier.createFromOptions()`를 사용한다. YAMNet은 독립적인 direct TensorFlow Lite 호출이 아니라 MediaPipe AudioClassifier를 통해 실행된다.
- `CameraScan.kt:330–336`: `FaceLandmarker.createFromOptions()`.
- `VideoImport.kt:314–315`: `FaceLandmarker.createFromOptions()`.

검토한 바이너리 호출 경로:

```text
AudioClassifier / FaceLandmarker createFromOptions
  -> TaskRunner.create(context, taskInfo, ...)
  -> TasksStatsLoggerFactory.create(context, taskName, runningMode)
  -> TasksStatsProtoLogger.create(...)  [조건 없이 호출]
  -> private TasksStatsProtoLogger constructor
  -> new RemoteLoggingClient(context)
  -> TransportRuntime.initialize(applicationContext)
  -> TransportRuntime.newFactory(CCTDestination.INSTANCE)
  -> getTransport("COREML_ON_DEVICE_SOLUTIONS", MediaPipeLogExtension, "proto", ...)
  -> Event.ofData(payload)
  -> Transport.send(event)
```

`TasksStatsLoggerFactory.create()` bytecode offset 3은 바로 `TasksStatsProtoLogger.create()`를 호출한다. `RemoteLoggingClient` 생성자 offset 23의 transport 이름은 **`COREML_ON_DEVICE_SOLUTIONS`**, payload class는 **`com.google.mediapipe.proto.MediaPipeLoggingProto$MediaPipeLogExtension`**이다. `logEvent()` offset 5는 `Event.ofData()`, offset 8은 `Transport.send()`다.

TaskRunner 생성자는 session start를 기록하고, 입력 process/send 경로는 CPU 입력 도착 timestamp를 기록한다. 출력 callback에서 로거에 전달되는 값은 output packet의 timestamp다. 미디어 입력이나 분석 결과 object가 로거로 전달되는 경로는 아니다. close/restart는 session end/start를 기록한다. 통계 보고 간격은 30,000ms, 지연 기준은 3,000ms이다. session end에도 집계 report를 만들므로 30초보다 짧은 사용이 진단 이벤트를 만들지 않는다는 주장은 틀리다.

### MediaPipe payload: 스키마와 실제 설정 필드

| protobuf 영역 | 검토한 로거가 실제로 채우는 값 |
|---|---|
| `MediaPipeLogExtension` | `SystemInfo`, `SolutionEvent` |
| `SystemInfo` | platform=Android, app ID=application package, app version=version code 문자열 |
| `SolutionEvent` | task/solution enum, event enum, event detail oneof |
| task/solution | 앱 진입점에 대응하는 `TASKS_AUDIO_CLASSIFIER`, `TASKS_FACE_LANDMARKER` |
| session start/clone | running mode, 초기화 지연시간 ms |
| invocation report | mode, dropped 수, peak/average pipeline latency ms, elapsed time ms, CPU/GPU 입력 종류와 호출 수 |
| session end | invocation aggregate report; finished 수를 집계 계산에 사용 |
| initialization error | `ERROR_INIT` enum |

event enum은 session start, session clone, invocation report, session end, initialization error를 구분한다. Java protobuf 스키마에는 MediaPipe version, host version/environment, graph name 등 추가 필드도 있지만 **검토한 `TasksStatsProtoLogger`는 이 필드들을 설정하지 않는다**. 스키마에 필드가 존재하는 것과 현재 로거가 전송하려고 채운 필드는 구분했다.

이 payload 경로에는 raw PCM, 이미지/영상 bytes, 얼굴 landmark 좌표, 분류 label/score, 생성 문장, 보호자 메모, 앱 기록 object를 설정하는 필드/호출이 없다. 이 결론의 범위는 검토한 MediaPipe Java Tasks 로깅 경로이다. SDK 전체 네이티브 구현과 모든 가능한 통신을 패킷 캡처로 검증한 결과는 아니다.

### 공개 opt-out 또는 no-op 주입 여부

Tasks 1.0.0의 `BaseOptions`/Builder, TaskOptions, TaskRunner, logger factory를 확인했다.

- `TasksStatsDummyLogger` 클래스는 존재하고 메서드들이 no-op이다. 하지만 core JAR class 참조 검사에서 이 클래스는 자기 자신 외에 참조되지 않았다. factory는 항상 ProtoLogger를 만든다.
- TaskRunner의 statsLogger field는 final이고 logger를 받는 생성자는 private이다. 공개 `create()` overload에는 logger 인자가 없다.
- ProtoLogger 생성자는 private이며 항상 RemoteLoggingClient를 만든다.
- BaseOptions builder는 모델 asset/path/buffer와 delegate 옵션을 제공한다. 공개 logging disable/replace 설정은 발견하지 않았다.

따라서 **cached Tasks 1.0.0 공개 API에서 지원되는 telemetry off/no-op hook을 확인하지 못했다**. DummyLogger의 존재만으로 앱이 지원되는 경로로 로깅을 껐다고 표현하지 않는다. reflection, SDK 재작성, 내부 클래스 대체는 적용하지 않았다.

## Billing 9.1.0: 별도 진단 transport

```text
BillingClientImpl constructor/initialize
  -> metadata protobuf zzkg builder
  -> new com.android.billingclient.api.zzdr(context, zzkg)
  -> new com.android.billingclient.api.zzdt(context)
  -> TransportRuntime.initialize(context)
  -> newFactory(CCTDestination.INSTANCE)
  -> getTransport("PLAY_BILLING_LIBRARY", zzkw, "proto", zzds)
  -> Event.ofData(zzkw)
  -> Transport.send(event)
```

`zzdt` constructor offset 18의 이름은 **`PLAY_BILLING_LIBRARY`**, payload class는 **`com.google.android.gms.internal.play_billing.zzkw`**이다. transformer `zzds`는 protobuf의 `.zzQ()` byte array를 반환한다. `zzdt.zza()` offset 20/23에서 `Event.ofData()`/`Transport.send()`를 호출한다. `BillingLogger`라는 진단 tag로 초기화/로깅 오류를 처리하는 코드도 있다.

확인한 metadata 입력:

- `BillingClientImpl` 초기화에서 library version `9.1.0`, application package, app version code, Android SDK_INT를 `zzkg` builder에 설정한다.
- `zzbA()`는 total memory(MiB), Build.BRAND, Build.MODEL, Build.MANUFACTURER, Build.FINGERPRINT를 설정한다.
- 별도의 string/long/boolean/internal build 값도 설정한다. 모든 obfuscated field의 의미를 완전히 복원하지는 않았다.
- `zzdr`는 `zzkg` metadata와 event variant를 `zzkw`로 묶으며 integer/long/boolean 값도 갱신한다. `zzkw` 스키마에는 event oneof와 `zzkg`, `zzkj` nested fields가 있다.

이 검토는 Billing telemetry의 전체 semantic schema나 구매 token 포함 여부를 확정하지 않았다. MediaPipe의 제한된 진단 필드 결론을 Billing 전체 payload에도 그대로 적용하면 안 된다. 현재 앱의 Billing 연동은 구매 API 경로이며 아기 미디어/기록을 로거에 직접 넘기는 코드는 발견하지 않았다.

`BillingClient.Builder` 공개 메서드는 listener, pending purchases, 재연결 및 billing program 관련 설정이다. telemetry disable/no-op logger setter는 보이지 않는다. 내부 logger field/생성자 seam이 있다는 이유로 이를 지원되는 공개 opt-out이라 판단하지 않았다. SDK 내부 hook을 변경하지 않았다. 이 CCT 진단 경로와 Play 서비스 IPC/실제 구매 통신은 별개다.

## DataTransport: 실패가 저장보다 앞서는 정확한 순서

소스 `app/src/main/AndroidManifest.xml:8`:

```xml
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" tools:node="remove" />
```

debug/release 병합 매니페스트에서 이 권한은 없다. `INTERNET`과 CCT backend discovery metadata, `JobInfoSchedulerService`, `AlarmManagerSchedulerBroadcastReceiver`는 남아 있다.

runtime 3.1.0/3.1.8 `DefaultScheduler.lambda$schedule$1()`:

1. offset 8: backend registry 조회.
2. **offset 65: `TransportBackend.decorate(event)`**.
3. offset 85: decorate 성공 후에만 `SynchronizationGuard.runCriticalSection(...)`.
4. critical section인 `lambda$schedule$0()` offset 6: `EventStore.persist(...)`.
5. offset 18: `WorkScheduler.schedule(...)`.
6. exception handler offset 113: `Error scheduling event` 메시지; callback에 exception 전달.

CCT 3.1.0/3.1.8 `CctTransportBackend.decorate()`:

```text
offset 0: aload_0
offset 1: connectivityManager
offset 4: ConnectivityManager.getActiveNetworkInfo()
offset 8 onward: event.toBuilder(), metadata population ...
```

권한이 없으면 offset 4에서 SecurityException이 난다. 따라서 이 경로의 해당 이벤트는 **metadata 추가, EventStore.persist, job scheduling, HTTP upload보다 앞에서 실패**한다. DB 초기화는 TransportRuntime 초기화와 별개로 가능하다.

권한 조회를 통과할 경우 decorator는 Android SDK, model, hardware, device, product, OS build ID, manufacturer, fingerprint, timezone offset, network type/subtype, locale/country, SIM operator MCC/MNC, application build를 붙인다. CCT에는 이를 gzip JSON HTTP POST로 보내는 구현이 있다. 즉 현재 코드에 원격 진단 전송 구현이 없는 것은 아니다. bundled endpoint/key 상수는 이 문서에 복사하지 않았다.

## 실행 관찰: logcat와 DB

### 기존 logcat

파일: `work/pr2-integration-20260930/evidence/instrumentation-cycle2-logcat.txt`.

line 8496:

```text
09-30 13:32:05.904  5938  6291 W TransportRuntime: Error scheduling event ConnectivityService: Neither user 10216 nor current process has android.permission.ACCESS_NETWORK_STATE.
```

동일 권한 실패는 lines 8535, 8837, 8994, 9383, 9386, 9691, 9693, 10964, 10968, 10972, 12163, 12167에도 있다. 첫 실패 13:32:05.904, 마지막 실패 13:32:36.506. 실제 SDK 이벤트 scheduling 시도와 권한에 의한 실패를 보여준다. 이 메시지 자체에는 transport 이름/payload가 없으므로 각 줄을 MediaPipe/Billing 어느 하나의 특정 이벤트로 식별하지는 않는다.

### 기존 추출 DB

root가 추출한 `synthetic-sdk-transport.db`의 요약 `sdk-transport-summary.json`:

- SDK DB 이름: `com.google.android.datatransport.events` (journal도 관찰).
- tables: android_metadata, events, event_metadata, transport_contexts, event_payloads, log_event_dropped, global_log_event_state.
- `pending_events: 0`, `transports: []`, `backends: []`.

이는 **추출 시점에 대기 이벤트가 없다는 관찰**이다. 빈 DB만으로 전송 이력 부재를 결론내리지 않는다. 위 코드 순서와 실제 권한 실패 로그를 함께 보면, 로그에 나타난 scheduling 실패 이벤트는 persistence 이전에 중단됐다고 해석할 수 있다. 실제 Play 구매/ARM 실기기/패킷 캡처 결과로 소급하지 않는다.

## 개인정보 문구 해석과 후속 검증 기준

`MainActivity.kt:1287`은 “아기 소리·영상·기록은 서버로 자동 전송하지 않으며 광고·행동 분석 SDK를 사용하지 않습니다.”라고 안내한다. 이번 검토는 아기 콘텐츠가 MediaPipe 진단 payload에 들어간다는 결함을 확인하지 않았다. 다만 SDK 내부 진단 이벤트 생성은 확인했다. 앱 문서에서 “SDK 로깅 없음”, “공식 opt-out 완료”, “모든 외부 SDK 통신 없음”이라고 넓혀 표현하지 않는다.

`AndroidManifest.xml:6`의 “Only purchase verification uses network” 주석도 SDK와 Play 서비스 전체 통신에 대한 검증 완료로 쓰지 않는다. 확인 가능한 설명은 다음과 같다: **아기 원본과 기록은 앱의 로컬 처리 경로에 남고, 검토한 MediaPipe 진단 payload에는 해당 콘텐츠가 없으며, 현재 매니페스트는 검토한 CCT 전송의 네트워크 상태 조회를 권한 부재로 실패시킨다.**

SDK/권한을 변경할 때는 정확한 resolved 버전·병합 매니페스트와 decorator/persist 순서, 실행 로그를 다시 확인해야 한다. 기존 권한 제거를 해제하거나 내부 no-op hook을 넣는 결정을 이 문서로 승인한 것은 아니다.

## 재검토용 artifact 및 SHA256

캐시 루트: `C:/Users/최용우/.gradle/caches/8.14.3/transforms/`. 아래 transform 경로의 `transformed/<artifact>/jars/classes.jar`를 읽었다.

| artifact | transform directory | classes.jar SHA256 |
|---|---|---|
| tasks-core-1.0.0 | ce9a8a9e44fcac1ef15fc854db63eb97 | de1322e89f201b8cd4f0be52c577fd70fe4edb64d16e0d9e8c02b340f2805602 |
| tasks-vision-1.0.0 | a33b1e166258c26fbb3af50e1590ed61 | e7edf3608c461a843fa987ed438ad1bf7f87ba7b9d2fea9a78a0c9e13f10302e |
| tasks-audio-1.0.0 | 89ef3e81eb989a6e2bb575d619859578 | b9526837dde7da496ea892b30386600dc96b8fac8851d170d02f35f8489d9977 |
| billing-9.1.0 | 1926ef43c75397368eb6f1400c620338 | 3be24f22afdafe9abb2b0eb864b02b64c16e51619d756d3727a14c557c75c10d |
| transport-backend-cct-3.1.8 | 91cab33f7cebd26cffb6abbb3512dae5 | 881428792997249f3c57c9f8c295595b886e72a984809a9c4988bbd329ea3ec4 |
| transport-runtime-3.1.8 | 73e676baa5fcb8d621de99fba43c537e | 2d5bc39106ad24ceeadf4a41451b6113fcc5449ed6336a2fffc832b6411b5069 |
| transport-backend-cct-3.1.0 | da4dbf5d4cc427829056b2855989420c | a90a6b1e86e69e939d616fccc933466bf5d7b215d261e08451f0651cb3781253 |
| transport-runtime-3.1.0 | 99e5b16dfeb9ed32cdd2bda76259d9d1 | 70892e72cdb19765624f490d88de4c903053648ef437f341e275684c092d9471 |

| class | SHA256 of uncompressed class bytes |
|---|---|
| core TaskRunner.class | 13da70232e9637149fa9e5be1f6bd80da44dda2610c7c58e237785e87a75c203 |
| core TasksStatsLoggerFactory.class | fc88676ca1ca1543d6e0b495bbe9c948161f87f51c369eeb6d7478e094aa8944 |
| core TasksStatsProtoLogger.class | 1debe048871ceeb0cf7cf305c3b1f46e16698529ad1f75c78b0f7b34a018ac00 |
| core RemoteLoggingClient.class | 3562a85ffe209d8123ef788ab90192354e1bf00b619689b0930f6b3c5ec4aa80 |
| Billing zzdt.class | 22eaac83aafc29258ea4fd1e86e04aba72c584a598127bcb628e00b6384d7aa4 |
| CCT 3.1.8 CctTransportBackend.class | 348de03a4caa0fb864d9ca3158ab93859c3cc0974aa431134d4a32cd2a7740f3 |
| CCT 3.1.0 CctTransportBackend.class | 88c832bbf77eddbc59c59ffffba78cffb65ac8d656d8148d4e6b78635bedcff6 |
| runtime 3.1.0 / 3.1.8 DefaultScheduler.class | d491d8b227c3a812f55e3921ff356a9ade8409a5afd9d6eb8f3299bc1613d2c4 |

기존 DEX/version 보고서 확인용 SHA256:

- release external `classes3.dex`: `093a88464128ce45be4ab58ee70bfbc54ef7ae0f4ad59dca055758b636a6f863`.
- debug external `classes3.dex`: `44633298bd1cb139d1197fabff7e4412c0d0dc2e68b973df150f35195dc7fd5d`.
- 현재 debug APK 안 `classes5.dex`: `44633298bd1cb139d1197fabff7e4412c0d0dc2e68b973df150f35195dc7fd5d` (위 debug external DEX와 동일).
- 최신 release `release-artifact-dependencies.xml`: `9beaeb790299b921817693d320e1328427912e4c2fcea4bc44b8f81943d6d1bc`.

이 해시는 검토 입력을 고정하기 위한 값이며 APK 전체 해시/서명 검증을 대신하지 않는다.
