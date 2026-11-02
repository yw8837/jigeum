# 영아 발성 누락: 다음 실행 비교 한 개

2026-10-06 · 공개 원천 조사 후 공식 source·weight 취득과 호스트 준비 검증을 진행했다. 이후 부모가 retained3 추론을 실행했다. 제품/빌드/ADB 변경은 없다.

**초기 비교 후보는 `PretrainedSED frame_mn10_strong_1.pt` 하나로 고정했고, 후속 retained3 실행은 회복 근거를 추가하지 못했다.** 새 영아 전문 모델을 발견했다는 결론은 아니다.
[이전 조사](20261005-temporal-model-followup.md)는 이 checkpoint의 존재만 확인했고, 저장소의 연구 실행 기록에는 이 모델의 결과가 없다.
현재 EfficientSED `fmn10+tf-256_advanced-kd-weak-strong.pt`와 다른 공개 학습 weight/구조의 **아직 실행하지 않은 작은 시간축 head**다.
실제 retained 누락 두 구간에서 EfficientSED가 이득을 주지 못했으므로, 기존 점수의 threshold 재조정 대신 이 한 번의 비교로
다른 시간축 representation에 회복 신호가 있는지를 확인했다. 현재 모델보다 낫다는 증거는 추가되지 않았다.

## 실행 계약

| 항목 | 공개 원천에서 확인한 계약 |
|---|---|
| 고정 source | [fschmid56/PretrainedSED `1aa47e482f7e89904cba2338999345025d8b4e36`](https://github.com/fschmid56/PretrainedSED/tree/1aa47e482f7e89904cba2338999345025d8b4e36) |
| 정확한 weight | [v0.0.1 `frame_mn10_strong_1.pt`](https://github.com/fschmid56/PretrainedSED/releases/download/v0.0.1/frame_mn10_strong_1.pt), [공식 release API](https://api.github.com/repos/fschmid56/PretrainedSED/releases/tags/v0.0.1)의 asset **226877789**. 후속 실제 다운로드 **15,537,114 bytes**, SHA256 **`4a0fe320d5369987b772394c51881fb20a602967e6842f72f3e5c8181065ece7`** 확인. API digest=null과 구분한다. |
| 전체 모델 | 후속 strict load로 **3,830,798 parameters / 312 state keys / embedding960**을 확인했다. 작은 head만의 크기가 아니다. 현재 EfficientSED 20,304,913B/5.03M과 동일 artifact로 합치지 않는다. |
| 실제 head | [PredictionsWrapper](https://github.com/fschmid56/PretrainedSED/blob/1aa47e482f7e89904cba2338999345025d8b4e36/models/prediction_wrapper.py): 기본 sequence model=`Identity`, linear strong/weak heads, strong **447 labels × 250 timesteps**/10초. CNN sequence를 250step으로 pool/interpolate한 grid이며 40ms 단위가 실제 경계 정확도를 보증하지 않는다. |
| 라벨 | [고정 `as_strong_train_classes`](https://github.com/fschmid56/PretrainedSED/blob/1aa47e482f7e89904cba2338999345025d8b4e36/data_util/audioset_classes.py)를 AST로 파싱해 447개 확인. **Babbling15 / Baby cry16 / Baby laughter17 / Child speech78 / Female speech157 / Male speech238 / Speech353 / Television381**. YAMNet521/AST527 index를 재사용하지 않는다. |
| 입력/시간축 | [공식 inference](https://github.com/fschmid56/PretrainedSED/blob/1aa47e482f7e89904cba2338999345025d8b4e36/inference.py): 16kHz mono, 10초 chunk, 끝 right-zero-padding, chunk별 strong logits→시간축 concat→sigmoid. 20초는 독립 10초 둘이며 단일 20초 문맥은 아니다. |
| 전처리 | [FrameMNWrapper](https://github.com/fschmid56/PretrainedSED/blob/1aa47e482f7e89904cba2338999345025d8b4e36/models/frame_mn/Frame_MN_wrapper.py) + [AugmentMelSTFT](https://github.com/fschmid56/PretrainedSED/blob/1aa47e482f7e89904cba2338999345025d8b4e36/models/frame_passt/preprocess.py): preemphasis .97, symmetric Hann400, FFT512/hop160, center reflect, **power** spectrum, Kaldi mel128, fmin0/fmax**7000Hz** in eval, natural-log floor1e-7, `(mel+4.5)/5`. `.eval()`가 augmentation을 끈다. 현재 AST 특징을 연결하지 않는다. EfficientSED frontend 재사용도 수치 비교 뒤 결정한다. |
| 로더 | [`config.py`](https://github.com/fschmid56/PretrainedSED/blob/1aa47e482f7e89904cba2338999345025d8b4e36/config.py)가 정확한 release 이름을 연결한다. `PredictionsWrapper`는 `weights_only=True`, missing/unexpected assertions를 사용하나 head shape가 다르면 제거하는 경로도 있다. **실험에서는 447-way 두 head와 key/shape 완전 대응을 별도 assert**해 무작위 새 head 비교를 막는다. |
| 상업 권리 | [고정 MIT LICENSE](https://github.com/fschmid56/PretrainedSED/blob/1aa47e482f7e89904cba2338999345025d8b4e36/LICENSE)는 use/distribute/sell 허락과 저작권 고지 보존을 명시한다. README는 이 MobileNet checkpoint를 저자 제작 `ours`로 공개한다. 별도의 NC 조건은 확인되지 않았다. **코드 MIT와 제3자 학습 오디오 재배포 권리는 구분**한다. 원음 배포를 하지 않는 로컬 비교 후보로 삼으며, 제품 탑재 시 artifact의 허락 범위와 upstream 고지를 다시 보존·점검한다. |
| Android | 이 공식 pin에 Android/TFLite/ONNX 실행 증거는 확보하지 못했다. 현재 EfficientSED의 Android parity 결과가 이 checkpoint에 전이된다고 보고하지 않는다. Python host 비교부터 시작하고 이득이 있을 때만 export/parity/ARM 비용을 진행한다. |

## 가장 작은 다음 실행

1. 완료된 준비를 사용해 실제 strong/weak output shape·finite·repeatability를 첫 추론에서 검증한다.
   공식 source14파일과 weight 한 개를 취득하고 고정 447 labels·두 head·strict state 대응·synthetic mel까지 확인했다.
   첫 추론의 CPU mel/strong logits를 기록한다. 제품 source/권한/표현/threshold를 먼저 바꾸지 않는다.
2. [EfficientSED retained comparison](20261006-efficientsed-retained-failure-screen.md)의 같은 3구간/2원본을
   개발 비교로 실행한다. 새 infant holdout이라고 부르지 않는다. 두 누락의 time-localized babble/child-speech 신호,
   기존 울음 대조와 성인/TV 회귀를 같은 원음·시간축·창 정책으로 기록한다. 각 infant event가 청취 정답으로 확정되지
   않은 구간은 unknown으로 남긴다. 낮은 점수를 성공으로 바꾸기 위해 threshold를 사후 완화하지 않는다.
3. **이미 알려진 누락에 후보 신호가 없으면 이 경로를 종료**한다. 신호가 있으면 점수를 보지 않고 먼저 만든
   별도 family/source 분리 정답의 infant babble/cry/laugh와 adult/TV/재생 아기 hard negatives에서 고정 판정을 비교한다.
   miss 감소와 false accept 유지/감소가 함께 확인돼야 export로 간다. class별 event recall·보류 coverage·false accepts와
   원본/가족 수를 보고한다. 현재 3구간만으로 일반 인식 개선이나 화면 속 아기 신원을 주장할 수 없다.

이 next step은 원음·embedding을 외부로 보내지 않는 로컬 실행이다. 현재 가능한 것은 공개 head의 비교이며,
새로운 대상 월령 정답·성인/TV 반례와 ARM 실측이 없는 상태에서 제품 교체를 승인하는 결과는 아니다.

## 새로 발견했으나 제외한 113KB 경로

HF [`chayuto/yamnet-cry-distill-int8` pin `7a1999feb3ccd956f33adb4dddbac78a6ab53e25`](https://huggingface.co/chayuto/yamnet-cry-distill-int8/tree/7a1999feb3ccd956f33adb4dddbac78a6ab53e25)
와 [blobs API](https://huggingface.co/api/models/chayuto/yamnet-cry-distill-int8/revision/7a1999feb3ccd956f33adb4dddbac78a6ab53e25?blobs=true)는
`model.tflite` **112,848B**, LFS SHA256 `cf7f879e2ae065f06ddc209830395bc1b448a5f819db44c7d40195095758f5ba`를 공개한다.
[source v0.2.0 commit `57178b971d9c936063fc3ae854f2e9c0c171158c`](https://github.com/chayuto/yamnet-cry-distill-int8/tree/57178b971d9c936063fc3ae854f2e9c0c171158c)
와 MIT 허락도 연결된다. TFLite INT8·ESP32 실행은 저자 보고이며 Android 실행을 직접 확인한 것은 아니다.

이 작은 크기가 이번 목표의 적합성을 만들지는 않는다. [고정 card](https://huggingface.co/chayuto/yamnet-cry-distill-int8/blob/7a1999feb3ccd956f33adb4dddbac78a6ab53e25/README.md)는
**Babbling을 cry hard negative로 학습**, score는 YAMNet `Crying, sobbing19 + Baby cry20`를 합산한다고 명시한다.
성인 울음 분리나 옹알이/웃음 head 개선 근거가 없다. 단일 가정의 teacher-filtered 증류와 caregiver 동시 발성 편향도 있다.
README의94% recall 주장은 [INT8 평가 JSON](https://huggingface.co/chayuto/yamnet-cry-distill-int8/blob/7a1999feb3ccd956f33adb4dddbac78a6ab53e25/eval_audioset_holdout_int8.json)의
best recall **16/18=.8889**와 다르다. precision .64는 TP16/FP9, 즉 대조 **9/44** 오인을 뜻하며,
best threshold는 같은 test slice에서 선택했다. `.5`에서는 TP3/FN15다. 이를 누락 감소와 성인/TV 오인 유지의 근거로 삼지 않는다.
config의 `.96초` patch 명명과 15,600samples(**물리적 .975초**)는 특징창과 실제 receptive waveform 길이를 구분해 읽어야 한다.
README의v0.2.0/config의v0.1.0 표기도 다르므로 실제 frontend/artifact 대응 검증 전 재사용하지 않는다.

## 후속: 실행 harness 준비 결과와 실행 명령

소유 파일 [runner](../../tools/research/pretrainedsed_probe.py), [pin](../../tools/research/pretrainedsed-model.json)에만 코드를 추가했다.
공식 source14파일은 `work/pretrainedsed-20261006/source/`, weight 한 개는 같은 실험 루트에 있다.
각 source의 SHA256를 pin에 보존했고 공식 release weight 실제 크기/SHA를 검증했다.
기존 연구 runtime Python3.13.14, torch/torchaudio2.11.0+cpu, torchvision0.26.0+cpu, NumPy2.5.2를 사용한다.

최종 `validate03`은 **36조건의 manifest·PCM 해시/길이/finite를 확인**했지만 classifier forward는 **0회**다.
312 state keys·전체 tensor shape와 strong/weak447-way 두 head를 `strict=True`로 로드했으며,
10초 silence의 공식 frontend `(1,1,128,1000)`/finite/반복 일치를 확인했다. 모델 출력 shape·분류 성능은 미검증이다.
Python socket 연결을 차단하고 Windows Job **180초/4GiB**, CPU4threads로 실행한다.
최종 검증 worker exit0·9.162초·cleanup true·잔여 자손0, Job aggregate peak committed 약983.0MB다.
이는 실폰 RAM/속도 측정이 아니다. `validate01`은 불필요한 raw expanded-input를 함께 넣어 PCM contract에서 실패했다.
이 실패·정리 로그를 보존했고 `validate02/03`은 정확한 retained3+기존33만으로 통과했다. 추론은 어느 검증에서도 하지 않았다.

**먼저 retained3만 실행한다.** 아래는 저장소 루트 기준 PowerShell이다. 이미 있는 output 이름을 덮어쓰지 않는다.

```powershell
& 'C:/Users/최용우/AppData/Local/Temp/agattok-efficientat-20261005/venv/Scripts/python.exe' tools/research/pretrainedsed_probe.py --assets work/pretrainedsed-20261006 --corpus work/sed-followup-20261006/retained-corpus --output work/pretrainedsed-20261006/retained-run01
```

회복 가능 신호가 있을 때만 같은 고정 runner로 공개33조건을 실행한다.

```powershell
& 'C:/Users/최용우/AppData/Local/Temp/agattok-efficientat-20261005/venv/Scripts/python.exe' tools/research/pretrainedsed_probe.py --assets work/pretrainedsed-20261006 --corpus work/ast-public-paired-01/corpus --output work/pretrainedsed-20261006/public-run01
```

모든 경우의 `scores-private.json`은 nominal40ms grid와 실제 PCM sample count를 저장한다.
마지막 `bin_end_ms`는 **실제 sample/16**으로 자르며 manifest의 정수 ceil `duration_ms`를 실제 파형 길이로 대신하지 않는다.
성공 output 수를 사건 재현율로 보고하지 않는다. 공개33은 기존 개발 회귀이며 독립 holdout이 아니다.

최종 준비 증거는 `work/pretrainedsed-20261006/validate03/{validation-private.json,process.json,inference-private.log}`에 있다.
판정이 끝난 뒤 제거할 재생성 대상은 **이 실험의 `source/`와 `frame_mn10_strong_1.pt` 한 개**다.
pin·runner·연구 문서·검증/추론 JSON·로그와 기존 corpora/사용자 원음을 보존한다.
source 재다운로드는 pin revision의 `raw.githubusercontent.com/fschmid56/PretrainedSED/<revision>/<source_files key>`,
weight는 pin의 공식 release URL을 사용하고 저장된 SHA256를 다시 확인한다. 부모의 실험 종료 정리 단계에서 위 재생성 대상만 제거한다.

### 부모의 첫 추론 결과

부모가 위 retained-run01 명령을 실행했고 이 문서 담당자가 실제 `scores-private.json`과 `process.json`을 읽어 확인했다.
3구간의 classifier forward는 완료됐으며 exit0·8.232초·cleanup true·잔여 자손0이다.
두 기존 누락 구간의 Babbling max는 약**.151/.140**, Child speech 약**.0068/.0027**이며
둘 다 관찰용0.5 위치는0개다. 울음 대조의 Baby cry max는 약**.196**, Music max약**.722**다.
작은 점수를 제품 성공으로 재정의하거나 threshold를 조정하지 않았다.
**확정 회복·모델 일괄교체 근거가 없어 공개33 추론을 추가하지 않고 이 후보 경로를 종료**한다.
원본별 기존 개발 회귀 자료의 결과이며 일반 사건 정확도·실기기·성인/TV 오인율을 입증한 것은 아니다.
위 실행 명령은 재현용 이력이다. 새 조건 없이 같은 추론을 반복하지 않는다.

### 부모 요청에 따른 PANNs 권리·추론 계약 확인

PretrainedSED의 실패 뒤 부모가 PANNs Cnn10의 실제 비교를 이어가기로 했다. 아래는 별도 weight를 받거나 실행하지 않은 원천 확인이다.
[PANNs source `d2f4b8c18eab44737fcc0de1248ae21eb43f6aa4`](https://github.com/qiuqiangkong/audioset_tagging_cnn/tree/d2f4b8c18eab44737fcc0de1248ae21eb43f6aa4),
[Zenodo3987831 API](https://zenodo.org/api/records/3987831)는 checkpoint 배포의 **CC BY4.0**를 명시한다.
`Cnn6_mAP=0.343.pth` **23,698,321B/MD5 e25e26b84585b14c7754c91e48efc9be**,
`Cnn10_mAP=0.380.pth` **25,237,595B/MD5 bfb1f1f9968938fa8ef4012b8471f5f6**다.
이는 원격 메타데이터이며 실제 파일 SHA256 검증이 아니다. 코드 [MIT](https://github.com/qiuqiangkong/audioset_tagging_cnn/blob/d2f4b8c18eab44737fcc0de1248ae21eb43f6aa4/LICENSE.MIT)와
checkpoint CC BY4.0는 상업 사용을 허용하며 각각 attribution/license notice를 보존해야 한다.
AudioSet 원음의 별도 권리를 이유로 이 공개 checkpoint 허락을 자동으로 금지하지 않는다. 원음 재배포·새 학습 자료 취득은 별도다.

[고정 models.py](https://github.com/qiuqiangkong/audioset_tagging_cnn/blob/d2f4b8c18eab44737fcc0de1248ae21eb43f6aa4/pytorch/models.py)의
Cnn6/Cnn10 모두 `fc_audioset:512→527`, sigmoid **clipwise_output+512dim embedding**이다.
시간축을 frequency-mean→time-max+time-mean으로 합쳐 **native framewise head는 없다**.
[공식527 CSV](https://github.com/qiuqiangkong/audioset_tagging_cnn/blob/d2f4b8c18eab44737fcc0de1248ae21eb43f6aa4/metadata/class_labels_indices.csv)의
Babbling6/Baby laughter17/Baby cry23/Child speech3/Female2/Male1/Speech0를 확인했다.
[고정 inference](https://github.com/qiuqiangkong/audioset_tagging_cnn/blob/d2f4b8c18eab44737fcc0de1248ae21eb43f6aa4/pytorch/inference.py)의
기본값은 **32kHz mono·Hann1024/hop320·center reflect·mel64·50–14000Hz**이며 ref1/amin1e-10/top_db=None이다.
16kHz PCM을 32kHz로 실제 resample해야 하며 고주파 복구로 표현하지 않는다.
torchlibrosa0.0.4/torch1.0.1 등 [구형 requirements](https://github.com/qiuqiangkong/audioset_tagging_cnn/blob/d2f4b8c18eab44737fcc0de1248ae21eb43f6aa4/requirements.txt)가
현재 runtime과 다르므로 다음에 실행할 경우 정확한 frontend/state 대응을 먼저 확인한다.
공식 loader의 `torch.load(...)[model]`을 그대로 unsafe load하지 않고 `weights_only=True`와 strict527 head 계약을 사용해야 한다.
Android/export 실행 근거는 이번 pin에서 확보하지 못했다. 두 checkpoint의 공개 존재를 시간축 SED 개선이나 영아 발성 회복으로 보고하지 않는다.
저장소의 `tools/research`, `docs/research`, ignored `work`의 JSON/Markdown/Python/log에서
`Cnn10/Cnn6_mAP/audioset_tagging_cnn`을 검색한 결과 기존20261001 문헌 조사 외 **실제 실행 기록을 찾지 못했다**.
이는 다른 외부 디렉터리까지 모델 사용이 전혀 없었다는 단정은 아니다. 새 Cnn10 host 비교는 부모가 별도 runner로 맡으며,
native temporal head가 없다는 점만으로 clipwise 보조 신호의 비교를 금지하지 않는다.

조사 경로: research·agent-reach의 gh search/API·공식 HF API/raw source. Exa 재호출·유료API·가입·저자 연락·Elice·설치 없음.
후속에서 source14파일과 weight 한 개만 취득했다. 원음/특징 외부 전송·제품/Gradle/ADB 변경은 없다. Agent Reach1.5.0 최신 확인.
