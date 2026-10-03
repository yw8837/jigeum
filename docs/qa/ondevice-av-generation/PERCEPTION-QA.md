# UltraQA — 소리·영상 구간 관찰 개선

갱신: 2026-10-04. 기준 커밋 `72fe00aaeb75b5a036eb7a101bf281f60c6ade22`, 작업 브랜치 `codex/ondevice-av-probe`.

## 목표와 범위

사용자의 또렷한 옹알이 누락을 원음·전처리·모델별로 재현하고, 선택 구간 전체의 시간순 영상 관찰 경로를 구현·검증한다. 연구 도구의 정상 종료, 장면 적합성, Android 실행, 배포를 별도 판정한다. 문장 다양성이나 형식 통과를 인식 개선의 대용으로 사용하지 않는다.

원본·파생 미디어·개별 관찰문은 로컬 ignored `work/`에 보존한다. 공개 증거에는 익명 수치와 합성 자료만 넣는다. 외부 AI 호출, 제품의 권한 우회, Play 교체, 다른 작업의 프로세스 종료는 이 QA에 포함하지 않는다. 전체 앱 품질 통과는 이 표의 일부 성공으로 선언하지 않는다.

## 최초 시나리오 행렬 — 실행 과정 기록

아래의 대기 상태와 초기 검사 수는 최초 Astra 단계의 기록이다. 현재 판정은 뒤의 최종 재검사 표와 [Android AST QA](ANDROID-AST-QA.md)를 우선한다.

| ID | 목적·사용자/오류 상황 | 설정·실행 경로 | 기대 신호 | 실제 상태·수정·증거 | 정리 |
|---|---|---|---|---|---|
| IN-01 | 잘못된 구간·자료형·프레임 시간 | `python -m unittest discover -s tools/research -p test_ondevice_av_probe.py` | 모델 로드 전 거절, 유효 1–20초는 유지 | 기존19개 통과 이후 whole-interval/status 사례 추가. 최종 재실행 대기 | 임시 합성 파일만 정리 |
| IN-02 | 다중 음성 트랙으로 잘못된 downmix | 첫 stereo/둘째 default 5.1 합성 입력 | 모호한 입력 거절, 단일 stream 명시 선택 | 선행 Sol 검토에서 실제 소리 유실 재현 후 수정. 원본 제공 영상의 원인으로 일반화하지 않음 | 로컬 fixture 보존 |
| IN-03 | private 출력이 Git 대상 폴더로 새는 경우 | `docs/` 출력·경로 이탈·HTML 렌더 입력 | 쓰기 전에 거절 | 프로브/렌더러의 ignored work 경계 구현. 최종 리뷰에서 재확인 | 공개 파일에 사용자 자료 없음 확인 |
| IN-04 | 지연 음성·비영점 시작·회전 | FFmpeg 합성 컨테이너, 단일 공통 구간 | WAV 길이·PTS·회전 유지 | 선행 읽기 전용 검토 통과. 새 Android decoder는 별도 검증 필요 | 현재 앱 상태 유지 |
| RS-01 | JSON 형태만 성공하는 허위 통과 | 필수 필드 누락 응답을 stub으로 주입 | status invalid, 비정상 종료 | Astra가 generated/withheld 잔류 수정. 최종 테스트 대기 | 외부 모델 불필요 |
| RS-02 | 타임아웃·취소·낡은 결과 | 한정 timeout, 기존 출력 디렉터리 재사용 | 실패 명시, 새 결과로 이전 증거 덮어쓰지 않음 | 기존 디렉터리 거절 및 timeout 경로 있음. 동적 QA 대기 | 담당 프로세스·임시 state만 정리 |
| RS-03 | 고정 모델/전처리 신원 | weights/config/preprocessor 해시 불일치 | 추론 전에 거절 | AST weights 고정, 설정 파일 고정 검증 추가 중 | 모델은 Git 제외 |
| AU-01 | 또렷한 옹알이 누락 | 동일 owner2 첫10/20초, 현재/float YAMNet × raw/filter | 원인 분리, 라벨을 프롬프트에 넣지 않음 | 8조합 모두 insufficient. 원음의 신호를 앱 필터가 제거했다는 근거 없음 | 개발용 사례로 유지 |
| AU-02 | 후보 교체의 웃음·울음·성인 회귀 | AST, 기존 공개11원본과 tone/TTS | 각 사건별 결과 보존, 부정 대조 확인 | owner2 Babbling0.419, 일부 공개 옹알이·웃음/울음 혼동 잔존. **일괄 교체 미승인** | 미보정 점수를 정확도/음량으로 표현하지 않음 |
| AU-03 | 20초의 끝부분 누락 | AST 10초창/5초 overlap | 0–10,5–15,10–20초 모두 포함 | window coverage4개 테스트 및 실제 owner2 3창 실행 | 자료는 로컬 |
| AV-01 | 앞 장면만 읽는 출력 | 빨강→파랑 합성10초, 같은4K 문맥의 event/whole 비교 | 후반 변화와 순서 관찰 | whole10 정순/역순은 전체 순서 기술. 소리 누락/영상 내용 혼입은 실패 | 합성 응답만 공개 가능 |
| AV-02 | 소리를 영상에서 만들어내기 | 동일 tone의 audio-only/AV, 무음·역순 | 실제 tone 유지, 허위 말소리 없음 | 초기 tone→사람 말 오인. whole 음성만 입력에서도 클릭·시각 장면 환각. **의미 실패** | 형식 통과와 분리 |
| AV-03 | 긴 구간·메모리 한계 | 20초, 문맥 크기 명시, bounded native 실행 | 실제 전체 입력·정상 실행, 잘림 없음 | 일부16K 실패 때 PC commit98%;4K10초는 실행.8K20초는111.059초 후450토큰 한도·비JSON invalid. **품질 실패** | 다른 앱 종료 금지 |
| AN-01 | 최대5장 제한·근사 시간 | 순차 MediaCodec decode, 실제PTS,2fps, VIDEO tracker | 선택 구간 앞/중간/끝 관찰, 순서 유지 | 구현·관련 계측 통과.20초0..19500ms,10초 전환,비영점 구간,초중후/입 변화 방향 보존 | Gradle/ADB 단일 담당 |
| AN-02 | 취소·회전·불완전/다중 얼굴 | Android 합성 fixture·계측 | 자원 해제, 불완전 coverage 명시, 과도한 추정 없음 | 관련 계측 통과.취소 후 재실행·실제90도 display matrix·62×34 crop·공백과 다중 얼굴 처리 | 에뮬레이터 데이터 유지 후 정상 종료 |
| AN-03 | 기존 기능 회귀 | 관련 소리·시각·접근 정책·JSON/DB 회귀 | 기존 계약 유지 | 위 신규 검사 포함 최종98통과/0실패/0제외. 최종 전체 앱/서명 beta/기록 내보내기 UI는 후속 gate | 신규 beta 배포 별도 gate |

## 현재 판단

**ULTRAQA BLOCKED: 독립 인식·새 문장 생성의 의미 품질 미충족, 배포 범위 답변 대기.** 실행·회귀 검증을 마쳤지만 사용자 전체 목표를 완료하지 않았다. 영상은 구간 전체의 시간순 관찰로 바뀌었고 AST는 Android 연구 빌드에서만 평가한다. 일반화된 옹알이 회복·새 문장 생성·배포는 미완료다. ARM 성능·배터리·발열은 에뮬레이터 성공으로 대체할 수 없다.

## Astra 단계 실행과 정리

- 최종 Android `assembleDebug assembleDebugAndroidTest` 성공: `work/ondevice-av/android-temporal-build5.log`,22초. 직접 실행한 계측 묶음은 `work/ondevice-av/run_android_temporal.py`와 `android-temporal-run4/result.json`:98통과/0실패/0제외,25.14초. 기존 `instrument.py`의 완료 수/종료/설치 SHA 검사를 재사용했다.
- 최초97개 중4실패와 다음98개 중1실패를 보존했다. 회전 사례는FFmpeg `-metadata rotate`가 display matrix를 만들지 않은 **fixture 오류**였다. `-display_rotation 90`과 실제 방향 검사로 수정했다. 나머지는3장→6장, 실제PTS로 바뀐 계약 기대값이었다. 부분 관찰의 기존 수치 안내는 생산 코드에 유지했다.
- 기존 설치와 서명이 달라 바로 업데이트가 거절됐다. 앱/테스트 APK의 복사본만 기존 키로 재서명하고 비서명 ZIP payload가 원래 빌드와 같은지 검증했다. 실제 설치된 앱·테스트 SHA도 일치했다. 키나 암호를 출력하지 않았고 앱 데이터 삭제는 하지 않았다.
- 부모가 최종 연구 테스트를 다시 실행했다: `python -m unittest discover -s tools/research -p 'test_*.py'`,26통과,exit0. `git diff --check` exit0. CRLF 정규화 안내는 코드 오류와 구분한다.
- 시작했던 에뮬레이터는 `emu kill`로 정상 종료, `--no-daemon` Gradle과 모델 추론도 종료했다. 증거·기존 앱 데이터를 보존했다. 부모의 isolated UltraQA state는 후속 검증을 위해 active다.

관련 근거: [실측 보고서](../../research/20261003-ondevice-av-probe.md), [실행 계획](../../superpowers/plans/2026-10-04-perception-recovery.md), [후보 gate](PROBE-GATES.md).

## 최종 재검사 행렬 — Sol 통합 단계

| ID | 목적·오류 상황 | 실행/하네스 | 기대 신호 | 실제 결과·수정·증거 | 정리 |
|---|---|---|---|---|---|
| IN/RS | 손상 입력·시간·경로 이탈·타임아웃·형식만 성공 | `python -m unittest discover -s tools/research -p test_*.py` | 실패를 성공으로 보고하지 않고 로컬 경계 유지 | 43통과, exit0. 의도된 timeout 출력 포함. `work/ondevice-av/research-tests-ast.log` | 임시 합성 파일은 테스트에서 해제 |
| AN-FULL | 실제120초 촬영/녹음·후반 구간·기록/앨범·접근 회귀 | `android-ast-full1` 계측 | 전체 완료·실제 설치 해시·명시적 제외 구분 | 322통과/0실패/제외2,596.70초. v2/빌드 분리 전 기준 | 앱 데이터 유지 |
| AN-DEFAULT | 후보 없는 앱과 v2 정책·영상 변경 | `run_default_focused.py` | 기존 기능과6감정 하위 클래스 보류 유지 | 116통과/0실패/제외0,34.83초 | 기본 APK로 최종 복원 |
| AN-PROBE | 후보 수치·파일 해시·native 취소·동시 요청 | `run_probe_focused4.py` | 합성 logits 동등·취소 후 다음 실행 | 27통과/0실패/제외0,18.68초 | 연구 모델 캐시 제거 |
| PKG | 모델 누락·손상·금지 변형 | `check_ast_package_gates.py` | 연구만 모델 필수, beta/release/preview 연구 배포 차단 |6/6통과. 기본 APK/AAB AST·ORT 없음 | 원본 연구 모델 복원·해시 확인 |
| OFFLINE | 소리 없는 입력·같은 개발 원본10/20초 | `run_owner_offline_guard.py` → `run_owner_ast_offline.py` | 네트워크 없이 실행, 무음에 문장 생성 금지 | 하네스1통과/3사례 실행. 무음 보류, 후보2구간 회복. **quality_validated=false** | network 설정 복원·평가파일5개 제거 |
| SEMANTIC | 독립 source와 근거 없는 문장 | 사전 고정 공개 source·생성 템플릿 대조 | 새로운 자료에서도 회복, 없는 감정·욕구 금지 | 추가2소스6창 회복0, 생성 후보의 환각 잔존. 정확도 분모 아님. **품질 미통과** | 후보 비활성·새 모델 탐색 반복 중단 |

### 실행 명령·실패와 수정

Android 하네스는800초, 오프라인 평가는600초로 상한을 두었고 실제 종료 코드는0이다. Gradle 기본 빌드와 beta/release lint는 성공, 각각 오류0/경고19. 실패를 기대한5개 Gradle gate는 exit1과 지정 진단문을 함께 확인했으며 기본 모델누락 gate만 exit0이다.

첫 통합4실패와 다음1실패는 테스트 디렉터리·시스템 asset·합성 색상 메타데이터 설정에서 발생했다. 이를 수정한 후 전체 회귀를 통과했다. 코드 검토 P2두건은6개 감정 하위 클래스 보류와 모델/ORT 연구 빌드 분리로 수정·재검증했다. [두 축 검토](REVIEW-AST-20261004.md).

### 정리·잔여 위험

- 원본·개별 모델 응답·모델 가중치는 Git에 넣지 않는다. 실패/성공 증거는 로컬 ignored `work/`에 보존한다.
- 에뮬레이터 평가파일5개와 AST 캐시를 제거, 기본 앱/계측 APK 복원, 영구 파일6개 전후SHA 동일, 네트워크 설정 복원, 에뮬레이터 정상 종료를 확인했다.
- 격리된 UltraQA 실행 상태는 blocked 결과를 기록한 뒤 clear한다. 제품 goal의 완료와 구분한다.
- HDR/wide gamut 변환, ARM 실기기 지연·발열, 일반화·생성 품질, Play 배포는 미완료다. 이후 기본 경로의code24 서명 패키지·실제23→24 업데이트와7개 서명 UI는 [후속 QA](SIGNED-CANDIDATE-QA.md)에서 통과했다. 마지막 Console 확인은0.5.9-beta/code23 제공 중이다.
- 다음 담당은 부모 에이전트다. 사용자의 배포 범위 답변 후 검증된 범위로만 최종 패키지·업데이트·게시 검증을 진행한다. 전체 생성 요구를 임의로 축소하지 않는다.

공개 집계: [QA-EVIDENCE.json](QA-EVIDENCE.json). 전체322와 후속116/27은 겹치고 소스 시점이 달라 합산하지 않는다.
