# ACLEW tutorial/GoldStandard: 공개 metadata와 schema 차단

확인일 **2026-10-07 KST**. 담당 문서 하나만 작성했다. 공식 GitHub/OSF **metadata GET6회**만 수행했다. README/template body는 발견하지 못했고 EAF 개인 전사·음원·weight·대형 body 취득·청취·훈련·추론·외부 메시지·기기·키·Git·빌드는0이다. 기존11파일은 편집하지 않았다.

## 1. 확인한 사실과 미확인

**실제 예제 basename 및 tutorial의 월령별 폴더는 공개 metadata에서 확인했다. exact enum·시간 unit·overlap 표기·상업권은 이번 범위에서 확정하지 못했다.** 공개 EAF를 개인 전사 body로 읽어 schema를 추출하지 않았다. caption을 event 정답으로 사용하지 않으며 `training_blocked=true`, `approved_training_label=null`을 유지한다.

[GoldStandard recursive tree](https://api.github.com/repos/aclew/ACLEW-GoldStandard/git/trees/5f2a067b8647dcac177eaac8c6fdb45e5a53b325?recursive=1)는 **90entries/truncated=false**, root tree SHA `5f2a067b8647dcac177eaac8c6fdb45e5a53b325`다. root는 `GSCompareApp`, `GSCompareAppEs`, `minCHATchecker Fall 2020`과.gitignore다. 읽힌 전체 tree의 README/LICENSE 이름 및 `.etf`/template/`.ecv` 검색 결과가 없다. 따라서 없는 README를 임의 경로로 반복 조회하지 않았다. 파일명 목록은 code 사용 허락이나 annotation 정확도를 입증하지 않는다.

[OSF b2jep node](https://api.osf.io/v2/nodes/b2jep/)의 title은 `Tutorials: Using the ACLEW DAS template`, `public=true`, user permission은read, **`node_license=null`**이다. storage에는 `Gold_Standard_Training_Files`가 있고 English/Spanish folders로 나뉜다. English의 공개 목록에는 **`08-18mos`, `0-7mos`, `19-36mos`, `gs_comparison_files`**가 있다. 폴더명만으로 개별 파일의 정확 나이나 발성 주인을 검증하지 않는다.

| 요구 항목 | 현재 확인/미확인 |
|---|---|
| speaker enum |이전 [공식 원문 조사](20261007-babble-event-label-next.md)에서 schematic CHI/FA1/MA1 및 speaker tiers는 확인. **현재 template의 전체 enum/코드별 정의·TV/noise/unknown token은 미확인** |
| maturity enum |이전 논문은 canonical/noncanonical syllables·word/multiword를 설명. **현재 template의 exact enum/value, laugh/cry 독립 코드, 필수/nullable는 미확인** |
| time unit |EAF라는 확장자와 segmentation 비교 목적만 확인. **actual TIME_UNITS·slot 단위·시간 기준/padding은 미확인**. 일반 ELAN 관례를 이 파일의 확인값으로 쓰지 않음 |
| overlap |이전 논문은 segmentation overlap 비교·동시화자/불일치를 설명. **actual overlap tier/token·여러 speaker의 동시 구간 허용/점수 집계는 미확인** |
| event examples |아래 exact EAF/WAV basename·size·Git blob SHA만 확인. **본문·라벨/event span·actual 발성·실패 예시는 미확인** |

## 2. 예제 basename: body를 읽지 않은 고정 metadata

아래는 [고정 GitHub tree](https://github.com/aclew/ACLEW-GoldStandard/tree/5f2a067b8647dcac177eaac8c6fdb45e5a53b325/GSCompareApp/eafs)의 값이다. 같은 basename은 pairing 단서이며 실제 연결 MEDIA_DESCRIPTOR를 읽은 결과는 아니다. Git blob SHA는 음원 SHA256이 아니다.

| `GSCompareApp/eafs/` 파일 | bytes | Git blob SHA |
|---|---:|---|
|5959-0GS0.eaf|71,819|be932e07888910cf284d3eda197e06104ad1dbe1|
|5959.wav|9,603,336|15796badc973bda3e46b4a85c9ec103796941c02|
|2337-0GS0.eaf|94,697|fe275df07b52274efd3ace102587570ef41e782f|
|2337-0GS0-transcribed.eaf|96,876|92939765e44efafb2da21087cadc308f364d9492|
|2337.wav|9,603,336|29cbd0772b6c8187ea8e3fce9ac3db11591fca68|

그 밖의 예제 EAF/WAV 본문도 읽지 않았다.

## 3. labels/code/audio 권리를 분리

- **Code:** repository를 공개 읽을 수 있다는 것과 상업 이용 license는 다르다. 현재 tree에서 LICENSE 이름을 찾지 못했고 code body/license/API license object는 읽지 않았다. 상업 코드 재사용 허락 **미확보**.
- **Labels/template:** OSF `node_license=null`, actual template/README의 grant·EAF별 권리는 미확인. 이전 ACLEW 공식 Usage Restrictions는 corpus별 조건과 raw/EAF 재배포·발표의 participant별 explicit 허용을 요구한다. 공개 GitHub EAF가 그 조건을 자동 해제한다는 증거는 없다.
- **Audio:** 예제 WAV의 존재/크기만 확인. voice/participant 동의·corpus 허락·상업 사용 grant는 미확인. 이전 HomeBank CC BY-NC-SA3 및 추가 corpus 접근 조건을 임의로 CC0/BY로 바꾸지 않는다.
- **Weights:** 이 두 metadata 경로는 새 weight 사용 허락이 아니다. weight body/card 조회0. 학습/배포/증류 허락으로 확장하지 않는다. 권리 차단에서 endpoint·mirror를 우회하거나 외부 요청을 보내지 않았다.

## 4. 실제 조회6회와 다음 항목1개

| # | GET·결과 |
|---:|---|
|1|[GitHub root contents](https://api.github.com/repos/aclew/ACLEW-GoldStandard/contents)200/3,081B |
|2|[b2jep node](https://api.osf.io/v2/nodes/b2jep/)200/4,334B |
|3|[b2jep storage root](https://api.osf.io/v2/nodes/b2jep/files/osfstorage/)200/1,855B |
|4|GitHub master recursive tree→위 고정SHA;200/22,880B/90entries |
|5|[Gold_Standard_Training_Files listing](https://api.osf.io/v2/nodes/b2jep/files/osfstorage/5d4c77770f488d00189095b7/)200/3,581B |
|6|[English_Files listing](https://api.osf.io/v2/nodes/b2jep/files/osfstorage/5d4c78e20f488d001890973e/)200/7,069B |

**외부6/6성공·HTTP/parse 오류0·audio/EAF/template body취득0**, 검색0. 실제 얻은 것은 JSON directory/node/tree metadata이며 audio/EAF/header/template body나 실행 정답은 아니다. English listing SHA256 `363486c1bb93ec578199f771f7e7fc800b0b3bbd1af7fdf28c0ba895c8615dd1`; GitHub tree JSON SHA256 `ff42058f8dee1d16ef7c3b9afaea07a7d34bc6ad4c2e7749b8c4c1dbfe8400ec`.

**다음 실행 가능한 항목1개:** 공개 API가 제공한 [English/08-18mos metadata 목록](https://api.osf.io/v2/nodes/b2jep/files/osfstorage/5d4c77ce272b140018ecacfe/)을 **GET1회** 읽어, 개인 전사가 아닌 README/빈 template/권리 문서의 exact basename·size·grant 경로가 있는지만 확인한다. 이번에는 미조회다. 목록이 없거나 인증이 필요하면 차단으로 기록하고 EAF/audio body를 읽지 않는다.
