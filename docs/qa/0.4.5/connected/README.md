# 0.4.5/code12 운영 연결 후 QA

2026-10-01 · root 단독 빌드/ADB · 전용 API35 x86_64 에뮬레이터.

이 결과는 앞선 URL·공개 키 미설정 [로컬 체크포인트](../README.md)에 추가한 별도 실행이다. 최종 `connected` APK/AAB에는 실제 HTTPS 주소와 대응 공개 키가 들어 있다. 앞선 `internal`·중간 `configured` 파일과 해시를 덮어쓰지 않는다.

## 실제 검증

| 항목 | 결과 | 근거 |
|---|---|---|
| 최종 Android 회귀 | **142 통과 / 2 assumption skip / 실패·ignored 0**, 89.91초 | [결과](instrumentation-result.json), [원본 로그](instrumentation.txt) |
| 최종 debug/test/lint 빌드 | 성공, Lint 오류 0 / 검토한 경고 15 | [빌드](build-debug.txt), [Lint](lint-summary.json) |
| 최종 업로드 키 서명 APK/AAB 빌드 | 성공 | [빌드](build-signed.txt), [APK 서명](apk-signature.txt), [AAB 서명](aab-signature.txt) |
| 실제 릴리스 앱 ↔ 서버 | **10/10** | [결과](live-app-result.json) |
| 실제 HTTPS 경로 | **9/9** | [결과](../../../release/20261001/https-smoke.json) |
| 설치별 제한·격리 | **4/4**, 오류 코드 요청 48개 | [결과](../../../release/20261001/live-load-smoke.json) |
| 실제 Cloud IAM inventory | **19/19** | [결과](../../../release/20261001/cloud-iam-audit.json) |
| 실제 Cloud Run 설정 | **11/11**, 고정 image digest | [결과](../../../release/20261001/run-service-verdict.json) |
| 최종 패키지 | 서명·ZIP/ELF 16KB·백업/공유 경계 통과, 모델/native 18파일 불변 | [결과](package-verdict.json) |
| 구성·fixture 분리 | APK/AAB에서 실제 URL·공개 키 확인, 운영 비밀·TLS fixture 항목 0 | [해시/크기](artifacts.json) |
| 정리 | 합성 기록·심사 캐시·앱 제거, 글꼴/화면 원복, Gradle·에뮬레이터 종료 | [정리](cleanup-result.json), [최종 ADB](adb-final-devices.txt) |

건너뛴 두 시험은 미설정 빌드 전용 `missingConfigurationCannotUnlockPremium`, `unconfiguredManagerSurvivesRefreshRestoreAndRepeatedClose`다. 앞선 미설정 빌드에서는 144개가 실제 통과했다. runner의 `OK (144 tests)`를 연결 빌드에서 144개가 모두 실행 통과한 것으로 보고하지 않는다. [계측 결과 파서 수정/재생](../instrument-parser-replay.json)을 보존한다.

Git용 `.txt` 실행 로그는 CR/CRLF와 줄 끝 공백만 정규화했다. 원본은 로컬 work와 `outputs/Jigeum-QA-connected-0.4.5.zip`에 보존했고 [원본/정규화 해시](log-normalization.json)를 별도로 기록했다. 정규화 전후 파서의 통과·skip·실패 결과는 같으며 실행 실패나 진단 내용을 삭제하지 않았다.

## 실제 앱 흐름과 시각 검토

1. 무료 직접 입력 → 문장 → 기록 저장.
2. 실제 이용권을 받기 전 카메라 잠금.
3. 실제 HTTPS 서비스가 서명한 심사 이용권을 업로드 키 서명 릴리스 앱이 수락.
4. 카메라 시작·종료, 영상 화면 진입, PDF 생성·공유.
5. 심사 접근 해제 → 카메라 재잠금, 무료 기록 보존.
6. 앱 개인정보 화면과 공개 정책의 서버·로그 30일·국외 처리 안내 일치.

영상은 이번 실행에서 유료 화면 진입만 확인했다. 디코딩·손상 영상·기한·수명주기는 이전 로컬 QA를 따른다. 합성 장면만 사용했고 원본 아기 데이터나 실제 구매는 사용하지 않았다.

실제 심사 활성 화면과 PDF 공유 화면을 직접 검토했다. 비밀 코드는 표시되지 않는다. 200% 글꼴의 개인정보 화면은 스크롤로 새 4번 항목과 로그 안내에 접근할 수 있고, 제목/본문의 가로 잘림·겹침이 없음을 [안정된 캡처](connected-privacy-200-stable.png)에서 확인했다. 긴 본문의 화면 아래 잘림은 스크롤로 이어진다. 글꼴 변경 직후 전환 중 캡처는 최종 시각 근거에서 제외했다.

## 운영에서 발견한 수정과 독립 검토

- PowerShell7의 단일 JSON/배열 출력 문제와 budget quota project 누락을 실제 실패에서 교정했다. PS5.1/7 각각 14사례, 총 [28/28](../../../../tools/deploy/QA-bootstrap-20261001.json).
- 외부 `/healthz`는 Google HTML 404였고 앱 요청 로그가 없었지만 로컬에서는 JSON 200이었다. 외부 상태 확인을 `/jigeum-health`로 추가하고 Node **56/56**·실제 HTTPS **9/9**를 확인했다. 공식 reserved route라고 단정하지 않는다.
- 첫 앱 실행 도구는 심사 해제 후 접힌 영역의 문구를 찾아 실패했다. **해제 버튼 소멸·카메라 재잠금·무료 기록 보존**이라는 실제 결과를 검사하도록 고쳤다. 제품 권한 조건은 바꾸지 않았다. [첫 결과](../live-app-first-attempt.json)를 보존한다.
- 앱 개인정보·약관을 실제 공개 정책과 같은 시행일 및 로그 설명으로 맞췄다.
- 원래 [소스 승인](../review-verdicts.json)은 당시 해시 그대로 보존하고, 이후 변경은 [별도 승인](supplemental-review.json)에 기록했다. 검토자가 Android·Cloud를 실행한 것으로 보고하지 않는다.

## 수치의 적용 범위와 남은 항목

요청 제한 시험은 **warm 상태의 잘못된 심사 코드** 요청이다. p95 374ms를 실제 Play 구매 처리 시간이나 수용량으로 쓰지 않는다. 콜드 시작, 실제 구매 동시 부하, Google quota, 장시간 운영, DDoS·UUID 회전 공격·여러 revision 공유 제한은 검증하지 않았다. 월 1만원 알림과 최대 1인스턴스는 비용 상한이 아니다.

실제 Play 상품·가격 등록, Play 배포 설치, 구매/ACK/보류/복원/환불·계정 변경, ARM/OEM·16KB 실기기, TalkBack·보호자 사용성, 비공개 테스트 12명·14일, 공개 제출은 미완료다. 은행 소액 입금 확인도 남았다. Console 정책 URL은 저장 완료이며 심사 접근 코드·나머지 선언·스토어 정보의 최신 상태는 [등록 문서](../../../PLAY-REGISTRATION.md)를 따른다.

이 문서는 **운영 연결 후보의 통합 QA·패키징 완료** 근거이며 실제 유료 출시 완료 보고가 아니다.
