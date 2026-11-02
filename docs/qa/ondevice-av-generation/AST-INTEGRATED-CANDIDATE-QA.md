# UltraQA Report — AST 실제 앱 경로 통합 후보

2026-10-04 · `17ca360` 다음 변경 · 구현 Astra/high, 읽기 검토 Sol, 실행 부모

## 목표와 경계

고정 AST/v2를 실제 `DeviceClipAnalyzer`에 연결한 별도 debug 후보에서 경로 진입,
알려진 옹알이 사례의 10/20초 분석, UI 취소/재분석, 기존 기록/미디어 회귀를 검사한다.
이번 범위는 배포 승인·일반 정확도·소리/영상 공동 생성·감정/욕구 추론의 완료 조건이 아니다.

- `agattokAstProbe=true`와 `agattokAstCandidate=true`가 모두 있어야 `.astcandidate` APK를 만든다.
- 후보에만 `AST_INTEGRATED_CANDIDATE=true`, 전체 테스트 접근을 적용한다. 기본 설정과 `BabbleSupportPolicy.ENABLED=false`는 유지한다.
- 고정 모델 91,134,123bytes / SHA256 `3dff0cc2b374977ae1460258261608e33428f4767a6959cb8bc910b09433f71e`, ORT1.24.3, 기존 임계값을 유지한다.
- 후보는 별도 이름/패키지, 인터넷·결제 권한 없음, backup=false. 기존 제품에 설치/삭제/중지하지 않는다.
- 아기 원본은 사용자가 제공한 기존 로컬 파일 한 개만 SHA로 확인해 로컬 에뮬레이터에 복사한다. 외부 전송·새 원본 다운로드 없음.
- ADB/Gradle은 부모 한 명. 실행은 각각 제한시간을 두고, 실패/부분 출력/제외 검사를 pass로 세지 않는다.
- 반복은 원인 있는 실패만 수정 후 재실행한다. 같은 33입력 평가·임계값 조정은 하지 않는다.

## 시나리오 행렬

| ID | 의도/사용자 | 설정·실행 | 기대 신호 | 실제·근거 | 수정·정리 |
|---|---|---|---|---|---|
| DISABLED | 호출 누락 검출 | standalone + 실제 경로 sentinel 2개 | AST 진입 횟수 0으로 실패 | 두 검사 모두 `expected:<1> but was:<0>` | 별도 패키지 제거 |
| BUILD | 후보 격리 | 후보 APK/test APK 실물 검사 | 별도 package/target·고정 model·offline | 빌드43초 성공, manifest/설치 SHA 확인 | 고정 실행 사본 |
| GATES | 잘못된 설정·배포 방지 | probe 누락, mixed, 잘못된 Boolean, beta/release/preview/bundle | exit1 + 각 의도한 오류 | 7개 모두 통과 | 로그 보존 |
| CALL | 실제 경로/취소/실패 | decoder→YAMNet→AST 진입 sentinel | 진입·취소 전파·baseline 보존 | 경로2개와 package/model1개 통과 | synthetic 임시 파일 정리 |
| KNOWN | 알려진 회복의 통합 | 고정 원본 actual analyzer 10/20초, visual=true | 옹알이·20/40개 시간순 관찰 | 1개 계측의 두 구간 모두 통과 | 원본/개별 결과 ignored |
| UI | 화면 취소·다음 분석 | 실제 CaptureFlow/DeviceClipAnalyzer, 합성 후반20초 | 메인 응답·취소·새 반환·입력 SHA 보존 | 1개 통과, heartbeat1ms, 저장0 | 결과 위조 없는 관찰 wrapper |
| NATIVE | 네이티브 취소·복구 | 기존 실제 ORT 회귀 | 정상 종료 후 다음 실행 | 2개 통과 | owned native handle 정리 |
| ACCESS | 구간 선택·권한·지연 응답 | CaptureAccessLimits/ClipRangeEditor/취소 회귀 | 기존 접근·UI 계약 유지 | 21개 통과 | isolated test fixture |
| MEDIA | 저장/앨범/이전 기록 | TranslatedMedia/GalleryMedia/RecordTransfer | 손상·취소·충돌 때 기존 내용 보존 | 45개 통과 | 테스트 소유 데이터 정리 |
| TIMELINE | 시간순 영상·취소 | VideoTimelineTest | 뒤쪽 구간·취소 후 재사용 | 13개 통과 | bitmap/decoder 정리 |
| DEFAULT | 기본 빌드 보호 | 정상 assembleDebug + APK/dex 확인 | AST/runtime/opt-in 없음 | 1분 빌드 성공, 모두 부재/false 확인 | 제품에 설치하지 않음 |
| ARM | 실폰 성능 | S25 실제 ADB 연결 필요 | 기기 지연/발열 | USB 장치·ADB 목록에 실폰 없음 | 기존 연결 질문 유지 |

## 첫 실행의 실패와 수정

`run-01`의 **81개 기존 회귀는 통과**했지만 전체 실행은 fail이다. 다음 두 테스트 작성 오류를 숨기지 않는다.

1. known-case 메서드의 `runBlocking`이 마지막 `JSONObject.put()` 반환형을 추론해 JUnit이 `should be void`로 class 전체를 거부했다. `runBlocking<Unit>`로 수정한다.
2. UI 시험에서 `capture-two-minute-tone.mp4`의 첫20초를 골랐다. 이 합성 영상은 첫100초 무음·마지막20초 tone이라 AST 대상이 아니었다. 실제 UI 조절로100–120초를 선택하고 표시 범위까지 확인하도록 수정한다.

둘 다 테스트 설정 결함이며 모델 인식 실패나 앱 취소 실패의 증거가 아니다. 원래 실패 로그와 입력 보존 결과를 남긴다.
고정 app APK는 두 테스트 수정 전후 **동일 SHA**였으며, 재실행은 해당 통합/UI 검사로 한정했다.

## 수정 후 결과

`run-02`는 통합4개와 실제UI1개 **5통과/0실패/0제외**다. 같은 app APK에서 앞서 통과한
81개와 합쳐 이번 범위의 Android 검사86개를 확인했다. 한 번의 일괄86개 실행이라고 표현하지 않는다.
수정 뒤 test APK 재빌드는33초, 통합4개28.22초, 실제UI1개13.31초였다.

| 실제 DeviceClipAnalyzer 입력 | 전체 분석 소요 | 소리 결과 | 시간순 영상 |
|---|---:|---|---|
| 알려진 약국 원본0–10초 |9.296초|옹알이 회복|20개, ordered/전체 구간 coverage 통과|
| 같은 원본0–20초 |15.477초|옹알이 회복|40개, ordered/전체 구간 coverage 통과|

이는 API35 x86_64 에뮬레이터 수치다. S25/일반 휴대폰 속도·발열·독립 정확도로 확대하지 않는다.
시각 관찰은500ms 간격의 순서 있는 표본이며 전체 AV 의미 생성이나 발성 주체 일치를 입증하지 않는다.
일반화 근거는 별도 [33입력 전후 비교](AST-PUBLIC-PAIRED-QA.md)와 그 한계를 함께 따른다.

UI의 첫 호출은 AST 저장소 진입 후 취소가 전달되어 종료됐고 결과를 반환하지 않았다.
메인 스레드 heartbeat는1ms 표본이다. 다음100–120초 호출은 실제 분석기에서 반환했고 합성 tone의
가짜 아기말/저장 없이 입력SHA를 보존했다. 저장소 진입은 native run 시작 증거가 아니며,
tone 보류만으로 AST 정상 추론을 주장하지 않는다. native 취소/복구2개와 알려진 사례의 실제 회복은 별도 근거다.

### 고정 산출물

- 후보 app:264,235,436bytes, SHA `9577306d4c1488a4bf764e05518b478bf4a198d76f67a0fe81745ac0bd1df221`.
- 수정 후 test:2,538,317bytes, SHA `cbc699b6b87d4f1bfd4c7c92b3a78bfd6fd88baa2367636f30e8ab82caf23717`.
- 실행 고정 사본:`work/ast-integrated-candidate-01/run-02/app.apk`, `test.apk`.
- 기본 debug:83,518,799bytes, SHA `03e7428438682d66a2a9f670e0d09bf08b2f1ee3902e95b27688c76d3ed141b0`.
- 기본 APK에서 AST asset/ORT native 및 dex 참조 없음, YAMNet 존재. BuildConfig를 실제 dex로 읽어
  AST/closed-test/preview 세flag false 확인. 기존 제품 package 유지.
- 후보 ORT는arm64/armeabi-v7a/x86/x86_64 native 파일을 포함한다. ABI 파일 존재는 해당 기기의 실행 검증이 아니다.

## 코드 검토

### Standards

Sol 읽기 검토: 문서화 규칙 위반0, 수정이 필요한 설계 smell0. 명시 후보·기본 경로 격리,
androidTest 관찰자·원본 미번들·로컬 결과 구분이 유지된다. 읽기 검토는 동적 결과 재인증이 아니다.

### Spec

Sol 읽기 검토는 새 P1/P2를 찾지 못했지만 최초 동적 실행에서 위 두 테스트 설정 결함이 나왔다.
수정 후 실제5개 재실행 통과로 보완했다. 최종 Spec축 재검토도 두수정·후보격리·결과미치환·QA범위를 대조해
남은P1/P2 없음으로 보고했다. 첫 읽기검토의 누락과 동적실행의 보완을 함께 기록한다.
이번 명세는 통합 후보 경로이며 전체 제품의 AV생성 목표는 계속 미완료다.

## 실행 근거와 남은 작업

모든 원본·개별 관찰·APK·결과는 ignored `work/ast-integrated-candidate-01/`에 둔다.
`disabled/`, `run-01/`, `run-02/`, `build-*.log`, `gate-*.log`를 보존했다. 세 설치 실행 모두 후보 package/PID 제거를 확인했다.
이 에뮬레이터 부팅의 기존 제품은 전후 미설치다. 이를 사용자 실폰의 기존 기록 보존 검증으로 확대하지 않는다.

원본 staging/내부 model cache는 각 별도 package uninstall로 제거됐다. 시작한 에뮬레이터는
`emu kill`로 정상 종료했다. 격리UltraQA state를 complete→read→clear→read로 확인했고
최종 `exists:false`와 state파일부재를 확인했다. 다른작업의state/프로세스는 건드리지 않았다.

**ULTRAQA COMPLETE: Goal met after 2 cycles** — 이번 로컬 통합 후보 QA 범위에 한정한다.

### 재현 명령

SDK `C:/MoaSdk`, JDK `C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot`를 명시한다.

```powershell
# outputs/MoaAndroid
.\gradlew.bat --no-daemon --max-workers=1 -PagattokAstProbe=true -PagattokAstCandidate=true assembleDebug assembleDebugAndroidTest
# 저장소 루트, 새 ignored run 폴더; emulator-5554만 대상으로 고정된 로컬 실행기
python work/ast-integrated-candidate-01/candidate.py <new-run> integration,ui-real
```

테스트의 입력/opt-in 계약은 [구현 계획](../../superpowers/plans/2026-10-04-ast-integrated-candidate.md)을 따른다.
로컬 실행기는 실제 APK manifest/model/설치SHA를 검사하고, known 원본의 과거SHA 일치·전송SHA·새run을 확인한다.
각 계측은150–400초 제한과 전체 테스트 이벤트를 확인하고 실패에도 두 후보 패키지 정리를 시도한다.
7개금지게이트는 각각150초 제한·비zeroexit·해당오류문구를 확인했다.

실제 ARM 성능, 독립 발성 주체 평가, 제품 채택 및 Play 배포는 미완료다. 이번 로컬 통합 QA와 별도 승인 기준이다.
