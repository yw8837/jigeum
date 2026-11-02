# 분리된 AST 기기 점검 도구 QA

2026-10-04 · 연구 APK 전용. 제품 인식 채택·정확도·Play 배포 판정과 별도다.

## 범위와 격리

- app: `com.moa.babylanguage.astprobe`, test: `.astprobe.test`.
- 합성 PCM만 기기 내부에서 생성한다. 아기 원본·기록을 읽거나 전송하지 않는다.
- 최종 manifest의 인터넷·카메라·마이크·저장·결제 권한 제거, 런처 비활성, backup=false,
  계측 targetPackage를 실제 APK에서 확인했다.
- 설치 전 APK 사본을 실행별 폴더에 고정하고, manifest·로컬 SHA·설치된 SHA를 같은 파일로 검사한다.
- timeout/실패에도 두 연구 패키지의 중지·제거·패키지/PID 부재를 각각 확인한다.
  한 단계가 실패해도 나머지 정리를 시도하고 마지막에 실패를 보고한다.
- 제품 `com.moa.babylanguage`는 설치·삭제·중지하지 않는다. 설치돼 있으면 APK SHA·버전·업데이트 시각만 비교한다.

## 재현한 오류와 수정

1. 테스트 패키지 uninstall 오류가 app 패키지 정리를 건너뛰던 host 결함: 실패 회귀 후 best-effort 정리로 수정.
2. manifest 검사 후 빌드 원본이 덮어써지면 다른 파일을 설치할 수 있던 host 결함: 실패 회귀 후 실행별 사본으로 수정.
   두 수정은 Astra/high 읽기 재검토에서 해소됐으며 host 12개를 직접 실행해 통과했다.
3. 첫 실제 Android 실행은 `measureSyntheticTenAndTwentySecondCalls() should be void`로 실패했다.
   Kotlin의 마지막 `JSONObject.put()` 반환값 추론을 `runBlocking<Unit>`로 고쳐 실제 재실행했다.
   첫 실패도 연구 패키지 정리는 완료했다.
4. 중간 재빌드는 `ANDROID_HOME` 누락으로 실패했다. `C:/MoaSdk`를 명시한 재빌드가 성공했다.
   이 환경/하네스 실패를 모델 인식 실패로 세지 않는다.

## 실제 에뮬레이터 실행

`MoaQA35`, API35, x86_64, 합성 tone. `work/ast-device-probe/emulator-run-02/result.json`은 exit0/pass다.
benchmark 계측 1개에서 3회 분석, 별도 native 취소/복구 계측 2개 모두 통과했다.

| 분석 | 입력 | 실제 창 범위(ms) | analyzer 전체 | 최고 PSS 표본 |
|---|---:|---|---:|---:|
| 최초 10초 |160,000 samples|0–10,000|2.570초|517,256 KiB|
| 20초 |320,000 samples|0–10,000 / 5,000–15,000 / 10,000–20,000|4.602초|521,081 KiB|
| 20초 반복 |320,000 samples|동일|3.801초|529,166 KiB|

메모리는 약100ms 간격 표본이며 절대 최대치가 아니다. 시간에는 분석기 내 모델 준비·특징 추출·추론이 포함되지만
영상 디코딩·YAMNet·얼굴 관찰·제품 UI는 포함되지 않는다. 에뮬레이터 thermal0은 실폰 발열 증거가 아니다.
초기 분석은 해당 프로세스의 첫 AST 호출이며 OS 파일 캐시까지 비운 하드웨어 cold 측정은 아니다.

이 부팅의 제품 패키지는 실행 전후 모두 미설치였다. `production_install_unchanged=true`를
기존 44개 파일의 보존 재검증으로 표현하지 않는다. 연구 패키지/PID 제거는 완료됐다.
이전 QA 부팅의 기록 보존 결과와 별도 실행이다. 시작한 에뮬레이터는 `emu kill`로 정상 종료했다.

### 고정 산출물

- AST INT8 모델:91,134,123bytes, SHA256 `3dff0cc2b374977ae1460258261608e33428f4767a6959cb8bc910b09433f71e`.
- 점검 APK:264,235,296bytes, SHA256 `f76224c21fb2280881db0c3c6bed5e50e7151f9f50ce53ec712f0029fc064f43`.
- 수정 후 test APK:2,466,325bytes, SHA256 `f28d9b68f0a80c01389a90397827d7fb792bbaa483aba59e522f29b211144c74`.
- 실행 사본: ignored `work/ast-device-probe/emulator-run-02/probe-app.apk`, `probe-test.apk`.
  일반 Gradle debug 출력은 이후 기본 빌드로 바뀔 수 있으므로 이 사본을 실기기 점검에 사용한다.

## 실기기 상태와 남은 범위

사용자가 S25 USB 디버깅을 켰다고 알려 준 뒤 ADB와 Windows 현재 장치를 조회했다.
ADB에는 에뮬레이터만, Windows에는 S25 블루투스 항목만 확인돼 USB 재연결을 요청했다.
**이번 AST의 ARM 실측은 아직 없다.** 이전 Gemma 실측 수치를 AST에 옮기지 않는다.
연결되면 같은 사본/명시 serial로 실행하고 제품 설치 정보 불변·연구 패키지 정리를 확인한다.

기본 `assembleDebug` 빌드를 새로 실행해 성공했다. 패키지는 기존 `com.moa.babylanguage`이고,
APK의 AST asset·ORT native/Dex는 없으며 YAMNet은 존재한다. SHA256은
`2cbdaf2b43fd492a02d57a52b9fcc5628bb04a0aa2cf17d169ff5090f211a949`다.
`agattokAstStandalone=true` 단독 요청과 연구 standalone의 `assembleBeta`는 각각 exit1과
의도한 진단문으로 거부됐다. `work/ast-device-probe/package-gates.json`에 결과를 보존했다.
기본 앱 전체 회귀/서명 beta/Play 배포를 이번 연구 도구 검증에 포함했다고 보고하지 않는다.

최종 Sol 읽기검토에서 standalone 도구의 추가 P1/P2는 발견되지 않았다.
별도 delta 감사의 전후 동시 누락 P2는 합성 RED 후 기대 대상×mode 대조로 수정했고,
host 12개와 delta 10개를 함께 직접 실행해 **22개 통과**했다.
감사 실제 `run-03`도 통과했다. [감사 결과](../../research/20261004-ast-delta-audit.md).

실기기 속도 검증 후에도 [제한적 채택 기준](../../research/20261004-ast-adoption-recheck.md)의
추가 승인 오인 대조와 제품 통합 성능 검증이 남는다.

## 재현 명령

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot'
$env:ANDROID_HOME='C:/MoaSdk'
# outputs/MoaAndroid에서 연구 빌드
.\gradlew.bat --no-daemon --max-workers=1 -PagattokAstProbe=true -PagattokAstStandalone=true assembleDebug assembleDebugAndroidTest
# 저장소 루트에서 실행. 새 output 폴더와 실제 확인한 serial을 사용한다.
python tools/research/ast_device_benchmark.py --serial <serial> --adb C:/MoaSdk/platform-tools/adb.exe --apkanalyzer C:/MoaSdk/cmdline-tools/latest/bin/apkanalyzer.bat --apk <standalone.apk> --test-apk <standalone-test.apk> --output work/ast-device-probe/<new-run>
python -m unittest discover -s tools/research -p test_ast_device_benchmark.py -v
```
