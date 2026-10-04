# UltraQA Report — AST 베타 0.5.11 / code25

2026-10-04 · 기준 커밋 `85110f7` · 로컬 서명 후보 검증

## 최종 후보 확정 — 경고 보완 후

**사용할 로컬 후보는 `work/ast-beta-package-03/`의 0.5.11-beta / code25다.**
아래 첫 실행의 `ast-beta-package-01` 파일과 중간 `02` 파일은 전달 대상이 아니다.
원격에 올리는 것은 소스와 QA 문서이며 APK/AAB·모델·원본·비밀키는 제외한다.

| 최종 검사 | 결과 |
|---|---|
| 서명·manifest·배포 표시·16KiB 파일 정렬 | beta/release APK+AAB 모두 통과 |
| AST 모델·고지·ORT 4 ABI | 명시 beta에 포함, 모델 SHA 동일. 기본 beta/release에는 없음 |
| 실제 dex flags | beta AST_BABBLE=true, CLOSED=true, CANDIDATE/PREVIEW/DEBUG=false. release는 모두 false |
| Lint | beta/release 각각 오류 0 / 경고 10. 원인과 유지 사유는 [경고 보고서](LINT-20261004.md) |
| 관련 계측 | 경고 보완 회귀 121개 통과, 마지막 미디어 수정 후 46개 통과. 중복 제외 122개, 실패·제외 0 |
| 실제 서명 23→25 업데이트 | 217.05초, ID·내용·LEGACY·기존 MP4 SHA·베타 전체 접근 보존 |
| 최종 서명 UI | 572.43초, 7개 시나리오 모두 통과. 작은 화면·200% 글자·실제 2분 촬영·20초 무음 보류·200MiB 경계·저장/PDF·재잠금 |
| beta→release 후 기존 영상 | 같은 파일명·SHA 유지, 이용권 없이 기존 미디어 내려받기 통과 |
| 알려진 약국 원본 | 최종 beta UI의 0–10초에서 옹알이 후보 근거와 결과 표시. 원본 SHA 불변·복사본 제거 |
| 독립 Spec / 부모 Standards 검토 | 미해결 P1/P2 없음. 독립 리뷰가 실제 실행 검증을 대신하지 않음 |
| 정리 | 새로 만든 전용 AVD·INI 제거, 격리 UltraQA state clear 후 부재 확인 |

- 최종 APK: **255,122,018 bytes**, SHA `d0351e1d3249b668d904c982b4e9188cd958424c91887e3fa7165f030c0fbdb8`.
- 최종 AAB: **160,149,768 bytes**, SHA `4a91fa66c266f763255d9fc8770570280d19025bb1cc09e0cbada62611bed70e`.

S25 device spec의 bundletool 다운로드 예측: **112,371,205 bytes (약 112.4MB)**.
실제 Play 다운로드/자동 업데이트를 측정한 값은 아니다.

최종 빌드는 287.47초 / exit0이며 `final-package-result.json`, `control-lint-result.json`,
`signed-upgrade/`, `signed-closed/`, `signed-known/`, `cleanup-result.json`을 직접 대조했다.
첫 후보와 native/model 비교는 beta 26개, release 18개 모두 byte 동일하다.
타이머의 Kotlin 코드와 Java 의존성은 바뀌었으므로 이전 S25 속도 측정을 최종 패키지 실측으로 부르지 않는다.

경고 작업 중 Media3 새 AV factory의 무음 트랙 생성 차이를 발견하여 single-item export로 수정했고,
새 실제 video-only 회귀가 장면·자막·길이·오디오 트랙 부재를 확인했다. 테스트용 혼합 SQL 인자 배열의
Kotlin 타입 추론 경고는 `arrayOf<Any>`로 명시하고 test Kotlin 컴파일 exit0 / 24초를 확인했다.
이 마지막 한 줄은 androidTest 전용이며 제품 APK 코드를 바꾸지 않는다.

임시 AVD의 직접 재귀 삭제 명령은 실행 전 자동 승인 검토에서 차단됐다. 정확한 serial로 종료한 뒤,
등록 경로를 확인하고 Android 공식 `avdmanager delete avd -n MoaASTBeta25`로 정리했다.
기존 MoaQA35의 누락된 시스템 이미지 때문에 전체 AVD 목록 명령이 오류를 냈으나, 해당 환경은 변경하지 않았다.
최종 임시 AVD·INI·프로세스와 격리 UltraQA state의 부재를 확인했다.

**ULTRAQA COMPLETE: Goal met after 2 cycles — 이 로컬 패키징·경고·접근·저장 범위는 보완 후 통과했다.**
전체 AV 이해·새 문장/감정·욕구 생성, 일반 인식 정확도, 모든 기기 성능, Play 배포는 미완료다.
Gemma를 포함하지 않은 부분 개선본 선배포 범위를 사용자와 확정한 후에만 Play 제출을 진행한다.
이 문서 이후의 아래 섹션은 첫 후보와 수정 과정의 기록이다.

---

## 목적과 종료 기준

고정 AST 모델을 명시적으로 포함한 베타를 만들고, 실제 최종 설치본의 실행·접근 정책·기존 기록 보존을 확인한다.
기존 제품의 옹알이 누락을 제한적으로 보완하는 작업이다. 전체 소리·영상의 의미를 공동으로 이해하여 새 문장이나
감정·욕구를 생성하는 기능은 완료되지 않았다. Gemma는 이 후보에 포함하지 않는다.

- 사용자 S25에는 별도 연구 앱과 합성 입력만 사용했다. 기존 제품 앱과 아기 자료는 변경하지 않았다.
- 최종 제품 패키지는 새 일회용 AVD `MoaASTBeta25` / `emulator-5556`에서 검사한다.
- 아기 원본·파생 입력은 로컬에만 둔다. 원본, APK/AAB, 모델, 비밀키, 개별 사용자 출력은 Git에 넣지 않는다.
- 동일한 33입력 연구나 끝난 단독 성능 측정은 반복하지 않는다. 최종 설치본의 알려진 사례 재검사는 배선 확인이다.
- Play 업로드는 이번 QA에 포함하지 않는다. Gemma 미포함 부분 개선본의 선배포 범위는 사용자 확인이 남아 있다.
- 모든 실행은 시간 제한을 두며, 같은 실패 3회 / 최대 5회 원칙을 따른다. 정리 완료와 실제 증거가 있어야 종료한다.

## 변경

`agattokAstBeta=true` 또는 서명 도구의 `-AstBabbleSupport`를 명시한 closed-test 베타에만
AST 모델·ONNX Runtime·라이선스 고지와 `AST_BABBLE_SUPPORT=true`를 포함한다.
기존 v2 임계값·충돌 방지·실패 시 기존 판정 유지·취소 전파는 변경하지 않았다.
일반 beta/debug/release는 AST를 제외한다. 연구 candidate와 실제 beta의 플래그를 구분했다.

첫 서명 빌드는 graph guard가 `stripBetaDebugSymbols`와 `mergeBetaNativeDebugMetadata`를
debug 변형으로 잘못 인식해 실패했다. 정확한 두 beta 작업 이름만 제외하여 수정했고,
실제 debug/release/preview 및 혼합 graph는 작업 실행 전에 거부되는지 별도로 확인했다.
실패 로그는 보존하며 재시도 성공만으로 처음부터 성공한 것처럼 기록하지 않는다.

## 시나리오와 결과

| ID | 의도·조건 | 실행과 기대 | 결과·근거 | 수정·정리 |
|---|---|---|---|---|
| PAYLOAD | 누락·변조 모델 또는 잘못된 배포물 | 최종 APK/AAB의 해시·고지·4 ABI·flags·서명 검사 | beta와 release 통과. 기존 beta24에 present 검사는 exit1로 실제 RED | 고정 기존 산출물 보존 |
| GUARD | 잘못된 속성·연구 속성 혼합·다른 변형 | 잘못된 값 1개, 연구 혼합 3개, 혼합 graph 1개 | 5개 모두 exit1, task 실행 없음 | 성공 빌드 방해 2개 task 오인만 수정 |
| WRAPPER | 다른 배포 목적에 AST 요청 | internal/paid-candidate + AST switch | 2개 모두 환경·서명키 처리 전 거절 | 키 출력 없음 |
| MODEL | 파일 부재·동일 크기 변조 | mergeBetaAssets 이전 모델 검증 실패 | 2개 모두 exit1, asset merge 미실행 | finally에서 원래 SHA 복구 |
| CONTROL | 일반 beta와 정식 release 오염 | AST payload 부재·실제 dex의 플래그 확인 | 일반 beta와 release APK/AAB 모두 통과 | 고정 배포 후보와 control APK 분리 |
| UPGRADE | beta23 사용자의 기존 문장·영상 | 데이터 삭제 없는 서명 업데이트 23→25 | ID·내용·LEGACY·MP4 SHA·beta 접근 보존, exit0 | 외부 fixture 제거 |
| ACCESS | beta 전체 접근·release 재잠금 | 실제 촬영/가져오기/보류/저장/PDF/기존 무료 내보내기 | 7개 시나리오 통과; exit0, 601.81초 | 화면 크기·글자 크기 복원 |
| ACTUAL_AST | 최종 서명 앱이 새 경로를 실행하는지 | 알려진 약국 원본 0–10초의 실제 UI | 실제 서명 UI에서 0–10초 옹알이 근거 확인; exit0 | 로컬 복사본 제거·원본 SHA 불변 확인 |
| LINT | 정적 오류·경고 | AST beta lint, 기본 release lint | 오류 0; Warning 20 / 19 | 경고 억제·숨김 없음 |
| REVIEW | 구현과 제한 범위 일치 | Spec 독립 검토 / Standards 부모 검토 | 새 P1/P2 명세 결함 없음; 검토 역할 구분 | 독립 Standards 재검토를 수행한 것으로 보고하지 않음 |

## 패키지

작업 증거와 고정 사본: ignored `work/ast-beta-package-01/`.
정식 release 파일은 정책 비교용이며 이 AST 개선을 포함한 정식 제품이 아니다.

| 파일 | bytes | SHA-256 |
|---|---:|---|
| `Agattok-0.5.11-beta-code25.apk` | 255,161,308 | `f42c15da0bf7a8fa9175d474df7c72546d99bdbedf3eac712ac0392d1462dee7` |
| `Agattok-0.5.11-beta-code25.aab` | 160,178,386 | `df15f1156a5117948a4bcf1c73b7bf93a502ca531733d8b4f61cb7e34b5a3fa4` |
| `Agattok-0.5.11-release-code25.aab` | 46,713,455 | `a0a401439d6728418dc79b5256962e9e32f93cc1cdcf9d13221203ea8728c2d1` |

- AST 모델: 91,134,123 bytes, SHA `3dff0cc2b374977ae1460258261608e33428f4767a6959cb8bc910b09433f71e`.
- ORT 1.24.3의 공식 MIT/third-party 고지 원문 SHA, 별도 출처 고지, 4 ABI의 native 8개를 검사했다.
- 실제 APK/AAB의 패키지명·code25·서명·beta/release manifest·backup 제외·native 정렬을 확인했다.
- 기존 beta24/release24의 모델·native 18개씩은 byte 동일하다. 추가 AST/ORT는 별도 payload 검사 대상이다.
- S25의 실제 device spec에 대한 bundletool 다운로드 예측은 **112,456,822 bytes (약 112.5MB / 107.2MiB)**다.
  Play에서 실제 관측한 설치 용량이 아니다. 용량 계산용 `.apks`는 debug 서명이며 설치·배포에 사용하지 않았다.
- 실제 dex: beta는 AST_BABBLE=true / AST_CANDIDATE=false / CLOSED=true / PREVIEW=false / DEBUG=false.
  release는 AST와 두 테스트 접근 플래그 모두 false. 일반 beta는 AST=false / CLOSED=true다.

## 실행 명령과 증거

아래는 저장소 루트 기준 경로다. 실기기·에뮬레이터 serial을 생략하지 않는다.

| 실행 | 제한 | exit·증거 |
|---|---|---|
| `build-play-bundle.ps1 -Purpose closed-test -AstBabbleSupport` | 상위 bounded 실행 | 첫 graph 오인 실패, 수정 후 exit0; `build-beta.log`, `build-beta-02.log` |
| `build-play-bundle.ps1 -Purpose paid-candidate` | 상위 bounded 실행 | exit0; `build-release.log` |
| `check_package.py --distribution beta/release` | 도구별 제한 | 각각 exit0; `beta-package/package-verdict.json`, `release-package/package-verdict.json` |
| `verify_ast_payload.py --expect present/absent` | 로컬 ZIP 검사 | 최종 beta present 2개 / release absent 2개 / 일반 beta absent 1개 통과 |
| 속성·graph·wrapper·모델 음성 검사 | 실행당 bounded | 의도한 exit1, `guards/result.json`, `guards/model-negatives.json`, `wrapper-negatives.json` |
| `:app:lintBeta -PagattokAstBeta=true` | bounded Gradle | exit0, 1분25초; `lint-ast-beta.xml` |
| `:app:assembleBeta :app:lintRelease -PagattokAstBeta=false` | bounded Gradle | exit0, 2분7초; `control-lint-result.json` |
| `signed_media_upgrade.py` | 700초 | exit0, 234.85초; `signed-upgrade/signed-media-upgrade-result.json` |
| `closed_test_ui.py` | 1,400초 | exit0, 601.81초; signed-closed/closed-test-ui-result.json |
| 알려진 원본 최종 UI | 360초 | exit0, 55.32초; signed-known/result.json |

인식과 성능의 선행 근거는 [통합 후보 QA](AST-INTEGRATED-CANDIDATE-QA.md),
[S25 AST 실측](S25-AST-20261004.md), [33입력 전후 비교](AST-PUBLIC-PAIRED-QA.md)에 있다.
이를 최종 패키지에서 다시 수행한 검사 수에 합산하지 않는다.

## 경고·한계

Lint는 AST beta 20개, 기본 release 19개 경고다. 공통은 ApplySharedPref 1,
GradleDependency 6, NewerVersionAvailable 1, UsableSpace 2, UseKtx 9다.
AST의 추가 경고 1개는 `NativeRunGuard`의 `scheduleAtFixedRate`다.
캐시 상태에서 깨어날 때 밀린 호출이 연달아 실행될 수 있다는 Android 경고다.
현재 callback은 종료 상태 검사와 terminate 요청만 하고, guard가 닫히면 timer를 취소한다.
화면 ON_STOP에서 분석 job을 취소하며 native 취소/복구 실측은 선행 QA에 있다.
이번에는 검증한 runtime을 그대로 유지했다. 장시간 캐시 상태에서의 최악 지연을 측정한 것은 아니다.

- 알려진 약국 원본의 회복은 개발 사례 하나다. 독립 정확도나 모든 옹알이의 회복이 아니다.
- 33입력에서 기존 승인 12개·보류 21개를 유지한 결과는 일반 오인율 수치가 아니다.
- S25 합성 전체 AV의 10/20초 처리 3.780/8.180초, thermal 전후 0/0은 단회 제한 측정이다.
  단색 영상이므로 복잡한 얼굴 추적·저사양 기기·장시간 발열을 보장하지 않는다.
- 영상 관찰은 시간순 표본과 구조화 단서다. 음성 화자와 얼굴의 동기·정체성을 확정하지 않는다.
- 문장은 현재 관찰에 대응한 작성 표현이다. 전체 AV 생성과 장면에 맞는 감정·욕구 추정은 미완료다.
- Gemma는 S25 의미 품질·발열·취소 지연이 미통과여서 포함하지 않았다.
- beta는 구매 없이 전체 기능을 제공하지만 영구 이용권을 만들지 않는다. 상품·실거래 검증과 공개 출시는 별도다.
- 최종 code25의 Play 업로드·split 설치·테스터 배포는 수행하지 않았다.

## 정리와 판정

위 첫 후보의 UI와 알려진 사례 검사는 모두 통과했다. 이후 사용자가 경고 해소를 요청하여
[경고 보완 작업](LINT-20261004.md)을 이어 간다. 위 해시는 경고 수정 전 파일이며 최종 전달본으로 쓰지 않는다.
전용 AVD와 격리 QA state는 같은 후속 작업에서 재사용하며 종료 시 정리한다.
전체 서비스 또는 생성 품질 완료가 아니다.
