# 0.4.5 통합 QA와 패키징

**최신 결과:** [운영 연결 후 QA](connected/README.md). 최종 Android142/2skip/0fail·Node56·실제 앱10·HTTPS9·패키징을 별도 실행했다. 아래는 미설정 로컬 체크포인트다.

2026-10-01 · Android0.4.5/code12 · 전용 API35 x86_64 · root 단독 Gradle/ADB.

[전체 보고서](../../../outputs/Jigeum-QA-0.4.5.md) · [독립 검토](source-review.md) · [판정 JSON](review-verdicts.json) · [산출물](artifacts.json).

Android144/144, Node55/55, 배포 fake21/21, 실제UI7유형, 0.4.4→0.4.5 업데이트와 upload-signed release 실행, 서명/ZIP/ELF16KB/native 모델18개 보존을 확인했다. Lint0오류/15검토경고다. 첫 전체 시험의 실패1과 교정 후 focused3/전체144를 별도로 보존한다.

현재 internal AAB/APK는 검증 URL/공개 키 미설정으로 구매와 심사 접근이 비활성이다. 실제Play/ARM/운영 서버/정책 게시/비공개 테스트/공개 제출은 이 QA 통과와 구분한다. 최신 Cloud 결제·판매자·지급 계좌 상태는 [등록 진행](../../release/20261001/CONNECTION-PROGRESS.md)을 따른다.

Git에는 합성 시험 로그와 요약만 보존한다. 실제 키·코드·구매 token·금융/신원 자료와 등록 캡처를 포함하지 않는다. 로컬 QA ZIP에도 계좌/판매자 캡처와 비밀 경로를 넣지 않는다.
