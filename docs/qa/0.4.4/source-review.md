# 0.4.4 소스 최종 검토

기준 main c7faa2c / 검토 source 9cef337db42a3ed038383dc87b84a1ffbde39474. 읽기와 실행 근거를 구분한다. 원격 main 동일, open PR 없음을 확인했고 코닷 PR #2 head8475658은 main에 포함돼 있다. 코닷의 조사·가격·파일럿 절차를 실제 실행 결과로 바꾸지 않았다.

## 유지한 경계

- SQLite: 현재 DB에서 merge를 재계산하고 행/출처/월령을 하나의 transaction으로 저장한다. 출처에는 원 ID와 길이로 구분한 모든 UTF-8 필드를 포함한다. 삭제 때 출처를 제거하며 원시 meta 읽기는 10,000개로 제한한다. JSONv1/schema1 및 새 DB 복원의 출처 구분 한계는 유지한다.
- UI mutation: 실제 수락한 삭제만 generation을 무효화한다. import의 root busy 예약은 Guide가 사라져도 유지하고 finally에서 해제한다. stale PNG/PDF callback은 최신 기록과 UI snapshot을 대조한다.
- 공유: dispatch 전 generation/RESUMED Activity 확인, 전달 snapshot을 별도 폴더로 이동하고 24h lazyTTL/20개/64MiB 제한을 적용한다. 이미 전달한 URI는 원본 삭제와 별개로 유지하며 취소는 미전달 출력만 제거한다.
- CameraX: provider 완료 listener에서 최신 authorize와 CAMERA를 bind 직전에 확인한다. 분석 결과 발행에도 최신 이용권을 확인한다. production fake provider/premium flag/권한 추가는 없다.
- 결제: 0.4의 무료/전체 이용권,0.4.2 서버ACK와 서명 tokenhash, 오프라인 같은-boot 시각 계약을 유지한다. 설치ID/서명 envelope와 시각 anchor의 동기 commit 성공 Boolean을 계속 확인한다. clear도 KTX commit=true로 동기 처리한다.
- 의존성/개인정보: SDK 버전을 변경하지 않았고 ACCESS_NETWORK_STATE 제외/INTERNET/백업 제외/FileProvider 범위를 유지한다. 이 검토는 모든 SDK 통신을 패킷 캡처한 인증이 아니다.

## 이번 변경

- 실제 알림 겹침 회귀 시험은 이전 FIFO에서 2초 기준에 실패했다. LatestFeedback은 main thread에서 이전 요청을 취소하고 최신 요청 하나만 유지해 오래된 확인 알림이 최신 오류를 지연시키지 않게 한다. 앱 scope dispose 때 요청도 취소된다. 기록/구매 상태를 변경하는 로직과 알림 표시를 구분했다.
- 재입력 TextButton은 최소48dp의 버튼 semantics와 터치 영역을 제공한다. 원 초안 timeout의 원인을 확정한 변경은 아니다. 원래 입력·8초 조건을 유지하고 실제 IME 표시·48dp·편집 보존·무료 저장을 확인하는 추가 시험을 작성했다.
- Canvas translation/clip은 KTX의 finally 복원 경로를 사용한다. bitmap/URI/scale API 치환의 기존 인수·크기를 유지하고 CameraSelector Int state를 primitive state로 바꿨다. 디코더의 두 INFO 상태는 기존 bounded loop에서 retry/no-op로 명시한다. 런처 리소스는 min26에 맞는 일반 폴더로 옮겼다.

- Billing gateway는 미설정 설치에서 생성하지 않도록 lazy로 바꿨다. init.refresh는 설정된 경우 기존처럼 필요한 연결을 시작한다. close는 생성된 client만 종료하며 중복 close는 무시한다. ACK·PENDING·세대/만료/회복 계약은 변경하지 않았다. 미설정의 생성/refresh/restore/현재권한/close0회, 설정된 생성/종료1회,21개 수명주기와 최종123개 계측을 검증했다.

## Lint 판정

원본23경고/2힌트 → 현재11경고/0힌트, 오류0. 경고 억제·baseline·검사 비활성화는 추가하지 않았다.

| 남은 항목 | 건수 | 유지 이유 |
|---|---:|---|
| Gradle·Activity·Lifecycle·test runner/JUnit·protobuf 새 버전 안내 | 6 | 검증된 SDK/빌드 조합 유지. 최신 버전 존재를 실행 결함 또는 취약점으로 단정하지 않는다. 업그레이드는 새 의존성·모델/ABI·프로토콜·전체 QA를 포함해야 한다. |
| SharedPreferences KTX edit 권고 | 5 | KTX edit는 Unit을 반환한다. 현재 설치ID, 서명 envelope/anchor, 문장 저장/삭제, legacy 삭제는 commit의 Boolean으로 저장 실패를 처리한다. 단순 치환으로 실패 처리를 버리지 않는다. |

정확한 위치/메시지는 lint-review.json과 원본 lint XML/txt에 남긴다. 현재 APK에는 운영 결제 설정·업로드 서명이 없다. 실제 Play/ARM/OEM/TalkBack 사용자 검증·부모 파일럿·공개 제출을 수행했다고 주장하지 않는다.
