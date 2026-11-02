# 고정 AST 후보의 baseline→candidate 변화 감사

2026-10-04. 기존 측정 JSON만 읽는 로컬 연구 감사다. 모델 추론·원본 미디어 읽기·threshold 변경·ADB/Gradle·제품 활성화는 포함하지 않는다. 개별 점수·문장은 이 문서와 코드에 복사하지 않는다.

## 구현 계획과 경계

`superpowers:writing-plans`의 계획 위치는 이번 지정 소유 파일인 이 문서로 제한한다.

**목표:** 고정 결과의 추가 승인, 기존 승인 유지/손실, 보류 유지와 baseline 미확인을 구분한다.

**구조:** Python 표준 라이브러리로 기록을 정규화하고 원본 SHA·출처 그룹·구간·실행 방식·정책 provenance별로 집계한다. 기존 실제 Kotlin 정책 적용 전후 기록을 읽으며 정책 수식을 별도 Python threshold로 복제하지 않는다. 공개 AST 점수 파일에 없는 YAMNet baseline이나 fused candidate를 만들지 않는다.

**담당 파일:** `tools/research/ast_candidate_delta.py`, `tools/research/test_ast_candidate_delta.py`, 이 문서. 변경·커밋은 부모 작업자와 통합하며 다른 파일은 수정하지 않는다.

- [x] 합성 기록에서 추가 승인·승인 유지·승인 손실·보류 유지·AST-only unknown·라벨 강도·겹친 구간·정책 불일치의 실패 테스트를 먼저 실행한다.
- [x] Android 실제 전후, 보존 PCM의 기존 v2 재생 전후, 공개 AST-only의 metadata adapter를 구현한다.
- [x] 현재 정책 소스 해시와 보존 재생의 해시를 대조하고, v1/v2·해시 미확인 층을 합산하지 않는다.
- [x] 원본 그룹으로 중복을 묶고 mode별 source/segment 수와 변화 집계를 검증한다. raw timeline과 filtered aggregate를 독립 성공으로 합산하지 않는다.
- [x] 기존 JSON으로 도구를 실행하고 집계만 이 문서에 기록한다. source별 변화표는 ignored work 아래에 둔다.

## 해석 원칙

- baseline에서 이미 승인한 울음·웃음의 유지와 AST가 추가 승인한 구간은 별도다. 기존 오인도 후보가 새로 만든 오인과 구분한다.
- 사용자 약국=옹알이 확인은 **개발 사례의 사용자 라벨**이다. 독립 source 정답으로 승격하지 않는다.
- 원문 제목/업로더 설명은 weak label이다. source-title 불일치만으로 확정 오인율을 만들지 않는다. 불명확한 사용자 발성 보고도 정확한 옹알이 정답으로 치환하지 않는다.
- 공개 AST-only 결과에 fused baseline이 없으면 효과는 unknown이다. 추가 승인 0이나 독립 회복 실패 0으로 만들어 계산하지 않는다.
- 같은 원본의 겹친 선택, 여러 창, 다른 replay mode는 독립 분모가 아니다. source grouping과 mode 층을 모두 남긴다.
- 개발 약국 회복을 신규 독립 양성 부족만으로 무효화하지 않는다. 그 사실과 신규 일반화 근거 부족을 각각 기록한다.

## 입력 경로

- `work/ondevice-av/android-owner-probe-02-offline/{results-private.json,execution.json}`: 실제 Android 전후. 결과 JSON에 정책 source hash/version 직접 필드는 없어 문서상 v2와 hash 미확인을 함께 표시한다.
- `work/retained-pcm-ast-check/{results-private.json,input-audit-private.json}`: 이미 실행한 현재 v2의 raw timeline / filtered aggregate fallback 재생. PCM을 다시 읽지 않는다.
- `work/public-perception/{int8-frozen-01,int8-target-age-01}/scores.json`와 attribution/manifest/cases metadata: 11소스30창의 AST-only 측정. YAMNet 기준 판정·실제 fused 판정은 기록되지 않았다.

## 기록된 변화 결과

| 기록 층 | 원본 수 | 기록 수 | baseline→candidate 변화 | 정책 출처 |
|---|---:|---:|---|---|
| 실제 Android 개발 사례 | 2 | 3 | 추가 승인 2, NO_AUDIO 유지 1 | 문서상 v2, 결과 JSON 자체의 정책 해시 미기록 |
| 보존 raw timeline 재생 | 2 | 3 | 기존 승인 유지 1, 보류 유지 2 | 현재 v2의 4개 Kotlin 소스 해시 일치 |
| 보존 filtered aggregate fallback 재생 | 2 | 3 | 기존 승인 유지 1, 보류 유지 2 | 같은 v2·같은 소스·다른 재생 방식 |
| 공개 AST-only 개발 사례 | 1 | 1 | unknown 1 | 문서상 v1 점수 sweep, fused 기록 없음 |
| 공개 AST-only 신규 사례 | 10 | 10 | unknown 10 | 같은 AST-only 측정 |

총 20개 기록은 15개 원본, 17개 고유 선택 구간이다. 겹치는 구간 쌍은 2개다. raw/fallback 중복을 제거한 구간 집계이며, 이 수를 정확도 분모로 사용하지 않는다. 공개 11개 소스의 AST 창 30개도 30개 독립 사례가 아니다. 앞서 개발에 쓴 약국 원본 1개·구간 2개는 보존 재생 신규 집계에서 제외됐다.

**약국 회복은 기록된 개선이다.** 같은 원본의 10초·20초 선택 모두 기존 `INFANT_UNCONFIRMED`에서 `BABBLE` 승인으로 바뀌고 해석이 생성됐다. 사용자의 옹알이 확인과 일치하는 개발 사례 1개에서 두 선택을 회복했다. 독립 양성 부족이 이 전후 사실을 없애지는 않는다. 결과 JSON에는 정책 해시가 직접 기록되지 않아 해시 검증 v2 재생과 별도 층으로 유지한다.

**보존한 다른 원본의 발성 보류는 남아 있다.** 서로 겹치는 선택 2개가 raw/fallback 모두 보류를 유지했고 지원 창도 추가되지 않았다. 정확한 옹알이 독립 정답이 없어 이를 확정 옹알이 미탐률로 계산할 수는 없다. 다른 원본의 기존 `CRY` 승인은 두 재생 방식에서 그대로 유지됐다.

독립 exact label이 있는 전후 기록은 0개다. 확인된 새 오인 수는 `null`이며, 새 오인이 없다는 검증값 0이 아니다. 관찰된 기존 승인 손실·승인 종류 변경·blocked 입력 승인은 0개다. 공개 AST-only 11개는 baseline과 fused candidate가 모두 없어 효과가 unknown이다. 현재 자료로 일반화 정확도나 새 오인율을 산출하지 않는다.

## 실행·검증

도구는 점수 JSON의 형식·모델 해시·cases metadata 해시를 확인하고, Android/보존 재생에 기록된 판정만 사용했다. 정책 재실행·모델 추론·원본 미디어 읽기는 모두 0회다. 점수와 문장은 출력 변화표에 복사하지 않았다.

```powershell
python -m unittest discover -s tools/research -p test_ast_candidate_delta.py -v
python tools/research/ast_candidate_delta.py --output work/ast-delta-audit/run-02
```

합성 테스트는 최초 모듈 미구현으로 실패한 뒤 구현했다. 첫 실제 기록 감사는 NO_AUDIO의 decoded length 불일치로 거절됐다. 기록상 0 sample이며 전후 모두 NO_AUDIO인 완료 사례를 blocked 유지로 인정하도록 수정했고, 짧게 디코딩된 승인 사례는 계속 거절하는 합성 회귀 검증을 추가했다. 최종 테스트 9개 통과, 실제 감사 exit 0. 출력은 ignored `work/ast-delta-audit/run-02/{source-deltas-private.json,aggregate.json}`에 있다. 같은 출력 디렉터리를 덮어쓰지 않으며, 후속 실행은 새 경로를 사용한다.

최종 Sol 읽기검토에서 retained 기록이 before/after 양쪽에서 함께 누락되면 감사에서 사라지는 P2를 확인했다.
비중복 input-audit 대상과 필수 raw/filtered 두 mode의 전체 집합을 대조하도록 수정했다.
빈 전후 기록과 한 mode 누락의 합성 RED를 확인한 뒤 **10개 GREEN**을 직접 실행했다.
중복 audit ID도 거부한다. 실제 기록의 `run-03`은 exit0이며 위 집계와 같고 누락 대상이 없었다.
이 수정은 측정된 인식 결과나 임계값을 바꾸지 않는다.
