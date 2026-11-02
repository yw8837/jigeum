# UltraQA Report — AST768 연구 학습·평가 경로

2026-10-07 KST. 목적은 [고정 계약](../research/20261007-ast-infant-head-contract.md)의
공개 source 학습·평가 경계, 짧은 입력·실제 span, 오류 거부·종료·보존 확인이다.
앱의 일반 인식 성능·욕구 번역·Android·배포 완료를 승인하는 QA가 아니다.
사용자 원음/특징 외부 전송·학습 금지, Job180초/2 또는3GiB, CLI30초, 최대5cycle/같은 실패3회 중단.

## Scenario matrix

| ID | 의도·모델 | setup/command | 기대와 실제 | 수정/증거 | 정리 |
|---|---|---|---|---|---|
| A01 | 정상 연구자 | feature35→fit/val14→eval3+33 | 정상4run exit0; 모든527logits exact; 실제35clip/43span | process/completion/source selection | 원음/기존asset 보존 |
| A02 | 잘못된 수치·짧은 소리 | empty/NaN/Inf/shape/zero/20초·1sample tail | 거부/끝padding·실제sample 합 보존·quiet score 없음 | 새unit5 | 메모리 fixture |
| A03 | 훈련 누출·낡은자료 | validationrole/출처fold/기본dimension | 거부·train-only18fold; 기존1024 기본 유지 | 기존unit26+새768 guards | held 재할당0 |
| A04 | CLI오류·출력 침입 | wrongSHA/stale/outside/missing | 실제4/4nonzero; head불변·신규output0 | guards-ast-head01 | 새 악성fixture0 |
| A05 | 거짓성공·실행 종료 | exit/completion6codeSHA/Job한도/자손 | 4run 모두 일치·termination없음·cleanuptrue/자손0 | process/completion | native worker0 |
| A06 | 지시삽입·dirty·state/storage | description데이터·격리OMX·전후용량 | 기존source unit의 지시실행0; 다른변경보존 | state/용량 기록 후속 아래 | 기존 차단fixture 미변경 |

## Commands and actual verification

- `python -B -m unittest discover -s tools/research -p test_ast_infant_head_probe.py -v`:5/exit0.
- `python -B -m unittest discover -s tools/research -p test_infant_*.py -v`:26/exit0.
- `python -B -m unittest discover -s tools/research -p test_ast_embedding_interface.py -v`:2/exit0.
  고유33개이며 중복 합산하지 않았다. 학습 신호의 합성 검사를 실제 영아 정답으로 보고하지 않는다.
- `infant_head_screen_cli_qa.py --ast-head`: 실제4거부/4통과, exit0; classifier forward/network/new fixture0.
- `ast_infant_head_probe.py features/fit/evaluate`: 정상4run, source/model/runner/frontend SHA 및 resource 종료 일치.
  실행 시간·peak은 [실측 결과](../research/20261007-ast-infant-head-results.md)의 표를 따른다.

## Failures and fixes

정상 연구 경로와 hostile guards의 최종실패/skip0. 초기 unit/setup 실패도 없었다.
실제 품질에서는 다른 울음·짧은publicbabble·held영아2개 미통과가 남는다. 이는 tool QA 성공으로 지우지 않는다.
약국 두누락의 broad 지원은 첫 개선 단서이며 같은 원본의 겹치는 개발 구간이다.
제품 모델/문장/임계값/versionCode/이용권 정책은 변경하지 않았다.

shared binary fit/select에 명시dimension768을 추가하면서 기존default1024 계산 순서를 보존했다.
AST 짧은 waveform padding과 원음 RMS/actual sample을 분리하며 quiet/invalid 표현은 거부한다.
큰시간창을 실제 발성 duration으로 보고하지 않는다.

## Independent code review

고정 `f54eaf6…4f85c52`의 두축 리뷰: **Spec 필수0 / Standards 문서규칙 위반0**.
Spec은 Android20초3중첩창과 연구2비중첩span 차이, broad 단서/일반정확도 구분을 후속 보고조건으로 유지했다.
Standards는 `AstRepresentation`/기존 interface worker의 setup 중복을 선택적 Duplicated Code1건으로 보고했다.
현재 오류 증거 없는 비차단 제안이며 이번 실행 source를 사후 바꾸지 않았다.
결과/QA/HANDOFF 문서는 후속 커밋이며 해당 고정 코드리뷰로 검토했다고 소급하지 않는다.

## Cleanup, evidence and residual risks

새 음원/대형 weight/model 파일0.35개 preview를 재사용했고 small feature/head와 출처·로그는 후속용으로 유지한다.
새 adverse disk fixture0, 기존 자동검토 차단 대상·개인원본·제품asset·다른작업은 보존했다.
격리OMX planning→qa→adversarial-e2e→cleanup→complete 모두 write success/exit0,
clear `cleared:true`/read `exists:false`/exit0을 확인했다. 최초 complete 입력의 수동시각은
실제 UTC로 수정해 같은 run에서 재write/clear/read 부재를 확인했다. 앱 저장 state는 건드리지 않았다.

전후 `storage-before-ast-head01.json`/`storage-after-ast-head01.json`:
연구폴더6,918,926→7,201,576B/250→284files, 증가282,650B.
C여유76,535,652,352→76,574,986,240B. C전체변화와 연구폴더 증가는 다른 수치이며
드라이브 변화 전부를 이 실험 정리 효과로 보고하지 않는다.

증거는 ignored `work/infant-head-20261007/{qa-ast-head01,guards-ast-head01,ast-features35-01,ast-head35-01,retained-ast-head01,public-ast-head01}`.
PC 두 session은 제품 한 session 성능이 아니다. strong 사건 라벨·새source·Android 중첩20초·ARM·TV·실제욕구 평가가 남는다.
전체앱 개선goal은 active이며 제품 미탑재다.

**ULTRAQA COMPLETE: 연구 학습·평가 경로의 보호·재현·종료·보존 목표를1cycle로 충족.**
