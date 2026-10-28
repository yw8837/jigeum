# VTC1 내부 로컬 ONNX 이식 — 실행 전 계약

2026-10-07. 첫긍정연구신호와피아노오인은 [첫 비교](20261007-vtc1-first-screen-results.md)에있다.
상업weight배포권은미확정이고문의초안은미발송이다. 제품assets/APK/Play에넣지않는다.
현재공개4.53MB후보를내부연구용으로ONNX에변환할수있는지와수치일치부터검증한다.

## 입력·계산·gate

- exact source/weight/pin을 그대로읽고원래PyanNet.eval을export한다. SincNet/InstanceNorm/3층BiLSTM/5Sigmoid를제거하거나재학습하지않는다.
- Python3.13.14/torch2.11.0+cpu/NumPy2.5.2/ONNX1.20.1/ORT1.24.3, CPU4threads/1interop, batch1.
- ONNXopset17/legacyexport/dynamic samples+frames/staticbatch1/externaldatafalse/constantfoldtrue.
  공식reference원음window는1~2초다. 여기서2초보다긴단일창이나전체20초의미모델로바꾸지않는다.
- 합성1초무음,2초tone(350Hz+.03진폭),1.999초 seedednoise(.03STD)만새input으로사용한다.
  graph는원래torch와동일shape/finite/[0,1]이고모든5column **maxabs<=5e-4**를export전기준으로고정한다.
  이수치는변환동등성gate이며아기판정threshold/정확도확률이아니다.
- ONNXchecker full검증,weight크기/SHA/현재runtime,전후원weighthash와graphhash를기록한다.
  변환파일은16MiB상한,Job180초/3GiB로제한하며자손0을확인한다. 외부파일/공유설치/다운로드/전송없음.
- INT8는floatgate이후별도검토하며동등성을검증하지않은양자화물을제품에넣지않는다.
  실제원음추론은이번export시험에서0이다. 합성score를새아기인식품질/Android속도/발열로보고하지않는다.

실행은새ignored출력경로를한번사용한다. 실패/경고와source변경은기록하고출력을덮어쓰지않는다.
실패이면정확원인을수정하거나모바일비용기준으로다음후보를선택하며,문턱을완화해통과를만들지않는다.
후속에는변환backend의실제누락/오인비교와ARM실기기/취소/메모리가필요하다.

## 첫 실패와 추가 진단의 사전 범위

export01은4,544,244B graph와shape/range/checker는통과했으나완전무음maxabs.055750으로고정5e-4를실패했다.
tone.000456/noise1.20e-7은gate내다. 원문InstanceNorm단계의합성무음hook에서극소conv변동이반복정규화로
증폭되는수치차이를관찰했다. kernel의정확원인을확정하거나gate를완화하지않았다.
이graph는 **변환통과/제품후보 승인으로 취급하지 않는다**.

다음은같은graphSHA `dfb7abbd27bba6149c87fe6b129ce41da32f84252d364cadb1ac6f07b72cacc8`의
실제보존3구간에대한backend진단을한번실행한다. 원음/hash/원래slide/평균은유지하고
원래Torch기록의모든5column·동일RF좌표와대조한다. maxabs5e-4는그대로이며재추론을새정확도표본으로세지않는다.
성공해도무음실패는그대로남아전체export통과로바꾸지않는다. graph를제품에복사하지않는다.
보고에는private3구간·동일원본overlap·ORT창수·원Torch재추론0·실기기미검증을분리한다.

## 실제 결과·채택 상태

- export01: exit1/3.148초, peak1,397,325,824B/3GiB, cleanuptrue/자손0.
  graph4,544,244B/SHA위값, checker·1초581/2초1174/1.999초1173 shape/range 통과.
  무음.055750/tone.000456/noise1.20e-7. **전체floatgate실패**는보존한다.
- exporter경고3개: legacyTorchScript지원변경권고, InstanceNorm의inputstats=True,
  variableLSTM batch주의. 실제batch1/짧은길이시험과원문eval/Norm학습상태를구분하며경고를숨기지않는다.
- norm-diagnostic01: 실제classifier입력은합성1초무음한번뿐이다.
  반복Norm/conv단계에극소변동과후단증폭이관찰됐다. 이는원인단서이며exactbackendkernel근본원인확정이아니다.
- graph-parity01: 기존보존3구간/2원본에 **ORT49창**을처음실행했다. 원래Torch 재추론0.
  동일원문slide·crop·균등평균의모든5column은 **최대오차1.59145e-5**, 3구간모두고정5e-4이내다.
  exit0/6.361초/peak1,028,661,248B/3GiB/자손0. 추론·native합산3.940초는PC이며Android속도가아니다.
  누락2구간은동일원본의겹치는개발자료다. 이를새3사례/정확도/전체float통과로바꾸지않는다.
- 단위gate4개는가짜success/누락·중복case/NaN·음수/wrongshape거부를통과했다.
  실제CLI변형graphHash/기존output2개도nonzero·새성공없음/기존결과불변·자손0을확인했다.
  변형3Bfixture만절대경로/SHA확인후개별제거했고private결과/로그/원음을유지했다.

무음실패가남아 **양자화시험·Android탑재·베타배포는진행하지않았다**.
floatgraph는Norm원인/후속adapter진단용활성4.54MB하나만유지한다. 다음은정규화수치차이와
기존제품의무음·배경음계약을검토하고독립오인/영아평가를확보하는것이다. 의미있는연구신호를버리거나
단순문구변경으로목표를종료하지않는다. 상업권명확한작은taskhead/공개데이터대안조사도병행중이다.

증거: `work/vtc1-20261006/export01/{report.json,process.json,export.log}`, `norm-diagnostic01.json`,
`graph-parity01/{report-private.json,process.json,diagnostic.log}`, `graph-guards01/{report.json,cleanup.json}`.
원음·개별점수·가중치·graph는Git에넣지않는다. source/pin/runner/합성fixture계약만공유한다.

## 독립 검토의 실행 이력 보완

`62e7de4...124f06c` 독립검토에서 Standards P2 하나를 받았다. 실행 후 추가한 guard/metadata를
당시 실행에도 있었다고 소급해서 확인하지 않는다. 기존 report는 그대로 보존한다.

- export01의 실제 runner SHA는 `fbd56c8547c227371dc5e00bb4966213004bea34b17ba043ff958753cae920f9`다.
  `124f06c`의 `vtc1_export.py` SHA는 `c4101a572f9706e4cdf9a8d7ed238df6347283b09abac57ee4d3c23cb619325a`다.
  첫 실행 후 완료성 검사 함수와 row의 `finite` 필드를 추가했다. 첫 report에는 `finite`가 없다.
  앞서 기록한 수치·shape/range 검사와 무음 실패는 당시 runner의 결과이며,
  최종 runner SHA의 export 실행 검증이라고 보고하지 않는다. 단위 gate4개는 추가 guard를 검사한 별도 증거다.
- graph-parity01의 실제 runner SHA는 `275a8fc8d04a2f3db1b207200059e35884a411bb972be27844e15b04c6024903`다.
  `124f06c`의 `vtc1_graph_parity.py` SHA는 `8ab38dcdce374d1a2767e5cce57712bf59bcba4d9da33c96f0be500d7080c605`다.
  첫 실행 후 기록된 runtime의 비교와 `recorded_reference_runtime`/`historical_python_recorded` 표시를 추가했다.
  당시 report에는 두 필드가 없고 원래 Torch score의 Python도 미기록이다.
  보존된 모든 열의 수치 비교 결과를 최종 runtime guard가 당시 실행됐다는 증거로 확대하지 않는다.
  원음 재추론이나 report 덮어쓰기는 하지 않았다.

## 최적화 수준 진단 — 설정 변경으로 무음 차이 해결 안 됨

원래 graph의 disabled/basic/extended/all 네 ORT 최적화 수준을 합성 세 입력에서 한 번 비교했다.
이 설정 비교는 기존 graph의 수치 문제 진단이며 새로운 아기 인식 사례를 추가하지 않는다.
모든 수준에서 무음 `.05574977398`, tone `.000456266105`, noise `1.1974771e-7`로 동일했다.
최적화 수준을 낮추는 방법은 이 차이의 해결책이 아니다. raw floatgate 실패는 계속 유지한다.
Torch 합성forward3/ORT12/원음0, exit0/3.651초/peak1,271,590,912B/3GiB/자손0이다.
graph의 디스크 복사·새 다운로드·제품 변경은 없다. 다음은 저에너지 창의 불안정성과
실제 Android 전처리에 대한 별도 행동 계약이며, raw graph가 통과했다고 바꾸지 않는다.

증거: ignored `work/vtc1-20261006/diagnose_ort_optimizations.py` 및
`norm-optimization01/{report.json,process.json,diagnostic.log}`.
