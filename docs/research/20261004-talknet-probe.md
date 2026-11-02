# TalkNet CPU probe — 구현과 사전 검증 계약

2026-10-04 · source `6d6821479af485e251c4991487e40573b42181b4` · 제품 비활성 연구 도구

## 현재 상태와 목적

[공식 실행 구조 검토](20261004-talknet-runtime-review.md)를 바탕으로 `tools/research/talknet_probe.py`와 `test_talknet_probe.py`를 작성했다. 공식 model4개 파일과 loss.py를 SHA 확인 후 **바이트 그대로** ignored `work/talknet-probe/official/`에 복제했다. 계산 본문을 패치하지 않았다. Sol의 읽기 검토 후 부모가 bounded import와 **합성 feature 한 번의 forward를 실행하여 통과**했다. 작성자는 소스/metadata/실측 JSON만 읽었으며 모델·원본 미디어를 직접 실행하거나 열지 않았다.

이 probe는 입 움직임과 음성의 관계를 점수화하는 경로를 검증한다. 합성 출력이 나오더라도 아기 발성 주인, 옹알이 종류, 감정·욕구, 영상/의도 문장 생성, Android 성능이 검증된 것은 아니다. 이전 SpeechMaturity 분류 실패의 조건을 바꾸어 반복하는 실험도 아니다.

## 고정 파일과 복원

- checkpoint: 공식 demo의 기본 TalkSet 공개 Drive 파일을 부모가 받은 `work/talknet-checkpoint-audit/pretrain_TalkSet.model`.
- 크기63,227,827 bytes, 로컬 SHA256 `d985ddd07dca08864a28337726f5b7d91b6426bebc7327f0e9b21cf9ff1cc937`.
- upstream 게시 digest/불변 weight revision은 없으므로 위 hash는 **받은 파일의 로컬 고정**이다.
- 저장 감사 metadata:487 tensors, `model`481 / `lossAV`2 / `lossA`2 / `lossV`2. dtype은float32와BatchNorm counter int64다. 이전 제한 로딩 감사와 이번 실제 module strict 복원은 별도 단계다.

CPU 컨테이너는 `model=talkNetModel()`, `lossAV=lossAV()`, `lossA=lossA()`, `lossV=lossV()`로 전체 prefix를 보존한다. 보조 audio/visual loss head와 실행 중 사용되지 않는 등록 weight도 제외하지 않는다. checkpoint size/SHA를 확인하고 `torch.load(weights_only=True,map_location='cpu')`로 읽어 전 키·shape·dtype·finite·그룹 수를 대조한 뒤 `strict=True`로 복원한다. 무작위 잔여 weight, 이름 유사도 mapping, `strict=False`, unrestricted pickle은 허용하지 않는다.

공식 `talkNet.py`는 import하지 않는다. 따라서 `.cuda()`, optimizer, scheduler, train/evaluation dataset, 원래 permissive loader를 호출하지 않는다. 공식5파일은현재torch만 import하며 추론의 numpy 변환 때문에 NumPy도 필요하다. **합성 feature gate에는 새 패키지가 필요 없다.**

## import 및 CPU 호환 범위

공식 `model/talkNetModel.py`의 absolute `model.*` import를 유지하기 위해 전용 연구 subprocess에서 임시 module namespace를 만든다. 이미 `model` package가 있으면 충돌을 거부하며, 지정한5파일만 hash 검증 후 import하고 finally에서 임시 이름을 제거한다. source 파일의 import 문이나 계산 메서드는 수정하지 않는다. 이 방식은 범용 앱 내 동시 import 경로가 아니다.

현재 torch2.13.0+cpu에서 Conv/BatchNorm/TCN/MultiheadAttention/FC를 원문대로 실행하여 합성 gate를 통과했다. source의 torch≥1.6 환경과 현재 backend의 **수치 parity를 검증한 것은 아니다**. `MultiheadAttention(tar,src,src)`와 src residual, visual GlobalLayerNorm의 시간·채널 평균 및eps1e-8, visual normalization 등은 그대로 뒀다. 모델의 모든 child module이 eval이며 parameter는 CPU·gradient 비활성인지 실행 때 검사했다.

B=1만 허용한다. 공식 visual frontend가 중간에서 B*T를 하나의 temporal 축으로 재배치하므로 여러 사례를 배치로 넣어 경계 의미를 바꾸지 않는다.

## 입력·출력 계약

| 항목 | 고정 계약 |
|---|---|
| 음성 입력 |CPU contiguous float32 `[1,4T,13]`, 이미 계산한 MFCC |
| 영상 입력 |CPU contiguous float32 `[1,T,112,112]`, 회색조0..255, 단일 얼굴 track |
| 시간축 |25fps 영상과100Hz MFCC의 정확한4:1 frame 수. 어긋나면 거부 |
| 길이 |`1 <= T <= 500`, 최대 명목20초. 최초 합성 검증은T25만 |
| pixel normalization |공식 model 내부 `(x/255 - .4161)/.1688`; 외부에서 중복 적용하지 않음 |
| 실제 crop |demo의224×224 face crop→gray/resize224→중앙 `[56:168,56:168]`로112×112 |
| output |AV FC raw logits `[T,2]`와 공식 `lossAV(labels=None)`의 class1 raw logit `[T]` |
| 시간표 |`start_seconds + frame_index/25`의 **명목 시간**. 실제 decoder PTS 검증을 대신하지 않음 |

최종 class1이 FC 출력 두 번째 열과 정확히 같은지 검사한다. softmax·확률·threshold·아기 판정으로 변환하지 않는다. `labels` 입력 자체가 probe API에 없다. model의 실제 audio/visual frontend가 `[1,T,128]`, cross-attention도 같은 길이, backend가`[T,256]`인지 확인한다.

입력 cap500프레임은 메모리·실행시간 통과 결과가 아니다. 20초의 frontend activation과 attention 자원 사용은 아직 측정하지 않았다. 원본 decoder, MFCC, face detector/tracker, 시각화·멀티duration 평균도 이 래퍼에 없다.

### 1초 원음과 1초 합성 feature의 차이

첫 합성 검사는100 MFCC형 feature/25 영상 frame을 직접 만든다. 이 feature는 실제 물리적 MFCC나 얼굴이 아니다. `python_speech_features`의 흔한 ceil framing(400sample window/160hop)을 적용하면16000sample 원음은99 MFCC frames가 되고, demo는이를96MFCC/24video로 맞출 수 있다. 실제 package version/default를 고정해 확인하기 전까지 “1초 원음=100 MFCC”라고 단정하지 않는다. 마지막 짧은 정렬 chunk를 보존할 수 있도록 wrapper 하한은1프레임이며, 짧은 chunk의 품질을 보장하지 않는다.

## 최초 실행 gate

부모가 Windows Job 제한 아래 실행한다. 기존 output 또는 started marker가 있으면 묵시적 재실행을 거부한다.

```powershell
work/ast-portability/venv/Scripts/python.exe tools/research/test_talknet_probe.py --checkpoint work/talknet-checkpoint-audit/pretrain_TalkSet.model --sources work/talknet-probe/official --output work/talknet-probe/synthetic-report.json
```

- 네트워크 차단, CPU threads2/inter-op1, training 없음.
- 소스5파일 hash, 전체487키 strict 복원·dtype·finite·freeze 확인.
- 1초 합성 AV feature **한 번** forward. `[25,2]` 유한 logits, 원문 class1 경로 동일성, nominal25fps 시간축 확인.
- 입력 mismatch, batch2, 잘못된224 crop, 빈track,20초 초과, NaN/Inf, pixel범위, dtype, 음수 시작시간 등10가지 거부 확인. 잘못된 입력은 실제 모델을 실행하지 않는다.
- 점수의 크기/부호나 class 선택을 합성 품질 성공으로 세지 않는다. MFCC·track·무음/shift·영아·기기·제품 품질 검증 flag는false다.

### 실제 첫 실행 결과

`work/talknet-probe/import-supervision.json`, `synthetic-supervision.json`, `synthetic-report.json`을 직접 읽어 확인했다.

| 항목 | 실측 |
|---|---|
| 환경 |torch2.13.0+cpu, source5파일 hash 일치 |
| state strict 복원 |model481 + lossAV2 + lossA2 + lossV2 =487 |
| 입력 |합성 features `[1,100,13]`, `[1,25,112,112]`; 원음/원본 영상 없음 |
| 중간 shape |audio/visual frontend 및cross 각 `[1,25,128]`, AV backend `[25,256]` |
| 출력 |float32 finite `[25,2]`; 공식 no-label class1 raw logit 일치 |
| nominal 시간 |25fps frame 시간표 검사 통과; 실제 PTS 검증 아님 |
| 잘못된 입력 |10종 모두 거부 |
| frozen/eval |통과 |
| import 실행 |2.498068초, peak Job committed829,247,488 bytes |
| 합성 전체 실행 |2.958445초, peak Job committed1,048,666,112 bytes |
| model load / forward |0.267229초 /0.221565초 |
| 모델 추론 횟수 |**1회**, 반복 안정성 미검증 |

두 감독 실행 모두2GiB Job 아래 exit0, termination=null, cleanup_complete=true, remaining_job_processes=0, memory_limit_hit=false, output_truncated=false다. Job committed 메모리는 RSS/Android 메모리와 다르며1초 합성 시간은 실제 영상 전처리나20초 성능을 입증하지 않는다. 검증된 probe source SHA256은 `da7104320fef1c2cbfbb0b0593a843b8bb9f153c4e6485a4f514b27711816eb9`다.

MFCC 전처리·face tracking·무음/shift 품질·영아 도메인·원래torch1.6 수치동등성·Android·제품 품질·20초 자원 gate는 모두 **미검증**이다. 합성의logit값/부호를 연구 품질 통과로 세지 않는다.

## 무음·시간이동 대조에 필요한 사전 조건

실제 원음 단계는 합성 runtime gate 다음이다. 아래 원칙을 **모델 점수를 보기 전에** manifest에 고정한다.

1. 기존 승인된 로컬 자료에서 동일 얼굴 track·동일 구간을 사용한다. 입력과 파생물은 외부 전송하지 않는다. 얼굴이 안정적으로 보이는 최소3초 구간 안에 관찰 가능한 입 움직임/소리 시작·끝과 quiet 구간이 있어야 한다. 계속 말하는 구간만 있으면 단순500ms shift로 active-speaker 정답이 달라진다고 가정할 수 없다.
2. 얼굴 track의 주체·연속성, audio 시작점과 video PTS/fps mapping을 점수와 독립적으로 확인한다. 성인/아기 얼굴 교체, 가림, 중복·누락 frame은 기록하고 unsupported 범위를 분리한다. 얼굴 위치만으로 음성 주인을 정답 처리하지 않는다.
3. baseline 정렬 음성, **동일길이 PCM 무음**, 고정 ±500ms 음성 shift를 같은 video tensor에 대응한다. shift는16kHz에서±8000samples로 고정하고 순환 wrap 없이 빈 부분을zero-fill한다. 모든 조건을 같은 MFCC pipeline으로 다시 계산한다. **MFCC tensor에0을 넣는 것은 무음 원음과 다르다.**
4. 결과 비교는 양쪽shift가 유효한 공통 중앙 시간 범위와 동일 관찰 event 구간으로 제한한다. 최소 양끝500ms와 MFCC frame 지지 범위의 경계를 제외하고, 남는 frame index를 추론 전에 고정한다. video crop·gain·필터·window 구성·checkpoint를 조건별로 바꾸지 않는다.
5. 최초 대조는 고정한 동일길이 한 window로 수행한다. 공식 demo의duration1..6 ensemble을 실행한 것으로 표현하지 않는다. baseline/silence/shift raw logit 시간열과 paired 차이를 모두 보존하며 결과를 보고 offset·조건·window·threshold를 고르지 않는다.
6. 예상되는 탐색 신호는 관찰 event에서 baseline이 무음/두shift보다 높은지다. 이는 사전 가설이며 통과를 보장하지 않는다. sustained speech, 가림 등으로 대조가 성립하지 않으면 입력 적격성 실패로 기록한다. 순서가 뒤집히거나 차이가 없으면 이번 표본에서 동기 구분 근거를 얻지 못한 것으로 남긴다. 창/frame을 독립 정답 표본으로 세거나 작은 양의 차이를 제품 정확도로 바꾸지 않는다.

TalkNet은 active-speaker 모델이지 전용 오디오-입술 offset 추정기가 아니다. 위 대조의 실패만으로 모든 ASD 능력을 부정할 수 없고, 성공해도 영아 일반화·음성 소유자 확정·의도 해석의 충분조건은 아니다.

## 실제 AV crop 단계의 다음 최소 작업

이 절은 **실제 AV 전처리 전에 고정한 계약**이다. 이후10초·25fps·250frame 전처리와 네조건 비교까지완료했다.
최신결과는 [실제 AV 비교](20261004-talknet-av-comparison.md)와아래후속결론을따른다.

- 먼저 사용할 source 구간·25fps 변환 정책과 원래 PTS mapping·동일 얼굴 bbox track을 고정한다. 기존 face track을 활용하더라도25fps 전 구간의 연속성과 원인 불명 보간을 확인해야 한다. 검증된 track이 없으면 그 부재를 남기며 얼굴 검출 성공을 꾸며내지 않는다. 후속으로 부모가 공식 S3FD weight를 확보하고 제한 감사를 수행한 사실은 아래 별도 절에 기록했다.
- 첫 원음 대조에는 **공식 S3FD 기반 crop 경로가 더 근거 있다.** 고정 FAQ §3.1은 TalkSet의 face가S3FD로 만들어졌고 검출기의crop차이가성능에영향을준다고 설명한다. 기존 MediaPipe crop을동등하게볼수있는근거는없다. 공식source/weight/CPU경로로crop차이의혼입을줄이되, 빈 합성frame 실행 통과를 실제 track이나영아얼굴 검출성능으로확장하지 않는다.
- 공식 `crop_video`의 bbox center/반크기 계산, kernel13 median smoothing, cropScale.40, border110과 비대칭 crop 좌표→resize224를 기준으로 대조한다. 다음 단계의gray→center112와 구분한다. dataloader의 직접112resize/wrap-padding을 demo 경로에 섞지 않는다.
- 원demo의 JPEG/XVID 중간 encode를 생략해 메모리 crop을 쓰면 pixel이 bitwise 같다고 말할 수 없다. 사용한 decoder/color conversion/interpolation/codec 경로를 manifest에 기록하고, 비교 조건 간에는 동일 crop tensor를 재사용한다.
- mono16kHz PCM_s16le와고정 `python_speech_features`의 실제 version/hash·MFCC defaults를 기록한다. numcep13,25ms/10ms 외에도preemphasis/filterbank/NFFT/lifter/energy/window defaults가 결과에 관여한다. NumPy/SciPy/OpenCV/ffmpeg의 사용 여부와 설치 버전은 원음 단계 전에 확인하며, 현재 준비를 이유로 설치하지 않는다.
- audio/video를 독립적으로 truncate하여 겉으로4:1만 맞추지 않는다. 같은 track 시작·끝을 자르고 demo식 길이 조정으로 제외된 잔여 samples/frames와 source 시간 coverage를 기록한다. 만들어진 MFCC/112crop의shape·dtype·finite·hash 및몇개 frame의입이crop안에있음을 로컬에서 확인한 뒤에만 고정 대조를 실행한다.

이 준비는 성숙도 분류기의 실패를 반복 튜닝하는 일이 아니다. AV 주체 정합성이라는 별도 가설을 검증하는 경로이며, 영상/의도 생성 목표는 여전히 남아 있다. 제품 코드·임계값·Gemma·ADB/Gradle·배포는 변경하지 않는다. 코드 MIT와 model/학습자료의 상용 재배포 권리는 별도 미해결이다.

## S3FD 후속 CPU 실행 준비와 합성 실측

부모가 같은 공식 source commit의 S3FD와 연결된 `sfd_face.pth`를 확보했다. source 근거는 `work/talknet-face/manifest.json`과 `model/faceDetector/s3fd/{__init__,nets,box_utils}.py`다. wrapper는 `tools/research/talknet_face_probe.py`이며 Sol의 읽기 검토를 거쳐 부모가 bounded 실행했다. 이 작성자는 원본 frame이나 checkpoint를 직접 읽거나 모델을 실행하지 않았다.

### 고정 checkpoint 및 원문 계산

| 항목 | 결과 |
|---|---|
| checkpoint 파일 크기 |89,844,381 bytes |
| 로컬 SHA256 |`d54a87c2b7543b64729c9a25eafd188da15fd3f6e02f0ecec76ae1b30d86c491` |
| serialization |legacy torch serialization, ZIP 아님 |
| 저장 제한 감사 |torch2.13.0+cpu, weights_only CPU, tensor 65개 / 89,836,440 tensor bytes |
| 실제 module 복원 |전체 65개 key·shape·dtype·finite 대조 후 strict load 통과 |
| source |nets.py·box_utils.py 원문 파일 hash 확인 후 그대로 import |
| 검출 설정 |RGB uint8, scale 0.25, conf 0.9로 고정 |

로컬 hash는 받은 파일의 고정이며 upstream의 서명 또는 게시된 불변 revision을 확인했다는 뜻이 아니다. 감사의 근거는 `work/talknet-face/structure.json`이며, ZIP이 아닌 파일에 ZIP 검사 통과 주장을 붙이지 않는다.

공식 S3FD 초기화 파일의 top-level 다운로드·torchvision import·CUDA 생성은 실행하지 않는다. 대신 `detect_faces` 메서드만 AST로 추출하여 기존 CPU net에 연결한다. 원래 RGB/BGR 두 번의 channel swap, `[104,117,123]` mean, resize·decode·NMS 계산을 보존한다. 계산 경로에 적용한 호환/경계 변경은 정확히 둘이다.

1. `box_utils` 모듈 로컬 numpy 참조에만 없어진 `np.int`를 옛 의미인 builtin `int`로 공급한다. process-global NumPy를 변경하거나 NMS 식을 바꾸지 않는다.
2. score 추출의 `while`에 `j < detections.size(2)`를 추가한다. top-k 750개가 모두 confidence를 통과할 때 원래 코드가 배열 끝을 넘어 읽는 오류를 막으며, 정의된 750개 후보의 선택은 바꾸지 않는다.

원래 tensor `nms()`는 빈 boxes에 이미 `(keep,0)`을 반환한다. 이 부분은 수정하지 않았고 합성 gate에서 직접 검사했다. 현재 torch/OpenCV의 연산을 원저자의 옛 CUDA 환경과 수치적으로 동등하다고 주장하지 않는다.

### 부모의 실제 합성 실행

`work/talknet-face/synthetic-report.json` 및 `synthetic-supervision.json`을 직접 읽어 확인했다.

| 검증 | 실측 |
|---|---|
| 환경 |torch2.13.0+cpu, NumPy2.4.6, OpenCV5.0.0 |
| 입력 |빈 RGB uint8 `[720,1280,3]` 한 frame |
| 실제 net 실행 |**1회**, 0.268764초 |
| load / 전체감독실행 |0.303980초 /4.122064초 |
| 출력 |finite `[0,5]`, 빈 frame 검출0개 |
| 경계검사 |tensor/NumPy empty NMS, 가짜 750개 후보로 top-k 루프 상한, 잘못된 입력 4종 통과 |
| peak Job committed |531,918,848 bytes, 2GiB cap내 |
| 종료 |exit0, termination=null, cleanup_complete=true, remaining_job_processes=0 |
| 제한/로그 |memory_limit_hit=false, output_truncated=false |

top-k 경계검사는 가짜 검출 tensor를 공급한 parser 검사이며 추가 실제 network forward가 아니다. 검증된 wrapper SHA256은 `67a63594b5a98ccd7cb46b06f66e4a29340ba092747b7bca72efec0ccd920a07`이다. 이 gate는 CPU 복원·shape·유한성·경계 처리가 작동한다는 증거이며 얼굴 검출 정확도·영아 얼굴·track 연속성·TalkNet 주체 판단·앱/Android 성능은 여전히 미검증이다.

### 실제 JPEG 경로의 진행 상태

부모 보고에 따르면 최초 실제 준비는 OpenCV `cv2.imread`가 한글 경로의 첫 JPEG를 읽지 못하여 첫 실제 frame 검출 전에 중단됐다. 부모가 **동일 JPEG bytes를 읽어 `cv2.imdecode`에 전달하는 방식**으로 경로 처리를 수정한 후 250frame 실행을 진행 중이다. 검출 scale/confidence·모델 weight·원본 이미지 내용을 변경하지 않았으며, 이전 중단을 인식 품질 실패나 이후 완료로 세지 않는다.

진행 중인 250frame의 완료 여부, 검출·보간 coverage, 입이 crop 안에 있는지와 track 주체 연속성은 별도 결과를 확인해야 한다. 독립 발성 event 라벨 없이 baseline/무음/shift를 실행하면 **조건에 따른 점수 민감도**만 보고하고 active-speaker 정답 검증으로 표현하지 않는다. 앱 코드·배포 변경은 없다.

## 후속 결론 — 실제 4조건 완료 후 채택 판단

앞 절의 준비 진행 상태는 이후 완료됐다. 부모 보고상 10초·25fps의 250frame에 얼굴이 연속 검출됐고 보간은 0이며, 표본 5개에서 입이 crop 안에 있음을 확인했다. 이 작성자는 원본 미디어를 열지 않았으며, 아래 실행·해석은 로컬 `compare-supervision.json`, `execution-contract-private.json`, `comparison-private.json`을 읽은 결과다. 개별 점수·원본 식별 정보는 이 문서에 옮기지 않는다.

고정 4조건은 모델 입력 249frame, 사전 고정 공통 비교 223frame에서 모두 완료됐다. 전체 감독 실행 11.690164초, 각 forward 약 1.86~2.11초, peak Job committed 1,028,141,056bytes이며 exit0·자손 정리 완료·메모리 제한 초과 없음이다. 이는 PC의 준비 완료 tensor 추론 실측이고 얼굴 검출을 포함한 앱 전체 지연이나 Android 성능이 아니다. 실행 계약에는 준비물 해시와 `contextual_boundary_effects_excluded=false`가 기록됐다. 0.56초 마스크는 직접 MFCC 경계 위치를 제외하지만 전체 시간 풀링·attention을 통한 경계 영향은 제거하지 않는다.

**판정: 제품 채택은 보류하고, 독립 라벨로 평가할 연구 후보로 유지한다.** 원음이 무음보다 높았지만 한 시간이동 조건이 원음보다 높아, 이 표본에서 원래 정렬이 양방향 이동보다 우세하다는 근거를 얻지 못했다. 원음/무음 차이만으로 입과 음성의 결합 관계, 아기 발성 주인 또는 누락 회복을 입증할 수 없다. 독립 사건 라벨이 없고 TalkNet은 전용 offset 추정기가 아니므로 이 순서만으로 ASD 전체의 실패도 확정할 수 없다. 10초 단일 window는 공식 demo ensemble이 아니다. 점수에 맞춰 threshold·offset·window를 조정하거나 이번 frame 수를 독립 정확도 분모로 쓰지 않는다.

### 다음 한 단계: 독립 사건 라벨을 갖춘 로컬 AV 검증 묶음

다음 산출물은 **모델 점수를 보지 않고 사람이 발성 주인과 사건 경계를 표시한 로컬 평가 manifest**다. 기존 자료에서 분명히 판별 가능한 사건을 우선 사용하고, 아래 대조가 없다면 해당 장면을 포함하는 별도 로컬 촬영 또는 이용 가능한 공개 AV 자료로 보완한다. 동일 원본에서 창을 더 잘라 독립 사례 수를 늘리지 않는다.

- 화면의 아기가 실제로 발성하는 사건: 시작·끝, 얼굴/입 가시성, 발성 주인 근거를 표시한다. 기존 누락 영상은 알려진 실패 사례로 별도 표시한다.
- 아기는 조용하고 보호자만 말하는 사건: 아기 얼굴은 보이면서 오디오 에너지가 존재하는 대조다. 무음보다 높다는 반응이 단순 음성 존재 반응인지, 해당 얼굴의 발성을 구분하는지 확인한다.
- 아기가 소리 없이 입을 움직이는 사건과 실제 무음 사건: 시각 움직임만으로 발성을 잘못 연결하는지 확인한다.
- 발성 주인·시작/끝이 불분명하거나 입이 가려진 구간은 `unknown`으로 남기고 coverage를 보고한다. 안 보이는 입 때문에 실제 발성을 부정하는 제품 규칙을 만들지 않는다.

모델 결과를 모르는 검토자가 사건/주인 라벨을 확인하고, 새 장면을 포함한 평가 구간·전처리·현재 단일 window 실행 정책을 먼저 고정한다. 그 다음 고정 모델을 한 번 평가하여 사건별 발성/비발성 점수 분리와 누락·잘못된 연결을 검토한다. frame별 관측은 같은 사건에 종속되므로 결과는 사건 및 원본 단위로 요약한다. 이 첫 묶음은 후보를 계속 검증할 가치가 있는지 판단하는 파일럿이며 일반 정확도 추정이나 출시 통과를 대신하지 않는다.

이 단계가 해결하는 질문은 **기존 소리 모델이 놓친 아기 발성을 영상과 함께 포착하면서 보호자 목소리를 아기에게 잘못 붙이지 않는가**다. 구분 근거가 생기면 발성 주인에 대한 보조 증거로 검증을 이어갈 수 있다. TalkNet 자체는 옹알이 종류·감정·욕구·아기말 생성 모델이 아니므로 이 평가 통과를 그 기능의 완료로 바꾸지 않는다. 앱 registry·기존 임계값·제품 경로·배포는 변경하지 않았다.
