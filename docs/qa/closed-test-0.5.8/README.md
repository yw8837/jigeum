# UltraQA Report · Play 전체 기능 beta 0.5.8/code22

2026-10-02 · 기준점 `2374fb8025dfc781def90de45f4036e8dc94b7ca` · [변경 계약](../../UX-CLOSED-TEST-0.5.8.md)

**구현·빌드·검증·정리 완료.** Android 전체 계측 **253통과 / 0실패 / 3명시제외**, 호스트 게이트 **30통과**, beta/release lint 각각 **오류0 / 기존 경고20**. 최종 서명 UI는 같은 beta APK의 첫 실행 앞4개와 보완 실행 나머지3개의 증거를 합쳐 **7시나리오**를 검증했다. 이를 한 번의 중단 없는 전체 UI 실행으로 보고하지 않는다. 이전 버전의 수치를 합산하지 않았다.

## Goal and success criteria

Play 비공개 테스트용 beta에서 구매 없이 **120초 촬영/녹음·20초 분석·200MiB 가져오기·시각 관찰·PDF**를 사용한다. 영구 구매 권한을 생성하지 않으며 정식 release의 실제 이용권 검사를 유지한다. 같은 업로드키 release 수동 교체 후 무료 **60초/10초/100MiB**로 돌아가고 기존 기록·미디어·무료 내보내기는 유지한다.

사용자 자료 없는 전용 `MoaQA35 / emulator-5554`(Android35/x86_64), 공개 CC0 울음+단색 영상으로 수행했다. Gradle/ADB 담당자는 root 한 명이다. 모델/임계값·외부 DB·서버·상품·실거래·공개 프로덕션을 변경하지 않았다. 최대5cycle·동일 실패3회 중단, 현재 cycle4. 성공 조건 확인 후 선택적 QA를 늘리지 않는다.

## Scenario matrix

| ID | 의도·사용자/오류 모델 | 준비 | 명령/하네스 | 기대 신호 | 실제 결과 | 수정 | 증거 | 정리 |
|---|---|---|---|---|---|---|---|---|
| B1 | 배포 표시 누락/모순·반대 AAB 혼입 | beta/release APK+AAB, 잘못된 flag/suffix | host gate, check_package | 실제 manifest·서명으로 혼동 거부 | host30통과, 두 패키지pass, 실제 beta APK+release AAB 거부 | AAB manifest 검사 필수화 | [beta](evidence/beta-package.json), [release](evidence/release-package.json), [혼입](evidence/mixed-package-rejected.json) | beta AAB 하나만 제출; JAR/cache 미커밋 |
| B2 | 구매 없이 실제 전체 범위·경계 | 최종 서명 beta·구매 없음 | closed_test_ui | 실제2분 캡처·20초 분석·200MiB 경계 |119700ms 영상,20초 무음은 문장 없이 보류;209715200바이트 허용/209715201 거절 | 실제 전체 타임라인·정확한 바이트 경계 검사 | [첫 실행](evidence/signed-ui/closed-test-ui-result.json), [최종](evidence/signed-ui-complete/closed-test-ui-result.json) | 대용량/Downloads fixture 부재 확인 |
| B3 | 접근/기록/구매 회귀·허위 성공 | debug/test 설치 해시 검증 | instrument.py | 각 test 완료·요약 일치, 정확한 skip만 허용 | 첫252/1/3 실패 보존, 최종253/0/3·exit0 | scroll/click/editor 동기화 | [최초](evidence/full-first-result.json), [최종](evidence/full-result.json), [분리17](evidence/navigation-synchronized-result.json) | 전용 기기 앱/test·합성 자료만 정리 |
| B4 | 테스트 결제 유도·기능 잠금 | beta·상품 미등록 | signed UI/UiFeedbackTest | 구매/복원/구독 숨김·모델/PDF 실제 실행 | YAMNet·face/no-face 관찰·실제 PDF chooser/read grant 통과 | closed-test 접근·빈 verifier 설정 | [최종 UI](evidence/signed-ui-complete/closed-test-ui-result.json), [PDF](evidence/signed-ui-complete/pdf-share-activity.txt) | 실제 구매/토큰 생성 없음 |
| B5 | 재시작·stale 권한·교체 후 미디어 유실 | beta 기록 후 같은 키 release | signed UI+SAF+ffprobe/SHA256 | beta재시작 개방·release재잠금·기록 보존 |60/10/100·시각/PDF 재잠금, 동일 기록 MP4 SHA 일치 | 문구 검사에 실제 파일 비교 추가 | [최종 결과](evidence/signed-ui-complete/closed-test-ui-result.json) | 다운받은 MP4 삭제; 자동 업데이트 미검증 |
| B6 | 작은 화면·200% 글자 |720×1280/큰 글자 | signed UI | 안내·촬영·분석·구간 접근 | 앞2개 시나리오 통과 | 완료 문구는 스크롤, dialog는 BACK으로 종료 | [앞4개](evidence/signed-ui/closed-test-ui-result.json), [단계](evidence/signed-ui-complete/ui-steps.jsonl) | 표시 크기·폰트 원복 확인 |
| B7 | dirty worktree·원본/기존 패키지 보호 | 이전0.5.7 test APK·산출물 | package baseline/Git staging | 모델 동일·비밀/원본/cache 제외 | 모델/네이티브18파일 byte 동일·기존 산출물 보존 | 커밋 범위 지정 | [baseline](evidence/beta-package.json), [계약](../../UX-CLOSED-TEST-0.5.8.md) | 다른 작업자/기기/Cloud 미변경 |
| B8 | 중단 후 fixture/process/state 누적 | 소유 파일·PID28112·격리 OMX state | finally+소유 cleanup+state CLI | 소유 기기/자료만 제거·state 없음 | 아래 정리 결과를 따름 | OMX env 제거·run별 cwd·AVD/PID 확인 | 아래 정리 근거 | 전역 삭제/다른 프로세스 종료 없음 |

## Commands run

SDK/JDK/serial/evidence 경로와 기존 저장소 밖 업로드키를 지정했다. 비밀값은 명령/로그에 쓰지 않는다. 공식 bundletool1.18.3의 SHA256은 `a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29`이며 JAR는 커밋하지 않는다.

| 명령·범위 | 종료·핵심 결과 | 목적/제한·증거 |
|---|---|---|
| `python -m unittest discover -s outputs/QA -p test_qa_gates.py -v` |30통과·exit0 | 배포/출처/서명/허위 성공 gate · [host](evidence/host-green.txt), [red](evidence/host-red.txt) |
| Gradle `assembleDebug assembleDebugAndroidTest` | 성공·exit0 | [debug](evidence/build-debug.txt), [test sync](evidence/build-test-sync.txt) |
| `build-play-bundle.ps1 -Purpose closed-test` + beta lint | 성공·exit0·0오류/20기존경고 | beta APK/AAB · [빌드](evidence/build-beta.txt) |
| `build-play-bundle.ps1 -Purpose paid-candidate` + release lint | 성공·exit0·0오류/20기존경고 | 재잠금 비교용·Play 제출 안 함 · [빌드](evidence/build-release.txt) |
| `check_package.py --distribution beta/release --apk … --aab … --bundletool … --baseline …` | 각pass | 서명/16KiB/백업/FileProvider/manifest/모델 · [beta](evidence/beta-package.json), [release](evidence/release-package.json) |
| 실제 beta APK+release AAB 검사 | 정상 거부·statusfail | 반대 배포 혼입 · [로그](evidence/mixed-package-rejected.txt) |
| 전체 계측 최초 `--install-app --timeout 1600` |252통과/1실패/3제외; 기기 runner exit0이나 하네스fail | 실패 본문/완료 수 동시 검사 · [로그](evidence/full-first-run.txt) |
| AccessNavigationTests 변경 전 분리/동기화 보완 후 분리 | 각17통과/0실패/0제외 | 스크롤 실패 원인 분리 · [전](evidence/navigation-baseline-result.json), [후](evidence/navigation-synchronized-result.json) |
| 전체 재실행 `--install-app --timeout 1600` + 아래3개 `--allow-skip` |253/0/3·exit0·564.18초 | 설치 SHA와256개 결과 일치 · [로그](evidence/full-rerun.txt), [JSON](evidence/full-result.json) |
| signed UI 최초 | 앞4개 완료 후 화면 탐색 실패 | [로그](evidence/signed-ui/execution.txt) |
| signed UI 첫 재개 | MP4 실제 검증 후 돌아가기 탐색 실패 | 같은 APK·앞4개 증거 확인 · [로그](evidence/signed-ui-resume/execution.txt) |
| signed UI 최종 `--access-proof` 재개 |7시나리오 증거 결합·exit0 | 실제 모델/PDF·재시작·release 미디어 검증 · [로그](evidence/signed-ui-complete/execution.txt) |

### 명시 제외3개

- `PublicAudioProbeTest#reportPublicSoundEventProbes`: opt-in 공개 원본 미공급. 별도 공개 음성 연구 결과를 이번 전체 통과로 합산하지 않는다.
- `PurchaseVerifierTest#missingConfigurationCannotUnlockPremium`: 이번 debug에는 실제 공개 검증 URL/key가 있어 미설정 전용 가정과 다르다.
- `PurchaseVerifierTest#unconfiguredManagerSurvivesRefreshRestoreAndRepeatedClose`: 같은 미설정 전용 가정이다.

Unexpected skip0. 이3개를253통과에 포함하지 않았다. 실제 beta의 verifier 설정은 비어 있다.

## Failures found

1. **계측 스크롤 동기화:** 최초 전체 실행의 `collapsingEditedDraftStillSavesTheEditedPhrase`가 부분 노출 버튼을 클릭해 editor를 열지 못했다. 변경 전 분리17개는 통과했다. `openDraft()`를 완전히 scrollTo한 뒤 클릭·editor를 기다리도록 보완했다. 앱 debug SHA는 최초/최종 동일하며 test APK만 바뀌었다. 보완 후 분리17개·전체253개 통과. 최초 실패를 삭제하거나 pass로 바꾸지 않았다.
2. **signed UI 화면 탐색:** 첫 실행에서 실제 저장 완료 문구가 아래에 있어 단순 wait 실패. 첫 재개에서는 위쪽 돌아가기 버튼을 아래로만 스크롤해 찾지 못했다. 문구까지 스크롤하고 Android BACK으로 dialog를 닫도록 하네스를 보완했다. 제품 코드 변경 없이 같은 beta APK에서 실제 MP4/PDF·재시작·release 보존을 확인했다. 실패 실행2개는 별도 폴더로 보존했다.
3. **검증 누락:** 초기 AAB manifest 검사·실제2분 길이·200MiB 정확한 경계·미디어 보존 실증이 부족했다. 실제 AAB·전체 타임라인·바이트/파일 비교를 추가했다. 실제 beta APK+release AAB는 정상 거부한다.

동일 실패를3회 반복하지 않았다. 최종7개 UI는 첫 실행 앞4개와 최종 보완 실행 나머지3개의 증거 결합이다. `--access-proof`에서 같은 beta APK SHA·119700ms·209715200/209715201바이트·원본 증거 해시를 확인했다. 실패 실행 전체 status는 여전히fail이다.

## Fixes applied

- `build.gradle.kts`, manifest: beta 배포/표시·versionCode22·빈 verifier. 정식 release는 테스트 flag false, preview AAB 차단 유지.
- `AccessPolicy.kt`, `MainActivity.kt`, `LibraryScreens.kt`: 테스트 전체 범위와 정식 전환 안내, 구매/복원/구독 숨김. 실제 release 권한 검사 유지.
- `UiFeedbackTest.kt`: beta 접근/안내/버튼/권한 저장 없음 회귀. `AccessNavigationTests.kt`: scroll/click/editor 동기화.
- `build-play-bundle.ps1`, `check_package.py`, `test_qa_gates.py`: 제출 purpose·컴파일된 marker·AAB manifest·혼입 거부.
- `closed_test_ui.py`: 실제 길이/경계·모델/PDF·미디어·재잠금·표시 복구 증거.

### Standards

별도 Standards 재검토에서 AAB 검사·검증 증거 부족을 지적했다. release 실제 권한 검사·preview 제출 금지·온디바이스·기록 보존 계약을 유지하며 gate를 보완했고 소스 재검토에서 보완을 확인했다.

### Spec

별도 Spec 재검토에서 실제2분 길이·용량 경계·기존 영상 보존 실증 부족을 지적했다. 타임라인/바이트/파일 비교로 보완했다. 로컬 결과를 Play 자동 업데이트·실기기·아기 의도 정확도라고 표현하지 않는다.

## Packaging

| 산출물 | SHA256 | 제공/제출 |
|---|---|---|
| `Agattok-0.5.8-beta-code22.apk` | `5aeddc4140435a5d89340bc656d44d8502cc28f870081f5808ae86c765c4d803` | 다운로드 전체 기능 APK와 동일 |
| `Agattok-0.5.8-beta-code22.aab` | `4f393f9561bfcf1b73575e60e12a0f8f7308332459dbdc54f665a04f729e52fd` | Play Alpha 제출본 하나 |
| `Agattok-0.5.8-release-code22.apk` | `5b3d699a15bbd4d68e11717fa44a6c87d43ffa5e03c0aeb5c6cc77baa6a9e5d7` | 로컬 재잠금 비교용 |
| `Agattok-0.5.8-release-code22.aab` | `7991542d261e73754c734223f3f6dfdc44d320a1b4645e8c44b461859c8cc73e` | 비교용·제출하지 않음 |

서명 UI에서 실제 설치 base APK SHA와 위 파일의 일치를 확인했다. 모델/네이티브18파일 byte 동일, 백업 제외·공유 경로·16KiB 통과. self-signed chain 안내는 키 서명 검증과 구분한다.

동일 기록 `아가톡-1790921866899.mp4`를 beta/release에서 SAF로 실제 내려받았다. **825395바이트·10초·audio+video**, SHA256 `d97dc005f454943e008b50a56fe9d7802b580d8fe63319c0f1704f8b53b13e0b` 동일. 원본/내려받은 MP4는 Git 제외.

## Cleanup and rollback

**정리pass.** [최종 근거](evidence/cleanup-result.json). 하네스 finally에서 소유 Downloads 파일·대용량 fixture·내려받은 MP4 부재와 표시/폰트 원복을 확인했다. 전용 `MoaQA35`와 PID28112의 정체를 확인한 뒤 합성 앱/test 데이터·`/sdcard/moa-ultraqa.xml`을 제거하고 package/Downloads/dump 부재를 확인했다.

`adb emu kill`은 성공했지만 첫15초 `Wait-Process`는 시간 초과했다. 이후 PID28112/자식과 ADB 목록에서 소유 에뮬레이터의 부재를 확인했다. 강제 종료나 다른 프로세스 종료는 하지 않았다. 격리된 run의 OMX cleanup→complete4→clear가 성공했고 최종 read는 `exists:false`, state파일 부재다. 저장소/다른 run의 상태는 보존했다.

다운로드 `아가톡 테스트/아가톡-전체기능테스트.apk`는 파일1개,75354969바이트이며 최종 beta APK SHA와 같다. 증거/최종 패키지/기존 기준본은 보존한다. 원본·구매 토큰·키·서비스 계정·빌드 캐시·JAR는 커밋하지 않는다.

**ULTRAQA COMPLETE: Goal met after 4 cycles.** 완료 범위는 로컬 접근·작동·회귀·패키징·정리이며 아래 잔여 검증과 Play 심사를 대신하지 않는다.

## Residual risks

- 실제 Play split 설치·자동 업데이트·ARM/OEM·Android26–28 권한/제공자·실제 구매/ACK/환불은 미검증이다.
- beta 자체 자동 만료/강제 업데이트 없음. 정식 Play 전환은 **code23 이상**과 실제 서명/설치 경로 확인 필요. 같은code22 release 수동 교체를 자동 업데이트로 보고하지 않는다.
- 의도 정확도·유료 가치·작은 수음의 원인을 이번 접근 QA로 입증하지 않았다. 모델/필터/임계값을 임의 변경하지 않았다. 다음 사용에서 선택 구간 원음을 확인한다.
- 상용 유료20/30초 확정·상품/실거래·프로덕션 신청은 별도. 기존 기록/영상/무료 내보내기는 정식 전환 후에도 유지한다.
- lint기존20경고와 Play가독화/native기호2경고는 남는다. 난독화false여서 mapping은 생성하지 않음. 기호 미제공은 native crash 분석의 한계. beta 지원 기기 감소0.
- 최소12명·연속14일은 실제 Play 참여로 확인한다. 링크/그룹회원/sideload는 이를 대신하지 않으며 정식 승인이 자동으로 되는 것도 아니다.

## Evidence

- [전체 최종 JSON](evidence/full-result.json) / [per-test 로그](evidence/full-rerun.txt)
- [서명 UI 최종](evidence/signed-ui-complete/closed-test-ui-result.json) / [실제 단계](evidence/signed-ui-complete/ui-steps.jsonl)
- [signed UI 첫 실패](evidence/signed-ui/closed-test-ui-result.json) / [첫 재개 실패](evidence/signed-ui-resume/closed-test-ui-result.json)
- [Play 최신 상태](../../release/20261002/PLAY-STATUS.md) / [초대 안내](../../release/20261002/TESTER-INVITE.md)
