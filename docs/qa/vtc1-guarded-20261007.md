# UltraQA — VTC1 전처리·무음 제외 연구 경로

2026-10-07. [사전 계약](../research/20261007-vtc1-guarded-contract.md).
목표는 원래 floatgraph 승인과 구별된 전처리/무음 제외 경로의 실행 검증이다.
일반 아기 정확도·Android·상업 사용권·제품 배포 검증으로 합산하지 않는다.
소스/원본 불변, 네트워크 없음, Job180초/3GiB와 모든5열5e-4 유지가 안전 경계다.

## 실행 전 시나리오 행렬

| ID | 의도/입력 | 경로 | 기대 신호 | 실제 결과/증거 | 정리 |
|---|---|---|---|---|---|
| U1 | 길이/타입/shape/NaN/Inf/범위 오류 | 단위 invalid PCM | 추론 전 거부 | 6개 단위 전체 exit0; 상세는 아래 갱신 | 파일 생성 없음 |
| U2 | DC와 긴 무음 속 짧은 소리 | 필터/RMS 불변 검사 | DC 제거/원본 보존/1초 burst 희석 없음 | 위 단위에 포함 | 메모리 fixture |
| U3 | quiet 창과 invalid classifier 출력 | guarded forward | quiet classifier0/zero support, NaN 거부 | 위 단위에 포함 | 메모리 fixture |
| N1 | 고정 합성5조건 | 실제 Torch/ORT | gate 내/quiet skip | quiet4조건zero/skip, nonquiet tone오차.000133947 | 새graph/PCM 파일 없음 |
| N2 | 보존3구간/2원본 | 실제 native slide | 원래 좌표/전열 parity, 아이 단서 보존 여부 측정 | 3구간 최대1.38879e-5, 두 누락KCHI신호 유지 | 원음 보존 |
| N3 | 공개33조건 | 신호 유지 뒤 처음 실행 | 동등성 측정/오인 유지 여부 기록 | 전열 최대.000354439, child활성 여부 변화0, 피아노오인 남음 | 원음 보존 |
| H1 | 손상 graph bytes | 실제 CLI | nonzero/성공 report 없음/자손0 | exitnonzero/성공없음/자손0 | 새fixture 삭제 자동검토 차단 |
| H2 | 기존 output 재사용 | 실제 CLI | nonzero/기존 결과 불변 | exitnonzero/기존SHA보존 | 기존 증거 유지 |
| H3 | 거짓 성공/원래 rawgraph 실패를 감추기 | 메타데이터·독립 검토 | gate/raw_float_graph_accepted=false/품질false 유지 | 두 축 지적0/원래무음실패유지 | 보고 범위 검토 |
| H4 | 과대 sample domain | 실제 CLI | file/추론 전 거부 | exitnonzero/성공없음/자손0 | 새fixture 삭제 자동검토 차단 |
| H5 | traversal case ID | 실제 CLI | file/추론 전 거부 | exitnonzero/성공없음/자손0 | 새fixture 삭제 자동검토 차단 |

CLI는 새ignored output에만 실행한다. 동작 문제가 발견되면 최대5 cycle,
같은 실패3회 시 그 경로의 반복을 중단하고 근본 원인과 별도 다음 후보를 진행한다.
취소/시간초과/Job자손 정리는 기존 bounded supervisor10개 검사를 근거로 구분한다.
이번 연구의 취소 검사를 Android 앱 취소 검증이라고 표시하지 않는다.

## 현재 단위 실행

`python -B -m unittest discover -s tools/research -p test_vtc1_guarded_probe.py -v`:
고유6/exit0. PCM domain 거부, DC 제거/불변, burst 희석 방지, 실제 모든 offset RMS와 일치,
quiet classifier 미호출, nonquiet NaN 출력 거부를 확인했다. 실모델/원음 검증은 별도다.

## 실제 모델 비교와 명령

최종 runner `823dd4e5…`/고정 `15b86c9`는 두 run report와 같다.

```powershell
python -B tools/research/vtc1_guarded_probe.py --assets work/vtc1-20261006 --graph work/vtc1-20261006/export01/vtc1-float.onnx --corpus work/sed-followup-20261006/retained-corpus --output work/vtc1-guarded-20261007/retained01
python -B tools/research/vtc1_guarded_probe.py --assets work/vtc1-20261006 --graph work/vtc1-20261006/export01/vtc1-float.onnx --corpus work/ast-public-paired-01/corpus --output work/vtc1-guarded-20261007/public01
python -B work/vtc1-guarded-20261007/hostile_cli.py
```

실제 interpreter는 `C:/Users/최용우/AppData/Local/Temp/agattok-efficientat-20261005/venv/Scripts/python.exe`다.
retained exit0/9.486102초/peak1,193,951,232B, public exit0/127.476856초/peak1,263,157,248B,
두 Job180초/3GiB/자손0이다. 합성5조건은 두 run에서 같은 입력을 사용했고 고유10조건으로 늘려 세지 않는다.
retained49창/backend에서 실제classifier31/quiet제외18, public636창/backend에서 실제classifier570/제외66이다.
report의 총counter에는 각run의 합성classifier1/제외4도 들어 있다.

두 누락KCHI 활성 합계는 원음2.8738/.6868초→필터·guard2.1398/.6514초다.
원래 signal 전부를 그대로 보존했다는 뜻은 아니며, 두 누락에서 유효 단서가 남았다는 결과다.
별도cry대조의 성인/아이 신호가 섞이므로 VTC 단독 사건 타입/화면 주인을 승인하지 않는다.
공개33의 어떤child 활성 여부22/33은 전후 같고 정확도가 아니다. 피아노KCHI약4.0348초 오인은 남았다.
성인대화·청소기·무음/잡음대조에 새child 활성은 없다. TV·새 독립 영아 사건 평가는 여전히 부족하다.

hostile CLI4/4 nonzero·성공report없음 또는 기존결과불변·자손0을 확인했다.
단위6은 최초와 로그보존 실행에서 모두 통과했으며 고유12개로 세지 않는다.

## Standards

독립 `124f06c...15b86c9` 검토에서 이전 graph provenance P2 해소,
새 규칙 위반0/휴리스틱0. 고정 runner와 retained report SHA 일치, domain/Job/offline/원본경계를 확인했다.

## Spec

동일 고정검토에서 필수지적0. 실제 전체filter/창별RMS 제외/원래slide·coverage·Binarize/5e-4 유지와
rawfloat실패·제품미변경을 확인했다. 공개33은 그 당시 진행중이라 검토자가 읽지 않았고,
개별음성/PCM/score/event 열람·독립추론은 하지 않았다. 이번 public/CLI 후속 metadata의 확인 범위는 이후 기록을 따른다.

## 정리와 종료 상태

새fixture3파일(합계3,065B)·빈폴더2개의 native PowerShell 개별삭제가 **자동승인검토 `blocked by policy`**로 거절됐다.
삭제 명령은 실행되지 않았고 다른도구로 우회하지 않았다. `guards01/cleanup-blocked.json`에 정확경로/bytes/잔존을 기록했다.
bad-graph.onnx, invalid-corpus/manifest.json, traversal-corpus/manifest.json은 이번범위에 남아 있다.
이전 삭제차단10fixture와 혼동하지 않는다. 원본·실험graph/weight·로그는 보존했다.
QA lifecycle는 outcome=blocked로 종료하고 이run의 state만clear·read existsfalse를 확인했다.
진행중인 제품 개선 전체목표는 active이며 이 파일의 종료는 해당QA 실행 범위에 한정된다.

**ULTRAQA BLOCKED: 신규fixture 정리 자동승인검토 차단.**
행동검사·동등성 비교는 통과했으나 cleanup gate가 남아 COMPLETE로 표시하지 않는다.
현재 다음 행동은 음악/성인 오인 결합 및 별도 합법적 학습자료 검수다. 아직 제품탑재/새beta/일반정확도 개선이 아니다.

증거: ignored `work/vtc1-guarded-20261007/{retained01,public01}/{report-private.json,process.json,comparison.log}`,
`unit.log`, `public-delta.json`, `guards01/{report.json,cleanup-blocked.json}`, `state-read-response.json`.
