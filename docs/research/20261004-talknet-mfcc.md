# TalkNet용 python_speech_features 0.6 소스·MFCC 계약

확인일: 2026-10-04 (KST). 범위는 공개 PyPI 배포물/GitHub 원문 확보, AST·수식·설치 metadata 검토와 ignored 로컬 vendor 준비다. 기존 venv의 설치/변경/downgrade, 모델·torch 실행, NumPy/SciPy import, MFCC 계산, 원본 미디어 접근은 하지 않았다.

## 준비된 파일과 출처

PyPI `python_speech_features==0.6` sdist는 **5,588 bytes**이며 PyPI가 공개한 SHA-256 `a0aebf746464bc929dc3162cb369d7ff967c398c5120ddf5fb40a65f01b92b11`과 받은 bytes가 일치했다. 2017-08-16 업로드 배포물이다. tar의 경로·타입·파일 수·개별/총크기를 검사하고 package를 실행하지 않은 채 11개 파일, 총21,715 bytes를 읽었다. `setup.py`도 실행하지 않았다.

로컬 사용 후보는 `work/talknet-mfcc/vendor/`다. package의 순수 Python **3파일은 PyPI 0.6 원문 그대로**, LICENSE는 아래 고정 GitHub tag 커밋 원문이다.

| vendor 파일 | bytes | SHA-256 |
|---|---:|---|
| `python_speech_features/__init__.py` | 20 | `1119a63b1cfff6652422e71c35bcd46b963a8152cb543772738702c5b08251b6` |
| `python_speech_features/base.py` | 10,904 | `5da9dd3a98ebf697d5ac0dec1cc79e605f6df4874d1aa327096acf3002aaabd1` |
| `python_speech_features/sigproc.py` | 5,371 | `f4769a1c7b01c1d42d11549e2c5db4a84159e755d61a2f72c59afdbaad0b1352` |
| `LICENSE` | 1,078 | `fa2897135b74c090e0f2a32052f917157148787303683c8d8e96060bcbb2b40e` |

근거는 ignored `work/talknet-mfcc/{pypi-metadata,distribution-manifest,vendor-manifest,static-review}.json`이다. 배포물과 source도 같은 디렉터리에 보존했다. 모든 `.py`는 AST parse를 통과했으며 package code는 실행하지 않았다.

### PyPI 배포물과 GitHub tag는 동일하지 않음

공식 repo의 `v0.6`와 `v0.6.0`는 조사 시 commit `40c590269b57c64a8c1f1ddaaff2162008d1850c`를 가리켰다. 이 커밋의 `base.py`/`sigproc.py`는 PyPI 0.6 배포물과 다르다. Git blob SHA·크기 검증 후 차이를 `tag-difference-base.py.diff`, `tag-difference-sigproc.py.diff`에 보존했다.

- GitHub tag의 `mfcc`는 nfft=None 및 `calculate_nfft`를 추가한 반면 **PyPI 0.6의 기본 nfft는512**다.
- GitHub tag의 `framesig`에는 rolling-window stride 경로/`stride_trick` 인자가 있다. PyPI 0.6은 명시적 index 배열로 framing한다.
- `__init__.py`만 byte 단위로 같다. 코드 3개를 같은 tag의 bytes라고 표기하지 않는다.

TalkNet의 requirements는 package 버전을 고정하지 않으므로 저자의 실행 환경이 정확히 PyPI 0.6이었는지는 확인되지 않았다. 이번 준비는 **재현 가능한 PyPI 0.6 전처리 선택**이며 원저자 전처리와 수치 동등성의 증거가 아니다. 16kHz/25ms에서는 GitHub 동적 nfft도512가 되는 수식이지만, 두 배포 형태의 전체 수치 동등성을 실행 확인하지 않았다.

## 라이선스

PyPI metadata·sdist의 PKG-INFO·setup.py는 MIT를 표시한다. sdist에는 LICENSE 원문이 들어 있지 않아 공식 repo의 고정 커밋에서 **Copyright (c) 2013 James Lyons / MIT** 원문을 확보해 vendor 루트에 함께 보관했다. MIT의 저작권·허가 문구를 복사본에 포함하는 조건을 유지한다. 이 전처리 코드의 라이선스는 TalkNet weight/학습자료 권리를 대신하지 않는다.

## PyPI 0.6의 MFCC 기본값과 계산

TalkNet demo의 호출 `mfcc(audio,16000,numcep=13,winlen=.025,winstep=.010)`에 적용되는 나머지 기본값은 다음과 같다(`base.py:9–11`).

| 인자 | 값 |
|---|---|
| sample rate / window / hop | 16,000Hz / 25ms / 10ms |
| numcep / nfilt / nfft | 13 / 26 / 512 |
| lowfreq / highfreq | 0Hz / None→samplerate/2=8,000Hz |
| preemph | 0.97 |
| ceplifter | 22 |
| appendEnergy | True |
| winfunc | `numpy.ones((x,))`: rectangular window |

Hamming window·mel spectrogram 라이브러리 기본값·CMVN·delta feature를 대신 추가하지 않는다. 수식은 다음 순서다.

1. preemphasis: 첫 sample은 유지하고 나머지는 `x[n] - .97*x[n-1]` (`sigproc.py:111–118`). 빈 입력은 `signal[0]`에서 실패하므로 상위 harness가 거부해야 한다.
2. window400 samples/hop160 samples로 framing하고 마지막 부족분은 **0 padding**한다. 기본 window는 모두1이다.
3. `numpy.fft.rfft(...,512)`의 magnitude를 제곱해512로 나눈 one-sided power spectrum을 사용한다(`sigproc.py:71–95`). 400-sample frame의 FFT 뒤쪽은 zero padding된다.
4. power bins의 합을 frame energy로 사용하고,26개 triangular mel filterbank의 energy를 구한다. 0인 energy/filter 값은 `numpy.finfo(float).eps`로 치환한다(`base.py:54–62`).
5. 자연로그 → `scipy.fftpack.dct(type=2,axis=1,norm='ortho')` → 처음13개 coefficient → sinusoidal lifter22를 적용한다. 마지막으로 coefficient0을 **log frame energy**로 바꾼다(`base.py:27–33`).

공식 demo는 wavfile의 **정수 PCM_s16le amplitude**를 MFCC에 넘긴다. -1…1로 먼저 정규화하면 energy와 로그 값이 바뀔 수 있다. 기본 preemphasis·float zero padding의 소스상 연산은 float 계산을 사용하며 최종 demo `torch.FloatTensor`에서 float32로 바뀐다. 원문 dtype·finite 결과는 실행 확인할 항목이다. 무음도 epsilon/log/DCT를 거치므로 zero MFCC라고 간주하지 않는다.

## framing과 25fps 정렬

`sigproc.py:13–38`은 frame_len/frame_step을 Decimal ROUND_HALF_UP으로 정수화한다. 16kHz·25ms/10ms의 결과는 정확히400/160 samples다. N>400의 frame 수는 `1 + ceil((N-400)/160)`이고, N≤400이면1개다. 마지막 padding 길이는 `(frames-1)*160+400`이다. 각 feature frame의 시작은 j*10ms, window 중심은 시작+12.5ms다. demo는 이 feature들을 묶어25fps 영상과 맞추며 별도의 중심 시각 보정을 하지 않는다.

**10ms hop은 “정확히1초 음성에 MFCC100개”를 뜻하지 않는다.** 아래 값은 package 실행이 아닌 원문 수식의 stdlib 산술 계산이다. 충분한25fps 영상이 있고 원문 demo의 multiple-of4 trim을 적용하는 경우다.

| 원음 길이 | raw MFCC 수 | demo MFCC trim | 대응 영상 frame 수 |
|---|---:|---:|---:|
| 1초 /16,000 samples | 99 | 96 | 24 |
| 2초 /32,000 samples | 199 | 196 | 49 |
| 20초 /320,000 samples | 1,999 | 1,996 | 499 |

따라서 앞선 T=25/audio100 합성 feature gate는 물리적1초 PCM의 전처리 결과를 검증하지 않는다. 실제 demo 계약은 `min((n_mfcc-n_mfcc%4)/100,n_video/25)`로 길이를 정하고 두 feature를 자른다. 마지막 구간의 실제 MFCC·영상 frame 수/시각·trim을 보고해야 하며 부족한 feature를 dataloader의 wrap padding으로 조용히 바꾸지 않는다. 이 프레이밍이 끝부분의 일부 영상을 제외하는 사실도 비교 구간에 반영해야 한다.

## 설치 없이 사용할 환경 후보

`importlib.metadata`와 top-level `find_spec` 및 SciPy 파일 내용만 읽었다. NumPy/SciPy/torch/package를 import하지 않았다.

| 기존 interpreter | 정적 확인 |
|---|---|
| `work/ast-portability/venv/Scripts/python.exe` | Python3.13.14, NumPy2.4.6, SciPy1.18.0 metadata/spec 존재. SciPy fftpack에서 dct export 및 구현 소스 존재. `python_speech_features`는 미설치 |
| `work/ondevice-av/runtime/Scripts/python.exe` | Python3.13.14, NumPy/SciPy/python_speech_features metadata/spec 없음 |

NumPy/SciPy가 발견된 AST venv에서 **격리된 subprocess의 import 경로에 vendor 루트를 앞에 추가**하면 설치 없이 package를 선택할 수 있는 구조다. 실제 사용 전에는 vendor3파일 hash와 package origin을 확인하고 기존 `python_speech_features` module alias 충돌을 거부한다. `.pth`/site-packages 또는 venv를 바꾸지 않는다.

이 상태는 의존성 metadata·경로·API 소스 확인이다. DLL 로딩/import 성공, 합성 PCM MFCC의 finite/shape, 현대 NumPy/SciPy와의 수치 결과는 아직 검증하지 않았다. 후속 실행은 부모 작업자의 제한 Job 아래에서 진행한다. 원본 접근·모델 실행·제품 품질/권리 판단은 이 조사 결과에 포함되지 않는다.

## 공개 출처

- [PyPI 0.6 metadata](https://pypi.org/pypi/python_speech_features/0.6/json), [PyPI 배포 페이지](https://pypi.org/project/python_speech_features/0.6/)
- [검증한 PyPI sdist](https://files.pythonhosted.org/packages/ff/d1/94c59e20a2631985fbd2124c45177abaa9e0a4eee8ba8a305aa26fc02a8e/python_speech_features-0.6.tar.gz)
- [공식 GitHub 고정 tag commit](https://github.com/jameslyons/python_speech_features/tree/40c590269b57c64a8c1f1ddaaff2162008d1850c), [MIT LICENSE](https://github.com/jameslyons/python_speech_features/blob/40c590269b57c64a8c1f1ddaaff2162008d1850c/LICENSE)
- [TalkNet demo 고정 입력 호출](https://github.com/TaoRuijie/TalkNet-ASD/blob/6d6821479af485e251c4991487e40573b42181b4/demoTalkNet.py#L219)

## 후속: 부모 작업자의 제한 MFCC 합성 실행

위 초기 조사를 마친 뒤 부모 작업자가 동일 vendor를 기존 AST venv에서 제한 실행했다. 이 절은 `work/talknet-mfcc/{smoke.py,smoke-report.json,smoke-supervision.json,smoke-blas2-supervision.json,smoke-supervised.log,smoke-blas2-supervised.log,supervise.py}`를 읽어 확인한 **별도 후속 실측**이다. 이 문서 작성자는 테스트를 재실행하지 않았다.

처음1GiB Job은 OpenBLAS의 반복 메모리 할당 오류로 종료했다. 실패 로그를 보존했고 supervision은 exit1, termination=`memory_limit`, 0.623223초, cleanup_complete=true, 남은 process0으로 기록했다. raw peak Job committed counter는1,080,238,080 bytes이며, 이 counter는 거절된 할당을 포함할 수 있어 허용된 RSS 수치로 해석하지 않는다.

부모는 실행 subprocess의 **OPENBLAS_NUM_THREADS / OMP_NUM_THREADS / MKL_NUM_THREADS를 각각2**로 제한했다. 같은1GiB·60초 Job에서 후속 실행은 **1.097051초, exit0, termination=null, cleanup_complete=true, 남은 process0**으로 통과했다. peak Job committed는172,572,672 bytes, memory_limit_hit=false, 출력880 bytes/truncation=false다. package 설치나 vendor 코드 변경은 없었다.

smoke는 실행 전에 vendor4파일의 크기/hash와 package origin을 확인하고, int16 합성 무음과440Hz tone을 생성했다. 실제 NumPy2.4.6 / SciPy1.18.0에서8조건 모두 MFCC shape·finite·float64 검사를 통과했다.

| 합성 PCM 길이 | 무음 shape | tone shape | multiple-of4 후 대응 영상 frame |
|---|---|---|---:|
| 1초 | `[99,13]` | `[99,13]` | 24 |
| 2초 | `[199,13]` | `[199,13]` | 49 |
| 10초 | `[999,13]` | `[999,13]` | 249 |
| 20초 | `[1999,13]` | `[1999,13]` | 499 |

무음의 feature가 모두0이 아니며 coefficient0이 `log(numpy.finfo(float).eps)`와 atol1e-12로 일치하는 검사도 통과했다. 따라서 앞 절의 정적 프레이밍 산술은 이8개 합성 조건에서 동적 결과와 일치한다. 이 후속 결과로 해당 환경의 vendor import/MFCC 합성 계산은 확인됐다. 원저자 환경 수치 parity·실제 음성 전처리 정확성·TalkNet 모델 결과·영아 품질·Android 검증은 포함하지 않는다. `private_media_read=false`, `installed=false`, `author_environment_parity=false`를 유지한다.
