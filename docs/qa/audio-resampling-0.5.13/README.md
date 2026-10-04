# UltraQA Report — 영상 음성 변환 개선 · 0.5.13-beta/code27

검증일: 2026-10-05. 기준 `148614134533544a25fbaa4d854593ad38e8bf84`.
최종 서명 UI·패키지·업데이트 보존 검증을 완료하고 **2026-10-05 03:27 KST에 Play 비공개 Alpha 검토 제출**을 확인했다.
당시 Console은 자동 빠른 검사 진행 중이었다. Google 최종 승인·테스터 제공은 아직 확인하지 않았다.

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
| FINAL-UI / 최종 권한·캡처·분석·저장 | 실제 120초/20초·200MB·시각/PDF·정식 재잠금 | 7시나리오 통과. `signed-closed/process-result.json`, `process.log` | 대용량 fixture·복사 미디어 제거, 화면/글자 설정 복원 |
| SIGNED-REAL / 최종 beta의 알려진 옹알이 | 최종 서명 beta27에서 약국0–10초 실제 UI | 통과. broad babble 근거, 대기10.8초. `signed-known/result.json` | 기록 저장 안 함, QA 복사본 제거·원본 SHA 동일 |
| PLAY / 테스터가 업데이트를 받음 | beta AAB만 Alpha 제출·Console 게시 확인 | 03:27 KST 검토 제출, 자동 검사 중. 최종 제공 확인 대기 | 공개 출시 제외 |

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
- `run_ui.py closed`: exit0, 573.59초, 최종 서명본의 실제 UI 7시나리오 통과.
  작은 화면·200% 글자, 실제120초 촬영/20초 분석, 정확히200MiB 수락/+1byte 거부,
  실제 YAMNet/얼굴 관찰·영상/문장 저장·PDF, 재시작 후 접근/기록 유지,
  같은 서명 release의60/10/100·시각/PDF 잠금과 기존 기록/MP4 보존을 확인했다.
- `known_ui.py`: 최종 beta27의 알려진 약국0–10초 실제 UI 통과. 설치 SHA 검증·기록 미저장·원본 불변/복사본 제거.
- 연구 실행기 host27개 통과는 `combined-host.log`다. Android 계측48개와 서로 합산하지 않는다.

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

통합 후보 앱·시험용 원본 복사본·업데이트용 외부 파일·최종 UI 대용량 fixture 정리를 확인했다.
전용 제품 QA 앱을 제거하고 `emulator-5556`을 종료했다. 공식 avdmanager로 이 작업의
`AgattokAudio27`만 삭제한 후 해당 avd/ini 부재를 확인했다. 기존 `MoaQA35`는 유지했다.
03:29 KST 격리 UltraQA state를 complete로 기록·읽고 clear한 뒤 `exists:false`를 확인했다.
`cleanup-result.json`이 근거다. 다른 프로젝트 state와 S25 제품 앱/기록은 변경하지 않았다.
정리 검증 실행기의 첫 두 시도는 CLI read/clear 응답에 공통 success 필드를 잘못 기대해 실패했다.
실제 read의 phase와 clear의 cleared 필드를 검증하도록 수정 후 exit0를 확인했다. 제품 실패가 아니다.

**ULTRAQA COMPLETE: Goal met after 2 cycles** — 이번 제품 수정의 로컬 검증·정리 판정이다.
이 판정이 Google 승인, 일반 인식 정확도, 미측정 Gemma 실기기 품질을 대신하지 않는다.

이 수정은 입력 변환 품질 개선이며 일반 영아 의도 정확도, 성인·아기 화자 분리, 전체 AV 새 문장 생성을 입증하지 않는다.
S25는 현재 미연결이고 새 Gemma budget의 실기기 비교는 미측정이다.
EfficientAT/GTCRN의 실제 비교와 불채택 이유는 [후속 조사](../../research/20261005-noise-model-comparison.md)를 따른다.

## Play 비공개 업데이트

기존 code26에서 **0.5.13-beta/code27**만 비공개 Alpha의100% 대상으로 저장하고 검토 전송했다.
03:27 KST Console의 `검토 중인 변경사항`에 정확한 출시명을 확인했다.
세부 상태는 자동 빠른 검사 진행 중이며 성공하면 Google 검토로 전송된다는 안내였다.
관리형 게시 꺼짐을 유지했으므로 승인 후 자동 게시된다. 프로덕션·상품 등록은 하지 않았다.

- 지원 제외 기기0. 전화11,301·태블릿6,408 등 기존 지원 수 유지. 기종별 실행 품질 보장은 아니다.
- 신규 설치 표시112MB, 이전 대비942B. 업데이트 크기 표시2.51MB. 실제 기기별 다운로드는 다를 수 있다.
- 차단 오류0, 경고2: 가독화 파일 없음 / native debug symbols 없음.
  beta의 R8 난독화는 꺼져 있어 mapping이 생성되지 않는다. 외부 native 라이브러리 진단 기호 미첨부는
  향후 native crash 원인 분석의 제한으로 남으며 기능 실패2건을 뜻하지 않는다.
- `play-submitted.jpg`가 이번 제출 화면 증거다. code27 게시/개별 테스터 설치는 아직 미확인이다.
- beta AAB의 SHA는 위 패키지 절과 같다. release 비교본·연구 APK·아기 미디어는 제출하지 않았다.
