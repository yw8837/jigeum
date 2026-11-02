# UltraQA Report — 0.5.14 분석 창 경계와 실패 화면

2026-10-05. 기준 `9617daea5f2c286cbfa9b128dd48b58ca27d5885`.
사용자 목표는 안내의 길이가 아니라 실제 발성 누락 개선이다. [구현 계약](../../UX-SOUND-PHASE-0.5.14.md)을 따른다.

## Goal and success criteria

- 같은 2초 공개 울음의 위치 때문에 달라지던 판정을 실제 모델에서 개선한다.
- 기존 옹알이·웃음·울음과 비아기 대조 판정, 원본·기록·이용 정책을 보존한다.
- 실패 안내는 한두 문장으로 줄이고 세부 시각 관찰은 접는다.
- 외부 전송·S25 제품 앱 변경·공개 출시 없이 격리 Android, 서명 업데이트, 비공개 beta를 검증한다.
- 최대5회/동일실패3회 제한. 이번에는 manifest 하네스 결함1회 수정 후 새 전체 실행을 했다.

## 실제 인식 변화

고정 YAMNet/AST를 이번 Android에서 실행한37조건이다. AST는 각 입력에서 새로 실행하고,
동일 PCM의 기본 결과→기존 AST와 기본→기존 AST→추가 창을 비교했다.

| 입력 | 기존 | 수정 후보 |
|---|---|---|
| 같은2초 울음을20초의 맨앞에 배치 | 보류 | 울음 |
| 같은2초 울음 단독 | 보류 | 울음 |
| 같은2초 울음을9초·18초에 배치 | 울음 | 울음 유지 |
| 기존 공개·합성33조건 | 승인12/보류21 | 판정 동일 |
| 알려진 약국 영상10/20초 | 옹알이 | 옹알이 유지 |

위치4조건은 **한 원음의 파생 조건**이다. 독립 아기4명의 정확도나 새로운 옹알이 회복4건이 아니다.
33개는 기존 개발·회귀 표본이다. 새 오인이 이 묶음에서 관찰되지 않았다는 근거이며
일반 인식률·욕구 해석 정확도를 입증하지 않는다. 사용자 최신 실패 입력은 아직 특정되지 않았다.

## Scenario matrix

| ID | 의도·입력/사용자 모델 | 실행·기대 | 실제 결과/수정 | 증거 | 정리 |
|---|---|---|---|---|---|
| POLICY | 단발·중복창·반대단서·성인소리·AST 승인 | 실제Kotlin 정책, 기존승인보존·중복승인금지 | host14, Android14 통과 | policy-green-03, android-policy-ui | 합성만 |
| CORPUS | 전체37조건, 골라내지 않은 전후비교 | 실제 Android YAMNet/AST·입력SHA·시간순서 | 신규회복2조건, 기존33판정유지 | android-corpus-02 | 별도패키지 제거 |
| CONTRACT | 새4조건 manifest 변조/누락 | 손상된 계약은 실패, 통과로 숨기지 않음 | 첫 실행sha누락으로 실패, 작성기수정후37전체통과 | android-corpus, android-corpus-02 | 실패원인보존 |
| DEVICE | 약국10/20초·tone/click·모델오류 | 실제 DeviceClipAnalyzer·전체범위·원본불변 | 5통과, 약국6.884/9.987초 | android-integration | 원본복사 제거 |
| CANCEL | 실제20초 중 취소·즉시 다음분석·native취소 | 늦은 결과 무시·락/분석 복구 | UI1+native2통과, 메인응답0ms 표본 | android-integration | 앱/PID정리 |
| TIMELINE | 시간순 영상20/40장·범위 | 전체10/20초표본범위, 잘못된timestamp 거부 | 13통과 | android-integration | 합성만 |
| UI | 실패상세접기·조작·저장/복원 | 결과없는저장금지·수동구간변경 | UI3 포함 android-policy-ui31통과 | android-policy-ui | test 대역은androidTest |
| SIGNED | 서명·권한·기록/MP4 업데이트·작은화면200% | 실제 최종APK 검증 | 모두 통과, 기존기록/미디어bytes보존 | signed-* / final-package-result | 전용AVD정리완료 |
| PLAY | 실제 비공개Alpha 제출 | beta AAB만 제출·상태구분 | code28 제출, Google자동검사중 | play-submitted.png | 공개출시제외 |

## Commands run

증거 루트는 ignored `work/sound-phase-20261005/`다. 개별 점수·미디어·APK·원본은 Git에 넣지 않는다.

- `host_policy.py policy-green-03`: 실제 Kotlin/JUnit14통과. compiler90초/test30초 제한.
- `host_policy.py policy-corpus-03 --replay`: 새 CPU YAMNet과 기존 AST 기록의37조건 정책 replay.
  이 host replay를 새 Android AST 실측으로 세지 않았다.
- `assembleDebug assembleDebugAndroidTest -PagattokAstProbe=true -PagattokAstCandidate=true`: exit0.
- `run_tests.py android-policy-ui SoundPhaseRecoveryTest,SoundTimelineTest,ExpressionBasisUiTest,AudioPresenceRegressionTest`:31통과/실패0/제외0.
- `run_corpus.py android-corpus-02`: 계측1개 안37조건, exit0/82.95초, 최대600초.
- `monitored_integration.py`: 실제 통합5/UI1/native2/시간축13, exit0/제외0. 전체550초 제한.
- Android 고유계측 합계53개.37조건을37개 JUnit으로 중복 합산하지 않는다.
- 계측 APK는 별도 `.astcandidate`이며 버전 증가 직전 debug code27이다. 이후 동일 제품 소스를
  code28로 서명해 패키지·실제 업그레이드·실패 화면2조건·약국 UI를 별도로 실행했다.
- `build_final.py final-build`: beta/release 서명·lint exit0, 328.064초. 종료 시 남은 빌드 자식은
  bounded runner가 정리해 remaining0을 확인했다. package 검사는 실제 고정 파일에서 수행했다.
- `verify_final.py`: beta/release APK+AAB 서명·정책·실제 dex flag·AST payload 통과.
  beta AST/전체테스트 true, preview/candidate false. release 네 테스트/AST flag false.
- `run_ui.py upgrade`: 실제27→28 업데이트, 220.38초/exit0. 기록·MP4 bytes·LEGACY·beta접근 보존.
- `failure_ui.py`: 최종서명본/실제합성tone, 작은화면·200%글씨에서 안내와접기·펼치기 통과, 설정복원.
- `known_ui.py`: 최종서명beta의 약국0–10초가 실제옹알이. UI대기8.358초. 원본SHA불변·사본제거.

## Package and deployment

- beta AAB: `01815b2f1cea733bbae9fe4f4e45cde2ec402c77bf375b481a32a47d07605165`.
- beta APK: `a1489d7183bc329d03bab111d560ea8edbcfa01a858146de2158798fb1032d9b`.
- Lint는 두variant 각각 오류0/기존경고10. 이번 수정으로새경고는 추가되지 않았다.
- 2026-10-05 code28을 실제 Alpha에 업로드하고 `검토를 위해 변경사항 전송`을 완료했다.
  `검토 중인 변경사항`에 **0.5.14-beta(28)**가 나타났고 Google 빠른자동검사는 아직 진행 중이다.
  자동검사·최종승인·테스터제공은 미완료이며, 제출을 게시 완료로 보고하지 않는다.
- 관리형게시off로 승인후자동게시. 이번에지원제외된기기0. Play 신규설치표시112MB, 이전대비2.13KB증가.
- 제출화면의경고2건은 가독화파일/네이티브디버그기호 권고다. 차단오류는 없었다.
  트랙개요의기존Fragment1.1.0·edge-to-edge 유지보수권고는 별도잔여항목이며 제거했다고보고하지 않는다.
- code27은10월5일04:05에실제게시됐고 `선택한 테스터에게 제공됩니다`를 확인했다.
  공개프로덕션·유료상품·서버설정은 변경하지 않았다. 비교용release는 업로드하지 않았다.

## Failures found / Fixes applied

1. 975ms 창의 위치 차이로 발성을 놓쳤다. 기존 판정이 미확정이고 영아identity floor를 한 번도
   넘지 않았을 때만487.5ms 옮긴 독립 시간열로 재관찰한다. 두 시간열을 합쳐 통과시키지 않는다.
2. 초기 후보는 AST 이전에 새판정을 선택할 수 있었다. 기본→AST→미확정일때추가창 순으로 수정했다.
3. 추가모델 실행오류가 정상기본결과를 지우던 초기 후보를 수정했다. 정상예외는 기본결과 유지,
   취소는 전파한다. 이를 실제 Kotlin과Android 합성경계에서 검사했다.
4. 배경점수를 음량으로 단정하고 관찰없는 웃음을 안내하던 문구를 수정했다.
5.37조건 하네스의 신규4조건에pcm_sha256이 빠졌다. 앱결함으로 세지 않고 계약실패를 보존,
   작성기preflight를 보완한 뒤 새run-id로 전체37조건을 다시 실행했다.

## Code review

### Standards

초기P2문구1건 수정 후 미해결0. 자원정리와 독립시간열, 관찰표현을 검토했다.

### Spec

초기P2순서/오류fallback/배경문구3건 수정 후 미해결0.
AST 시간이 제외된 corpus의 `phase_ms_excluding_ast`를 전체분석시간으로 보고하지 않는다.

## Performance and limits

격리x86_64 에뮬레이터 `AgattokPhase28`에서 약국 전체AV10/20초는6.884/9.987초.
0.8초 이상간격의PSS표본 최대573,124KiB(약560MiB), 메인응답표본0ms였다.
YAMNet classifier를 유지한 채AST가 실행되는 변경을 포함한 실제제품 경로다.
S25 실폰의 속도/발열 측정이 아니며 폰전체 성능보장은 아니다.
AST 뒤의추가 YAMNet은 조건부이며 큰 생성모델이나 새다운로드는 추가하지 않았다.

## Cleanup and rollback

후보패키지와후보test앱·PID는 통합하네스가 제거하고기존제품미설치상태와일치를확인했다.
최종서명QA의원본사본·표시설정을복원하고, 해당AVD의시험제품앱을제거한뒤 emulator를종료했다.
초기복합파일삭제명령은자동정책에서실행전거부됐다. 정확한AVD경로·일반디렉터리를먼저확인하고
공식 `avdmanager delete avd -n AgattokPhase28`로정리해 `.avd`/`.ini`와serial부재를확인했다.
격리QA state를complete기록→실제read확인→clear true→exists false까지검증했다.
기존사용자폰과 `MoaQA35`는 변경하지 않았다. 재현용로그·서명기준본은ignored work에보존한다.

## Status

**ULTRAQA COMPLETE: Goal met after 2 cycles.**

여기서완료는위에정한제품코드/로컬QA/검토제출/정리범위다. Google자동검사·최종승인과
일반아기인식정확도까지완료됐다는뜻은아니다. 최신실패원본·구간은계속확인필요하다.
