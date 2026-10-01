# UltraQA Report — 아가톡 0.5 심층 감사

2026-10-01 · 원본 `063c183...4d98944` · 수정 후보 **0.5.1/code14**.

## 현재 판정

**ULTRAQA COMPLETE: Goal met after 3 cycles — 로컬 코드·QA 증거 감사 및 확인된 결함의 회귀 수정 범위.** 앱 결함3건과 QA 하네스 결함을 재현·수정했고 최종 통합/서명앱 검사를 마쳤다. **실제 아기 장면 적합성·유료 가치·출시 승인은 아직 아니다.**

## 목표와 성공 기준

이전 QA가 실제로 실행됐는지, 테스트가 실패를 잡는지, 새 캡처 흐름에서 놓친 결함이 있는지 검토한다. 발견 결함을 재현한 뒤 수정하고, 설치된 APK의 해시까지 연결한 통합 회귀로 확인한다. 최대5사이클·동일원인3회 실패 제한. 로컬 합성자료/소유AVD만 변경하며 Cloud·Play·실제 사용자 자료는 변경하지 않는다. [실행 전 계획](PLAN.md).

## 발견 및 수정

| 심각도 | 발견 | 원인/영향 | 조치 |
|---|---|---|---|
| P2 | 수정 문장을 접은 뒤 저장하면 원문으로 되돌아감 | UI 접힘 `edit`를 수정 여부로 사용하여 문장 유실 | `draftEdited`로 분리, 접기·복원 후 수정 보존, 빈값 저장 금지 |
| P2 | 영상 옵션이 다음 무료 음성 분석을 취소 | 이전 `visual` 옵션이 남아 만료 watcher 실행 | reset 초기화 + 실행 요청의 `analyzingVisual`만 감시 |
| P2 | 표정 모델 실패를 시각 관찰 성공으로 표시 | 설명 문자열 존재를 성공으로 취급 | 완료/부분/실패 상태 구분, 상단 안내·상태복원, 소리 문장 보존 |
| P2 | 불완전 계측·전체skip·중복결과도 pass 가능 | OK문구/host exit0 위주 parser | test별 start/end/count/runner 완료 확인, 명시skip 목록, 실제설치app/test 해시 일치 필수 |
| P2 | unsigned AAB도 기본 검사 pass 가능 | jarsigner가 unsigned일 때도 exit0 | `jar verified.` 및 부분미서명 부재 요구 |

제품3건은 수정 전 실제 Android 회귀실패로 확인했다. 하네스 parser는 수정 전 거짓성공7건을 재현했다. 기존 code13 자체의 서명과 146통과·3skip 집계는 정상이며 허위 실행으로 판단하지 않았다.

추가 QA 공백: 기존 늦은결과 테스트는 취소 가능한 대역 때문에 반환 자체가 없었다. 이제 `NonCancellable` 대역이 실제로 반환한 카운터를 확인하고 취소/만료 후 결과가 노출되지 않는지 검사한다. 영상 오디오 트랙·구간 종료·1분 영상 상한·파일 크기/길이/손상 경계·시각 실패/부분 결과 검사도 보강했다.

첫 전체 실행은163건 중159통과·1실패·3skip였다. 실패는 앞선 권한 UI검사에서 force-stop한 임시 녹음이 남아 `listFiles().single()` 단언이 깨진 **테스트 격리 결함**이었다. 실제 MainActivity는 onCreate에서 이 폴더를 정리하지만 ComponentActivity 테스트는 이를 건너뛴다. CaptureDeviceTest의 준비 단계에서도 소유한 임시 capture를 정리하도록 고쳤다. [실패 원문](full-before-isolation-fix.txt)과 [당시 APK 해시](full-before-isolation-fix.json)를 보존하며 이후 성공으로 덮어쓰지 않는다.

## Standards

[독립 원본 판정과 수정 후 검토](standards-review.md): hard finding2건(시각 성공 오표시, 늦은반환 검증 누락), 판단상 smell1건(상태를 String으로 전달). 최우선은 시각 실패의 성공 표기였다. 수정 후 소스 기준 승인. 시각 수정 담당자가 후속 검토에도 참여했다는 한계 명시.

## Spec

[독립 원본 판정과 수정 후 검토](spec-review.md): 제품 결함2건(문장유실, 무료음성 차단), 검증누락1묶음. 최우선은 수정 문장의 조용한 유실이었다. 수정 후 요구사항 기준 승인. 두 축의 원본 판정은 합치거나 재순위화하지 않는다.

## 증거 감사

[감사 상세](evidence-review.md). 기존 Android 원시149건은146통과·3조건skip. 새 parser로 재판정해도 같았다. 이전 APK/AAB 해시도 보고서와 일치. 실제 아기 모델 평가와 합성/대역 UI평가를 구분한 과거 보고의 제한은 타당했다.

## 잔여 위험

- 실제 아기 원본을 받지 않아 사용자 제보 녹음의 전후 재현은 없다. 이전 공개 자료는 울음1건 문장·웃음1건 보류·옹알이 미실행. 이번 감사는 이 정확도 표본을 늘리지 않았다.
- 일반 YAMNet gate와 유한한 문장 풀은 유지. 웃음 과보류, 반복 표현, 화자 혼합, 시간대별 최대 점수의 혼합 문제는 모델/제품 평가가 필요하다. 코드를 고친 세 건을 이해력 향상으로 표현하지 않는다.
- 실제 FaceLandmarker가 선택된 길이의 no-face 영상에서 실행되는지는 검사하지만, 장면이 바뀌는 실제 얼굴 영상의 시간 경계·회전·감정 의미는 검증하지 않았다.
- 실제 ARM/OEM·TalkBack·저장공간 고갈·외부 문서 공급자의 무응답·native 호출의 장기 정지·준비 직전 권한 회수 경쟁조건은 완전 검증 범위 밖이다. 코루틴 timeout이 모든 native I/O를 강제 종료한다고 주장하지 않는다.
- 서버56건은 localhost/Google대역. 실제 Play 구매·PENDING·ACK·환불·운영부하, 정책 공개배포, 스토어자료 갱신은 별도 미완료. 공개 제출은 하지 않는다.

## 실행/정리

### 실행 결과와 명령

환경: Windows, JDK21.0.12.101, SDK36, API35 x86_64 `MoaQA35/emulator-5554`. Gradle worker1. 실제 아기 대신 합성 미디어, 결제는 androidTest/localhost 대역. build의 공개 verifier 설정은 실제 URL/공개 키이며 운영 서버를 수정하지 않았다.

| 실행/명령 | 종료·결과 | 목적·기한·증거 |
|---|---|---|
| 수정 전 표적 Android6건 | harness exit1, 앱3실패·나머지3통과 | 수정문장·무료음성·시각실패 red,300초 cap; [로그](red-regressions.txt) |
| 수정 후 표적24건 | exit0,24/24 | 실제 media/model/UI 경계; [로그](green-targeted.txt) |
| `gradlew :app:assembleDebug :app:assembleDebugAndroidTest --max-workers=1` | exit0 | 최종 debug/test 생성; [빌드](build-debug.txt) |
| `instrument.py --install-app --timeout 900 --allow-skip ...` 최종 전체 | exit0, **163발견·160통과·3조건skip·0실패**,231.24초 | 설치app/test SHA와 현재빌드 일치; [원시](instrumentation.txt), [판정/해시](instrumentation-result.json) |
| font scale2.0 표적3건 | exit0,3/3 | 시각실패 안내·접은 수정문장·빈값 저장 금지; [로그](font200.txt) |
| `python -m unittest discover -s outputs/QA -p test_qa_gates.py -v` | exit0,21/21 | 불완전/중복/skip/timeout/서명/설치hash; [로그](host-final.txt) |
| `npm --prefix outputs/PlayVerifier test` | exit0,56/56,skip0 | localhost/Google대역; [로그](server-final.txt) |
| `build-play-bundle.ps1 -Purpose paid-candidate ...` | exit0 | 기존 업로드 키, 서명 APK/AAB 생성; [빌드](build-release.txt) |
| `gradlew :app:lintDebug --max-workers=1` | exit0, **오류0·경고15** | 경고 억제/새baseline 없음; [원문](lint.txt) |
| `check_package.py --apk ...code14.apk --aab ...code14.aab --baseline ...code13.apk` | exit0,pass | 각 subprocess120초, 서명/16KB/백업·공유범위, native/model18파일 동일; [판정](package-verdict.json) |
| `capture_ui.py` 서명release | exit0,small/font200 통과 | 720×1280/320dpi 및1080×2400/420dpi·200%; 녹화/재생/가져오기/유료·정책왕복/실제모델의 순음 보류; [판정](release-ui-result.json) |
| 실제 권한 UI | exit0,pass | 카메라 거절·마이크 허용 → 영상시작 차단, 음성녹음/재생 성공; [판정](permission-result.json), [UI](permission-ui-steps.jsonl) |

최종skip3개는 설정이 **없는** 빌드용 `PurchaseVerifierTest`2개와 명시적 공개자료가 필요한 `PublicAudioProbeTest`1개다. 실제 연결 빌드이므로 허용한 이름을 정확히 지정했다. 163/163통과로 집계하지 않는다. 전체160에 표적24·font3을 합산해 고유 테스트 수를 부풀리지 않는다. 셸의 exit0이더라도 Android 실패본문은 실패로 판정했다.

린트15건은 기존 KTX권고7·라이브러리업데이트4·protobuf1·Gradle1·동기commit1·저장공간API1이다. 이번 감사에서 경고를 숨기거나 대규모 SDK교체를 하지 않았다.

### 시나리오 행렬

목적·행위자·준비·하네스·기대/실제결과·수정·증거·정리를 모두 포함한 [행렬 A01–A10](PLAN.md). 원본 자료에 실행 지시가 있다고 실행하지 않았고, 앱에 LLM 프롬프트 입력경로가 없어 동적 prompt-injection은 해당하지 않는다. 메타데이터/플랫폼 native I/O의 완전 강제종료는 미검증으로 남겼다.

### 패키지와 추적성

- 설치 후보: `outputs/Agattok-0.5.1-code14.apk`,73,776,727bytes. SHA256 `bc05a2fc1cb7b09ff0e27b5f9da931101690bcd72f57e154742f1ba9e55d9c8a`.
- AAB: `outputs/Agattok-0.5.1-code14.aab`,45,155,013bytes. SHA256 `0e22a825256bad476cb3f0e125f5c852917f6167a49abeea24f57e62918bd3e9`.
- [검증 속성](artifacts.json): code13과 같은 업로드 서명, 실제 공개 verifier설정, 새 테스트대역/fixture release미포함. [설치된 release 해시](release-installed.json)도 일치.
- 상세 원로그·재현스크립트는 로컬 `outputs/Agattok-QA-0.5.1.zip`에 보존한다. 선택 증거는 이 폴더에 버전관리한다. 테스트 당시 Git HEAD는4d98944+명시된 working diff이며 새 커밋을 당시 실행 SHA로 소급하지 않는다.

### Cleanup and rollback

시작 worktree는 clean. 작업자는 소유 파일만 수정했고 기존0.5.0/이전 패키지·증거·다른 작업은 보존했다. 테스트 중 생긴 원본은 synthetic이며 아기 원본·실제 구매토큰·개인키를 추가하지 않았다. 실패 로그도 유지했다.

소유 AVD의 앱 데이터·test APK·공유 테스트 fixture를 정리하고 화면1080×2400/420dpi·font1.0을 복원했다. 소유 앱 프로세스와 에뮬레이터를 종료했고 adb 장치목록이 비었다. 이 실행 전용 OMX 상태만 clear하고 `exists:false`를 확인했다. 임시 재현스크립트/원로그는 QA ZIP으로 보존했다. **자동 도구 정책이 `work/deep-audit` 재귀삭제를 실행 전에 차단해 이 증거 폴더도 그대로 남겼다.** 구체 사유는 반환되지 않았으며 우회 삭제는 하지 않았다. 세부 상태는 [cleanup.json](cleanup.json).
