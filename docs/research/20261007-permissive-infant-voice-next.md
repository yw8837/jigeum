# 상업 이용 허락이 있는 영아 발성 head: 공개 메타데이터 감사와 출처 분할안

조회일: **2026-10-07 KST**. 이 문서는 공개 메타데이터 조사 결과다. 원음·preview·모델 가중치를 취득하거나 디코딩하지 않았고, 학습·추론·설치·연락·Git 명령·제품 수정은 하지 않았다.

기존 [저장소 감사](20261005-infant-repos-audit.md), [논문 감사](20261005-infant-papers-audit.md), [specialist 후속](20261005-infant-specialist-followup.md), [VTC1 권리](20261007-vtc1-distribution-rights.md), [이전 local head 제안](20261006-specialist-actionable-next.md)을 확인해 기존 실험과 보류 후보를 반복하지 않았다. 일반 AudioSet 모델 비교, 성인 VocalSound, 울음 원인 5분류, 무허락 VTC, NC BabyHuBERT를 새 후보로 제안하지 않는다.

## 1. 결론과 현재 산출물

**FSD50K의 CC0/CC-BY 원천은 작은 판별 head를 개발할 수 있는 실제 허락 경로다.** 다만 공개 `Babbling`은 영아 월령 라벨이 아니며 성인 발성·합성·변조 소리도 포함한다. 원문 설명과 원음 검수를 거쳐 정답을 다시 만들어야 한다.

공식 dev 안에서 월령을 명시한 후보를 보수적으로 분리할 수 있다. 학습 후보는 4개 업로더의 30개, 출처 보류 후보는 다른 1개 업로더의 16개다. 각각 옹알이·울음·웃음 raw 라벨을 모두 포함한다. **이는 업로더 단위 개발 분할의 가능성이다. 독립 가족 검증이나 학습 완료는 아니다.** 울음 학습 후보가 2개뿐이고 짧게 잘린 동일 아기 녹음이 많아, 일반 성능이나 출하 판단에 충분하지 않다.

공개 메타데이터 전용 산출물은 활성 worktree의 ignored 경로에 저장했다.

- [source-audit.json](../../work/infant-head-20261007/source-audit.json): 허락 대상 233개 전부의 clip ID, 원문 설명·제목·tags, 원래 license/uploader, 원작자 URL, 잠정 출처 분류, 나이 명시 후보 46개의 분할 역할, 별칭, preview URL 3개, 파생 원천 연결, 원음 검수 미완료 상태.
- 이 문서: 재현 규칙과 출처 분할 계약, 한계 및 다음 검수 조건.

메타데이터 zip은 메모리에서 읽었다. 디스크에 저장한 JSON은 의도적인 공개 자료 감사 산출물이며 원음·가중치·사적 기록을 포함하지 않는다. `work/`는 이 worktree의 `.gitignore`에 명시돼 있다.

이번 추가 탐색에서 **작고 상업 허락이 명확하며 영아 데이터·입력 계약까지 확인된 완성 checkpoint는 확보하지 못했다.** 이는 전 세계에 없다는 선언이 아니다. 확인한 모델은 8절에 기록했다. 부모 에이전트가 수행하는 VTC1 및 모바일 수치 검증은 이 문서의 검증 범위 밖이다.

## 2. 공식 파일과 고정 해시

원천은 [FSD50K 공식 Zenodo record 4060432](https://zenodo.org/records/4060432)다.

| 실제 읽은 공개 archive | bytes | 확인 내용 |
|---|---:|---|
| [FSD50K.doc.zip](https://zenodo.org/api/records/4060432/files/FSD50K.doc.zip/content?download=1) | 6,984 | `FSD50K.doc/LICENSE-DATASET`와 README 전문 |
| [FSD50K.metadata.zip](https://zenodo.org/api/records/4060432/files/FSD50K.metadata.zip/content?download=1) | 6,700,838 | 공식 record의 MD5와 일치, 자체 SHA256 계산 |

메타데이터 zip 고정값:

```text
MD5    b9ea0c829a411c1d42adb9da539ed237
SHA256 9a738e032546f9a2c6e3d04566928d04a65fb79b422cc9d78bb781723537bd19
```

| zip 내부 정확한 파일 | 사용 필드와 의미 |
|---|---|
| `FSD50K.metadata/collection/collection_dev.csv` | `fname`, `labels`, `mids`; 공식 dev의 raw collection 라벨 |
| `FSD50K.metadata/collection/collection_eval.csv` | 같은 필드, 공식 eval |
| `FSD50K.metadata/dev_clips_info_FSD50K.json` | string clip ID가 key; `title`, `description`, `tags`, `license`, `uploader` |
| `FSD50K.metadata/eval_clips_info_FSD50K.json` | 같은 구조, 공식 eval |
| `FSD50K.metadata/collection/vocabulary_collection_dev.csv` / `vocabulary_collection_eval.csv` | raw 라벨과 ontology MID 대응 자료 |
| `FSD50K.metadata/pp_pnp_ratings_FSD50K.json` | `{clipID: {ontologyMID: [ratings]}}`; 예: `338674`의 `/m/0ghcn6:[1,1]`, `/m/01z5f:[1,1,-1]`. 연령·가족·발성 구간 검증은 아님 |
| `FSD50K.metadata/class_info_FSD50K.json` | 클래스 설명 자료 |

clip-info에는 **검증된 가족 ID, 안정된 업로더 numeric ID, 나이 필드, 발성 시작·끝 시각이 없다.** 출처 key는 `FSD50K:{official_split}:{metadata_uploader}`로 만든다. 이는 관리용 key이며 영아 개인이나 가족 ID가 아니다.

`FSD50K.ground_truth.zip` 읽기는 중도 실패했다. 해당 archive의 내용이나 200-class 최종 ground truth를 읽었다고 보고하지 않는다. 이 문서의 수량과 사건 이름은 **raw collection 라벨**에서 나온다.

## 3. 허락 범위와 정확한 선별 규칙

공식 `LICENSE-DATASET`은 **CC BY 4.0**이며 다음 문구를 포함한다.

> Adapt — remix, transform, and build upon the material for any purpose, even commercially.
>
> The licensor cannot revoke these freedoms as long as you follow the license terms.

dataset/주석 허락과 개별 원음 허락은 구분한다. 이번 허락 대상에서 실제 관측한 원음 license URL은 정확히 다음 둘이다.

```text
http://creativecommons.org/publicdomain/zero/1.0/
http://creativecommons.org/licenses/by/3.0/
```

향후 선별은 URL을 파싱해 공식 `creativecommons.org`의 허용 license 경로를 정확히 비교한다. 위 두 허락의 canonical 경로만 이번 감사의 대상이다. `/licenses/by/`를 포함한다는 문자열 조건만으로 다른 URL을 승인하지 않는다. BY-NC, BY-SA, Sampling+ 등은 이번 대상에 포함하지 않았다. 원음 CC BY 3.0과 dataset/주석 CC BY 4.0을 manifest에서 각각 보존한다.

[Freesound 공식 AI 학습 FAQ](https://freesound.org/help/faq/#i-want-to-download-large-amounts-of-sounds-from-freesound-to-train-an-artificial-intelligence-ai-model-can-i-do-that)는 “yes as long as individual sounds' Creative Commons licenses are respected”라고 설명한다. CC-BY attribution을 유지하고 상업 학습에서 CC-BY-NC를 제외해야 한다. 이 제안은 소리를 생성하지 않는 판별 head이므로 같은 FAQ의 Generative AI 추가 preferences와 혼동하지 않는다.

공식 record의 commercial inquiry 문구를 모든 CC-BY 자료의 상업 금지로 해석하지 않는다. 반대로 dataset 전체 허락으로 NC 원음을 허용하지 않는다. 후속 취득 시 원작자·제목·원천 URL·license URL·변경 사항을 attribution에 유지해야 한다.

klankbeeld 원문에는 CC BY 3.0과 함께 raw stock 재배포를 원치 않는다는 설명도 있다. 이 후보는 대부분 2/4개월로 대상 연령 밖이라 첫 분할에서 제외했다. 원음 재배포나 stock 자료 판매를 자동 제안하지 않으며, 향후 쓸 경우 허락·출처 설명을 함께 보존한다.

대량 취득은 공식 FAQ의 Data Packs Portal 등 배포 경로를 따라야 한다. 사이트 벌크 수집·연락·가입을 실행하지 않았다. 공개 FSD 원음 archive는 크기 때문에 작은 subset의 취득 방식·원음 bytes/hash를 별도로 고정해야 한다.

## 4. 재현 가능한 메타데이터 수량과 원문 출처 분류

1. zip bytes/MD5/SHA256 확인 후 두 collection CSV를 읽는다.
2. `fname` string으로 **같은 official split**의 clip-info JSON key를 join한다.
3. `labels.split(',')`의 완전 일치 토큰만 센다. `Hubbub`이나 `speech_babble`를 영아 옹알이에 포함하지 않는다.
4. 3절의 원음 허락 URL만 남긴다.
5. unique clip ID와 업로더 이름을 각각 센다. 여러 raw 라벨이 붙은 파일을 여러 사람으로 세지 않는다.

| raw target | dev 전체 → 허락 대상 | dev 대상 업로더 | eval 전체 → 허락 대상 | eval 대상 업로더 |
|---|---:|---:|---:|---:|
| Babbling | 152 → 127 | 11 | 5 → 4 | 4 |
| Baby_cry_and_infant_cry | 69 → 48 | 21 | 26 → 23 | 15 |
| Baby_laughter | 45 → 21 | 12 | 12 → 12 | 10 |
| Child_speech_and_kid_speaking | 158 → 69 | 26 | 97 → 72 | 41 |
| Female_speech_and_woman_speaking | 393 → 382 | 29 | 247 → 212 | 148 |
| Male_speech_and_man_speaking | 391 → 362 | 67 | 467 → 397 | 222 |
| Speech_synthesizer | 59 → 58 | 8 | 61 → 46 | 33 |

Babbling dev 127은 CC0 57 + BY 70, eval 4는 모두 BY다. 세 영아 이름 라벨의 허락 대상 union은 **233개: dev 195개/33 업로더, eval 38개/27 업로더**다. 총 업로더 이름 그룹은 60개다. 모두 `source-audit.json`에 전체 ID·원문·출처 URL을 저장했다.

원문 기준의 보수적 출처 분류는 다음과 같다. 이 분류는 출처 주장과 확실성을 분리하기 위한 감사 필드이며 모델 정답이 아니다.

| 원문 출처 분류 | clip 수 | 의미 |
|---|---:|---|
| 아이 원천이라고 설명 | 189 | 자녀/조카, 아이 녹음, 현장 녹음 또는 유아 pack 설명. 실제 아이·나이·자연 발성은 미검증. 어린이·대상 월령 밖도 포함 |
| 성인 또는 본인 gibberish라고 설명 | 11 | 성인 나이 명시 1개, 본인 발성/만화 할아버지 의도/자동 잘라낸 nonsense 발성 등. 본인이라는 이유만으로 성인 나이까지 검증된 것은 아님 |
| 가공·리믹스 원천 | 5 | 피치 변조 재게시·합성 편집·샘플 리믹스·원천 미상 가공 녹음. 원천 연결과 허락을 다시 검수해야 함 |
| 합성 발성 설명 | 1 | Google Translate를 이용한 babytalk |
| 원천 미상 | 27 | 짧은 generic 설명, 이름/라벨만으로 실제 아이임을 보증할 수 없는 자료 |

아이 설명이 있는 자료에도 처리 flag가 필요하다. NoiseCollector는 loopable clip과 Wavosaur 편집을 명시하고, Stevious42 `259610`은 baby monitor를 통한 울음이며, carolgee `202730`은 어머니 발성이 함께 있는 설명이다. wjoojoo의 웃음 `194535,194536`은 “My son laughing”이지만 다른 18개는 “marvie babbling/screeching” 등으로 나이·개인 관계가 명확하지 않다. 한 업로더가 여러 아기를 녹음했을 가능성을 열어 두고 전부 동일 source group에 묶는다.

## 5. 공식 dev 안에서의 구체적 출처 분할 계약

다음 46개는 **나이를 명시한 공개 설명에서 고른 잠정 후보**다. 원음 검수가 완료되기 전 학습 정답으로 승인하지 않는다. 나이 범위는 앱 대상인 6~18개월이다.

### 학습 쪽 잠정 후보: 30개 / 업로더 4개

| source ID | 설명상 월령·raw 라벨 | 전체 clip ID |
|---|---|---|
| `FSD50K:dev:NoiseCollector` | 9개월 남아, Babbling 20개 | `113220,113221,113223,113224,113225,113226,113229,113236,113237,113238,113239,113240,113241,113242,113243,113249,113253,113255,113256,113257` |
| `FSD50K:dev:reinsamba` | 딸 Charlotte 9개월, cry 2 + laughter 6 | cry: `46963,46964`; laughter: `47370,47371,47372,47373,46966,46962` |
| `FSD50K:dev:Fugazi80` | 딸 10개월, Babbling 1 | `377237` |
| `FSD50K:dev:levinj` | 아들 Jazz E. 9개월, laughter 1 | `8157` |

학습 후보 합계: **Babbling 21 / cry 2 / laughter 7 = 30개**. `46962,46966`의 원문은 “baby language”이며 raw laughter 라벨을 그대로 정답으로 확정할 수 없다. 원음에서 어떤 발성인지 다시 확인한다.

같은 업로더의 대상 외 후보도 다른 분할로 보내지 않는다.

- Fugazi80 `377238`: 같은 딸의 4개월 기록. 대상 연령 밖으로 제외하고 원천 그룹 유지.
- reinsamba `33670,33671,33672,33673`: 같은 Charlotte의 생후 2주 울음. 대상 연령 밖으로 제외하고 원천 그룹 유지.
- NoiseCollector `111310,111312,111315,107416,107424,111161,107422`: 3종 union에는 있지만 정확 나이가 해당 원문에 없다. 첫 학습 후보에서 제외하고 같은 source group 유지. `111161`은 기존 segment의 clipping 복구 편집이라고 명시했다.

### 출처 보류 후보: 16개 / 업로더 1개

source ID: **`FSD50K:dev:Corsica_S`**. 현재 확인한 원작자 페이지의 이름은 **tim.kahn**이다. 이 이름 변경을 새 업로더나 새 가족으로 세지 않는다. manifest에 `current_name_alias`와 확인 페이지를 저장했다.

역할 이름: **`source_heldout_provisional_not_familytest`**.

| 원문상 15개월 남아의 raw 라벨 | 전체 clip ID |
|---|---|
| Babbling 10 | `18274,18277,18278,18279,18280,18285,18286,18288,18289,18292` |
| Baby_cry_and_infant_cry 3 | `18276,18284,18291` |
| Baby_laughter 3 | `18273,18275,18281` |

전부 CC BY 3.0이고 각 원문에 15개월을 명시한다. 다만 `18273`은 “aha ... a happy sound”, `18281`은 “sort of laughing”, 울음 후보는 whining/protesting/scream 등의 설명이다. **원문이 나이를 명시해도 사건 라벨이 검증된 것은 아니다.** 짧은 이름 유사 clip과 이어지는 crib 발성은 같은 source group에 묶는다.

### 분할 사용 조건

- 같은 업로더의 모든 원음·잘린 구간·편집본·augmentation은 한 source group에 둔다. 녹음/session/동일 아기/재게시 연결을 확인하면 그룹을 합친다.
- Corsica_S를 threshold 선택이나 반복 개선에 쓰면 **validation**으로 보고한다. 같은 자료를 최종 독립 test로 다시 보고하지 않는다.
- 업로더는 검증된 가족이 아니다. 이 보류 후보는 다른 업로더의 15개월 설명 자료이며 **family-disjoint 최종 평가**로 표시하지 않는다.
- 성인/기타 클래스 및 calibration용 음원은 아직 배정하지 않았다. 이 manifest는 완성된 3-class 학습 corpus가 아니다.
- 30/16은 원음 검수 전 후보 수다. 실제 event, 겹침, 불확실성, 중복을 확인하면 줄어들 수 있다.

## 6. 제외 자료와 원천 연결

| 제외 또는 별도 검수 그룹 | 전체 관련 clip ID | 원문 근거 / 계약 |
|---|---|---|
| dev batchku | `10180,10181,10183,10186,10188,10191,10199` | rhythmic nonsense talk를 자동 분할. 영아 나이 근거 없음. 본인/성인 유사 발성 confuser 후보, 영아 정답 제외 |
| dev gipsyGA | `128479,128484` | 본인 gibberish를 cartoon grandpa 목소리로 만들 의도. 영아 정답 제외 |
| eval rubekolo | `393504` | “30 y.o. female says WELLWELLL”; 성인 |
| eval Creeper_Ciller78 | `317359` | 본인 발성을 pitch change한 “Frustrated Man”; 변조 |
| eval Puniho | `115378` | Google Translate babytalk를 편집·가공; 합성 |
| dev gumballworld | `391181` → `tomhannen/243992` | 해당 원음을 고음으로 바꿨다고 명시. 원천 허락·나이 확인 전 clean infant 제외, 파생본과 부모 원천을 같은 그룹으로 묶어야 함 |
| dev iut_Paris8 | `390658,390156` | 반복·속도·증폭·EQ 또는 울음/웃음/자장가 mix. 자연 원천과 파생 관계 미검증 |
| dev WIM | `26147` | 여행 sample pack, 가공됨, 녹음 장소를 모르겠다고 명시. 영아 원천 미상 |
| eval arcanekids | `264842` → `41328,161691,4912,67882,126584` | 리믹스의 원작자 링크를 명시. 각각 Sumanguru Gyra Jones/felix.blume/NoiseCollector/gyzhor/tomtenney. 원천 관계를 먼저 고정 |

공식 raw collection 전체에서 dev/eval **업로더 이름 집합의 교집합은 0**이었다. 그러나 eval `264842`가 dev 후보 업로더 NoiseCollector의 `4912`를 인용한다. `4912`는 이번 233개 union에 없고 같은 아이인지도 알 수 없다. 이 예는 이름 분리만으로 재사용·리믹스 원천 관계를 해결하지 못한다는 근거다. 해당 eval 리믹스를 독립 영아 품질 평가에 사용하지 않는다.

eval Babbling 허락 대상 4개 중 3개가 위 성인·변조·합성 설명이다. 나머지 Erdie `22693`은 “son ... a few month old”로 정확 월령이 없다. raw Babbling 개념과 앱의 6~18개월 영아 발성 과제가 다르다는 증거다. 이 연령 과제의 관점만으로 원래 FSD 라벨을 잘못됐다고 단정하지 않는다.

eval에는 별도 보류 가능한 월령 설명도 있다: BoilingSand `50665,50666`은 16개월의 자연 울음, amccoy711 `388407`은 15개월 아들 웃음, carameans `135185`는 한 살 쌍둥이 딸 목욕, soundscalpel.com `110392`는 15개월 남아 웃음이다. 마지막 두 사례의 여러 아기·효과음 출처는 별도 검수해야 한다. 이 자료를 이번 dev 분할에 학습용으로 끌어오지 않았다.

## 7. 소량 로컬 청취 준비: 공식 preview URL만 확인

원작자 페이지 세 개의 HTML에서 실제 `data-mp3` 속성을 확인했다. Jina Reader의 본문 출력에는 preview 링크가 없어 해당 공식 페이지 HTML의 속성만 추가로 읽었다. 원음·preview body를 GET하거나 HEAD 검사·다운로드·디코딩하지 않았다.

| 원작자 페이지 / FSD 원음 허락 | 공식 페이지가 제공한 공개 저음질 preview URL |
|---|---|
| [Fugazi80 377237](https://freesound.org/people/Fugazi80/sounds/377237/) / CC0 1.0 | `https://cdn.freesound.org/previews/377/377237_5491823-lq.mp3` |
| [NoiseCollector 113220](https://freesound.org/people/NoiseCollector/sounds/113220/) / CC BY 3.0 | `https://cdn.freesound.org/previews/113/113220_4948-lq.mp3` |
| [Corsica_S → tim.kahn 18274](https://freesound.org/people/Corsica_S/sounds/18274/) / CC BY 3.0 | `https://cdn.freesound.org/previews/18/18274_7037-lq.mp3` |

후속 담당자는 공개 허락과 attribution을 유지하며 이 세 URL의 소량 청취 준비를 할 수 있다. 이 URL의 존재 확인은 **원본 PCM 취득·동일성 확인·접속 성공을 대신하지 않는다.** preview는 손실 압축 청취 사본이며 원본 FSD 파일과 sample rate/bytes/hash가 같다고 보고하지 않는다. preview에서 학습 품질이나 공식 데이터 입력 계약을 확정하지 않는다. 원본 다운로드의 계정/API 조건과 작은 subset의 공식 취득 방식은 아직 해결하지 않았다.

## 8. 작은 head의 조건과 새 checkpoint 조사

최초 개발안은 고정된 제품 YAMNet의 1024차원 embedding을 freeze한 **1024→3 선형 head**다. 잠정 클래스는 `infant_vocal / adult_vocal / other_or_uncertain`. bias 포함 **3,075 FP32 parameter = 12,300B**는 순수 가중치의 산술값이다. serialized/export 크기, latency, 정밀도, 성능은 측정하지 않았다. 일반 AST/EfficientAT/PretrainedSED/PANNs/GTCRN 비교를 다시 실행하는 제안이 아니다.

원음 검수 전에 필요한 계약:

- 공개 원천·license/attribution·출처 그룹을 보존하고 실제 합법적으로 얻은 PCM의 SHA256/bytes/format을 고정한다.
- 로컬에서 들은 실제 발성의 시작·끝, 성인 겹침, monitor/TV/합성/가공, 불확실성을 기록한다. weak clip 라벨을 모든 window에 강제 적용하지 않는다.
- 6~18개월이라는 설명은 검증되지 않은 월령 주장으로 보존한다. face owner나 발성 동기는 이 메타데이터로 판정할 수 없다.
- **사용자 사적 원본·그 구간·feature/embedding은 학습·calibration·augmentation에 넣지 않는다. 평가에만 사용하고 외부 업로드하지 않는다.**
- 출처 분리 validation에서 threshold를 선택하고 새로운 독립 자료로 평가한다. 공개 자료만으로 가족 독립성과 실제 제품 품질은 아직 입증하지 못한다.
- infant event recall 및 false approval/minute를 비교하고 성인/합성/TV·저음질·겹침을 검수한다. 현재 정답·negative corpus가 미완성이라 학습 승인 단계가 아니다.

| 새로 확인한 artifact / 고정 revision | bytes·입력·출력 | 허락과 판단 |
|---|---|---|
| [bookbot/distil-wav2vec2-adult-child-cls-37m](https://huggingface.co/bookbot/distil-wav2vec2-adult-child-cls-37m/tree/5410bd526423b7c95409247274d7be8680b528f7) | `model.safetensors` 151,474,736B; SHA256 `4e7c49b2aabf5ddc173e922253a2dd165ee138884b061c58760194a5143c825f`. 7 conv + 4 transformer layers, hidden 768/projector 256, `adult=0/child=1`. 16kHz raw 입력, normalize=true/right zero padding. 고정 chunk 계약 없음 | card Apache 2.0. 비공개 adult/child speech와 teacher 증류, 월령·가족·출처 분할 불명. 작은 영아 detector에 해당하지 않음. 95.89%를 영아 정확도로 바꾸지 않음 |
| [Aditya7864/Children_vs_Adult_Classifier](https://huggingface.co/Aditya7864/Children_vs_Adult_Classifier/tree/c2ee55cc955945ebf8dc80bce7a3ce3080128159) | `ChildrenVsAdult_classifier.h5` 44,409,872B. README 28B의 Apache 2.0 YAML뿐. config/label order/input/provenance 없음 | 이름과 허락 표시는 있으나 실행 계약·영아 연령 미확보. weight를 읽어 추정하지 않고 보류 |
| [haseong8012/whisper-tiny_child10k-adult6k](https://huggingface.co/haseong8012/whisper-tiny_child10k-adult6k/tree/d2065a9823f7d49f33865f241d11778da4135d03) | 151,097,459B, `WhisperForConditionalGeneration`, Korean ASR, 30s/16kHz/80mel | Apache 2.0 metadata. voice-type classifier가 아님. 학습 data 이름을 output label로 바꾸지 않음 |

가중치는 하나도 취득하지 않았다. HF 공개 API/blob metadata/card/config만 읽었다. 좁은 GitHub infant vocal/child-adult 검색에서는 대응하는 새 release를 확인하지 못했고 Zenodo babbling 제목 검색에는 동물·비음성 연구가 섞였다. 이름 일치·CC0만으로 사람 영아 corpus라고 보고하지 않았다.

## 9. 다음 실행의 완료 기준

현재 완료한 것은 **허락 대상 공개 index, 원문 출처 감사, 30/16개 잠정 출처 분할 계약, preview URL 확인**이다.

다음 단계의 완료는 원음 취득 및 로컬 검수를 거쳐 license·중복·발성 사건·나이 주장·출처 그룹을 검수하고 `approved_training_label`을 채운 상태다. 이 단계가 끝나야 실제 학습 corpus와 calibration/test 범위를 결정할 수 있다. `source-audit.json`의 해당 값은 현재 모두 null이며 `not_acquired_not_reviewed`다. 일반 영아 품질 개선과 출시 판단은 이후 독립 평가 결과가 필요하다.
