# UltraQA Report — 짧은 발성 보존 · 0.5.12-beta / code26

검증일: 2026-10-05. 기준 commit `94b7b61cb9e1b4418df6b9b40a7de2a18f6a56d0`의 작업 변경을 검토했다.
최신 사용자 요청은 공개 자료 조사 → 타당한 인식 개선 → UltraQA/code-review → Play 비공개 업데이트다.
공개 프로덕션·상품 등록·실제 거래는 제외한다. [조사·적용 결정](../../research/20261005-reference-review-decision.md).

**최신 배포 상태:** 2026-10-05 01:53 KST에 **0.5.12-beta/code26 비공개Alpha 게시 완료**.
02:03 KST 실제 Console에서 최신 출시 버전26과 `선택한 테스터에게 제공됩니다`를 확인했다.
`work/audio-presence-20261005/play-available.png`가 증거다. 아래 검토 대기 기록은 당시 이력이다.
기존 테스터가 업데이트를 받을 수 있으며 개별 휴대폰의 설치 완료 시각은 확인하지 않았다.

## Goal and success criteria

선택한 10/20초 안의 짧고 작은 소리가 주변 무음 때문에 모델 실행 전에 누락되는지 재현하고,
원음·구간·기존 승인 정책을 보존하여 고친다. 최종 서명본의 업데이트·저장 보존·알려진 누락 사례와
비공개 제출을 확인한다. 일반 영아 의도 정확도나 모든 기기의 성능을 입증하는 실험은 아니다.

- 실제 Android RED → GREEN, 잡음·비아기 대조와 실패/취소 회귀가 통과해야 한다.
- 최종 beta에만 AST와 전체 테스트 접근이 있어야 한다. 정식 release 권한은 유지한다.
- 전용 `AgattokPresence26` 에뮬레이터만 사용한다. 사용자 S25 제품 앱은 변경하지 않는다.
- 아기 원본·파생 미디어·임베딩은 외부로 보내지 않는다. 원본 SHA를 확인하고 기기 내 QA 복사본을 정리한다.
- 통과 후 같은 실험을 반복하지 않는다. 새 실패의 원인을 확인할 때만 다시 실행한다.

## Failures found and fixes applied

기존 무음 검사는 정리한 **전체 PCM 평균 RMS**를 사용했다. RMS `.006`인 2초 합성 소리에
18초 무음을 붙이면 전체 RMS가 약 `.00190`이 되어 `.003` 검사에서 `SILENCE`로 끝났다.
20초의 앞·중간·끝 × 40Hz 잡음 유무 6조건에서 실제 `SoundAnalyzer` 실패를 재현했다.

`AudioCleanup.strongestSecondRms()`가 모든 연속 1초 창의 RMS 최댓값을 O(n)으로 계산한다.
이를 `SoundAnalyzer` 무음 판단과 `ClipEvidence`의 작은 소리 안내에 공유했다.
`.003/.012` 기준, 모델 승인 점수, 80Hz 필터, PCM 길이·시간축·모델 입력은 유지했다.
원음 전체를 AST의 중첩 10초 창에 전달하는 기존 검증된 개선도 명시적 beta에 포함한다.

## Scenario matrix

모든 로컬 경로는 ignored `work/audio-presence-20261005/` 기준이다.

| ID / 의도·사용자 상황 | 준비·하네스 | 기대 신호 | 실제 결과·수정·증거 | 정리 |
|---|---|---|---|---|
| PAD / 부모가 긴 구간 중 짧은 소리를 선택 | 2초 RMS .006 tone 단독, 20초 앞/중간/끝, ±40Hz. `AudioPresenceRegressionTest` | 모델 도달, 입력/전체 구간 유지, 영아 승인 없음 | 수정 전 6조건 SILENCE. 수정 후 통과. `red/`, `green/` | 전용 앱·AVD 제거 완료 |
| NOISE / 무음·저음·충격음 | 20초 silence/DC/40Hz, 단일 .8 impulse | 제거 가능한 저음은 SILENCE, 클릭을 영아로 승인하지 않음 | 무음/저음 회귀와 실제 AST 전체 경로 통과. `green/`, `integrated-review/` | 임시 WAV finally 삭제 |
| AST / 단계 실패를 정상 보류로 오집계하지 않음 | 실제 DeviceClipAnalyzer, 같은 WAV의 decode/YAMNet/AST 별도 확인 | 320,000 samples, AST problem null, 정확한 3개 창, babble/문장 없음 | 리뷰 후 보완하여 1개 재검사 통과. `integrated-review/result.json` | 입력 임시파일 삭제 |
| REAL / 공개 원음 전후 대조 | 보관 CC0 cry 첫2초를 증폭 없이 단독/앞/중간/끝. 고정 RED/GREEN APK | 동일 파형/출력·프레임 차이를 측정 | 4조건 모두 전후 동일. 위치별 판정 차이는 기존부터 있었음. `real-pair-valid/summary.json` | 원본 SHA 동일 |
| REGRESSION / 잘못된 PCM·취소·실패복구 | cleanup/timeline/policy/입력계약 34개 + AST 접근/취소/실패복구 포함4개 | exit뿐 아니라 JUnit 실패/제외 없음 | 고유 38개 통과/실패0/제외0. 리뷰 재검사1개는 중복이므로 더하지 않음 | 전용 패키지 제거 완료 |
| PACKAGE / 테스트 접근이 정식으로 유출되지 않음 | 서명 beta/release APK/AAB, 실제 dex/manifest/model/고지/ABI 검사 | beta만 AST·closed=true, release=false | `final-package-result.json` pass. candidate/preview/debug=false. 정렬/고지·backup exclusion 통과 | 고정 산출물 보존 |
| UPGRADE / 기존 테스터의 기록/장면 보존 | `run_ui.py upgrade`, code23 → code26 실제 서명 설치 | 구형 기록 필드·저장 MP4 SHA·전체 테스트 접근 유지 | 최종 결과 아래 기재 | 외부 QA 파일 finally 삭제 |
| KNOWN / 보고된 약국 옹알이 사례 | 최종 서명 beta, 원본0–10초 UI 실행 | 전체 경로 옹알이 관찰·선택 구간 확인 | 최종 결과 아래 기재. 개발에 이미 사용한 사례이며 독립 정확도 아님 | 원본 불변/기기 복사 삭제 |
| STATE / stale state·미정리 환경 | 고유 run cwd, OMX_ROOT/STATE_ROOT 제거, timeout/job 제한 | 기존 프로젝트 상태 보존, 전용 state 제거 | 최종 cleanup 아래 기재 | 다른 AVD/제품 데이터 삭제 금지 |

문자 프롬프트를 받아 실행하는 생성 모델·서버를 이번에 추가하지 않았으므로 prompt injection과
서버 몰림 시험은 변경 범위에 없다. 입력 오류·추론 취소·native 실패·시간 예산은 실제 경로에서 검사했다.

## Commands run and evidence

| 실행 | 제한 / 결과 | 핵심 근거 |
|---|---|---|
| `run_tests.py red AudioPresenceRegressionTest` | 계측300초, JUnit 2통과/1실패 | adb exit0만으로 성공 판단하지 않음. 실제 실패6조건 확인 |
| `run_tests.py green …` | 300초, 34통과, 4.51초 | `green/result.json`, 입력 보존/전체 timeline |
| `run_tests.py integrated …` | 300초, 4통과, 12.40초 | `integrated/result.json` |
| `run_tests.py integrated-review AstIntegratedCandidateTest#sparseSyntheticSoundsNeverBecomeInfantEventsThroughTheWholePipeline` | 300초, 1통과, 18.12초 | 기존 테스트 보완 후 재검사 |
| `real_pair.py` | 하네스별 bounded 실행 | `real-pair-valid/summary.json`, 네 조건 전후 동일 |
| `build_final.py signed-build-03` | 900초/12GiB/output2MiB, exit0, 325.81초 | 서명 beta/release APK/AAB, 각각 Lint; 자식0/cleanup true |
| `verify_final.py` | package360초/dex90초, exit0 | `final-package-result.json`, 실제 dex/manifest/payload |
| `run_ui.py upgrade` / `known_ui.py` | 전용 emulator, 단계별 timeout | 최종 결과 아래 기재 |

beta/release Lint는 각각 **오류0/경고10**이다. 기존과 같은 UseKtx7·UsableSpace2·GradleDependency1이며
[유지 이유](../ondevice-av-generation/LINT-20261004.md)를 따른다. 새 suppression/baseline은 없다.

### 최종 패키지

| 파일 | bytes | SHA-256 |
|---|---:|---|
| `Agattok-0.5.12-beta-code26.apk` | 255122014 | `9826fffa57b452d42902ee13c0706cff8b7df832507f2f4f398b1b1d9d0837ca` |
| `Agattok-0.5.12-beta-code26.aab` | 160149848 | `2b1fe6f9160cd605bc3be0218ee806e9791b7c15d184a0b8bad93ea40e69bc9b` |
| `Agattok-0.5.12-release-code26.apk` | 75332075 | `6f830bdc30b8bd29d423732244d8ddd925130b3066210c6be795baa45e5377f3` |
| `Agattok-0.5.12-release-code26.aab` | 46682227 | `cbbb3004c9d7e086e269c89eb585a5624b73057e5f2c13b1349770337b9b119a` |

Play에는 **beta AAB만 제출**한다. release는 정식 정책 비교용이다. 범용 APK 크기를 기기별 Play 다운로드 크기로 표시하지 않는다.

## Code review

### Standards

독립 Standards 검토: **지적0건**. 원음 보존, 전체 구간, 공유 RMS 계산, 기존 권한/승인 기준을 확인했다.
저장소 기준과 Fowler smell 기준을 적용했다. 정적 검토이며 실행검사를 대신하지 않는다.

### Spec

독립 Spec 검토: 제품 P1/P2 결함 없음. **테스트 P2 1건**: AST 실패 후 baseline으로 돌아가도
합성 비아기 보류 검사가 통과할 수 있었다. 동일 WAV decode→YAMNet→AST의 성공·3개 실제 창·
최종 정책 미승인을 직접 검사하도록 보완했다. 재실행1통과와 소스 재검토 후 잔여 지적0건.

Standards 0건 / Spec 1건 수정 완료, 미해결0건. 수정 후 테스트를 추가한 횟수를 고유 테스트 수로 부풀리지 않는다.

## Harness failures

- 최초 AVD는 비ASCII 긴 경로의 qemu-version.txt 문제로 boot 실패. ASCII `C:/MoaAVD/AgattokPresence26.avd`에서 재실행했다.
- 첫 공개 원음 하네스는 이미 clipping된 PCM에 잡음을 더해 입력범위1.001을 초과했다. 제품이 거부한 정상 동작이다.
  원음4조건만 비교하도록 수정했고 잡음 쌍 비교는 합성 회귀로 검증했다. 실패 `real-pair/`를 숨기지 않는다.
- 서명 wrapper의 앞선2시도는 PowerShell5의 UTF-8 한국어 경로/모듈 경로 문제로 실패했다.
  bundled PowerShell7·UTF8 BOM으로 고쳐 빌드 exit0를 확인했다. 제품/Gradle 실패로 집계하지 않는다.

## Final signed UI, cleanup and Play status

### 서명 설치본

- `signed-upgrade/process-result.json`: exit0, **232.17초**. code23 → code26 설치 APK SHA 확인.
  구형 기록의 모든 필드·LEGACY 출처 유지, 저장 영상824,745bytes/SHA/10초/audio+video 트랙 동일.
  `전체 기능 테스트 · 2분 촬영 · 20초 분석` 표시 확인. 앱 데이터 초기화 없음.
- `signed-known/result.json`: 최종 code26에서 약국 원본0–10초의 `범위 옹알이 후보`와 구간 확인.
  화면 결과 대기17.416초(에뮬레이터/화면 폴링 포함, 모델 벤치마크 아님). 원본 SHA 동일,
  기록 저장 없이 실행하고 기기 내 복사본 삭제. 이미 개발에 사용한 원본으로 정확도를 계산하지 않는다.
- 이전 code25에서 검증한 전체 UI7개와 S25 성능은 기존 보고서에 남기며, 이번에 전부 재실행했다고 하지 않는다.
  이번 변경의 20초 전체 경로는 `integrated-review`에서 실제 decode/모델/정책까지 재검증했다.

### Cleanup and rollback

후보/test/최종 서명 테스트 앱을 전용 에뮬레이터에서 제거했다. 실제 S25 연결은 없었고 제품 앱은 수정하지 않았다.
정상 AVD keeper 종료 exit0, ADB 장치목록 비어 있음, 확인된 backend3296/47996 부재.
최초 실패 AVD keeper의 종료 명령은20초 timeout이었으며, backend 종료 확인 후 공식 avdmanager로
이번에 만든 두 `AgattokPresence26` AVD 경로만 삭제했다.

재귀 삭제 명령은 자동 승인 검토에서 `blocked by policy`로 거부됐다. 구체 사유는 제공되지 않았다.
공식 avdmanager로 정확한 AVD를 정리하고, 별도 설정파일은 경로·일반파일 여부를 확인한 뒤
**검사한 개별 파일만 비재귀 삭제**하고 빈 폴더를 지웠다. 두 설정 폴더도 부재를 확인했다.
`cleanup-result.json`에 기록했다. 연구 원문·실패 로그·고정 APK/AAB·QA 증거는 ignored work에 보관한다.
아기 파일·키·개별 결과·빌드 캐시는 커밋하지 않는다. 기존 타 작업자 변경은 보존했다.

격리 OMX의 phase 갱신이 초기 planning에 남았던 절차 공백을 기록하고, 실제 실행 로그를 기준으로
후속 adversarial-e2e/cleanup 상태를 갱신했다. 과거 실행 때 phase를 기록한 것으로 소급하지 않는다.
01:21 KST에 격리 state를 complete로 기록·읽은 뒤 clear했고, 최종 read의 `exists:false`를 확인했다.
기존 프로젝트의 다른 state는 변경하지 않았다.

**ULTRAQA COMPLETE: Goal met after 2 cycles** — 로컬 변경 검증·정리에 대한 판정이다.
cycle1에서 실제 실패를 재현했고 cycle2에서 수정·리뷰 보완·서명 UI까지 통과했다.
Google 심사 통과나 테스터 배포 완료를 이 판정으로 대신하지 않는다.

### Play 비공개 업데이트

기존 비공개 Alpha code23에서 **0.5.12-beta/code26** AAB를 업로드하고 릴리스 초안을 저장했다.
Play 표시 신규 설치 크기는112MB, 지원 제외 기기는0개다(전 기종 실행 품질 검증을 뜻하지 않음).
검토 화면에는 차단 오류 없이 경고3개: 크기 증가, 가독화 파일 없음, native debug symbols 없음.
R8 난독화를 사용하지 않으므로 해당 mapping은 생성되지 않는다. third-party native 진단 기호 미첨부는
크래시 원인 분석의 제한으로 남긴다. 크기 증가에는 온디바이스 AST/런타임이 포함된다.
사용자 사전 승인 범위 안에서 비공개Alpha 전체 테스터(100%) 업데이트를 저장하고,
**검토를 위해 변경사항 전송**을 완료했다. 01:24 KST 관찰에서 Console은 `검토 중인 변경사항`에
code26을 표시했다. 세부 설명은 자동 빠른 검사가 성공하면 검토에 전송된다는 상태였다.
관리형 게시가 꺼져 있어 승인 후 자동 게시되는 설정을 유지했다.
`work/audio-presence-20261005/play-submitted.png`가 이 상태의 스크린샷이다.
아직 Google 승인·테스터의 code26 수신/설치를 확인한 것은 아니다. 기존 code23과 참여 링크는 유지한다.
Console의 `전체 출시 시작`은 **비공개Alpha 안의100%**이며 프로덕션 출시가 아니다.

후속 확인: 자동 빠른 검사 영역이 종료되고 **`변경사항을 검토 중입니다`**가 표시됐다.
별도 수정 요구는 표시되지 않았다. `play-in-review.png`에 저장했다. 자동 검사 완료와 검토 접수까지
확인했으며, Google의 최종 승인·code26 실제 제공은 아직 확인되지 않았다.

## Residual risks

- 공개 원음 네 조건은 전후 동일했지만, 원본 배치 위치에 따라 보류/울음이 달랐다. 기존 window 경계/집계 민감도가 남는다.
- 알려진 약국 한 원본의 회복은 일반 정확도·모든 옹알이 성공·성인/TV 분리의 증거가 아니다.
- 잡음 처리는 DC/저주파 제거다. 성인 대화와 아기 목소리를 분리하는 기능을 탑재했다고 하지 않는다.
- Gemma/새 한국어 생성은 미탑재. 소리 종류·시간순 얼굴 관찰과 욕구 원인 이해는 다르다.
- 이번 최종 code26은 에뮬레이터 검사다. 앞선 S25 AST 실측은 [기존 보고서](../ondevice-av-generation/S25-AST-20261004.md)에 구분하며 모든 폰 속도로 일반화하지 않는다.

## 완료 조건별 최종 대조

2026-10-05 후속 goal audit. 이전 goal turn은 수정·패키징·실행 검증·커밋/푸시·Play 제출로 실제 진행했다.
이번 확인은 현재 소스와 보관 실행 결과, 새로 불러온 Play 릴리스 상태를 대조했다. 완료된 시험은 재실행하지 않았다.

| 요구 | 확인한 원천 | 판정 |
|---|---|---|
| 선택 구간 전체 소리·시간 흐름 | `SoundAnalyzer`가 전체PCM을 로드하고 timestamp별 결과 보존. `AstAudioAnalyzer.windowRanges`는20초에서0–10/5–15/10–20초. `integrated-review`가 실제3창/성공을 검사 | 구현·실행 확인 |
| 영상의 시간순 변화 관찰 | `VideoImport`의 `detectForVideo` 실제PTS 사용, `VisualTimeline`의순서/최대간격/입변화/초중후집계. `ast-integrated-candidate-01/run-02/known-results-private.json`은0–9.5초20표본·0–19.5초40표본/coverage=true | 전구간 표본 관찰 확인. 모든 frame 의미의 공동생성·발성주체 증명은 아님 |
| 또렷한 옹알이 누락 재현·수정 | 알려진약국10/20초 `INFANT_UNCONFIRMED→BABBLE`, 최종서명26의` signed-known/result.json` 10초실행. 별도약한소리6조건 RED→GREEN | 알려진누락 회복과 평균RMS결함 수정 확인. 독립일반정확도 아님 |
| 후보 모델 검증과 처리 비용 | AST33입력paired, Astra/high 채택 검토, S25원본실행JSON의성능/UI통과·기존앱동일. 조사4문서에후보별권리/Android/평가한계 명시 | 제한적AST 채택 근거 확보. Gemma는 실패·미탑재로 명시 |
| Android통합·회귀·리뷰 | 고유38개/최종signed업데이트/원음SHA/패키지실물/독립두축리뷰 | 해당 변경범위 통과. 과거122개 전체를 이번재실행으로 세지 않음 |
| 원본외부전송금지·온디바이스 | 디코딩→로컬모델→로컬결과 소스, 원본/QA복사본SHA보존·삭제, 연구후보manifest의INTERNET부재. 제품INTERNET은기존결제경로 | 이번변경의원음전송경로추가 없음. 앱전체트래픽새감사로 확대하지 않음 |
| 검증결과/한계구분·커밋·푸시 | QA·조사·HANDOFF/NEXT, `6b8de93`의local/remote SHA일치·clean tree | 완료 |
| 승인된비공개업데이트의실제제공 | Console을새로불러온결과 `0.5.12-beta(26)`은`변경사항을검토중입니다`. 자동검사종료/관리형게시off | **미완료: Google최종승인·테스터제공확인대기** |

위 감사 당시에는 실제 배포 확인이 남아 전체 작업 완료를 보류했다.
후속 02:03 KST 확인에서 **릴리스6/code26이01:53 KST에 게시돼 테스터에게 제공됨**을 확인했다.
마지막 미완료였던 비공개 배포 조건이 충족됐다. 이번 누락 개선·검증·커밋/푸시·비공개 배포 작업은 완료다.
남겨 둔 일반 정확도/새 후보 비교/AV 의미 생성의 한계는 그대로이며, Google 승인으로 품질이 입증됐다고 하지 않는다.
