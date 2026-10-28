# TalkNet-ASD CPU 추론 경로·입력 계약 소스 검토

검토일: 2026-10-04 (KST). 저자 공식 `TaoRuijie/TalkNet-ASD`의 commit `6d6821479af485e251c4991487e40573b42181b4`를 고정했다. 범위는 코드의 정적 검토와 부모 작업자가 제공한 후속 체크포인트 구조 로그의 읽기다. 이 문서 작성자는 모델 모듈을 import/생성하거나 weight를 로드하지 않았으며, 모델 forward·설치·원본 미디어 접근을 하지 않았다.

## 결론과 증거의 단계

공식 코어 모델은 PyTorch CNN/TCN/attention 모듈로 구성되어 있으며, 훈련 wrapper의 CUDA 강제 이동을 우회하는 CPU 추론 컨테이너를 작성할 수 있는 구조다. **소스상 경로가 확인됐다는 사실은 CPU forward 성공이나 수치 동등성 검증을 뜻하지 않는다.** 동적 실행 결과는 별도 probe 기록으로 확인해야 한다.

실제 모델 입력은 얼굴 영상 `[B,T,112,112]`와 MFCC `[B,4T,13]`이다. 공식 demo는 얼굴을 grayscale 224×224로 resize한 뒤 중앙 112×112를 잘라 모델에 넣는다. 출력은 `lossAV.FC`의 **class-1 raw logit**이며 확률·영아 발성의 소유자 판정·욕구 해석 점수가 아니다.

초기 검토는 다운로드 전의 소스 조사였다. 이후 부모 작업자의 별도 제한 실행에서 공식 Drive 파일의 tensor 구조 읽기가 성공했다. 이 후속 검사에서는 모델을 생성하거나 forward하지 않았다. 아래에서 소스 판단과 후속 실측을 구분한다.

## 고정 소스와 확보 방식

`agent-reach`의 GitHub CLI/API 경로로 공식 repo의 13개 파일을 고정 commit에서 읽었다. 각 파일은 50KB 이하, 합계 **73,372 bytes**다. GitHub 응답의 크기와 Git blob SHA를 검증하고 파일별 SHA-256을 기록했다. 코드 실행·weight 다운로드·미디어 다운로드 없이 텍스트만 확보했다.

- 로컬 근거: `work/talknet-runtime-review/sources-manifest.json`, `sources/`.
- 읽은 파일: `README.md`, `TalkSet/README.md`, `FAQ.md`, `requirement.txt`, `talkNet.py`, `loss.py`, `demoTalkNet.py`, `dataLoader.py`, `model/{talkNetModel,audioEncoder,visualEncoder,attentionLayer}.py`, `utils/tools.py`.
- 파일별 공식 URL·Git blob SHA·SHA-256·크기는 manifest에 있다. 동적 probe는 이 manifest와 복제 파일을 먼저 대조해야 한다.

공식 demo 전체를 import하거나 실행하는 경로는 피한다. `demoTalkNet.py:39–44`는 import 시점에 인자를 파싱하고 없는 checkpoint를 `gdown`으로 받으며, 이후 영상 경로 접근·외부 도구 실행·출력 폴더 삭제 경로가 있다. S3FD·scene detection·ffmpeg 및 별도 face-detector weight도 코어 추론과 다른 작업이다.

## CPU 컨테이너와 추론 호출

`talkNet.py:13–18`의 생성자는 `model`, `lossAV`, `lossA`, `lossV`를 `.cuda()`로 이동하고 optimizer/scheduler를 만든다. 순수 추론은 이 생성자를 쓰지 않고 공식 모듈을 다음 이름으로 등록하는 CPU `nn.Module` 컨테이너를 만든다.

| 컨테이너 이름 | 공식 클래스 | 필요 이유 |
|---|---|---|
| `model` | `talkNetModel` | `model.*` checkpoint prefix 유지 |
| `lossAV` | `lossAV` | Linear(256,2) head의 weight와 bias 보존 |
| `lossA` | `lossA` | 추론에 쓰지 않아도 전체 checkpoint schema 보존 |
| `lossV` | `lossV` | 추론에 쓰지 않아도 전체 checkpoint schema 보존 |

코어 4개 model 파일과 `loss.py`의 import는 torch 계열이다. MFCC·영상 전처리 의존성은 별도다. README의 Python 3.7.9/CUDA 환경과 `requirement.txt`의 광범위한 demo 의존성 전체를 CPU 코어에 반드시 설치해야 한다는 근거는 없다. 최신 torch에서의 실제 동작은 probe로 확인할 항목이다.

공식 추론 순서는 `forward_audio_frontend` → `forward_visual_frontend` → `forward_cross_attention` → `forward_audio_visual_backend` → `lossAV.forward(..., labels=None)`이다. 컨테이너를 `eval()`로 전환하고 gradient를 끈다. 두 frontend 결과는 각각 `[B,T,128]`, AV backend는 `[B*T,256]`, head 출력은 `[B*T]`의 class-1 logit이다. 최초 합성 gate는 **B=1**과 고정 T를 사용한다. 입력은 CPU float32·finite·연속 메모리, 영상 값은 정규화 전 0…255 범위로 제한하고 입력/출력 shape를 검사한다.

`attentionLayer.py:24–36`은 `[B,T,C]`를 `[T,B,C]`로 바꾼 뒤 `self_attn(tar, src, src)`를 호출하고 **src에 residual을 더한다**. 서로 같은 T가 필요하며 padding mask가 없다. 방향을 직관으로 바꿔 구현하거나 generic Transformer block으로 교체하지 않는다.

## state 키와 수식 보존

소스는 `talkNet.saveParameters()`에서 전체 `self.state_dict()`를 저장한다(`talkNet.py:78–79`). optimizer/scheduler는 이 module state에 포함되지 않는다. 등록된 parameter와 buffer를 모두 보존해야 한다.

| 계열 | 보존할 구조 |
|---|---|
| `model.visualFrontend` | Conv3d→BN3d→ReLU→MaxPool3d, 64/128/256/512 ResNet과 4×4 AvgPool. visual BN의 eps=0.001, momentum=0.01 |
| `model.visualTCN.net.{0…4}` | 5개 DSConv1d residual block. BN512, depthwise Conv512, PReLU, GlobalLayerNorm, pointwise Conv512 |
| GlobalLayerNorm | `.gamma`, `.beta` shape `[1,512,1]`. 채널과 시간 전체 평균/분산, eps=1e-8. 일반 LayerNorm으로 대체하지 않음 |
| `model.visualConv1D.net` | Conv512→256 kernel5/pad2 및 Conv256→128 kernel1. 두 Conv의 bias도 존재 |
| `model.audioEncoder` | SEBasicBlock 수 `[3,4,6,3]`, 폭 `[16,32,64,128]`, BN buffer·SE fc·downsample 포함. layer2/3의 temporal stride로 MFCC 시간을 4배 축소 |
| `model.crossA2V`, `crossV2A`, `selfAV` | MultiheadAttention의 `in_proj_*`/`out_proj.*`, FFN `linear1/2.*`, `norm1/2.*` |
| `lossAV.FC` | weight `[2,256]`, bias `[2]` |
| `lossA.FC`, `lossV.FC` | 각 weight `[2,128]`, bias `[2]` |

모든 BN의 `running_mean`, `running_var`, `num_batches_tracked`도 state다. `visualFrontend.resnet.layer1.downsample.weight`는 stride=1 forward에서 우회되지만 등록된 parameter이므로 제거하면 전체 strict load가 실패한다.

`loss.py:11–18`의 labels=None 경로는 head 적용 후 `x[:,1]`을 detached CPU numpy로 반환한다. softmax는 labels를 제공하는 다른 경로에서만 사용한다. bias 생략·softmax 추가·probability라고 표기하는 변경은 원래 추론을 바꾼다.

## 공식 demo 전처리 계약

### 얼굴 crop

`crop_video()`는 S3FD face track의 bbox에서 중심 x/y와 반쪽 최대 변 s를 계산하고 각각 kernel13 median filter로 평활화한다(`demoTalkNet.py:163–176`). 기본 cropScale은 0.40이다. 원본 주변을 값110으로 padding하고, 원본 중심 기준으로 다음 비대칭 범위를 잘라 224×224로 resize한다.

- y: `center_y-s` … `center_y+s*(1+2*cropScale)`
- x: `center_x-s*(1+cropScale)` … `center_x+s*(1+cropScale)`

추론에서는 BGR→grayscale, 224×224 resize 후 `[56:168,56:168]` 중앙 crop을 사용한다(`demoTalkNet.py:225–227`). 실제 모델 입력은 **112×112**다. 모델 내부에서 `(x/255 - 0.4161)/0.1688`을 적용한다(`talkNetModel.py:29–38`). 이 정규화를 외부에서 중복 적용하지 않는다. 224×224를 그대로 넣는 방식은 고정 AvgPool/512 reshape 계약과 맞지 않는다.

FAQ는 TalkSet의 S3FD face-crop 도메인과 다른 검출기의 crop 차이가 성능에 영향을 줄 수 있다고 설명한다(`FAQ.md:38–40`). 기존 앱의 MediaPipe crop을 동일 전처리로 간주할 근거는 없다. cropScale·bbox 평활화·padding·center crop을 기록해야 추후 비교 의미를 해석할 수 있다.

### MFCC와 시간

demo는 video를 25fps로 바꾸고 track 범위 음성을 mono PCM_s16le 16kHz로 자른다. 범위는 `frame0/25`부터 `(lastframe+1)/25`다(`demoTalkNet.py:187–191`, `395–405`). MFCC 호출은 다음과 같다(`219`행).

```python
python_speech_features.mfcc(audio, 16000, numcep=13, winlen=0.025, winstep=0.010)
```

wavfile에서 읽은 정수 PCM을 사용한다. float waveform을 -1…1로 먼저 정규화하는 변경은 에너지 계열 MFCC를 바꿀 수 있다. 다른 MFCC 인자는 package 기본값에 의존하며 `requirement.txt`는 해당 package 버전을 고정하지 않는다. 실제 비교 전에 package 버전과 filterbank/nfft/preemphasis/lifter/energy 등의 유효 기본값을 기록해야 한다. 이 검토에서 해당 package를 설치하거나 기본값을 실행 확인하지 않았다.

demo 길이는 `min((n_mfcc-n_mfcc%4)/100, n_video/25)`이고 그 길이로 음성과 영상을 각각 round 후 자른다(`233–235`행). 초기 합성 입력은 모호한 tail 없이 **정확히 4T개 MFCC와 T개 영상 frame**을 넣는다. 이후 실제 전처리에서 마지막 부분의 frame 수와 frontend T를 검사하고 맞지 않는 경우를 명시적으로 처리해야 한다. 임의 반복 frame이나 wrap padding을 조용히 넣지 않는다.

### window·후처리와 dataloader 차이

`durationSet={1,1,1,2,2,2,3,3,4,5,6}`은 **Python set**이므로 실제 고유 duration은 1…6초다. 1/2/3초를 중복 가중하지 않는다(`215`행). 각 duration별로 ceil 배치와 마지막 부분 crop을 사용하고, 같은 시점의 raw logit을 duration별로 평균한 뒤 **소수 1자리**로 round한다(`237–251`행). 원래 model logit과 이 후처리 값을 구분해 기록한다.

시각화는 주변 score의 평균과 0 threshold를 사용한다(`262–278`행). 슬라이스의 상한 `min(fidx+3,len(score)-1)`은 Python exclusive stop이므로 단순한 정상 ±2 frame 평균이라고 다시 구현해 같은 결과라고 주장하지 않는다. 시각화 threshold도 영아에서 보정된 기준이 아니다.

`dataLoader.py:40–46,56–67`의 검증 경로는 FPS에 따라 MFCC window/hop을 25/fps로 조절하고, 부족한 음성 feature를 wrap padding해 4*numFrames로 만든다. 얼굴은 grayscale을 곧바로 112×112로 resize한다. demo의 224→center112과 다른 계약이다. 최초 probe/비교에서 demo 계약을 선택했다면 dataloader의 padding·resize를 혼합하지 않는다.

## 제한 로딩 요구사항과 후속 실측

원래 `loadParameters()`는 `torch.load(path)`에 CPU map/weights_only를 지정하지 않으며, 모르는 키나 shape 불일치를 출력한 뒤 건너뛴다(`talkNet.py:81–94`). 이를 연구 harness에 재사용하지 않는다.

필요한 단계는 파일 크기·로컬 hash 고정 → ZIP 항목/총크기/압축률/중복 이름 및 static globals 검사 → patched torch에서 `weights_only=True`, `map_location='cpu'`로 제한 로딩 → tensor mapping의 전체 키·dtype·shape·finite 검사 → 컨테이너의 strict load → 제한된 합성 forward다. `TORCH_FORCE_WEIGHTS_ONLY_LOAD=1`을 보조 방어로 사용할 수 있으나 명시적 인자와 자원 제한을 대신하지 않는다. 예외를 broad allowlist나 `weights_only=False`로 우회하지 않는다. 제한 로딩도 임의 파일에 대한 완전한 sandbox가 아니므로 CPU Job 메모리·시간·출력/자손 정리 제한 아래에서 실행한다.

### 부모 작업자의 후속 checkpoint 구조 검사

다음은 source 검토 이후 `work/talknet-checkpoint-audit/`의 `download.json`, `structure.json`, `supervision.json`, `audit.log`를 읽어 확인한 별도 실측이다. 이 작성자가 파일을 다시 다운로드하거나 weight를 재로드하지 않았다.

| 항목 | 후속 결과 |
|---|---|
| 공개 출처 | 공식 demo 기본 TalkSet Drive ID `1AbN9fCf9IexMxEKXLQY2KYBlb-IhSEea`, 로그인 없이 다운로드 |
| 파일 | 63,227,827 bytes |
| 로컬 SHA-256 | `d985ddd07dca08864a28337726f5b7d91b6426bebc7327f0e9b21cf9ff1cc937` |
| upstream 검증 | 공개된 SHA-256/불변 weight revision 없음. 로컬 hash는 받은 파일 고정용이며 저자 서명·digest 확인이 아님 |
| 구조 | ZIP bounds 및 static unsafe globals 검사 통과(부모 보고), torch 2.13.0+cpu weights_only CPU mapping/finite 검사 통과 |
| tensor 수 | 487개: `model` 481, `lossAV` 2, `lossA` 2, `lossV` 2 |
| tensor bytes | 63,049,180 bytes. 파일 크기·parameter 수·resident RAM과 서로 다른 값 |
| 제한 실행 | 2GiB / 120초 Job 아래 2.618366초, exit0, termination=null, cleanup_complete=true, 남은 process0, 출력263B/truncation=false |
| peak | aggregate Job committed bytes 928,870,400. RSS·Android RAM 수치가 아님 |
| 범위 | `model_forward=false`, `private_media_read=false`; 모델 생성·source schema 대조 strict load·합성 forward는 이 검사에 포함되지 않음 |

후속 tensor metadata에서 head shape도 위 source 계약과 일치한다. 전체 487키가 새 CPU 컨테이너와 정확히 일치하는지, 함수가 실제로 finite logit을 반환하는지는 별도 gate로 남는다. FAQ의 15.01M parameter 설명은 저자 기술이며 위 checkpoint buffer 포함 tensor inventory와 같은 계산이 아니다.

### 후속 wrapper 정적 검토

이후 준비된 `tools/research/talknet_probe.py`와 `test_talknet_probe.py`를 읽기 전용으로 검토했다. 공식 복제 5파일의 SHA-256은 위 manifest와 모두 일치했고, 검토 당시 복제 폴더에는 해당 `.py` 5개만 있었다. 모델/torch를 import하거나 테스트를 실행하지 않았다. **격리된 최초 T=25 합성 feature gate를 막는 구체 결함은 정적 검토에서 발견하지 못했다.**

- 기존 `model` namespace가 있으면 거부하고 임시 alias를 `finally`에서 역순 제거한다. CUDA 훈련 wrapper를 실행하지 않고 고정 공식 클래스와 계산을 그대로 호출한다.
- checkpoint 로컬 크기/hash, 전체 487키 set, shape/dtype/finite/CPU와 `strict=True`를 검사하고 auxiliary head도 유지한다. 성공 후 eval/frozen 상태에서만 forward한다.
- B=1·112×112·audio 4T×13·float32·CPU·연속/finite·영상 0…255를 검사하고 frontend/cross/backend shape를 확인한다. 공식 FC hook의 `[T,2]`와 공식 labels=None `[T]` 결과의 class-1 동일성을 검사한다.
- 테스트 계획은 T=25 합성 feature forward 1회와 잘못된 입력 10건의 거부 확인이다. T=1…500 허용은 입력 상한 계약이며 전체 길이의 실제 자원 검증이 아니다. report는 raw 미디어/MFCC 전처리/face tracking/시간 어긋남 품질/영아 성능/원저자 torch1.6 수치 동등성/Android 및 20초 자원 검증을 false로 구분한다.

이 정적 검토는 namespace 정리의 동적 예외 시험이나 checkpoint strict load·forward 성공을 대신하지 않는다. 해당 실행은 부모 작업자의 제한 Job 아래에서 별도 확인한다.

## 연구와 제품 판단의 경계

구조와 CPU 합성 forward가 통과하면 기존 승인 범위의 로컬 연구에서 시간 동기/어긋남 비교를 준비할 수 있다. 원본 미디어 외부 전송은 하지 않는다. 합성 feature 검사는 MFCC·face detection·영상 decode의 정확성이나 영아 도메인 성능을 검증하지 않는다.

FAQ는 1초 미만·작은 얼굴·측면 얼굴에서 성능 한계를 인정한다(`49–50`행). 이 소스 검토나 checkpoint 구조 검사는 영아 옹알이 주체 정확도, 감정/욕구 번역, 기존 분류기 개선, Android/ONNX 실행, 상용 weight 재배포 권리를 입증하지 않는다. 코드 MIT와 weight/학습자료의 권리는 구분하며, 내부 로컬 연구 후보라는 상태를 제품 채택 완료로 바꾸지 않는다.

## 공식 고정 출처

- [talkNet wrapper/load](https://github.com/TaoRuijie/TalkNet-ASD/blob/6d6821479af485e251c4991487e40573b42181b4/talkNet.py)
- [model/input normalization](https://github.com/TaoRuijie/TalkNet-ASD/blob/6d6821479af485e251c4991487e40573b42181b4/model/talkNetModel.py)
- [audio encoder](https://github.com/TaoRuijie/TalkNet-ASD/blob/6d6821479af485e251c4991487e40573b42181b4/model/audioEncoder.py), [visual encoder](https://github.com/TaoRuijie/TalkNet-ASD/blob/6d6821479af485e251c4991487e40573b42181b4/model/visualEncoder.py), [attention](https://github.com/TaoRuijie/TalkNet-ASD/blob/6d6821479af485e251c4991487e40573b42181b4/model/attentionLayer.py)
- [inference head](https://github.com/TaoRuijie/TalkNet-ASD/blob/6d6821479af485e251c4991487e40573b42181b4/loss.py), [demo/crop/MFCC](https://github.com/TaoRuijie/TalkNet-ASD/blob/6d6821479af485e251c4991487e40573b42181b4/demoTalkNet.py), [dataloader](https://github.com/TaoRuijie/TalkNet-ASD/blob/6d6821479af485e251c4991487e40573b42181b4/dataLoader.py)
- [FAQ/domain limits](https://github.com/TaoRuijie/TalkNet-ASD/blob/6d6821479af485e251c4991487e40573b42181b4/FAQ.md), [requirements](https://github.com/TaoRuijie/TalkNet-ASD/blob/6d6821479af485e251c4991487e40573b42181b4/requirement.txt)
