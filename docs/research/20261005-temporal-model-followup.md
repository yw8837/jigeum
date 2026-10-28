# 아가톡 추가 모델 조사와 시간축 음성 모델 실측

2026-10-05. 사용자 요청: 앱을 더 사용하며 피드백을 모으는 동안 적합한 분석 엔진·모델을 찾는다.
제품 기준은 **0.5.14-beta/code28**이며 이번 작업은 별도 로컬 연구다.

## 결론

**새 비교 후보로 EfficientSED `fmn10+tf-256`의 advanced-KD checkpoint를 선정한다.**
실제 파일은 **20,304,913 bytes**, 전체 로드된 모델은 **5,028,286 parameters**다.
공식 소스·학습 가중치를 고정해서 실행했고, 알려진 약국 영상에서 시간에 국한된 옹알이 단서를
확인했다. 작은 fmn04/06은 같은 입력의 옹알이 단서가 약했다. 세 후보 중 다음 비교 우선순위가
분명해졌으나 **앱 승인 판정·새로운 실패 회복·일반 정확도·휴대폰 성능을 검증한 결과는 아니다.**

이 모델은 소리 종류와 발생 시간의 후보다. 영상·배고픔·졸림·아기 의도나 한국어 문장을 출력하지
않는다. 기본 입력은10초이고,20초는10초씩 나눠 전체 소리를 처리했다. 두10초 사이를 잇는
20초 단일 문맥 모델이라고 부르지 않는다. 기존 YAMNet/AST·제품 임계값·표현·APK는 변경하지 않았다.

영아 전문 모델·새 논문·사용권의 별도 결과는 [전문 모델 후속 조사](20261005-infant-specialist-followup.md)를 따른다.

## 발견 경로와 기존 조사와의 차이

- agent-reach Exa 검색 → 공식 GitHub API/고정 소스/release → arXiv API와 직접 PDF 원문.
- [EfficientSED 원문](https://arxiv.org/abs/2503.11373v2)은 EUSIPCO2025 연구다. 최신 날짜 자체를
  선정 이유로 삼지 않았다. 이전에 시험한 **EfficientAT mn04의 clip tagging**과 **시간 라벨로
  학습한 EfficientSED의 frame-wise 모델**은 가중치·구조·전처리·출력이 다르다.
- [PretrainedSED](https://github.com/fschmid56/PretrainedSED/tree/1aa47e482f7e89904cba2338999345025d8b4e36)의
  small frame_mn06/10도 공개되어 있으나 같은 연구 계열이다. 이를 독립된 새 영아 검증으로 세지 않고,
  후속 EfficientSED의 실제 checkpoint3개만 선별했다.
- [E-PANNs](https://github.com/Arshdeep-Singh-Boparai/E-PANNs)는 공식 설명상 약24M/92MB이며
  범용 분류 모델이다. 해당 repo의 공개 존재는 확인했지만 별도 가중치 실행은 하지 않았다.
  이름이 비슷한 [E2PANNs](https://github.com/StefanoGiacomelli/e2panns)는 긴급차량 사이렌 연구용
  틀로, 아기 특화 detector의 근거가 아니다.
- Jina의 arXiv HTML 응답은 그림 제목과 한 문장만 돌아와 전문으로 인정하지 않았다.
  공식 API에서v2를 확인하고 arXiv의5페이지 PDF를 직접 받아 읽었다. 논문의 처리량은
  **RTX3090, batch64, FP16**이며 Android 측정이 아니다.
- 웹 API에는 검색어와 공개 문서 주소만 보냈다. 사용자 미디어·파생 특징·점수는 로컬에만 있다.

## 소스·가중치·라벨·권리

고정 source: [theMoro/EfficientSED e3449bfd1f4922b81d82410ed842653f462bfca8](https://github.com/theMoro/EfficientSED/tree/e3449bfd1f4922b81d82410ed842653f462bfca8).
정확한 파일 SHA256·공개 asset ID·실제 bytes·실행 환경은
[pin manifest](../../tools/research/efficientsed-models.json)에 있다. GitHub release의 digest가 null이어서
파일을 실제로 내려받아 SHA256을 계산했다. weights를 저장소에 포함하지 않는다.

| 실제 실행한 공식v0.0.1 asset | bytes | 로드된 전체 params | 이 비교의 판단 |
|---|---:|---:|---|
| `fmn04_strong.pt` | 3,600,026 | 860,318 | 일부 공개 발성 단서 있음. 약국 옹알이 보완 근거 부족 |
| `fmn06_strong.pt` | 6,637,146 | 1,615,022 | 일부 공개 웃음·옹알이 단서가 있으나 약국에서는 약함 |
| `fmn10+tf-256_advanced-kd-weak-strong.pt` | 20,304,913 | 5,028,286 | 약국의 국소 옹알이 단서 확인. 후속 비교1순위, 제품 채택 보류 |

[release 원천](https://github.com/theMoro/EfficientSED/releases/tag/v0.0.1).
크기는 decimal MB이며 peak RAM·APK 증가량·양자화 크기가 아니다. 세 파일은 작은 head만이
아니라 이 실행에서 strict하게 로드한 backbone/분류 head 전체를 포함한다.

- [447 strong labels](https://github.com/theMoro/EfficientSED/blob/e3449bfd1f4922b81d82410ed842653f462bfca8/data_util/audioset_classes.py)에
  `Babbling`, `Baby cry, infant cry`, `Baby laughter`가 모두 있다. 일반 `Speech`와 성인/아동 말,
  일반 웃음/울음은 별도 라벨이다. 강한 라벨의 순서는 기존 AudioSet527 순서와 같지 않다.
- [공식 wrapper](https://github.com/theMoro/EfficientSED/blob/e3449bfd1f4922b81d82410ed842653f462bfca8/models/prediction_wrapper.py)는
  `[batch,447,250]`을 출력한다.10초당250개의40ms 위치별 sigmoid 점수다. 이 출력 격자 간격을
  실제 사건 경계 오차40ms 또는 초정밀 판별 정확도로 해석하지 않는다.
- 논문은 AudioSetStrong에서 다운로드 가능한 train100,911/eval16,935클립을 쓰며,
  train447종·겹치는 eval407종의PSDS1을 평가한다. 아기/가족 단위6~18개월 성능은 없다.
  README의45.25는 전체 소리PSDS1 평균이며 아기 인식 정확도45.25%가 아니다.
- [LICENSE](https://github.com/theMoro/EfficientSED/blob/e3449bfd1f4922b81d82410ed842653f462bfca8/LICENSE)는
  MIT(Copyright2024 Florian Schmid). 공식 README/release에서 이 세 asset에 별도NC 조건은
  찾지 못했다. **repository MIT와 학습 음원의 권리는 별개**이고, 별도 weight grant 전문은
  확보하지 못했다. 로컬 비교를 진행한 것과 최종 앱 재배포 판단을 구분한다. 학습 원음은 받지 않았다.
- 공식 Android/LiteRT/ONNX 배포본은 확인하지 못했다. 실제 PyTorch CPU 추론만 확인했다.

## 실제 실행 계약

[연구 runner](../../tools/research/efficientsed_probe.py)는 다운로드 없이 고정 로컬 source/weights만
읽는다. 원천은 `~/.agent-reach/agattok-sed-20261005`에 보관했고 모델 바이너리·개별 점수는 Git 제외다.

1. 소스별SHA·weight bytes/SHA·PCM bytes/SHA/길이/finite/[-1,1]을 검증한다.
2. `torch.load(weights_only=True)`와 **strict=True**로 전체 key/shape를 로드한다.
   원 loader의 세 key rename만 동일하게 적용했다. 부족한 학습 head를 임의 초기화하지 않았다.
3. upstream `PredictionsWrapper`의 클래스 AST를 그대로 사용한다. 불필요한 자동 다운로드와
   GPU-only Mamba import를 피하고 CPU에서 쓰는 TF branch 의존만 불러온다. constructor/forward는
   변경하지 않았다. source hash와 선택 모델을 고정하고 다른 sequence mode는 노출하지 않는다.
4.16kHz mono float32 원음 그대로,10초 비중첩창·끝부분 zero-padding. 공식 전처리:
   preemphasis[-.97,1],FFT512/window400/hop160,nonperiodic Hann,center reflection,
   Kaldi128mel,fmin0/**fmax7000**,power→clip1e-7→natural log→(x+4.5)/5.
   모든 모델·mel은eval. PCM에 별도denoising/gain/80Hz필터를 추가하지 않는다.
5. 마지막 padded 구간의 출력은 원음 길이로 잘라서 집계한다. 원본SHA를 끝에 다시 확인한다.
   모든 출력shape/finite와 synthetic silence의 반복동일성을 확인한다. median smoothing 없음.
6. Python socket연결을 막은 worker, Windows Job Object **180초·4GiB aggregate committed 한도**.
   이 설정은 적대적 native코드의 완전한 network sandbox라고 주장하지 않는다.
7. CPUtorch2.11.0/torchaudio2.11.0/torchvision0.26.0,4threads.
   TF실행은einops0.8.2/rotary-embedding-torch0.9.1. 공식 requirements는후자 버전을 고정하지 않아
   사용한 버전을 별도로 고정했다. pinning은 과거 학습 때의 의존버전까지 복원했다는 뜻이 아니다.

## 실제 결과와 한계

입력은 **기존 공개·합성33조건 + 알려진 약국의10/20초2조건**이다. 두 약국 조건은 같은
원본이며, 공개 묶음도 파생·월령 밖·화자 미확인 자료를 포함한다.35개의 독립 아기나
새로운 held-out 평가셋이 아니다. 같은 점수표에서 임계값을 낮추거나 학습하지 않았다.

- fmn04/06: run01 정상종료,35조건씩 실행. 합계worker **5.138초**, 추론구간합계
  각각 **0.834/0.990초**. peak Job committed **1,103,388,672bytes**.
- fmn10+TF: run02 정상종료,35조건 실행. worker **4.926초**, 추론구간합계 **1.649초**.
  peak Job committed **1,129,054,208bytes**.한도도달/타임아웃 없음,잔여자손0.
- TF후보에서는 알려진 약국의 짧은 시간구간에 옹알이 단서가 나왔다. 이 사례는 이미 현재AST가
  보완한 개발자료이므로 **기존앱보다 새 실패를 하나 더 해결했다는 뜻이 아니다.**
- 동일후보에서도 일부 공개옹알이는 약하고,웃음·혼합발성에서는 클래스별 강도가 달랐다.
  관찰용0.5 점수구간을 기록했지만 이는 교정된 제품승인 기준이 아니다. 모델별 점수크기와
  현재AST의 승인률을 직접 비교하지 않는다.
- 성인대화·성인웃음·음악·무음·기계음 대조에서는 영아라벨이 약했다. 이 제한된 묶음으로
  일반오인율0 또는TV/모든겹침 안전성을 주장하지 않는다.
- PC시간은 35조건의 준비된PCM 추론이며, 영상decode·전처리앱통합·얼굴관찰·화면·모델cold
  load 전체시간이 아니다. Android 지연·배터리·발열·중저가폰 검증으로 옮기지 않는다.

증거: ignored `work/efficientsed-20261005/run01/` 및 `run02/`의
`process.json`, `scores-private.json`, `inference-private.log`.
run01은seq후보를 추가하기 전 runner로두모델을실행했고,run02는현재 `--model`선택형runner로실행했다.
35조건의 입력은 기존 corpus이며, 원본 보존·loader/출력 검사를 통과했다.

## 다음 비교에서 결정할 것

1. **작은 보완 detector 후보는20.3MB TF모델로 좁힌다.** 알려진 샘플만으로 즉시 탑재하지 않는다.
   새 실패 사례와 독립 발성·성인/TV/겹침 대조에서 현재YAMNet+AST에 없는 이득이 있는지 평가한다.
2. 다음 구현 단계는 이 정확한 checkpoint의 **모바일 export parity/INT8 전후 보존**이다.
   가중치 양자화만으로 옹알이 인식이 개선된다고 예측하지 않는다. 현재 float 결과가 비교 기준이다.
3. 10초 경계 근처 발성·20초 앞뒤 순서·짧은 발성·조용한 웃음을 포함하고, 기존 창 경계 보완과
   AST 회복의 회귀 검증을 보존한다. 40ms 출력 수나 동일 원본의 padding 파생을 독립 표본 수로 세지 않는다.
4. 실제 S25와 중급 ARM에서 cold/warm 지연, RAM, 취소, 연속 분석 발열을 측정한 뒤 제품 후보로 올린다.
   S25가 미연결이면 그 제한을 기록한다. 연결 요청을 반복하지 않는다.
5. 영아 전문 교사 모델을 통한 증류·추가 학습은 먼저 모델·영아 데이터 사용권과 아이/가족 분리 평가를 확보한다.
   권리가 불명확하거나 NC인 모델을 증류하면 괜찮다는 가정은 하지 않는다.

이번 산출물은 추가 조사·3개 후보의 실제 CPU 스크리닝·재현 runner다. Android 제품 수정·새 업데이트
배포는 없다. 사용자가 더 사용하며 보내는 실패 사례와 연결해 후속 비교를 할 수 있도록 근거를 남겼다.
