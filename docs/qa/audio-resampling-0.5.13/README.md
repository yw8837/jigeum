# UltraQA Report — 영상 음성 변환 개선 · 0.5.13-beta/code27

검증일: 2026-10-05. 기준 `148614134533544a25fbaa4d854593ad38e8bf84`.
현재 최종 서명 UI를 검증 중이며 **Play에는 아직 제출하지 않았다.**

## Goal and success criteria

48/44.1/96kHz 원본을 16kHz로 변환할 때 고주파가 발성 대역에 접히는 결함을 수정한다.
원점·10/20초 길이·발성 대역을 보존하고 실제 MediaCodec, 옹알이 회귀, 취소,
업데이트 보존을 확인한 beta만 비공개 Alpha에 제출한다. 모델·승인 임계값·이용권 범위는 유지한다.

공개 프로덕션과 상품은 보류하며 원본·파생 미디어는 로컬에 둔다. 사용자 S25의 앱을 변경하지 않는다.
검사 기기는 이 작업에서 만든 `AgattokAudio27`이다. 같은 오류 3회 또는 QA 5회에서 해당 실행을 진단하고 멈춘다.

## Failures found and fixes applied

- 선형 보간에 anti-alias LPF가 없어 12kHz가 4kHz로 접혔다. Blackman windowed-sinc의 streaming 변환으로 수정했다.
- float Infinity가 clamp를 거쳐 ±1의 정상 PCM이 됐다. clamp 전에 각 채널의 유한성을 검사한다.
- 8kHz 10초 변환의 마지막 1sample이 빠졌다. EOS에서 ceil(N×16000/rate)를 출력 상한 내에서 flush한다.
- 16kHz에도 불필요한 선형 연산으로 수 ULP 변동이 있었다. native 16kHz는 직접 전달한다.

## Scenario matrix

아래 경로는 ignored `work/model-specialization-20261005/` 기준이다. 원본·개별 점수·APK는 Git에서 제외한다.

| ID / 사용자·실패 상황 | 실행기·기대 신호 | 실제 결과 / 증거 | 정리 |
|---|---|---|---|
| RED / 고주파가 모델용 음성에 섞임 | PcmResamplerTest 6개, 같은 tone/PCM | 기존 Android 5실패·1통과. 44.1k/12k alias 비율 .788, 4k 오차 .0052, tail/Infinity/16k 재현. `resampler-red/` | 전용 후보 APK |
| GREEN / 정상·손상 입력·chunk·형식 변경 | PcmResamplerTest 9개; 12k 차단 약50dB 이상, 300–6000Hz gain/원점, EOS·cap·재사용 | 9통과. `resampler-green/` | 내부 PCM 정리 |
| CODEC / 실제 MP4·누락 track·선택 후반·취소복구 | VideoAudioTest 9개 | 9통과. `resampler-green/` | fixture finally 삭제 |
| PRESERVE / 짧은 소리·DC·무음·클릭 | AudioCleanupTest 6 + AudioPresenceRegressionTest 3 | 9통과. `resampler-green/` | 입력 보존 |
| REAL / 약국 10/20초·실제 AV·UI·native 취소 | 통합5 + UI1 + native2 + timeline13 | 21통과. 약국 두 구간 모두 기존 옹알이 회복 유지, 전체 AV 4.534/8.926초. `resampler-integrated/` | 전용 후보 앱·private 복사본 제거 완료 |
| PACKAGE / beta 권한·AST와 정식 잠금 | 서명 APK/AAB·payload·실제 dex·정렬·lint | beta/release 모두 통과. lint 각각 오류0/기존 경고10. `final-package-result.json`, `*-package/` | 확정 사본 보존 |
| UPGRADE / 업데이트에서 기록·영상 손실 | 실제 서명 code26→27, JSONv1 기록과 저장 MP4 | 통과. 앱 데이터 삭제 없이 구형 기록과 MP4 SHA 동일, 전체 테스트 접근 유지. `signed-upgrade/` | QA 외부 파일 제거 |
| FINAL-UI / 최종 권한·캡처·분석·저장 | 실제 120초/20초·200MB·시각/PDF·정식 재잠금 | 진행 중 | 전용 에뮬레이터 |
| PLAY / 테스터가 업데이트를 받음 | beta AAB만 Alpha 제출·Console 게시 확인 | 미실행 | 공개 출시 제외 |

고유 Android 계측은 **48개 통과, 실패·제외0**이다. 서명 UI 검사는 별도 시나리오이며 계측 수에 더하지 않는다.
에뮬레이터 시간은 실제 휴대폰 성능 보장이 아니다. 약국 두 구간은 같은 알려진 원본이며 독립 정확도 표본 2개가 아니다.

## Commands run and evidence

- 기존 `build.ps1`: 후보 debug/test 빌드 exit0. RED를 보존한 뒤 수정본을 별도 사본에 고정했다.
- `run_tests.py resampler-red PcmResamplerTest`: 5실패를 재현. ADB exit0만으로 통과시키지 않았다.
- `run_tests.py resampler-green ...`: 27개 통과, 11.24초.
- `integrated.py resampler-integrated integration,ui-real,native-recovery,timeline`: 21개 통과, 설치 SHA·입력 SHA·정리 확인.
- `build_final.py`: 서명 beta/release 및 lint exit0, 322.61초. 남은 Gradle 자손을 bounded Job이 정리했고 메모리 제한 초과는 없었다.
- `verify_final.py`: 두 APK/AAB의 서명·구성·AST payload·권한 플래그·code27·정렬 통과.
- `run_ui.py upgrade`: 217.23초, 기록/MP4 보존과 정리 통과. Play 경유 설치 시험은 아니다.

## Code review

독립 두 축 검토 결과: **Standards 0건 / Spec 0건**.
첫 입력 뒤 rate 고정, phase1024 carry, 지연 보상, bounded ring, EOF 길이, finite-before-clamp를 확인했다.
정적 검토를 실제 실행이나 일반 인식 품질 검증으로 대신하지 않았다.

## 패키지

- beta AAB SHA256: `a6f5a98c7eabd394038011fc6eada61350bc3d618f30ed8bfa391157742b6f62`
- beta APK SHA256: `edd455e8a3fc4a6de93415a720452a5e1facc48b0d8d7784c35ab7e6d25ecbb3`
- beta만 AST·전체 테스트 접근을 포함한다. release 비교본은 테스트 플래그와 AST가 모두 꺼져 있다.
- 정식 release AAB와 연구 APK는 Play에 제출하지 않는다.

## Cleanup and residual risks

통합 후보 앱·시험용 원본 복사본·업데이트용 외부 파일 정리를 확인했다. 최종 UI와 AVD 정리는 진행 중이다.
이 수정은 입력 변환 품질 개선이며 일반 영아 의도 정확도, 성인·아기 화자 분리, 전체 AV 새 문장 생성을 입증하지 않는다.
S25는 현재 미연결이고 새 Gemma budget의 실기기 비교는 미측정이다.
EfficientAT/GTCRN의 실제 비교와 불채택 이유는 [후속 조사](../../research/20261005-noise-model-comparison.md)를 따른다.
