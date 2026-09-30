# UltraQA — 지금 내 기분이 그래 0.4.4

실행 2026-09-30~2026-10-01 · code11 · Android API35 x86_64 전용 에뮬레이터 `MoaQA35`.

## 목표와 범위

사용자가 지적한 Lint 23경고와 간헐적 초안 시험 timeout을 재검토하고, 코닷 PR #2가 포함된 서비스의 품질·통합 회귀·패키징을 확인한다. 기준 main은 `c7faa2c`, 검증 소스는 `9cef337db42a3ed038383dc87b84a1ffbde39474`다. 원격 main과 open PR 목록을 확인했으며 새로운 미통합 PR은 없었다. PR #2 head `8475658`이 포함돼 있다.

기존0.4 무료/전체 이용권,0.4.2 서버ACK/서명tokenhash,JSONv1/schema1,SDK 버전과 네트워크 상태 권한 제외를 유지한다. root만 빌드/ADB를 실행하고 실제 아기 데이터·운영 계정·서비스를 조작하지 않았다. 최대5cycle/동일 미교정 실패3회 정지를 기준으로 한다. 공개 출시·Play 제출은 보류한다.

## 수정과 Lint 판정

23경고/2힌트에서 **11경고/0힌트, 오류0**으로 줄였다. 검사 비활성화·baseline·경고 억제는 추가하지 않았다.

| 항목 | 변경/유지 이유 |
|---|---|
| API 사용 권고 | bitmap/URI/scale/Canvas API를 KTX로 전환. Canvas는 예외에도 clip/translation을 복원한다. 인수·크기를 유지했다. |
| 상태/디코더/아이콘 | CameraSelector Int state의 boxing 제거, 두 MediaCodec INFO 상태를 기존 bounded loop에서 명시, min26에 불필요한 런처 폴더 qualifier 제거. |
| 결제 초기화 | 운영 검증 설정이 없을 때 Play gateway를 생성하지 않는다. 필요 시 lazy 생성하고 생성된 경우에만 종료한다. 미설정 생성0회/설정 생성·종료1회, 결제 수명주기21개를 검증했다. |
| 이용권 clear | KTX `edit(commit=true)`로 기존 동기 삭제를 유지. 단순 async apply로 바꾸지 않았다. |
| 남은 새 버전 안내 6개 | Gradle·Activity·Lifecycle·test runner/JUnit·protobuf의 새 버전 안내. 검증된 SDK 조합을 유지하며, 안내를 실행 결함 또는 보안 취약점으로 단정하지 않는다. |
| 남은 KTX edit 권고 5개 | 설치ID/서명 envelope·시각 anchor/문장 저장·삭제/legacy 삭제는 `commit()` Boolean으로 실패를 처리한다. KTX edit의 Unit 반환으로 이 확인을 버리지 않는다. |

[전체 경고 위치와 판정](../docs/qa/0.4.4/lint-review.json), [소스 검토](../docs/qa/0.4.4/source-review.md).

## timeout 재검토와 확인한 결함

1. 기존0.4.3의 초안 편집→재입력→paywall timeout은 입력·8초 제한을 유지한 원본 시험 **12/12**에서 재현되지 않았다. 원인은 여전히 미확정이며 이 실행으로 원인이 고쳐졌다고 주장하지 않는다.
2. 같은0.4.3 APK의 전체119개 재실행에서는 다른 시험인 `capturedAlbumCallbackAfterDeleteAllCannotMintFreshGeneration`이 한 번 알림 대기 timeout으로 실패했다. 초안 시험은 이 실행에서도 통과했다. 원본 결과를 보존한다.
3. 삭제 완료 알림이 표시된 동안 새로운 오류를 즉시 안내하는 실제 UI 회귀를 만들었다. 이전 FIFO는2초 기준에 실패했고, `LatestFeedback` 수정 후 동일 시험이 통과했다. 오래된 알림 요청을 취소하고 최신 요청 하나만 유지해 연속 탭으로 대기 요청이 쌓이지 않게 했다. 테스트 제한을 늘리지 않았다.
4. 재입력 행동을 최소48dp TextButton으로 바꿨다. 추가 시험은 실제 IME가 표시됐는지 확인하고, 터치 영역·이용권 만료·화면 복원·편집 보존·무료 저장을 검사한다. **12/12** 반복과 최종 전체에서 통과했다. 원 초안 timeout의 원인을 확정한 수정과는 구분한다.
5. 아이콘 이동 뒤 두 번의 incremental 빌드에서 리소스 연결이 실패했다. 소스 파일은 새 폴더에 존재했지만 merge/parse가 UP-TO-DATE 상태로 처리됐다. `--no-watch-fs --rerun-tasks`로 입력을 다시 읽은 빌드와 후속 정상 빌드가 통과했다. 이전 실패 로그를 보존하며 런타임 앱 오류로 계산하지 않는다.

6. 최종 UI의 paywall-layout은 UiAutomator `null root node` 연속3회로 두 실행이 실패했다. 실패 직후 동일 홈의 구조는 정상으로 수집됐고 앱 crash buffer는 비어 있었다. Activity 시작 명령이 비동기로 반환된 직후 hierarchy를 읽던 harness를 `am start -W` + `Status: ok` 확인으로 교정했다. 문구·터치·화면 구조 검증과 원 초안8초 기준은 유지했다. 교정 후 해당 시나리오 **2/2**와 transfer-e2e가 통과했다. [진단](../docs/qa/0.4.4/layout-harness-diagnostics.json)과 두 실패 원본은 로컬 ZIP의 layout-harness-failure에 보존한다. 플랫폼 타이밍의 상세 원인이나 원 초안timeout을 해결했다고 확대하지 않는다.

## 검증 결과

| 검증 | 실제 결과 |
|---|---|
| Android 전체 | **123/123**, fail/ignored0,123.75초. 기존119 + 알림 겹침1 + 실제IME/터치영역1 + Billing 생성/종료2 |
| 서버 | **43/43**, fail/cancel/skip0. 실제localhost HTTP, Google 응답 대역 |
| 빌드·Lint | debug/test/release/lint 성공. 후보 빌드86초, 마지막300초한도 재빌드10.77초 및 APK/AAB 바이트동일 확인. 오류0·경고11·힌트0 |
| 업데이트 | 실제0.4.3/code10 합성기록1건 →0.4.4/code11 install-r. ID/모든필드/meta/schema1 보존 |
| 패키지 | 개발 서명 APK·미서명 AAB,ZIP/ELF16KB,FileProvider/백업 제외 통과.0.4.3의 모델/native18파일과 바이트 동일 |
| 실제UI | normal/access/album/paywall-layout/transfer-e2e **5/5**. 무료 저장·공유, 결제 잠금/복귀, 앨범, 작은/200%/넓은 화면, 선택기 프로세스 종료·손상 JSON·중복 가져오기 검증 |
| 정리 | 합성기록·출처·내보내기·시험DB·testAPK·fixture 제거, 권한·화면 원복, 재기동 확인 후 전용 emulator/daemon 종료 |

Android 유료 이용권·Google 응답은 시험 대역이며 실제 Play 거래를 대체하지 않는다. CameraX는 실제provider/usecase,권한은 시험 대역이다. 별도UID URI 시험은 명시적인 시험 수신 앱을 사용한다. 실제 UI와 chooser PNG미리보기, 작은/200%/넓은 화면의 시각 검토는 별도 근거로 남긴다.

## 시나리오·명령·근거

| ID | 실패 모델/실행 | 기대/근거 |
|---|---|---|
| Q1 | Lint 원본23/2와 현 소스 대조 | 12경고·2힌트 처리,11개 이유/위치 공개; lint-review.json |
| Q2 | 원 초안8초 조건12회, 실제IME12회 | 원인미확정 보존, 편집/무료저장/48dp 검증; 반복 결과 JSON |
| Q3 | 코닷 exactPR와 최신 mutation/provider/share 경계 | transaction,늦은callback,실행시권한,전달URI 보존; source-review.md/최종계측 |
| Q4 | malformed JSON/충돌/rollback,삭제·공유/만료·취소/프로세스복원/ACK,알림 중첩 | 최종123+43,red→green 알림회귀; instrumentation/server/feedback 로그 |
| Q5 | 실제이전APK 기록후 업데이트,패키지 모델/native/서명/provider/백업 | upgrade-result.json/package-verdict.json/artifacts.json |
| Q6 | dirty상태/허위성공/hung/비밀/정리 | exit뿐아니라tests/fail/ignored 확인,명시적기기/timeout,소유파일만제거,scoped상태·원격SHA확인 |

실행 명령은 `gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:bundleRelease :app:lintRelease --max-workers=1 --console=plain --no-watch-fs`, `python outputs/QA/instrument.py`, `node --test outputs/PlayVerifier/test/*.test.js`, portable UI normal/access/album/paywall-layout/transfer_e2e, `check_package.py --expect-unsigned-aab --baseline outputs/Jigeum-Android-debug-0.4.3.apk`다. 원본 반복/키보드 시험은 class-filtered instrumentation,최대12회/각120초 제한이다. 전체600초,UI각300초,설치90초,패키지120초 제한과 실패 종료코드를 확인한다.

[Git 검증 근거](../docs/qa/0.4.4/README.md)에 최종/실패/반복/업데이트/정리 로그를 함께 남긴다.0.4.3 과거 보고서는 수정하거나 새 검증을 소급 적용하지 않는다.

## 최종 결제 초기화·기동 관찰

미설정 gateway 생성0회를 요구한 시험이 이전 eager 코드에서 `expected0/actual1`로 실패했고, lazy 변경 후 통과했다. 설정된 경우 생성·종료 각1회 시험과 전체 결제 수명주기21개·전체123개도 통과했다. 이전 후보 `5baca43`의121개/SDK13줄/해시8e3b… 증거는 로컬 ZIP의 `before-billing-lazy`에 별도 보존하며 최종 APK의 결과로 쓰지 않는다.

정리 전후 기동 시간은 [측정 요약](../docs/qa/0.4.4/cold-start-lazy-summary.json)에 기록했다. 저사양 x86_64/1536MiB/2core/SwiftShader/debug 환경의 순차 관찰이며 제어된 비교나 실제 ARM 성능 수치가 아니다. 미설정 상태의 불필요한 SDK 생성 제거는 회귀로 확인했지만 기동 속도 개선 원인·실기기 체감은 이 관찰만으로 확정하지 않는다.

## SDK·한계·운영 조건

현재 resolved MediaPipe1.0.0/Billing9.1.0/CCT runtime/backend3.1.8/API3.0.0/protobuf4.27.5를 확인했다. 컴파일된 APK에는 ACCESS_NETWORK_STATE가 없고 최종계측에서 CCT scheduling의 해당 권한 실패12줄을 확인했다.0.4.3의 [전송 경로 검토](../docs/qa/0.4.3/sdk-transport-review.md)와 [이번 차이 확인](../docs/qa/0.4.4/sdk-delta.json)을 함께 읽는다. 공식 opt-out 또는 모든SDK통신의 패킷 인증이 아니다.

JSONv1에 로컬출처를 넣지 않으므로 새DB복원뒤 원충돌백업의 완전한 구분은 불가능하다. 전달snapshot은24h뒤 다음앱작업에서 지연정리하고20개/64MiB상한에서 새공유를 거절한다. 부모가 고른 소리/상황을 유한 문장으로 기록하며 영아 의도 정확도·의료 진단을 검증하지 않았다.

운영결제 URL/공개키와 업로드서명 미설정으로 현재 APK의 실제구매는 비활성화다. 실제Play 구매/보류/복원/환불/ACK/계정변경,Cloud/Play계정·상품/서비스계정/HTTPS/프록시·관측/RTDN,ARM/OEM/16KB 실기기,실사용TalkBack·부모파일럿,정책HTTPS/시행일·스토어제출은 별도 gate다. 완료된 로컬QA·패키징을 전체출시승인으로 표현하지 않는다.

## 정리와 종료

[정리 결과](../docs/qa/0.4.4/cleanup-result.json), [도구 형식 오류와 교정](../docs/qa/0.4.4/cleanup-harness-note.md), [실행별 exit·timeout](../docs/qa/0.4.4/execution-summary.json), [최종 시나리오 행렬](../docs/qa/0.4.4/plan-final.md)을 함께 읽는다. `ls` 열 출력과 없는 폴더 `find`의 stdout 오류는 harness 문제로 분류했고 앱 결함으로 집계하지 않았다. 이전 실패와 현재 통과를 분리했으며 임시 기기·daemon과 이 실행의 OMX 상태를 정리했다.

**ULTRAQA COMPLETE: Goal met after 5 cycles.** 완료 범위는 사용자 요청의 로컬 품질 검토·패키징이며 원 초안timeout의 원인확정 및 실제 Play/ARM/운영 조건은 위 잔여 항목이다.

## 산출물

- [설치 APK](Jigeum-Android-debug-0.4.4.apk): 개발 서명. Android8/API26 이상에서 무료 기능 확인용.
- [AAB](Jigeum-Android-unsigned-0.4.4.aab): 미서명. 현재 상태로 Play 제출 불가.
- [통합 패키지](Jigeum-0.4.4-package.zip): APK/AAB/설치안내/정책초안/QA 근거.
- [QA 원본 증거 ZIP](Jigeum-QA-evidence-0.4.4.zip): 합성자료·화면·실행/실패 로그와 재현/정리 스크립트.
- [해시·크기](../docs/qa/0.4.4/artifacts.json). 이전0.4.3 산출물을 덮어쓰지 않는다. 키·실거래token·사용자기록·빌드캐시는 커밋하지 않는다.
