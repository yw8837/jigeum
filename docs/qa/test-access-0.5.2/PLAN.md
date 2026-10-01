# 테스트 접근 복구 · 0.5.2

## 목표와 범위

사용자 확인: 영상 기능이 이용권 때문에 잠겼다. 테스트 동안 전체 기능을 열라는 기존 요청이 APK 배포에 반영되지 않았다. 0.5.1의 제한 자체는 출시 정책과 일치하지만 테스트 인도 기준을 위반한다.

승인 기준: 구매·심사 코드 없이 영상의 시각 관찰과 PDF를 실행하는 서명된 테스트 APK. 같은 package와 업로드 서명으로 기존 설치/기록 유지. 출시 build는 기존 실행 시점 이용권 검증 유지. 공개 제출/Cloud 변경 없음.

가설: (1) 출시 APK 전달로 잠김 — 사용자 설명과 소스 일치. (2) 복원 실패 — 테스트 구매가 필수가 아니므로 해법 아님. (3) 카메라 권한 문제 — 별도 회귀 대상이며 이번 잠금 원인은 아님.

## 디자인 결정

Refero connector는 이 세션에 없다. 기존 승인된 앱과 docs/UX-0.5.md를 주 기준으로 사용한다. 새 시각 스타일을 만들지 않는다.

| 결정 | 근거/역할 | 이유 |
|---|---|---|
| Cream 바탕, Ink 글자/CTA, Sage 보조면, 기존 아기 일러스트 유지 | 사용자 승인 디자인 | 익숙한 앱을 유지 |
| 모든 화면에 작은 테스트 안내 | 사용자 전체 기능 테스트 요청 | 구매 이용권과 테스트 권한을 혼동하지 않음 |
| 허용된 카메라/마이크는 화면 진입 시 준비, 녹화는 직접 시작 | 사용자 번거로움, refero craft | 준비 탭 제거, 자동 녹화 방지 |
| 표정 옵션 행 전체 터치 가능 | refero craft의 label/control hit target | 작은 스위치만 겨냥할 필요 제거 |
| 테스트 영상은 표정 관찰 기본 선택 | 사용자 영상 테스트 요청 | 숨은 옵션·결제 진입 제거, 소리 전용으로 변경 가능 |
| 출시용은 기존 무료/유료 경계 | 기존 정책 | 테스트 종료 시 정식 업데이트로 제한 복구 |

## UltraQA 시나리오 행렬

모든 실행은 전용 에뮬레이터에 한정한다. 최대 5회 수정 사이클, 같은 실패 3회면 원인과 잔여 범위를 보고한다. 원본 아기 자료/인증정보는 커밋하지 않는다.

| ID | 의도/사용자 | 준비·명령/하네스 | 예상 신호 | 실제/수정/증거 | 정리 |
|---|---|---|---|---|---|
| R1 | 기존 테스트 사용자 | code14 APK + 저장 영상 + 표정 선택 | 이용권 잠금 재현 | 재현: raw/baseline-lock.json | 전용 앱 교체 |
| T1 | 테스트 사용자 | preview APK, 공개 울음+단색 영상, 구매 없음 | 모델 실행→시각 결과→기록 저장→PDF 공유 | PASS: raw/preview-ui-final.json, preview-pdf-share.txt. 얼굴 미검출 정상 반환 | 기기 fixture 삭제; 기록 최종 정리 |
| T2 | 무음/비아기 입력 | 합성 tone 영상 | 전체 접근이어도 한마디 생성 안 함 | PASS: raw/preview-ui-first.json의 small/font200 완료 시나리오(뒤 PDF 검사기 실패와 구분) | fixture 삭제 |
| T3 | 권한 허용/거절/중단 | 실제 capture UI + 계측 | 준비 탭 없음, 자발적 시작, 배경 전환 중지 | PASS: raw/permissions.json 및 CaptureDeviceTest | 표시 설정 복원; 앱 최종 정리 |
| T4 | 재시작/업데이트 | code14→preview→release, 같은 서명 | 기록 유지, preview 안내, release 재잠금 | PASS: raw/preview-ui-final.json. 세 설치 SHA 확인 | 전용 데이터 최종 정리 |
| T5 | 위조 UI/만료/취소 | 기존 AccessNavigationTests 및 계측 | release는 실제 entitlement로 실행 차단 | PASS: raw/instrumentation-final.json 전체164통과/3skip/0fail | 테스트 앱 삭제 |
| T6 | 작은 화면/200% | 실제 preview UI 하네스 | 시작·분석·닫기 접근 가능 | PASS: 첫 실행 small/font200 캡처·분석, 스크린샷 육안 확인. 세 번째 실행은 변경 없는 layout을 반복하지 않음 | 표시 설정 복원 |
| T7 | 배포 실수 | preview bundle task + release/preview package 검사 | preview AAB 거부, release flag false | PASS: raw/bundle-preview-blocked.log(예상 exit1), package-*.json. signed/16KB/native18 유지 | 로그 보존 |
| T8 | 불완전 테스트 결과 | 전체 instrumentation 엄격 파서·lint | 개별 완료/허용 skip/설치 해시 확인 | PASS: 호스트24/24, lint0오류·15경고 각각, 계측167건 엄격 검증 | 프로세스 종료·격리 OMX 상태 정리 |

실제 아기 뜻 해석 정확도와 실제 ARM 폰은 이번 접근 복구 통과만으로 검증됐다고 하지 않는다.
