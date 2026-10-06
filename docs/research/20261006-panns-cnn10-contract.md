# PANNs Cnn10 — 공식 계약·로컬 실행 결과

2026-10-06. 현재 앱에 탑재하지 않았다. 사용자 원음·특징은 외부로 전송하지 않았다.

## 고정 원천

- [공식 source](https://github.com/qiuqiangkong/audioset_tagging_cnn/tree/d2f4b8c18eab44737fcc0de1248ae21eb43f6aa4),
  pin `d2f4b8c18eab44737fcc0de1248ae21eb43f6aa4`, 코드 MIT.
- [저자 Zenodo3987831](https://zenodo.org/records/3987831), Qiuqiang Kong et al., PANNs,
  checkpoint CC BY4.0. 상업 사용 가능·저자/출처/변경 고지 조건과 원 AudioSet 음원 권리를 구분한다.
- 실제 `Cnn10_mAP=0.380.pth`: **25,237,595B**, MD5 `bfb1f1f9968938fa8ef4012b8471f5f6`,
  SHA256 `5240bdc47444e331bbeb54fd741d2b3f933a4526e84b0c17bf3593df94b13962`.
- 527라벨 sigmoid clipwise head와512dim embedding. 주파수평균→시간max+mean pooling이며
  native event 시간 head가 아니다. Babbling6/아기웃음17/아기울음23/어린말소리3을 실제 CSV와 검사한다.
- 공식 입력32kHz mono, Hann1024/hop320/center reflect/mel64/50–14000Hz,
  dB ref1/amin1e-10/top_dbNone. 16kHz 보존원음을 torchaudio sinc로32kHz로 바꾸며
  사라진 고주파를 복원한 것으로 주장하지 않는다. 고정10초 chunk·오른쪽zero-padding을 적용한다.

## 실제 준비·호환성 검증

공식 source의 Cnn10/ConvBlock/초기화 본문을 그대로 추출한다. 안전한 `weights_only=True`로
checkpoint의60개 key·각shape·유한성·512→527 head·frozen frontend를 검증하고 strict 전체 로드했다.
전체 parameter6,302,735다. 무작위 head를 만들거나 불일치 키를 버리지 않았다.

공유환경 변경 없이 연구 EfficientAT torch2.11/torchaudio2.11과 별도 dependencies의
NumPy2.4.6/Librosa0.6.3/torchlibrosa0.0.4를 사용했다. 설치된 과학패키지는 읽기 전용 fallback이다.
처음에는 NumPy2.5.2와 Numba0.66이 충돌해 분리된NumPy wheel로 해결했다.
NumPy에서 제거된complex/float/int와numba.decorators.jit는 **자식프로세스 내부**의 동일타입/함수 alias로
복원했다. 실제2119개 로드 모듈의 경로·파일 SHA manifest를 만들고 그 digest를 inference 전 고정했다.
이 혼합 연구 runtime을 일반 Android 런타임이나 완성된 portable environment라고 보고하지 않는다.

세 frozen STFT real/imag/mel계수는 source 생성값과 **byte identical**이고 로드 후 checkpoint와 동일하다.
무음·440Hz tone·고정noise의 finite/shape/repeat를 비교했다. 최초의 모든 log-bin .01dB gate는
순수tone의약-95dB floor근처에서float32 conv/doubleFFT의오차를 확대해 .023464dB로 실패했다.
모델 추론 전에 원인을 진단하고 frontend gate를 다음 수치계약으로 고정했다:
byte-identical계수, STFT power relativeL2≤1e-5, reference>-60dB signal의maxdB차이≤.01,
모든bin 오차를 삭제하지 않고 보고. 이는 recognition threshold 변경이 아니다.
tone/noise relativeL2는각9.03e-7/9.39e-7, signal-binmax차이는.000787/.0000158dB였다.
무음0이다. 36조건 입력무결성까지 검증했고 validation에서는 classifierforward0이었다.

runtime manifest의 최초 helper가PyTorch의가상`torch.classes/ops.__file__`을 실제파일로 읽다 실패한
setup오류도 남겼다. 해당 두 등록namespace만 실제`torch._classes/_ops`의경로/SHA로 추적하도록
고쳤고, 일반 누락file을 건너뛰는 경로는 추가하지 않았다.

## 실제 retained 비교와 결정

기존 보존3구간/2원본에 처음 inference를 실행했다. 두 누락 구간은 한 원본에서 서로 겹치며
새holdout·독립청취정답이 아니다. 모델/전처리/창/원음/점수를 사후 변경하지 않았다.
두 누락 구간에서 옹알이 단서가 약했고, 둘 모두 관찰용.5 이상의10초chunk0개였다.
별도 울음 대조도.5chunk0이었다. 따라서 **교체나 새 회복 근거가 없어 채택하지 않는다.**
.5는 기술용관찰값이며 앱승인기준·교정확률이 아니다. 모델끼리 raw score크기를 같은확률척도로
비교하지 않는다. 실패문구·욕구번역·일반인식률 개선도 입증하지 않았다.
이번선별에서 추가공개33 추론/점수완화는 하지 않는다. 무결성검증36건을36개의새품질정답으로 세지 않는다.

workerexit0,12.017초, aggregate committedpeak1,915,912,192B,180초/4GiB상한,
cleanuptrue/남은자손0. PC초기화·frontend참조·manifest감사를포함한시간이다. 휴대폰속도/발열이 아니다.
3구간inference합계는약.779초다. 입력바이트/SHA/형식/실제길이/finite를확인하고추론후해시도보존했다.
음원·개별 점수와 module manifest는ignored work에만 둔다.

## 재현·후속

`tools/research/panns_probe.py --assets work/panns-cnn10-20261006`
`--corpus work/sed-followup-20261006/retained-corpus --output <새 ignored run폴더>`.
별도 고정runtime으로 실행하고, 완료된`retained-run01`은 다시 덮어쓰거나 반복하지 않는다.
source/dependencies/weight는 이 연구 전용이며 결정/검토 후 정리대상이다.
공용Python/SDK/Gradle캐시·제품assets·원본·기존f32는 변경하지 않는다.

비공개 증거: `work/panns-cnn10-20261006/validate-final02/validation-private.json`,
`retained-run01/scores-private.json`, `process.json` 및 초기setup/diagnostic 로그.
pin: `tools/research/panns-model.json`.
다음은 다른 학습representation·영아taskhead의 실제 사용권/weights 계약을 조사한다.
이 실패를 threshold완화나 같은3구간추론의반복으로 해결했다고 만들지 않는다.
