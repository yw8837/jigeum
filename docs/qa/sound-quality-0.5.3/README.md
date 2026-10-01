# UltraQA Report — 아가톡 소리·대사·노이즈 처리 0.5.3

2026-10-02 · versionCode16. **분석 개선과 기능 회귀는 검증했고, 출시 수준의 전체 해석 정확도 승인은 보류한다.** 사용자 테스트 파일은 `outputs/Agattok-0.5.3-test-code16.apk`. 전체 기능이 열린 sideload APK이며 Play 제출은 하지 않았다.

## Goal and success criteria

웃음에 슬픈 대사·울음에 신나는 대사가 붙는 모순을 줄이고, 유용한 응답률·보류를 함께 측정한다. 추가 연구를 실제 집계/문장/필터에 적용하고 접근·작동·UX만 검사하는 것으로 끝내지 않는다. [사전 행렬](PLAN.md), [개발/holdout 및 규칙 변경 이력](DECISIONS.md). 전용 API35 x86_64 기기만 사용, 원본 업로드·Cloud/Play 변경 없음. 최대5회 cycle/동일 실패3회 중단 원칙; 이번 실행은3 cycle.

## Failures found / fixes applied

1. **실제 웃음 불필요 보류:** 서로 다른 순간의 최고점이 합쳐져 강한 웃음에 약한 generic cry가 충돌했다. baseline actual model은 보류; 새 같은 PCM은 `아기 웃음`으로 출력한다.
2. **울음 쪽만 빠진 충돌 검사:** cry .8 + baby laugh .7/general laugh .95를 울음으로 선택하던 회귀를 red로 확보했다. 양방향 경쟁 점수·시간 창 판정으로 해결. [red](raw/red-regressions.txt).
3. **단발/혼합 과장:** 지속된2창 또는 강한1창을 요구한다. 실제 순간 변화와 동시 충돌을 나눈다. 혼합 후보는 `응애… 헤헤`를 전사처럼 붙이지 않고 중립 대사를 내며 울음이 포함되면 돌봄 안내를 유지한다.
4. **어린 말소리 활용:** exact Child speech가3창에서 확인되면 중립적인 말소리 대사를 허용한다. generic Speech만으로 대사를 생성하지 않는다. 영아 신원·연령 인증은 아니다.
5. **사용자 요청 노이즈 필터:** DC 제거+80Hz2차 Butterworth. 원음 재생은 보존하고 분류/리듬은 같은 필터를 사용한다. 게이트로 작은 발성을 잘라내거나 자동 증폭하지 않는다. DC만 있는 신호/비정상 PCM은 대사가 없다.
6. **독립 검토 수정:** 필터 분류와 raw 리듬 계산 불일치, API33 전용 Optional 호출을 수정. 비어 있거나 실패한 소리는 리듬 처리 전에 반환한다.

## 실제 소리 측정 — 정답률로 부르지 않는 이유

11개 공개 source와11개 합성/변형 case, 총22개. 출처·저자·권리·SHA는 [manifest](audio-manifest.json), [원문 조사](../../research/20261002-sound-appropriateness.md). 배포자 제목/설명이 사건 label이며 독립 청취/아기별 정답은 아니다. 원본은 앱·Git·QA ZIP에 없다. 변형을 독립 아기로 세지 않는다. 첫 추가 확인 뒤 정책을 수정했으므로 동일 데이터 재검사는 독립 holdout 성능이 아니다.

- 최종 **출력 8/22, 보류 14/22**. 음원 제목과의 엄격한 사건 비교는 **4개 불일치**를 남겼다. 계측 성공으로 이 불일치를 숨기지 않았다.
- 기존 웃음은 웃음 대사로 개선. 7개월 발성은 옹알이를 정확히 알아낸 것이 아니라 **어린 말소리**로 중립 대사를 낸다.
- 울음6개월 파일 일부는 모델이 웃음으로 잡아 혼합 결과가 된다. 새 혼합 문구는 감정을 단정하지 않지만 사건 분류가 정확하다고 주장할 수 없다.
- 짧은 Babbling Happy2, 일부 혼합 음성, 작은 웃음은 보류. 응답률을 더 높여야 한다.
- 대화·화자 미확인 웃음·피아노·청소기4개 source와 무음/톤/백색잡음/저주파/DC 대조군에서 대사 생성 없음. TV/큰 어린이의 실제 대조군 및 사용자 원본은 미확보.
- 필터 적용 전후 사건 출력 변화 **0/22**. 이번 소표본에서 뒤집힘은 없으나 **필터로 분류 정확도가 개선됐다는 증거는 없다.** 수치 회귀는40Hz 신호 감쇠와250–4000Hz의 작은 신호 보존·DC 제거를 검사한다. TV/부모 목소리 분리가 아니다.

| Case | 배포자 설명/합성 조건 | 필터 적용 결과 | 필터 전 모델 결과 | 실제 한마디 |
|---|---|---|---|---|
| cry | cry | 울음 | 울음 | 으앙… 잠깐 내 곁에 있어줘요. |
| laugh | laugh | 아기 웃음 | 아기 웃음 | 헤헤, 나랑 같이 웃어줄래요? |
| babble | babble | abstain | abstain | — |
| laugh_babble | laugh/babble mixed | abstain | abstain | — |
| almost_laugh | almost laughing boundary | 여러 아기 소리 | 여러 아기 소리 | 내 소리 좀 들어줘요. 잠깐 곁에 있어줄래요? |
| cry_six_month | cry | 여러 아기 소리 | 여러 아기 소리 | 나 여기 있어요. 내 목소리에도 귀 기울여줘요. |
| conversation | non-infant conversation | abstain | abstain | — |
| vacuum | vacuum | abstain | abstain | — |
| piano | music | abstain | abstain | — |
| generic_laugh | speaker-unverified laughter | abstain | abstain | — |
| babble_seven_month | babble | 어린 말소리 | 어린 말소리 | 나도 소리 내볼래요. 잠깐 들어줄래요? |
| laugh_quiet | laugh | abstain | abstain | — |
| laugh_then_cry | mixed | 여러 아기 소리 | 여러 아기 소리 | 잠깐 나를 봐줘요. 내 소리, 들려요? |
| cry_then_laugh | mixed | 여러 아기 소리 | 여러 아기 소리 | 잠깐 나를 봐줘요. 내 소리, 들려요? |
| overlap | overlap | abstain | abstain | — |
| silence | silence | abstain | abstain | — |
| tone | tone | abstain | abstain | — |
| noise | noise | abstain | abstain | — |
| laugh_with_rumble | laugh with 40Hz rumble | abstain | abstain | — |
| cry_with_dc | cry with DC offset | 울음 | 울음 | 으앙… 내 목소리 들려요? |
| rumble | rumble | abstain | abstain | — |
| dc | dc | abstain | abstain | — |

[최종22개 점수·시각·문장](raw/sound-final.json), [불일치/실행 해시](raw/sound-report.json), [최초 추가 확인](raw/first-holdout-sound.json), [이전 baseline](raw/baseline-sound.json). FFmpeg float overshoot는 Android decoder와 같은[-1,1] clamp로 정정했다. baseline cry와 새 cry는 clamp 때문에 바이트가 다르며 동일 파형 비교로 집계하지 않았다. 웃음은 동일 PCM이다.

## Commands run / Scenario matrix results

| ID | 실행/timeout | 실제 결과·근거 | 정리 |
|---|---|---|---|
| S1–S3 | sound_corpus.py; sound_probe.py(300초) | 실제 모델22개 완료, strict source 비교4불일치는 잔여로 유지. 실제 TV/큰아이 시험은 미완료 | 기기 내부 fixture 제거; 원본 재배포 없음 |
| S2 | SoundTimelineTest/EvidenceInterpreterTest | 시간 순서·양방향 충돌·일시 spike·generic 반대·중립 혼합·동일 입력 결정성 통과 | 테스트 대역은 androidTest |
| S4 | AudioCleanup/decoder suite | finite/길이/빈 입력·DC·필터 대역·손상 미디어·구간 경계 통과 | 임시 PCM0화 |
| S5 | Android 전체,1200초 상한 | **184개 중 181통과/3예상skip/0실패**, 352.7초 | 시험 앱 종료/제거 |
| S6–S7 | signed preview_ui.py | 실제 YAMNet+얼굴 미검출 관찰→저장→PDF 공유, cold start, 같은 서명 업데이트/기록 보존/release 재잠금 통과 | fixture/화면 복원 |
| S8 | 첫 동일UI 전체 시나리오 + 최종 핵심 재검사 | 720×1280·글자200% 동작/시각 확인. 최종2줄 실패 조기반환 변경 후 핵심 실제APK 흐름 재실행 |1080×2400/420/font1 복원 |
| S9 | unittest host gates |24/24통과; test ID·허용skip·timeout·설치 해시·누락성공 검증 | 로그 보존 |
| S10 | diff·Spec/Standards | 다른 작업자 소유 파일 보존·자료 지시 실행 없음·비밀/원음 미커밋 | 최종Git 확인 |
| Package | check_package.py,lint,bundlePreview | 서명/16KB/백업/18개 native-model 동일. debug/preview 각각0오류15경고. preview AAB는 의도한 오류로 차단 | Gradle/에뮬레이터 종료 |

3개 예상skip은 공개음원 opt-in(별도22개 실행), connected 설정과 양립하지 않는 결제 미설정2개다. 실제 Google Play 구매·환불 성공으로 보고하지 않는다. [전체 계측](raw/instrumentation-final.json), [로그](raw/instrumentation-final.log), [최종UI](raw/preview-ui-final.json), [초기레이아웃](raw/preview-ui-layout.json), [호스트](raw/host-gates.txt).

## Standards

[검토](standards-review.md): min26 테스트 호환1건 수정, 미해결 hard0. enum→표시문자열 재분기는 설계 참고1건. 실제 Android26 기기 시험은 아니며 API35 실행과 소스 검토다.

## Spec

[검토](spec-review.md): 필터/리듬 불일치 수정 승인. TV/큰아이 대조군과 실제 부모 청취평가는 남는다. 문장 다양성은 여전히 유한한 의미군이며 다른 입력에서 같은 문장이 나올 수 있다.

## Harness failures

첫 음원 주입은 ADB 소유 external 폴더의 EACCES로 실패해 app-private run-as 경로로 수정. 문서의 maxResults=-1은 실제 SDK1.0.0에서 거부되어521명시. timestamp는 Optional을 실제API에 맞춰 처리. 첫 strict 음원검사는 분류 불일치를 정상적으로 실패로 보고했다. 이들 실행을 모델 품질 통과로 바꾸지 않았다.

## Artifacts

| 파일 | bytes | SHA256 |
|---|---:|---|
| `Agattok-0.5.3-test-code16.apk` | 73,793,163 | `64617e5167d83efaf9fd15e5baf804447473786b84261df051f0b86eef414b70` |
| `Agattok-0.5.3-release-code16.apk` | 73,793,151 | `5c6c6c6a17eade2da492d54006f18aa33b003413fd3210894f597f2349897dfb` |
| `Agattok-0.5.3-release-code16.aab` | 45,164,785 | `fdd5fa8b486a39e701e1deeae1ff0aee491af3dd08a851fb996bb2be3675c0ad` |

테스트 접근은 비영속이며 자동 만료 없음. 정식 업데이트로 재잠그며 실제 Play 업데이트는code17 이상 필요. ARM 실기기/OEM 음질·발열·실제 아기/가정의 독립평가·거래·스토어 제출은 아직 남는다.

## Cleanup and rollback / Residual gate

최종 정리 결과는 [cleanup](raw/cleanup.txt)을 따른다. 시험 앱을 제거하고 화면 설정을 복구했으며 에뮬레이터를 종료했다. crash buffer는 0 bytes, 후속 adb devices는 비어 있다. OMX는 complete → read → clear → read에서 `exists:false`를 확인했다. present state가 `exists:true` 대신 상태 객체를 바로 반환하는 CLI 차이는 하네스 assertion을 수정하여 처리했다.

임시 `work/sound-quality-053` 삭제는 정확한 workspace 하위 경로와 증거 보존을 확인한 단일 시도에서 자동 승인 검토가 `blocked by policy`로 거부했다. 우회·재시도하지 않았고 원본/PCM·폐기 빌드·임시 스크립트는 Git에서 제외된 해당 폴더에 남는다. 이전에 차단된 `work/deep-audit`, `work/test-access-052` 삭제도 재시도하지 않았다. 원래 버전 산출물과 서명 키는 보존했다. [실제 UI 증거](screenshots/actual-model-result.png), [큰 글꼴](screenshots/font200-ready-without-preparation-tap.png), [영상 재생](screenshots/preview-recorded-video-playback.png)을 함께 보관한다.

**소리 해석의 출시 품질 gate는 부분 충족이다.** 이번에는 분석 자체를 개선·측정했지만 엄격 source-label 불일치와 응답률 한계가 남아, “모든 상황에서 정확” 또는 “유료 품질 검증 완료”로 선언하지 않는다. 후속은 사용자 장면의 로컬 재현, 별도 청취 라벨·가정/아기 단위 분할 및 TV/다른 아이 대조군이다.

`ULTRAQA BLOCKED: Full interpretation-quality gate remains partial; independently labelled real-home/TV/other-child evaluation is missing, and automatic approval review blocked temporary-directory deletion.`

담당: 개발 작업자는 추가 평가 corpus와 별도 청취 label을 확보해 현재 규칙을 재검증한다. 사용자는 전체 기능 테스트 APK에서 실제 장면·구간·출력 피드백을 남길 수 있다. 기능 회귀와 패키징은 3 cycle에서 검증했으며, 이 종료 상태를 전체 해석 정확도 통과로 바꾸지 않는다.
