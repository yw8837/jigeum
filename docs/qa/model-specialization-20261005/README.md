# UltraQA — 모델 비교 실행기와 후속 제품 수정

2026-10-05. 기준 `148614134533544a25fbaa4d854593ad38e8bf84`.
사용자 지시에 따라 실제 모델 비교를 수행하고, 발견한 제품 변환 결함을 별도로 수정했다.
[모델 실행 결과](../../research/20261005-noise-model-comparison.md),
[제품 QA](../audio-resampling-0.5.13/README.md)가 실제 추론과 배포 상태의 기준이다.

## 목표와 범위

- Gemma 시각 budget을 바꾸되 원음·장면 수·시간축을 보존한다.
- EfficientAT와 GTCRN을 고정된 같은 입력으로 실행하고, 효과가 없거나 발성을 약화시키면 채택하지 않는다.
- 실제 원본과 파생 결과를 로컬에 보존한다. 모델 파일·사용자 미디어·개별 점수는 커밋하지 않는다.
- native 기기 비교를 못 했거나 모델이 실패한 경우 이를 품질 통과로 세지 않는다.

## Scenario matrix

| ID / 의도·실패 상황 | 실행·기대 신호 | 실제 결과·증거 | 정리 |
|---|---|---|---|
| GB-HOST / budget 인자·입력 보존 | test_gemma_android_probe.py | RED 인자 미지원 1건 후 host17개 통과. 0/70/140/280, AV 제한, stale 결과 거절 | 임시 fixture 자동 삭제 |
| GB-BUILD / SDK pin·연구 배포 경계 | 별도 Gradle debug/release | debug 성공; release 요청은 명시적 연구 배포 금지 오류로 거절(exit1) | 연구 APK는 Play 미제출 |
| GB-NATIVE / 실제 속도·발열·품질 | S25, 같은 합성 10/20초와 budget 비교 | 기기가 연결되지 않아 미실행. 연결 요청은 이미 전달함 | 이번 S25 설치 없음 |
| EA-PIN / 고정 source·weights·runtime | strict weights_only loading, corpus hash | source/weight/runtime pin 일치, host5개 통과 | 임시 source·model Git 제외 |
| EA-PAIR / 작은 모델의 옹알이 회복 | 공개·합성33개 + 약국10/20초 | 실제 추론 완료. 알려진 회복을 대체할 근거 부족으로 채택 보류 | Job 자손0·cleanup true |
| GT-DSP / 전체 구간·cache·손상 입력 | test_gtcrn_probe.py | 초기 NOLA/끝 반올림 계약 보정 후 host5개 통과 | 입력 불변 |
| GT-PAIR / 잡음 제거 후 발성 유지 | 공개·합성33개 + 약국10/20초의 AST쌍 | 기존 옹알이 단서 약화. 기본 필터 채택 보류 | 두 Job 정상 종료·자손0 |
| PRODUCT / 실제 입력 변환 결함 | Android RED→GREEN, 실제 codec·AV·UI | [0.5.13 QA](../audio-resampling-0.5.13/README.md)에서48개 통과 후 패키징 진행 | 전용 AgattokAudio27 |

## Commands and failures

host 세 모듈 합계27개를 현재 파일로 재실행해 통과했다(`combined-host.log`).
EfficientAT 최초 corpus duration의 floor/ceil 불일치는 모델 실행 전 harness 실패였다.
GTCRN 최초 부분-hop COLA 조건과 float32 복원 한계는 DSP harness 결함으로 분리해 수정했다.
네트워크를 차단한 bounded process에서 actual inference exit0·complete case 수·자손 정리를 확인했다.

## Code review

Gemma/EfficientAT, GTCRN 각각 두 독립 축 검토: **Standards0 / Spec0**.
GTCRN의 현재 결과가 AST쌍이라는 범위를 명시했으며 YAMNet·Android 품질 통과로 확대하지 않았다.
제품 resampler도 Standards0 / Spec0이며 제품 실행 결과와 분리했다.

## Residual risks and status

연구 실행기의 host 검증과 모델 실행은 완료됐다. 후보들의 일반 정확도/상용 Android 채택은 승인하지 않았다.
**Gemma native budget 비교는 S25 미연결로 미완료**다. 연구 전체의 품질 성공을 선언하지 않는다.
제품 수정은 별도 QA와 비공개 업데이트 흐름으로 계속 진행한다.
