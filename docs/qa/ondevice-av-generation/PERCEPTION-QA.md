# UltraQA — 소리·영상 구간 관찰 개선

갱신: 2026-10-04. 기준 커밋 `72fe00aaeb75b5a036eb7a101bf281f60c6ade22`, 작업 브랜치 `codex/ondevice-av-probe`.

## 목표와 범위

사용자의 또렷한 옹알이 누락을 원음·전처리·모델별로 재현하고, 선택 구간 전체의 시간순 영상 관찰 경로를 구현·검증한다. 연구 도구의 정상 종료, 장면 적합성, Android 실행, 배포를 별도 판정한다. 문장 다양성이나 형식 통과를 인식 개선의 대용으로 사용하지 않는다.

원본·파생 미디어·개별 관찰문은 로컬 ignored `work/`에 보존한다. 공개 증거에는 익명 수치와 합성 자료만 넣는다. 외부 AI 호출, 제품의 권한 우회, Play 교체, 다른 작업의 프로세스 종료는 이 QA에 포함하지 않는다. 전체 앱 품질 통과는 이 표의 일부 성공으로 선언하지 않는다.

## 시나리오 행렬

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

**진행 중.** 시간순 영상 처리 구현과 관련98개 계측은 통과했으나 앱의 옹알이 회복·새 문장 생성·배포는 미완료다. 후속 Sol 검토, AST 보조 모델 Android 동등성·보정, 실제 앱 통합, 최종 전체 QA가 남았다. Android ARM 성능·배터리·발열·오프라인은 PC/에뮬레이터 성공으로 대체할 수 없다.

## Astra 단계 실행과 정리

- 최종 Android `assembleDebug assembleDebugAndroidTest` 성공: `work/ondevice-av/android-temporal-build5.log`,22초. 직접 실행한 계측 묶음은 `work/ondevice-av/run_android_temporal.py`와 `android-temporal-run4/result.json`:98통과/0실패/0제외,25.14초. 기존 `instrument.py`의 완료 수/종료/설치 SHA 검사를 재사용했다.
- 최초97개 중4실패와 다음98개 중1실패를 보존했다. 회전 사례는FFmpeg `-metadata rotate`가 display matrix를 만들지 않은 **fixture 오류**였다. `-display_rotation 90`과 실제 방향 검사로 수정했다. 나머지는3장→6장, 실제PTS로 바뀐 계약 기대값이었다. 부분 관찰의 기존 수치 안내는 생산 코드에 유지했다.
- 기존 설치와 서명이 달라 바로 업데이트가 거절됐다. 앱/테스트 APK의 복사본만 기존 키로 재서명하고 비서명 ZIP payload가 원래 빌드와 같은지 검증했다. 실제 설치된 앱·테스트 SHA도 일치했다. 키나 암호를 출력하지 않았고 앱 데이터 삭제는 하지 않았다.
- 부모가 최종 연구 테스트를 다시 실행했다: `python -m unittest discover -s tools/research -p 'test_*.py'`,26통과,exit0. `git diff --check` exit0. CRLF 정규화 안내는 코드 오류와 구분한다.
- 시작했던 에뮬레이터는 `emu kill`로 정상 종료, `--no-daemon` Gradle과 모델 추론도 종료했다. 증거·기존 앱 데이터를 보존했다. 부모의 isolated UltraQA state는 후속 검증을 위해 active다.

관련 근거: [실측 보고서](../../research/20261003-ondevice-av-probe.md), [실행 계획](../../superpowers/plans/2026-10-04-perception-recovery.md), [후보 gate](PROBE-GATES.md).
