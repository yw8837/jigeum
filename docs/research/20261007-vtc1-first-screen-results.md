# VTC1 첫 실측 — 기존 누락에 아이 목소리 신호가 남음

2026-10-06 시작, 10월7일 자정을 넘어 검증. 기준 `ab14461`. 제품code32/YAMNet+AST+MediaPipe는 변경하지 않았다.
[사전 계약](20261006-vtc1-local-contract.md), [정확 artifact 조사](20261006-specialist-actionable-next.md),
[배포권 조사](20261007-vtc1-distribution-rights.md)를 따른다.

## 기존 범용 후보와 다른 첫 양성 결과

원음·모델·원래 threshold·2초/.5hop/end-aligned 계산을 고정한 첫 retained 실행에서,
기존에 놓친 **한 원본의 겹치는 두 구간 모두 KCHI 아이 목소리 단서가 남았다**.
원래 0.1초 on/off 후처리를 추가 추론 없이 재생해도 두 구간에 활성구간이 남았고,
두 구간 모두 성인 MAL/FEM 활성은 없었다. 별도 cry 대조에도 아이 단서가 있었다.

이는 단순 max 한 점만으로 성공을 주장한 결과와 다르다. 그러나 **기존 누락 회복을 위한 연구 신호**이며,
현재 앱의 승인/옹알이 유형/욕구 정확도 개선을 확인한 결과는 아니다. KCHI는 마이크 착용 아이 task이고
화면 속 아기·발성 의도를 입증하지 않는다. 보존3구간/2원본, 누락2구간의 약2.83초 overlap,
독립 청취 정답 부재와 사용자 설명이 개발 가설이라는 제한을 그대로 유지한다.

## 사전 규칙대로 처음 실행한 공개33조건

retained에 아이 신호가 있으면 공개33조건으로 진행한다는 사전 규칙에 따라 같은 runner를 처음 실행했다.
원래 threshold/후처리 기준으로 KCHI활성22조건/CHI활성16조건, 어느 child활성도 없는 조건11개다.
합성·quiet/순서/혼합 파생·동일 저자·목표월령 밖·화자 미확정 자료가 섞여 있어 **22/33을 정확도로 계산하지 않는다**.

- 공개 옹알이·웃음·울음 자료 일부에서 강한 아이 구간이 유지됐다.
- 성인 대화, 청소기, 합성 무음/톤/잡음/rumble/DC 대조는 KCHI/CHI 활성0이다.
- **피아노를 KCHI로 잘못 보는 활성구간이 생겼다.** SPEECH도 열려 SPEECH 동시조건만으로 이 오인을 막지 못한다.
- 화자 미확정 일반 웃음에도 child 단서가 나온다. uploader가 baby라고 설명한 일부 자료에는 단서가 없다.
- 합성 tone에 성인 FEM/SPEECH가 열리는 반례도 있다. voice label을 단독 사실이나 얼굴 주인으로 쓰지 않는다.
- **TV 재생·같은 화면의 성인/아이 구분·새 독립 영아 정답은 이번33조건에 충분히 포함되지 않았다.**

기존 승인 사례를 그대로 유지하며 누락에만 VTC를 추가하는 결합이 가능할지는 다음 평가 과제다.
피아노를 보고 음악 문턱을 새로 고르거나, score를 조정해 품질 통과로 만들지 않았다.
현재 제품 규칙과 결합해 모든 입력을 평가한 결과도 아직 없다.

## 실행·시간축·provenance

| 실행 | 실제 범위 | exit/시간 | Job peak committed | 자손 |
|---|---|---|---|---|
| validation03 | strict state44key, 합성2초 forward1/private0 | 0 /2.252초 | 1,021,239,296B | 0 |
| retained-run01 | 실제3구간/2원본 | 0 /9.586초 | 1,045,164,032B | 0 |
| public-run01 | 실제공개·합성33조건 | 0 /94.663초 | 1,301,815,296B | 0 |
| validation04 | 새 train/specs 검증·합성2초 forward1/private0 | 0 /2.865초 | 1,021,558,784B | 0 |

모든 inference worker는180초/2GiB Job 상한·CPU4threads·offline이며 입력 SHA를 전후 확인했다.
시간은 PC의 source/입력 검증·모델 load·결과 직렬화도 포함한다. Android/S25/기종별 속도나 발열 보증이 아니다.
원음/개별 점수/이벤트는 ignored work에만 남겼다.

원문 `Model.slide`는 끝에서 coverage0 grid를 생성할 수 있다. 원문 chunk/crop 산술을 별도로 재생해
complete RF AND coverage>0을 검사했다. 이번36조건의 보존 RF 내 unsupported 행은0으로,
기존 scores를 고치거나 재추론하지 않고 동일값에 원래 Binarize를 적용했다.
합성1/1.999/2/2.173/10/20초 crop과 원래 Model.slide의 가변 chunk값·균등평균·zero edge도 검증했다.
2초1174frame/1초581frame은 실제 합성 forward shape다. convolution grid의20ms를 사건 정확도로 주장하지 않는다.

처음 source pin18개에 실제 loader의 **train/specs.yml185,313B** 및 원래 Binarize source를 추가해20개로 고정했다.
train task/classes와 weight 옆 구 task명의 차이를 명시적으로 검사한다. 배포 config는 논문 그림보다 우선하는3층 LSTM다.
후처리는 원문 Binarize AST 본문을 실행하고 제한된 Timeline add/support/gaps만 어댑터로 공급한다.
엄격 threshold 비교, 짧은on 제거 후 짧은off 병합 순서, equality와 metadata injection 반례를 확인했다.

## 채택 상태와 실제 다음 행동

**유망 연구 후보로 유지한다. 제품 미탑재이며 새 배포는 없다.** 단독 승인 모델로 쓰지 않는다.

1. 앱 사용권을 확인한다. 공식 license/권리자 허락이 없다. [문의 초안](20261007-vtc1-license-request-draft.md)은
   작성했고 외부 게시의 명시적 승인 질문을 보냈다. 초안은 **미발송**이다.
2. 권리 답을 기다리는 동안 내부 로컬 graph 이식/합성 출력 일치/모바일 비용·기존음악충돌 결합 계약을 검증한다.
   사용자 자료는 외부로 보내지 않는다. 독립 영아/성인/TV/새 소스 평가를 고정한 뒤 실제 품질 gate를 통과시킨다.
3. 통합 이득·오인 방지·원래수용 보존·Android 취소/성능·두 축 리뷰가 충족되면 beta에만 적용/QA/업데이트한다.

[연구 UltraQA](../qa/vtc1-20261007.md)는13 temporal/synthetic검사+Job10검사+실제 CLI 거부5를 통과했다.
실기기/욕구 정답/제품품질로 합산하지 않는다. 이번합성fixture 파일10개/빈폴더5개와 격리state는 정리했다.
활성 VTCweight4.53MB는 다음 이식 비교를 위해 유지하며, 종료 때개별SHA/경로를 확인해 제거한다.
이전차단fixture10폴더를 이번정리로 삭제했다고 보고하지 않는다. 전체개선목표는 **활성**이다.

로컬 증거: `work/vtc1-20261006/{retained-run01,public-run01,validation03,validation04}/process.json`,
`retained-events01-private.json`, `public-events01-private.json`, `qa01/report.json`, `qa01/cleanup.json`,
`supervisor-qa01/`, `state-read-response.json`.
