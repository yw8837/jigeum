# UltraQA Report — 0.5.18 소리·영상 시간 연결

날짜2026-10-06. 기준 `737c0a8`(0.5.17/code31), 최종 제품 코드 `a8a3c18`.
Android·최종 서명·패키지·업데이트 보존 검증은 통과했다.
**ULTRAQA COMPLETE: Goal met after 3 cycles.** 이번 시간 연결 수정·검증·비공개 검토 요청 범위다.
2026-10-06 18:36 KST, code32의 비공개 Alpha 검토 제출 요청을 완료했다.
Google 빠른 검사·최종 승인·테스터 제공은 아래 배포 상태와 구분한다.

## 목표·성공 기준·안전 경계

선택 구간의 소리와 시각 관찰을 같은 시간창에서 연결하고 여러 소리 단계에도 반영한다.
시간이 어긋난 표정·누락·오인·비정상 timestamp로 시각 근거를 만들지 않는다.
소리 게이트/모델/음성 원본·필터/500ms 시각 cadence/이용권 정책은 유지한다.
최대 QA5회/동일 실패3회에서 중단하며 아기 자료 외부 전송·공개 출시·상품·거래는 금지한다.
부모만 Gradle/ADB를 사용한다. 재사용 QA AVD만 대상으로 하고 사용자폰·다른 작업은 보존한다.

## 시나리오 행렬

| ID | 의도 | 사용자/공격 모델 | Setup | 명령/하네스 | 기대 | 실제 | 수정 | 증거 | 정리 |
|---|---|---|---|---|---|---|---|---|---|
| V1 | 소리와 표정 시점 | 앞소리/뒤표정 | 실제 정책·합성 관찰 | VisualTemporalAlignmentTest | 다른 시간표정 제외 | 최초5중4실패→관련28통과 | 시간창 연결 | baseline-red/fixed-green | host 격리 |
| V2 | 여러 소리 흐름 | 옹알이→웃음 | 실제 interpreter | 같은 검사 | 각각 연결·소리 보존 | 통과 | 다중단계 연결 | final-host-green/android-visual-01 | fixture 없음 |
| V3 | 비정상 입력 | 중복/역순/누락PTS/NaN/다중얼굴 | 소유 관찰 | 순수/Android 검사 | 근거 없음 | 통과 | timestamp 재검증 | android-visual-01 | 앱 정리 |
| V4 | 누락/간격/무음 | 화면밖·성인·AST broad | 소유 관찰 | 같은 검사 | 억지 발성/립싱크 없음 | 통과 | local run | android-visual-01 | 앱 정리 |
| V5 | 실제 영상/취소 | 10/20초·nonzero·crop/회전 | 실제 codec/MediaPipe | VideoTimeline/VisualFeatures/Audit/Color | 전체 관찰·취소재시도 | 통과 | decoder 유지 | android-visual-01 | fixture/bitmap/codec 정리 |
| V6 | 통합·UX/기록 | 이전 기록/MP4·새구간·취소 | 실제 앱/서명APK | integrated/signed-upgrade/known_ui | 보존·정상 분석 | 최종서명/31→32 보존/실제UI 통과 | 분석만 변경 | android-integrated-32/signed-upgrade-final/signed-known-final | 복사본/격리 앱 제거 완료 |
| V7 | 빌드/권한/거짓 성공 | wrong APK/skip/timeout | 소유 하네스 | bounded build/parser/패키지 | 실패는 차단·beta/release 분리 | 통과 | 버전32 | build-final-32/final-package-result | 자식프로세스0 |
| V8 | 오디오 PTS 대응 | delayed track/공백/겹침/누적drift | 합성 MP4 실제codec 및 실제 audit | AudioTimestampAudit/VideoAudioTest | false alignment 차단·원음 보존 | drift red→green/실codec 통과 | global keptPCM clock | drift-red/final-host-green/android-visual-01 | 합성fixture만 보존 |

프롬프트 주입은 새 실행형/LLM 입력이 없는 범위다. 실제 데이터·타임스탬프·상태 경계를 검증한다.
작업 전 tree clean/HEAD737c0a8. 기존 문서/원음/다른 작업자의 변경을 보존한다.

## 실제 변경·실패와 해소

1. 구간 전체의 웃는 모양/입 변화를 합산해 앞쪽 소리에 뒤쪽 표정을 붙이거나,
   얼굴이 사라진 사이의 두 웃는 표본을 합쳤다. 여러 소리 단계에서는 시각 표현을 아예 제외했다.
   실제 interpreter5검사 중4실패로 재현했다. 이제 승인된975ms 소리창의 이어진 단일 얼굴 관찰만 연결한다.
2. 독립 Standards 리뷰가 기존 열린 입 상한1f 누락을 찾았다. 기존 범위를 복원하고1.2 반례를 검사했다.
3. 독립 Spec 리뷰가 crop된 PCM의 시각과 실제 영상 PTS의 불일치를 찾았다. 원음은 유지한 채
   실제 kept output buffer PTS를 확인해 제품 연결로 전달한다. 오디오 시작이2.978667초인 합성
   MP4의 전체 구간은 불일치,3200–4800ms crop은 일치함을 실제 Android codec으로 확인했다.
4. 한 sample 이하의 작은 공백/겹침을 매번 용서하면 누적drift가 생기는 추가 결함을4검사 중1실패로
   재현했다. 선택 시작+누적 kept source frames의 전체PCM clock을 기준으로 바꿔 최종 통과했다.

얼굴·소리의 주인, 정확한 발성 시작, 입술 동기나 욕구를 입증하지 않는다.
PTS 없는 구형 관찰의 요청형 표시를 유지하며 새 시간 일치 근거로 쓰지 않는다.
AST broad 창은 정확한 발성 시점이 없으므로 새 temporal 연결에서 제외한다.

## 실행·증거

루트는 기존 ondevice-av-probe worktree. 임시 하네스·실행 결과는 ignored
`work/visual-alignment-20261006/`에만 보관한다. 아기 원음·특징·서명키·캐시는 커밋하지 않는다.

| 명령/목적 | 종료·한도 | 실제 결과 |
|---|---|---|
| `host.py baseline-red VisualTemporalAlignmentTest` | exit1/compile90s/run30s | 실제 정책5중4실패 |
| `host.py drift-red AudioTimestampAuditTest` | exit1/동일한도 | 누적drift4중1실패 |
| `host.py final-host-green ...` | exit0/동일한도 | 관련43검사 통과 |
| `build_debug.py build-debug-32-final` | exit0/600s/12GiB Job |42.53s, limit hit없음/자식0 |
| `run_tests.py android-visual-01 ...` | exit0/600s | 실제 codec/모델/정책73검사,28.33s,fail/skip0 |
| `integrated.py android-integrated-32 integration,ui-real,ui-access,media` | 각180–400s/exit0 |5+1+21+46통과,fail/skip0,격리 앱·복사 제거/기존 제품 상태 보존 |
| `summarize.py` | exit0 | 로그의 고유 class/test146개 확인, 중복0 |
| `build_final.py build-final-32` | exit0/900s/12GiB Job |526.81s,메모리 limit hit없음/자식0 |
| `verify_final.py` | exit0/각360s | 실제 APK/AAB 서명·버전32·payload·dex beta/release 분리 통과 |
| `run_ui.py upgrade` | exit0/700s |265.65s,서명31→32/LEGACY문장·출처·MP4 bytes 보존 |
| `known_ui.py` | exit0/각UI bounded |최종서명 beta 실제약국10초 UI22.452s/재진입·OS선택·촬영취소 결과 보존 |

최종 고유 Android **146개** 통과(실패·제외0). Host43개와 Android개수를 합쳐 모델 정확도처럼 보고하지 않는다.
기존 약국0–10/20초의 실제 `DeviceClipAnalyzer` 처리5.569/10.051초, 시각20/40관찰·순서·coverage를
확인했다. 에뮬레이터/기존 개발 자료이며 S25/ARM 실측이나 새로운 발성 회복이 아니다.
소리 모델/게이트/필터/리샘플러·순차 영상 디코더·FaceLandmarker 경로의 기존 소스와 baseline의
일치를 확인했다. 새 소리 corpus 추론을 반복해 새 회복으로 세지 않았다.

독립 리뷰의 최종 Standards/Spec 필수 수정0. 비차단 루프 중복 제안1은
[코드 리뷰](CODE-REVIEW.md)에 기록한다.

## 한계·잔여 검증

- 시간 연결 결함을 수정했다. 새 아이/새 원음·사건 정답을 이용한 일반 시각/발성 정확도 검증은 아니다.
- 현재 제품은 YAMNet+AST+MediaPipe 관찰이다. Gemma/새 영상 생성·욕구 모델은 추가하지 않았다.
- 정상 얼굴의 blink/smile 계수는 행복·졸림 확률이 아니다. 울음 라벨을 웃는 화면으로 행복으로 바꾸지 않는다.
- 실제 휴대폰 기종별 지연/메모리/발열은 이번 에뮬레이터 실행만으로 보장하지 않는다.
- 실제 Play 설치/구매·환불 경로는 이번 sideload 서명 업데이트 검사를 대신하지 않는다.

## 최종 파일·정리

최종 파일은 ignored `work/visual-alignment-20261006/final/`의 beta32 APK/AAB다.
정식 release 비교 파일은 Play에 제출하지 않는다. beta는120초/20초/200MB·시각/PDF를 개방하고,
release의 두 테스트 접근표시는false, 실제 entitlement 경계는 유지한다.
beta AAB SHA256 `304e8581c026e6f1da38ca03cc3364be744db6a76f5abed17e9da2415663c20f`.
Lint각 오류0/기존경고10. 기존 compiler의 INFO_OUTPUT_BUFFERS_CHANGED deprecation도 남아 있다.
경고가 모두0이라고 보고하지 않는다.

서명31→32에서 기존 LEGACY문장·출처와 MP4 SHA·932,978bytes·영상+음성·10초 길이를 보존했다.
앱 데이터는 지우지 않았고 beta 전체 기능도 유지한다. 실제 Play 업데이트 경로의 확인은 아니다.
최종 실제 원음 UI는 기존 AST broad 옹알이 결과를 유지했다. 이를 새로운 시각 인식률 향상으로 세지 않는다.

QA앱3개·별도원음/외부 fixture복사·중복APK4개 제거, emulator/관련qemu프로세스0 확인.
기존 AgattokNavigation30 AVD폴더/ini는 재사용·보존했고 사용자폰·다른AVD는 변경하지 않았다.
Lint집계/정리용 inline Python의 인용 오류2건은 실행 전 실패했다. 제품 결함이 아니며
수정한 집계와 범위 검증된 별도 정리 script로 각각 최종 확인했다. 첫 오류를 성공으로 덮지 않았다.
격리 QA state는 complete(3회)→clear 후 부재를 확인했다. 저장소 기존 state는 변경하지 않았다.
최초 state helper는 clear 응답의 `cleared`를 `success`로 읽어 KeyError로 중단했다.
complete 기록과 실제 clear는 이미 실행됐고, CLI 소스의 응답 계약을 확인해 helper를 수정했다.
재삭제/상태 재생성 없이 `verify-cleared`(exit0)로 `exists:false`와 mode를 확인하고 증거를 남겼다.
첫 PowerShell 호출은 뒤의 Get-Date 때문에 전체 exit0이었지만 Python 실패를 출력에서 발견했다.
이 하네스 오류를 제품 QA 성공으로 세지 않으며 최종 부재 확인은 독립 명령으로 실행했다.

## 배포

code31은10월6일18:13 게시/선택한테스터제공 상태를 트랙 상세에서 직접 확인했다.
**code32는10월6일18:36 KST, 비공개 Alpha 검토 제출 요청을 완료했다.**
Console의 `검토 중인 변경사항`에 `0.5.18-beta (32) - visual timing`을 직접 확인했다.
현재 Google의 빠른 검사가 진행 중이며 성공 후 검토로 전송된다. 최종 승인/새 버전 테스터 제공은 미확인이다.
관리형 게시off다. Play 신규설치112MB/업데이트2.55MB, 이전대비 지원제외기기0을 확인했다.
경고2개는 가독화 파일·네이티브 디버그 기호 업로드 권고이며 차단 오류는 표시되지 않았다.
증거는 ignored `work/visual-alignment-20261006/play-submitted.txt`, `play-submitted.png`,
`play-warnings32.txt`, `state-complete-read.json`, `state-absent.json`이다.
같은code32를 중복 업로드하지 않는다. 공개 프로덕션/상품/실제 거래는 보류한다.
