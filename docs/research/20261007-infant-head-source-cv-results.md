# 훈련 source 안에서 선택한 head — 옹알이 단서와 성인 웃음 충돌

2026-10-07. [선행 계약](20261007-infant-head-source-cv-contract.md)을 실제1회 실행했다.
사용자 원음/feature·validation8·public33은 설정선택/학습/calibration에 쓰지 않았다.
기존train15clip/12source의leave-one-source-out만 사용했다. 동일975ms/필터·threshold.8/margin.4다.

| L2 | 12source평균 negative log likelihood | 설명상clip argmax일치 |
|---|---:|---:|
| .0001 | .0426320903 | 15/15 |
| .001 | .1056247579 | 15/15 |
| .01(원래) | .2881568942 | 15/15 |

이는weakcaption에대한CV값이며15개의독립아기/최종정확도가아니다.
사전규칙대로.0001만선택하고source/훈련IDs/weight SHA를selection.json에먼저동결한후validation을읽었다.
validation8의영아4는support4개가됐으나, 기타guitar51319의argmax가기타→성인으로변했다.
새infant support는없다. 이8개는이미사용한development자료이고 영아4는같은원천이다.

## 핵심 regression

| 구간 | 첫head .01 | sourceCV선택head .0001 | 결정 |
|---|---:|---:|---|
| 사용자누락0–10초 | support0 | .975초/인접2창없음 | 전체회복으로보고불가 |
| 같은원본7.173–17.173초 | .975초/인접없음 | .975초/인접없음 | 별도새회복아님 |
| 다른원본울음대조 | 1.950초 | 4.875초/인접있음 | 기존개발대조 |
| 공개babble | .898초/인접없음 | 2.848초/인접있음 | 회귀단서증가 |
| 공개7개월babble | .975초/인접없음 | 6.825초/인접있음 | 회귀단서증가 |
| 공개6.5개월crib | .975초/인접없음 | 14.625초/인접있음 | 기존미검수caption |
| 남성웃음cchenail | 0 | **11.700초** | 새성인오인 |
| 여성웃음ripc | 0 | **10.725초** | 새성인오인 |

성인대화/남성말소리·피아노/청소기/무음/tone/noise/DC/rumble은여전히support0이다.
generic_laugh는화자미검수상태이며2.925→11.700초support라부가위험으로유지한다.
확신도숫자가커졌다고진짜아기정답확률이향상됐다고해석하지않는다.

**새성인웃음오인 때문에이head도제품에미탑재한다.** 더약한L2를임의선택하거나threshold를조정하지않는다.
다음은새출처의성인자연웃음·울음·비명높은목소리반례를공개허락으로보강하고,
기존sourcevalidation/user자료를학습으로옮기지않는새비교다. CV값만으로실제성능을보증할수없음을확인한사례다.
사용자핵심누락은희소단서수준이며아직전체경로해결이아니다. 앱0.5.18/code32·새beta없음/전체goal active다.

## 실행/검증 이력

새CVrunner SHA `363868f354268194e62d4df947fc35bd2806828c43e3d74edc3d219a7cb815f7`.
penalty인자추가된linear/evalrunner SHA `10813729209ddbec0b0308fbd3d43b4adf2f80669316f0eec56c746256c126ce`.
첫5run의a678…를새소스의실행으로소급하지않는다. 같은default.01 수식/순서는보존했고고유7unit을재통과했다.
새sourceCV합성고유3unit은source전체제외/validationrole·중복·class소실거부/finiteNLL을통과했다.
CV/source단위검사과실측은제품정확도나AndroidQA가아니다.

| 실행 | exit | elapsed초 | peak bytes | 잔여자손 |
|---|---:|---:|---:|---:|
| sourceCV36foldfit+선택1fit | 0 | 1.094 | 701,407,232 | 0 |
| 선택head retained3 | 0 | .625 | 734,711,808 | 0 |
| 선택head public33 | 0 | 3.703 | 737,775,616 | 0 |

offline/Job180초2GiB·termination없음·cleanup true. 모든원음·feature는온디바이스이며별도encoder를받지않았다.
첫inline메타formatter에서st_size속성에()를붙여TypeError가났고,모델단계를다시실행하지않고formatter만수정했다.
해당실패를검사통과로세지않는다.

증거: ignored `work/infant-head-20261007/source-cv01/{selection.json,fit.json,completion.json,process.json}`,
`retained-cv01/`, `public-cv01/`, `unit-source-cv.log`, `unit-head-after-cv.log`.
학습media/feature/head는새hardnegative비교에활성유지하며Git에는코드·계약·집계문서만올린다.
