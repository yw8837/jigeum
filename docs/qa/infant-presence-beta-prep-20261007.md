# UltraQA Report — 영아 발성 보완 beta 적용 준비

2026-10-07. 고정 기준 `a4fcf49`, [사전 계약](../research/20261007-infant-presence-beta-prep-contract.md).
목표는 동결 연구 모델의 beta 옵션·실폰 검증/패키징 경로를 준비하고,
검증 누락·잘못된 변형·거짓 성공이 최종 beta를 만들지 못하도록 실제 명령으로 확인하는 것이다.
실제 아기 정확도/새 beta 배포/실폰 성능 완료가 이 QA의 성공 조건은 아니다.

## Scenario matrix

| ID | 의도/사용자·오류 모델 | 설정/명령 | 기대 | 실측/수정 | 근거 | 정리 |
|---|---|---|---|---|---|---|
| B1 | 새옵션과기존범위 혼동 | 신규beta/ASTbeta/release generateBuildConfig | true/true·true/false·false/false | 3실제통과·정식전체접근false도확인 | gradle-results/config logs | 설정파일은다음build용 |
| B2 | malformed·의존성/variant 혼합 | boolean yes·AST없는presence·연구혼합·assembleRelease | 실제Gradle거부 | 4실제거부 | malformed/dependencies/mixed_research/forbidden_release | 모든Job child0 |
| B3 | 미검증·stalecode 배포 | verifyInfantPresenceArm proof없음·code32 | 실제패키징거부 | 2실제거부 | missing_arm/old_code | APK/AAB미생성 |
| B4 | 중복/대체model | stageInfantPresenceAssets | 고정graph하나 | 1실제통과·91,138,597B/SHA4a378…802a | staging.json | 활성staging유지 |
| B5 | stale/거짓pass/partial/skip·주입 | sharedgate9unit·CLI부재/에뮬레이터·로그재파싱 | 거부/실패/증거분리 | 9unit통과 | final-gate.log | tempfixture자동정리 |
| B6 | 잘못된목적/옵션/proof | 실제PowerShell builder6조건 | signing/build이전거부 | 6조건/1unit통과 | final-powershell.log | destination미생성 |
| B7 | 기존probe 보존/기기혼동·취소후복구실패 | probe15unit·candidate전체simulatedroute | 정확연구package·완전로그·cleanup | 15통과; 전체경로는mock | final-runner.log | 가상성공proof임시삭제 |
| B8 | dirtyworktree/timeout/상태잔존·용량 | boundedGradle·OMX uniquecwd | 타작업보존·child0·state부재·전후용량 | 10Job전부cleanuptrue/child0·stateclear, 시작용량누락 | gradle-results/state-final-clear/storage-end | 이번state부재·종료용량확인 |

LLM prompt/사용자 입력 실행 경로가 없어 injection은 JSON/file boundary·가짜OK·옵션의 잘못된 값으로 다룬다.
실행/반복은 시간 제한이 있고 임시 unit 자료만 쓴다. 성공 fixtures는 실제 ARM 증거로 보존하지 않는다.

## Commands run and evidence

ignored root `work/infant-presence-beta-prep-20261007/`, Python은 `work/ast-portability/venv/Scripts/python.exe`.

- `run_qa.py`: actualGradle10조건, exit0. 각Job timeout180초·commitlimit6GiB·log1MiB.
  첫malformed24.063초, 나머지10.363~13.058초. 모두자손0/cleanuptrue/출력잘림false.
- sharedverifier9 / runner15 / PowerShell1: **고유25unit/0실패**.
  PowerShell1에는 실제초기거부6번을 포함한다. mockdevice경로·CLI거부를실폰실행으로세지않는다.
  선행runner14묶음에추가1을더했고최종15전부재실행했다. 반복25검사를새고유수로합산하지않는다.
- 실제에뮬레이터 `benchmark-01/measurements.json`을sharedverifierCLI에입력:exit1,
  `Incomplete run, cleanup or production preservation`. 에뮬레이터의부분보고로ARM승인불가.
- `git diff --check`:exit0. ADB readonly기기목록은0. 새ARM실행/앱설치/계측/Play호출0.
- 모든unit최종로그와exit는 `final-units.json`/`final-*.log`로보존한다.

## Fixes applied

- beta전용presence옵션은ASTbeta를요구하고연구flags/정식variants와혼합하지않는다.
  기존model대신동결후보graph/notice하나만staging하고BetaBuildConfig에연결했다.
- directGradlemergeBetaAssets와공식PowerShellbuilder가sharedARMverifier를사용한다.
  실제proof·7일이내timestamp·APK/test/modelpin·ARMdevice/runtime metadata·complete10/20/20창·
  시간/PSS/열·exact취소테스트로그·기존production불변/연구정리/동결사본제거를요구한다.
- 기존code32재사용은Gradle에서거부한다. 이번에는versionCode를늘리거나APK/AAB를만들지않았다.
- runner는별도candidate만명시optin으로측정하며생성실행사본을새임시폴더에서자동정리한다.
  기존final한세트·제품패키지·기록을보존한다. host설치hash는streaming으로계산한다.

## Cleanup and rollback

이번가상JSON/로그fixture·임시목적지는TemporaryDirectory로정리됐고,
격리OMXstate는write→read→clear의exit/응답을검사해actual부재를확인했다.
Git에는소스/문서만, keys·원음·특징·캐시·모델/생성물은올리지않는다.
큰새APK/AAB/weight다운로드0이다. 활성graphstaging은후속build에필요해유지했다.
전단계app/build/hostclasses/controlledPCM삭제의자동검토차단은그대로다. **우회삭제0**,
이번측정을파일정리회수량으로보고하지않는다. 전단계cleanupBLOCKED는소급완료하지않는다.

독립 Standards 검토에서 RUNBOOK246의 **시작 용량 관측 누락(P2)**을 확인했다.
이번 실행 시작 값을 저장하지 못했고, 과거 종료/중간 값을 시작값으로 대신하지 않는다.
실제 종료 snapshot `storage-end.json`은 2026-10-06 20:12:35 UTC에 준비 evidence 폴더
29파일/34,560 logical bytes, C 여유68,357,656,576B를 기록했다. snapshot 파일 쓰기 전 수치다.
이는 폴더 논리 크기·현재 여유이며 삭제로 회수한 공간이 아니다.
다음 실제 ARM runner는 시작·종료의 소유 run 폴더/드라이브 여유를 자동 기록하도록 수정했다.
실폰 실행이 완료된 것처럼 보고하거나 과거 시작 관측을 소급 복원하지 않는다.

## Residual risks / next

이검사는workflowproof다. 의도적인파일위조를막는서명된기기attestation이아니고,
속도/PSS/열gate는전체AV·여러기기지원·반복사용성능검증의대체물이아니다.
실제ARM·약국원MP4·새자연옹알이사건정답·TV/흉내/혼합오인·일반정확도·욕구번역은미완료다.
추가공개두파일조사는권리/preview경로확인이며청취·추론·새source확보는0이다.

**ULTRAQA BLOCKED: initial storage snapshot was not recorded.**
Owner는 메인의 실행 기록이다. 시작 관측은 복원할 수 없어 보고 누락을 공개했다.
행동 검사는 통과했지만 이 실행의 전후 용량 증거는 미완전하다. 전체 goal은 active다.
code32 이후 새 beta 패키징/업로드는 없다.
다음은S25실측→독립검토→새codebeta서명/업데이트보존→Play비공개검토제출이다.
