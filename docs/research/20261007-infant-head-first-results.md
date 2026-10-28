# 첫 작은 영아 head — 실제 실행과 남은 누락

2026-10-07. [사전계약](20261007-infant-head-screen-contract.md),
[현재 원작자 허락/출처](20261007-infant-head-subset-sources.md)를 따른다.
이는 **caption-weak 개발 비교**이며 제품 승인·독립 아기 정확도 검증이 아니다.

## 자료·실행

- 공식24페이지 감사 후 기존 회귀원음47370을 제외했다. 실제23preview=Train15/validation8,
  출처 그룹12/5이며 양쪽 source 겹침0. 기존public33과 관련된source는 회귀로만 사용한다.
- 전체snapshot96,231B/SHA `af3a0a62bf0a84f363c9bf75424e5a6496ac4a57117d5455778322e011dcc3ea`.
  실제23실행snapshot87,199B/SHA `b4c65fd17427ceec74c34d7051a4230688707761012ac8fc149a978a24e0746f`.
- MP3총1,317,779B. 기존3개의URL/SHA를 확인해 복사재사용했고20개만 신규취득했다.
  현재허락과2020archive허락을 각각 유지한다. 원음FSD lossless가 아닌공개lossypreview다.
- 공개153창의1024embedding을 실제추출했다. 기존521score는 **모든창 maxdiff0**이다.
  feature NPZ42,345B. 실제sample/time·padding·MP3overshoot의rawSHA/peak/clamp수를 구분한다.
- 3075param/FP3212,300B의 head를공개weakcliplabel만으로학습했다. NPZ는5,968B,
  SHA `dee3c4fb44c36996af1c8202433e96d515bc0ae2c1bbc3474a903b4f0a26e8a1`.
- 실제runner SHA는모든5단계에서 `a67863d1326e8469e1feef08963c12d679b2456b8c89c0dffac686d461551d5f`.
  Python3.12.14/NumPy2.5.3/LiteRT2.2.0/flatbuffers25.12.19.

| 단계 | exit | elapsed초 | peak committed bytes | 잔여자손 |
|---|---:|---:|---:|---:|
| 공개취득 | 0 | 39.781 | 671,264,768 | 0 |
| 공개feature | 0 | 2.578 | 723,914,752 | 0 |
| weakhead fit | 0 | .281 | 702,279,680 | 0 |
| retained3평가 | 0 | .594 | 734,662,656 | 0 |
| public33회귀 | 0 | 3.750 | 737,742,848 | 0 |

모두Job180초/2GiB·termination없음·cleanup true다. PC전체worker시간/메모리이며 Android성능이 아니다.
feature/fit/eval은네트워크를차단했다. 사용자 자료는 학습·정규화·calibration·augmentation에 사용하지 않았다.

## 결과와 결정

clip argmax의weaklabel대조는 train영아7/성인4/기타4, validation영아4/성인2/기타2가
설명상분류와모두일치했다. **8/8을 일반100%정확도로 보고하지 않는다.** 영아validation4는동일15개월원천이고
원음청취·사건·가족검수는없다. 학습완성stronglabel이라고표시하지않는다.

고정support조건(p≥.8/margin≥.4)은 더엄격하다. validation영아4중clip support1개뿐이다.
나머지영아p는.647/.798/.617이며threshold를낮추지않았다. 이값은calibrated아기확률이아니다.

| 기존평가구간 | infant support시간 | 인접support2창 | 해석 |
|---|---:|---|---|
| 누락원본0–10초 | 0 | 없음 | **누락미회복**, 최대infant score .560 |
| 같은원본7.173–17.173초 | .975초 | 없음 | 단일단서, 연속회복이라고보고불가 |
| 다른원본울음대조 | 1.950초 | 있음 | 기존알려진대조자료, 일반정답아님 |

누락두구간은동일원본에서약2.827초겹친다. 독립두아기/새holdout이아니다.
public33의성인대화·청소기·피아노·무음/tone/noise/DC/rumble,
추가남성웃음/남성voice/여성laugh는고정support0이었다. speaker-unverified generic_laugh는2.925초support가
있으므로모든성인웃음을차단했다고보고하지않는다. TV/원천미상/새영아일반화는남았다.

**첫head 단독은사용자핵심누락을해결하지못해제품에미탑재한다.** threshold완화/같은private자료에맞춘재학습은하지않는다.
다음은훈련source만사용한leave-one-source-out설정선택과더넓은source평가다.
validation자료를후속선별에쓰면계속development validation이며finaltest가아니다.
VTC는누락에서단서가있지만피아노오인/권리/Android검증이남아별도다.

제품은0.5.18/code32 YAMNet+AST+MediaPipe 그대로이며새beta배포없음이다.
작은head 학습가능성을앱인식률향상이나욕구번역으로확대하지않는다. 전체개선목표는active다.

## 증거

ignored `work/infant-head-20261007/{subset23-01,features23-01,head23-01,retained-head01,public-head01}/`
각completion/process/report, `first-summary.json`,
[도구QA](../qa/infant-head-first-20261007.md). 원음·개별평가score/feature·head는Git에올리지않는다.
