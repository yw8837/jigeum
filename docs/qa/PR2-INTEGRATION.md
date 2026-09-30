# PR #2 통합 대기 — 2026-09-30

사용자 지시: ddot은 분석 근거·평가·시장·BM 상세 기획과 PR #2의 가져오기·공유·카메라 시작 권한 수정을 담당한다. **준비 완료 안내 전에는 PR #2를 머지하지 않는다.** 기존 0.4 접근 정책을 유지한다.

## 현재 구분

- main의 후속 작업: 결제 콜백 유실 복구, 구매 조회 timeout과 이전 응답 방어, acknowledge 제한 재시도, protobuf 4.27.5. PR #2의 Android 수정은 포함하지 않는다.
- PR #2: `feat/ddot-04-access-and-record-safety`, 읽기 확인 당시 head `0e19c09577352bd2c1c0da69dff4de58ed4d2844`, Draft. Android 실행 검증은 미완료로 명시돼 있다.
- 기존 로컬 대안: `codex/service-041-candidate` / `99ca1e1`. 가져오기 provenance·JSON v2와 공유 작업 예약 구현을 포함한다. **로컬에만 보존하고 main이나 원격에 이 대안을 올리지 않았다.**
- 로컬 대안의 debug/test/release/lint 빌드와 Android 95개 시험은 통과했다. 이 결과는 PR #2의 실행 결과가 아니다. 증거는 `work/review-041-20260930/evidence`, 후보 APK는 그 안의 `local-candidate`에 있다.

## 준비 완료 후 비교할 경계

1. 타임스탬프 충돌로 ID가 바뀐 기록의 반복 import, 별개 시각의 같은 내용 보존, provenance 삭제·트랜잭션 rollback·DB upgrade·백업 왕복.
2. PNG/PDF 생성 중 삭제, 생성 후 dispatch 직전 삭제, 이미 chooser에 전달한 파일의 수신 앱 읽기. 이전 기록 콜백은 현재 기록 존재 여부를 다시 확인해야 한다.
3. 가져오기가 진행되는 동안 전체 삭제: 완료 뒤 기록이 다시 생기지 않도록 작업 예약/취소/세대 처리를 검토한다. 이는 기존 코드의 추가 경계이며 PR #2에서 해결됐다고 가정하지 않는다.
4. CameraX provider의 비동기 대기 후 실제 bind 직전 현재 권한 확인. 이전 화면의 늦은 콜백과 만료 후 시작을 거절한다.
5. main의 결제·protobuf 수정, 0.4 무료/전체 이용권 정책을 유지하고 합성 자료만 사용해 통합 빌드·lint·기존+신규 전체 계측과 관련 UI를 실행한다.

PR #2를 main에 맞추고 정확한 head SHA를 고정한 뒤 검증한다. 최신 main의 결과와 PR #2 결과를 섞어 완료로 보고하지 않는다. 공개 출시·실제 구매·ARM 검증은 별도 조건이다.
