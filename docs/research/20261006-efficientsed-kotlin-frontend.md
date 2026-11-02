# EfficientSED 공식 전처리의 Kotlin 이식·호스트 수치 비교

2026-10-06 · Task 2 · 합성 입력만 사용 · 제품 엔진 미변경

**순수 Kotlin 전처리를 구현하고 공식 PyTorch mel 및 20.3MB ONNX 출력과 실제 대조했다.**
7개 합성 입력의 mel 7개와 출력 28개가 사전에 고정한 허용값을 통과했다.
이는 고정 10초 frontend의 호스트 이식 근거다. 아래 추가 Android 이식 검사도 통과했으며,
ARM 실폰 성능·새 영아 실패 회복·일반 정확도·제품 채택 근거로 확대하지 않는다.

## 구현 계약

- [frontend](../../tools/research/efficientsed_frontend.kt)는 Android·ORT·파일 IO에 의존하지 않는다.
  API는 `research.efficientsed.EfficientSedFrontend.extract(pcm, cancelled)`이며
  **16kHz mono, 정확히 160000개 finite float PCM, [-1,1] 범위**를 받는다. 결과는 mel-major
  float 128000개로, ONNX 입력 **[1,1,128,1000]**이다.
- 공식 `fmn_wrapper.py` constructor를 AST로 읽고, pin에 든 소스 전체의 SHA를 확인한 뒤
  공식 `AugmentMelSTFT` 클래스를 `.float().eval()`로 실행했다. 원본 constructor의
  `fmax=None, fmax_aug_range=2000`에 따라 실제 eval 상한은 **7000Hz**다.
- preemphasis는 cross correlation `x[n+1] - .97*x[n]`의 valid convolution이다.
  160000개 입력에서 159999개가 된다. DC 제거·32768 배율·AST 프레임 padding은 없다.
- FFT512/win400/hop160. nonperiodic Hann400을 FFT512의 중앙(offset56)에 놓는다.
  `center=True`의 reflection은 경계 샘플을 반복하지 않으며 preemphasis **이후** 신호를 반사한다.
  이 길이에서 정확히 1000프레임이 나온다. power→Kaldi128mel(0–7000Hz,Nyquist0)→
  clamp1e-7→natural log→`(x+4.5)/5`를 적용한다.
- [상수](../../tools/research/efficientsed_tables.kt)는 공식 torch/torchaudio2.11.0+cpu에서 생성한
  Hann400·희소 Kaldi 필터의 float32 값이다. 각 실행에서 다시 생성해 저장된 Kotlin 코드와
  문자 단위로 일치 확인한다. Android에서 Torch를 로드하거나 필터를 다운로드하지 않는다.
- FFT는 Double 연산을 쓰되 window 곱/FFT 출력/power/log/norm의 float 경계를 보존한다.
  float 연산 구현 차이는 아래 mel·전체 출력 비교로 검사했다. 원본 Torch STFT 비트 일치를
  주장하지 않는다.
- 잘못된 길이,NaN,Infinity,범위 밖 finite PCM을 거절하고, 시작과 프레임마다 취소 callback을 확인한다.
  취소하면 `IllegalStateException`이다. 호출자가 coroutine 취소를 전달하려면
  `extract(pcm) { ensureActive(); false }`처럼 연결할 수 있다.

짧은 PCM이나 20초 입력을 이 API가 자동 변형하지 않는다. 이후 비교 호출자는 짧은 PCM의
끝을 0으로 채워 정확히160000개로 만들고, 긴 PCM은160000개씩 나눠 마지막 PCM 창만
0으로 채울 수 있다. 이번7조건은 모두 정확히10초이며, 이 padding/두창 stitching 정책의
인식 품질·40ms 출력격자 경계를 검증하지 않았다. 제품에 적용할 때 별도 계약과 검사가 필요하다.

## 실제 비교 결과

실행 전에 mel 절대오차≤**2e-4**, 모든 logit의 `atol=1e-4,rtol=1e-3`를 고정했다.
각 입력에서 두 경로를 비교했다: Kotlin mel→ORT 대 공식 mel→동일ORT,
Kotlin mel→ORT 대 공식 mel→원본PyTorch. 후자는 frontend와 export 오차를 함께 포함한다.
각 경로의 strong[1,447,250]/weak[1,447] shape와 finite도 검사했다.

| 합성 입력10초 | mel 최대 절대오차 | mel 평균 절대오차 | strong 최대 절대 logit 오차¹ | weak 최대 절대 logit 오차¹ |
|---|---:|---:|---:|---:|
| 무음 | 0 | 0 | 2.2888184e-5 | 1.1920929e-6 |
| .03/440Hz tone | 1.1444092e-5 | 1.7999595e-7 | 2.5749207e-5 | 8.3446503e-7 |
| seed17/σ.005 Gaussian noise | 1.7166138e-5 | 1.8259780e-7 | 1.8119812e-5 | 1.0728836e-6 |
| 시작·끝·창경계 impulse | 7.6293945e-6 | 7.4755369e-10 | 2.1934509e-5 | 1.3113022e-6 |
| amplitude.01 DC | 4.7683716e-7 | 1.1408702e-8 | 2.2888184e-5 | 1.4901161e-6 |
| amplitude1e-5/997Hz quiet | 2.3841858e-7 | 3.9115548e-11 | 2.0980835e-5 | 1.1920929e-6 |
| 123/997/6800Hz multitone | 1.7166138e-5 | 3.7791401e-7 | 3.0517578e-5 | 1.3113022e-6 |

¹ 둘 중 더 큰 비교 오차. strong은 raw logits이며 sigmoid 이후 제품 점수가 아니다.
Impulse는 PCM index0,1,199,200,159798,159998,159999에 서로 다른 진폭을 놓았다.
quiet는 clamp 근처,DC는 AST식 평균 제거 혼입,6800Hz 성분은 상한 근처의 회귀를 다룬다.
한 입력을 여러 문장으로 바꾼 인식률 시험은 아니다.

최종 run03는 **10.704061초**, Windows Job peak committed **1,389,703,168bytes**였다.
180초·4GiB 한도 미도달, exit0,cleanup완료,잔여자손0. compiler·JVM·두runtime 비교를
모두 합친 PC 실행이다. 새 JVM별 frontend63.0–97.6ms는 PC 수치이며 Android 성능이 아니다.
메모리 값도 Android RSS가 아닌 Windows kernel Job committed accounting이다.

## 고정 파일·재현

기존 export [근거](20261005-efficientsed-mobile-export.md)의 model을 그대로 썼다.
ONNX20,315,630bytes/SHA256 `0a1bf37323cb9069d26a63b0ca4486c9c027b29e3bff087e0acd7270022716fc`.
source revision `e3449bfd1f4922b81d82410ed842653f462bfca8`, weights SHA
`7d5326a485b742b323b33dd2bcb366606b614b2a5deaa5e452ef34ddedb9797c`를 유지했다.

frontend SHA256 `884b822134d045a65cd16c92b0c4829a84424cf0a6a2b39e4144d5f8e121b9b8`,
tables SHA256 `8ebc305aa7133d9d2cd87d38d72a03b0d7be322df966e09bac94062ebc592c6b`.
호스트는 torch2.11.0+cpu/ORT1.24.3/NumPy2.5.2/Kotlin2.2.10/JDK21을 사용했다.
컴파일러·런타임은 기존 설치본이며 새 다운로드나 Gradle 호출은 없다.

저장소 root에서 실행:

```powershell
& 'C:/Users/최용우/AppData/Local/Temp/agattok-efficientat-20261005/venv/Scripts/python.exe' tools/research/efficientsed_frontend_parity.py --assets 'C:/Users/최용우/.agent-reach/agattok-sed-20261005' --onnx work/efficientsed-export-20261005/run03/mel.onnx --output work/efficientsed-frontend-20261006/run04
& 'C:/Users/최용우/AppData/Local/Temp/agattok-efficientat-20261005/venv/Scripts/python.exe' -m unittest discover -s tools/research -p test_efficientsed_frontend_parity.py -v
```

출력 경로는 저장소ignored `work/` 아래 새 폴더만 허용한다. runner는 Python socket을
차단하고 compiler·JVM 자손도 Windows Job에 포함한다. 신뢰된 로컬 연구 코드의 자원 경계이며
native network sandbox를 주장하지 않는다. 외부·사용자 미디어 입력 인자는 제공하지 않는다.

최종 run03의 `result.json/process.json/parity.log` 및7조건×PCM/공식mel/Kotlinmel/ORT·Torch출력을
보존했다. PCM파일은640000bytes,mel파일은512000bytes,
strong파일447000bytes,weak파일1788bytes이며 전부ignored work에 있다.
ONNX나 원본·파생 미디어를 tracked source 또는 제품 assets에 넣지 않았다.

[실패 gate 검사](../../tools/research/test_efficientsed_frontend_parity.py)는6개 테스트에서
누락/중복case·누락/중복output·shape오류·falseflag·NaN/Infinity/음수 수치·허용값 위조를
거절한다. 부모는worker exit/cleanup과 저장된28개 수치행을 다시 검사한다.
logit별 `(abs(error)/(atol+rtol*abs(reference)))` 최대값≤1도 별도로 검사하므로
`passed=true`만 위조해서 허용값을 넘긴 실행을 성공으로 표시할 수 없다.

첫 호출은 모델 경로를 primary 저장소로 잡아 `FileNotFoundError`로 실행 전에 종료했다.
기존 파일은 이 worktree의`work/efficientsed-export-20261005/run03/mel.onnx`에 있었으며,
올바른 경로로 run01을 수행했다. run02는 parser gate 보완과 Android 기준출력 보존 후
재실행한 것으로 같은 frontend/table SHA와 같은 최대오차를 확인했다.
메인의 입력 범위 검토를 반영해 run03은 normalized PCM [-1,1] 밖의 입력을 fail-fast 거절한다.
추가 계약 검사에서 ±1.01/Float.MAX_VALUE를 거절하고 ±1 경계 impulse는 finite 결과를 냈다.
수학적 frontend 연산은 동일하고 새 frontend SHA로 전체7조건을 재실행했다.

## Android 인계 경로

메인이전용연구 계측을 할 때 `efficientsed_frontend.kt`와 `efficientsed_tables.kt` 두 파일만
androidTest 영역에 임시 복사할 수 있다. `efficientsed_frontend_probe.kt`는 host IO adapter이므로
Android에 복사할 필요가 없다. 모델은 opt-in context.filesDir에서 읽고 기존 연구ORT1.24.3 CPU
session에 FloatBuffer와 shape[1,1,128,1000]으로 공급한다. `OnnxTensor`·결과·session은 close한다.
7개 case의run03 PCM/official-mel/official-torch-{0,1}.f32를 같은 순서로 읽어 같은 허용값을
검사할 수 있다. 모델과 모든기준파일 SHA·오프라인 경로도 확인한다.

호스트 구현 작업에는 Gradle/ADB 실행이 없었다. 메인이 아래 후속 Android 검사를 수행했다.
제품 sourceSets/AST/YAMNet/권한/임계값은 변경하지 않았다.

## 추가 — 실제 Android 이식 검사

전용 `AgattokNavigation30`, API35/x86_64, 기존 ORT Android1.24.3 CPU에서 실행했다.
[연구 계측](../../tools/research/EfficientSedAndroidProbeTest.kt)을 frontend·상수와 함께
androidTest에만 임시 복사했고 고정20,315,630bytes ONNX는 앱 private files로 전달했다.
정식/beta제품 sourceSets에는 연결하지 않았으며 계측 후 임시소스와 격리앱·복사모델은 제거했다.

- 합성7개 PCM의 Android mel 전체128000값이 공식 mel과 **절대오차≤2e-4** 통과.
  실제 최대오차1.7166138e-5. 같은 원본 공식mel→hostORT와 Android의 strong/weak 모든값도
  `atol1e-4,rtol1e-3` 통과했다. strong최대1.4305115e-5,weak최대6.556511e-7.
- Android native session의 **미리 종료한 RunOptions**가 실패하고 새 RunOptions로 정상복구했다.
  frontend취소도 실패로 반환했다. 실제 UI에서 실행중인 작업 취소·timeout/연속발열 검증을 대체하지 않는다.
- model load98.3ms,frontend27.6–96.2ms,inference42.1–71.8ms. **PC기반에뮬레이터 수치**다.
  PSS snapshot최대265843KB/닫은후214696KB이며 peak·실폰 RAM을 측정한 값이 아니다.
- 계측1개 안에서7개입력×mel/strong/weak를검사,실패·제외0. 알려진 아기 음원은 이 수치검사에 사용하지 않았다.

증거는 ignored `work/result-navigation-20261006/sed-android-result.json`,
`sed-android-process.json`, `sed-android-instrumentation.txt`, `sed-fixtures/`다.
fixture는 run01의공식mel·PCM과고정hostORT로생성했으며 최종run03과동일7조건이다.
Android에복사한 frontendSHA884b8221…/상수SHA8ebc305a…는 최종run03 코드와같다.

**남은채택조건:** 실제ARM기기 성능/발열, 새영아원본 누락회복과성인·TV·겹침오인 대조,
10/20초창/시간격자·447라벨/sigmoid·padding계약검증. 아직새인식률을입증하지않아제품모델교체는보류한다.
