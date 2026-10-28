# UltraQA Report — 표현 다양성·관찰 순서 0.5.15

2026-10-05 · 최종 소스 기준 `63d77a8` · versionCode29 · 비공개 beta

## Goal and success criteria

같은 관심 요청에 몰리는 출력, 모든 울음의 의성어 시작, 서로 다른 사건 순서를 동일하게
출력하는 문제를 개선한다. [계약](../../UX-EXPRESSION-0.5.15.md)을 따른다.
소리 승인·보류 기준과 모델은 변경하지 않으며, 원음은 로컬에서만 처리한다.
새 욕구 정확도·인식률 개선을 주장하지 않는다. 기존 전체 접근 beta/정식 release 권한을 유지한다.
현재 완료된 Android 검사와 최종 서명 검증을 구분하고, Play 업로드·검토·게시도 별도로 기록한다.

## Failures found / Fixes applied

- 실제 기존 interpreter에 새6검사를 실행하니3개 실패: 울음3문장뿐, broad옹알이3문장뿐,
  **여섯 서로 다른 사건 순서가 같은 대사**였다. 관찰이 허용하는 놀이·흉내·인사·달래기 표현을 넓혔다.
- `BabyExpression`에 표현 책임을 모았다. 각 기본 사건8개, 관찰된두사건순서10방향의
  표현을 제공한다. 표면 변형키는 욕구 선택에 사용하지 않으며 기존 결정성을 유지한다.
- 관찰순서에는 원본시간열의 중복/역순/범위밖/중간탈락을 검사하고, 연속두묶음 각각
  고유관찰2개 이상·900ms이상 지지가 필요하다. 최대허용 관찰간격1500ms다.
  세묶음이상·교대/희박/충돌·넓은AST는 일반표현을 유지한다.
- Spec 리뷰가 `CHILD_VOICE`를 `BABBLE`로 바꿔 말하는P2를 발견했다. 정확한말소리
  사건을 보존하도록 수정하고4방향·3묶음 회귀를 추가했다.
- Standards 리뷰의 경계관찰(중복충돌창이 승인목록에서 사라짐)도 보완했다.
  원본시간열을 확인하고, 충돌관찰이 빠져있는 순서 문장은 생성하지 않는다.
- 긴표현의 실제지속/쉼 검사, broadAST의 정확시점 미확인, 무음/성인/충돌 보류는 유지한다.

## Scenario matrix

| ID | 의도 / 준비 | 실행 | 기대 | 실제 결과·증거 | 정리 |
|---|---|---|---|---|---|
| E1 | 같은사건 다른wordingKey/극단Int | ExpressionVariety/EvidenceInterpreter | 표현편중감소·결정성 | Android최종52내 통과 | 테스트내정리 |
| E2 | 시간순사건10방향/3묶음 | ExpressionVariety/LongExpression | 관찰종류·순서 보존 | 최종통과,말소리오류수정후 | 테스트내정리 |
| E3 | 중복/역순/범위밖/희박/교대/중간충돌/넓은AST | 실제interpreter | 순서/쉼 날조없음 | 최종통과 | 테스트내정리 |
| E4 | 성인·잡음·무음·혼합·오류 | SoundTimeline/BabbleSupport 등 | 대사보류유지 | 최종통과 | 테스트내정리 |
| E5 | 소리에무음/성인덧붙임 | PaddingExpression/LongExpression PCM검사 | 길이·쉼날조없음 | 최종통과 | PCM zeroing |
| E6 | 알려진약국10/20초,실제UI/취소/접근 | AstIntegrated5/UI1,접근21 | 기존회복·출처·취소유지 | Android27 통과 | 복사미디어/격리앱정리 |
| E7 | 저장·앨범·내보내기,서명28→29 | media46+signedupgrade | 저장/기록영상보존 | 계측46통과,서명단계별도아래 | 전용AVD |
| E8 | 실제beta/release | 서명·payload·dex/lint | beta전체/release검사 | 최종패키지단계별도아래 | 전용산출물 |

관찰합성 입력은 표현계약 검증이다. 테스트125개를125아기의 정확도 평가로 해석하지 않는다.
기존약국은 이미회복된 개발·회귀표본이다. 새독립실패를 고친 근거가 아니다.

## Commands run / evidence

ignored `work/expression-20261005/`에 로그를 보존한다.

- `host.py baseline`: exit1,6검사중3실패(위의실제표현결함). compile exit0.
- `host.py review-fixed ExpressionVarietyTest,LongExpressionTest,PaddingExpressionRegressionTest`:
  compile/test exit0,**21통과**. Kotlin원문과기존정책을실제컴파일,Android모델실행아님.
- `build_debug.py`: 최종debug/test build exit0,33.25초,600초/12GiB 경계,잔여자손0.
  기존로그 덮어쓰기를 막는 FileExistsError는 하네스초기화실패로 별도처리하고 새로그명으로실행했다.
- `run_tests.py android-expression-final ...`: **52통과,실패/제외0**,exit0.
- `integrated.py android-integration-final integration,ui-real,ui-access,media`:
  **5+1+21+46=73통과,실패/제외0**,exit0,격리앱/복사미디어정리,production snapshot동일.
- Android최종 **고유125검사**. 이전수정전50+73실행은최종집계에중복포함하지않는다.
- 예비서명빌드는리뷰P2수정을위해본인이시작한process를중지했다. 예비APK/AAB는배포대상이아니다.
  최종서명파일은 `work/expression-20261005/final/`만 사용한다.

### 최종 서명·업데이트·게시

최종 beta/release 빌드·서명·manifest·AST payload·실제 BuildConfig dex 검사를 통과했다.
각 Lint는 **오류0/경고10**이며, 이전 code28과 경고 ID·심각도·메시지가 모두 동일하다.
서명28→29 업데이트에서 기존 문장 기록·MP4 bytes·beta 전체 접근을 보존했다.
최종 서명 beta의 알려진 약국0–10초 실제 UI도 통과했다. broad AST 관찰과 새로운 표현이
표시됐고, 선택 구간·표현 출처가 유지됐다. 에뮬레이터 UI 대기는11.139초였다.
실제폰 속도나 새 인식 회복으로 해석하지 않는다.

검증된 최종 beta 파일:

- AAB `work/expression-20261005/final/Agattok-0.5.15-beta-code29.aab`
  SHA256 `7ce403424605e8fe0140568ca0bf867227313c25d145493d55b7966d5c8ad172`.
- APK SHA256 `187389520ba197b0730c3297c556d11490257da4a68c940ffe22a2e312b1c8bb`.
- 정식 정책 비교용 release는 Play에 제출하지 않는다.

직접 조회한 Play Alpha에서는 **0.5.14-beta/code28이10월5일16:22게시,선택한테스터에게제공** 중이었다.
**21:02경 새0.5.15-beta/code29 검토 제출을 완료했다.** 게시 개요의 `검토 중인 변경사항`에
code29가 표시되며,Google 빠른 검사17% 상태를 확인했다. 관리형 게시off다.
아직 Google 최종 승인·새 버전 게시·테스터 설치 완료로 보고하지 않는다.
지원 제외 기기0,새 설치표시112MB,업데이트2.52MB,차단오류0이다.
경고2개는 가독화 파일·네이티브 디버그 기호 권고이며 이전과 같다.
제출 화면은 ignored `work/expression-20261005/play-submitted.jpg`다. 같은code29를중복업로드하지않는다.

## Standards

독립Sol high가 `94f0011...0759d58`,수정 `0759d58..63d77a8`을읽기전용검토했다.
문서규칙위반/수정필수smell0. 원본충돌창의경계관찰은보완했고재검토에서추가결함0.
검토자는Android/성능을직접실행했다고주장하지않았다.

## Spec

별도Sol high가같은고정diff와계약을검토했다. 말소리→옹알이치환P2한건을수정했다.
재검토에서해소확인,미해결0. 원인판별/일반정확도는이번표현변경의검증범위가아니다.

## Residual risks / 후속 인식 개선

- 표현은 authored character phrase이다. 문장폭확대/관찰순서반영을LLM통합이나욕구판별로보고하지않는다.
- [배고픔·졸림·놀이근거조사](../../research/20261005-infant-needs-expression-evidence.md):
  독립아기·월령·원음분리·라벨/권리부족을검토했다. 높은논문숫자를이앱의정확도로쓰지않는다.
- [EfficientSED모바일export](../../research/20261005-efficientsed-mobile-export.md):
  20.3MB float ONNX의합성CPU출력일치까지성공. Android동일mel/실기기/새실패·오인평가는남았다.
  제품모델은기존YAMNet+AST다. 이번변경만으로새옹알이인식률이올랐다고보고하지않는다.
- 실제S25가연결되지않아새서명버전실폰조작·발열·Play설치완료는미검증이다.

## Cleanup and rollback

전용 `AgattokExpression29`만사용한다. 기존 `MoaQA35`와사용자폰은변경하지않는다.
단계별 임시미디어/계측앱과 최종 설치앱을 제거하고 에뮬레이터를 종료했다.
AVD 등록은 avdmanager로 제거했지만, 종료 직후 잠긴 일부 캐시가 남았다.
**자동 승인 검토가 `C:/MoaAVD/AgattokExpression29.avd`의 재귀 삭제를 차단했다.**
다른 삭제 도구나 우회 경로로 지우지 않았다. 이 폴더의 수동 정리가 남아 있다.
초기 복합 정리 명령도 실행 전에 차단돼 어떤 파일도 삭제하지 않았다.
검증 전 예비패키지 역시 배포하지 않았으며,검증 완료 산출물은 `final/`만사용한다.
격리 UltraQA 상태는 active=false/blocked를 기록한 뒤 clear하고 부재를 확인했다.
`cleanup-result.json`이 제품검사 통과와 잔여캐시를 따로 기록한다.
작업시작tree는clean이었고,다른조사작업자의담당파일을보존했다.

**ULTRAQA BLOCKED: 임시 AVD 캐시 삭제가 자동 승인 검토에 의해 거부됨.**
제품·서명·업데이트 검증은2번째 사이클에서 통과했다. 전체 정리까지 완료됐다는 선언은 하지 않는다.
남은 정리 소유자는 사용자이며 위의 이번 작업 전용 폴더만 수동 제거하면 된다.
