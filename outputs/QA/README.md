# 0.5 QA 진입점

최신 [0.5.1 심층 QA](../../docs/qa/deep-audit-0.5/README.md). `instrument.py --install-app`은 현재 전체 계측, `capture_ui.py`는 새 촬영/가져오기/보류/유료 왕복 UI와 큰 글씨 확인, `check_package.py`는 서명/16KB/백업 검사다. 아래 `run_qa.py normal/camera/video/access/layout` 등0.4 선택기는 과거 화면용으로 새 앱에서 성공한다고 가정하지 않는다. 기존 run_qa.py는 역사적 재현용으로 유지한다.

`range-silence-tone.mp4`는12초 합성 fixture(앞6초무음/뒤6초997Hz순음)다. 실제 아기 분류 성공 자료가 아니다. 새 UI스크립트는 명시한 QA기기의 표시 설정을 임시 바꿨다가 복원한다. 전용 에뮬레이터에서만 실행한다.

`under-second.mp4`와 `over-minute.mp4`도 순수 합성 검은 화면이다. 각각0.5초/61초 입력 거절을 확인하며 androidTest에만 포함된다. 생성: `ffmpeg -f lavfi -i color=c=black:s=32x32:r=10:d=0.5 -an -c:v libx264 -preset ultrafast -movflags +faststart under-second.mp4`; 긴 fixture는 `r=1:d=61`로 생성한다. 아기 원본이나 외부 사진을 사용하지 않았다.

`PublicAudioProbeTest`는 기본 전체 실행에서 조건skip한다. 권리를 확인한 로컬 PCM자료를 명시적으로 준비한 뒤 `-e public_audio_probe true -e class com.moa.babylanguage.PublicAudioProbeTest`로 별도 실행한다. 없는 사건은 미실행으로 보고하며 측정 도구의 성공을 분류 성공으로 해석하지 않는다. [출처/절차/한계](../../docs/research/20261001-public-audio-probes.md).

# Android 통합 QA 재현 도구

0.3.0부터 사용한 계측·UI·파일 전송·패키지 점검 스크립트에 0.4.0 무료/전체 이용권 검증을 추가한 소스입니다. **이 도구를 추가했다는 사실은 테스트 통과를 의미하지 않습니다.** 실행 결과는 해당 실행의 `evidence`와 최종 QA 보고서에서 확인합니다. 실제 Play 구매 시험이나 출시·제출·서비스 배포를 수행하는 도구가 아닙니다.

## 준비와 기기 소유권

- Python 3.10 이상, Android SDK/API 36와 build-tools 36.0.0, JDK 17 이상(현재 프로젝트 작업 환경은 21), ffmpeg가 필요합니다. Python 외부 패키지는 사용하지 않습니다.
- 디버그 앱과 instrumentation APK를 먼저 빌드합니다. 패키지 검사는 선택적으로 release AAB도 읽습니다. 스크립트가 Gradle을 실행하거나 파일을 배포하지 않습니다.
- **전용 시험 에뮬레이터/기기를 한 작업자만 사용합니다.** UI 스크립트·instrumentation·다른 에이전트의 ADB 조작을 동시에 실행하지 마세요. 다른 사람이 사용하는 기기나 실제 아기 기록이 있는 설치에는 실행하지 않습니다.
- 모든 ADB 작업에는 `ANDROID_SERIAL` 또는 `JIGEUM_QA_SERIAL`이 반드시 필요합니다. 기기가 하나뿐이어도 명시해야 하며 모든 명령에 `adb -s`를 붙입니다.
- 실제 기록 대신 합성 데이터만 사용합니다. UI 시험은 기록 저장/가져오기, 권한 변경, 앱 종료/재시작, 공유 화면, 글꼴·화면 크기 변경을 수행합니다. `layout`은 원래 표시 크기/밀도 override와 글꼴값으로 복원합니다. 강제 종료되면 수동 복구가 필요할 수 있습니다.
- UI 선택기는 **영어 DocumentsUI**의 `Downloads`, `SAVE`, `Show roots`, `List view`를 사용하고 앱은 한국어 문구를 사용합니다. AOSP/Google APIs API 35 시험 이미지가 기준입니다. 다른 OS 버전·언어·OEM 선택기는 조정이 필요합니다.
- 카메라 모드는 전후면 에뮬레이터 카메라가 모두 필요합니다. 마이크 시험에 오디오 입력이 없는 경우 무음/수동 대체 흐름을 구분해서 기록합니다. x86_64 에뮬레이터 통과가 ARM 실물 기기의 발열·카메라·소음 검증을 대신하지 않습니다.

저장 위치는 기본 `outputs/QA/evidence/`와 `outputs/QA/fixtures/`이며 둘 다 Git에서 제외합니다. `JIGEUM_QA_EVIDENCE`로 현재 실행의 증거 폴더를 별도로 지정할 수 있습니다. 증거에는 화면·로컬 DB·내보낸 기록이 포함될 수 있으므로 실제 개인정보로 실행하지 마세요.

## 환경 예시 — PowerShell, 저장소 루트에서

아래 경로와 시리얼은 설치 환경에 맞게 지정합니다. 업로드 서명 키나 검증 서비스 비밀키는 필요하지 않습니다.

```powershell
$env:ANDROID_HOME = 'C:\Android\Sdk'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:ANDROID_SERIAL = 'emulator-5554'
$env:PYTHONIOENCODING = 'utf-8'
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"
```

SDK가 PATH에만 있다면 ADB 시나리오는 동작할 수 있지만 패키지 검사에는 `ANDROID_HOME` 또는 `ANDROID_SDK_ROOT`가 필요합니다. `JIGEUM_QA_ADB`로 adb 실행 파일, `JIGEUM_QA_BUILD_TOOLS`로 build-tools 버전을 지정할 수 있습니다. ffmpeg도 PATH에서 검색합니다.

### 별도 빌드

```powershell
Push-Location outputs/MoaAndroid
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:bundleRelease :app:lintRelease --console=plain
Pop-Location
```

이 명령은 직접 실행하는 빌드 단계입니다. QA 스크립트가 빌드를 자동 수행하지 않습니다. 업로드 설정 없는 AAB는 unsigned이며 현재 작업 범위는 출시/제출 이전입니다.

## 권장 순서

### 1. 계측 테스트

```powershell
python outputs/QA/instrument.py --install-app `
  --allow-skip 'com.moa.babylanguage.PublicAudioProbeTest#reportPublicSoundEventProbes' `
  --allow-skip 'com.moa.babylanguage.PurchaseVerifierTest#unconfiguredManagerSurvivesRefreshRestoreAndRepeatedClose' `
  --allow-skip 'com.moa.babylanguage.PurchaseVerifierTest#missingConfigurationCannotUnlockPremium'
```

위3개 허용목록은 **실제 검증URL·공개키가 설정된0.5 debug 빌드**의 예상skip입니다. 공개 음성 측정용 원본이 없고, 미설정 전용2개검사는 이 빌드에서 실행하지 않기 때문입니다. 다른 빌드는 그 실행에서 의도한skip만 정확한 `CLASS#METHOD`로 명시하세요. 미예상skip, 모든 테스트skip, 중복/미완료 결과, 비정상runner종료는 실패입니다. skip을 실행통과에 합산하지 않습니다.

현재 debug APK와 테스트 APK를 `install -r`로 설치하고 AndroidJUnitRunner를 실행합니다. 기존 앱 데이터는 유지하므로 전용 합성 설치를 사용합니다. `--install-app`을 생략해도 **로컬 intended app/test APK가 둘 다 필요하며**, 실제 설치된 각 패키지의 base APK SHA256이 로컬 파일과 일치해야 실행합니다. 오래된 앱·누락패키지·읽을 수 없는 해시·split설치는 계측 시작 전에 실패합니다. 이 검사는 로컬에서 빌드한 단일 debug APK만 지원하며 Play split설치용이 아닙니다.

`instrumentation-result.json`은 app/test APK 경로·SHA256·크기, git SHA·dirty상태, 선택한 ADB기기, 설치된 app/test base APK해시, target versionCode/versionName을 함께 기록합니다. source의 dirty상태도 보존하므로 commit SHA만 보고 완전히 같은 소스 빌드라고 추정하지 않습니다. 설치 추적성 실패는 `instrumentation_started:false`와 실패 사유를 남깁니다.

기본 대기 한도는600초이며 `--timeout`으로 변경합니다. `instrumentation.log`와 결과 JSON에 실제 테스트 수·시간·결과를 씁니다. 서버측 검증기의 독립 테스트는 별도 터미널에서 `outputs/PlayVerifier`의 `npm test`로 실행하며 Android UI와 공유 자원을 점유하지 않습니다. 호스트 하네스만 검증하려면 `python -m unittest discover -s outputs/QA -p test_qa_gates.py -v`를 실행합니다. 이 unittest는 ADB·Gradle·네트워크를 실행하지 않습니다.

### 2. 합성 영상·JSON 파일 준비

```powershell
python outputs/QA/fixtures.py --push
```

Android 테스트 assets의 합성 영상 3개를 사용하고 ffmpeg로 31초의 160×120, 5fps 단색 영상을 만듭니다. 크기 초과용 파일은 유효한 MP4 앞부분을 51MiB로 패딩하며 손상 영상/JSON과 미래 시각 JSON도 생성합니다. 모두 로컬 ignored 폴더에 만들고 지정한 시험 기기의 Downloads로 복사합니다. 큰 실패 fixture를 Git에 커밋하지 않습니다.

`--push` 없이 실행하면 로컬 파일만 생성합니다. `--push`는 이전의 **정확한 소유 파일명** `jigeum-qa-backup.json`을 먼저 지워 저장 선택기의 덮어쓰기 대화상자가 시험을 방해하지 않게 합니다. 다른 다운로드 파일을 지우지 않습니다.

### 3. UI 시나리오를 한 개씩 실행

```powershell
python outputs/QA/run_qa.py normal
python outputs/QA/run_qa.py permission
python outputs/QA/run_qa.py access
python outputs/QA/run_qa.py paywall-layout
python outputs/QA/run_qa.py album
python outputs/QA/run_qa.py transfer
python outputs/QA/transfer_e2e.py
python outputs/QA/run_qa.py layout
python outputs/QA/run_qa.py stress
```

앞 명령이 종료된 후 다음 명령을 실행합니다. `normal`은 직접 고친 문장의 프로세스 재생성, 연속 저장 탭, SQLite 저장과 무료 공유를 확인합니다. `album`은 데이터가 있는 상태에서 실행하며 **Play 설정이 비어 있는 현재 QA 빌드**의 구매 불가/복원 안내와 표지·메모 선택 유지에 맞춰져 있습니다. 실제 가격이 나오는 설정된 빌드에는 그대로 사용하지 않습니다.

`access`는 무료 상태의 카메라·영상 선택이 센서 권한 요청/파일 선택보다 먼저 이용권 화면을 여는지, 이용권 화면의 프로세스 복원과 안전한 뒤로가기가 되는지, 그 후에도 무료 문장을 저장할 수 있는지 검사합니다. `paywall-layout`은 작은 화면·200% 글꼴·넓은 화면에서 설명과 구매 복원에 접근하고 원래 화면으로 돌아가는지 확인합니다. 결과 이미지를 직접 검토해야 시각 품질을 판단할 수 있습니다.

기존 `camera`, `video`, `invalid` 모드는 카메라/영상의 실제 센서·파일 선택기용입니다. **0.4에서는 유효한 실제 이용권이 있는 운영 연결 시험 설치에서만** 사용할 수 있으며 현재 설정 없는 APK에는 적용하지 않습니다. 잠금 화면을 만난 것을 미디어 엔진 실패로 집계하지 마세요. 로컬 계측의 테스트 대역은 무료/유료 화면 전환과 만료를 검증하며 실제 Play 거래나 OEM 센서 시험을 대신하지 않습니다.

`transfer`는 파일 선택 취소 복귀를, `transfer_e2e`는 저장 선택기에서 앱 프로세스가 실제로 죽은 뒤 백업 완료, JSON 재가져오기 중복 방지, 손상 파일 거부, 미래 시각 import 이후 현재 시각 저장을 확인합니다. DocumentsUI 저장 위치는 **기기의 Downloads**여야 합니다. 이전 실행의 동일 이름 백업이 있으면 `fixtures.py --push`로 소유 fixture를 다시 준비한 후 실행합니다.

`layout`은 200% 글꼴, 720×1280/320dpi 작은 화면, 1280×720/160dpi 넓은 화면을 확인하고 스크린샷을 저장합니다. 자동 문구/버튼 검증만으로 시각적 품질을 판정하지 않으므로 실제 스크린샷도 검토합니다. `confirm-layout`은 넓은 화면 확인에만 사용합니다. 화면 정책의 원래 override는 정상 종료와 예외 경로에서 복원합니다.

`results.jsonl`은 실행마다 누적됩니다. 이전 실행의 pass와 이번 실패를 섞어 최종 통과로 계산하지 마세요. 새 실행은 새 `JIGEUM_QA_EVIDENCE` 폴더를 지정하면 구분하기 쉽습니다.

### 4. 선택적인 0.2.1 → 0.3.0 이전 시험

기존 설치와 같은 디버그 서명 키로 빌드된 알려진 0.2.1 APK가 있어야 합니다. 이 저장소 도구는 baseline APK를 내려받거나 제공하지 않습니다.

1. 전용 시험 기기에 baseline APK를 설치하고 앱에서 합성 기록을 한 건 이상 저장합니다.
2. `python outputs/QA/capture_legacy.py`로 옛 SharedPreferences를 현재 증거 폴더에 캡처합니다. 읽기만 수행합니다.
3. 새 debug APK를 **데이터 삭제 없이** `adb -s <serial> install -r <current-debug.apk>`로 설치합니다. 서명이 다르면 기기를 초기화해서 억지로 이전 시험으로 계산하지 않습니다.
4. 앱을 한 번 열어 이전이 완료되게 하고 `python outputs/QA/run_qa.py migration`을 실행합니다.

이 모드는 캡처 파일이 없으면 명시적으로 실패합니다. 임의의 새 빈 설치를 migration 통과로 기록하지 않습니다.

### 5. 패키지 검사 — ADB 없이 실행

```powershell
python outputs/QA/check_package.py --expect-unsigned-aab
```

백업/기기 이전 제외 도메인, 컴파일된 manifest의 INTERNET와 백업 비활성화, 공유 경로, APK 서명, ZIP 16KiB alignment, APK/AAB native ELF PT_LOAD alignment를 확인합니다. 기본 입력은 `outputs/MoaAndroid/app/build/outputs` 산출물입니다. 다른 파일은 `--apk`/`--aab`로 지정합니다. `--skip-aab`는 AAB를 검사하지 않은 것으로 기록합니다.

알려진 로컬 baseline APK와 비교하려면 `--baseline <파일>`을 추가합니다. 생략하면 모델/native 비교를 **skipped**로 기록하며 동일하다고 주장하지 않습니다. `--expect-unsigned-aab`는 이번 unsigned 구성에만 사용합니다. ELFs의 최소 페이지 정렬 검사는 실제 16KiB ARM 기기 부팅·기능 시험을 대신하지 않습니다.

## 정리와 제한

```powershell
python outputs/QA/fixtures.py --clean-device
```

명시된 QA Downloads fixture와 QA 백업 파일만 제거합니다. 앱 기록·설치·권한은 자동 초기화하지 않습니다. 필요한 증거를 보관한 뒤 전용 시험 설치의 앱 내 ‘모든 기록 삭제’를 사용하거나 전용 에뮬레이터를 별도로 정리하세요. 스크립트 중단으로 화면 설정이 남았다면 원래 값으로 복원해야 합니다.

실제 Google Play 구매/복원/보류/환불, 검증 API 배포·운영 부하, ARM 실물 기기, 모든 OEM 파일 선택기·기기 이전 도구는 이 재현 세트의 자동 통과 항목이 아닙니다. 코드의 방어 테스트와 실제 서비스 검증을 구분해 보고합니다.
