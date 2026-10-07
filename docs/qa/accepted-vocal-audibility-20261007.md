# 인식 후 음량 보류 개선 — 로컬 QA 완료·Alpha 검토 요청

2026-10-07 KST. [사전 계약](../research/20261007-accepted-vocal-audibility-contract.md).
최신 상태:0.5.20-beta/code34 사전검사 종료, Google 검토·게시 확인 대기다.
마지막 실제 제공 확인 버전은33이다. 아래 본문은 단계별 실행 이력이며
최신 상태는 마지막 Google Play 절과 [완료 감사](accepted-vocal-audibility-20261007/completion-audit.md)를 따른다.

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

## 실제 Android 동일 입력 비교

`candidate01`: 실제 Android 검사1통과/0실패/0skip, 103.77초. 28개 입력의
해시·감쇠량이 baseline01과 동일하며 원본과 production 설치 상태를 보존했다.
기기 입력 사본은 제거됐다. `comparison.json`의 자동 대조가 통과했다.

- 작은 옹알이 `babble_seven_month_rms6/rms9`가 QUIET에서 결과 생성으로 회복됐다.
  동일 공개 원본1개의 감쇠2조건이다. 독립 아기2명이나 일반 인식률 향상이 아니다.
- 기존 출력3개와 모든 조건의 모델 단서·사건·승인 범위는 동일하다.
- 성인 대화/일반 웃음/음악/청소기/무음/DC/rumble/noise의 새 출력0.
- 작은 울음2조건은 보류가 유지됐다. 승인창 자체의 .003 음량 기준을 만족하는
  창만 남기면 기존 지속성 규칙을 통과하지 못한다. 다른 시간의 큰 소리로
  이를 대신하거나 해당 샘플에 맞춰 음량·지속성 기준을 낮추지 않았다.

연구 빌드는 Gradle exit0/BUILD SUCCESSFUL, 121.94초였으나 bounded runner는
`orphan_cleanup`으로 exit1이다. 잔여 자손0/cleanup_complete이며 메모리 상한·출력
절단은 없었다. 깨끗한 runner 성공으로 보고하지 않는다. 설치된 APK/test 해시는
candidate01에서 실제 대조했으며 최종 서명 빌드는 별도 통과해야 한다.

## 독립 코드 검토

고정 diff `12e870c..0865cff`에 대해 Sol high 두 에이전트가 각각 검토했다.

### Spec

확인된 코드 결함0. 같은 승인창 음량·기존 지속/충돌 규칙·잘못된 시간/수치 거부가
사전 계약과 일치한다. 실제 후보28조건·기존53조건·native·저장·업데이트는
코드 검토가 대신하지 못하며 실행 증거가 필요하다.

### Standards

필수 지적0. P3 선택 의견은 ClipEvidence 내부의 .003..1 유한 음량 검사가
두 곳에 있어 추후 변경 시 어긋날 수 있다는 것이다. 현재 값은 같고 영향이
없으므로 이번 검증 중 추가 구조 변경은 하지 않았다.

## 배포 전 남은 실행 계약

| ID | 입력/사용자 행동 | 기대 | 실행/증거 | 정리 |
|---|---|---|---|---|
| N1 | 조용한 승인창/다른 시간의 큰 소리/잘못된 수치·시간/기존 충돌 | 해당창 근거만 허용, 기존 거부 보존 | PASS native100/0skip | 연구앱 제거 |
| R1 | 동결된 기존53조건 | 기존20출력 및 보류 관찰·문장 보존 | PASS paired-audibility01 exact53 | UUID 입력 제거 |
| B1 | 서명 beta34 및 비교 release34 | 서명/모델 payload/권한 flag 정확 | PASS clean 빌드·실제4패키지 | 최종 한 세트 |
| U1 | 서명33→34 업데이트 | 실제 기록·MP4 hash 보존 | PASS signed-upgrade-final | 소유 파일 제거 |
| U2 | beta 전체 기능/정식 재잠금 | 120/20/200·PDF 및 release 60/10/100 | PASS signed-closed-final 7시나리오 | 크기 fixture·설정 복구 |
| U3 | 실제 선택 영상 분석 후 다른 구간·취소 | 결과·돌아가기·취소 정상 | PASS signed-known-final | 원본 hash/기기 사본 제거 |
| U4 | 같은 작은 옹알이를 AAC 영상으로 가져와15초 선택 | 서명33의QUIET→서명34의옹알이 결과 | PASS signed-recovery02; 실제화면 확인 | 공개원본 보존·생성MP4 제거 |
| D1 | 현재 소스·QA 푸시 후 기존 Alpha | 새 code34만 제출, 실제 상태 확인 | 검토요청 완료/빠른 검사종료, 실제제공 미확인 | 이전+현재 배포본 보존 |

새 모델·추가 native graph·head 변경은 없다. 기존 S25 proof는 동일 AST 추론의
성능 근거이며 이번 후처리까지 실폰에서 재측정한 것으로 보고하지 않는다.
현재 Play code33 유지. 후보는0.5.20/code34로 올려 서명 패키징을 진행한다.

### 후속 실행: 고정 회귀와 native

`paired-audibility01`: Android1통과/0실패/0skip, 246.64초. 고정53조건의 모든
입력 hash와 최종 관찰/문장이 기존 `paired-run03`과 exact 동일하며 기존20출력이
유지됐다. 새로 추가한 창 음량 metadata만 비교에서 제외했다. 원본과 production
설치 상태 보존, UUID 기기 입력 제거도 확인했다.

`native01`: Android100통과/0실패/0skip, 10.12초. 새 승인창 검사, 실제 YAMNet
timestamp의 독립 RMS 재계산, 무음/낮은 rumble/단발 click, 이전 phase·timeline·
babble·infant policy, 문장·padding·시각 시간 정렬을 포함한다. 실제 설치 APK/test
해시를 확인했으며 오류나 skip을 성공으로 집계하지 않았다.

빌드 중 호스트 메모리 경쟁을 피하기 위해 연구앱/test를 제거하고 공용 에뮬레이터
실행을 종료한 뒤 서명 빌드를 시작한다. 공용 AVD 디렉터리는 보존한다.

beta34 공식 서명 빌드165.51초, 비교 release34는119.03초로 모두 clean exit0,
termination 없음·잔여 자손0·출력 절단 없음이다. 새 모델/헤드 없이 기존 payload를
사용한다. 실제 APK/AAB 검사 및 signed runtime 확인은 이어서 진행한다.

최종 APK/AAB 검사 통과: 실제 compiled flags, 서명, 4 ABI/16KiB,
beta의 정확 graph/head 및 release의 추가 payload 제외를 확인했다.
beta APK255163379B(기존과 같은 크기), AAB160195341B이며
AAB SHA256은 `172df8f28cd2ac503bbb7b6e4c4c4f34a9df18b349549b4ee75313addbd0ec25`다.

### 보존한 환경 실패

cycle1의 `signed-recovery01`은 부팅 직후 MP4를 `/sdcard/Download`로 복사하는
단계에서 Android가 `Operation not permitted`로 거부했다. 앱 설치/분석 전에
중단되어 제품 인식 실패로 세지 않는다. 생성MP4와 기기 경로 제거·원본 hash 보존을
확인했다. 사용자0의 RUNNING_UNLOCKED/저장소 경로를 확인하고 소유 임시 파일의
실제 쓰기/삭제를 추가한 뒤 cycle2의 새 출력 폴더에서 재시도한다.

### 서명 앱에서 실제 회복 확인

`signed-recovery02`는 PASS다. 공개 옹알이의 같은 감쇠PCM을 검정 화면+AAC의
15초 MP4로 한 번 만들고, 같은 파일·같은0–15초 선택을 서명33과34에서 실행했다.
33에서는 실제QUIET 보류,34에서는 저장 가능한 결과와 `범위 옹알이 후보`를
화면에서 확인했다. 설치 APK hash는 최종 서명본과 일치한다.
이는 codec·파일가져오기·구간선택·실제모델·표현/UI까지 연결한 개발 입력 검증이다.

UI 대기20.806/30.007초는 후자가 관찰근거를 펼치고 찾는 시간까지 포함한다.
두 값을 순수 추론시간·휴대폰 성능 비교로 쓰지 않는다. 생성MP4 및 기기사본 제거,
원본 hash 보존을 확인했다. `code33.png`, `code34.png`, `result.json`을 보존한다.
기록을 저장하지 않은 이 소유 QA 설치는 hash 확인 후 제거해,33→34 미디어
보존 검사를 별도 시작한다. 사용자 폰이나 기존 사용자 데이터를 초기화하지 않았다.

### 서명33→34 기록·미디어 보존

`signed-upgrade-final`: exit0/223.45초.33에서 실제 저장한10.000000초 MP4의
audio/video stream과932978B·SHA256
`284b98d279c82501f22b512eb4f46025e284f0d6b6a54be92709ce88643192fe`가
34에서도 동일하다. 구형 기록의 텍스트·LEGACY 출처와 beta 전체 접근이 유지됐다.
이 업데이트 검사 사이에 데이터 초기화는 없었다. 소유 외부 파일은 제거됐다.
Play split 설치 증거가 아닌 실제 같은키 서명 APK 업데이트 검사다.

### 전체 정책·실제 저장 검사

`signed-closed-final`: exit0/610.22초, 전체7시나리오를 이번 서명본으로 새로 실행했다.
작은화면/200%글꼴·실제1:59.6촬영·20초 무음보류·정확200MiB/+1byte 경계,
실제 분석·MP4 저장·PDF content URI/chooser·재시작이 통과했다. 이어서 비교용
release34에서60/10/100·시각/PDF 제한 및 기존 기록의 무료 내보내기를 확인했다.
새10초 audio/video MP4는 beta/release에서 같은932978B/SHA
`2bd8f762f006a4731276eb656fbc0cc9114e8079e66dfa566e60fc9464e8a045`다.
대형로컬/외부fixture 제거·디스플레이/글꼴 복원 모두true, cleanup errors0이다.

후속 독립Spec/Standards는 실제28/53JSON·native 원문100·서명 회복 화면·기록/MP4
보존과 source hash를 재대조해 새필수0을 보고했다. 당시 진행 중인 정책/내비게이션을
완료로 계산하지 않았으며, 최종 실행과 배포 상태는 별도 근거로 확인한다.

### 실제 영상 내비게이션·정리

`signed-known-final`은 PASS다. 기존 로컬 영상의 실제10초 AV 분석에서 옹알이
근거와 저장 버튼을 확인했고, 같은 구간으로 돌아가기·OS 파일 선택 취소·새 촬영
취소가 결과를 보존했다. UI 대기20.887초는2core 에뮬레이터 값이며 S25 속도가 아니다.
이 검사에서는 새 기록을 저장하지 않았다. 원본 hash 보존·기기 복사본 제거를 확인했다.

소유 code34 QA 설치의 정확한 APK hash를 확인한 뒤 설치/XML을 제거하고 에뮬레이터
연결 종료를 확인했다. 공용MoaQA35 폴더·사용자 폰은 보존했다. 생성한 대형fixture는
남아 있지 않다. 최종 code34와 직전 code33 배포본을 보존하고, 생성 build/outputs
내 중복APK/AAB6개804857502B를 경로 검사 후 제거했다. free95618437120B는 시스템
스냅샷이며 정확한 물리 회수량으로 주장하지 않는다. 과거 삭제 거부 대상은 재시도하지 않았다.

소스 commit `7ffc6a3bd277a34b48e56912ca58d3e9eca25392`는 origin의 같은 브랜치와
SHA 일치를 확인했다. 이 후속 QA 문서 갱신은 최종 검토 뒤 별도 커밋·푸시한다.
아직 Play34 제출/제공을 완료로 계산하지 않는다.

## 최종 로컬 QA 결론

마지막 정책·내비게이션·정리 원문을 독립Spec/Standards가 다시 검토했고 필수 지적은
각0이다. scope2의 complete(active=false) CLI read를 저장한 후 clear/read 부재를
확인했다. **ULTRAQA COMPLETE: Goal met after 2 cycles**는 위 로컬 적용·회귀·서명·
업데이트/정책 QA 범위다. cycle1의 부팅 직후 파일복사 실패는 그대로 보존한다.
PDF는 실제 생성·content URI·공유 chooser까지 검증했으며 외부 PDF 앱 검수로
확대하지 않는다. 실제 Play split 설치와 모든 휴대폰 성능은 별도 미검증이다.
Google 검토/테스터 제공은 아래 후속 외부 상태로만 기록한다.

## Google Play Alpha 후속 상태

2026-10-07 21:25 KST에 검증한 code34 beta AAB만 기존 Alpha에 업로드하고,
출시 `0.5.20-beta (34) - quiet vocal fix`를 저장한 뒤 변경1건의 검토 요청을 전송했다.
게시 개요에 정확한34가 `검토 중인 변경사항`으로 표시됐으며 사전 빠른 검사 중이다.
이는 요청 접수 증거이며 빠른 검사 완료·Google 승인·테스터 제공 증거가 아니다.
현재 마지막으로 확인된 제공 버전은33이다.

지원기기 감소0, 참조 기기 설치112MB/이전 대비1.32KB다. 가독화 파일 및 native
디버그 기호 미제공 권고2개, 제출차단오류0이다. 기존 테스터 목록·다른 트랙·
프로덕션·상품·권한은 변경하지 않았다. 최종QA `31ecf70`은 원격SHA와 일치했다.
[화면·제출 정보](accepted-vocal-audibility-20261007/play-review.md)에 작은 증거를 남긴다.
자동화33은 기존15분 주기로34의 승인 확인을 이어간다. 실제 게시 후 기록/푸시와
목표의 남은 요건 감사까지 끝나야 전체goal을 완료로 처리한다.

21:38 KST 후속 확인: 빠른 검사 진행 표시가 사라지고 `변경사항을 검토 중입니다`로
바뀌었다. 정확code34는 계속 검토 중 변경사항에 남아 있다. 검사 종료와 실제 제공은
별개이며 승인/게시를 아직 완료로 계산하지 않는다.
