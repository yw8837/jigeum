# TalkNet 실제 AV 조건 비교 — 제품 채택 근거 미확보

2026-10-04 · CPU 로컬 연구 · Android/Play 변경 없음

## 결론

제공된 로컬 영상의 첫10초에서 얼굴 추적·전처리와 네 조건의 TalkNet 실행을 완료했다.
**원음 조건은 무음보다 높은 발성 점수를 냈지만, 소리를500ms 늦춘 조건이 원래 정렬보다 높았다.**
이 단일 자료에서 입 움직임과 소리의 정합성을 확인할 근거는 확보하지 못했다.
현재 앱의 옹알이 누락을 회복하는 제품 판정으로 연결하거나 새 모델을 활성화하지 않는다.

이 결과는 active-speaker 모델 전체의 성능 부정도 아니다. 독립적인 발성 주체/event 정답이 없고,
TalkNet은 전용 offset 추정기가 아니다. 한 원본의 네 변형이나249frame을 독립 정확도 표본으로 세지 않는다.
개별 점수·프레임·원음·원본 식별/해시는 ignored `work/talknet-face/private-av10/`에만 보관한다.

## 실행 전 고정한 처리

- [CPU 모델·합성 gate](20261004-talknet-probe.md), [공식 소스 검토](20261004-talknet-runtime-review.md),
  [PyPI0.6 MFCC 검증](20261004-talknet-mfcc.md)을 완료한 뒤 실제 자료를 처리했다.
- 얼굴 detector는 공식 S3FD weight65키 strict 복원, RGB/scale.25/conf.9를 유지했다.
  10초를25fps로 변환한250frame 모두에서 한 얼굴을 검출했고, 이 구간에는 보간이 필요하지 않았다.
  원음/모델 결과를 보고 crop·검출 임계값·시작점·분석 길이를 고르지 않았다.
- 고정 공식 source의 `track_shot`과 IoU 함수를 그대로 추출했다. 별도 scene detector는 실행하지 않았고
  해당 짧은 구간의 시각 검토를 기록했다. 일반 영상의 scene cut·여러 사람 추적 품질 검증은 아니다.
- bbox center/반크기에 median13, cropScale.40, border110, resize224→gray→중앙112를 적용했다.
  같은112crop tensor를 네 조건에서 재사용했다. 다섯 위치의 crop에 입이 포함됨을 확인했다.
- JPEG q2를 사용했고 원demo의 AVI/XVID 중간 인코딩은 생략했다. 원demo와 pixel parity를 주장하지 않는다.
- source/sample showinfo checksum을 대조해250개 원본 PTS를 연결했다. 최대 resample 차이는13.3333ms이며
  원본 음성/영상 stream 시작은 모두0이다. 입력 시각 매핑과 검토 자료의 해시는 실행 계약에 고정했다.
- 음성은 mono16kHz PCM16. 별도 gain·잡음 필터 없이 PyPI0.6 원문 MFCC를 계산했다.
  10초 PCM의999MFCC를996으로 맞추고250video중249를 사용했다. 부족분을 wrap하지 않았다.

## 네 조건과 해석 범위

`tools/research/talknet_av_compare.py`는 아래 조건을 전부 준비한 다음 가중치를 불러온다.

1. 원래 정렬의 원음.
2. 동일길이 PCM 무음. MFCC를0으로 대체하지 않고 다시 계산한다.
3. 음성을8000sample(500ms) 앞당기고 끝을zero-fill.
4. 음성을8000sample(500ms) 늦추고 앞을zero-fill.

모두 같은 영상, 같은 full-track window, 같은 checkpoint로 한 번씩 실행했다.
공식demo의1…6초 ensemble을 실행한 것은 아니다. 모델 점수 전에 양끝.56초를 제외한 공통223frame을 고정했다.
이 마스크는 직접 zero-fill/MFCC 지지 경계 위치를 제외할 뿐이다. 모델의 전체 시간 평균 풀링과 attention 때문에
**중앙 출력에도 경계 조건의 영향이 남는다.** 이를 완전히 제거한 sync 실험이라고 표현하지 않는다.

검출 정답·정확도·확률·제품 임계값을 계산하지 않는다. 사전 기준은 paired raw-logit 차이의 기술뿐이다.
원음과 무음의 차이는 음성에 대한 반응을 보이지만, 소리 주인·옹알이 종류·감정·욕구를 확인한 것은 아니다.
한 방향 shift가 더 높은 결과를 얻었다고 그 offset에 맞춰 원본을 재정렬하거나 다시 시험하지 않는다.

## 실제 실행 및 성능 한계

| 단계 | 전체 경과 | 내부 작업 | Peak Job committed |
|---|---:|---:|---:|
| S3FD 합성 blank1frame |4.122064초 |forward0.268764초 |531,918,848bytes |
| 실제250frame 얼굴 전처리 |73.893800초 |detector68.034867초 |619,106,304bytes |
| TalkNet 네 조건 비교 |11.690164초 |각forward1.862…2.112초 |1,028,141,056bytes |

S3FD 두 실행은2GiB, TalkNet 비교는3GiB Windows Job 제한 아래 수행했다.
각 실행은exit0/termination=null/cleanup_complete=true/남은process0/출력미잘림을 확인했다.
메모리 counter는 PC의 aggregate committed memory이고 RSS/Android RAM이 아니다.
**TalkNet 추론 약2초만 따로 떼어 앱이 빠르다고 주장할 수 없다. 얼굴 전처리에약68초가 들었다.**
모바일 최적화·20초 실제자료·ARM 실행·발열·취소·제품 UI 성능은 이번 연구에 포함되지 않는다.

## 발견·수정한 실행 문제와 검토

- MFCC 첫 실행은OpenBLAS thread의 메모리 할당으로1GiB Job에 걸렸다. 실패를 보존하고 subprocess의
  OpenBLAS/OMP/MKL threads2로 제한하여 같은1GiB에서 합성8조건을 통과했다. [상세](20261004-talknet-mfcc.md).
- 첫 실제얼굴 준비는Windows의OpenCV `imread`가 한글 경로를 읽지 못해 첫frame 검사에서 멈췄다.
  동일JPEGbytes를 `Path.read_bytes`→`cv2.imdecode`로 읽도록 고쳤다. 새 모델 판정/조건 튜닝이 아니다.
  실패로그와 시작표시를 보존한 뒤250frame 처리와후속crop 검토를 통과했다.
- 공식 NMS의현대PyTorch resize deprecation 경고는남아 있다. 약2MB의로그를보존했고잘림없이완료했다.
  현재연구에서실행오류는없으나향후runtime호환/상용실행준비완료로보지않는다.
- Astra/high의실행전검토에서공통경계의문맥영향과frame-map provenance 보완을받았다.
  frame의첫/끝/개수와prepared report일치 검사,관련manifest hash,경계효과미제거표시를추가한후실행했다.
- Sol의소스/래퍼읽기검토와모델실행증거는구분한다. 실행은부모한명만수행했다.

## 근거 파일

공개해도되는연구코드는 `tools/research/talknet_probe.py`, `talknet_face_probe.py`,
`talknet_av_compare.py`, `test_talknet_probe.py`다. 공식source와checkpoint는각문서의출처/SHA를따른다.

로컬감독로그는 `work/talknet-face/{audit,synthetic,prepare,prepare-unicodefix,compare}-supervision.json`과
각 `*-supervised.log`다. 실제준비script는같은경로의 `prepare_media.py`, `prepare.py`이며
개별실측과입력계약은 `private-av10/`와그하위 `comparison/`에있다. 원본/파생물/개별결과는커밋하지않는다.

현재앱의임계값·모델·권한·기록DB·베타버전은변경하지않았다. Gemma탑재·새아기말생성·일반옹알이회복·배포는미완료다.
