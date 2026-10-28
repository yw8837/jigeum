# 새 exact 옹알이 파일: 9개월 취침 전 caption 후보

확인일 **2026-10-07 KST**. 담당 범위는 이 문서 하나다. `agent-reach` SKILL 및 references/search/web의 경로로 Exa CLI 검색과 공식 Freesound HTML만 읽었다. 앞선5파일·CC0두파일·extra9·학습35의 current 페이지는 다시 조회하지 않았다. 음원/preview body·weight·feature 취득, 청취, 학습, 추론, calibration, 사용자 자료·기기·키 접근, Git, 빌드는 **모두0**이다.

## 1. 결과: 새 exact 파일1개, 실제 검수 완료0개

**[bvaudio /475589: Baby girl babble cry.m4a](https://freesound.org/people/bvaudio/sounds/475589/)** 를 후속 검수 후보로 고정한다. 원설명에 **9개월**, **잠들기 전**, **babbling/trilling**, **끝부분의 little cry**, **Samsung S10 Plus 녹음**이 모두 있다. title/tag만의 월령·발성 추정이 아니다. 현재 **CC0**와 공식 preview URL이 확인된다.

이는 생활 녹음 상황과 발성을 명시한 **caption 근거**이며 실제 자연 영아/비연기/월령/발성 구간 정답을 검증한 결과가 아니다. little cry를 FUS 정답으로 전환하지 않는다. 학습 차단 **`training_blocked=true`, `approved_training_label=null`** 및 평가 전용 보류를 유지한다.

**새 exact ID 발견과 새 source 발견을 구분한다.** bvaudio는 기존233행 metadata 색인에 이미 있던 업로더이며, 이번에는 색인에 없던475589의 원문을 처음 고정했다. 독립 신규 업로더/가족/아동/녹음 세션 확보로 보고하지 않는다.

## 2. 먼저 확인한 로컬 source/alias 중복

[execution-sources35](../../work/infant-head-20261007/execution-sources35.json), [extra-babble-sources](../../work/infant-head-20261007/extra-babble-sources.json), [영아source 색인](../../work/infant-head-20261007/source-audit.json), [negative-source-audit](../../work/infant-head-20261007/negative-source-audit.json), [adult-nonverbal-sources](../../work/infant-head-20261007/adult-nonverbal-sources.json), [앞선5후보](20261007-next-infant-head-validation.md)를 웹 조회 전에 읽었다. train/held의 Corsica_S→tim.kahn, chrillz3r→giddster, kleine_blume→FreyaMaluk, TheAtomicBrain→marlonnnnnn 등 current alias도 포함했다.

- **475589 exact ID는 기존233행/실행35/extra9/앞선5후보에 없다.** bvaudio는 실행35의 train/held 업로더와 extra3·앞선5 source, 기록된 public33의23 source 이름에 없다.
- 기존 색인의 bvaudio 항목은 **395197 /Baby Laugh.mp3**이며 작은 아이 웃음·Samsung S7 Edge·Vancouver/2017년 설명이다. 같은 업로더이므로 이 둘의 파생/가족/세션 독립성을 가정하지 않는다. 기존 파일의 월령을475589의9개월로 채우지 않는다.
- adult-nonverbal의 `banned_uploader_or_source_names`에는 bvaudio가 있다. 이는 영아 metadata source를 성인 negative pool에서 제외하는 목록이다. 실제 negative source 파일을 bvaudio로 확인한 것이 아니다. negative-source-audit에는 bvaudio/475589가 없다.
- 검색의 clod111803534는11개월 caption이지만 **clod111이 public33 기존 평가 source**여서 페이지를 조회하지 않고 제외했다. Islabonita471060/471061도 기존 public33 source다. klankbeeld는 앞선 후보 source, NoiseCollector/reinsamba/Fugazi80는 기존 학습 source로 제외했다.

이름 기준 미중복은 전체 인터넷의 재게시 관계나 가족 신원의 독립 검증이 아니다. bvaudio의 현재 preview owner2883167은 확인했지만 다른 source의 numeric ID를 추가 웹 조회하지 않았다.

## 3. current 공식 metadata와 취득 경로

| 확인 항목 | 실제 페이지 값 |
|---|---|
| name/author |Baby girl babble cry.m4a /bvaudio |
| 게시일 |June 27th,2019 |
| 원설명 요약 |9개월 baby girl이 잠들기 전 babbling 및 trilling sounds, 끝부분에서 조금 cry. Samsung S10 Plus 녹음 |
| description 외부 원저작/파생 링크 |0개. 미표기는 원저작 보증이 아님 |
| 현재 원음 권리 |[CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) |
| 원음 format metadata |M4a(.m4a), **4:32.161**,4.3MB,44.1kHz,131kbps,stereo |
| 공식 preview |[475589_2883167-lq.mp3](https://cdn.freesound.org/previews/475/475589_2883167-lq.mp3) |
| 실제 access |공식 sound HTML HTTP200. 원음은 `Login to download`; preview URL 제공. **preview/원음 GET·HEAD·재생0**, body 접근/내용·format/동등성 미확인 |
| HTML snapshot |35,845B /SHA256 `d4611cdc5795c43446ee282e6892ace751dc54e5c6acba150523e043c10c700b` |

duration/format은 원음 페이지 metadata이며 실제 header를 읽은 값이 아니다. HTML SHA는 음원 hash가 아니다. 현재 CC0 표시와 description의 별도 이용 제한 없음은 소량 후속 사건 검수의 권리 단서다. [직전 공식 권리 확인](20261007-infant-source-readiness-update.md)의 Freesound FAQ/약관 및 CC0 제3자 권리·동의 한계는 그대로 적용하며 이번에 다시 조회하지 않았다. CC0만으로 모든 아동 관련 권리·보호자 신원/동의·원작자 lineage가 입증됐다고 말하지 않는다.

**caption의 시간 범위를 분리한다.** 설명은 babbling/trilling과 끝부분의 little cry를 말하지만, **272.161초 전 구간이 옹알이라고 명시하지 않는다.** caption에 babble/cry의 onset/offset나 지속시간이 없다. 부모 작업자의 동결 평가가 **첫20초**에 한정되면 그 결과는 첫20초 material의 결과이며, 끝부분 cry가 포함되거나 검증됐다고 추정하지 않는다. 최초20초에 실제 발성이 있는지 자체도 인간 검수 전에는 불명이다. 원음의 끝부분과 preview 끝부분이 일치하는지도 아직 확인하지 않았다.

**처리/권리 문구:** 현재 description은 Samsung S10 Plus 녹음만 명시하며 denoise/EQ/pitch/time-stretch/loop/edit 등의 처리·연기 여부를 설명하지 않는다. 이 미표기를 무편집·비연기 증거로 사용하지 않는다. 페이지의 CC0 안내는 복사·수정·배포·공연 및 상업 목적 이용에 저자의 별도 허락을 요구하지 않는다고 표시한다. description의 별도 이용 제한/파생 원작 링크는0개다. 앱 배포나 학습의 별도 승인으로 확장하지 않고 현재 evaluation-only 계약을 유지한다.

## 4. 후속 검수 조건과 blocker

1. 취득/청취가 허용된 별도 실행 담당자가 정상 공식 player 또는 lawful 원음 경로를 사용한다. 현재 작업은 metadata만 허용되어 실제 확보/청취를 진행할 수 없다. 접근 실패를 기록하며 로그인·키 탐색·우회·외부 연락은 하지 않는다.
2. **파일1개 전체4:32.161을 metadata 기준 사전 고정**하되 실제 재생 길이는 별도 확인한다. 모델 score/승인 여부를 숨긴 인간 검수가 먼저다. 모델 점수를 본 뒤 좋은10초만 선택하지 않는다. caption을 숨길 수 없으면 노출 사실을 기록한다.
3. 실제 들리는 babble/trill-like vocal/laugh/cry/fuss/other/uncertain 사건과 시작·끝을 수동 기록한다. 성인 겹침·생활잡음·복수아동/재생·편집 불확실성은 mix flag로 남긴다. caption의 끝부분 cry를 실제 위치/발성 정답으로 복제하지 않는다.
4. 가능하면2명 독립 판독 후 불일치를 보존한다. 월령은 `caption_only`, speaker/family는 `unverified`다. 동기·기분·욕구·의료 상태 정답을 만들지 않는다. head와 기존 기준을 동결하고 학습/calibration/augmentation에는 사용하지 않는다.
5. 현재 파일을 같은 업로더의 다른 파일과 한 source 그룹으로 관리한다. 검수 이후에도 소수 source의 support/miss를 약국 옹알이 개선의 일반 정확도·가족 독립 검증으로 보고하지 않는다. 제품 적용 판단은 부모 작업자의 별도 전체 경로 검증을 따른다.

**현재 blocker:** 실제 음원 접근·청취/사건 구간·자연성/정확한 나이/파생 관계 미검증. 동일 업로더가 기존 색인에 있으므로 source 독립성을 추가 확인해야 한다. 다음 새 후보를 승인했다는 뜻이 아니라, **정확한 월령+생활 상황+옹알이 설명+CC0를 갖춘 새 exact ID1개**의 metadata 경로를 확보한 상태다.

## 5. 제한된 검색 범위·실제 횟수

Exa CLI `mcporter call exa.web_search_exa`를 사용했다. 검색3회 제한을 모두 사용했으며 반환 결과는 각8개, 총24개/중복 제거 exact ID14개다.

| # | 발행한 query |
|---:|---|
|1|`site:freesound.org/people/ babbling baby months old` |
|2|`site:freesound.org/people/ baby babble "month old" -NoiseCollector -reinsamba -klankbeeld -Islabonita` |
|3|`site:freesound.org/people/ babbling baby "months old" "home"` |

첫 검색의2개월464742·4개월377238/407606/728634·3개월61701·6개월 미만63965, 추가 검색의6주14264·2개월691559·5개월204528은 target 범위 밖이다. 제목의 nearly6/months tag로6개월 이상을 만들지 않는다. 기존 source와 겹친803534/471060/471061/113310도 제외했다. 이 제외는 검색 snippet 수준이며 해당 페이지를 새 GET로 재확인하지 않았다.

| 실제 수행 | 수·결과 |
|---|---|
| 검색 query |**3/3성공**, 실패0 |
| 후속 공식 sound page GET |**1/최대8**,475589 HTTP200, 실패/삭제0. 추가 적합 결과가 없어 나머지7회는 사용하지 않음 |
| 외부 조회 합계 |**4operation**(검색3+페이지GET1). 검색 backend 내부 HTTP/redirect 수는 아님 |
| 로컬 orchestration 오류 |1: 최초 JS 문자열 구문 오류. tool 실행/외부 요청 전에 실패했고 바로 수정함. 외부 실패로 세지 않음 |
| 이전 대상 반복 current 페이지 조회 |0 |
| 새 exact 후보 /새 업로더 입증 /인간 검수 완료 |**1 /0 /0** |
| 음원·preview·weight·feature 취득/HEAD·청취·훈련·추론·calibration·기기·Git·빌드·키·사용자 자료·외부 연락 |모두 **0** |

검색3회 소진 뒤 새 검색을 추가하지 않았다. 확인된 metadata 후보1개만 유지하며 두 번째 후보를 채우기 위해 범위 밖 월령이나 기존 평가 source를 승격하지 않는다.
