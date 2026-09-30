# jigeum 최신 협업 체크포인트 검토

검토일: 2026-09-30 · 목표: **이번 주 설치해서 사용할 수 있는 테스트 공개**

대상 커밋: [f42238c341ff890449154562052be15dc7f9b2e7](https://github.com/yw8837/jigeum/commit/f42238c341ff890449154562052be15dc7f9b2e7), 10:10:33 UTC. 기준 a693b98부터 이 커밋까지 중간 커밋은 없다. 이 검토 당시 앱 운영 코드 수정·배포·실결제는 하지 않았다.

## 가장 중요한 결론

현재 소스는 **0.3.0/code6 + 결제 테스트 구조 보강**이다. 새로 정한 0.4 전체 이용권 정책의 카메라·저장 영상 유료 제한은 아직 구현되지 않았다. [HANDOFF 7–20행](https://github.com/yw8837/jigeum/blob/f42238c341ff890449154562052be15dc7f9b2e7/docs/HANDOFF.md#L7-L20)

따라서 두 테스트 목표를 구분해야 한다.

1. **현재 기능을 제한된 테스터가 사용:** 결제를 비활성으로 둔 새 debug APK를 통해 음성/직접 입력/카메라/영상/기록/공유/백업 테스트 가능. Play 본인인증·스토어 공개·운영 서버·업로드 서명 AAB는 이 경로의 필수 선행조건이 아니다. 최종 APK의 실제 기기 설치/동작과 전달 경로는 아직 확인하지 않았다.
2. **0.4 무료/전체 이용권 경계까지 테스트:** 중앙 AccessPolicy와 화면·센서·파일 선택·실행시점 게이트 구현이 먼저 필요하다. 계측의 서명 대역으로 경계를 검증할 수 있으나 이를 실제 Play 구매 테스트로 표시하면 안 된다. 운영 앱에 하드코딩 premium/숨은 우회 스위치를 넣는 방식은 피한다.

기존 로컬 0.3.0 APK는 새 체크포인트 소스와 동일하지 않다. 새 SHA에 대응하는 별도 이름의 APK, SHA256, 실제 실행 결과를 확보하고, 기준 APK를 덮어쓰지 않아야 한다.

## 새 커밋의 실제 변화

- `BillingManager`에 내부 BillingGateway/BillingEntitlements 주입 구조와 `hasCurrentEntitlement()`를 추가했다. 공개 생성자는 계속 실제 Play adapter와 PurchaseVerifier를 사용한다. 테스트 fake/메모리 키는 androidTest에 있으며 runtime intent/설정 우회는 발견하지 못했다.
- `hasCurrentEntitlement()`는 액션 순간 캐시 만료를 다시 확인한다. **아직 MainActivity/LibraryScreens/CameraScan/VideoImport에서 호출하지 않는다.** 현재 게이트 연결이 완료됐다는 뜻이 아니다.
- PurchaseVerifier.accept는 isConfigured 조건을 추가했다. 서명 검증·HTTPS 경계·입력 상한 등은 유지된다.
- 새 BillingManagerLifecycleTest 9개는 보류→완료, 액션시점 만료, 일시 장애/거절, 중복 restore, 늦은 응답, close, 취소 콜백, 캐시 복구를 다루도록 작성됐다.
- 서버 소스/시험, Gradle 의존성, 저장·백업·공유·미디어·주요 UI 코드는 baseline과 동일하다.

근거: [BillingManager 28–71행](https://github.com/yw8837/jigeum/blob/f42238c341ff890449154562052be15dc7f9b2e7/outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/BillingManager.kt#L28-L71), [production adapter](https://github.com/yw8837/jigeum/blob/f42238c341ff890449154562052be15dc7f9b2e7/outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/BillingManager.kt#L264-L332), [PurchaseVerifier 22–68행](https://github.com/yw8837/jigeum/blob/f42238c341ff890449154562052be15dc7f9b2e7/outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/PurchaseVerifier.kt#L22-L68)

## 테스트 공개 전 우선순위

### 필수: 새 APK와 실제 실행 증거

체크포인트의 빌드/debug APK/test APK/release AAB/lint는 기록상 성공했다. 새 9개 계측은 **컴파일됐지만 실행 전 중단**됐다. ADB에 에뮬레이터가 없었던 환경 문제로 기록돼 있다. 기존40 성공을 신규9 성공으로 확장할 수 없다. [검증 영수증](https://github.com/yw8837/jigeum/blob/f42238c341ff890449154562052be15dc7f9b2e7/docs/qa/handoff-checkpoint.md)

다음 단계는 기존 로컬 Codex 환경의 전용 에뮬레이터를 복구해 현재 소스 계측 전체를 실행하고, 새 APK를 실제 ARM 단말에 설치해 정상 무료 경로와 카메라/영상/권한 거부/백업 복원을 확인하는 것이다. 0.4를 구현한다면 아래 경계 시나리오도 추가한다.

- 비구독/미설정 상태의 카메라·영상 진입은 권한 요청/파일 선택/모델 시작 전에 안내
- Back·savedInstanceState 복구·선택기 callback·화면 복귀로 유료 화면에 우회 진입 불가
- 카메라/영상 도중 만료·거절 시 정리, 늦은 결과 폐기
- 이미 생성한 문장 초안은 계속 무료 편집/저장, 기존 기록/파일 유지
- 유효할 때 시작한 PDF는 중간 만료에도 완성하되 새 작업은 차단
- 닫기/정책 왕복/복원 후 원래 화면·편집내용 유지

이는 [NEXT](https://github.com/yw8837/jigeum/blob/f42238c341ff890449154562052be15dc7f9b2e7/docs/NEXT.md) 및 [Access Policy](https://github.com/yw8837/jigeum/blob/f42238c341ff890449154562052be15dc7f9b2e7/outputs/Jigeum-Access-Policy.md)와 일치한다.

### 확인 필요: baseline 데이터 경계 후보 2개

이 코드들은 새 커밋에서도 변경되지 않았다. 합성데이터/스케줄 제어로 검증할 항목이며, 실제 실패로 재현됐다고 보고하지 않는다.

- 같은 날짜 동일 내용 + timestamp 충돌 시 별개 기록이 중복으로 버려질 가능성. 기존 t1=B/t2=A, incoming t1=A를 확인. [RecordTransfer 147–178행](https://github.com/yw8837/jigeum/blob/f42238c341ff890449154562052be15dc7f9b2e7/outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/RecordTransfer.kt#L147-L178)
- 공유 파일 생성/삭제 mutex 밖에서 share를 호출하므로 생성완료→삭제→share 또는 삭제→오래된 snapshot으로 생성 순서를 시험. 없는 URI 또는 삭제한 기록에서 새 공유본을 만드는지 확인. [MemoryArtifacts 49–63행](https://github.com/yw8837/jigeum/blob/f42238c341ff890449154562052be15dc7f9b2e7/outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/MemoryArtifacts.kt#L49-L63), [share 219–226행](https://github.com/yw8837/jigeum/blob/f42238c341ff890449154562052be15dc7f9b2e7/outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/MemoryArtifacts.kt#L219-L226)

### 확인 필요: MediaPipe의 protobuf 전이 의존성

Gradle 선언은 unchanged. `tasks-core:1.0.0`이 protobuf-javalite4.26.1을 의존한다는 upstream artifact 보고가 있고 이 버전은 공급자 CVE-2024-7254 영향 구간이다. 실제 resolved dependency/APK 버전과 공격 입력 도달성은 미확정이다. 새 빌드에서 dependencyInsight와 runtime dependency 목록을 남겨 확인한다. 공급자 패치 버전의 MediaPipe 호환성도 모델 실행으로 검증해야 한다.

[공식 보안 공지](https://github.com/protocolbuffers/protobuf/security/advisories/GHSA-735f-pc8j-v9w8), [upstream artifact 관찰](https://github.com/google-ai-edge/mediapipe/issues/6364)

## 실제 유료 테스트를 활성화하기 전 남은 항목

### 결제 콜백 미도착 복구

PURCHASING 상태에서 refresh/restore가 구매 재조회를 생략하는 경로는 유지된다. [BillingManager 64–84행](https://github.com/yw8837/jigeum/blob/f42238c341ff890449154562052be15dc7f9b2e7/outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/BillingManager.kt#L64-L84)

새 시험 이름에 ‘stuck state’가 있지만 [117–133행](https://github.com/yw8837/jigeum/blob/f42238c341ff890449154562052be15dc7f9b2e7/outputs/MoaAndroid/app/src/androidTest/java/com/moa/babylanguage/BillingManagerLifecycleTest.kt#L117-L133)은 USER_CANCELED 콜백을 명시적으로 주입한다. **콜백 미도착**의 recovery test는 아니다. 신규 실행 결과가 성공하더라도 이 미검증 경계는 남는다.

### client-only acknowledge

새 adapter로 옮겼지만 단발 client acknowledge, 실패 메시지, 다음 방문 재시도 구조는 동일하다. [215–220행](https://github.com/yw8837/jigeum/blob/f42238c341ff890449154562052be15dc7f9b2e7/outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/BillingManager.kt#L215-L220), [329–330행](https://github.com/yw8837/jigeum/blob/f42238c341ff890449154562052be15dc7f9b2e7/outputs/MoaAndroid/app/src/main/java/com/moa/babylanguage/BillingManager.kt#L329-L330)

Google은 신규 구독 구매 acknowledge를 3일 내 완료하지 않으면 자동환불·회수한다고 명시한다. 서버 acknowledge/재시도 또는 충분한 복구 검증을 권한다. 이번 검토에서 실제 환불을 일으킨 것은 아니다. [공식 안내](https://developer.android.com/google/play/billing/security), [생애주기](https://developer.android.com/google/play/billing/lifecycle/subscriptions)

### 외부 계정·운영 설정

로컬 Codex가 기록한 상태: 새 개인 Play 계정 신원확인 중, 연락처 인증 대기, 앱 만들기 비활성. 선택된 Cloud 프로젝트도 없음. 이는 이 검토자가 Console에 직접 접속해 재확인한 사실이 아니라 handoff의 관찰 기록이다. [HANDOFF 72–79행](https://github.com/yw8837/jigeum/blob/f42238c341ff890449154562052be15dc7f9b2e7/docs/HANDOFF.md#L72-L79)

APK의 제한적 사용성 테스트 일정과 유료 거래/스토어 공개 일정을 분리한다. 운영 HTTPS·서비스계정·서명키·상품·정책 URL·서명 AAB는 해당 단계 전에 필요하다. 다른 프로젝트를 임의로 재사용하거나 계정·권한·배포를 생성하지 않는다.

## 보안 검토 한계

새 diff의 알려진 비밀 패턴(개인키 PEM, Google API key 형식, GitHub token 형식, AWS access key 형식)에 매칭된 텍스트는 없었다. 전체 이력·바이너리·모든 형태의 비밀 검사가 아니므로 ‘secret-free’ 인증으로 표현하지 않는다.

바뀐 결제 구조에서 직접적인 새 runtime 우회는 발견하지 못했다. 기존 HTTPS/서명/설치 바인딩·provider 경계·SQLite 방어는 유지된다. 오프라인 시계 rollback, bearer 구매토큰 재바인딩, reverse proxy 공유IP 제한은 알려진 잔여위험이다. 제로데이 부재나 전체 보안/출시 완료를 보장하지 않는다.

독립 실행: 새 f42238c의 PlayVerifier에서 기존16개+추가18개=34/34 통과, 실패·취소·skip0. 실제 Google API/구매는 사용하지 않고 응답 대역과 localhost HTTP만 실행했다. 실행 증거는 [검증 기록](verification.md)을 참고한다. 이 결과는 Android 추가9개나 UI가 통과했다는 뜻이 아니다. 아래 인수 체크리스트는 최신 체크포인트에 맞춘 후속 실행 항목이다.

## 현재 Codex 담당자에게 넘길 실행 체크리스트

1. **테스트할 버전 고정:** 이번 공유본이 현재 0.3 체크포인트인지, 무료/유료 경계까지 구현한 0.4인지 명시한다. 0.4라면 NEXT의 중앙 정책·화면 분기·실행 직전 검사를 구현한 뒤 해당 SHA로 진행한다. main과 다른 미푸시 변경을 포함하면 그것도 기록한다.
2. **에뮬레이터 복구와 49개 실행:** RUNBOOK의 실제 SDK/AVD 경로를 사용하되 단일 ADB 담당자가 실행한다. assembleDebug, assembleDebugAndroidTest, bundleRelease, lintRelease의 최종 코드 결과와 전체 계측 수·skip·실패를 남긴다. 새 9개가 성공하기 전 49/49라고 표시하지 않는다.
3. **백업 충돌 시험 추가:** 같은 날 timestamp t1의 문장 B와 t2의 문장 A를 저장 → JSON에 t1의 문장 A를 넣어 preview/merge → 서로 다른 관찰 세 개가 남아야 하는 조건에서 duplicates로 빠지는지 확인한다. 현재 알고리즘의 의도된 재가져오기 dedup도 함께 검증한다.
4. **삭제/공유 경합 시험 추가:** 긴 PDF 생성 또는 지연을 주입한 PNG 생성 → 기록 화면으로 이동 → 해당 기록/전체 삭제 → 생성 완료를 풀어 준다. chooser가 삭제한 기록을 새 파일로 내보내거나 존재하지 않는 URI를 제시하는지 확인한다. 반대 순서인 생성 완료 후 공유 직전 삭제도 검증한다.
5. **의존성 실제값 저장:** :app:dependencyInsight --dependency protobuf-javalite --configuration releaseRuntimeClasspath와 전체 runtime 의존성 결과를 남긴다. 4.26.1 포함 여부를 먼저 확정하고, 교체 시 FaceLandmarker/YAMNet이 최종 릴리스에서 실제로 초기화·추론되는지 다시 확인한다.
6. **테스터용 산출물:** 기준본을 덮어쓰지 않는 고유 파일명 debug APK, SHA256, 대응 commit, 설치/업데이트 조건(같은 서명키 필요), 지원 Android 버전, 알려진 한계, 실제 ARM 1대의 설치·녹음·영상·카메라·백업 복원 결과를 묶는다. 개인 아기 자료 대신 합성 자료로 먼저 시험한다. 실제 유료 미활성임을 분명히 적는다.
7. **유료 시험 전에 별도 처리:** fake Play에서 launch OK 후 콜백을 주지 않고 refresh/restore/복귀를 반복하여 PURCHASING을 빠져나올 수 있는지 시험한다. acknowledge 실패/응답 유실/앱 종료를 주입한다. 이후 운영 계정·서비스·상품·라이선스 테스터 준비가 된 뒤 실제 Play 생애주기를 검증한다.

위 체크리스트는 기존 소유자가 실행할 구체적인 인수 항목이며, 외부 배포·스토어 제출·실구매·키 생성·권한 부여를 승인하거나 수행한 기록이 아니다.
