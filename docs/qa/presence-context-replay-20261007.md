# UltraQA Report — 발성 존재/감정 충돌의 동결 점수 진단

2026-10-07. [계약](../research/20261007-presence-context-replay-contract.md),
[실측 결과](../research/20261007-presence-context-replay-results.md).

## Goal and success criteria

고정 Android 결과51case를 완전하게 읽고 기존판정/문장·감정 단서를 보존한다.
같은창 음량/score와 감정context를 분리하되 새한마디·화자·감정 정답으로 승격하지 않는다.
raw media/model/학습/제품/Git 외부자료 업로드0이다. 모델추론·native Android 재실행도0이다.
이번 metadata CLI QA의 범위다. 부모goal/ARM/정식 출시/새beta배포 완료가 아니다.
Job60초256MiB/1MiB, CLI각30초, 최대5cycle/동일실패3회중지다.

## Scenario matrix

| ID | 의도/사용자·입력 | 실행 | 기대 | 실제 | 수정·증거 | 정리 |
|---|---|---|---|---|---|---|
|R1|정상 fixed3보고서51조건|replay run01–04|완전case/phase불변·qualityfalse|run03/04에51완료·18/25/8·추가승인0|run04/results·input/code6pin|입력파일불변 |
|R2|malformed/missing/NaN/duplicate|unit15+실제CLI2|완료거부|고유unit15·CLIduplicate/nonfinite거부|unit·cli결과/로그|fixture2제거 |
|R3|partial/fake success·stale/잘못된창|unit+CLI3|출력보존·외부/기존output거부|stale/outside/unprepared전체거부·sentinelSHA불변|CLI5조건·unit|sentinel제거·새output없음 |
|R4|quiet head와다른창큰음량·감정/TV혼합|synthetic score unit|같은창audibility·flag보존·문장null|통과|unit；음향정답시험아님|메모리fixture only |
|R5|dirty트리·prompt문구·자료변경|source/inputpin·unit|타작업보존·문구실행0·drift거부|다른agent문서보존·phaseSHA·driftunit통과|Git/status·source6pin|범위밖삭제0 |
|R6|시간/메모리/부분결과|WindowsJob+strict완료재생|exit0·자손0·51count·종료pin|run04 .356479초·58,183,680B·자손0|processjson·결과재계산|현재worker0 |
|R7|scoped lifecycle·정리|OMX CLI·nativeexact삭제|stateclear부재·fixtures없음|CLI성공·existsfalse·fixture3제거|state-response2·storage전후|repo원state미변경 |

CLI/점수메타데이터의 prompt주입·잘못된판정은 synthetic문자열/누락/NaN/거짓완료로 다뤘다.
고의프로세스hang나취소/실기기native는 기존동결candidate의 별도ARM gate이며 이번재생에서실행하지 않았다.

## Commands run / failures / fixes

Python은 활성worktree의 `work/ast-portability/venv/Scripts/python.exe`다.
도구cwd는repo이며 unitcwd는`tools/research`다. ignored evidence root는`work/presence-context-replay-20261007/`.

| 명령 | exit | 실제결과/한계 |
|---|---:|---|
| 최초unit |0|13통과/.008초 |
| replay run01 |1|PCM시간 가정거부+parentdataclass JSON실패. process파일 미기록 |
| replay run02 |1|parentasdict수정·workerPCM시간거부 .295489초/자손0 |
| qa_driver start |0|진단/수정/계획·격리state·고유14unit/.015초 |
| replay run03 |0|51완료 .359483초/자손0 |
| qa_driver hostile |0|고유CLI5거부/0실패·source불변·sentinel보존 |
| unit sourcepin 후최초호출 |shell1|tools/research cwd에repo상대python경로를써실행전실패 |
| 같은unit 올바른상대경로 |0|고유15통과/.007초 |
| replay run04 |0|sourcepin/완료gate 포함51완료 .356479초/자손0 |
| exact fixture cleanup |0|3개명시파일제거·외부경로거부·차단대상변경0 |
| qa_driver finish |0|statecomplete/read/clear/read existsfalse |

원인1: recorded selection은samples/16올림, AST창은samples/16절삭인데 처음같다고가정했다.
실제174614samples의10914ms선택/10913ms ASTendpoint로수정하고분수ms잘못된창도거부한다.
원인2: run_bounded는dataclass인데dict JSON처럼썼다. asdict와typedfield로수정했다.
원인3: Python/로컬파일 경로조회 일부실패는harness설정문제이며제품누락으로세지않았다.
모델/threshold/기존감정guard를완화하지않았다. 실패로그를지우거나성공으로소급하지않는다.

## Cleanup and rollback

첫두run은lifecycle시작을기록하지못했다. qa/preflight에누락을공개했고소급state를만들지않았다.
수정cycle의실제격리lifecycle은iteration3으로계획→진단/수정→QA→adversarial→cleanup→complete→clear를기록했다.
시작snapshot은run01/02후7파일8,572B, 종료는43파일139,581B(각snapshot쓰기전)다.
이는이번작은연구폴더logicalbytes이며실제드라이브회수공간이나전체작업시작용량이아니다.
input3SHA불변·원판정해시보존·source6pin·worker0이다. 새APK/AAB/weight/rawPCM0이다.
temporaryfixture3개와pyc2개34,494B를exact경로검사후삭제했고기존필요증거/연구APK는보존했다.
자동검토가차단한과거publicMP3/빌드/PCM삭제는이번에시도하거나우회하지않았다.

## Residual risks

51조건의source/family중복·caption-weak·정확사건정답부재가있다. 높은presence도호흡/배경shortcut일수있다.
감정flag가같은아기의감정을증명하지않고top10에없는Music/Breathing을낮다고보지않는다.
8held는새회복이아니며소리속성분류/욕구·영상/화자정확도검증0, 이번실폰/새beta업데이트0이다.
run01parentprocess최종metric미기록과최초lifecycle누락은원래실패이력으로남긴다.

독립검토전에는3cycle COMPLETE로표시했지만R5/R6의사전범위가충분히검사되지않았다.
**그완료선언을정정한다.** 아래추가검증전까지사전계약전체완료로보면안된다.

## 독립 P2 수정과 실제 중단/복구 검증

Spec P2 두건을수정했다. 첫째,출력을공용ignored work전체가아닌전용evidence root하위새폴더로제한했다.
실제 `work/replay-unrelated-forbidden` CLI는mkdir전exit1/전용root오류로거부됐고폴더가생성되지않았다.
둘째,R5의거부는외부/기존output에적용하고dirty트리는타작업을보존한다는계약을명확히했다.
읽기전용진단이공용worktree의dirty변경을전부거부하도록바꾸지않았다.

이후실제untracked synthetic문서를만들고그대로둔채새run05를실행했다.
문서SHA와전후`git status --porcelain`의`??`가같았다. 원input3SHA도보존됐다.
run05는기존51진단18/25/8을그대로재생했으며exit0/.378788초/58,073,088B/자손0이다.

실제replay `load`에5초지연을주입하고1초Jobtimeout을검사했다.
초기inline코드quote오류와sys.argv변경후잘못된marker경로라는harness설정실패2건은
hang진입전exit1로끝났다. 실패로그를보존했고실제timeout성공으로세지않았다.
수정후cycle5에서load진입marker=true, **1.004256초/termination=timeout/exit1/자손0**, 완료results없음을확인했다.
그후위run05가정상완료해동일진단의중단뒤복구를실행했다. 모델/native취소측정이아니다.

최종고유unit은**16통과/.011초**이며`qa-review/unit-cycle5-resume.log`에전체로그를보존했다.
이전15/.007초는당시터미널출력이었고별도로그저장이누락된검증한계가있었다.
최종16로그가이를대체하되과거15로그가존재했다고소급하지않는다.
실제고유거부CLI는기존5+전용root1=6개다. 새dirty/timeout/복구는별도행동검사다.

cycle4–5의evidence는`qa-review/`이다. synthetic timeout 준비/marker 및dirty문서5파일은
정확한경로검사후제거했다. 이번격리state clear뒤existsfalse·입력pin/원고정run04보존이다.
원범위밖publicMP3/빌드/PCM삭제차단을해제하거나우회하지않았다.

**ULTRAQA COMPLETE: scoped metadata replay goal met after 5 cycles.**
최대5cycle의마지막cycle내setup오류수정후실제목표행동을검사했다.
완료범위는명시한metadata계약/CLI/정리다. 이전단계cleanup BLOCKED와ARM·부모goal은해소되지않았다.
전체goal은active이며동결발성후보실폰검증과새source/감정귀속검토를이어간다.
