# 발성 존재와 감정 충돌: 동결 결과의 진단 재생 계약

2026-10-07. 기준 `064e8e9`. 기존 Android에서 기록한 점수를 **읽기 전용으로**
재생한다. 새 추론·청취·학습·threshold 선택이나 제품 동작 변경이 아니다.
[원문 근거](20261007-infant-presence-emotion-separation-evidence.md)의 별도 연구 sidecar만 만든다.

## 입력과 사전 규칙

| 고정 입력 | case | SHA256 |
|---|---:|---|
| infant-presence-android/pcm-04 |36|640f2eea851852ab9c12fb038ebbc6af558309c0e0e0fe7944f3f9e54a2e4960 |
| infant-presence-android/pcm-05 |7|35bbb62dfd77ec001abddc939d23e71bfdcb1a801f20e96805b43d8f6755b8c5 |
| public-infant-followup/android01 |8|9106d91ab1a99cee0e1608e5120220536d8c090c944350604f09bbfb88f274b2 |

- graph `4a378476dcbe5f6b13c5f67295797d59376dab3627fe3dbd56bfeeadbd7f802a` 고정.
- 기존 broad score≥.80, 같은 창 cleaned strongest1s RMS≥.003을 고정한다.
- AST 감정11종과 Television≥.30, YAMNet 아기울음/아기웃음≥.35,
  화자미확인 울음/웃음/비명≥.65를 **그대로 기록**한다. 보호조건을 완화하지 않는다.
- 1–20초의 완전한 실제 AST 창과 finite/range·sample/byte/run ID·완료상태를 검사한다.
  기록된 선택 길이는 samples/16의 올림이고 AST 끝점은 samples/16의 정수 절삭이다.
  두 시간 기준을 분리해 실제 AST coverage를 검사한다. 기존 context 창은 이벤트 onset이 아니다.
- 일부 AST label만 저장된 결과다. Music/Breathing 등이 top10에 없다고 낮다고 가정하지 않는다.
- sidecar의 speaker=UNVERIFIED, emotion/subtype=HELD, onset=null, phrase=null.
  감정 단서는 같은 아기·같은 화자·실제 동시 사건으로 귀속하지 않는다.
- 모든 기존 prior_phase/phase를 canonical SHA로 고정한다. sidecar가 기존 울음·웃음·문장·보류를
  수정하거나 neutralOnly 경로로 들어가지 않는다. 새 한마디 승인과 일반 정확도는 계속0이다.
- replay/helper4 및 기존 policy2 소스의 시작/종료 SHA를 기록한다. 최종 완료 결과도 동일 입력으로
  다시 계산해 partial/거짓 완료와 source drift를 거부한다. 이는 실제 제품 Kotlin의 재실행이 아니다.

## 진단의 목적과 채택 경계

단순 감정 guard 완화가 어느 조건을 건드릴지, 모델 score 자체가 낮은 누락과 보호조건으로
막힌 누락을 분리한다. public·합성·개발·사용자 보존 결과가 섞인51case는 독립 표본51개가 아니다.
후속 감정혼합 연구의 입력 우선순위만 정하며 사건 정답이나 안전한 앱 회복으로 보고하지 않는다.

출력은 ignored `work/presence-context-replay-20261007/` 새 실행 폴더에만 쓴다.
기존 보고서·원음·model·APK는 읽기 전용/불변이다. 네트워크·raw media 접근·외부연락0.
실폰 ARM/새 beta 서명·업데이트 gate를 유지한다. 기존 삭제차단 대상은 건드리지 않는다.

## QA matrix (실행 전)

| ID | 의도/입력 | 실행 | 기대 신호 | 증거/정리 |
|---|---|---|---|---|
| R1 | 고정51case 정상 | 실제 CLI replay |51완전 진단·원판정 보존·품질 false|결과/input 종료 SHA·전후 용량 |
| R2 | malformed/NaN/missing/duplicate JSON | in-memory unit·실제 CLI duplicate/hash 오류 | 거부·완료 산출물 없음 | unit/CLI log; 작은 exact fixture 정리 |
| R3 | 누락창/잘못된시각/행수/run/model | in-memory schema mutation | partial/fake success 거부 | unit; 원자료 수정0 |
| R4 | 큰 다른창 RMS·웃음/울음/TV score | pure sidecar unit | 같은창audibility·contextflag 유지·문장 null | unit; 음향 반례로 보고하지 않음 |
| R5 | stale output/외부경로/더러운트리 | 실제 CLI·Git status | 취득/변경 전 거부·타작업 보존 | sentinel/hash·Git |
| R6 | bounded process/중단·거짓성공 | Job60초256MiB/1MiB·strict completion | exit/count/pin/자손0·완료 전 최종pin | process/QA log |
| R7 | local QA lifecycle | scoped OMX CLI state | write/read/clear/부재·repo state 보존 | state json·전후 용량 |

최대5cycle·동일실패3회 중지. 최대3입력/각8MiB이며 child timeout60초다.
이 replay의QA는 metadata 계약 검증이며 native Android 재실행·아기 감정/화자 정확도 검증이 아니다.
