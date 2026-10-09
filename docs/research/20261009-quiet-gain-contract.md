# 작은 녹음 음량 회복 실험 — 2026-10-09

기준 a064170 / 배포 code34. 원음·기록·사용자 자료 전송/학습 없음.

## 재현
기존 Android laugh_rms6 trace를 실제 SoundTimeline/EvidenceInterpreter에 재생:
`work/quiet-gain-20261009/host.py red01 QuietGainRecoveryTest` exit1,
1검사/1실패, INFANT_UNCONFIRMED. 원본 laugh는 승인됐으나 감쇠본에서 누락.
원본 소스의 같은 변형이므로 독립 영아 표본으로 세지 않는다.

## 사전 고정 실험
모델·분류 threshold·필터는 유지한다. 실제 측정된 strongest-second RMS가
[.003,.012)일 때만 한 번의 일정 gain을 사용: min(4, .012/RMS, .95/peak).
.003은 기존 무음 gate, .012는 기존 activity 측정 floor다. 새 숫자로 정답에 맞추지 않는다.
이것은 음원 분리나 인식 정확도 보장이 아니다. gain1을 상회하지 않으면 변경하지 않는다.
실험에서는 입력 전체를 증폭한 연구 복사본으로 모델의 레벨 민감성만 확인한다.
후보 제품은 채택 전 원래 PCM 음량으로 최종 gate/증거를 보존해야 한다.

28조건: 기존8원본 × 원음/.006/.009 + 무음/DC/rumble/noise4.
성인대화/일반웃음/음악/청소기 등16대조의 신규출력0, 기존양성보존,
actual model inference와 기록해시·오류를 확인한다. 실패 시 threshold 탐색은 하지 않는다.
이득이 있으면 추가53기존조건과 Android/서명업데이트/저장·권한 회귀 후 기존Alpha에만 배포한다.
새 모델/추가 graph/대형 다운로드는 없다. 실행시간·메모리는 별도 측정한다.

## 가설
1. 낮은 level에 대한 모델 민감성: bounded gain으로 같은 사건이 회복되는가.
2. 창 경계: 기존 shifted path만으로 같은 입력이 회복되는가.
3. 최종 gate: 승인 모델 사건이 최종해석에서만 사라지는가.
현재 laugh_rms6는 결정단계에서이미미승인이라3단독설명은아니다.

## 결과
실제 Android 모델로 사전 고정한 28조건을 실행했다. 회복은 0조건이며,
`laugh_rms6`는 INFANT_UNCONFIRMED에서 MIXED_VOCAL로 바뀌었다.
성인/잡음 대조의 신규 출력은 없었다. 음량 gain은 제품에 넣지 않았다.
기존 필터·임계값·모델을 유지하고 [발성 존재 보완 계약](20261009-corroborated-vocal-contract.md)으로 이어갔다.
원시 실행 근거는 `work/quiet-gain-20261009/`에 보존한다.
