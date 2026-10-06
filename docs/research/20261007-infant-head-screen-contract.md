# 기존 YAMNet + 작은 영아 발성 head — 첫 실행 계약

2026-10-07. 공개 subset의 source snapshot을 고정한 뒤 **한 번의 caption-weak 개발 비교**를 한다.
원문 설명은 청취 정답이 아니므로 이 실험은 학습 가능성 검사이며 제품 승인이나 일반 정확도 시험이 아니다.
현재 source 및 제품 score·필터·판정 기준을 바꾸지 않는다. 사용자 원음/특징은 학습·정규화·calibration·augmentation 금지다.

## 고정 자료와 분할

공식 원작자 페이지의 현재 CC0/BY 허락·preview URL·과거 FSD 허락을 각각 snapshot에 유지한다.
현재 BY4.0으로 변경된 허락도 과거 BY3.0과 분리한다. lossless FSD PCM과 lossy preview를 같은 자료로 보고하지 않는다.
고정24 ID 밖 크롤링이나 전체 archive 다운로드는 하지 않는다. 파일당2MiB/취득 timeout20초,
전체취득 Job180초/2GiB. 같은 preview3개는 기존 SHA 확인 후 복사 재사용해 재다운로드하지 않는다.

| 역할 | infant_vocal | adult_vocal | other_or_uncertain |
|---|---|---|---|
| train | 113220,113221,113223,46963,47370,47371,377237,8157 | 184432,168494,87680,122204 | 256684,412332,148628,410616 |
| source validation | 18274,18277,18276,18273 | 241477,17643 | 51319,118659 |

Train16/validation8. 같은 source는 양쪽에 들어갈 수 없다. 영아 validation4는 같은 15개월 원천 그룹이므로 독립4아기라고 세지 않는다.
학습clip과 관련된 기존 public33은 회귀 자료다. 이 validation은 변경 선별에 쓰이므로 finaltest가 아니다.
사용자 자료는 기존2원본/3구간의 평가 전용이다. 두 누락 구간은 동일 원본에서 겹친다.
승인된 strong training label은 만들지 않는다. caption의 infant/adult/other를 **weak clip label**로만 사용하고 창별 정답으로 바꾸지 않는다.

## 첫 head / 비교 고정값

- 기존 고정 YAMNet의 tensor115 int8 특징을 memory-only output으로 노출한다. 기존521 score 전열 maxabs≤1e-6 유지.
- mono16kHz 실제 첫20초까지만 decode, overshoot clamp수/raw SHA와 처리후 SHA 분리. 전체실제 PCM에 DC/80Hz.
- 975ms nonoverlap 창, tail 오른쪽padding/실제시간 보존. RMS<.003 창은 pooling에서 제외한다.
- 학습 입력은 실제sample수로 가중한 **clip별 평균1024 특징**. 각 clip L2 normalize; quiet-only 자료는 거부한다.
- frozen1024→3 선형 softmax, 총3075param. 0 초기화, fullbatch Adam lr.03/300step,
  L2 penalty .01, float64 개발연산. 같은 class내 source당 동일가중, 같은 source내 clip당 동일가중.
- threshold 탐색·grid search·추가 augmentation·crop label·후보 반복 없음. validation은 training/정규화에 접근하지 않는다.
- 관찰값: class prediction/confusion, infant p≥.8 및 1위-2위 margin≥.4의 support.
  이 값은 calibrated probability/아기 정답 확률이 아니다. clip support와 시간별 support는 분리한다.
- 기존 retained3/public33 평가에서 원래 infant 수용 보존과 새 비아기 오인, 음악·성인 반례를 비교한다.
  head가 실패하면 같은 자료 threshold를 낮춰 제품에 넣지 않는다.

## 실행·QA·출구

Python3.12.14/NumPy2.5.3/LiteRT2.2.0/flatbuffers25.12.19를 고정한다. feature/training/evaluation은 offline,
각 Job180초/2GiB, exit/termination/잔여자손0과 SHA를 확인한다. 생성head·feature·media는 ignored 로컬만 둔다.
작은 head에 의미있는 신호가 있어도 weaklabel/독립영아/성인TV/AndroidARM/성능 검증이 남으면 제품승인으로 표현하지 않는다.
사용권·고정비교 이득·오인방지·Android 검증된 변경만 적용·두축리뷰·beta업데이트로 이어간다.

## UltraQA 사전 matrix

| ID | 의도 / 사용자·공격자 | 설정·명령 | 기대 | 실제/수정/증거/정리 |
|---|---|---|---|---|
| H01 | 정상 공개subset 연구 | acquire→features→fit→eval, 고정24 | 원천/SHA/score동등성/분할·종료 증거 | 후속 보고서 |
| H02 | 임의URL/허락변조 | in-memory source validation | 고정ID/CDN/license밖 거부 | 후속 보고서 |
| H03 | source누출/validation혼입 | 순수 합성 manifest | source overlap거부, 학습index train만 | 후속 보고서 |
| H04 | 비정상특징/무음 | NaN/shape/quiet-only 합성 | 실패, NaN승인없음 | 후속 보고서 |
| H05 | 오래된출력/해시변조 | 실제CLI 잘못SHA·기존output | classifier/다운로드전 nonzero | 후속 보고서 |
| H06 | 중단/초과시간/오해성공 | 기존 Job wrapper 및 report검사 | nonzero/잔여자손0, incomplete거부 | 후속 보고서 |
| H07 | 지시삽입 | description은 데이터로만 사용 | 명령실행/외부전송없음 | 후속 보고서 |
| H08 | 원래 작업/정리 보존 | status전후, 종료시 fixture/state | 기존 차단fixture 우회없음·앱미변경 | 후속 보고서 |

최대5 QA cycle, 같은3실패면 해당경로 종료진단. 전체 앱 개선목표는 별도로 active 유지한다.
