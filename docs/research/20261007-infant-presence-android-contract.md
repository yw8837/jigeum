# Android 동결 AST infant-presence 후보 계약

2026-10-07. 제품 기준 `102a326` / 0.5.18 code32. 연구용 debug 후보부터 구현한다.

## 범위와 고정 입력

- 사용자 원음/영상은 평가 전용·외부 전송/훈련/정규화 적합/threshold 선택 금지.
- 후보 graph 91,138,597B, SHA `4a378476dcbe5f6b13c5f67295797d59376dab3627fe3dbd56bfeeadbd7f802a`.
- 원 AST 527 logits와 입력을 보존한 graph다. 새 presence score ≥ .8은 미보정 support 기준이며 정확도 확률이 아니다.
- 공개 train21/18source(영아7clip/4source/40.7초)의 caption-weak head다. 약국 두 구간은 동일 원본의 겹치는 개발 자료다. extra9의5누락을 보존한다.
- `agattokInfantPresenceCandidate=true`는 `agattokAstProbe=true`, `agattokAstCandidate=true`를 모두 요구한다. APK 전용·별도 package, beta/release/preview/bundle 금지.
- 후보는 기존 AST graph asset을 덮지 않는다. 후보 APK에는 선택된 graph 하나와 기존 notice만 포함한다. 새 모델 다운로드 없음.

## 판정과 표현

- 한 native run에서 원logits와 새score를 받는다. FLOAT/shape/finite/range 검사, 기존 mutex/deadline/cancel/메모리 보호를 유지한다.
- 20초는 기존 Android 0–10/5–15/10–20 중첩3창을 유지한다. score는 넓은 창의 단서며 정확onset·연속발성·반복횟수·화자신원·표정동기를 만들지 않는다.
- 기존 승인 결과를 지우지 않고 기존 AST 옹알이 보완 다음에만 추가 지원한다. quiet/invalid/no audio/mixed·기존YAMNet emotional guard와 AST 전체선택 cry/laugh/scream/TV ≥ .30 충돌을 유지한다.
- 새 presence는 BABBLE/CRY/LAUGH/욕구로 치환하지 않는다. 별도 `InfantVoiceSupportWindow`와 일반 발성 표현을 사용한다. 으앙/응애·웃음·옹알이·배고픔·졸림을 이 score만으로 붙이지 않는다.
- Android 첫36조건에서 첫약국10초는 score .8198 지원 후 whole-scene activity<.04로QUIET,
  후속10초는 .9400으로 한마디회복했다. 다음 수정은 새head의 score/threshold를 유지하고,
  generic presence에만 기존 입력무음기준 strongest1s RMS≥.003을 재사용한다. 원래quiet/invalid입력은
  계속보류하고 scene energy/rhythm을아기에게귀속하지않는다. 새로운 낮은음량threshold를맞추지않는다.
  각supported AST창 자체의 같은 cleaned sliding1s RMS도 .003 이상이어야 한다. 구간 밖 부모말/음악의
  큰소리로 quiet candidate창을 승인하지 않는다. 실제 window level은nullable/finite/range도 검증한다.
- 기존 AST 및 shifted YAMNet 승인까지 먼저 마친 뒤 generic presence를 마지막에 보완한다.
  원래phase 승인 문장·이벤트를 generic으로 치환하지 않는다. 같은AST run을재사용한다.
- 넓은 창에는 whole-waveform rhythm·정확PTS 얼굴 연결·긴발성 문구를 귀속하지 않는다. 시각 관찰은 계속 제공하나 단독 판정/입술동기 승인으로 쓰지 않는다.
- 기존 이용권·기록·미디어·실행취소 정책을 유지한다.

## 사전 검증과 다음 gate

1. pure 정책: .8 경계, 부정/비정상score·불완전/중복/역순/구간밖 window, 전체선택 충돌, 기존승인 보존, 일반문구/no rhythm/no lip-sync.
2. 후보 빌드·archive pin/단일graph, 잘못된 flags/forbidden variants 거부. 기존 beta/release 구성 불변.
3. Android 실제 약국10초/후속10초/전체17초 및 가능한20초 선택의 기존/후보 대조. 짧은 원본을20초로 패딩해 실제20초라고 보고하지 않는다.
4. 실제 공개 성인/음악/무음·기존 울음/웃음 회귀와 native malformed/cancel/resume/memory/model-store 검사.
5. Android 전체AV·UI 다른구간·저장/취소 회귀. 호스트/에뮬레이터 결과로 ARM 실폰속도·발열을 보장하지 않는다.
6. 독립 Standards/Spec 리뷰와 ARM 검증 후 유효 변경만 버전 증가로 beta 반영. 현재 code32 재업로드 금지.

연구APK 통과는 앱 배포·일반 영아 인식률·욕구 번역 품질 승인과 구분한다. 전체 goal active를 유지한다.
