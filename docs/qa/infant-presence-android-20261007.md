# UltraQA Report — Android 발성 후보의 실제 누락 회복

2026-10-07. [사전 계약](../research/20261007-infant-presence-android-contract.md),
[실제 품질 비교](../research/20261007-infant-presence-android-results.md). 제품0.5.18-beta/code32는 유지한다.
**연구 후보의 검증 결과이며 새 beta 배포·일반 정확도·휴대폰 품질 승인은 아니다. 전체 goal active.**

## Goal and success criteria

동결 head를 추가한 실제 Android 경로에서 보존 누락의 한마디 회복, 기존 승인 보존·비아기 반례,
모델 무결성·20초 중첩·취소/재시도·UI/기록/미디어·연구 APK 배포 차단을 검사한다.
온디바이스·별도 오프라인 debug package·MoaQA35/API35 x86_64에서만 수행한다. 빌드/ADB/Git 담당은 메인 한 명이다.
사용자 원음/특징 외부 전송·학습·calibration·threshold 선택0; 새 LLM/유료 API0; production·상품·실거래0.
최대5cycle/동일실패3회 종료 조건을 유지한다. 부모 goal 완료와 이 한정 연구 QA 완료를 구분한다.
초기 fixedpoint102a326/clean tree·조사자 소유 문서 변경 보존. 구현323c82f의 두 축 리뷰를 먼저 받았다.

## Scenario matrix

| ID | 의도 | 사용자/공격 | Setup | 명령/하네스 | 기대 | 실제 | 수정 | 증거 | 정리 |
|---|---|---|---|---|---|---|---|---|---|
| I1 | 일반 발성 보완 | .8 경계·NaN·null·범위 | pure Kotlin / native | host.py / native02 | subtype·onset·리듬 발명0 | host42 / Android56 pass | 전용window/finite/같은창RMS | host-policy03/native02 | 소유classes 삭제 차단 |
| I2 | 오인·기존 승인 보존 | 감정/TV 전창 충돌·기존승인 | native / 실제PCM36 | run_device.py pcm04 | 기존phase 결과 exact보존 | 기존승인13 exact / 공개33 새generic0 / 약국2 회복 | shifted승인 후 general 보완 | pcm04 safe-summary | UUID copy 부재/원본 불변 |
| I3 | 모델/설정·배포 혼입 | 잘못된Boolean·의존flag·forbidden variants | Gradle/APK | flags.py / verify_ast_payload | 후보 APK만·단일pin | 7/7실제거부 / archive pass / 잘못된모드2거부 | generated Sync / flag 보호 | flags01/archive01 | child0/build삭제 차단 |
| I4 | 낮은 소리의 잘못된 승인 | quiet창+성인/음악 tail·click/DC/tone | synthetic+기존public control7 | run_controlled.py pcm05 | 새general/한마디0 | 7조건 새지원0 / 중첩3창 정상 | perwindowRMS 및 기존입력 기준 | pcm05 safe-summary | runtime부재·소유PCM 삭제 차단 |
| I5 | native 실패·취소·재개 | corrupt/missingmodel·시간초과·동시실행 | 실제ORT/store/UI | native02 / ui01 | 실패보류·취소 후 새run | 56pass·실제20초 UI 취소/다음완료 | 기존mutex/deadline/zeroing 유지 | native02/ui01 | 임시input/modelpartial 정리 |
| I6 | 실제 decoder·AV·저장/구간 | 보존음성3·기존MP4 10/20·취소 | actualDeviceClipAnalyzer/Compose | decoder01 / av01 / regression01 | 한마디·시각시간·기록보존 | decoder3회복/보존·AV2보존·80pass | QUIET 중복gate general한정 수정 | decoder01/av01/regression01 | WAV/UUID copy 제거 |
| I7 | 거짓성공·stale·입력주입 | fakeOK·누락/skip·잘못된manifest/pins | strict parser / owned unit | test_qa_gates / extra_evaluation unit | exit/terminal/count 검증 | parser30pass / finalpins3pass | 종료metadata 검사·과거보고정정 | tool출력/해당source | 임시unit fixture 자동정리 |
| I8 | sparse 입력·메모리 경로 | 20초 말미tone/click·반복native | decoded WAV / synthetic10/20/20 | sparse01 / benchmark01 | 새발성0·completecoverage·보호동작 | 2pass·benchmark1pass | 후보명시optin benchmark | sparse01/benchmark01 | 입력/런타임보고 정리 |

LLM·실행명령 입력을 추가하지 않아 prompt injection은 파일명/manifest 경계·거짓성공 검사로 다룬다.
현재 실폰은 연결0이며 ARM 측정은 별도 적용 gate로 남는다. 에뮬레이터 thermal0을 실폰 발열 검증으로 쓰지 않는다.

## Commands run and evidence

ignored evidence root `work/infant-presence-android-20261007/`.
아래 명령은 활성 worktree에서 `work/ast-portability/venv/Scripts/python.exe`로 실행했다.

| 명령/출력 | exit | 실제 검사 | 초 | timeout/범위 |
|---|---:|---:|---:|---|
| host.py host-policy03 InfantVoiceSupportPolicyTest,BabbleSupportPolicyTest,ExpressionVarietyTest,VisualTemporalAlignmentTest |0|42pass|JUnit .287|compile90s/run30s·호스트 policy only |
| build_debug.py build-candidate05 |0|app+test build|54.248456|Job600s/12GiB·child0 |
| build_debug.py build-candidate06 |0|decoder test 추가·app SHA동일|39.744|동일 |
| build_debug.py build-candidate07 |0|benchmark optin 추가·app SHA동일|33.511726|동일 |
| run_device.py install02/03/04 |0|설치baseAPK SHA와code32 검사|별도provenance|각install120s |
| run_device.py native02 |0|56pass/0fail/0skip|34.70|600s |
| run_device.py pcm04 |0|36측정·계측1pass|112.28|600s·동일source overlap |
| run_controlled.py pcm05 |0|7측정·계측1pass|24.88|600s·비아기반례 |
| integration.py av01 |0|actual MP4 10/20·계측1pass|20.94|500s |
| integration.py ui01 |0|실제20초취소→다음완료·계측1pass|15.95|500s·합성 only |
| integration.py regression01 |0|80pass/0fail/0skip|62.37|500s |
| decoder.py 01 |0|보존PCM→PCM16WAV→actualdecoder3·계측1pass|8.21|180s·원MP4 검증 아님 |
| sparse01 actual instrumentation |0|전체20초sparse경로/설정2pass|21.80|180s |
| benchmark01 actual instrumentation |0|synthetic10/20/20 native1pass|18.52|180s·x86_64 only |
| flags.py |0|실제Gradle negative7/7거부|각10.384–21.004|각Job180s/6GiB·child0 |
| verify_ast_payload.py --expect present --model-variant infant-candidate |0|정확model/단일graph/ORT4ABI/notice|archive검사|서명·실폰 대신하지 않음 |
| -m unittest discover -s tools/research -p test_extra_babble_evaluation.py -v |0|3pass|.009|hostunit·새원음실행 아님 |
| -m unittest discover -s outputs/QA -p test_qa_gates.py -v |0|30pass|.016|hostunit |

최신 Android 실행 **144회 / 고유143 test IDs / 실패0 / skip0**다. 두 PCM 데이터셋의 같은probe test ID를 중복으로 더하지 않았다.
초기native54/PCM03은 이 최종 집계에 중복 합산하지 않는다. Host42는 Android 검사와 겹치는 정책/표현 검증이며 별도다.
host03 pin15는12개 rawGitblob 일치·3개 rawCRLF→GitLF 차이만 확인됐다. 의미/source drift는 없었다.

APK source SHA `77b20be46ecef6e302515d63bde12f4c378a8eeb50ea829fb455d0271a61624e`,
최신 test APK SHA `3dea96314dbb0fe8daafecbdfed051e8a9d7b72ed774a2c02c590027bb184159`.
실제 compiledflags AST_INFANT_PRESENCE/INTEGRATED_CANDIDATE/CLOSED_TEST=true, BABBLE_SUPPORT/PREVIEW=false.
manifest는com.moa.babylanguage.astcandidate·INTERNET/BILLING 없음·allowBackup=false다.
원모델 대신 고정91,138,597B graph 하나/SHA4a378476…802a가 있고 ORT4ABI를 확인했다.
notice는현재source bytes와 exact동일/SHA08655177…ae836이었다. 연구signature/distribution으로 실제beta를 주장하지 않는다.

## Failures found / fixes applied

- 첫build01의Android asset API exclude미지원:exit1/23.454초. 제품런타임결함과 구분하고 generated Sync로 단일모델을 구성했다.
- PCM01 adb shell redirect quoting 실패:분석미실행. PCM02 exec-out dd EOF이벤트30초timeout:분석미실행.
  기존검증 exec-in/run-as/sh 방식으로 수정했다. 두freshUUID copy부재를 확인했고 같은문제3번반복은 하지 않았다.
- 초기PCM03의첫약국구간은 head.8198지원 뒤 whole-scene activity<.04로 QUIET였다. 실제input .003기준을
  해당창·최종표현에서도 재사용해 수정했다. PCM04에서실제한마디 회복/기존phase13 exact/비아기 반례 통과다.
- 기존 extra evaluator의종료metadata 측정을했다고쓴P2:과거보고를정정하고 verify_final_pins가metadata/model/head를 실제검사하도록수정했다.
  drift단위검사를추가했다. 과거56초run을 새코드로 수행했다고 소급하지 않았다.
- 독립Standards/Spec323c82f 검토 필수지적0. Standards의expectedwindow 로직중복은 비차단Fowler제안1로 보존한다.

## Measured scope and residual risks

약국 두10초는같은원본의겹친개발구간이다. 다른울음은기존YAMNet승인을보존했다. 공개33은train overlap·합성/개발조건 포함이다.
약국의원MP4현재미확보로WAV변환검증과별도원본MP4검증을분리했다. 실제20초 AV는이미기존지원된별도개발원본이었다.
새head의generic이 정확화자/욕구/발성onset/긴연속성을증명하지않는다. 추가extra9의5누락을 유지한다.
같은window의성인말/음악이RMS를채울가능성, Music hardguard부재·TV/아기흉내·대상월령/자연성·독립정답 결손을 남긴다.

synthetic10/20/20 AST측정은5.103/6.089/5.103초, process sampledPSSpeak514792/523056/522409KB였다.
모델/decoder/AV timing 범위가다르며 이benchmark는memory sampling 비용도포함한다. x86 에뮬레이터thermal0은S25발열승인이 아니다.
새benchmark는오프라인명시optin·해당candidate package만실행되며같은런타임의head/RMS 출력·20초3창을검사한다.

제품code32는변경/재업로드하지않았다. 10월7일04:33KST 실제Play게시개요는code32검토중.
다음은S25 ARM10/20초 속도/PSS/발열/취소와새사건·TV반례 평가, 유효변경의새code beta 서명·업데이트·검토제출이다.

## Cleanup and rollback / lifecycle

각UUID runtimecopy·임시WAV·fixture와benchmark runtimereport는실행후제거/부재확인했다. 원음/PCM pin은불변이다.
Job build05/06/07·negative7 모두종료/자손0/cleanuptrue였다. 최신app/test 한세트만final에보존한다.
연구앱/테스트앱 uninstall Success·정확package 부재를확인했고 이번AVD실행은종료했다. AVD폴더는보존했다.
중간app/build·hostclasses3폴더·새controlledPCM7파일의일괄삭제는실행도구의자동승인검토가차단했다.
구체이유는`blocked by policy` 외반환되지않았으며 해당삭제명령은시작되지않았다. 대체도구로우회하지않았다.
이생성파일들은남아있으며 이한정QA의cleanup은BLOCKED다. 최신final app/test한세트의SHA는보존했다.
사용자원본/기존0.2.1·0.3.0/직전배포final/공용AVD폴더/키/무관dirty변경은보존한다.
격리state는cycle2/blocked/activefalse 기록→read확인→clear→실제파일부재를확인했다.
clear응답은success필드가아니라cleared=true였다. 첫wrapper assertion schema실패후원응답과부재를재검증하고
scratch helper를수정했다. CLI삭제는이미성공했으며 처음schema실패를앱결함으로세지않는다.
정리후C여유68,188,336,128B(약63.50GiB)를읽었다. 삭제가차단돼파일정리회수량을주장하지않는다.
**ULTRAQA BLOCKED: owned generated-directory cleanup rejected by automatic approval review.**
Owner는메인/실행정책이며 다음안전단계는명시대상의허용된정리다. ARM/새beta는별도미완료gate다.
전체goal은active로유지하고complete로표시하지않는다.
