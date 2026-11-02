# UltraQA Report — 공개 infant presence 연구 도구

2026-10-07. 목표: 정확35source 취득·기존encoder 특징·훈련source내 선택·평가전용 원본·CLI거부·종료·보존.
성공조건은실제exit/출처/수치/오류거부/정리이며 **영아 정확도·제품개선·Android·배포 승인이 아니다**.
실측품질실패는 [결과](../research/20261007-infant-presence-results.md)를 따른다.
원음외부전송금지/새public만취득/각Job180초2GiB/CLI30초/최대5cycle/동일실패3회중단.

## Scenario matrix

| ID | 의도·모델 | 설정/실행 | 기대와 실제 | 수정·증거 | 정리 |
|---|---|---|---|---|---|
| P01 | 정상연구자 | acquire35/features35/presencefit/eval3+33 | 완료35/249창·score차이0·21/14분리 | 정상5run process/completion | 자손0/활성공개자료만 |
| P02 | source침입 | exact23/35외·partialextra·duplicate·role/URL/NC | 모두거부 | 9head unit | 메모리/임시reuse fixture제거 |
| P03 | 선택누출 | validationrole·sourcefold·class소실 | 거부/18train source만CV | 3CV+5presence unit/selection.json | private학습0 |
| P04 | 숫자·무음 | NaN/shape/zero/weight합·sigmoid극값·quiet | 거부/quiet창미판정/실제sample시간 | presence unit | 메모리fixture |
| P05 | CLI오류·stale | wrongSHA/stalehead/outside/missing | 실제4/4nonzero·head불변·새output0 | guards-presence01/report.json | 신규악성fixture0 |
| P06 | 종료·거짓성공 | 각process/completion/runnerSHA | 실제exit0/한도내/잔여0 | 정상5run/既存Job10검증 | 신규Jobunit반복안함 |
| P07 | 지시삽입·dirty | description명령문·Git·격리OMX | 실행/외부전송없음·다른변경보존 | unit/modelnetwork차단/qa-presence01 | 이번state clear |

## Commands / failures / fixes

`python -B -m unittest discover -s tools/research -p test_infant_*.py -v` 고유21/exit0:
head9 + CV3 + presence5 + encoderinterface4. synthetic pass는 실제 아기 품질 evidence가 아니다.
`infant_head_screen_cli_qa.py --presence` 실제4/4/exit0. 네트워크·classifierforward·새fixture0.
Job제어코드는변경하지않았으며 이전고유10검사와이번5실행의자원/종료기록을함께본다.
기존guarded VTC cleanup차단을해소했다고표시하지않는다.

취득2회timeout과후속feature setup실패의 원인/exit/재시도는 결과 문서에 남겼다.
완료되지않은취득을features입력으로사용하는실행순서를수정했다.
완료파일별진행snapshot·이전URLschema/size/SHA검증을추가했다.
순서·허락목록확장과binary모델의학습입력/score범위·weight합·평가창의sharedhelper를검사했다.
최종head품질은미승인이라제품/모델assets/versionCode변경없다.

## Cleanup / residual / evidence

실패run중복음원은최종본과size/SHA를대조한뒤file별정리하며실패로그는보존한다.
공개35의2.07MB·특징67KB·head2.6KB는다음학습방식비교용활성자료다. 사용자원본/기존head/차단fixture보존.
이번OMX는qa-presence01 cwd로planning→qa→adversarial-e2e→cleanup→complete→clear/read부재확인을한다.
state·정리의최종결과는후속완료란에기록하며확인전완료를주장하지않는다.

소스분포·weaklabel·성인비명오인·목표원본미회복·Android미검증이남는다.
독립검토는이번diff의Standards/Spec 두축으로수행한다. issue-tracker.md는없고고정local계약을Spec으로사용한다.

증거: ignored `work/infant-head-20261007/{unit-presence01.log,guards-presence01,qa-presence01}` 및정상/실패run.
전체앱개선goal은active다. 연구도구완료만으로목표를닫지않는다.

## 이번 lifecycle 완료

실패run중복MP3 55개/3,126,690B를 최종본과 size/SHA 비교 후 개별 삭제했다.
최종35개/실패로그/소스·head·개인자료/과거삭제차단fixture는 보존했다. 근거는duplicate-cleanup35.json.
planning/qa/adversarial-e2e/cleanup/complete의success와exit0을확인했다.
clear는 실제응답`cleared:true`/exit0이었다. 처음wrapper가write용`success`필드를 기대해
예외를 냈지만state삭제실패가아니다. 응답schema를 확인하고read`exists:false`/exit0으로 복구했다.
새state쓰기나모델재실행은없었다. 이wrapper검사오류를정상통과에합산하지않는다.

**ULTRAQA COMPLETE: 연구 도구의 재현·입력 보호·종료·보존 목표를1cycle로 충족.**
제품품질실패와기존VTC cleanup BLOCKED는그대로다. 전체앱개선목표는계속active다.

## 독립 리뷰와 기록 누락 보완

고정diff `b40b251…686c6b2`의Spec필수0, Standards필수1(P2)/선택적중복제안1이었다.
Spec은source18fold/CV값·실행pin/종료/학습경계를확인했으나private점수를독립재추론하지않았다.
Standards P2는 RUNBOOK의 실험 시작/종료 용량 기록 누락이다. **이번 시작 관측값은 없다.**
이 누락을 이후 시작 snapshot으로 소급해소하거나 추정값으로 채우지 않는다.
종료·정리후 실측은 `storage-before-mil01.json`: 연구폴더6,543,648B/197file,
C여유77,443,268,608B다. 이 값에 제거3,126,690B를 더한9,670,338B는 다른 파일 변화가
없다고 가정한 산술 값이며 시작/정리직전 실측이 아니다. 두 값을 대신 보고하지 않는다.
필수 보완인 누락명시·종료관측연결을 적용했다. 다음 MIL은 실행 전 snapshot을 남겼다.
Adam중복은 계산오류없는 비차단판단이므로 이번실행근거를바꾸는 refactor는하지않았다.
