# UltraQA Report — 로컬 음성 연구 도구

2026-10-06. 범위는 새 AST 짧은창 및 PretrainedSED 연구 runner다.
제품 Android 앱 QA/아기 의도 정확도 검증/새 베타 배포를 대신하지 않는다.

## 목표·기준·경계

원음 불변·모델/원천/runtime 고정·손상 입력의 명확한 실패·오래된 성공 결과 미사용·실행 제한·자손 정리를 확인한다.
한 passing cycle 후 선택적 반복을 끝낸다. 원음/특징/개별 점수는 외부로 전송하지 않고, private 결과는 Git 제외한다.
제품 및 기존 작업 트리는 유지한다. 기본점 `d4f3e1874f4aa9d11b6b45cf042277c35121d091` 이후 tools/docs만 변경했다.

## 시나리오

| ID/의도·사용자 모델 | 준비·명령/하네스 | 기대 신호 | 실제 | 수정·근거 | 정리 |
|---|---|---|---|---|---|
| B1 정상 연구 입력 | 기존 보존3구간, AST runner / PretrainedSED runner | 고정 hash·finite·shape·완료·원음 불변 | 각각 exit0, 23창/3구간 및3구간 완료 | `retained-run01`의 private scores/process | 자손0, 원음 보존 |
| B2 숨은 충돌 | `test_ast_short_window_probe.py` 7조건 | TV/웃음 및 하위감정 충돌, child speech 단독과 다른 창 길이 충돌은 AST 양성으로 승격 안함 | 최종 고유7검사 pass/skip0 | 독립 Spec 지적 수정·기존 scores 요약 재생3/변경0·추론0 | 임시 입력 없음 |
| A1 손상 PCM·입력 작성자 | 실제 AST CLI에 잘못된 SHA manifest | nonzero, hash 오류, 성공파일 없음 | exit1, 기대 거부, 자손0 | `audio_screening_qa.py` / `final01/ast_bad_hash.log`·worker log | 합성 fixture 폴더 보존* |
| A2 NaN·입력 작성자 | 실제 PretrainedSED validate CLI, 올바른 hash의 NaN PCM | finite 오류가 정상 보류로 숨겨지지 않음 | exit1, 기대 거부, classifier 미실행, 자손0 | `final01/sed_nonfinite.log`·worker log | 합성 fixture 보존* |
| A3 traversal·악성 manifest | 실제 AST CLI, id=`../signal` | 잘못된 ID를 읽기 전에 거부 | exit1, ID 오류, 성공파일 없음, 자손0 | `final01/ast_traversal.log` | 합성 fixture 보존* |
| A4 stale 성공·재실행자 | 기존 완료 sentinel과 같은 output로 실제 CLI | 기존 결과 덮어쓰기/재사용 거부 | exit1/FileExistsError, sentinel bytes 유지, worker 생성 없음 | `final01/sed_stale_output.log` | sentinel 증거 보존 |
| A5 출력 경계·잘못된 CLI | output=`docs/qa-output-must-not-exist` | work 밖 쓰기 거부 | exit1, 경계 오류, 대상 생성 없음 | `final01/ast_outside_output.log` | 외부 파일 생성 없음 |

## 명령·실행 제한

- AST: `work/ast-portability/venv/Scripts/python.exe tools/research/ast_short_window_probe.py`
  `--model outputs/MoaAndroid/app/src/astProbe/assets/ast-dynamic-int8.onnx`
  `--config work/ondevice-av/ast-model/config.json --corpus work/sed-followup-20261006/retained-corpus`
  `--output work/ast-short-context-20261006/retained-run01` — exit0, Windows Job300초/2GiB.
- PretrainedSED: 연구 EfficientAT Python으로 `tools/research/pretrainedsed_probe.py`
  `--assets work/pretrainedsed-20261006 --corpus work/sed-followup-20261006/retained-corpus`
  `--output work/pretrainedsed-20261006/retained-run01` — exit0, Job180초/4GiB.
- `python -m unittest discover -s tools/research -p test_ast_short_window_probe.py -v` — 최종 exit0,7검사.
- `tools/research/audio_screening_qa.py --output work/audio-screening-qa-20261006/final01`
  `--ast-python <고정 AST Python> --sed-python <고정 EfficientAT Python>` — exit0,5/5 기대 거부.
  각 CLI60초 제한, 실제 worker의 Job과 자손 정리 결과까지 검사한다.
- `omx state` planning→qa→adversarial-e2e→cleanup, 이 run cwd와 child env만 사용했다.
  read/clear를 수행하고 최종 `exists=false`를 확인했다. 저장소 기존 state는 변경하지 않았다.

## 발견·수정

제품 결함을 발견했다고 주장할 결과는 없다. fixed false/NaN/hash/stale guard는 기대대로 동작했다.
독립 Spec 검토의 P2는 AST2초/4초간 전역 충돌 가드 미공유였다. 전체 새창을 공유하는
`summarize_variants`로 수정하고 양성2초+TV4초 반례를 추가했다. 기존23회 추론의 full scores를
실제로 재생해3구간 모두 요약 변경0을 확인했다. 추가 모델 추론은0이고 초기 결과는 보존했다.
Standards 필수수정0/비차단 네트워크 차단 중복1은 유지보수 제안으로 남긴다.
초기 smoke도5/5 통과했으며 lifecycle을 고정한 최종5조건을 추가 실행했다. 이를10개 고유 검사로 세지 않는다.
state helper가 처음 write의 `success`, read의 상태본문/`exists`, clear의 `cleared`를 같은 schema로 취급해
실제 CLI 성공을 harness 오류로 표시했다. action별 schema로 수정했고 최종 read exit0/existsfalse를 확인했다.
이 오류와 수정은 ignored run의 응답/스크립트에 보존하며 앱 오류나 테스트 실패로 바꾸지 않는다.

## 정리·남은 위험

실행 worker 자손은 모두0이고 격리 state는 제거됐다. APK/ADB/에뮬레이터/서버는 만들지 않았다.
**합성 corpus 폴더10개(run01/final01의5개씩) 재귀 삭제는 자동 승인 검토가 실행 전에 차단했다.**
출력은 구체적인 사유 없이 `blocked by policy`다. 다른 도구로 해당 삭제를 우회하지 않았다.
합성 PCM/manifest와 증거는 현재 남아 있고 실제 합성 payload는 총640KB다. 원본·제품 assets·키는 보존했다.
이 정리 제한을 숨기고 UltraQA 완료로 보고하지 않는다. 모델 품질 목표도 아직 달성되지 않았다.

**ULTRAQA BLOCKED: cleanup — 명명된 합성 fixture 폴더 삭제가 정책 검토에서 차단됨.**
기능검사7+실제 거부5는 통과했으며 인식 후보 조사·실험은 별도 활성 목표로 계속 진행한다.
다음 정리는 삭제 권한/정책 상태를 확인한 뒤 동일 명명 대상을 처리한다.

## 증거

- [실제 연구 결과](../research/20261006-short-context-and-pretrainedsed-results.md).
- `work/audio-screening-qa-20261006/final01/report.json`, `matrix-before.json`, 각 CLI/worker log.
- `work/audio-screening-qa-20261006/state-*-response.json`, `state_cli.py`와 최종 `state-read-response.json`.
- `tools/research/audio_screening_qa.py`, `test_ast_short_window_probe.py`.
