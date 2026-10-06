# UltraQA Report — 0.5.18 소리·영상 시간 연결

기준 `737c0a8`(0.5.17/code31). 실행 중이며 완료 전 결과를 완료로 보고하지 않는다.

## 목표·성공 기준·안전 경계

선택 구간의 소리와 시각 관찰을 같은 시간창에서 연결하고 여러 소리 단계에도 반영한다.
시간이 어긋난 표정·누락·오인·비정상 timestamp로 시각 근거를 만들지 않는다.
소리 게이트/모델/음성 원본·필터/500ms 시각 cadence/이용권 정책은 유지한다.
최대 QA5회/동일 실패3회에서 중단하며 아기 자료 외부 전송·공개 출시·상품·거래는 금지한다.
부모만 Gradle/ADB를 사용한다. 재사용 QA AVD만 대상으로 하고 사용자폰·다른 작업은 보존한다.

## 시나리오 행렬

| ID | 의도 | 사용자/공격 모델 | Setup | 명령/하네스 | 기대 | 실제 | 수정 | 증거 | 정리 |
|---|---|---|---|---|---|---|---|---|---|
| V1 | 소리와 표정 시점 | 앞소리/뒤표정 | 실제 정책·합성 관찰 | VisualTemporalAlignmentTest | 다른 시간표정 제외 | 최초5중4실패→관련28통과 | 시간창 연결 | baseline-red/fixed-green | host 격리 |
| V2 | 여러 소리 흐름 | 옹알이→웃음 | 실제 interpreter | 같은 검사 | 각각 연결·소리 보존 | 대기 | 다중단계 연결 | 대기 | fixture 없음 |
| V3 | 비정상 입력 | 중복/역순/누락PTS/NaN/다중얼굴 | 소유 관찰 | 순수/Android 검사 | 근거 없음 | 대기 | timestamp 재검증 | 대기 | 앱 정리 |
| V4 | 누락/간격/무음 | 화면밖·성인·AST broad | 소유 관찰 | 같은 검사 | 억지 발성/립싱크 없음 | 대기 | local run | 대기 | 앱 정리 |
| V5 | 실제 영상/취소 | 10/20초·nonzero·crop/회전 | 실제 codec/MediaPipe | VideoTimeline/VisualFeatures/Audit/Color | 전체 관찰·취소재시도 | 대기 | decoder 유지 | 대기 | fixture/bitmap/codec 정리 |
| V6 | 통합·UX/기록 | 이전 기록/MP4·새구간·취소 | 실제 앱/서명APK | integrated/signed-upgrade/known_ui | 보존·정상 분석 | 대기 | 분석만 변경 | 대기 | 복사본/앱 제거 |
| V7 | 빌드/권한/거짓 성공 | wrong APK/skip/timeout | 소유 하네스 | bounded build/parser/패키지 | 실패는 차단·beta/release 분리 | 대기 | 버전32 | 대기 | 자식프로세스0 |

프롬프트 주입은 새 실행형/LLM 입력이 없는 범위다. 실제 데이터·타임스탬프·상태 경계를 검증한다.
작업 전 tree clean/HEAD737c0a8. 기존 문서/원음/다른 작업자의 변경을 보존한다.
