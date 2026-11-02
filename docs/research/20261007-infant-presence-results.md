# 공개 성인 비언어 반례를 추가한 infant presence — 실제 결과

2026-10-07. [실행 전 계약](20261007-infant-presence-contract.md)과 [새12source 감사](20261007-adult-nonverbal-source-audit.md).
**제품 미탑재. 약국 첫 누락은 회복하지 못했고, 새로운 성인 비명 오인이 남는다.**
전체 목표는 active이며, 이 연구의 실행/도구 QA 성공을 앱 품질 개선으로 보고하지 않는다.

## 고정 자료와 실행

기존23+새성인12=35 preview, train21/18source, validation14/11source. 영아 train7은4source,
영아 validation4는 같은15개월 원천 한 개다. 모든 라벨은 caption-weak이며 청취·사건·욕구 정답은 아니다.
사용자3구간/2원본과 기존public33은 평가에만 썼다. 기존public33에는 source 중복·합성 조건이 있어 독립 test가 아니다.

실행snapshot SHA `27966aa9265da239489e7351959c01399ed3a0aed6428149babc2cb127fad4b9`.
preview 총2,069,263B/기존23재사용, 특징66,887B/249창, 기존521score차이0.
1024→1sigmoid/1025param/FP324100B. 실제 압축head2612B, SHA
`8cf42eb18703403f35d98fe6b52643ea72344ca896eb3606a3bd39969e2302cf`.
특징SHA `5dabe7acace700d68ae7bcc3db83d40cbe6df4fbb0b26c7a65756cbe61c2b240`.

18train source만 leave-one-out했다. macro binaryNLL은 .0001→.2859073406,
.001→.2658495902, .01→.3909549485라 사전 규칙대로 .001 하나를 선택했다.
head/selection 저장 후 validation을 읽었으며 private자료 학습/정규화/calibration/augmentation은 없다.
support .8/binarymargin .4는 고정된 연구 판정이며 calibrated 정답 확률이 아니다.

## 실제 판정

| 평가 | 아기 support 시간 | 인접2창 | 판단 |
|---|---:|---|---|
| 약국 첫0–10초 | 0초 | 없음 | 미회복 |
| 같은 원본7.173–17.173초 | .975초 | 없음 | 희소 단서만 |
| 다른 울음 대조 | 2.925초 | 있음 | 대조 단서 |
| 이전 오인 cchenail/ripc 성인 웃음 | 각0초 | 없음 | 해당 반례 오인은 감소 |
| 신규validation 성인 웃음42841 | .975초 | 없음 | 단일창 오인 |
| 신규validation 성인 비명384213 | 3.9초 | 있음 | **새 연속 오인** |
| validation 영아4clip | 각0초 | 없음 | 목표 영아source 미검출 |
| public 성인대화/피아노/기계음/무음·tone·noise·DC·rumble | 각0초 | 없음 | 해당 개발 대조 유지 |

validation clip pooledscore와 창 판정은 다르다.
validation 총14는 영아4/성인8/기타2이며, 성인384213 pooledscore .404지만 개별창에서3.9초 오인했다.
공개babble/웃음의 일부 support는 늘지만 해당 source 중복과 실패를 함께 남긴다.
기존 첫 head와 단순 숫자 성공률로 비교하지 않는다. task·negative 구성·선택규칙도 바뀌었다.

**채택하지 않는다.** 소리 존재 binary과제만으로 실제 child+adult중첩을 분리했다고 말할 수 없다.
다음은 clip평균이 짧은 발성을 지우는지 검증하는 weak multiple-instance 방식과 영아source 확장이다.
검증자료를 훈련으로 옮기거나 threshold를 낮춰 성공을 만들지 않는다.

## 실행 무결성과 초기 실패

ignored `work/infant-head-20261007/` 아래 결과다.

| run | exit | 초 | peak committed bytes | 자손 |
|---|---:|---:|---:|---:|
| subset35-03/acquire | 0 | 90.328 | 672,411,648 | 0 |
| features35-02 | 0 | 3.875 | 727,138,304 | 0 |
| presence35-01/fit | 0 | .703 | 703,250,432 | 0 |
| retained-presence01 | 0 | .609 | 700,510,208 | 0 |
| public-presence01 | 0 | 3.765 | 703,627,264 | 0 |

각Job180초/2GiB, memorylimit/termination없음. acquire외 network차단. 실제runnerSHA:
feature/acquire `467fb305d9555de6012a6b75988f042aa3d836675d4cac95b94004c097742e32`,
folds `991100835d0c98b8dbfae1356af621845f6c5e83222b473727f25efa7bc54e35`,
presence `8fb04de499c59c65cafa9cbb47988b59aeb636257627c92bafb72e7fd7856d83`.

subset35-01은 CDN read timeout/exit1/76.734초였다. acquisition.json이 없는데 시작한
features35-01은 missing acquisition/exit1/.25초인 후속 setup실패였다. subset35-02도
첫 신규음원에서 response timeout/exit1/28.281초였다. 성공으로 합산하지 않는다.
CDN HEAD200을 확인한 후 완전히 받은 파일만 hash/URL진행snapshot에 남기는 경로를 추가해
새출력subset35-03에서 완료했다. 기존 무기록 partial파일은 재사용하지 않았다.
처음 두 실패의 원문run.log/process.json은 보존한다. 중복 partialMP3는 최종본과SHA를 대조해 정리한다.

[연구 도구 QA](../qa/infant-presence-20261007.md)는 제품 품질과 별개다.
