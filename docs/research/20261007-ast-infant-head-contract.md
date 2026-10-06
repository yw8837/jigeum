# 기존 AST768 + 작은 영아 존재 head — 실행 전 계약

2026-10-07. 기준 `f54eaf6`. [인터페이스 첫 검사](20261007-ast-embedding-interface-results.md) 이후의 첫 학습 비교다.
기존 YAMNet head의 누락과 성인 비명 오인을 해결하는지 확인한다. 제품 변경·새 다운로드는 없다.

## 입력과 시간 범위

- 기존 AST BSD-3-Clause 91,134,123B/SHA `3dff0cc2b374977ae1460258261608e33428f4767a6959cb8bc910b09433f71e`.
  메모리에 FLOAT768 출력만 추가, 모든527 logits exact 동일성 유지. CPU4/inter1/sequential/memorypatternoff.
- Python3.13.14/NumPy2.4.6/ONNX1.20.1/ORT1.24.4. 기존 frontend 그대로.
- 고정 public35 source SHA `27966aa9265da239489e7351959c01399ed3a0aed6428149babc2cb127fad4b9`와
  기존 MP3 bytes/SHA/공식 preview URL을 확인해 첫20초 PCM16 mono16k로 decode한다. gain/DC filter 추가 없음.
  이전 YAMNet float decode와 새 PCM16 quantization 차이를 숨기지 않는다.
- 비중첩 10초 span 최대2개. 1초 미만 public clip/tail은 **끝에만 zero waveform padding해1초로** 만든다.
  실제 sample수·padding·원음 RMS/입력 SHA를 따로 기록한다. 반복/가짜 음성/시간 늘리기 없음.
  이 span은 넓은 clip 분류 맥락이며 975ms 사건이나 실제 아기 발성 시간으로 보고하지 않는다.
- 학습 시 RMS>=.003 span의768 표현을 actual sample 가중평균 후 L2 정규화한다.
  quiet-only 학습 clip은 거부한다. 평가의 quiet span은 점수를 만들지 않는다.

## 학습·동결·비교

- 기존 train21/18source(영아7/4source), validation14/11source(영아4/1source) 유지.
  caption-weak이며 자연 아기 사건/혼합/욕구 정답이 아니다. 기존 held 재할당·새 blocked9clip 사용 없음.
- 768→1 sigmoid,769parameter/FP323076B. 기존 binary와 같은 zero초기화/Adam.03/300step,
  class/source/clip 균등가중과 L2 `.0001,.001,.01`, train-only leave-one-source-out macroNLL 최소/동률 강한L2.
  train 선택·fit 결과와 weightSHA 저장 후 validation에 접근한다.
- support p>=.8 / binary margin>=.4 유지. score는 calibrated 정답확률이 아니다.
  FP64/저장FP32 차이5e-5 이내를 각 span/clip에서 검사한다.
- head 동결 후 retained3/2원본과 기존 public33 평가. 사용자 자료는 평가 전용이며 외부 전송·학습·정규화 적합 금지.
  두 약국 누락은 같은 원본의 겹치는 구간이다. public33은 개발/합성/중복source이며 독립 test가 아니다.
- 실제 누락 회복과 성인 웃음·비명·대화·음악·TV·잡음 오인, 기존 승인 보존을 함께 판단한다.
  실패 후보는 앱에 넣지 않는다. 통과도 broad span만으로 문장/의도/화자를 확정하는 승인이 아니다.

## 실행·QA·정리

features/eval Job180초3GiB, fit180초2GiB/offline/자손0. 각 완료SHA·exit·시간·peak 보존.
두 session 연구 메모리를 Android 한 session 성능으로 보고하지 않는다. 모델 파일 저장 없음.

| ID | 시나리오·모델 | 실행/기대 | 기록·수정·정리 |
|---|---|---|---|
| A01 | 정상 연구 | feature35→trainCV→val→eval3+33 | source/byte/SHA/527 parity와 actual span |
| A02 | 잘못된 숫자·짧은 소리 | empty/NaN/shape/zero/짧은 tail | 거부 또는 끝padding, 실제시간 보존 |
| A03 | 누출/낡은자료 | validationrole/틀린sourceSHA/headSHA/modelSHA | 거부·기존파일 보존 |
| A04 | CLI·출력경로 | missing/stale/outside/wrongSHA | 실제 nonzero·신규출력 없음 |
| A05 | 실패/종료/거짓성공 | Job한도·completion mismatch·자손 | bounded exit·SHA·잔여0 검사 |
| A06 | dirty/state/storage | 격리OMX/전후용량/description | 지시실행 없음·다른변경 보존·이번state clear |

최대5cycle/동일실패3회 중단. 품질실패와 도구QA를 구분하고 전체 개선goal은 active 유지.
시작/종료 폴더 용량과C여유를 기록한다. 기존35음원 재사용·새음원/대형모델 생성0,
로그·source·작은특징/head만 보존하고 종료 중복 생성물은 정리한다.
