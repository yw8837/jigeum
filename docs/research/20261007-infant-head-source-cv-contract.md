# 작은 영아 head 후속 — 훈련 source 안에서만 선택

2026-10-07. [첫실측](20261007-infant-head-first-results.md)은주요누락을해결하지못했다.
기존15train의clip평균학습과시간창추론의차이, 규제강도/확신도계약을검토한다.
개발자료를이미본후진행하는후속이며독립test라고보고하지않는다.

## 실행 전 고정

기존허락23preview/153feature를그대로쓴다. 새다운로드/augmentation/crop/windowlabel없음.
source snapshot23 SHA `b4c65fd17427ceec74c34d7051a4230688707761012ac8fc149a978a24e0746f`.
훈련15clip의12source에서leave-one-source-out으로 **L2 세값 .0001/.001/.01만** 비교한다.
원래.01도포함하며lr.03/300step/zero초기화/clipL2normalize·class/source/clip균등가중은고정이다.

- 각fold의source 전체를빼고나머지로fit. heldsource의clip별negative log likelihood 평균,
  source평균·동률이면더강한L2를선택한다. 모든fold에3class가남아야한다.
- 선택에사용하는것은 **훈련15clip/12source만**이다. 기존validation8/private원본·feature·public33을
  선택/fit/calibration에넣지않는다. sourceCV 자체는약한caption에대한개발분석이다.
- 하나의선택된head만15train전체로fit하고, 선택결과/훈련IDs/hash를먼저저장한후에validation8을읽는다.
  그결과에따라선택을변경하거나threshold를낮추지않는다.
- support .8/margin.4 유지. 확률calibration/욕구추론으로표현하지않는다.
- 후보결정후동일retained3/public33을평가하되이미사용한개발/회귀자료다.
  두겹치는누락이수용돼도새source영아·TV·Android가필요하고바로출하판정하지않는다.

Python/NumPy/LiteRT/flatbuffers와기존encoder/SHA/형식은그대로고정.
offline·새ignored출력·Job180초2GiB·exit/완료SHA/자손0, publicNPZ2MiB상한.
실제5단계첫실행runner SHA a67863d…는보존하며이번penalty인자추가를과거의실행검증으로소급하지않는다.
원래default.01의수식/순서는유지한다. 새sourceCV의실행SHA를별도로기록한다.

QA: train/holdsource겹침·누락class·validationrole거부,nonfinite/corruptfeature·staleoutput,
fold누락·CVlossnan·조기실패,Job종료·기존feature/head불변. 일반품질과도구QA를구분한다.
같은CV를반복하지않으며선택이실패하면더넓은독립source와다른task representation으로진행한다.
