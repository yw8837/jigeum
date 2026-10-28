# UltraQA Report — 첫 공개 weakhead 연구 도구

2026-10-07. 목표는고정public취득→feature→weakfit→local평가의재현/오류거부/종료·보존이다.
**아기인식률향상·Android·제품배포승인이목표가아니다.** 그실측/미회복은
[첫결과](../research/20261007-infant-head-first-results.md)를따른다. 전체앱개선목표는active다.
사전matrix는[계약](../research/20261007-infant-head-screen-contract.md), 최대5cycle/같은3실패종료.
온디바이스·user평가전용·소량허락public·각Job180초2GiB·CLI30초·기존작업보존.

## Scenario matrix

| ID | 의도/모델 | 설정·명령 | 기대/실제 | 수정·증거 | 정리 |
|---|---|---|---|---|---|
| H01 | 정상연구자 | infant_head_screen acquire/features/fit/evaluate | 실제23/153창·score차이0·fit/3+33평가 exit0 | 각run completion/process | 자손0, 소량활성자료유지 |
| H02 | 임의URL/허락변조 | puretest exactID/CDN/NC/evilhost | 모두거부 | unit-head.log | 메모리fixture만 |
| H03 | 누출/혼입 | duplicate/missing/47370/sourceoverlap·validationweight | 모두거부,15train만fit | 고유unit검사 | 기존manifest불변 |
| H04 | 잘못된특징/무음 | NaN/shape/zero/quiet-only/실제시간가중 | 거부·정상weight2:1·quiet배제 | 고유unit검사 | 메모리fixture만 |
| H05 | 실제오류CLI | wrongSHA/stalehead/outside/missingargs | 4/4nonzero·기존head SHA불변·새output없음 | guards-head01/report.json | 신규fixture0 |
| H06 | 중단/자원/오해성공 | 기존test_bounded_process suite | 고유10pass·timeout3세대/memory/orphan/exit7/flood | recoveredlog | test임시폴더정리·PID부재 |
| H07 | 지시삽입 | description내uploadsecret문구 | 데이터로만검증, 실행/외부전송없음 | unitexactsource검사/정적경로 | 메모리fixture만 |
| H08 | 작업/정리보존 | Gitstatus·OMX隔離state·기존출력검사 | 제품/이전차단fixture미변경,exists=false | qa-state01/absent.json | 이번stateclear |

## Commands / 실패·수정

baseline`test_infant_head_screen.py`고유7/exit0. 둘째호출은같은검사의로그보존용이며14개라고합산하지않는다.
실제`infant_head_screen_cli_qa.py`4/4/exit0, 네트워크요청/분류기forward/새fixture0.
원음실행5단계와실제resource정보는첫결과table및각process.json을따른다.

Job검사첫호출은model-059 Python에psutil이없어ImportError/exit1이었다. 제품결함/통과검사로세지않는다.
새패키지를설치하지않고기존ast-portability Python3.13.14/psutil7.2.2에서동일10검사를실행해exit0이었다.
이는model-059 Python3.12.14의실제5run resource기록과별도로표시한다.
동일실패3회·cycle5·무한재시도는없다. 실제4CLI는subprocess30초이며성공문구만으로통과하지않는다.

## Cleanup / residual / evidence

이번CLI는악성filefixture를만들지않고기존sources/head를읽기전용으로사용했다.
실험publicpreview1.32MB/feature42KB/head6KB는다음CV비교용으로필요하여활성유지한다.
종료worker자손모두0, Jobunit임시자손/폴더도정리됐다. 기존VTC cleanup차단fixture는건드리지않았고
그이전UltraQA BLOCKED를이번완료로해소했다고표시하지않는다.

OMX planning→qa→adversarial-e2e→cleanup→complete를성공JSON/exit0으로확인한뒤clear/read existsfalse다.
첫unit검사는planningstate생성전에도실행했으며현재baseline로그는state qa후실행으로구분한다.
증거: ignored `work/infant-head-20261007/unit-head.log`, `unit-job-head.log`(실패),
`unit-job-head-recovered.log`, `guards-head01/`, `qa-state01/`, 각normalrun completion/process.
공개미디어도Git/앱에포함하지않는다. 첫head는사용자첫누락미회복이므로제품미탑재다.

**ULTRAQA COMPLETE: 연구 도구 재현·입력/실행 보호 목표를1cycle로충족.**
이는전체앱목표완료나아기분류정답검증을뜻하지않는다. 다음학습source내설정검토·새source·오인회귀를이어간다.
