# Android 음성 평가 도구: 20초 입력 및 실패 처리 검증

2026-10-04 · 제품 main/모델/임계값 변경 없음 · Play 업로드 없음

## 발견과 수정

기존 `sound_probe.py`는 PCM을 1~15초로 제한했고, `PublicAudioProbeTest`는 실제 PCM이 더 길어도
해석에 전달하는 `ClipRange` 끝을 15초로 제한했다. **평가 도구의 결함이며 제품 앱의 20초 분석이
15초에서 잘린다는 증거는 아니다.** 이번 수정은 이후 후보 비교에서 전체 구간과 실행 실패를 제대로 기록하기 위한 것이다.

- host/device 모두 mono 16kHz float32 little-endian, 1~20초/64,000~1,280,000 bytes를 검증한다.
  sample 정렬·유한성·기존 제품과 같은 `abs <= 1.001f`·SHA-256을 검사한다.
- 실제 sample 수로 구간을 만들며 부분 millisecond는 올림한다. 수신 bytes/hash/samples/range를 결과에 기록하고 host가 대조한다.
- 32자리 run ID를 manifest·계측 인자·결과와 연결한다. host와 device는 run별 하위 폴더를 사용한다.
  기존 결과 재사용은 거부하며, 계측 실패 뒤 결과 파일을 성공 측정으로 읽지 않는다.
- host timeout은 이 도구가 사용하는 target/test 패키지를 중지하고 PID 부재를 확인한다. 정리 실패도 보고서에 남긴다.
- Astra/high 읽기검토에서 `MODEL_FAILURE`가 일반 보류로 집계될 수 있는 P2를 발견했다.
  실제 분석 직후 이를 실패로 전환하며, 정상 무음 보류와 구분하는 회귀를 추가했다. 재검토에서 지적 해소를 확인했다.

## 실제 검증 결과

| 검사 | 결과 | 범위 |
|---|---|---|
| host 전체 QA | 46/46, exit0 | 신규 8개 포함; 외부 ADB 경계는 대역 |
| debug/app + androidTest 빌드·서명 | 성공 | 제품 모델 변경 없이 기존 upload key로 에뮬레이터 업데이트 |
| Android 입력 계약 | 5/5, skip0 | 20초 끝 sample, 20초 초과·잘린 sample·NaN/Inf·진폭·hash, 부분 ms, 모델 실패 구분 |
| 실제 20초 PCM 분석 | 320,000 samples / range end 20,000ms / 마지막 frame start 19,500ms | 19초 무음 뒤 1초 500Hz 합성 tone; host/device hash 일치; 정상 보류 |
| 실패 입력 실제 계측 | 4종 거부 | 이전 run 재사용, 잘못된 hash, 15초로 위조한 duration, 다른 run ID |
| 이전 결과 보존 | byte 일치 | 거부된 재사용 실행이 기존 결과를 덮어쓰지 않음 |
| 환경 복원 | 통과 | beta24 APK hash 복원, 원래 없던 test 패키지 제거, 생성한 5개 run 디렉터리만 제거 |
| 기존 데이터 보존 | files/databases/shared_prefs 44개 SHA 전후 완전 일치 | 추가 영구 파일 없음; 기존 QA 데이터도 보존 |
| 종료 | 정상 종료 응답 및 이후 ADB/에뮬레이터 프로세스 부재 | `MoaQA35` 전용 에뮬레이터 |

실패 입력 4종은 실패를 기대한 검사다. 정상 검사의 실패를 숨기거나 46+5를 제품 전체 회귀 수치로 합산하지 않는다.
모델 실패 guard 추가 전 4개 입력 계약/20초 실제 경로도 통과했고, 추가 후 5개 계약/20초 경로를 새로 실행했다.
따라서 전후 실행을 서로 독립 품질 표본으로 세지 않는다. host timeout의 실제 장치 강제 지연은 만들지 않았으며 대역 회귀 범위다.

첫 에뮬레이터 실행은 `ANDROID_AVD_HOME` 누락으로 옛 사용자 경로를 읽다 종료됐다.
RUNBOOK의 `C:/MoaAVD`를 지정해 정상 실행했다. AVD wipe나 기존 앱 데이터 삭제는 하지 않았다.
사용자 S25는 이번 실행에서 ADB에 표시되지 않았고 이 QA에 사용하지 않았다.
이전에 완료한 S25 연구 앱/모델 삭제는 [별도 실측 문서](S25-GEMMA-20261004.md)의 증거다.

## 재현과 증거

```powershell
python -m unittest discover -s outputs/QA -p 'test_*.py' -v
# outputs/MoaAndroid 에서:
./gradlew.bat --no-daemon --max-workers=1 assembleDebug assembleDebugAndroidTest
# 전용 QA 기기에 현재 APK와 동일 서명을 설치하고 ANDROID_HOME/ANDROID_SERIAL/JIGEUM_QA_EVIDENCE 명시:
python outputs/QA/sound_probe.py --corpus <manifest.json과-f32가-있는-로컬폴더> --install-app
```

기존 `sound_corpus.py`의 baseline fixture 생성 길이는 유지한다. 20초 입력은 별도 manifest/f32로 준비할 수 있다.
manifest는 `id`, `pcm_sha256`, `expected`, 비어 있지 않은 `allowed`를 포함한다. 실험 대상/라벨을 실행 전에 고정한다.
이름·라벨 일치는 독립 정확도 검증을 대신하지 않는다. `--strict`는 선언된 라벨 불일치에도 실패하도록 하는 옵션이다.
현재 설치된 앱과 다른 서명의 APK를 억지로 설치하기 위해 기존 앱을 제거하지 않는다.

Git 제외 로컬 증거: `work/public-audio-20s-qa/`의 `host-tests.log`, `build.log`,
`final-contract-instrumentation.txt`, `final-verification.json`, `run-summary.json`,
`sound-probe-*/`, `negative-*.txt`, `baseline.json`, `cleanup.json`.
최종 app SHA256 `d1b1de5fc6485e4c02f6b263204bc564188f994af4c07bbd49e9f1cd25a8d772`,
test SHA256 `294196d06830be07f9ab967f54ac7ba8829fb2b0f2bf784698ca0449c998388e`.

**이번 통과는 평가 도구의 구간·증거·실패 처리 검증이다.** 일반 옹알이 회복, AV 화자 연결,
감정·욕구 추측, 새 한국어 아기말 생성, Gemma 제품 성능/품질, 새 베타 배포는 미완료다.
독립 사건/화자 평가의 [최소 계약](../../research/20261004-av-evaluation-contract.md)과
[공개 자료 조사](../../research/20261004-labeled-av-data.md)를 다음 판단에 사용한다.
Rollins/Providence의 corpus 설명은 확인했지만 등록이 필요한 transcript/media나 정답셋을 확보한 상태는 아니다.
