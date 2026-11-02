# GTCRN 352KB ONNX 로컬 비교 실행 계약

확인일 **2026-10-05**. 공식 `Xiaobin-Rong/gtcrn` source commit **`502ebfab64da7c4a9af78dcb9c6ceef1ebb01c73`**의 코드·Git tree를 읽었다. source와 API metadata만 TEMP에 저장했다. ONNX/가중치 다운로드·설치·추론·학습·앱/기기 변경은 수행하지 않았다. 이 문서만 작성했다.

## 1. 실제 사용할 파일과 권리

| 항목 | 고정 값·근거 |
|---|---|
| 원 ONNX | [stream/onnx_models/gtcrn.onnx](https://github.com/Xiaobin-Rong/gtcrn/blob/502ebfab64da7c4a9af78dcb9c6ceef1ebb01c73/stream/onnx_models/gtcrn.onnx) |
| 다운로드 URL | [고정 raw URL](https://raw.githubusercontent.com/Xiaobin-Rong/gtcrn/502ebfab64da7c4a9af78dcb9c6ceef1ebb01c73/stream/onnx_models/gtcrn.onnx) |
| size | **352,084 bytes** |
| Git blob SHA | **`613bd97557556a296f578af0a0993da191bbf8d4`**. 이전 감사와 동일. **파일 SHA256이 아니므로** 부모가 다운로드 후 별도 SHA256 계산·고정. |
| 혼동할 다른 파일 | `gtcrn_simple.onnx`는 **535,190 bytes**,Git blob `015454eef64bd4328e6ff4946c231d4a4a632e93`. 원 ONNX와 다른 artifact. |
| 출처·과제 | [README](https://github.com/Xiaobin-Rong/gtcrn/blob/502ebfab64da7c4a9af78dcb9c6ceef1ebb01c73/README.md): DNS3/VCTK-DEMAND speech enhancement. stream export source는 DNS3 checkpoint를 사용. 실제 ONNX 내부 provenance는 이번 미검사. |
| 라이선스 | [LICENSE](https://github.com/Xiaobin-Rong/gtcrn/blob/502ebfab64da7c4a9af78dcb9c6ceef1ebb01c73/LICENSE): **MIT,Copyright(c)2024 Rong Xiaobin**,사용·수정·배포·판매 허용과 copyright/license notice 보존. 읽은 원천에 weight별 별도 NC 문구는 없지만 모델별 별도 허락 문구도 미확보. 원 학습자료 권리와 software MIT는 구분. |

크기/blob 근거는 [고정 tree API](https://api.github.com/repos/Xiaobin-Rong/gtcrn/git/trees/502ebfab64da7c4a9af78dcb9c6ceef1ebb01c73?recursive=1)다. 48.2K parameters/33MMACs/s 및 i5 CPU RTF0.07은 README 보고이며,우리 소리·PC·Android 성능으로 옮기지 않는다.

## 2. ONNX tensor·cache 계약

공식 [StreamGTCRN.forward](https://github.com/Xiaobin-Rong/gtcrn/blob/502ebfab64da7c4a9af78dcb9c6ceef1ebb01c73/stream/gtcrn_stream.py#L641),[export names](https://github.com/Xiaobin-Rong/gtcrn/blob/502ebfab64da7c4a9af78dcb9c6ceef1ebb01c73/stream/gtcrn_stream.py#L819),[ORT loop](https://github.com/Xiaobin-Rong/gtcrn/blob/502ebfab64da7c4a9af78dcb9c6ceef1ebb01c73/stream/gtcrn_stream.py#L861)를 직접 읽었다. 아래는 **source의 export 계약**이며 부모가 실제 session metadata로 일치 여부를 검사해야 한다.

| 입력 name | float32 shape | 출력 name·처리 |
|---|---|---|
| `mix` | **`[1,257,1,2]`** = batch,FFT frequency,time1,real/imag | `enh` 같은 shape의 **enhanced complex spectrum**. mask나 waveform이 아니므로 추가로 mask를 원 spectrum에 다시 곱하지 않는다. |
| `conv_cache` | **`[2,1,16,16,33]`** | `conv_cache_out`,다음 frame 입력으로 교체 |
| `tra_cache` | **`[2,3,1,1,16]`** | `tra_cache_out`,다음 frame 입력으로 교체 |
| `inter_cache` | **`[2,1,33,16]`** | `inter_cache_out`,다음 frame 입력으로 교체 |

각 **새 독립 clip에서 cache를 모두 zeros**로 초기화한다. 동일 clip의 인접 frame 사이에는 cache를 유지한다. source는 매 frame `session.run` 후 반환 cache를 다시 넣고 `enh`를 time axis2로 이어 붙인다. 부모 runner는 output names를 명시하거나 `get_outputs()` 순서를 확인해 연결한다. frame마다 초기화하거나 다른 clip의 cache를 재사용하면 공식 계약과 달라진다.

## 3. waveform·STFT·overlap-add

근거: [공식 infer.py](https://github.com/Xiaobin-Rong/gtcrn/blob/502ebfab64da7c4a9af78dcb9c6ceef1ebb01c73/infer.py)는 **fs==16000**을 assert한다. [stream source](https://github.com/Xiaobin-Rong/gtcrn/blob/502ebfab64da7c4a9af78dcb9c6ceef1ebb01c73/stream/gtcrn_stream.py#L737)도 동일 분석 설정을 사용한다.

- 입력은 **16kHz mono float waveform**. source `sf.read(...dtype='float32')`; 자동 resample/mono 처리는 없음. 부모가 원 sample rate를 확인·실제 resample하고 stereo 정책을 기록한다.
- **FFT512,win512(32ms),hop256(16ms),50% overlap**,window는 `sqrt(torch.hann_window(512))`,즉 **periodic Hann의 제곱근**. 원 torch 호출의 기본은 center=True,reflect padding,normalized=False,one-sided257 bins다.
- ONNX 입력은 각 분석 frame의 **linear complex STFT real/imag**다. mel/log/dB/추가 preemphasis 입력이 아니다. magnitude/ERB·mask 처리는 model 내부에 있다.
- 호환 torch에서는 `return_complex=True` → `torch.view_as_real(...)`로 마지막 real/imag axis를 만들 수 있다. 전체 shape `[1,257,T,2]`에서 `[...,i:i+1,:]`를 넣고 동일 길이의 모든 frame을 처리한다.
- 출력 spectrum을 complex로 되돌리고 **같은 sqrt-periodic-Hann,FFT512,hop256,win512**로 ISTFT한다. overlap-add는 synthesis window를 적용하고 **겹친 window²의 합으로 정규화**해야 한다. `torch.istft`가 이 정규화를 수행한다. 부모 비교에서는 `length=원16kHz샘플수`를 명시해 길이와 원점 정렬을 보존하고 finite/clipping을 확인한다.
- 위 center=True clip 분석은 **전체 clip STFT → frame별 ONNX** 비교 경로다. 이것만으로 실제 실시간의 lookahead·host buffer·flush 지연을 검증한 것은 아니다. 실제 streaming host에서는512-sample 분석 buffer를256씩 이동하고 마지막 tail/중심 padding·출력 offset을 별도 계약으로 맞춰야 한다.

## 4. 선호 공식 참고 경로와 그대로 실행하면 생기는 문제

**352KB 이미 만들어진 ONNX의 비교에는 `stream/gtcrn_stream.py`의 ORT loop만 참조한 session-only runner가 가장 직접적이다.** root `infer.py`는 `.tar` PyTorch checkpoint용이며 이번 ONNX 선택과 다르다. full stream script는 checkpoint load·변환·ONNX export·simplify·비교까지 한꺼번에 수행하므로 부모의 ONNX-only 실험에 그대로 필요하지 않다.

- full script 기본 session은 **`gtcrn_simple.onnx`**이며352KB raw model session은 주석이다. 부모는 raw 고정 경로를 명시해야 한다.
- source의 분석은 `torch.hann_window(512)`(**periodic=True**)이나 마지막 librosa ISTFT는 `np.hanning(512)`(**symmetric**)를 사용해 window가 정확히 같지 않다. 부모 계약은 위처럼 분석·복원 window를 동일하게 고정하고 이 변경을 기록한다. 그대로 복사한 code가 완전 numerical reference라고 주장하지 않는다.
- source는 `onnx_model`을 raw file이 없을 때만 정의한 뒤 simple file이 없으면 이 변수를 사용한다. raw만 받은 상태로 full script를 돌리면 이 분기도 깨질 수 있다. 새 simplify/export가 불필요한 session-only 경로는 이 문제를 피한다.
- 최소 ONNX-only 실행 의존은 **onnxruntime,numpy**,WAV입출력 **soundfile**,분석/복원을 위 공식 torch 방식으로 맞출 경우 **torch**다. core ORT inference에 `.tar`,onnxsim,ptflops,einops,학습code가 필요하지 않다. [원 requirements](https://github.com/Xiaobin-Rong/gtcrn/blob/502ebfab64da7c4a9af78dcb9c6ceef1ebb01c73/requirements.txt)는 torch1.11 등 개발 기준이며 ORT를 포함하지 않으므로 전체 파일 설치를 실행 준비와 동일시하지 않는다.

## 5. 부모 비교에서 남길 증거

실제 bytes/SHA256·ORT tensor names/types/shapes·ORT provider/version,cache reset 정책,16kHz변환·동일window/length,clip별 처리시간/RTF·RMS/peak·출력유한성·길이/시간정렬을 기록한다. **동일 원음/처리음의 AST/YAMNet 시간별 발성 유지와 누락·오검출**을 비교하며 denoiser가 줄인 에너지 자체를 개선 점수로 세지 않는다. 성인 말/음악과 함께 아기 소리도 사라지면 채택 실패다. speech PESQ/STOI/DNSMOS는 영아 babble/laugh 보존 증거가 아니다.

미검증: 실제 ONNX download hash/graph 검사·session 성공·우리10/20초 처리·아기 발성 보존·Android 성능. 이번 산출물은 실행 계약이며 후속 실행의 성과를 선행 주장하지 않는다.
