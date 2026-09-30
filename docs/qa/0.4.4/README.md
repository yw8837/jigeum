# 0.4.4 최종 품질·패키징 근거

실행 2026-09-30~2026-10-01. API35 x86_64 전용 `MoaQA35`. root만 빌드/ADB를 실행했다.

[전체 QA 보고서](../../../outputs/Jigeum-QA-0.4.4.md) · [소스 검토와 Lint 판단](source-review.md) · [산출물 SHA/크기](artifacts.json).

검증 소스 `9cef337db42a3ed038383dc87b84a1ffbde39474`, 기준 main `c7faa2c`. 코닷 PR #2 head8475658을 포함하고 새 open PR은 없었다. 기존0.4 정책·0.4.2 ACK·JSONv1/schema1·SDK 버전을 유지했다.

## 최종 결과

| 검증 | 결과 | 근거 |
|---|---|---|
| Android | 123/123,fail/ignored0 | [결과](instrumentation-result.json),[로그](instrumentation.log) |
| 서버 | 43/43,fail/cancel/skip0,Google대역 | [로그](server-tests.log) |
| 실제UI | normal/access/album/paywall-layout/transfer-e2e 5종 | [결과](ui-summary.json),[화면](visual-review.md) |
| 빌드·Lint | 성공,오류0/경고11/힌트0 | [빌드](build-final-lazy.log),[항목판정](lint-review.json),[전체Lint](lint-final.txt) |
| 업데이트 | 0.4.3/code10 실제합성1건→0.4.4/code11,필드/meta/schema1 유지 | [결과](upgrade-result.json) |
| 패키지 | 개발서명APK/unsignedAAB,ZIP/ELF16KB,백업/provider,모델/native18파일 동일 | [결과](package-verdict.json) |
| SDK | 버전8항목·권한제외·실제CCT권한실패12줄 | [차이](sdk-delta.json),[로그신호](sdk-diagnostic-signals.txt) |
| 정리 | 합성기록/출처/임시파일/시험DB/testAPK/fixture 제거,권한/화면 원복 | [결과](cleanup-result.json),[ADB](adb-final-devices.txt),[daemon](gradle-stop-final.log),[상태](state-cleanup.json) |

결제 초기화는 [수정전 생성1회 실패](billing-lazy-red.log) → 수정후 생성0회 통과다. [미설정 회귀](billing-lazy-green-unconfiguredBillingNeverCreatesGatewayEvenWhenRefreshedRestoredOrClosed.log), [설정/종료 회귀](billing-lazy-green-configuredBillingCreatesOneGatewayAndClosesItOnce.log), [수명주기21개](billing-lifecycle-final-result.json)를 확인한다. 원 초안timeout과 별개의 수정이다.

## 실패와 반복 검증

- 원 초안8초 조건: [12회 결과](draft-reproduction.json). 이전timeout의 원인은 미확정이다. 원본 로그는 로컬 증거ZIP의 draft-original-01~12이며 집계 파일은 실제 생성된 이름을 유지한다.
- 실제IME·48dp·편집보존·무료저장: [12회 결과](draft-ime-reproduction.json). 최종전체에도 포함했다.
- 기준0.4.3의 전체119 재실행: 다른 PDF callback 알림 대기1회timeout. [결과](baseline-full-instrumentation-result.json),[로그](baseline-full-instrumentation.log). 원 초안시험은 이 실행에서 통과했다.
- 알림 겹침 회귀: [수정전 실패](feedback-red.log),[수정후 통과](feedback-green.log). 동일2초조건에서 이전FIFO의 지연을 재현하고 최신요청1개만 유지하도록 고쳤다. 테스트대기를 늘리지 않았다.
- 아이콘폴더 이동 후 incremental 리소스연결2회 실패: [첫로그](build-before-feedback.log),[두번째](build-before-feedback2.log). 입력재읽기 빌드와 후속 정상빌드가 통과했다. 최종 APK에 런처리소스가 포함됨을 패키지/실제실행으로 확인했다.

Git에는 정규화한 문서·로그만 남긴다. 원본스크린샷·합성DB·재현/정리스크립트는 로컬 QA 증거ZIP에 보존하며 사용자기록·실거래token·비밀키·빌드cache는 커밋하지 않는다. 이전0.4.3 근거는 수정하지 않았다.

[정리 도구 교정](cleanup-harness-note.md) · [최종 행렬](plan-final.md) · [명령/종료코드/timeout](execution-summary.json) · [콜드 시작 관찰](cold-start-lazy-summary.json). 이전121개 후보의 원본은 로컬 증거ZIP의 before-billing-lazy에 별도 보존한다. 순차 기동 측정은 ARM 성능이나 제어된 비교가 아니다.

## 잔여 검증

원 초안timeout의 원인확정과 ARM/OEM·실사용TalkBack·부모파일럿은 별도다. SDK 검토는 모든통신의 패킷검증이나 공식opt-out이 아니다. 운영URL/공개키/업로드서명 미설정으로 실제구매는 비활성화다. 실제Play 구매/복원/환불/ACK·계정/상품·HTTPS/키/프록시/관측/RTDN·정책HTTPS/시행일과 공개제출은 로컬QA·패키징 통과와 구분한다.
