# AST768 MIL 실제 비교 — 신규 회복0, 미채택

2026-10-07. [사전 계약](20261007-ast-mil-contract.md)대로 새 AST768 bag head를 학습·동결했다.
현재 평균 head와 공개35/사용자 retained3/기존 public33을 비교한 결과 **support 변화0**이다.
기존 약국 회복2구간을 유지하지만, 이 학습 방식만으로 새 누락을 회복하지 못해 제품에 채택하지 않는다.
새 source2는 이번 학습에 넣지 않았고 blocked/null을 유지했다.

## 실제 실행

|실행|Job 시간|peak aggregate committed|exit/자손|
|---|---:|---:|---|
|train-only18source CV와 fit/validation14|9.070063초|810,352,640B|0/0|
|동결 뒤 retained3|5.903262초|2,194,718,720B|0/0|
|동결 뒤 public33|89.394377초|2,195,087,360B|0/0|

fit2GiB/evaluation3GiB·180초 한도 안에서 모두 종료·cleanup=true·절단0이다.
PC 연구의 original/modified 두 AST session 비용이며 앱/휴대폰 성능으로 쓰지 않는다.
가중 log-mean-exp·span L2·actual sample 가중, zero/Adam .03/300step을 사용했다.
선택 L2=.0001, head SHA `a1194e241b2a701e0752611b466c663c10762d22067e087cf242a7801e33ad6c`.
769parameter/3076B FP32, weights/selection를 저장한 뒤 validation에 접근했다.

|자료|기존 평균 support|MIL support|변화|
|---|---|---|---|
|train 영아caption7|7|7|0|
|train 성인10/기타4|0/0|0/0|0|
|validation 영아caption4|2|2|0|
|validation 성인8/기타2|0/0|0/0|0|
|retained3의 각 span|두약국지원/기존cry단독미지원|동일|0|
|public33의 각 span|기존판정|동일|0|

약국 score는 .82434/.94284로 지원, 별도cry .65279는 기존 엔진과 별개 broad head에서 미지원이다.
score 변화 자체를 정확도 향상으로 세지 않는다. threshold .8/margin .4, 기존 AST logits/제품asset은 유지했다.
서로 겹친 사용자 개발구간·caption weak·반복 사용한 source이므로 독립 아기나 일반정확도 분모가 아니다.
감정/욕구/실제 사건 시작·화자를 학습하거나 검증하지 않았다.

증거: ignored `work/ast-mil-20261007/{fit01,retained01,public01,comparison.json}`.
[QA](../qa/ast-mil-20261007.md)와 독립 리뷰 후 보존한다.
제품0.5.18-beta/code32·새APK/AAB/Play upload0. 기존 약국 회복 frozen Android 후보와 ARM gate는 유지한다.
다음은 [새source 취득](20261007-new-infant-audio-review-acquisition.md)·
[weak bag 경계](20261007-weak-presence-training-boundary.md)에 따른 별도 미검수 caption whole-recording 연구 계약 검토다.
이번 MIL을 단순 반복하거나 기존held를 학습에 옮기지 않는다. 전체 goal active.
