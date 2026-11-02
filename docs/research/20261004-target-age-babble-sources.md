# 6–18개월 월령 명시 옹알이 음원 후보

확인일: 2026-10-04. Agent-Reach Exa 검색 후 원본 Freesound 출처 페이지와 공개 preview를 확인했다. 기준은 업로더/저자가 아기의 나이를 6–18개월 안으로 직접 밝히고, 옹알이·여러 발성 또는 발성놀이를 설명한 자료다. **이 조건과 CC0/CC BY 권리를 함께 확인해 채택 가능한 새 source는 2개였다.** 세 번째 적격 source는 좁은 검색에서 확인되지 않아 억지로 채우지 않았다.

## 채택 source

| source | 저자 설명 / creator | 후보 label·불확실성 | 권리 | 길이와 고정 평가 구간 | 공개 preview 확인 |
|---|---|---|---|---|---|
| [baby daughter babbling — clod111](https://freesound.org/people/clod111/sounds/803534/) | 업로더의 원문: “my baby daughter babbling at 11 months. Recorded with Tascam H5.” | 11개월·babbling 후보. 세션/아이 신원은 페이지 설명 이상의 독립 검증이 없다. 별도 Freesound creator `clod111`; 이전 금지/기확보 creator와 다르다. | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). 원 페이지에 Attribution 4.0 표시. 상업적 이용 가능하나 저작자 표시 필요. | 84.5307초. 계획된 구간은 **첫 20초**. | Freesound 공개 LQ MP3 preview HTTP 200, **747,408 B**. 저장: `work/public-perception/target-age-new/clod111_803534.mp3`; SHA-256 `1F2924961948CACBE5A47D01F660290FC75D2742260A667B9A8C5E6545E3014D`. |
| [babblingts.wav — NoiseCollector](https://freesound.org/people/NoiseCollector/sounds/113227/) | 업로더의 원문: “9 month old baby boy making various baby noises, vocalizations, and actual baby behavior performances. More babbling …” | 9개월의 혼합 발성과 babbling 후보. babbling만으로 이뤄졌다는 설명은 아니다. 원문은 고유한 청취 정답이 아니므로 라벨은 source-described candidate로 유지한다. 별도 Freesound creator `NoiseCollector`; 기존 목록의 creators와 다르다. | [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/). 원 페이지에 Attribution 3.0 표시. 저작자 표시 필요. | 15.8128초. 전체가 20초 미만이므로 **클립 전체**가 고정 구간이다. | Freesound 공개 LQ MP3 preview HTTP 200, **136,536 B**. 저장: `work/public-perception/target-age-new/noisecollector_113227.mp3`; SHA-256 `D9A9E97BF67C71B6771DADEE6D0587E67FA87FAFC78611001E54CF7A456FB259`. |

두 preview 파일은 각 15 MB 제한보다 작으며 `work/` ignore 규칙 적용을 확인했다. 로그인 없이 공개되는 미리듣기만 내려받았고, full-quality 원본·점수·변환물을 만들지 않았다.

## 검토했지만 채택하지 않은 후보

- [11 month boy making some noises — hutsvoid](https://freesound.org/people/hutsvoid/sounds/197927/)는 11개월 및 babbling/first words 설명이 맞지만 원 페이지 라이선스가 **CC BY-NC 4.0**이다. CC BY/CC0 요구에 맞지 않아 제외했다.
- [Pau — frederic.font](https://freesound.org/people/frederic.font/sounds/482301/)는 “almost 10 months”와 talking-to-himself라는 설명이 있지만 **CC BY-NC 4.0**이라 제외했다.
- [toddlerbabble01.wav — pfly](https://freesound.org/people/pfly/sounds/33381/)는 16개월 babble 설명이 있으나 **Sampling+** license다. 요청한 CC BY/CC0 후보로 확인되지 않아 제외했다.
- 동일 creator의 NoiseCollector 추가 녹음은 새 creator/아기 독립 source 수로 세지 않았다.
- reinsamba/Charlotte, Islabonita, keplar, Sage Ross와 이미 확보한 keng, PDSounds/stephan 자료는 이번 추가 독립 source에 포함하지 않았다.

## 평가 해석 제한

이번 검색에서는 첫 20초 구간을 정하고 source label/권리만 확인했다. AST 실행, 점수 열람, threshold 조정, 독립 청취 정답화는 하지 않았다. 서로 다른 Freesound creator와 서로 다른 설명의 두 녹음은 신규 source 후보지만, 공개 설명만으로 실물 아기 신원·가족·세션의 독립성을 증명하지는 않는다. 제목/태그/업로더 설명은 부모의 frozen ASTv1에 대한 독립 정답이 아니다. 두 건을 사용하더라도 n=2 탐색 자료로만 보고, 기존 실패율이나 대상 전체 성능을 일반화하지 않는다.

검색에서는 `baby 7 months babbling`, `baby 10 months talking`, `9 month old`, `11 month boy`, `12/14/16 months toddler babble` 등의 Exa discovery 질의를 사용했다. Freesound 원 페이지에서 creator statement, duration, exact Creative Commons URL, preview URL을 확인하고 HTML metadata의 duration을 대조했다. 사용자 원본 또는 기타 개인 미디어를 검색·업로드하지 않았다.
