# 별도 공개 영아 음성 3개: 동결 모델 평가 계약

2026-10-07. 기준 `6511377`. 이 단계는 S25 연결을 기다리는 동안 확보한
[공개 CC0 자료](20261007-infant-source-readiness-update.md)의 실제 취득·디코딩·동결 head 반응을 확인한다.
같은 약국 원본을 다시 조정하는 실험이 아니다. 사용자 원음·특징을 읽거나 외부로 전송하지 않는다.

## 사전 고정

| ID | source | 공식 설명의 근거 | 평가 범위 |
|---|---|---|---|
| 388407 | amccoy711 | 15개월 아들의 giggling을 iPhone으로 녹음했다는 설명 | 원본과 구분한 MP3 preview 첫20초 이내 전체 |
| 135185 | carameans | 한 살 쌍둥이의 목욕, 웃음은 tags/FSD label | MP3 preview 첫20초; original6채널과 동등성 미확인 |
| 475589 | bvaudio | 9개월 여아의 잠들기 전 babbling/trilling, 끝의 little cry라는 설명 | 실제 길이로 사전 결정한 첫·중간·끝 각≤20초 |

URL·CC0·uploader·blocked/null을 실행 코드에 고정한다. 처음 두 파일은 이전 train35/extra9와 알려진 uploader가
겹치지 않는 metadata 후보다. [새475589 원문](20261007-new-natural-babble-sources.md)은 새exactID지만
기존 색인의 uploader이며 새가족으로 세지 않는다. 세파일 모두 가족/나이/자연성/사건 정답의 독립 검증은 없다.
canonical source SHA는 `ba345dd1dab51e0f0cc0d90805e5ba54b5aab902962ed07bae3ceb6f6ffbff2d`다.
이 결과를 정확도·영아 웃음/옹알이 정답·세 독립 가족의 증거로 세지 않는다.

- 동결 candidate graph91,138,597B/SHA4a378476…802a 및 head SHA81d7357c…d510,
  threshold.8/margin.4 유지. 재학습·calibration·임계값 변경0.
- 공식 CDN exact MP3만 취득, file당3MiB/20초timeout/redirect 거부. 기존 취득 함수의 기본2MiB는 유지한다.
  475589 원본 metadata272.161초 때문에 이번decode만≤300초/9.6MB PCM/20초timeout으로 제한한다.
  새model/download0이며300초 전체를 classifier에 넣지 않는다.
- 원음·embedding/PCM file을 만들지 않고 메모리에서 FFmpeg16k mono PCM16로 decode한다.
  네트워크는 취득 후 차단하고 FFmpeg protocol은file,pipe만 허용한다.
- 처음 두파일은 첫≤20초, 475589는 실제 decoded samples로 처음·정중앙·끝≤20초를 점수 계산 전에 선택한다.
  결과로 좋은 부분을 골라 이동하지 않으며 caption의 마지막 울음을 첫20초의 정답으로 쓰지 않는다.
  각 선택 구간의 실제 PCM 길이에 Android와 같은0–10/5–15/10–20/끝창 규칙을 적용한다.
  10초 classifier의 context window이며 사건 onset/연속발성 시간으로 해석하지 않는다.
- 같은 run의 original527 logits와 generichead를 관찰하고 head/NumPy score parity를 확인한다.
  host raw RMS quiet 여부를 기록한다. Android cleaned strongest-second gate·YAMNet·phase·문장은
  실행하지 않으므로 이를 앱 전체 support/회복/배포 검증이라고 하지 않는다.
- 분류는 공식 AST labels순서에서 읽는다. class score는 calibrated 의도 확률/음량이 아니다.
- 결과에 model/head/source/code pin·runtime·실제 samples/windows·오류/누락·process를 기록한다.
  시작/종료 소유 work폴더와 C여유를 자동 기록하고 state를 격리한다.
- 새MP3한세트는 다음 인간 사건 검수용으로 보존한다. 새 대용량 모델/APK/AAB는 만들지 않는다.
  전단계 삭제 차단을 우회하지 않는다.

## QA matrix

| ID | 상황 | 실행/기대 |
|---|---|---|
| P1 | 정상source3 취득/decode/10초맥락 | 세파일·첫/중간/끝 모두 완전 결과·source/model/head 종료pin |
| P2 | output외부/기존dir·model/head/ffmpeg 누락 | 실행 전 거부·외부취득/제품변경0 |
| P3 | SSRF/권리/label 승인 변경·redirect/파일cap | fixedsource/unit/공통HTTP guard 거부 |
| P4 | 부족PCM/NaN/outputshape·partial/fakecomplete | 검사 실패·품질승인으로 표시 안함 |
| P5 | timeout/worker/dirty tree/state 잔존 | bounded Job 종료·차단/실패 기록·이번state 정리 |
| P6 | source fit 변경/소급 성공/소유 파일 정리 | 종료pin·실측범위/누락 보존·사용자파일 보호 |

기본/오류 검증 뒤 실제 취득·추론을 수행하고 독립 검토한다.
모델 점수와 독립인 인간 사건 판정은 이번에 확보되지 않았다.
후속 인간 검수에는 모델 점수/승인 여부를 노출하지 않으며 정답을 모델 출력으로 채우지 않는다.
전체 목표는 계속 active다. 실제ARM·새 beta패키지/Play 제출은 별도 남은 gate다.

## 다음 비교: 동일 후보 Android 전체 음성 판정

host의 세파일/14창 첫 평가를 끝낸 뒤, score가 앱의 문장까지 연결되는지 추가로 검사한다.
새모델/threshold/앱build 없이 기존 동결 연구APK/test 한세트를 공용MoaQA35에 설치한다.
host에서 이미 사전 정한388407 첫구간,135185 첫20초,475589 첫/중간/끝20초를 모두 사용한다.
점수를 보고 좋은 창만 자르지 않는다. 원 preview PCM16 decode를float32/16k/mono로 format변환만 하고
실제총길이/selected offset/sourceSHA/PCM SHA를 기록하며 별도PCM host파일은 만들지 않는다.
예전 public paired corpus의conversation/piano/cchenail_laugh 세대조는 새source로 세지 않고 그대로 사용한다.

| ID | 의도 | 하네스 | 기대 |
|---|---|---|---|
| A1 | 새source의실제YAMNet/AST/기존phase/보완phase 비교 | AstPublicAudioProbeTest,public5+기존대조3 | 8case/전체창완료·동일PCM/pin·보류와새지원모두보존 |
| A2 | 이미 승인된결과보존/비아기오인 | 같은manifest | 기존지원은exact관찰/문장보존·세대조의새지원0; 결과후재튜닝0 |
| A3 | 잘못된앱/기기·partial/fakeOK/입력잔존 | installed_provenance/strictparser/finally | MoaQA35와동결pair만·skip0·UUIDruntime제거·원본불변 |
| A4 | 디스크/프로세스·shareddevice보호 | 소유run전후snapshot/기존production정보 | 기존앱불변·연구앱제거·이번에띄운공용AVD종료,AVDfolder보존 |

이는 emulator의음성판정검사다. 원래6채널preview동등성·원영상AV·실폰성능·사건정답/의도정확도는입증하지않는다.
세대조의오인이없어도일반오인율분모로세지않는다. 실제기기검증gate를에뮬레이터로우회하지않는다.
