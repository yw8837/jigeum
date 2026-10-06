# UltraQA Report — 공개 MIL 연구 경로

2026-10-07. [사전계약](../research/20261007-infant-mil-contract.md)의고정공개bag학습·source분리·오류/종료·보존목표다.
온디바이스/private평가전용/Job180초2GiB/CLI30초/최대5cycle·동일실패3회중단.
품질은[실측미회복](../research/20261007-infant-mil-results.md)이며앱변경/배포승인이아니다.

## Scenario matrix

| ID | 의도·모델 | setup·command | 기대/실제 | 수정·증거 | 정리 |
|---|---|---|---|---|---|
| M01 | 정상연구자 | MILfit/val14/eval3+33 | 正常3run exit0/한도내·train21만 | process/completion/selection | worker0/공개특징재사용 |
| M02 | 잘못된bag·quiet | chrono/shape/NaN/quiet | 거부·실제시간2:1가중·windowL2 | test_infant_mil_probe unit | 메모리fixture |
| M03 | 잘못된수치·미분 | ±1000/logmeanexp/central difference | overflow없음·analyticgradient8자리일치 | unitgradient | 모델정답시험아님 |
| M04 | 누출·학습오류 | validationrole/held source/weight합·class소실 | 거부/18source의held와train분리 | unit/CV | 사용자학습0 |
| M05 | CLI/stale | wrongSHA/stalehead/outside/missing | 실제4/4nonzero·head불변·새output0 | guards-mil01 | 신규악성fixture0 |
| M06 | 종료·성공오해 | exit/runnerSHA/자손/한도 | 모두일치·누락/오인품질실패는명시 |3run process/기존Job10 | 새Job검사반복안함 |
| M07 | dirty/state/storage | 격리OMX/전후용량 | lifecycle·clear/read부재·다른변경보존 | qa-mil01/storage2snapshot | 차단fixture미변경 |

## Commands, failures, fixes

baseline `python -B -m unittest discover -s tools/research -p test_infant_*.py -v`: 고유26/exit0.
기존21+새MIL5이며이전26중복실행으로합산하지않는다. `infant_head_screen_cli_qa.py --mil`실제4/4/exit0,
classifierforward/network/newfixture0. 正常3run은실제 모델실행이며summary의success문구만으로검증하지않았다.
교차분석산식중앙차분·source홀드·약국미회복·성인비명2.925초오인과publicbabble미검출을함께보존했다.
품질실패라제품/sourceassets/versionCode/출시범위변경없다. 약국판정에맞춘threshold변경도없다.

초기‘encoder추론없음’계약문구는학습특징재사용만을뜻했지만평가에서는새encoder추론이있었다.
이문구를명확히하고실제평가실행을결과문서에명시했다. 데이터를훈련으로옮기거나private전송은없었다.
특징배열SHA/sourceSHA/원모델SHA를고정하고동일preview취득을반복하지않았다.

## Cleanup / evidence / residual

MIL은새음원/encoder모델file없음. 기존35preview·features·종료후작은head와재현로그만유지한다.
planning→qa→adversarial-e2e→cleanup→complete모두write success/exit0,
clear`cleared:true`/read`exists:false`/exit0을확인했다. 기존OMX/차단fixture/개인원본/다른작업자파일보존.

전후실측은 `storage-before-mil01.json`/`storage-after-mil01.json`:
연구폴더6,543,648→6,873,434B/197→235files, C여유77,443,268,608→76,472,508,416B.
폴더증가329,786B와C여유변화는일치하지않으므로C의전체변화를이실험사용량으로보고하지않는다.
기존binary실험시작snapshot누락은별도QA에서유지하고이번전후측정으로소급대체하지않는다.

증거: ignored `work/infant-head-20261007/{unit-mil01.log,guards-mil01,qa-mil01,mil35-01,retained-mil01,public-mil01}`.
weaklabel/source결손·새source검수·Android/실폰·아기욕구정답이남는다.

**ULTRAQA COMPLETE: 연구 경로 재현·입력 보호·종료·보존 목표를1cycle로충족.**
전체goal은active/제품미탑재다. 다음source확장과기존AST표현을활용할수있는지조사를이어간다.
