# 지금 내 기분이 그래 Android 릴리스 빌드

## 현재 SDK 진단 전송 구성

0.4.3은 기존 `ACCESS_NETWORK_STATE` 제거를 유지한다. 현재 MediaPipe/Billing SDK의 CCT DataTransport는 decorate 단계의 네트워크 상태 조회에서 이 권한을 요구하므로 이벤트를 저장/HTTP 전송하기 전에 실패한다. 시험에서 SDK transport DB 초기화와 비어 있는 event/context, 해당 권한 오류를 확인했다. 이는 공식 telemetry opt-out 구현이 아니며, SDK 교체나 이 권한 추가 전에 소스·런타임·개인정보 선언을 다시 검토해야 한다. 단순히 오류 로그를 없애려고 권한을 추가하지 않는다. 결제 HTTPS의 `INTERNET`과는 구분한다. [현재 의존성 검토](../../docs/qa/0.4.3/sdk-transport-review.md).

현재 통합 버전은 **0.4.4 (`versionCode 11`)**입니다. 아래 0.3.0/0.4.1/0.4.2 절은 패키징 설정과 검증 이력을 설명합니다. 설치 식별자 `applicationId`는 기존의 `com.moa.babylanguage`를 유지하고, 사용자에게 보이는 앱 이름은 **지금 내 기분이 그래**입니다. 최소 SDK는 26, 타깃 SDK는 36입니다. 현재 범위는 로컬 통합 빌드와 QA이며 출시·제출·운영 배포는 별도 진행합니다.

## 0.3.0 이용권 설정

Google Play Billing 9.1.0과 서명된 이용권 검증을 구현했습니다. 빌드 시 `JIGEUM_PLAY_VERIFY_URL`(HTTPS 검증 주소)과 `JIGEUM_PLAY_ENTITLEMENT_PUBLIC_KEY`(P-256 공개 키)를 설정합니다. **이번 설치 APK는 두 설정이 비어 있어 결제를 안전하게 비활성화합니다.** 가격을 임의로 표시하거나 미검증 이용권을 열지 않습니다. 무료 문장·기록·PNG 공유·JSON 백업은 이용할 수 있습니다.

실제 유료 사용에는 Play Console 상품 `jigeum_album`, 플랜 `monthly`/`three-month-prepaid`, 운영 검증 서비스, 서명 키, 라이선스 테스터와 실제 구매 생애주기 검증이 필요합니다. 자세한 범위는 `../PlayVerifier/README.md`와 `../Jigeum-QA-0.3.0.md`를 참고합니다. 개인 기록은 외부 서버로 보내지 않으며 `INTERNET` 권한은 결제 검증 통신을 위해 추가했습니다.

이번 후보는 **R8 코드 축소와 리소스 축소를 끕니다.** 축소된 시험 APK에서 MediaPipe 1.0.0의 protobuf 필드(`SystemInfo.platform_`)가 제거되어 카메라와 저장 영상의 얼굴 모델 생성이 실패했습니다. 필드 보존 규칙만 적용하면 다음 JNI 연결 오류가 나타났습니다. 축소 문제를 해결했다고 간주하지 않으며, 모델이 동작하는 후보만 배포합니다.

## 검증용 미서명 빌드

Android SDK 36과 JDK 17 이상에서 다음을 실행합니다.

```powershell
.\gradlew.bat :app:bundleRelease :app:assembleRelease :app:lintRelease
```

서명 환경변수가 없으면 `app/build/outputs/bundle/release/app-release.aab`는 **미서명**입니다. Play Console에 업로드할 수 없습니다. `app-release-unsigned.apk` 역시 설치용이 아닙니다. 디버그 키로 릴리스를 대신 서명하지 않습니다.

## Play 업로드용 빌드

Play App Signing에 등록할 업로드 키를 별도로 안전하게 보관한 뒤, 빌드 프로세스에 아래 환경변수 **네 개를 모두** 제공합니다. 키 파일과 암호를 저장소·소스 ZIP·공유 산출물에 넣지 마세요.

- `MOA_UPLOAD_KEYSTORE`: 키스토어 파일의 절대 경로
- `MOA_UPLOAD_KEY_ALIAS`: 업로드 키 별칭
- `MOA_UPLOAD_STORE_PASSWORD`: 키스토어 암호
- `MOA_UPLOAD_KEY_PASSWORD`: 키 암호

한두 개만 설정한 경우 빌드 설정 단계에서 실패합니다. 모두 제공한 뒤 `:app:bundleRelease`를 실행하고 `jarsigner -verify app/build/outputs/bundle/release/app-release.aab`로 서명을 확인하세요. 실제 키는 출시 계정의 업로드 키로 만들고 백업해야 합니다. 테스트용 임시 키로 서명한 번들은 Play 제출용이 아닙니다.

## 16KB 페이지 및 최종 확인

CameraX 1.6.2를 사용합니다. 이전 1.3.4의 `libimage_processing_util_jni.so`는 arm64 ELF의 `PT_LOAD p_align=0x1000`이었고, 1.6.2의 최종 APK/AAB 네이티브 라이브러리는 `0x4000`입니다. AGP 8.13.2가 만든 AAB의 `BundleConfig.pb`에는 `PAGE_ALIGNMENT_16K`가 지정됐으며, APK에 `zipalign -c -P 16 -v 4`가 통과했습니다. 16KB ARM 실기기 실행은 별도 검증해야 합니다.

Play 제출 전에는 릴리스 서명 APK 또는 AAB에서 만든 APK를 실제 ARM 기기에 설치해 녹음, 후면 카메라와 전후면 전환, 저장 영상의 첫 최대 10초 오디오 후보, 보호자 확인, 권한 거부·복구, 기록 삭제를 확인하세요. Play Console의 개인정보처리방침 URL, 데이터 보안, 앱 콘텐츠, 스토어 등록 정보와 심사 항목도 완성해야 합니다.

공식 기준: [Play 타깃 API 요건](https://support.google.com/googleplay/android-developer/answer/11926878), [16KB 페이지 지원](https://developer.android.com/guide/practices/page-sizes), [Play 업로드 준비](https://developer.android.com/studio/publish/upload-bundle).

## 0.4.1 검증 추가

0.4.1/code8은 protobuf-javalite4.27.5와 결제 복구를 반영했다. 패키징 설정은 위 제한을 유지한다. [당시 QA](../Jigeum-QA-0.4.1.md)와 [PR #2 통합 이력](../../docs/qa/PR2-INTEGRATION.md)을 구분한다. 실제 업로드 서명·Play/ARM은 미검증이며 R8/resource shrink를 켜지 않는다.

## 0.4.2 검증 추가

code9/0.4.2는 서명된 구매 토큰 SHA-256 확인값으로 서버 acknowledge 완료를 연결한다. Node→Android 계약과 기존 회귀를 포함한85계측, 서버43시험을 통과했다. [당시 QA](../Jigeum-QA-0.4.2.md)를 확인한다. APK는 개발 서명, AAB는 unsigned였으며 이 실행에는 PR #2를 포함하지 않았다.

## 0.4.3 통합 검증

code10/0.4.3은 준비 완료된 PR #2와 가져오기 transaction·공유 snapshot·CameraX 실행 경계 보강을 통합한다. Android 119/119, 서버 43/43, 실제 UI 5종, 0.4.2에서의 데이터 보존 업데이트, 패키지·정리를 확인했다. [최신 QA](../Jigeum-QA-0.4.3.md)에 두 실패 cycle과 초안 보존 timeout의 미확정 원인도 남겼다. 기존 0.4 접근 정책과 0.4.2 ACK 계약을 유지한다. APK는 개발 서명, AAB는 unsigned이며 실제 구매는 운영 URL/공개키 미설정으로 비활성화다. 실제 Play/ARM/업로드 서명·공개 제출은 여전히 별도다.

## 0.4.4 최종 품질·패키징

최신 행동의 알림을 우선 표시하고48dp 재입력 버튼·실제IME 회귀·KTX/디코더/아이콘 개선을 반영했다. 결제 미설정 시 SDK를 생성하지 않도록 개선하고 생성·종료 회귀를 확인했다. Android123/서버43/UI5종·실제0.4.3업데이트·패키지를 검증했다. Lint오류0/경고11/힌트0이며 경고를 억제하지 않고6개 버전 안내와5개 Boolean 동기저장 권고의 유지 이유를 기록했다. [최신 QA](../Jigeum-QA-0.4.4.md),[해시/크기](../../docs/qa/0.4.4/artifacts.json). 원 초안timeout의 원인은 미확정이다. APK는 개발서명/AAB는 미서명, 실제결제는 비활성이다. 업로드서명·실제Play/ARM·공개제출은 별도다.
