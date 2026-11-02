# 동결 AST 영아 head의 ONNX 연결 — 실행 전 계약

2026-10-07 KST. [head 첫 결과](20261007-ast-infant-head-results.md) 이후의 첫 그래프 이식 검사다.
Train/threshold/weights를 바꾸거나 새 아기 자료를 학습하지 않는다. 앱에 바로 탑재하는 승인이 아니다.

- 기존 AST size/SHA/852node/공식 BSD3 고지와 input/logits/node/weight를 유지한다.
- 저장 head SHA `81d7357c4c59f57222952a1542716f7d89e423d45f2987dd3917a3522bdfd510`, FLOAT768 weights+scalarbias.
  기존 layernorm768 표현의 L2→MatMul→Add→Sigmoid만 연결한다.
  이름 충돌을 거부하고 norm floor1e-12(훈련의 정상 표현에서는 미사용)를 쓴다.
  원logits/FLOAT768와 새 `infant_presence` FLOAT[1,1]을 출력한다. 모든원node/weight를 바꾸지 않는다.
- Python3.13.14/NumPy2.4.6/ONNX1.20.1/ORT1.24.4/CPU4/inter1/sequential/memorypatternoff.
  원본·파생2session, 원527logits exact equality/차이0, NumPy 동결head와 새score maxabs5e-5.
  fixed support .8/.4의判定동일성도 요구한다. 불일치하면 gate를 완화하지 않는다.
- 첫 입력은 이미 취득한 public113221/184432/256684의 첫10초 PCM16 및 합성 silence1/tone10/sweep2.
  이후 retained3의 실제 각구간과 합성20초tone을 **Android와 같은0–10/5–15/10–20초**로 처리한다.
  retained3는 평가 전용/같은 원본의 겹치는 누락2개. 합성20초는 시간축 계약이며 영아 품질 정답이 아니다.
- offline/Job120초3GiB/자손0/completion·실행codeSHA·원모델전후SHA를 기록한다.
  생성/세션시간과 연구두session을 휴대폰 성능으로 보고하지 않는다.
- `--export`를 명시한 이번 run은 이 정확한 graph91MB대 파일 **한 개**를 ignored run 안에만 저장한다.
  Android 후보 후속에 필요한 복사본이다. 원제품asset은 변경하지 않으며 새다운로드는 없다.
  size/SHA와 추가 bytes를 기록하고 다음 후보시험이 끝나면 RUNBOOK대로 정리한다.

성공도 후보 그래프의 수치 연결 검사다. 약한데이터의 일반 인식·욕구 정답·추가source·Android 전체경로·ARM은 남는다.
다음은 별도source와 연구Android 후보. 이 성공만으로 code32를 교체하거나 공개출시하지 않는다.
