# UltraQA Report — 지금 내 기분이 그래 0.4.5

2026-10-01 · versionCode 12 · 전용 API35 x86_64 `MoaQA35` · 빌드/ADB 담당 root.

## 최신 추가 실행 — 운영 연결 후

최종 **connected 0.4.5/code12**는 실제 서버/공개 키를 포함한다. Android **142 통과·미설정 전용 2 skip·실패0**, Node **56/56**, 실제 HTTPS **9/9**, 실제 signed release 앱 **10/10**, IAM **19/19**, Run **11/11**, warm 오류 코드 제한 **4/4**를 확인했다. 최종 서명·패키지·정리와 소스 추가 승인/해시는 [연결 QA](../docs/qa/0.4.5/connected/README.md)를 따른다.

Cloud billing·Play 앱 한정 runtime 권한·정책 게시/Console URL 저장은 완료했다. 실제 Play 상품·구매/ACK/환불·ARM·Console 나머지 항목·12명14일·공개 제출은 미완료다. **아래는 URL/키 미설정 로컬 체크포인트의 보존 기록**이며 그 산출물과 테스트 수를 연결 후보에 소급하지 않는다.

## 목표와 승인 기준

기존 0.4 무료/전체 이용권 정책과 PR #2를 유지하면서 구매 없는 Play 심사 접근, 제한된 HTTP 전체 기한, 키 파일을 내보내지 않는 검증 서버 후보와 정책 제공 경로를 통합 검증한다. 새 APK/AAB를 서명·패키징하며 과거 산출물을 덮어쓰지 않는다. 합성 데이터와 대역만으로 실제 구매·운영 서버·ARM 실기기 검증을 완료 표시하지 않는다.

최대 5 cycle, 동일 원인 실패 3회 시 중단. 실제 아기 데이터와 금융·신원 자료를 시험/공유 패키지에 넣지 않는다. 다른 작업자의 수정과 저장소의 기존 OMX 상태를 보존한다.

## 실제 결과

| 검증 | 결과 | 근거 |
|---|---|---|
| Android 전체 | **144/144**, 실패·ignored 0, 88.77초 | [결과](../docs/qa/0.4.5/instrumentation-result.json), [로그](../docs/qa/0.4.5/instrumentation.txt) |
| 수정 후 focused UI | 3/3, exit 0 | [결과](../docs/qa/0.4.5/focused-ui-result.json) |
| Node HTTP/서명/Google 계약 | **55/55**, 실패·skip·cancel 0, Google 응답은 대역 | [로그](../docs/qa/0.4.5/server-tests.txt) |
| 배포 선행 조건 | 별도 **21/21**, fake gcloud, 실제 Cloud 작업 0 | [보고서](../tools/deploy/QA-preflight-20261001.json) |
| 실제 UI | normal/access/album/layout/paywall-layout/review-layout/transfer-e2e **7유형** | [결과](../docs/qa/0.4.5/ui-summary.json), [시각 검토](../docs/qa/0.4.5/visual-review.md) |
| 통합·서명 빌드 | debug/test/release/lint, 업로드 키 서명 AAB/APK 성공 | [빌드](../docs/qa/0.4.5/build-final.txt), [서명](../docs/qa/0.4.5/build-signed.txt) |
| Lint | 오류 0 / 경고 **15**, 억제·baseline 없음 | [개별 판단](../docs/qa/0.4.5/source-review.md) |
| 업데이트 | 실제 0.4.4/code11 기록 1건 → 0.4.5/code12, 모든 필드·schema1 유지 | [결과](../docs/qa/0.4.5/upgrade-result.json) |
| 서명 릴리스 실행 | 직접 입력·저장·기록·PNG 공유·미설정 심사 잠금 | [결과](../docs/qa/0.4.5/release-smoke-result.json) |
| 패키지 | APK/AAB 서명, ZIP/ELF 16KB 정렬, 백업/공유 경로, 모델/native 18파일 불변 | [결과](../docs/qa/0.4.5/package-signed-verdict.json) |
| 비밀/fixture 분리 | 실제 앱 debug/release APK와 AAB에 p12/pem/dpapi 및 TLS fixture 없음 | [결과](../docs/qa/0.4.5/fixture-exclusion.json) |
| 정리 | 합성 기록·앱/test 앱·소유 Downloads 제거, 해상도/글꼴 복구, daemon/에뮬레이터 종료, ADB 빈 목록 | [결과](../docs/qa/0.4.5/cleanup-result.json), [ADB](../docs/qa/0.4.5/adb-final-devices.txt) |

## 변경과 실패 진단

- 심사 코드를 검증 서비스로 확인하고 **별도 용도로 서명된 실제 심사 이용권**만 수락한다. 구매 서명과 심사 서명을 교차 수락하지 않는다. 설치 UUID에 묶고 최대24시간 캐시, 재부팅 후 온라인 확인, 거절/해제/만료/늦은 응답을 처리한다. 심사 코드는 Keystore AES-GCM으로 보관하고 화면 복원 상태에 넣지 않는다. 실제 운영 코드와 키는 저장소 밖에 생성했으며 앱/문서에 포함하지 않았다.
- 구매·심사 요청에 connect/read 제한뿐 아니라 전체 elapsed 기한과 취소 시 disconnect를 추가했다. HTTPS loopback의 느린 응답·멈춘 read·정상 완료를 실제 소켓으로 시험했다. 합성 TLS p12는 androidTest에만 포함한다.
- 서버는 metadata/IAM Credentials로 전용 계정의 Publisher 범위 토큰을 받고, 설치별/전체/동시 요청을 제한한다. 공유 프록시의 모든 사용자를 하나의 소켓 IP 한도로 묶지 않는다. UUID 회전 공격, 재시작/여러 revision, 대규모 DDoS의 한계는 유지한다.
- 정책 페이지는 정확한 GET/HEAD allowlist와 CSP로 제공한다. 파일 탐색·query·다른 method를 거부한다. 현재 HTML은 미게시 후보다.
- 배포 도구는 실제 billing/API/secret 버전/계정/repository/service 공개 IAM을 읽고 Node 시험을 통과해야 build를 시작한다. 기존 공개 서비스의 묵시적 재사용을 막고 전용 소스 버킷을 사용한다.

첫 전체144 시험에서 **실패1**이 있었다. 비활성 TextField에 `SetText` 의미 동작이 없어 시험 선택자가 필드를 찾지 못한 문제였다. 생산 enablement 조건을 바꾸지 않고 명시적 `testTag`로 같은 비활성 검증을 유지했다. focused3과 전체144 재실행이 통과했다. [첫 실패](../docs/qa/0.4.5/first-attempt-instrumentation.txt)와 [진단](../docs/qa/0.4.5/first-run-diagnosis.json)을 보존한다.

초기 공개 페이지 시험의 `/초안/` 정규식도 정상 서비스 문구 ‘표현 초안’을 미게시 문구로 잘못 분류했다. 미게시/약관 초안의 정확한 문구로 좁히고 focused와 최종55 시험을 통과했다. 두 사례는 시험 도구의 결함이며 제품 장애로 보고하지 않는다.

이전0.4.3의 초안 timeout 원인은 여전히 미확정이다. 이번 전체 시험의 통과를 그 원인 해결 증거로 소급하지 않는다.

## 시나리오 행렬과 안전 경계

상세 [계획/행렬](../docs/qa/0.4.5/PLAN.md)은 정상 사용, 변조·교차 서명·잘못된 코드·시간 조작·손상 캐시, 취소/재부팅/늦은 응답, slow body/upstream, 공유 프록시/몰림, 정책 경로침입, 실제 프로세스 종료와 복원을 포함한다. Android 서명·수명주기와 Node `/review`는 계층별 계약 시험이다. 운영 URL을 넣은 실제 앱과 운영 서버를 잇는 전체 경로 시험은 아직 수행하지 않았다.

핵심 명령은 Gradle debug/test/bundle/lint, `instrument.py --install-app --timeout 600`, Node `--test`, 배포 fake 하네스, 실제 UI7유형, `check_package.py`, 업로드 키 서명 도구다. 각 도구는 bounded timeout/exit와 실패 신호를 함께 확인했다. 세 번 같은 실패를 반복하거나 성공 문구만으로 pass하지 않았다.

## 산출물과 운영 상태

[파일별 SHA/크기](../docs/qa/0.4.5/artifacts.json)에 새 debug APK, 업로드 키 서명 release APK, 서명 internal AAB를 기록한다. **이 internal 빌드는 검증 URL/공개 키가 비어 있어 구매와 심사 접근이 비활성이다.** Play가 재서명해 배포한 APK가 아니다.

최신 사용자 ‘연결하자’ 요청에 따라 전용 `jigeum-play-20261001`의 Cloud 결제 연결을 완료하고 `billingEnabled=true`를 재조회했다. 연결에만 사용한 임시 프로젝트 역할은 제거해 재확인했다. Play 판매자 프로필 생성과 NH농협은행 지급 계좌 저장도 실제 Console에서 확인했고 계좌 인증은 소액 입금 확인 대기다. 이 외부 등록 진행은 위 fake 배포 시험의 ‘Cloud 작업0’과 별도 실행이다.

### 남은 실제 검증

- 운영 자원 준비/최소 IAM 재조회, Play 앱 한정 주문/구독 권한, Cloud Run 배포·컨테이너 실행·HTTPS smoke·실제 콜드 시작/부하 측정.
- 실제 URL/공개 키를 넣은 앱의 심사 접근 전체 경로, 실제 상품·구매/보류/취소/복원/환불/ACK, 재설치·계정 변경.
- 정책 HTTPS 게시와 실제 로그 보존/국외 처리 조건, Console 접근 안내·Data safety·스토어 자료/테스트 업로드.
- ARM/OEM/16KB 실기기, 실제 TalkBack와 부모 사용성, 개인 계정 비공개12명·14일, 공개 출시 제출.

월1만원 예산 알림과 최대1 인스턴스는 비용 차단선이 아니다. 키 없는 인증도 새 자격 증명 권한과 운영 검증의 필요성을 없애지 않는다. 금융·신원 원본과 등록 캡처는 QA 증거 패키지와 Git에서 제외한다.

**ULTRAQA COMPLETE: 로컬 통합/회귀/패키징 목표는 2 cycle에서 충족.** 실제 운영 연결·출시 완료를 뜻하지 않는다. 격리된 run의 상태만 정리하고 저장소의 기존 상태는 보존한다.
