# Standards 검토 — 테스트 접근 복구

2026-10-01 · 기준 `d33b886a1dbf8c3783b2a755f7649c1be181fdbb` · `git diff d33b886 -- outputs/MoaAndroid outputs/QA tools/release`와 새 빌드 스크립트를 읽었다. 기준 문서는 `AGENTS.md`, `docs/HANDOFF.md`, 이 폴더의 `PLAN.md`다. Gradle/ADB·테스트 실행 없이 소스만 검토했다.

## Hard violations

**확인된 위반 없음. 소스 검토 승인.** 전체 기능을 연 preview 배포는 사용자의 테스트 해제 요청에 따른 구현이며, 실제 구매를 위조하는 테스트 대역으로 취급하지 않는다.

- `app/build.gradle.kts:31,68–71`에서 release 기본값은 false, preview만 true이며 preview의 결제 URL·공개키는 비워진다. `MainActivity.kt:135–136`은 빌드 상수를 접근 정책으로 전달한다. 외부 Intent나 저장된 화면 값으로 배포 모드를 변경하는 경로는 없다.
- `AccessPolicy.kt:40–48`의 preview 허용은 메모리 값이다. 실제 구매 상태를 쓰지 않는다. release는 기존 `hasCurrentEntitlement()` 호출을 유지하며, PDF의 실행 검사(`MainActivity.kt:343–349`)도 UI의 premium 표시와 분리돼 있다.
- `CaptureFlow.kt:113–120,185–190`은 이미 허용된 센서만 준비한다. 자동 녹화·권한 요청은 추가되지 않았고, 녹화 시작 직전 권한 확인·백그라운드 취소·늦은 결과 폐기가 유지된다. 기본 시각 선택도 preview의 영상에 한정된다.
- 원본 저장·공유 경로와 분석 모델을 바꾸지 않았다. preview도 음성 근거가 없으면 한마디를 생성하지 않는 기존 분석 경계를 사용한다. 테스트 안내는 전체 화면 상단과 돌봄 화면에 표시된다.
- `build-test-apk.ps1:9–10,31`은 기존 산출물 덮어쓰기를 거절하고 APK/assemblePreview로 제한한다. 서명 비밀은 저장소 밖 DPAPI에서 읽고 finally에서 환경을 복구한다. preview AAB는 Gradle 두 task에서 차단하도록 작성돼 있다.

## Smells — 판단 사항

- **가능한 Duplicated Code:** `build-test-apk.ps1:12–28,45–48`의 키 로딩·환경 보관/복구가 `build-play-bundle.ps1`과 유사하다. 향후 서명 정책 변경 시 누락을 줄이려면 공통 함수로 모을 수 있다. 현재 보안 결함이나 승인 차단 사유는 아니다.

## 실행·운영 한계

실제 preview APK의 플래그/서명, preview→release 업데이트 후 재잠금, 기록 보존, AAB task 차단은 메인의 T4/T7 실행 증거가 필요하다. 이 검토는 해당 실행 통과를 주장하지 않는다. 테스트 APK 자체에 만료/원격 종료는 없으므로 테스트 종료 후 제한 복구는 정식 버전 업데이트를 설치해야 적용된다.

총 hard violation **0**, smell **1**. 소스 범위 최악의 차단 이슈는 없으며 배포 판정은 실제 패키지·업데이트 검증 결과를 따른다.
