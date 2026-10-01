# 아가톡 Android0.5.0 / code13

앱 전체 이름은 **아가톡 - 지금 내 기분이 그래**, 앱서랍은 **아가톡**, package는 `com.moa.babylanguage`. minSDK26, target/compileSDK36. **실사용 평가 후보이며 유료 품질 승인 전**입니다.

[협업 시작](../../docs/HANDOFF.md) · [흐름/문장 계약](../../docs/UX-0.5.md) · [QA](../../docs/qa/0.5.0/README.md) · [이용 정책](../Jigeum-Access-Policy.md) · [실행](../../docs/RUNBOOK.md)

## 실제 흐름

최대1분 영상+소리 또는 소리 녹음 → 재생 →1–15초 선택 → 같은 구간의 실제 소리 모델 → 한마디/근거 → 수정·저장. 저장 영상은1분·100MB 이하. 월령·직접 번역·상황 선택·후보 다시 뽑기는 없습니다.

촬영/가져오기/재생/소리/기록/PNG/JSON/CSV는 무료. 같은 구간의 얼굴·입 열림 관찰과 PDF는 실제 서명 이용권이 필요합니다. 결과·기존 기록은 만료돼도 무료로 유지합니다. 가격은 실제 Play 응답만 표시하며 상품/실제 거래 검증은 아직 남았습니다.

## 구현

- `CaptureFlow.kt` / `LocalCapture.kt`: CameraX Recorder, MediaRecorder, MediaPlayer, 임시파일·구간·백그라운드 처리.
- `SoundAnalyzer.kt` / `ClipEvidence.kt`: YAMNet 정확한 라벨 매칭과 미보정 보류 규칙. 울음/옹알이/웃음 후보를 상상한 아기말로 표현합니다. 배고픔/졸림 모델이 아닙니다.
- `VideoAudioDecoder.kt` / `VideoImport.kt`: 선택한 시간대 PCM / 최대5장면 Face Landmarker 얼굴·입 열림.
- `ObservationStore.kt` / `RecordTransfer.kt`: 기존 SQLite 기록/백업 호환 유지. 월령은 이전 백업 호환 필드만 보존.
- `AccessPolicy.kt` / 결제 모듈: 실행 전/중/후 현재 권한 확인. 테스트 대역은 androidTest에만 둡니다.

원본 미디어·아기 기록을 서버로 보내지 않습니다. 원본은 비공개 cache/captures에서 임시 처리하고 화면 종료/재촬영/다음 앱 실행 때 정리합니다. 원본은 FileProvider 공유·기록·백업에 포함하지 않습니다. 외부 네트워크는 결제/심사 확인의 기존 무상태 서버 경로입니다.

모델·SDK 고지는 `app/src/main/assets/NOTICE.txt`, `MODEL_NOTICE.txt`, `LICENSE-APACHE-2.0.txt`를 따릅니다. voc2vec·librosa는 연구 자료이지 탑재 엔진이 아닙니다. 실제 사건/의도/보호자 만족 정확도는 측정하지 않았고 공개 웃음 샘플이 보류되는 문제도 [기록](../../docs/research/20261001-public-audio-probes.md)했습니다.

## 빌드

JDK17+와 SDK36. `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintRelease --max-workers=1`.

업로드 서명/공개 검증 URL을 넣은 재현은 [RUNBOOK](../../docs/RUNBOOK.md)과 [RELEASE](RELEASE.md). 키·토큰·APK/AAB·캐시는 Git 제외. 실제 설치 파일은 저장소 루트 `outputs/Agattok-0.5.0-code13.apk`입니다.
