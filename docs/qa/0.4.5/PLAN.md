# 0.4.5 출시 준비 통합 검증 계획

2026-10-01 · 로컬 통합 QA 완료. 실제 운영 연결과 시험은 별도 gate다.

## 목표와 경계

기존 0.4.4 무료/전체 이용권과 PR #2를 유지하며, 구매 없는 Play 심사 접근, keyless 검증 서버 배포 후보, 정책 HTTPS 제공 후보를 구현·통합·빌드·QA·패키징한다. 기존 산출물은 덮어쓰지 않는다. 실제 Play 거래·ARM·운영 배포·12명 14일 테스트를 로컬 대역 검증으로 대신하지 않는다. Cloud billing 연결/유료배포/Play 신규 권한·업로드는 구체적 승인과 실제 결과가 필요하다.

root는 Gradle/ADB/패키지/정책·등록 담당, review_access는 Android 심사 접근, deploy_package는 서버/배포 도구를 담당한다. 다른 사람의 변경을 되돌리지 않는다.

## 시나리오 행렬

| ID | 의도·사용자/공격자 | 설정·실행 | 기대 신호 | 실제 결과·수정·근거 | 정리 |
|---|---|---|---|---|---|
| R1 | 심사자 정상 재사용 | 서명된 /review 대역, 새 설치 UUID·앱 재시작 | 실제 서명으로만 카메라/영상/PDF, 새설치도 검증 | Android 서명/수명주기와 Node /review 계층별 PASS. 운영 URL의 실제 앱 전체 통신은 대기. | 합성 대역·키 폐기 |
| R2 | 잘못된 코드·위조·교차 grants | Node/Android 계측 | 빈설정/HTTP/변조/구매↔심사 교차 수락 거부 | Android/Node 위조·잘못된 코드·교차 grant 거절 PASS. | 비밀 테스트값 메모리만 |
| R3 | 해제·만료·revocation·늦은 callback | 계측 유료 중단·초안 보존 | 늦은 응답은 권한을 복구하지 않고 무료 초안/기존기록 유지 | 해제/만료/늦은 callback·무료 초안 유지 계측 PASS. | app reset |
| R4 | clock rollback·재부팅·캐시·Keystore 손상 | 계측 합성 anchor | 24h 경과·boot binding 및 온라인 재확인 | clock/boot/ciphertext·키 손상 거절 계측 PASS. | 합성 prefs/키 삭제 |
| S1 | keyless 정상·Google 오류 | metadata/IAM 대역 | scopes, single flight, timeout, redirect 거부 | metadata/IAM Google 대역 계약 PASS; 실제 토큰 발급/Play 권한은 대기. | loopback 종료 |
| S2 | 공유proxy·쏠림·몰림 | 여러 UUID·burst·global/concurrency 대역 | 한 proxy30/min 병목 제거, bounded429/503; 공격 UUID 회전 한계 명시 | UUID/shared proxy/global/concurrency Node HTTP PASS. 공격 회전/여러 revision 한계 유지. | 소켓 종료 |
| S3 | malformed/Unicode/oversize/slow body·hung upstream | Node HTTP 테스트 | 제한/timeout, 비밀 미로그, 요청 해제 | slow body/upstream/oversize/cancel 및 실제 HTTPS transport 계측 PASS. | timeout·process 확인 |
| P1 | 공개 정책 정상·경로침입 | GET/HEAD/POST/query/encoded path | 인증없이 정확한 allowlist, 다른 파일 접근 금지, CSP | allowlist/CSP/method/path/query Node HTTP PASS. 실제 게시 대기. | loopback 종료 |
| A1 | 기존 기능 회귀 | 전체 Android+서버 시험, 무료UI5종 | 테스트 수·skip·exit 확인; 과거123/43 재사용금지 | Android144/Node55/UI7/업데이트/서명 릴리스 실행 PASS. | 시험상태·기기복구 |
| A2 | 심사 UI 접근성·200%글꼴·동선 | emulator 실제UI·스크린샷 | 입력·닫기·무료복귀 접근, 큰글자 겹침없음 | small/200%font/wide 실제 UI와 이미지 검토 PASS. | 글꼴·해상도복구 |
| B1 | 통합빌드·lint·패키지·서명 | Gradle debug/test/release/lint + check_package + jarsigner | 신규 version/code, native/models 유지, 실제로그 | 통합/서명 빌드·lint0/15·패키지·fixture 제외/native18 유지 PASS. | Gradle stop |
| E1 | 오해되는 성공·실제 운영 | Cloud/Play read-only 현황 | 200health≠Play검증; config없음≠전체완료; 외부 gate 별도 | billing=true/임시 연결 역할 제거/판매자·은행 저장 재확인. 실제 구매/ARM/서버는 미완료. | 임시토큰 미보관 |

최대 5 QA cycle. 동일 원인의 실패 3회 시 원인과 안전한 다음 단계를 기록한다. 테스트 도구의 설정 실패는 제품 버그와 분리한다. dirty work·기존 OMX 상태를 보존한다. 모든 실행은 bounded timeout, 실패·skip와 환경을 함께 기록한다.
