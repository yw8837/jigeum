# 0.4.3 통합 QA 실행 근거

2026-09-30 · versionCode 10 · Android API35 x86_64 `MoaQA35` 전용 에뮬레이터.

사용자의 PR #2 준비 완료 후 검증 지시에 따라 별도 통합 브랜치에서 실행했다. Android 검증 전 main에 머지하지 않았다. [QA 보고서](../../../outputs/Jigeum-QA-0.4.3.md)와 [PR 통합 이력](../PR2-INTEGRATION.md)을 함께 확인한다.

## 소스와 산출물

- 검증한 소스: `c4edfed8910c1d2cf01160deb0fd2d87f811c407`.
- 기준 main: `51131377d8d4a6f5b6aed1cd5e3ce76860780cb2`.
- 최초 준비 완료 PR head: `0e19c09577352bd2c1c0da69dff4de58ed4d2844`.
- 최신 PR head: `8475658f171b68e6d909f2798bd680fdeeaffd9d`.
- [산출물 해시와 크기](artifacts.json). APK는 개발 서명, AAB는 미서명이다. 이전 산출물은 덮어쓰지 않았다.

## 결과와 근거

| 검증 | 결과 | 근거 |
|---|---|---|
| Android 전체 계측 | 119/119, fail/ignored 0 | [요약](instrumentation-result.json), [전체 로그](instrumentation.log) |
| Node 서버 | 43/43, fail/cancel/skip 0; Google 대역 | [로그](server-tests.log) |
| debug/test/release/lint | 성공, lint 오류 0·경고 23·힌트 2 | [빌드](build.log), [lint](lint-summary.json) |
| 실제 무료 UI | normal/access/album/paywall-layout/transfer-e2e 5종 통과 | [요약](ui-summary.json), [화면 검토](visual-review.md) |
| 0.4.2 → 0.4.3 업데이트 | 합성 기록 1건의 모든 필드·월령·schema1 유지 | [업데이트](upgrade-result.json) |
| 패키지·모델/native | 서명·ZIP/ELF 16KB·FileProvider·백업 제외, 이전 18파일과 바이트 동일 | [판정](package-verdict.json) |
| 정리 | 합성 기록/시험 파일/testAPK 제거, 권한/화면 원복, 전용 emulator/daemon 종료 | [결과](cleanup-result.json), [ADB](adb-final-devices.txt), [daemon](gradle-stop.log) |
| UltraQA 상태 | 이번 실행 범위의 상태를 clear 후 absent 확인 | [상태](state-cleanup.json) |
| 소스·의존성 검토 | 실행 결과와 읽기 전용 검토 구분 | [교차 검토](cross-review.md), [SDK 전송 경로](sdk-transport-review.md), [SDK DB 요약](sdk-transport-summary.json) |

## 실패를 포함한 실행 이력

1. [cycle1](instrumentation-cycle1-result.json): 별도 UID 수신 시험 1개 실패. standalone test APK에 Kotlin 런타임이 없어 수신기가 종료되고 finally가 원래 오류를 가렸다. Android/Java 기본 API 수신기와 발송 전 context 확보로 시험 harness를 교정했다. [원본 로그](instrumentation-cycle1-failure.log), [교정 후 focused](uid-focused-result.json).
2. [cycle2](instrumentation-cycle2-result.json): 기존 초안 보존 시험의 8초 대기 1회 timeout. **원인 미확정**이다. 입력·대기 제한을 바꾸지 않은 [focused1](access-original1-result.json), [focused2](access-original2-result.json)와 최종 전체에서 통과했다. [원본 실패 로그](instrumentation-cycle2-failure.log)를 보존하고 재발 시 정리 전 진단을 남기도록 했다.
3. 최종 cycle3: Android 119/119. 이전 후보 95/main 85 결과를 PR #2 검증으로 재사용하지 않았다.

## 검증 범위와 잔여 조건

실제 CameraX provider/usecase와 별도 UID FileProvider 읽기를 사용했다. 유료 이용권·Google 응답은 시험 대역이며 실제 구매·ARM 실행을 증명하지 않는다. 공유 시스템판 진입과 PNG 미리보기는 실제 UI에서 확인했고, 별도 UID 시험은 명시적인 시험 수신 앱을 사용했다.

JSONv1 백업에 로컬 출처는 포함되지 않는다. 새 DB 복원 뒤 원 충돌 백업을 다시 가져올 때 사본이 추가될 수 있는 한계를 유지한다. 전달 snapshot은 최대 24시간 후 다음 앱 작업에서 지연 정리하며, 파일 수/총 크기 상한에서는 새 공유를 거절한다.

SDK 진단 경로의 현재 차단 조건은 공식 opt-out이 아니며 모든 SDK 통신의 패킷 검증이 아니다. SDK/권한 변경 시 재검토한다. 실제 Play 구매·복원·환불·ACK, 운영 URL/키/계정/프록시, 업로드 서명, ARM/OEM/16KB 실기기, 정책 HTTPS·시행일은 미완료다. 공개 출시·Play 제출은 보류한다.

Git에는 정규화한 검증 문서·로그만 남긴다. APK/AAB와 원본 증거 ZIP·합성 DB/스크린샷은 로컬 산출물이며 비밀키·실거래 토큰·사용자 기록·빌드 캐시는 포함하지 않는다.
