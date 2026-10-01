# UltraQA Report — 테스트 접근 복구 0.5.2

2026-10-01 · **테스트 접근 복구 통과.** 사용자 설치본은 `outputs/Agattok-0.5.2-test-code15.apk` (73,776,779bytes, 약74MB). 설치 안내 포함 `outputs/Agattok-Test-0.5.2.zip` (44,772,455bytes, 약45MB). 전체 아기 의도 해석 품질/유료 출시 완료 판정은 아니다.

## Goal and success criteria

사용자가 받은 APK에서 이용권 때문에 영상 관찰이 막혔다. 테스트 동안 전체 기능을 열라는 요청을 반영한 서명된 APK를 만들고, 실제 영상 모델→기록→PDF 공유와 정식 업데이트 후 재잠금을 확인한다. 전용 API35 x86_64 에뮬레이터만 사용하며 공개 제출/운영 변경은 하지 않는다. 최대5사이클, 같은 실패3회면 중단한다. [전체 행렬/디자인 결정](PLAN.md).

## 원인과 수정

- code14에는 출시용 접근 경계만 있어 표정 옵션을 선택하면 이용권 안내가 나온다. 사용자 확인과 실제 code14 UI에서 재현했다.
- `preview` 배포는 모든 기능을 열고 상단에 테스트 버전을 표시한다. 구매를 저장/위조하지 않고 결제 설정도 비어 있다. 동일 패키지·기존 업로드키 서명이다.
- `release`는 실제 실행 시 `hasCurrentEntitlement()`를 확인한다. preview에서 release로 업데이트하면 테스트 접근이 남지 않는다. preview AAB 생성은 task와 패키징 검사에서 차단한다.
- 이미 허용된 센서는 촬영 화면에서 준비한다. 녹화는 직접 시작한다. 시각 옵션 행 전체를 터치할 수 있고 테스트 영상은 시각 옵션이 기본 선택된다.
- 보호자 안내의 코드 입력 경로를 테스트 APK 설치 안내로 교체했다.

## Standards

[독립 소스 검토](standards-review.md): hard violation0건. 서명 빌더 공통 코드 중복은 향후 정리할 설계 참고1건이며 차단 결함이 아니다.

## Spec

[독립 소스 검토](spec-review.md): 요구누락·불필요한 범위확장·오구현0건. 실제 배포/사용 검증은 아래 실행 결과로 별도 판단한다.

## Failures found / fixes applied

1. 전달한 테스트 설치본의 paid lock: preview 전용 접근으로 수정. 출시 정책 자체는 유지.
2. 첫 Android 실행에서 새 테스트가 고정 하단 메뉴에 `performScrollTo()`를 호출해 1건 실패했다. 실제 클릭으로 고쳤다. 제품 코드 오류로 집계하지 않는다.
3. 첫 signed UI 검사에서 PDF는 생성·공유됐지만 API35 chooser의 중첩 SEND MIME이 `dumpsys`에서 생략되어 검사기가 실패했다. 생성된 PDF의 read grant·URI와 chooser를 동시에 검사하도록 수정했다. Android Print에서 실제 PDF 내용 렌더링도 확인했다.
4. PDF 인쇄 미리보기를 남긴 채 시험 앱을 삭제/재설치하자 기본 인쇄 서비스(`com.android.bips`)가 이미 철회된 FileProvider URI를 다시 읽으며 SecurityException 오류창을 띄웠다. 제품 프로세스의 크래시가 아니며 임시 인쇄 세션 정리 누락이다. 이 작업에서 연 인쇄 서비스/스풀러를 종료하고 재실행했다. 다른 정상 사용 경로의 권한을 넓히지 않았다.

## Residual risks

- preview APK는 자동 만료/원격 잠금이 없다. 테스트 종료 후 정식 앱 업데이트 설치가 필요하다. Play에 이 APK를 제출하지 않는다.
- code15 release 전환 검사는 `adb install -r`의 수동 업데이트다. 실제 Play 업데이트는 versionCode16 이상으로 빌드하고 별도 검증한다.
- 영상 fixture는 권리를 확인한 공개 울음 소리 + 단색 합성 영상이다. YAMNet과 얼굴 미검출 경로의 실행 증거이며 아기 얼굴·감정·욕구 정확도 검증이 아니다.
- 실제 사용자 아기 원본의 장면 적합성, ARM 실기기 카메라/발열/소음, 실제 Play 구매/복원/환불은 아직 별도 확인이 필요하다.
- 자체 업로드키 설치 간 업데이트를 확인한다. Google Play 앱서명으로 설치한 앱과 로컬 APK는 서명이 다를 수 있다.

## 실행 결과 / Commands run / Evidence

| 실행 | 결과 | 근거 |
|---|---|---|
| Android 전체 계측 | **167건:164통과/3예상skip/0실패**,280.15초 | [전체 결과](raw/instrumentation-final.json), [로그](raw/instrumentation-final.log) |
| 서명 APK 실제 UI | code14→preview 기록 보존, 구매 없는 실제 모델/저장/PDF, 재시작, release 재잠금 통과 | [최종 UI](raw/preview-ui-final.json), [PDF read grant/chooser](raw/preview-pdf-share.txt) |
| 작은 화면/200% 글자·비아기 입력 | 첫 실행의 두 layout/abstention 시나리오 통과. 이후 PDF 검사기 실패는 별도 기재 | [첫 UI 실행](raw/preview-ui-first.json), QA ZIP의 스크린샷 |
| 카메라 거절→음성 대안 | 마이크 이미 허용 상태에서 추가 준비 탭 없이 실제 녹음·재생 | [권한 결과](raw/permissions.json) |
| 호스트 하네스 | **24/24통과** | [호스트 로그](raw/host-gates.log) |
| Lint debug/preview | 각각 **0오류/15경고**. SDK/버전/플랫폼 호환 관련 경고를 숨기지 않음 | [debug](raw/lint-debug.txt), [preview](raw/lint-preview.txt) |
| 서명/16KB/백업/모델 | testAPK와 releaseAPK/AAB 통과, native/model18개 기준본과 동일 | [preview](raw/package-preview.json), [release](raw/package-release.json) |
| preview AAB 금지 | `packagePreviewBundle`에서 예상 오류로 중단(exit1) | [차단 로그](raw/bundle-preview-blocked.log) |

명령은 저장소 루트에서 실행했다. Android기기는 `ANDROID_SERIAL=emulator-5554`, SDK `C:/MoaSdk`, API35 x86_64, JDK21.0.12, Gradle worker1이다. root 한 명만 ADB/Gradle을 운용했다.

- `instrument.py --install-app --timeout 1200 --allow-skip <기존3개목록>`: 최종exit0. 첫 실행은 새 테스트의 스크롤 대상 오류1건으로exit1. 실패 기록도 [보존](raw/instrumentation-first.json)한다.
- `preview_ui.py --baseline-apk ...code14.apk --preview-apk ...test-code15.apk --release-apk ...release-code15.apk --cry-video <로컬QA영상>`: 첫 실행exit1은 PDF 검사기 가정, 두 번째exit1은 인쇄 세션 정리 누락. 수정 후 `--skip-layout`으로 이미 통과한 불변 경로는 반복하지 않고 모델/PDF/업데이트를 재검사해exit0. 각UI 대기15–55초, 설치120초 이내.
- `build-test-apk.ps1`, `build-play-bundle.ps1 -Purpose paid-candidate`: 각각exit0. 공개 제출은 하지 않음. preview의 결제 URL·공개키는 빈값이며 상단 테스트 표시를 최종 APK에서 확인했다.
- `check_package.py --distribution preview --skip-aab ...` 및 `--distribution release ...`: 각각exit0. subprocess 한도120초. marker/versionName/서명을 확인했다.
- `python -m unittest discover -s outputs/QA -p test_qa_gates.py`: exit0. `:app:lintDebug :app:lintPreview`: exit0. `:app:bundlePreview`: 예상exit1, 다른 빌드 오류를 성공으로 처리하지 않는다.

조건부skip3건은 공개PCM 전용 probe와 미설정 빌드 전용2건이다. 공개 울음 모델 실행은 signed UI에서 별도로 시행했다. 뜻 정답률을 측정한 것이 아니다.

APK SHA256: `7c7d9d74fb9d215abbc41aa259b6a60d4dec46cd6bcb45c77d5d6694ec44e170`. [산출물 해시](artifacts.json), [소스 지문](source-manifest.json), [결과 요약](verification-summary.json). QA ZIP은 `outputs/Agattok-QA-0.5.2.zip`이며 실패/재실행 스크린샷과 재현 헬퍼를 포함한다. 아기 원본·키·구매 토큰은 포함하지 않는다.

## Cleanup and rollback

전용 app/test 패키지를 모두 uninstall(Success)했고 생성한 Download fixture와 UI XML을 삭제했다. 화면1080×2400/420dpi/font1.0 복원을 확인했다. 인쇄 세션과 직접 시작한 에뮬레이터를 종료했다. 빌드는 `--no-daemon`으로 종료했다. 격리 OMX 상태는 complete→clear→exists:false를 확인했다([상태 로그](raw/state-cleanup.txt)).

실행 자료를 Git 보고서/QA ZIP(82개 항목, CRC 검사 통과)에 보관한 후, 이번 임시 폴더 `work/test-access-052`의 절대 경로가 작업 폴더 안인지 확인하고 PowerShell `Remove-Item -LiteralPath ... -Recurse -Force`를 요청했다. **자동 승인 검토가 `blocked by policy`로 실행 전에 거부했다. 추가 이유는 제공되지 않았다.** 다른 명령으로 우회하지 않았고 이번 임시 폴더는 Git에서 제외된 상태로 남았다. 과거 `work/deep-audit` 거부 역시 우회하지 않았다. APK 작동·검증 결과에는 영향이 없지만 임시 폴더 정리는 미완료다.

Standards: hard0/smell1, Spec:0. **기능/경계 검사: 3사이클 후 통과. ULTRAQA BLOCKED: Temporary QA directory cleanup rejected by policy.** 남은 소유자 작업은 필요 시 이 작업 전용 임시 폴더 정리이며, 설치본 테스트를 막는 문제가 아니다.
