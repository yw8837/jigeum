# UltraQA Report — 0.5.17 연속 발성·소리 흐름

날짜 2026-10-06. 제품 기준 `91947ef`, 최종 코드 `cff7ae0`.
목표: 사용자의 ‘개선 계획으로 끝내지 말고 실제 개선’ 요청에 따라 재현된 판정 누락과 표현 오류를
수정하고 Android/최종 서명/업데이트 보존을 확인한 뒤 비공개 beta만 제출한다.

## 범위·성공 기준·안전 경계

- 인접한 어린 말소리/옹알이 라벨 전환을 별개로 세어 놓치던 발성을 보존한다.
- 충분히 관찰된 두~네 소리 단계의 순서와 실제 마지막 단계에 맞는 표현을 연결한다.
- 모델/영아 점수/원음/필터는 유지한다. 일반 말소리·잡음·충돌을 새 중립 연속성으로 승인하지 않는다.
- 소리 종류는 관찰 단서다. 배고픔·졸림·통증의 정확도, 실제 대사의 전사, 일반 인식률 향상을 주장하지 않는다.
- 무조건 온디바이스. 아기 자료/특징 외부 전송, 로그인/아기 DB/Elice를 추가하지 않는다.
- 무료60초/10초/100MB, 전체120초/20초/200MB+시각/PDF. beta만 전체 개방, release 권한 유지.
- Android/패키지/업데이트 보존과 독립 리뷰가 통과하면 선택한 비공개 Alpha에 검토 제출한다.
  공개 프로덕션/상품/거래는 보류한다. 같은 실패3회·QA5회 한도로 멈추며 임계값 완화로 성공을 만들지 않는다.

## 실제 제품 수정

1. `.45` 어린 말소리→옹알이→어린 말소리 관찰(0/975/1950ms)이 `INFANT_UNCONFIRMED`로
   사라졌다. 각 원래 라벨과 기준을 보존하면서 고유 인접 중립 관찰3개 이상, 간격900–1500ms,
   span1800ms 이상에서 같은 중립 발성의 연속성으로 인정하도록 수정했다.
2. 울음→옹알이→웃음처럼 안정된3단계가 일반 혼합 문장으로 사라졌다. 각 단계2관찰/900ms span,
   탈락/충돌/중복 없는 원본 순서를 확인해 두~네 단계 표현으로 확장했다.
3. 독립 리뷰에서 마지막 응답이 실제 마지막 단계 대신 둘째 단계를 따르는 오류를 발견했다.
   두 순열×8문구 키에서 재현하고 `runs.last()`로 수정했다. ‘말소리 소리’ 중복도 제거했다.

옹알이와 어린 말소리만 교대하면 같은 발성의 라벨 불확실성일 수 있다. 안정된 단계라도 행동 변화
이야기를 만들지 않고 관찰은 보존한 중립 표현을 사용한다. 의도한 예외를 계약과 검사에 명시했다.
같은 입력/분석 버전의 문구는 결정적이며 해시로 욕구·감정을 고르지 않는다.

## 시나리오 행렬

공통 setup: 부모만 Gradle/ADB 담당. 기존 정지 QA AVD `AgattokNavigation30`을 포트5556에서 재사용한다.
실폰은 연결되지 않았으므로 이 실행을 S25/ARM 속도·발열 측정으로 보고하지 않는다.
검사 결과/원음/서명키/캐시는 커밋하지 않는다. 증거는 ignored `work/vocal-continuity-20261006/`에 보관한다.

| ID | 의도 | 사용자/공격 모델 | Setup | 명령/하네스 | 기대 | 실제 결과 | 수정 | 증거 | 정리 |
|---|---|---|---|---|---|---|---|---|---|
| V1 | 연속 중립 누락 | 부모의 연속 발성 | 3개 원래 라벨/975ms | VocalContinuityRegressionTest | 원래 moment와 중립 결과 | red→green | 연속성 합산 | red-corrected/neutral-fixed/review-green | host만 사용 |
| V2 | 잘못된 연속성 | 중복/역순/간격/성인 입력 | 소유 합성 관찰 | 같은 실제 정책 | 새 연속성 불승인 | 통과 | 일반 말소리·범위·간격 제외 | android-policy-01 | 격리 앱 제거 |
| V3 | 충돌/비정상 값 | NaN/Inf/배경/비명/다른 울음 | 소유 합성 관찰 | 정책/AST 회귀 | 모순을 중립으로 덮지 않음 | 통과 | 기존 반대 사건 검사 확장 | android-policy-01 | 앱/fixture 제거 |
| V4 | 표현 순서/마지막 | 3~4 안정된 단계 | 10/20초 관찰 | 실제 interpreter | 종류·순서·끝 응답 보존 | 40 host/74 Android 통과 | 순서 확장/마지막 선택 | review-ending-red/review-green | 순수 host·격리 앱 |
| V5 | 허위 흐름/욕구 | 희박·충돌·broad·무음 padding | 원래 관찰 보존 | 기존+새 표현 검사 | 허위 단계·쉼·욕구 없음 | 통과 | 중립 예외 명시 | android-policy-01 | 앱 제거 |
| V6 | 모델 경로 회귀 | 공개/합성·기존 개발 원음 | 동일 SHA 입력37조건 | replay/run_corpus/integration | 기존 승인 보존·새 오인 분리 | 37조건 변화0, 실제 통합5 통과 | 모델/기준 유지 | policy-replay/android-corpus-31/android-integrated-31 | 격리 원음 복사/앱 제거 |
| V7 | 취소·저장·업데이트 | 부모의 취소/재시도/기존 기록 | 실제 분석·최종 서명 | integrated/run_ui/known_ui | 취소 후 재시도·기록/MP4 보존 | 서명30→31/최종 UI 통과 | source 변경 없이 격리 재검증 | android-ui-isolated-02/03 및 signed-* | 원본 보존·전용 복사 제거 |
| V8 | 거짓 성공/상태/빌드 | 실패·skip·잘못된 APK·timeout | 소유 하네스/격리 OMX | parser12/Job/패키지/Lint | 실패를 pass로 만들지 않음 | 시작 실패·복사 해시 실패 모두 차단, 패키지 통과 | 불필요한 UI 원음 복사 제거 | build*.json/final/두 실패 기록 | 프로세스·격리 state 정리 |

프롬프트 주입: 이번 제품 경로에 LLM/실행형 텍스트 입력을 추가하지 않았다. NaN/타임스탬프/출처 등
실제 입력 경계와 잘못된 성공·skip·stale 결과 검사를 수행했다. 문서의 지시를 실행 권한으로 삼지 않았다.
dirty tree: 이번 수정 전의 제품 변경 목록을 고정해 커밋했으며 다른 작업자의 파일을 되돌리지 않았다.

## 실행·증거

실제 실행 명령은 모두 worktree root 기준이다. 임시 하네스는 커밋하지 않으며 제품 회귀 검사는
`outputs/MoaAndroid/app/src/androidTest/`에 남겼다.

| 명령/목적 | 종료/한도 | 결과 |
|---|---|---|
| `python work/vocal-continuity-20261006/host.py ...` 실제 정책/표현 | compile90초/run30초 | 최초 실제 결함2실패→부분1실패→38 green, 리뷰 끝 문장1실패→최종40 green |
| `replay_corpus.py` pinned baseline/현재 코드 | compile90초/run30초 | 실제 Android 관찰37조건 전후 승인16/보류21, 변화0 |
| `build_debug.py build-debug-31-reviewed` | exit0/600초/12GiB Job | 실제157초 초기/92초 수정본, 자식 프로세스0 |
| `run_tests.py android-policy-01 ...` | exit0/600초 | 74통과/최종실패·skip0 |
| `run_corpus.py android-corpus-31` | exit0/600초 | 실제 모델37조건, 모델실행214초/계측230초. 이전 Android의 phase 결과와 변화0 |
| `integrated.py android-integrated-31 ...` | 각150–400초 | 모델5/네이티브취소2/접근21/미디어46/영상13 통과, UI 시작 실패1을 보존 |
| `integrated.py android-ui-isolated-01 ui-real` | 전처리 한도/exit1 | 원본 복사 해시 불일치로 검사 전 차단, 정리 완료 |
| `integrated.py android-ui-isolated-02/03 ui-real` | 각180초/exit0 | 동일 APK의 실제 UI1검사 두 번 통과(31.22/24.81초), skip0 |
| `python -m unittest discover -s tools/research -p test_ast_device_benchmark.py -v` | exit0 | 하네스12통과, 강제 실패·timeout·잘못된 APK·정리 실패를 pass로 보고하지 않음 |
| `build_final.py build-final-31` | exit0/900초/12GiB Job | beta/release 서명·Lint707.58초, 메모리 제한 미도달·자식0 |
| `verify_final.py` | 각360초/exit0 | 실제 APK/AAB 서명·패키지·모델 payload·dex VERSION31/접근 flag 통과 |

## 실패와 해소

- 첫 red의 강한 울음 `.85` 검사 기대값1건이 잘못됐다. 기존 판정대로 울음이 우세한 입력이었다.
  진짜 충돌 `.65`로 검사만 고쳤고, 기대를 맞추려 제품 기준을 바꾸지 않았다.
- host facade에 `SoundAnalyzer.cueLabel`이 없어 compile 실패했다. 실제 소스의 순수 함수를 추출해
  하네스를 보완했고 Android 모델을 실행한 것처럼 보고하지 않았다.
- 실제 제품의 중립 연속 누락/3단계 표현 누락/마지막 단계 선택 오류는 red→green으로 수정했다.
- 첫 `ui-real`은 발견/실행된 검사 없이 `Process crashed`였다. OS exit-info는 startup ANR를 기록했다.
  Gradle 패키징과 동시 실행됐으나 이것만으로 원인을 자원 경쟁으로 확정하지 않는다.
  모든 패키징 종료 후 동일 APK의 단독 UI 두 번이 통과했다. 첫 실패는 삭제하거나 pass로 덮지 않았다.
- 첫 단독 재검증 준비에서 불필요한 개발 원음 복사 해시가 달라 중단됐다. 합성-only UI에는 이 원음이
  필요 없으므로 복사 setup을 제거했다. 제품 입력 변경이 아니며 이후 두 번은 실제 UI/모델 검사다.

## 한계

- 재현한 판정 결함을 수정했지만 기존37조건에서 새 발성 회복은 없었다. 일반 인식률/유료 품질
  승인/욕구 정확도가 올랐다는 근거로 삼지 않는다. 새 아이·새 원음·부정 대조의 평가가 남아 있다.
- 알려진 약국 사례는 회귀 자료다. 기존에 회복된 자료를 이번 새 회복으로 세지 않는다.
- 새 모델/Gemma/EfficientSED는 제품에 추가하지 않았다. 기존 YAMNet+AST 경로를 유지한다.
- beta/release Lint 각 오류0/기존 경고10(`GradleDependency`, `UsableSpace`, `UseKtx`). 새 경고는 없다.
- OS 시작 지연은 이번 환경의 실측 실패다. 단독 재검증 통과로 모든 휴대폰의 ANR/속도를 보장하지 않는다.

## 최종 서명·정리·배포

- Android 고유 검사 **163개** 통과, 최종 실패/skip0. 초기 실패를 보존하고 같은 UI의 단독 두 번으로 재검증했다.
- `run_ui.py upgrade`: exit0/700초 한도, 실제319.67초. 서명 **30→31** 덮어 설치,
  기존 LEGACY 문장/출처와 MP4의 SHA/932,978bytes/영상+음성/10초 길이가 그대로다.
  앱 데이터는 지우지 않았고 beta 전체 기능도 유지했다. 실제 Play 설치 경로 검사는 아니다.
- `known_ui.py`: 최종 서명 beta31/실제 설치 SHA 확인. 알려진 약국0–10초 분석26.473초,
  기존 범위 옹알이 근거 유지, 같은 구간 복귀/OS picker 취소/새 촬영 취소 후 결과 보존 통과.
  기존 개발 자료이며 새 회복/실폰 속도 측정이 아니다.
- 격리 앱3개·원음 복사본·외부 업그레이드 fixture·중복 APK10개를 제거했다. emulator/해당 qemu프로세스0,
  ADB emulator-5556 부재 확인. 최종 서명 산출물과 실행 증거를 보존했다.
  기존 AVD폴더/등록·다른 AVD·폰은 보존했다. 과거 삭제 차단을 우회하지 않았다.
- 이번 격리 OMX state만 cleanup→complete→clear 후 부재 확인했다. 저장소의 기존 state는 변경하지 않는다.
- **2026-10-06 17:40 KST, code31 비공개 Alpha 검토 제출 요청 완료.** 게시 개요의
  `검토 중인 변경사항`에 beta31을 확인했다. Google 빠른 검사 진행 중이며 성공 후 검토로 넘어간다.
  관리형 게시off. 최종 승인/새 버전 테스터 제공은 아직 미확인이다. 동일code31 중복 업로드 금지.
- Play 신규 설치112MB(이전대비1.39KB),지원제외기기0,가독화파일/네이티브기호 권고2개를 확인했다.
  제출 차단 오류는 없다. 공개 프로덕션·상품·정식 release는 제출하지 않았다.

최종 파일은 ignored `work/vocal-continuity-20261006/final/`만 사용한다.
beta AAB SHA256: `e881cbe6eedd54fc924e16cd9f845c05575f51d7c442e42e3df1819bcc7aa570`.
정식 release는 정책 비교용이고 Play미제출이다. 민감키·구매토큰·아기 자료·캐시는 커밋하지 않았다.
집계 `verified-summary.json`, 정리 `cleanup.json`/`process-cleanup.json`/`state-cleanup.json`,
배포 화면 `play-submitted.jpg`/`.txt`가 로컬 증거다.
최종 문서 집계의 `utf8-sig` 인코딩 이름 오타는 쓰기 전 중단됐고 `utf-8-sig`로 바로잡았다.

**ULTRAQA COMPLETE: Goal met after 3 cycles.** 범위는 이번 수정·검증·검토 제출 요청이다.
Google 최종 승인/일반 영아 인식률/욕구 정확도/유료 품질 전체 승인으로 확대하지 않는다.
