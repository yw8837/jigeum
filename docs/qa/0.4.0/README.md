# 0.4.0 로컬 QA 근거

2026-09-30 · API 35 x86_64 전용 에뮬레이터. [종합 보고서](../../../outputs/Jigeum-QA-0.4.0.md)를 먼저 읽는다.

- `instrumentation-result.json`, `instrumentation.log.txt`: 최종 APK 72개 통과.
- `build-final.log.txt`, `lint-summary.json`: debug/test/release/lint 빌드 결과.
- `billing-node-tests.log.txt`: Node 16개, Google 대역.
- `package-verdict.json`, `artifacts.json`: 서명·정렬·백업 제외와 배포 파일 SHA.
- `ui-summary.json`, `ui-results.jsonl`: UI 7유형, 실패 이력·최종 APK 구분.
- `cleanup-result.json`: 합성 데이터·시험 자원 정리.
- `staged-validation.json`: 원본 23개 Git 바이트, Python 문법, 문서 링크, 커밋 대상 비밀키 패턴 및 소스 공백 검사.

실제 Play 거래·ARM·운영 API·업로드 서명은 미검증이다. 화면 캡처와 원시 합성 자료는 로컬 QA ZIP에 보존하고 Git에는 요약/로그만 둔다. 로그의 로컬 절대 경로는 실행 당시 환경 정보다.
