# UltraQA Report — 발성 존재와 종류 불확실성

## Goal and success criteria

[계약](../research/20261010-vocal-kind-contract.md), 기준 e16f448. **code38 미제출, 성능 gate 미해결**.
같은 시간창에서 corroborated infant presence가 있는 울음/웃음 종류 불확실성이 전체 결과를
막는 현상을 개선한다. 최대5 cycle/동일실패3회 중단. 실제정답·욕구 정확도로 환산하지 않는다.

## Scenario matrix

|ID|의도·모델|준비·명령|기대 신호|실측·수정|근거·정리|
|---|---|---|---|---|---|
|K1|현재 전체보류 재현|VocalKindEvidenceTest 실제보관 score|baseline red, 수정후 중립결과|red1실패→최종host59통과|host/원천SHA|
|K2|고점수 성인/TV/비명·종류/시점혼동|동일 테스트|잘못된 근거로 회복0|반례통과·기존threshold유지|synthetic 반례|
|K3|기존성과·배경/성인대조|84정책 replay/53 실제 엔진|기존결과보존·대조새출력0|38→39/28→29, 기존출력exact·대조추가0|보관자료/동일원본변형 포함|
|K4|NaN/결측/창순서·취소·native오류|host+Android 경로|오류/취소정상전달|Android104통과·skip0|bounded exit/skip 확인|
|K5|실제 사용자 흐름|서명 baseline→candidate 동일 공개음성|보류→중립결과·저장가능|초회38 timeout, 동일파일후속2회결과·저장통과, 원인미확정|37→38기록·MP4보존통과|
|K6|정식 권한·패키지|beta/release package+UI|접근정책유지|패키지·실제AV/저장/내보내기·정식재잠금통과|Play미제출|
|K7|원천/작업트리·용량|시작clean/전용폴더·기존AVD재사용|원천보존·불필요증가억제|31패키지삭제·여러대상정책차단|차단된삭제 우회금지|

## Commands run / Failures found / Fixes applied

cycle1: 실제 보관 mayra_context의 현재 소스 재현은 MIXED_VOCAL, host red01 1실패/exit1이었다.
첫 구현 뒤58검사 중1실패는 기존 성공 조건을 부분결과로 기대한 테스트 오류였고, 기존 성공 보존
단언을 유지하며 실제 미해결 반대종류 조건을 추가했다. 정책 replay candidate01에서는 기존
laugh_babble의 좁은 부분근거가 넓은 부분근거로 바뀌어, 원래 부분결과가 있으면 우선 보존하도록
제품을 수정했다. 이 실패 기록을 보관했다.

cycle2: source76b5f3d, host green03 **59통과/0실패**, replay candidate02 **84조건 중83 exact**,
기존38출력의 문장·근거 모두 보존, mayra_context만 일부시간창 결과 추가(38→39).
연구 Android 최종 build02 exit0, **104검사/104통과/skip0**, 취소·native실패복구 포함.
실제53재추론 **28→29출력**, 기존28출력·원천 PCM·AST·frame exact, 대조 신규0이다.
새 연구 빌드 폴더를 만들지 않고 기존
work/interval-evidence-20261009/build를 재사용했다. 첫 에뮬레이터 실행은 오래된 스냅샷의
WHPX 상태 복원 오류로 실패했고, snapshot load 없이 동일 MoaQA35를 정상 부팅했다.
원래 app 설치가 없던 소유 QA기기에만 연구앱을 설치했다. 연결된 사용자폰은 수정하지 않았다.
기존 삭제 차단3개는 유지하며 우회하지 않는다.

beta/release38 서명·버전·접근flags·실제모델·200ms cadence 패키지 검사는 통과했다.
서명37→38 업데이트에서 합성기록과10초저장MP4 해시를 보존했다.
초회동일20초 AAC 검사는37 보류를 확인했지만38에서 앱45초 timeout으로 실패했다.
`signed-initial-timeout.json`을 보존하며 제품 성공으로 집계하지 않는다.

cycle2 진단: 같은SHA256 MP4로 기존 단계별 연구 probe를 실행했다. AV1 **19.689초**,
audio **8.748초**, AV2 **6.114초**. AST 단계15.683/7.238/4.299초,
영상단계1.504/0.895초다. 이 probe는 infant presence attach를 포함한 제품경로 전체와
다르므로 서명판 성공이나 제품처리시간의 대체 근거로 사용하지 않는다.
관측 시 PC여유RAM 약0.47GiB였다. 부하/워밍업 차이를 시사하나 timeout 원인을 확정하지 않는다.

후속 서명38에서 최초실패와 **동일SHA256·같은0–20초선택·시각ON**으로2회 cold app 시작 검사:
둘 다 부분발성결과가 나왔고 두번째 결과저장도 통과했다. 0–10초의넓은근거와 나머지종류보류를
확인했다. UI는25.296/25.247초 안에 관찰됐지만23초대기후읽었으므로 실제처리시간 수치가 아니다.
이때 반복UiAutomator동작을 줄였고 원천변경은 없다. 최초 실패와의 제어변수 차이로 인해
재시도성공만으로 timeout 해결이나 새성능개선을 선언하지 않는다.

MP4 재생성의첫시도는컨테이너SHA불일치로 제품실행전에중단했다. FFmpeg 동일명령이
140042/140062B로달라질수있음을 확인했고, 최대3번재생성중 기존SHA와일치한파일만
시험에사용했다(실제후속run은첫생성일치). 실패를없애기위한제품재시도가아닌입력동일성검사다.
임시MP4/기기복사본을제거하고원천PCM해시보존을각실행finally에서확인했다.

마지막서명smoke는exit0/196.21초, beta38실제10초AV결과·저장·MP4앨범저장통과다.
같은서명release38교체후60초촬영/10초선택·시각/PDF잠금과기존MP4무료내보내기해시유지를
확인했다. 이10초동작검사를앞선20초timeout의해결근거로사용하지않는다.

## Code review

고정점 e16f448, 최종76b5f3d: Spec 필수0, Standards 필수0/선택0.
두 검토 모두 새부분출력의 전체리듬·시각·감정/욕구 제한과 원문 경계를 확인했다.
Spec은 최종59host 및84정책 exact 원천도 대조했다. 실제 기기/출하 완료를 대신하지 않는다.
docs/agents/issue-tracker.md가 없어 로컬 사용자 요청/명시 계약을 spec 원천으로 사용했다.
버전 b2ef9c1의38/0.5.24는 패키징 후보이며 아직 Play 미제출이다.

## Cleanup and rollback / Residual risks / Evidence

기존 성과는 고정조건에 한정된다. 이번 현재판단도 일반 정확도·의도번역을 입증하지 않는다.
새20초서명판timeout의원인규명/제어된재현은남는다. 현재code38을Play에제출하지않았고
마지막제공확인판은37이다. [정리보고서](storage-cleanup-20261010.md)는 실제삭제와정책차단을구분한다.
소유MoaQA35의서명QA앱·연구앱·test앱3개를제거하고없음을확인했다. 에뮬레이터종료뒤
ADB목록에서emulator-5556부재를확인했고실제S25는변경하지않았다. 공용AVD파일은유지한다.
첫정리명령은AVD확인파이프의exit체크에서종료되어삭제0이었다. 전체출력을읽는검증으로
고친후정확한MoaQA35에서만정리를실행했다. 제품문제가아닌하네스오류다.
최대5cycle중현재2cycle을종료하고CLI stopped→clear→read상태부재를확인했다.
**ULTRAQA BLOCKED: 최초20초timeout 원인미해결·일부생성물삭제 자동정책차단.**
담당은메인이며다음안전한행동은동일서명/동일미디어의부하통제진단이다. 전체목표완료가아니다.

근거: [summary](vocal-kind-20261010/summary.json),
[host](vocal-kind-20261010/host.json), [Android](vocal-kind-20261010/native.json),
[실제53조건](vocal-kind-20261010/android-comparison.json),
[정책84조건](vocal-kind-20261010/policy-comparison.json),
[패키지](vocal-kind-20261010/package.json), [업데이트보존](vocal-kind-20261010/signed-upgrade.json),
[최초timeout](vocal-kind-20261010/signed-initial-timeout.json),
[단계별진단](vocal-kind-20261010/timing-diagnosis.json),
[서명2회재검사](vocal-kind-20261010/signed20-repeat.json),
[서명smoke](vocal-kind-20261010/signed-smoke.json),
[소유QA기기정리](vocal-kind-20261010/qa-device-cleanup.json).
