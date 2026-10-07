# 인식 후 음량 보류 개선 — 진행 중

2026-10-07 KST. [사전 계약](../research/20261007-accepted-vocal-audibility-contract.md).
현재 Play는0.5.19-beta/code33이며 아래 후보는 아직 배포하지 않았다.

## 재현·진단

기존 frozen caption app/test를 MoaQA35에 설치해 실제 모델28조건을 실행했다.
`work/accepted-vocal-audibility-20261007/baseline.py baseline01`: exit0,
Android1검사/0실패/0skip, 모델 실행96.77초. 원본hash/production설치 보존,
기기에 복사한 입력은 제거했다. 추론 실패·빈 결과를 성공으로 세지 않았다.

- `cry_rms6`, `cry_rms9`: 실제 YAMNet의 기존 지속 규칙은 CRY를 승인했지만
  표현 단계는QUIET. cleaned strongest-second RMS는.006035/.009052,
  activity비율은0/.013333이었다.
- `babble_seven_month_rms6`, `babble_seven_month_rms9`: AST의5–15초 창이
  기존옹알이 규칙을통과했지만표현단계QUIET. 해당창RMS는.003864/.005796,
  전체activity비율은0/.026667이었다.
- 성인대화/일반웃음/음악/청소기/무음/DC/rumble/noise의최종한마디0.
- 4조건은공개원본2개의감쇠조건이다.4명아기/일반정확도/욕구해석으로보고하지않는다.

가설순서: (1)장면activity의이중gate (2)실제분류지속부족 (3)필터가발성제거.
실제sound decision은승인됐으므로2가직접원인이아니며,같은필터입력의모델점수가있어
3만으로이후QUIET를설명할수없다.1을최소interpreter재현으로확인했다.

## 수정과 현재 검사

- `SoundFrame.windowRms`: 실제MediaPipe타임스탬프에해당하는cleaned15600samples의
  RMS. 마지막padding도분모에유지한다. 원본/gain/모델점수는바꾸지않는다.
- AST의옹알이승인창에기존측정 strongestSecondRms를전달한다.
- 기존activity승인은그대로다.조용한입력에서추가경로는승인창의측정음량을검사하고,
  직접사건은음량이충분한창만남겨**같은SoundTimeline 지속/충돌규칙**을다시통과해야한다.
  모든기존사건종류가유지되어야하며다른시간의큰성인말로대신할수없다.
- broad옹알이는각승인창의유한한음량이.003이상이어야한다.정확onset/리듬/감정은만들지않는다.
- 기존일반presence경로·분류임계값·필터·모델·이용권·저장기능은변경하지않는다.

`host.py audibility-red01 AcceptedVocalAudibilityTest`: 실제Kotlin/JUnit5검사중2실패,
둘다`Accepted audible ... was lost at the final gate: QUIET`.
수정후`audibility-green01`은새8+기존관련검사포함**56통과**(.462초),compile/test exit0.
이숫자는실제음원/Android모델정확도측정이아니다.

현재새Android빌드·같은28조건재실행·기존53조건·독립코드검토가남아있다.
후보추가회복/배포성공을선언하지않는다.중간산출물은이번ignored경로에두고
원본·기준APK·공용AVD를보존한다.
