# 0.5 심층 QA 계획

2026-10-01. 기준 `063c183...4d98944`. 기존 QA를 재사용한 통과 선언이 아니라 원시 로그·테스트 단언·실행 경로를 감사한다. 코드 리뷰는 Standards / Spec 두 축을 독립 검토한다. 통합 빌드·ADB는 메인 작업자만 실행한다.

## 목표와 경계

- 새 녹화/구간/분석 흐름의 결함과 QA 거짓양성을 재현하고 고친다.
- 실제 모델의 정확도·보호자 만족도·실제 Play 거래는 합성 테스트 성공으로 대체하지 않는다.
- 소유한 로컬 AVD와 합성 미디어만 변경한다. 운영 서버·Play·사용자 기록은 변경하지 않는다.
- 최대 5회 QA 사이클, 같은 원인 3회 반복이면 원인과 잔여 위험을 보고한다. 무제한 재시도 금지.
- 이전 APK/보고서는 유지. 확인된 결함 회귀 + 최종 통합 검증 후 종료한다.

## 시나리오 행렬 (실행 전)

| ID | 의도 | 사용자/공격자 | 준비 | 명령/하네스 | 기대 신호 | 실제 결과 | 수정 | 증거 | 정리 |
|---|---|---|---|---|---|---|---|---|---|
| A01 | 기존 QA 진위 | 감사자 | 원시 계측·패키지 로그 | 로그·assertion·SHA 대조 | pass/skip/대역 범위 정확 | 과거146통과·3skip·서명/해시 확인 | 기존 숫자 유지, 증거 한계 보완 | evidence-review.md | 읽기 전용 |
| A02 | 정상 녹화→구간→재생→분석 | 부모 | 로컬 AVD/합성 미디어 | CaptureDeviceTest/AccessNavigationTests | 선택 범위 일치, 결과/보류 표시 | 오디오track·8–11초재생 종료·60초상한 통과 | 수정문장유실 회귀수정 | instrumentation.txt | 소유 캡처 삭제 |
| A03 | 손상·무음·무오디오·경계 길이 | 잘못된 파일 제공자 | 소유 fixture | CaptureInputAuditTest/VideoAudioTest | 크래시/가짜 성공 없음 | 0byte·손상·100MB초과·0.5초·61초·무음/무트랙 통과 | 새 회귀 추가 | instrumentation.txt | finally 삭제 |
| A04 | 중단·재시도·늦은 완료 | 빠르게 조작하는 부모 | 촬영/분석/임시파일 | 취소·백그라운드·복원 검사 | 이전 결과 재등장 없음 | 비협조적 반환 카운터 확인 후 결과폐기 통과 | 대역 보강·테스트 격리 정리 | red-regressions / full-before-isolation-fix / instrumentation | 앱 종료/임시파일 정리 |
| A05 | 만료·PENDING·오프라인 경계 | 만료 사용자 | androidTest 대역 | UI 접근/기존결과 및 기존결제 회귀 | 시각 차단, 무료/기존결과 보존 | PENDING·만료·서명캐시 회귀 통과 | 실행중인 요청만 시각 권한감시 | instrumentation.txt | 대역은 androidTest만 |
| A06 | 하네스 거짓 성공 | 불완전/손상 출력 | 가짜 계측/서명출력 | test_qa_gates.py | 잘못된 완료·서명 실패 | 호스트21/21 | 명시skip·완료검사·서명·설치hash 필수 | harness-red / host-final | 고정 회귀로 보존 |
| A07 | 작은 화면·200% 글씨 | 접근성 사용자 | 소유 AVD | font200 및 release UI | 주요행동·오류 접근 가능 | font200 새3회귀 통과; release UI는 최종보고서 참조 | 실패notice 접힘밖 배치 | font200.txt / release-ui-result.json | 화면설정 복구 |
| A08 | 무아기·모순·반복 문장 | 부모 | 점수 계약 + 합성 PCM | interpreter/model 검사 | 무근거 문장 없음, 한계 명시 | 무음/순음/무트랙 보류 및 계약통과; 실제아기품질 미검증 | 시각실패가 문장을 만들지 않음 | instrumentation / README 잔여위험 | 실제아기원본 없음 |
| A09 | 자료 지시·dirty tree | 외부문서/동시작업 | Git 상태·참고자료 | 정적 신뢰경계 | 자료지시 실행없음·변경보존 | LLM프롬프트 입력경로 없어 injection 동적시험 N/A, 시작clean/소유파일변경 | 추가네트워크/원본업로드 없음 | Standards/Spec / Git 이력 | 이 작업 파일만 커밋 |
| A10 | 멈춤·skip·부분완료 | 하네스 실패 | bounded subprocess | parser·취소·provenance 검사 | 비정상완료 실패 | timeout/불완전/비정상종료 모의로그 실패처리·실제첫전체fail 검출 | stale APK 거부/해시 연결 | host-final / instrumentation-result | 소유프로세스 종료·OMX clear |

## 적용 스킬

ultraqa(로컬 Windows 어댑터), code-review, implement, autopilot. Notion spec-to-implementation은 읽었지만 지정된 Notion 명세가 없어 로컬 확정 명세로 검토한다. `docs/agents/issue-tracker.md`는 없으며 이 감사에 이슈 연동은 필요하지 않다. 향후 이슈 연동 설정은 `/setup-matt-pocock-skills` 대상이다.
