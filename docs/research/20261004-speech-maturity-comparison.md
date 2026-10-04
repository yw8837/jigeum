# SpeechMaturity 고정 원음 비교와 채택 보류

2026-10-04 · 연구 후보 · 제품 정책/Android/Play 변경 없음

## 결과 요약

**18구간의425창을 빠짐없이 실행했지만 현 재구성·고정창 경로의 제품 채택은 보류한다.**
아기 소리가 없는 합성 tone의20창 모두 Non-Canonical로 출력됐다. 단순 top1을 아기 발성의 긍정 근거로
사용할 수 없으며, 기존 누락의 유용한 회복·웃음/울음 구분 개선도 입증하지 못했다.
이 결과는 원저자 모델 전체의 실패를 확정한 것이 아니라 현재 로컬 재구성 경로의 채택 실패다.

## 목적과 실행 전 조건

기존 모델이 놓친 발성에서 다른 학습 분류기가 어떤 소리 범주를 출력하는지 확인한다.
5-way 음성 분류 후보이며 영상·감정/욕구·한국어 문장 생성 모델은 아니다.
실제 가중치의 완전 복원·합성 검증과 Windows 프로세스 전체 제한 검사를 통과한 뒤에만 원음에 실행한다.

동일한 실패를 반복하는 대신 새 고정 후보를 비교한다. 결과를 보고 구간·입력 gain·임계값·checkpoint를 바꾸지 않는다.
모든 입력·결과는 로컬에서 처리하고 개별 음성·점수·원본 해시는 Git에서 제외한다.

## 입력과 시간축

- 기존 비교에 보관한 약국 원본의10초/20초 선택, 중복 원본을 제외한 추가 누락2구간과 울음1구간,
  기존 공개 음성11구간, 합성 tone/말소리2구간을 고정했다. 총18구간, 파일 출처 그룹16개다.
- 같은 원본의 겹치는 구간과 합성 자료가 포함된다. 출처 그룹 수가 독립 정답 자료 수는 아니다.
  공개 파일명·배포자 설명도 독립 청취 정답으로 취급하지 않는다.
- 16kHz mono 원음에 gain/필터를 추가하지 않는다. 이미 추출·보관한 PCM을 사용하므로 현재 Android 디코더 시험과 다르다.
- 선택 전체를500ms(8000샘플)의 비중첩 창과 마지막 remainder로 나누고 각각 중앙 zero-pad9217샘플로 만든다.
  실제 계획은425창이며70ms 미만 remainder는0이다. 이425창은 독립 성공/정답률 분모가 아니다.
- 원본 시작점과 창별 시작·끝을 보존한다. 각 창을 독립 분류하는 방식으로, 창 사이 맥락을 학습한 시계열 모델은 아니다.
- B=1, CPU4스레드, pooling seed2025, 원래 작은 pooling noise 유지. AST sigmoid 임계값을 이 softmax 모델에 이식하지 않는다.

계획은 추론 전에 `work/speech-maturity-comparison/plan-private.json`으로 고정했다.
SHA256: `21a6b0a6b305ed480633d512e0e2cb920bf8f78eabf87c006ff83a505e3ff3c6`.

## 검증 행렬

| 시나리오 | 기대 신호 | 결과 |
|---|---|---|
| 합성 runtime |431개 state key 완전복원, B=1/유한5출력, 동일seed 반복 일치 |통과, load3.338초·forward0.373/0.250초 |
| 잘못된 runtime 입력 |다중batch/긴 입력/NaN 거절 |3개 모두 거절 |
| 모든 입력의 hash/형식/길이 |추론 전 전체 검사, 누락/변형시 중단 |계획 고정 시 형식/hash/길이 통과 |
| 전체 구간 보존 |0부터 끝까지 gap/overlap 없음, 매 창 결과 또는 명시 unsupported |18/18구간 전체샘플 포함,425창 모두 분류 |
| 기존 누락·웃음·울음·배경 대조 |창별 raw logits/softmax/top1 보존; 무근거 정확도 산출 없음 |실행/보존 통과, 의미 품질 채택 미통과 |
| 중단·메모리·로그 상한 |300초/Job committed3GiB/로그16MiB, 전체 자손 정리 확인 |모델2실행 exit0/termination없음/cleanup완료/로그미잘림 |
| 반복 실행 |시작/완료 산출물이 있으면 묵시적 재추론 거절 |runner에 명시 |

실행 성공은 exit0, termination 없음, cleanup_complete=true, 로그 truncation 없음 모두 필요하다.
Job memory 수치는 RSS·휴대폰 메모리가 아니며 한도 숫자만으로 실행 통과를 판정하지 않는다.

## 채택 판단의 범위

기술적 실행 완료와 인식 개선을 구분한다. 발성 분류가 달라지더라도 발성 주인·장면 적합성·일반 정확도는 별도다.
원저자 Fairseq/SpeechBrain runtime과의 수치 parity, 한국 아기 독립 평가, 상용 재배포 권리,
Android 변환·실기기 성능·앱 통합은 이 host 비교만으로 통과하지 않는다.

## 실측과 해석

실제 환경은 기존 Python3.13.14, torch2.13.0+cpu, Transformers5.14.1이다. 패키지를 설치하거나 downgrade하지 않았다.
HF offline 설정과 Python socket 진입점 차단을 사용했고 외부 모델/API 요청을 하지 않았다.
이는 Windows 전체 네트워크 격리 검사가 아니다. 실행기는 네트워크 sandbox를 제공하지 않는다.

| 실행 | 감독 실행 전체 | 모델 load/forward 등 내부 stage | raw peak Job committed |
|---|---:|---:|---:|
| 합성 입력 검증 |31.490초 |load3.338초, forward0.373/0.250초 |3,087,216,640 bytes |
| 고정18구간 비교 |85.784초 |65.223초(load3.291초 포함) |3,100,946,432 bytes |

두 실행 모두 제한 종료 없음, exit0, 잔여 Job 프로세스0, log reader 종료를 확인했다.
실제425개 창의 forward 중앙값은0.145초, 최댓값0.184초다. 초기 import/준비/종료를 포함하는 감독 실행과
내부 stage의 차이를 숨기지 않는다. 이 수치는 PC CPU의 고정 창 실행이며 휴대폰/앱 성능이 아니다.
raw peak는 Windows Job committed accounting이며 RSS·VRAM이나 실제 휴대폰 필요 메모리가 아니다.

공개·합성 대조군의 일부 진단 결과:

| 입력 | 창 수 | top1 출력 | 의미 |
|---|---:|---|---|
| 합성 tone10초 |20 |Non-Canonical20 |발성이 없는 입력을 발성 범주로 분류; 긍정 근거로 바로 쓰기 부적합 |
| 공개 웃음 표기 |5 |Laughing2 / Crying2 / Non-Canonical1 |전체 장면의 한 범주·감정을 단정할 근거 없음 |
| 공개 옹알이 표기 |6 |Crying2 / Laughing3 / Junk1 |배포자 명칭과 일관하지 않음; 명칭을 독립 정답으로 삼아 정확도를 계산하지 않음 |
| 공개7개월 옹알이 표기 |30 |Junk17 / Canonical10 / Non-Canonical3 |발성 후보가 일부 나타나도 전체 구간 회복·발성 주인 증거는 아님 |
| 공개 청소기 표기 |30 |Junk30 |단일 배경 대조 결과; 일반 특이도 증거 아님 |
| 공개 피아노 표기 |16 |Junk14 / Crying2 |배경음에 대한 잘못된 아기말 위험을 별도로 남김 |

비공개5구간은 원음·개별 점수·시간열을 ignored 산출물에만 보관했다. 독립 정답과 사전 정의된
제품 수용 정책이 없으므로 “몇 개 회복/정확도 몇%”를 새로 만들지 않는다. 공개11자료 역시 발성 단위 청취 라벨이 없다.
기존 YAMNet/AST의 임계값·비활성 상태·제품 정책은 변경하지 않았다.

## 두 축 코드 검토와 핵심 재판정

- **Standards / Luna:** 새 실행기·검사와 runtime·검사를 읽고 actionable finding0.
  전체 strict 복원/로그 상한/자손 정리와 선언한 연구 범위를 확인했다. OS 네트워크 차단으로 과장하지 않도록 확인했다.
- **Spec / Sol:** bootstrap 확정cfg와12hidden/norm/pooling/B1/9217 계약을 대조해 actionable finding0.
  정적 검토이며 합성 통과 수치는 부모의 실제 실행 근거를 따른다.
- **핵심 품질 / Astra high:** 제품 보완 활성화 부적합. 가중치431키 복원과 동일seed 반복은 원저자 runtime 수치 parity가 아니다.
  현재 부정적 결과를 무시하거나 임계값·창 길이 변경으로 같은 사례를 반복해서 채택하지 않는다.

## 다음 한 단계

추가 모델 실행 전에 **원저자 runtime에서 같은 고정 비민감 PCM의 기준 출력**을 확보해야 한다.
코드·checkpoint·전처리와 LL4300 층별 hidden/ASR hidden/최종 logits, pooling noise 처리와
사전 허용 오차를 고정해 한 번 비교한다. 이것이 재구성 오류와 후보의 한계를 구분할 다음 근거다.
기준 출력이 없으면 이 후보를 보류한다. 이 단계가 통과해도 의도·영상 결합 생성이나 제품 개선 완료는 아니다.

## 재현·정리

부모 실행 명령은 `work/speech-maturity-reconstruction/supervise.py synthetic` 및 같은 파일의 `comparison`이다.
감독자는 [bounded_process](../qa/ondevice-av-generation/BOUNDED-PROCESS-QA.md)를 사용했다.
합성 결과는 `work/speech-maturity-reconstruction/synthetic-result.json`과 supervision/log에 있다.
원음 계획·oneoff runner·개별 결과·완료 결과·supervision/log는 ignored `work/speech-maturity-comparison/`에 남긴다.
모델/개인자료/파생미디어는 stage하지 않는다. 완료한 실행을 묵시적으로 반복하지 않도록 시작 마커와 기존결과 거절을 뒀다.
실제 실행 프로세스 정리 완료를 확인했다. 원본·제품 앱·공개 모델은 삭제하거나 수정하지 않았다.

**새 모델의 제품 활성화, Android 통합·실기기 검증·Play 업로드는 없다.** 이 QA는 연구 실행과 결함 검증이며 앱 전체 품질 승인으로 보고하지 않는다.
