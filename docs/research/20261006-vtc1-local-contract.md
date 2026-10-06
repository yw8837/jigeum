# 작은 영아 화자 모델 VTC1 — 사전 고정 로컬 비교

2026-10-06. 시작 기준 `ab14461`. 제품은 YAMNet+AST+MediaPipe/code32다.
본 문서는 **첫 retained 원음 추론 전에** 입력·계산·판정 범위를 고정한다.
공개 source/weight 취득과 synthetic tensor validation만 완료했다.

## 비교 이유와 권리

일반 AudioSet AST/PretrainedSED/PANNs에서 기존 누락 원본의 양성 단서가 부족했다.
VTC1은 아이/성인 목소리 구간을 학습한 **다른 과제의 실제 작은 가중치**다.
아이 목소리 신호가 남는지를 비교한다. 이를 옹알이 종류·욕구·얼굴 주인으로 바꾸지 않는다.
KCHI는 원래 마이크를 착용한 아이이며 손에 든 휴대폰 화면 속 아기라는 라벨이 아니다.

[원 repo](https://github.com/MarvinLvn/voice-type-classifier/tree/9731a9aad21a85d7bc660193386de94f2fb19f76),
[전문 조사](20261006-specialist-actionable-next.md)를 따른다.
배포 가중치 `0100.pt`는 실제 **4,532,569B**,
SHA256 `8514e96c8e049abd9ffc75c816b6aff51df09507b3ec7ac1c800fd366408286c`다.
Git blob SHA1 `649293f96345748da846ecbdd46538838f1fae25`도 직접 재계산해 일치했다.
pyannote-audio source `85b84bc7ee3f44f1f112397f615873f348517541`/core3.7.1 commit
`1be2248ec6f36b8564c46267c0d257884a1258a7`는 MIT다.
**가중치 상업 포함·재배포 허락은 미확정**이다. 내부 로컬 연구와 제품 탑재를 구분한다.
업스트림 `apply.sh`의 삭제/이동 shell을 실행하지 않는다.

## 실제 계산과 제한 어댑터

- 정확한 artifact config: SincNet256/60/60, kernel251/5/5, maxpool3/3/3,
  affine InstanceNorm input+내부, abs(first conv), LeakyReLU .2,
  **3층** bidirectional LSTM128, FF128/128 tanh, linear128→5/Sigmoid.
  논문 그림의2층보다 config/strict state를 우선한다.
- 라벨 순서 `KCHI, CHI, MAL, FEM, SPEECH`. 원문 train specs의 task는
  `frame-wise multi-label classification`. weight 옆 구 specs의 옛 이름과 혼동하지 않는다.
- Python3.13.14/torch2.11.0+cpu/NumPy2.5.2, CPU4threads, batch1/eval/inference_mode.
- upstream SincConv1d/SincNet/RNN/FF/PyanNet/Task 계산 본문과 Model.slide를 AST로 추출한다.
  raw window start=-.5/16k, duration/step=1/16k를 보존한다.
  **2초창/.5초hop/끝 정렬/짧은 입력의 실제 길이**, strict 출력 crop/겹침 균등 평균은
  원문 SlidingWindow/SlidingWindowFeature/Model.slide 계산이다.
- package/train/CLI imports 생략, Timeline import2곳 제거 후 **단일 contiguous Segment만**
  허용하는 iterator를 공급한다. pescador는 동등한 batch1 stack만 공급한다.
  Model의 계산에 쓰는 constructor/property/slide만 남긴다. 기타 unused method는 실행하지 않는다.
  전체 추출 class의 원 AST/사용 AST 해시를 기록한다. 원저자 전체 환경 재현은 아니다.
- 출력 convolution RF325samples=20.3125ms, step27samples=1.6875ms.
  BiLSTM/InstanceNorm 때문에 각 출력은 해당2초창 문맥에 의존한다.
  이 grid를20ms 사건 정확도나 새로운20초 전체 의미 모델로 홍보하지 않는다.
  원음 밖 RF를 제외하고 정확한 start/end sample을 저장한다.
- source18개·weight·전체 state keys/shape/float32/finite·5×128 head를 검사하고 strict load.
  다운로드·설치·threshold 학습·원음 수정·외부 전송은 runner에 없다.
  ignored work 경계/원음 hash 전후/이전 output 거부/Job180초·2GiB/잔여 자손0을 확인한다.

## 첫 비교와 다음 gate

입력은 보존 `work/sed-followup-20261006/retained-corpus/manifest.json`의 **3구간/2원본**이다.
누락 두 구간은 같은 원본의 약2.83초 겹친 구간이며 새 holdout2개가 아니다.
독립 청취 정답은 없고 기존 `user_reports_baby_vocalization`은 개발 가설이다.
별도 cry 대조의 결과를 옹알이 회복으로 세지 않는다. 원본을 새로 decoder 처리하는 시험도 아니다.

점수를 보고 새 threshold를 정하지 않는다. 우선 raw5라벨 max/mean와 원래 onset 이상의 bin수를 기록한다.
KCHI.3739/CHI.1570/MAL.1363/FEM.3583/SPEECH.4014는 업스트림 params다.
**bin수는 descriptive screen이며 원래0.1초 on/off 병합·RTTM 검출 건수나 제품 승인수가 아니다.**
아이 신호가 기존 누락에 남으면 기존 공개33조건을 고정 runner로 처음 비교한다.
성인/TV·무음/기계음에 같은 child signal이 나오면 단독 infant 승인 경로로 채택하지 않는다.
새 독립 영아 사건/영상 화자·모바일·사용권이 없는 동안 제품에 넣지 않는다.
아이 신호가 없으면 같은 retained의 증폭/창/threshold 탐색을 반복하지 않는다.

## 준비 결과와 수정 이력

`validation01`은 unused 원문 method 안의 import까지 거부하던 추출 하네스에서 실패했고,
`validation02`는 Model base의 torch Module 공급 누락으로 실패했다.
두 경우 private classification과 weight load 전에 종료했고 child cleanup을 확인했다.
원문 계산을 바꾸지 않고 import 보존/Module 공급으로 수정했다.

`validation03`: retained3 입력 무결성, strict load와 **합성 무음 forward1회** `(1,1174,5)`/finite를 확인했다.
private 원음 forward0회다. process exit0,2.252348초, Job peak committed1,021,239,296B,
cleanup=true/잔여 자손0. 이 수치는 PC 준비검사이며 아기품질/Android/실폰 수치가 아니다.

재현 command는 실제 한 번씩만 새 output에 실행한다:

```powershell
& 'C:/Users/최용우/AppData/Local/Temp/agattok-efficientat-20261005/venv/Scripts/python.exe' tools/research/vtc1_probe.py --assets work/vtc1-20261006 --corpus work/sed-followup-20261006/retained-corpus --output work/vtc1-20261006/retained-run01
```

판정 후 재생성 가능한 이 실험의4.53MB weight만 hash/경로 확인해 정리한다.
pin/source/runner/log/results와 사용자 원음은 보존한다. Git에는 원음/개별 점수/가중치를 넣지 않는다.
