# 기존51조건의 발성 누락 원인: 점수 재생 결과

2026-10-07 KST. [사전 계약](20261007-presence-context-replay-contract.md),
[원문·의미축 근거](20261007-infant-presence-emotion-separation-evidence.md),
[QA](../qa/presence-context-replay-20261007.md).

**새 추론이나 제품 회복 시험이 아니다.** 이미 실제 Android에서 기록한 세 보고서의 점수를
읽기 전용으로 검사했다. raw media/weight 접근·download·학습·calibration·제품 코드 변경0이다.

## 결과

| 동결 연구 결과에 대한 진단 | 조건 수 | 의미 |
|---|---:|---|
| 이미 출력된 한마디 보존 |18|이전 연구에서 회복된 약국의 겹치는2구간도 포함. 이번 새 회복0 |
| broad 발성 단서 부족으로 보류 |25|같은창score≥.80·RMS≥.003을 함께 통과한 창 없음 |
| broad 발성 단서와 감정 충돌이 있어 보류 |8|현 guard가 막는 단서. 화자·감정·발성 subtype 정답 아님 |
| TV 충돌/기타 guard 진단 |0|이 고정 결과에서0이며 해당 오인 위험이 없다는 의미 아님 |

51조건에는 기존 public·합성/변형·개발·사용자 보존 구간이 섞여 있고 source/아기가 중복된다.
**8/51을 누락률, 18/51을 정확도, 8개를 추가 성공으로 보고하지 않는다.**
이 단계의 유의미한 정보 이득은 '소리가 들렸는데 왜 결과가 보류되는가'를 두 원인으로 분리한 것이다.

감정 충돌8은 기존 `laugh_babble`, `laugh_quiet`, `overlap`, `laugh_with_rumble`,
`stephan_5month`, `mayra_context`, `clod111_11month`, 새 public `475589`의 마지막20초다.
이는 내부 case ID이며 타깃6–18개월의 자연 옹알이8표본을 확보했다는 뜻이 아니다.

sidecar는 원래 판정/문장 대신 별도의 연구 진단만 쓴다. speaker=UNVERIFIED,
emotion/subtype=HELD, onset/phrase=null, product_authorization=false다.
AST 감정/TV와 YAMNet 감정 단서는 선택 전체에서 flag로 남으며 같은화자/동시사건을 주장하지 않는다.
원래 두 phase는 SHA로 보존했다. `neutralOnly`, 한마디, 저장, visual alignment에는 연결하지 않았다.

## 실제 실행과 수정

- run01은 선택 길이와 raw PCM 시간의 차이를 잘못 가정해 worker가 거부했다.
  parent도 BoundedResult dataclass를 JSON에 그대로 써 실패했다. 완료 결과0/원본 변경0이다.
- run02는 serializer를 고친 뒤 같은 PCM 길이 문제로 명시적으로 실패했다. 자손0/exit1을 보존했다.
- 실제 기록에서 선택 길이는 millisecond 올림, AST 끝점은 실제samples/16 절삭이었다.
  예:174,614samples는10,913.375ms, 선택10,914ms, AST끝10,913ms다.
  threshold나 coverage를 느슨하게 하지 않고 두 기준을 분리했다. 분수ms regression을 추가했다.
- run03은51완전case를 검증했다. run04는 소스6개 시작/종료pin과 strict 완료재계산을 추가한
  최종 재생이다. 결과는 같은18/25/8이며 원본 세 보고서 SHA 불변이다. 모델 재추론0이다.
- run04 Windows Job: **0.356479초**, exit0, peak aggregate committed58,183,680B,
  limit256MiB/60초, 자손0, 로그155B/절단0. 휴대폰 PSS·AST 속도·발열 측정이 아니다.
- 고유unit15통과/.007초, 실제 CLI 거부5조건 통과. 누락/NaN/중복/잘못된창/다른창음량/문장승격/
  source drift를 검사했다. 원음과 별도 모델의 음향 품질 반례로 보고하지 않는다.

## 다음 수정의 우선순위

1. 발성 자체가 약한25조건: 작은 head의 caption-weak 학습은 영아7clip/4source/40.7초로 부족하다.
   이미 본 eval/held를 train으로 이동하지 말고 새 source와 사건 검수 자료를 확보한다.
   성인 말·아기흉내·TV/재생·호흡·물소리 반례를 함께 분리한다.
2. 감정 충돌8조건: 기존 generic container를 재사용하면 울음/웃음이 neutral로 바뀔 수 있다.
   발성 존재와 감정 귀속을 분리하는 계약이 먼저다. 단일tail의 highscore로 guard를 없애지 않는다.
   아직 추가 아기 한마디를 안전하게 승인할 근거는 확보하지 못했다.
3. 이미 약국 회복이 확인된 동결 후보: 실제 ARM10/20초 속도·메모리·열·취소 gate를 통과하면
   새code beta의 서명/업데이트/로컬기록 QA로 이어간다. 현재 ADB 실폰0이라 해당 gate는 미완료다.

제품은 **0.5.18-beta/code32**다. 이번 APK/AAB/Play 업로드0, 새 베타 적용0이다.
연구 진단이나 메타데이터 QA를 인식률 향상·배포 완료로 보고하지 않는다. 전체 goal은 active다.

## 독립 검토 후 수정

Spec P2 두건(전용출력root미강제·사전중단/dirty검사부족)을보완했다. 전용root 밖의다른ignored
연구경로를mkdir전거부하고실제dirty문서보존·replay-load hang/timeout→새실행복구를검사했다.
최종run05는51진단18/25/8, exit0/.378788초/자손0이다. source6pin/원input3SHA불변,
고유unit16/.011초·거부CLI6통과다. 초기timeout harness설정오류2는실제hang성공으로세지않았다.
종전3cycle COMPLETE는미충족항목이있던선언으로정정했고최종한정QA는5cycle에완료했다.

[후속 학습source 조사](20261007-next-infant-training-source.md)는기존train/held와이름·ownerID가
겹치지않는18개월babbling/12개월coo-gurgle의CC0 caption후보2그룹을찾았다.
원음/자연성/사건검수·학습승인0이며blocked/null을유지한다. 98.858초metadata를양성발성시간으로세지않는다.
이들을자동정답으로학습하지않고소리구간/생활잡음/연기·재생과같은확인조건을먼저채운다.

## 보존과 정리

ignored `work/presence-context-replay-20261007/`에 작은4run 로그/결과와 QA 증거만 보존했다.
QA 전후 관측은7파일8,572B→43파일139,581B이며 시작은 run01/02 뒤 실제관측값이다.
원래 시작값이나 누락된 첫 lifecycle을 소급 만들지 않았다. snapshot 자체쓰기 전 수치다.
이번3 synthetic fixture와새pyc2개34,494B는 exact경로 검증 후 제거했다. scoped state부재·worker0이다.
과거publicMP3/빌드/PCM 삭제정책 차단은 유지하며 우회하지 않았다. 새대형산출물0이다.
