# 실행·검증·운영 준비

저장소루트기준. 비밀값은예시에넣지않는다. 과거0.3.0산출물은원래이름으로보존한다.

## 개발 환경

| 항목 | 현재값 |
|---|---|
| Android | Kotlin2.2.10 / Compose BOM2025.08.01 / CameraX1.6.2 / MediaPipe1.0.0 |
| 빌드 | Gradle8.14.3 / AGP8.13.2 / JDK17이상,검증JDK21.0.12 |
| SDK | min26 / target·compile36 / 계측기기API35 x86_64 |
| 앱 식별 | `com.moa.babylanguage`,현0.3.0/code6 |
| 결제 | Play Billing9.1.0,서버Node built-ins,외부npm의존성없음 |
| 테스트도구 | Python3.10이상,ADB,ffmpeg(합성fixture생성),Android SDK build-tools36 |

## Android 빌드

```powershell
cd outputs/MoaAndroid
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:bundleRelease :app:lintRelease --max-workers=1 --console=plain
```

메모리16GBPC에서는worker1권장.끝나면`gradlew.bat --stop`으로소유daemon정리. R8/resource shrink는끄며MediaPipe protobuf/JNI가축소빌드에서깨졌던실제이유는[RELEASE](../outputs/MoaAndroid/RELEASE.md)에있다.이부분을임의최적화하지않는다.

설치APK:`app/build/outputs/apk/debug/app-debug.apk`,계측APK:`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`,번들:`app/build/outputs/bundle/release/app-release.aab`. 업로드설정없으면AAB는**unsigned**,debug APK는개발서명이다.

## QA

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

경로/serial은각환경값사용.실제아기데이터없는전용기기에서한작업자가순서대로실행한다.전체모드는[QA README](../outputs/QA/README.md). UI선택기는한국어앱/영어DocumentsUI기준.0.4잠금구현후camera/video/album시나리오를갱신해야한다.선택기에서프로세스종료·손상파일·200%font·작은/넓은화면도포함한다.

```powershell
cd outputs/PlayVerifier
npm test
```

서버시험은실제로localhost HTTP요청을보내지만Google응답은대역이다.이는운영수용인원이나실제Play거래증거가아니다.

## 이 PC에서 확인한 경로

SDK`C:\MoaSdk`,AVD`C:\MoaAVD`,AVD이름`MoaQA35`,JDK`C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`.에뮬레이터실행시`ANDROID_AVD_HOME=C:\MoaAVD`필수.기본한글사용자경로AVD가멈췄던이력이있다.이값들은이PC관찰값이며코닷의다른환경에서는재설정한다.

## 설정 계약

| 위치 | 변수 | 비밀 여부 |
|---|---|---|
| 앱빌드 | `JIGEUM_PLAY_VERIFY_URL` | HTTPS 공개주소 |
| 앱빌드 | `JIGEUM_PLAY_ENTITLEMENT_PUBLIC_KEY` | P-256 SPKI DER Base64 공개키 |
| 릴리스서명 | `MOA_UPLOAD_KEYSTORE`, `MOA_UPLOAD_KEY_ALIAS`, `MOA_UPLOAD_STORE_PASSWORD`, `MOA_UPLOAD_KEY_PASSWORD` | 키경로/암호,Git제외 |
| 서버 | [PlayVerifier README](../outputs/PlayVerifier/README.md)의서비스계정·서명키·패키지·한도변수 | 개인키/서비스계정은서버전용 |

현재앱공개설정은비어있어결제불가.업로드키설정네개는모두제공하거나모두생략해야한다.키생성·권한부여·배포를자동수행하는설정은저장소에없다.

## 운영 연결 순서

1. 사용자Cloud프로젝트선택확인.다른프로젝트에임의배포하지않음.
2. HTTPS검증서비스와비밀관리,GooglePlay API접근,동시성/프록시/할당량/로그/모니터링설정.
3. Play새개인계정본인인증완료후이앱/상품생성.코드와같은package/SKU/basePlan설정.
4. 공개검증주소/공개키로앱빌드,라이선스테스터실제구매/복원/환불검증.
5. ARM실기기품질·서명·공개정책·스토어선언확인후사용자의출시/제출지시에맞춰진행.

오프라인서명캐시는최대24시간.명시적거절시즉시신규유료잠금,일시적장애시남은캐시만허용.환불오프라인반영지연·기기시각조작·토큰탈취/변조앱은[서버한계](../outputs/PlayVerifier/README.md)를읽는다.

## Git와검증근거

브랜치main,remote`https://github.com/yw8837/jigeum.git`.기존기준커밋`a693b98`.협업변경은파일소유/통합자를정한뒤커밋단위로올린다.키·토큰·`local.properties`·`work/`·APK/AAB/ZIP·cache는제외한다.공유용설치파일은로컬`outputs`에있으며Gitclone에APK가없는것은정상이다.

`docs/qa/baseline-0.3.0`에요약JSON과주요로그를함께보존했다.전체스크린샷/동작trace는로컬`outputs/Jigeum-QA-evidence-0.3.0.zip`.원본증거에개인자료가포함되면추가공유전에범위를검토한다.테스트수·skip·실패·실행환경·새APK SHA를각실행별로기록한다.
