# UltraQA Report — 지금 내 기분이 그래 0.4.2

2026-09-30 · versionCode 9 · 소스 `1888f331d827eb8c7cd9fac7e504a4ad62c3ba39` · API 35 x86_64 전용 에뮬레이터.

**서버·앱 acknowledge 계약의 구현과 로컬 통합 QA를 완료했다.** 기존 0.4 무료/전체 이용권을 유지한다. PR #2는 미통합이며 준비 완료 안내 전 머지하지 않는다. 운영 검증 URL·공개 키·업로드 서명은 모두 미설정이다. 실제 Play 거래·ARM 실기기·출시는 미완료다.

## 목표와 종료 조건

유효한 구매를 서버가 acknowledge한 뒤 이용권을 서명한다. 앱은 서명을 수락하고 현재 구매 토큰의 SHA-256과 확인값이 일치할 때만 중복 acknowledge를 생략한다. 이전 서버 응답에 확인값이 없거나 다른 구매를 가리키면 기존 앱 재시도를 유지한다.

기존 회귀, 오류·동시 요청·취소·시간초과 시험, 패키지 검사와 정리가 통과하면 이 범위의 QA를 종료한다. Google 응답은 대역이며 localhost HTTP·전용 에뮬레이터·합성 키만 사용했다. 배포·실제 구매·외부 아기 DB·PR #2 머지는 수행하지 않았다.

## 결과

| 항목 | 결과·범위 |
|---|---|
| Android | **85/85**, 실패·ignored 0, 하네스 70.46초. 기존 81 + 결제 수명주기 3 + Node→Android 서명 계약 1 |
| Node | **43/43**, 실패·취소·skip 0. 기존 34 + acknowledge 9. Google 응답은 대역 |
| 빌드 | debug APK·계측 APK·release AAB·release lint 성공, 2분 17초 |
| Lint | 오류 0, 경고 23, 힌트 2 |
| 패키지 | debug 서명·ZIP/ELF 16KB 정렬·FileProvider·백업 제외 통과. AAB는 unsigned |
| 모델/native | 보존된 0.4.1 APK의 18개 파일과 바이트 동일 |
| 실제 UI | 최종 0.4.2의 cold launch와 정리 화면 실행. normal/access/transfer 3유형은 이전 0.4.1 결과이며 이번 APK에서 반복했다고 주장하지 않음 |
| 정리 | 합성 기록·월령·문구 이력·공유 캐시·소유 fixture·테스트 패키지 제거, 권한·화면 복원. 앱 crash buffer 0, 소유 에뮬레이터·Gradle daemon 종료 |

## 시나리오 행렬

| ID | 실패 모델·설정 | 실행·기대 | 실제 결과·수정·근거 | 정리 |
|---|---|---|---|---|
| A1 | 유효 구매 ACK PENDING/ACKNOWLEDGED | 실제 verifier+Google 대역: PENDING만 POST, 완료 전 서명 없음 | 통과. 경로·OAuth·body `{}`·빈 성공 응답 확인. [서버](../docs/qa/0.4.2/server-green.log) | 응답 스트림 폐기 |
| A2 | 구매 보류·만료·다른 상품, ACK 누락·unknown | 부적절한 acknowledge와 서명 없음 | 통과. 구매 자격 확인 후 ACK 상태 검사 | 메모리 키만 사용 |
| A3 | ACK HTTP 400/401/403/409/429/5xx·무응답 | 503·호출 기한, 다음 Play 조회로 복구. 오류 본문을 성공 근거로 사용하지 않음 | 통과. 401에서 OAuth 폐기, 다음 ACKNOWLEDGED 응답으로 복구 | 요청·timer 해제 |
| A4 | 같은 토큰 20개 동시 요청, 한 요청 취소, ACK 중 만료 | GET/ACK 각 1회, 설치별 서명, 취소 요청에는 이용권 없음, 최종 만료 거절 | 통과. 조회·ACK 합치기, 완료 결과 캐시 없음 | 공유 요청 종료 |
| A5 | localhost ACK 실패→복구 | 실제 HTTP 503→서명된 200, 원본 토큰 누출 없음 | 통과. 실제 HTTP adapter·verifier와 Google 대역 | 서버·소켓 닫기 |
| A6 | 정확한/다른 토큰 해시, 서명 변조, 이전 응답 | 서명 수락 후 정확한 토큰에서만 앱 ACK 생략. 다르면 fallback | 통과. 기존 수명주기 16개 유지 + 3개 추가 | BillingManager close |
| A7 | Node→Android UTF-8·서명 계약 | 공개 fixture를 Android EC 검증, 토큰 해시 일치와 변조 거절 | 통과. Node 생성 fixture, 개인키 저장 없음 | 테스트 패키지 제거 |
| A8 | 기존 회귀·작업 보존·과거 증거 혼합 | Android 85/Node 43/build/package, 이전 산출물 보존 | 통과. PR 대안 후보 95와 구분, 소스·SHA 고정 | 전용 프로세스·상태 정리 |

수정 전 신규 서버 8개 시험은 1개 통과·7개 실패(exit 1)로 미구현 경계를 확인했다. 수정 후 신규 9개와 기존 34개가 통과했다. Android는 늦은 응답·만료·기록·소리·얼굴 모델 등의 기존 81개를 포함해 85개를 다시 실행했다. 운영 MainActivity에 유료 우회 설정을 추가하지 않았다.

## 명령·증거

- `node --test .../acknowledgment.test.js`: 수정전exit1,8중7fail. [red](../docs/qa/0.4.2/baseline-red.log)
- `node --test outputs/PlayVerifier/test/*.test.js`: exit0,43/43,473.6066ms. [green](../docs/qa/0.4.2/server-green.log)
- Gradle assembleDebug/assembleDebugAndroidTest/bundleRelease/lintRelease(worker1): exit0. [build](../docs/qa/0.4.2/build.log)
- `instrument.py --install-app`,timeout600초: exit0,OK(85tests),ignored0. [계측](../docs/qa/0.4.2/instrumentation.log)
- `check_package.py --expect-unsigned-aab --baseline outputs/Jigeum-Android-debug-0.4.1.apk`: exit0. [패키지](../docs/qa/0.4.2/package-verdict.json)
- 앱내삭제/ownedfixtures/권한·화면 복원/coldlaunch: exit0. [정리](../docs/qa/0.4.2/cleanup-result.json), [scoped상태](../docs/qa/0.4.2/state-cleanup.json)

공식 API의 ACK 상태·POST 경로·빈 성공 본문·OAuth scope를 확인했다: [acknowledge](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptions/acknowledge), [SubscriptionPurchaseV2](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2). 실제 키나 Play API 쓰기는 실행하지 않았다.

## 산출물

- [debug APK](Jigeum-Android-debug-0.4.2.apk),SHA-256 `6d30e3b04f994d33141922e57625fcf093b1ba56270bec40bb159cd81d0dae60`
- [unsigned AAB](Jigeum-Android-unsigned-0.4.2.aab),SHA-256 `353adff0fc9895ef073bd4628351a9c28a165f07e890c459295d2c4be8b86ba9`
- [영수증](../docs/qa/0.4.2/README.md), [크기·소스SHA](../docs/qa/0.4.2/artifacts.json)
- 원본evidence ZIP는 `outputs/Jigeum-QA-evidence-0.4.2.zip`에 보존하고 CRC 전체를 확인한다. 이전0.4.0/0.4.1파일은 덮어쓰지 않는다.

## 남은 범위·운영 조건

- **PR #2 미통합:** 가져오기 충돌·누락, 삭제·공유, CameraX bind 권한과 추가 경합은 [통합 대기](../docs/qa/PR2-INTEGRATION.md)를 따른다. main의 81/85 시험 결과는 PR #2 검증이 아니다.
- 실제 Play 계정 인증·상품, 서비스 계정의 구독 acknowledge 권한, HTTPS 검증/정책 주소, 서명 키, ARM/OEM 검증이 남아 있다. [설정 확인](../docs/qa/0.4.2/operating-settings-presence.json)은 값을 출력하지 않는 존재 여부 검사다. Cloud 프로젝트도 선택되지 않았다.
- 서버가 구매를 처음 받기 전 앱이 재실행되지 않거나 서버 프로세스가 종료되면, 이 무상태 경로만으로 acknowledge 기한을 보장하지 않는다. RTDN·누락 구매 복구·관측·장애 재처리는 운영 준비 과제다.
- HTTP 검증 16초, Google 호출마다 7초, OAuth+GET+ACK 최대 3호출이다. HTTP 기한이 먼저 지나면 이용권을 반환하지 않고 공유 작업은 각 호출 기한 안에서 끝난다. HTTP 32개·공유 Google 작업 32개·연결 256개 상한과 프록시/할당량 전제를 유지한다. 43개 시험은 운영 수용 인원이나 SLA의 증거가 아니다.
- 새 서버와 0.4.2 앱을 운영 전에 함께 검증한다. 구버전 앱은 서버 ACK 후 중복 ACK를 시도해 지연 안내를 표시할 수 있다. 이전 서버 응답에 대한 0.4.2 앱의 fallback은 유지된다.

**ULTRAQA COMPLETE: acknowledge 통합 범위는 cycle1에서 통과. 전체 서비스 완료·PR #2 통합·출시는 대기.**
