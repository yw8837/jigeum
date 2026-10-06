# AST 작은 head 코드리뷰 — 요구사항과 규칙 분리

2026-10-07 KST. code-review 스킬의 두축 독립 reviewer를 재사용했다.
실행자가 읽은 원음·모델body를 reviewer가 다시 읽거나 추론한 감사가 아니라 고정코드·허용JSON 근거의 검토다.

## 1. 학습·평가 실행기 — f54eaf6…4f85c52

### Standards

문서규칙 위반0. 기본dimension1024 계산순서 유지/768명시확장, 공개train21/18source만CV·fit,
head동결후validation, actual sample·끝padding·RMS 분리, 원logits parity0, 정상4run codeSHA/Job자손0,
실제CLI4 거부와 기존head보존을 확인했다.

선택적 Fowler Duplicated Code1: `AstRepresentation`와 기존interface worker의 graph검증·output추가·
ORToptions/session생성이반복된다. 공통생성경로로정리하면 drift위험을줄일수있지만 현재오류증거없는비차단제안이다.
후속결과/QA/HANDOFF 문서는해당고정diff에없어그검토를소급하지않는다.

### Spec

필수지적0. 공개35/43span 기존527logits exact, source분리/모든18fold·선택L2.0001,
FP32gate/quiet처리/실제CLI4 및4normalrun을계약과대조했다.
약국두support는동일원본overlap·다른울음/shortbabble 실패보존·product/Android/qualityfalse를유지한다.
연구20초2비중첩span을Android3중첩창전체검증으로표현하지않는것을후속보고조건으로남겼다.

요약: **Standards hard0/선택smell1, Spec필수0**. 전체goal 완료/제품승인은아니다.

## 2. ONNX 연결·첫 검사 — 0f7ba5a…ad1f254

### Standards

문서규칙 위반0. 원 AST 검증과8node동결head추가·원asset무변경·명시export한개,
headdtype/shape/SHA/runtime/threshold/private-training검사, quiet수치범위,
completion의4sourceSHA와고정blob,Job120초3GiB/자손0,CLI4보존·기존분기유지를확인했다.
windowhelper의별도SHA누락을고지하고완전dependency pin을주장하지않는다.
storage와후속후보보존이유,기존headQA와graph preflight/Android미검증범위를구분한다.

선택적 Fowler Duplicated Code1: graph worker의public첫10초FFmpegdecode/size·SHA/PCM16변환이
interface worker와반복된다. timeout·실제sample범위·decode후SHA를보존하며helper를공유할수있다.
현재오류증거없는비차단제안이다.

### Spec

필수지적0. 원852node/initializer/input/logits유지·FLOAT768L2head/충돌검사,
10조건/12span 원527logits0차이·head6.7646e-8·판정동일, actualsynthetic20초3창을확인했다.
NumPy reference가candidate의768표현을읽는범위를문서에서명시하며의도/975ms사건으로확대하지않는다.
recordedmodel91,138,597B/추가4,474B와output한개·전후용량·graph미탑재/goalactive를확인했다.
새source9는이고정시점미취득/blocked였으며후속평가를완료했다고소급하지않는다.

요약: **Standards hard0/선택smell1, Spec필수0**. 원음·PCM·NPZ·modelbody 읽기/재추론/수정/Git/빌드/ADB0.
