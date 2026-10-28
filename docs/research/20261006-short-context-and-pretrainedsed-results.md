# 누락 발성 후속 실행 — 짧은 AST 맥락과 PretrainedSED

2026-10-06. 조사 이후 실제 로컬 추론을 실행했다. **제품 모델·버전·문장·권한은 아직 변경하지 않았다.**

## 고정한 비교와 실제 결과

| 비교 | 입력·추론 | 관찰 | 결정 |
|---|---|---|---|
| 기존 AST INT8의 비중첩 2초/4초 | 보존3구간/2원본, 기본3회+짧은20회=23회 | 알려진 누락 두 구간 모두 기존 양성 조건 미달. 감정·TV 충돌 가드를 풀어도 양성 자체가 없음 | 추가 분석 비용을 정당화하지 못해 제품 채택하지 않음 |
| PretrainedSED `frame_mn10_strong_1.pt` | 동일 보존3구간/2원본, 공식 447라벨/10초당250 strong출력 | 누락 두 구간과 울음 대조의 해당 아기 클래스 모두 관찰용 .5 이상의 위치0 | 모델 교체나 새로운 회복을 입증하지 못함. 임계값을 낮춰 성공으로 만들지 않음 |

두 누락 구간은 한 원본에서 서로 겹친다. 독립 아기 두 명이나 새로운 holdout이 아니다.
원래 사용자 발성 제보는 재현 가설이며, 현재 독립 청취 정답이 확보된 것이 아니다.
PretrainedSED의 raw sigmoid를 AST와 같은 확률 척도로 비교하거나 .5를 제품의 새 기준으로 채택하지 않는다.
따라서 이 결과를 인식률0%·정확도 개선·욕구 예측 성능으로 변환하지 않는다.

AST는 [실행 전 계약](20261006-ast-short-context-contract.md)의 2초/4초 위치·기존 양성 및 충돌 조건을 유지했다.
4초 창에서 남는 2초 tail은 새 위치를 탐색하지 않아 제외되며 기존 10초 창에서 포함된다.
짧은 분류 창을 실제 발성 시작점이나 화면 속 발성 주인으로 보고하지 않았다.

PretrainedSED는 [원천 계약](20261006-new-infant-engine-next-experiment.md)의 공개 가중치 한 개를
실제 내려받아 **15,537,114B**, SHA256 `4a0fe320d5369987b772394c51881fb20a602967e6842f72f3e5c8181065ece7`를 확인했다.
312개 key와 두447-way head의 완전 대응을 확인하고 `strict=True`로 로드했다.
원래 전처리·10초 독립 chunk·오른쪽 zero-padding·실제 표본 길이 crop을 유지했다.

## 실행·자원·무결성

- 두 runner 모두 공개 소스/모델을 먼저 고정하고 추론 단계에서는 Python 네트워크 연결을 차단했다.
  아기 원음/특징/개별 점수를 외부 서비스로 보내지 않았다.
- AST process: exit0, 22.442초, peak aggregate committed1,405,337,600B, 2GiB/300초 상한,
  cleanup=true·남은 자손0. PretrainedSED: exit0, 8.232초, peak1,079,975,936B,
  4GiB/180초 상한, cleanup=true·남은 자손0. 커널 committed 계측이며 RSS가 아니다.
- PC host 전체 프로세스 시간이다. 초기화와 파일 읽기를 포함하며 Android/S25 속도·발열 결과가 아니다.
- 입력의 바이트·SHA·16kHz mono float32·유한성·진폭·표본수·길이를 검증했다.
  추론 후 PCM 해시도 동일하다. 보존 PCM 검증이며 사라진 원본의 디코더를 다시 실행한 것이 아니다.
- AST 보조 harness의 고정 위치·invalid length·전체선택 TV·추가창 웃음·하위감정·child speech 단독
  반례 및 다른 창 길이의 충돌 공유7검사는 실패/skip0으로 통과했다. 소리 모델 정확도 검사는 아니다.
- 최초 runner는 각 창 길이의 새 충돌만 해당 variant와 baseline에 적용했다. 독립 Spec 검토가
  실행 전 계약의 **모든 새 짧은 창** 공유 요구와 차이를 지적해,2초/4초 전체 충돌을 공유하도록 고쳤다.
  기존23회 full scores로 요약만 재생했고3구간 모두 변경0이었다. 새 모델 추론0회이며 기존 원시 결과를 덮어쓰지 않았다.
  `work/ast-short-context-20261006/cross-context-guard-replay.json`에 전후 runner/score hash와 요약을 보존했다.

## 다음 실제 비교

공식 소형 PANNs checkpoint의 정확한 source/labels/frontend와 CC BY4.0 허락 범위를 확인해
실제로 실행하지 않았던 다른 표현 방식을 비교한다. 동일 모델의 점수 완화·창 위치 탐색·gain 반복을 하지 않는다.
기존 AST/YAMNet의 보존 여부와 성인·TV 오인 대조가 함께 확인된 개선만 Android 적용 단계로 넘긴다.

## 비공개 증거

개별 입력·점수는 Git 제외 경로에만 둔다.

- `work/ast-short-context-20261006/retained-run01/scores-private.json`, `process.json`.
- `work/pretrainedsed-20261006/retained-run01/scores-private.json`, `process.json`, `inference-private.log`.
- `tools/research/ast_short_window_probe.py`, `pretrainedsed_probe.py`, 각각의 pin과 실행 전 계약.

다운로드 후보와 임시 source는 후속 비교에 필요할 때만 보존하며 결정 후 RUNBOOK 기준으로 정리한다.
