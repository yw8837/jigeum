# 0.5.5/code19 실행 안내

전체 계측231통과/0실패/1명시제외, 미디어7개/관련회귀31개, 서명앱 UI와 패키징 결과는 [최신 QA](qa/media-0.5.5/README.md)를 따른다. 새 전달 APK/AAB는 `outputs/Agattok-0.5.5-*-code19.*`. 무료/전체 시간 상한은0.5.4와 같다.

미디어 QA: `TranslatedMediaTests`(합성 fixture만), `outputs/QA/media_ui.py`(명시한 권리 확인 로컬 소리), 전체 `instrument.py --timeout 1500 --allow-skip com.moa.babylanguage.PublicAudioProbeTest#reportPublicSoundEventProbes`. Gradle/ADB는 한 명. 기록/파일에 실제 아기 자료가 없는 전용 기기만 사용한다. 신규 SDK 선언1.8.0은 기존 의존성 정렬로 실제 Media3 1.9.0이 선택됨을 dependencyInsight로 확인했다. 플랫폼 진단 전송은 껐다.

Play 초안은code18이다. code19 패키징을 업로드·검토 전송으로 보고하지 않는다. 공개 전화 승인과 계정 차단 해소 후 새 release AAB로 교체한다. preview APK는 Play 제출 불가. 사용자폰 자동 업데이트 전환은 code19보다 높은 후속 versionCode 및 실제 Play 서명/설치 경로로 검증해야 한다.

## 0.5.4 이하 실행 안내 이력

아래는 이전 실행 당시 기록이다. 현재 버전/검증/Play 상태는 위 설명과 HANDOFF를 우선한다.

# 0.5.4/code18 실행 안내 — 2026-10-02

**0.5.4/code18 로컬 QA·서명 UI·패키지 검사 통과, Play 미제출.** [HANDOFF](HANDOFF.md), [QA 보고서](qa/ux-quality-0.5.4/README.md)를 먼저 따른다. 전달본은 `outputs/Agattok-0.5.4-test-code18.apk`다. 유료20/30초는 사용자 검토 중이며 아래는20초 검토본 기준이다.

## 현재 시간·접근 계약

| 항목 | 무료 | 전체 이용권 |
|---|---|---|
| 영상·소리 녹화/녹음 | 60초 | 120초 |
| 영상 가져오기 | 60초·100MB | 120초·200MB |
| 한 번의 소리 분석 | 1~10초 | 1~20초 |
| 같은 구간 시각 관찰·PDF | 이용권 필요 | 제공 |

PCM 디코더의30초 상한은 실제 모델까지 검증했다. 현재 검토본 UI는20초이며 사용자가20초/30초를 검토 중이다. `BillingManager.hasCurrentEntitlement()` 실행 시점 확인을 유지한다. sideload `preview`만 전체 기능을 열고, `release`/Play AAB에는 테스트 접근을 포함하지 않는다.

## 이번 빌드·QA 실행 기준

- Gradle/ADB는 한 담당자가 수행한다. 작업별 `ANDROID_SERIAL`, `ANDROID_HOME`, `JIGEUM_QA_EVIDENCE`를 명시하고 실제 사용자 자료가 없는 전용 기기를 쓴다.
- 현재 UI는 `instrument.py`, `capture_ui.py`, `preview_ui.py`를 사용한다. 아래 과거 0.4 `run_qa.py`의 UI 선택기를 현재 화면 통과 근거로 재사용하지 않는다.
- 실제 무료 60초·전체 120초 녹음/녹화 자동 종료가 포함돼 전체 계측은 장시간 실행될 수 있다. 예: `python outputs/QA/instrument.py --install-app --timeout 1200`. timeout 확대는 실패 은폐나 무한 재시도 허가가 아니다.
- `AppTitleScreenTest`/`AppTitleActivityTest`, `FullCaptureDeviceTest`와 구간 편집·접근 변경·후반 디코딩 회귀를 포함한다. test별 완료·명시 skip·설치 APK 해시를 확인한다.
- 테스트 APK는 `tools/release/build-test-apk.ps1 -Destination <새 test.apk 경로>`로 만들고, `tools/release/build-play-bundle.ps1`은 정식 release를 만든다. 기존 업로드키·공개 결제 설정을 사용한다. `bundlePreview`는 실패해야 한다.
- 현재 버전은 0.5.4/code18이다. 최종 APK/AAB의 실제 파일·서명·SHA는 새 QA 뒤 확정하며 기존 파일명을 덮어쓰지 않는다. preview code18을 전달한 후 Play 자동 업데이트용 버전은 code18보다 높아야 한다.
- 공개 음원 재현은 `python outputs/QA/sound_corpus.py --directory <로컬 폴더>`로 권리 확인 fixture를 준비하고, 현재 debug/test APK에서 `python outputs/QA/sound_probe.py --corpus <같은 폴더> --install-app`으로 실행한다. 원본은 Git/APK/QA ZIP에 넣지 않는다. `--strict`의 사건 라벨은 독립 청취 정답이 아니다.

## QA 다음 단계 — 승인된 비공개 테스트

사용자는 앱 등록 마무리·테스트 버전 업로드·검토 제출·테스터 참여 링크 준비를 승인했다. QA/서명/정책·스토어 문구를 맞춘 뒤 실제 Play Console 상태를 확인하며 진행한다. 참여 대상과 실제 링크 접근·검토/배포 상태를 확인하기 전 ‘12명·14일 시작 가능’으로 보고하지 않는다. 상품 등록·실제 거래 검증·공개 프로덕션 출시는 보류한다. sideload preview를 Play에 제출하지 않는다.

## 이전 실행 안내 이력

아래는 0.5.3·0.5.2 당시 안내를 보존한 것이다. ‘현재’라는 과거 표현·파일·시간·유료 범위는 해당 버전 당시 사실이며 현재 실행에는 위 0.5.4 기준을 적용한다.

<details>
<summary>0.5.3·0.5.2 실행 안내 원문</summary>

# 0.5.3 실행 안내 — 2026-10-02

현재 배포 파일은 `outputs/Agattok-0.5.3-test-code16.apk`. [소리·필터·전체 QA](qa/sound-quality-0.5.3/README.md)를 먼저 따른다. 공개음원 재현은 `python outputs/QA/sound_corpus.py --directory <로컬 폴더>`로 권리가 확인된 fixture를 준비한 뒤, 전용 기기의 현재 debug/test APK를 빌드하고 `python outputs/QA/sound_probe.py --corpus <같은 폴더> --install-app`으로 실행한다. `ANDROID_SERIAL`, `ANDROID_HOME`, `JIGEUM_QA_EVIDENCE`를 명시한다. 원본은 Git/APK/QA ZIP에 넣지 않는다. `--strict`는 배포자 사건 라벨과 불일치하면 실패하지만 그 라벨 자체가 독립 청취 정답은 아니다.

아래는 이전 버전의 실행 이력이다. 버전 번호·설치 파일은 위 현재 값을 사용한다.

현재0.5.2/code15. 먼저 [HANDOFF](HANDOFF.md)와 [최신 QA](qa/test-access-0.5.2/README.md)를 따른다. 아래0.4 QA UI선택기는 역사적 시나리오다.0.5는 `instrument.py`, `capture_ui.py`, `preview_ui.py`를 사용한다. 계측의 조건skip은 [QA 도구 안내](../outputs/QA/README.md)의 명시 목록을 따른다. 정식 버전은 카메라/가져오기 무료, **시각 관찰과 PDF만 유료**. sideload `preview`는 사용자의 전체 기능 테스트 요청으로 구매 없이 열린다.

테스트 APK: `tools/release/build-test-apk.ps1 -Destination <새 test.apk 경로>` (기존 업로드키, JAVA_HOME/ANDROID_HOME 필요). `assemblePreview`는 same-package/서명·0.5.2-test, 공개 결제 설정은 빈값. `bundlePreview`는 명시적으로 실패해야 한다. `build-play-bundle.ps1`은 기존 release만 만들며 테스트 권한을 포함하지 않는다. 정식 배포는 이후 요청에서 진행한다.

</details>

# 실행·검증·운영 준비 — 0.4.5 기준 이력

이하 외부 운영 상태와 옛 UI 명령은 과거 확인 기록이다. 공통 환경·서명 계약을 참고하되 현재 Console/공개 URL 상태는 후속 작업에서 다시 확인한다.

**당시 통합 버전은 0.4.5/code12 connected**였다. [연결 QA](qa/0.4.5/connected/README.md)의 Android142/2skip/0fail·Node56·실제 release UI10·HTTPS9·서명/패키징을 따른다. [운영 구성](release/20261001/OPERATIONS.md)과 [연결 진행](release/20261001/CONNECTION-PROGRESS.md)이 과거 결제 미연결 설명보다 우선한다. SDK/모델 버전은 유지했다.

아래 경로는 저장소 루트를 기준으로 한다. 비밀값은 예시에 넣지 않는다. 과거 0.3.0 산출물은 원래 이름으로 보존한다.

## 개발 환경

| 항목 | 0.4.5 당시 값 |
|---|---|
| Android | Kotlin 2.2.10 / Compose BOM 2025.08.01 / CameraX 1.6.2 / MediaPipe 1.0.0 |
| 빌드 | Gradle 8.14.3 / AGP 8.13.2 / JDK 17 이상. 검증 환경은 JDK 21.0.12 |
| SDK | min 26 / target·compile 36 / 계측 기기 API 35 x86_64 |
| 앱 식별 | `com.moa.babylanguage`, 당시 0.4.5 / code 12 |
| 결제 | Play Billing 9.1.0. 서버는 Node 내장 모듈을 사용하며 외부 npm 의존성 없음 |
| 테스트 도구 | Python 3.10 이상, ADB, ffmpeg(합성 fixture 생성), Android SDK build-tools 36 |

0.4.0 전체 이용권의 권한 분리를 유지한다. 최신 통합 검증 결과는 [0.4.4 QA](../outputs/Jigeum-QA-0.4.4.md)를 따른다. PR #2와 가져오기·공유·카메라 경계 보강을 포함하며, JSONv2 로컬 대안은 적용하지 않았다. 이전 버전 증거와 새 실행은 구분한다.

## Android 빌드

```powershell
cd outputs/MoaAndroid
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:bundleRelease :app:lintRelease --max-workers=1 --console=plain
```

메모리 16GB PC에서는 worker 1개를 권장한다. 작업이 끝나면 `gradlew.bat --stop`으로 해당 작업의 daemon을 정리한다. R8과 resource shrink는 비활성화한다. MediaPipe protobuf/JNI가 축소 빌드에서 깨졌던 실제 이유는 [RELEASE](../outputs/MoaAndroid/RELEASE.md)에 있다. 이 설정을 임의로 최적화하지 않는다.

| 산출물 | 프로젝트 내부 경로 |
|---|---|
| 설치 APK | `app/build/outputs/apk/debug/app-debug.apk` |
| 계측 APK | `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` |
| 번들 | `app/build/outputs/bundle/release/app-release.aab` |

업로드 설정이 없으면 AAB는 **unsigned**이며, debug APK는 개발 서명을 사용한다.

## QA

다음 명령은 저장소 루트에서 실행한다.

```powershell
$env:ANDROID_HOME = 'C:\Android\Sdk'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:ANDROID_SERIAL = 'emulator-5554'
$env:JIGEUM_QA_EVIDENCE = 'C:\qa\jigeum-current-run'
python outputs/QA/instrument.py --install-app
python outputs/QA/fixtures.py --push
python outputs/QA/run_qa.py normal
python outputs/QA/run_qa.py album
python outputs/QA/transfer_e2e.py
python outputs/QA/check_package.py --expect-unsigned-aab
```

경로와 serial은 각 환경에 맞게 지정한다. 실제 아기 데이터가 없는 전용 기기에서 한 작업자가 순서대로 실행한다. 전체 모드는 [QA README](../outputs/QA/README.md)를 참고한다.

UI 선택기는 한국어 앱과 영어 DocumentsUI를 기준으로 한다. 0.4.0 무료 설치에는 access/paywall-layout/album 시나리오를 사용한다. camera/video/invalid는 실제 유효 이용권이 있는 운영 연결 설치에서만 사용한다. 파일 선택기에서의 프로세스 종료, 손상 파일, 200% 글꼴, 작은 화면과 넓은 화면도 시험 범위에 포함한다.

검증 서버 테스트 역시 저장소 루트에서 시작한다.

```powershell
cd outputs/PlayVerifier
npm test
```

서버 테스트는 실제 localhost HTTP 요청을 보내지만 Google 응답에는 테스트 대역을 사용한다. 운영 환경의 수용 인원이나 실제 Play 거래를 검증한 증거는 아니다.

## 이 PC에서 확인한 경로

| 항목 | 경로·이름 |
|---|---|
| SDK | `C:\MoaSdk` |
| AVD 폴더 | `C:\MoaAVD` |
| AVD 이름 | `MoaQA35` |
| JDK | `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot` |

이 PC에서 에뮬레이터를 실행할 때는 `ANDROID_AVD_HOME=C:\MoaAVD`가 필요하다. 기본 한글 사용자 경로의 AVD가 멈췄던 이력이 있다. 위 값은 이 PC에서 관찰한 설정이며, 코닷의 다른 환경에서는 다시 지정한다.

## 설정 계약

| 위치 | 변수 | 비밀 여부 |
|---|---|---|
| 앱 빌드 | `JIGEUM_PLAY_VERIFY_URL` | HTTPS 공개 주소 |
| 앱 빌드 | `JIGEUM_PLAY_ENTITLEMENT_PUBLIC_KEY` | P-256 SPKI DER Base64 공개 키 |
| 릴리스 서명 | `MOA_UPLOAD_KEYSTORE`, `MOA_UPLOAD_KEY_ALIAS`, `MOA_UPLOAD_STORE_PASSWORD`, `MOA_UPLOAD_KEY_PASSWORD` | 키 경로·암호. Git에서 제외 |
| 서버 | [PlayVerifier README](../outputs/PlayVerifier/README.md)의 서비스 계정·서명 키·패키지·한도 변수 | 개인 키·서비스 계정은 서버 전용 |

소스의 기본 공개 설정은 비어 있다. 최종 connected 빌드는 실제 HTTPS `/verify` URL과 대응 공개 키를 환경 변수로 제공했다. 키는 저장소 밖 기존 경로를 재사용하며 업로드 서명 변수 네 개는 모두 제공하거나 모두 생략한다. build 도구는 Cloud나 Play 자원을 자동 생성하지 않는다.

## 운영 연결 순서

1. 사용자가 선택한 Cloud 프로젝트를 확인한다. 다른 프로젝트에 임의로 배포하지 않는다.
2. HTTPS 검증 서비스와 비밀값 관리, Google Play API 접근, 동시성·프록시·할당량·로그·모니터링을 설정한다.
3. 새 개인 Play 계정의 본인 인증이 끝나면 이 앱과 상품을 생성한다. 코드와 동일한 package/SKU/basePlan을 설정한다.
4. 공개 검증 주소·공개 키로 앱을 빌드하고 라이선스 테스터의 실제 구매·복원·환불을 검증한다.
5. ARM 실기기 품질·서명·공개 정책·스토어 선언을 확인한 뒤 사용자의 출시·제출 지시에 맞춰 진행한다.

오프라인 서명 캐시의 유효기간은 최대 24시간이다. 명시적 거절을 받으면 새 유료 작업을 즉시 잠그며, 일시적 장애에는 남아 있는 유효 캐시만 허용한다. 환불의 오프라인 반영 지연, 기기 시각 조작, 토큰 탈취·변조 앱에 관한 내용은 [서버 한계](../outputs/PlayVerifier/README.md)를 읽는다.

## Git와 검증 근거

브랜치는 `main`, remote는 `https://github.com/yw8837/jigeum.git`이다. 기존 기준 커밋은 `a693b98`이다. 협업 변경은 파일 담당자와 통합자를 정한 뒤 커밋 단위로 올린다. 키·토큰·`local.properties`·`work/`·APK/AAB/ZIP·cache는 제외한다. 공유용 설치 파일은 로컬 `outputs`에 있으므로 Git clone에 APK가 없는 것은 정상이다.

`docs/qa/baseline-0.3.0`에는 요약 JSON과 주요 로그를 함께 보존했다. 전체 스크린샷과 동작 trace는 로컬 `outputs/Jigeum-QA-evidence-0.3.0.zip`에 있다. 원본 증거에 개인 자료가 포함돼 있다면 추가 공유 전에 범위를 검토한다. 테스트 수·skip·실패·실행 환경·새 APK SHA는 실행별로 기록한다.

## 0.4.2 결제 계약

검증 서버는 허용 가능한 구매의 acknowledge를 완료하거나 Play의 ACKNOWLEDGED 상태를 확인한 뒤 서명한다. 앱은 서명 수락 후 현재 토큰의 SHA-256과 일치하는 확인값에서만 중복 client acknowledge를 생략한다. 운영 서비스 계정에는 조회와 구독 acknowledge 권한이 필요하며, 새 앱·서버를 함께 실제 Play에서 검증한다. 자세한 오류·동시성·기한 제한은 [서버 README](../outputs/PlayVerifier/README.md)를 따른다. 실제 운영 서버/Play 앱 한정 접근은 연결됐고 실제 구매/ACK는 미검증이다.
