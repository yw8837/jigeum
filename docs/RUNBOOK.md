# 실행·검증·운영 준비

아래 경로는 저장소 루트를 기준으로 한다. 비밀값은 예시에 넣지 않는다. 과거 0.3.0 산출물은 원래 이름으로 보존한다.

## 개발 환경

| 항목 | 현재 값 |
|---|---|
| Android | Kotlin 2.2.10 / Compose BOM 2025.08.01 / CameraX 1.6.2 / MediaPipe 1.0.0 |
| 빌드 | Gradle 8.14.3 / AGP 8.13.2 / JDK 17 이상. 검증 환경은 JDK 21.0.12 |
| SDK | min 26 / target·compile 36 / 계측 기기 API 35 x86_64 |
| 앱 식별 | `com.moa.babylanguage`, 현재 0.4.1 / code 8 |
| 결제 | Play Billing 9.1.0. 서버는 Node 내장 모듈을 사용하며 외부 npm 의존성 없음 |
| 테스트 도구 | Python 3.10 이상, ADB, ffmpeg(합성 fixture 생성), Android SDK build-tools 36 |

0.4.0 전체 이용권의 권한 분리를 구현했다. 최신 main 검증 결과는 [0.4.1 QA](../outputs/Jigeum-QA-0.4.1.md)를 따른다. PR #2와 별도 로컬 후보는 이 소스에 포함되지 않았다. 이전 0.3.0 증거와 새 실행은 구분한다.

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

현재 앱의 공개 설정은 비어 있어 결제할 수 없다. 업로드 키 설정 네 개는 모두 제공하거나 모두 생략해야 한다. 키 생성·권한 부여·배포를 자동 수행하는 설정은 저장소에 없다.

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
