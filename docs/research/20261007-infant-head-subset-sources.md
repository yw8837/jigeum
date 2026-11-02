# 영아 head 소량 subset: 공식 원작자 페이지·허락·preview URL 감사

조회일: **2026-10-07 KST**. 메인이 지정한 **24개 ID만** 조사했다. 학습 파일을 만들거나 원음·preview를 다운로드·청취하지 않았다. 모델·학습·추론·연락·로그인·ADB·Git 작업도 수행하지 않았다. 다른 작업자의 기존 자료를 변경하지 않았다.

## 1. 산출물과 실행 목록

[subset-source-pages.json](../../work/infant-head-20261007/subset-source-pages.json)에 현재 공식 페이지의 작성자·제목·설명·허락 anchor, HTML data-mp3 값, 페이지 bytes/SHA256, canonical URL, 읽은 UTC 시각을 저장했다.

- 페이지 메타데이터 감사: **24/24 확인**. 접근 차단 또는 누락 0.
- 취득·학습 준비 목록: **23개 후보 = train 15 / validation 8**. 학습·정답 승인이나 성능 검증이 아니다.
- 47370은 기존 공개33의 웃음 clip과 정확히 같아서 **취득·학습 목록에서 제외**, 대체 없음으로 메인이 확정했다. 감사 행에는 prior_regression_clip=true, training_blocked=true, metadata_eligible_for_planned_subset=false를 보존했다.
- 다른 23행도 approved_training_label=null, audio_sha256=null, audio_review_status=not_downloaded_not_listened를 유지했다.

| 실행 후보 분할 | infant_vocal | adult_vocal | other_or_uncertain | clip 합계 | 업로더 출처 그룹 |
|---|---:|---:|---:|---:|---:|
| train | 7 | 4 | 4 | 15 | 12 |
| validation | 4 | 2 | 2 | 8 | 5 |

sourcegroup은 기존 manifest의 FSD50K:dev:{metadata_uploader} key를 유지한다. currentauthor와 별칭을 분리해 Corsica_S→tim.kahn 이름 변경으로 새 출처를 만들지 않는다. **업로더 출처 그룹은 검증된 아기·화자·가족 ID가 아니다.** validation을 threshold 선택이나 변경 판단에 사용하면 최종 독립 test라고 보고하지 않는다.

## 2. 현재 허락과 보관 허락의 차이

현재 원작자 페이지에서 확인한 허락은 **CC0 1.0 8개 / CC BY 3.0 4개 / CC BY 4.0 12개**다. 공식 페이지의 예전 CC0/BY3 anchor에는 HTTP URL이 사용돼 HTTP/HTTPS를 모두 받고 **공식 host와 정확한 허락 경로**를 비교했다. 원래 href와 HTTPS canonical 정규화 값은 따로 보존했다.

허용 exact path는 /publicdomain/zero/1.0/, /licenses/by/3.0/, /licenses/by/4.0/다. NC/SA/Sampling+ 또는 부분 문자열 매칭으로 권리를 확장하지 않았다.

보관된 FSD metadata와 current page에서 **허락 grant가 달라진 것은 13개**다. 12개는 당시 BY3→현재 BY4, unfa 184432는 당시 BY3→현재 CC0다. current preview 취득 경로의 권리 스냅샷과 보관된 FSD 원본 권리는 구분한다. 바뀐 현재 허락을 과거 dataset annotation 허락이나 원음 hash에 덮어쓰지 않는다.

기존 [source-audit.json](../../work/infant-head-20261007/source-audit.json), [negative-source-audit.json](../../work/infant-head-20261007/negative-source-audit.json)은 read-only로 읽었다. 두 파일의 공식 metadata archive 고정값은 이번 산출물에 연결했다.

    bytes  6700838
    MD5    b9ea0c829a411c1d42adb9da539ed237
    SHA256 9a738e032546f9a2c6e3d04566928d04a65fb79b422cc9d78bb781723537bd19

이 단계는 기존 manifest를 읽었으며 metadata archive를 다시 취득한 단계가 아니다. 개별 원음의 동일성·새 다운로드 bytes/hash는 확인하지 않았다. 후속 취득 시 current 원작자·제목·원천 URL·허락 URL·처리 사항을 attribution에 보존해야 한다.

## 3. 감사한 고정 24개

각 ID 링크는 실제로 확인한 current canonical page다. preview URL 전체와 description 전문은 JSON에 있으며, URL을 파일명 규칙으로 추측하지 않았다. 역할은 요청된 분할이고 47370은 별도 제외 flag가 우선한다.

| ID / current page | 현재 작성자 | 제목 | 잠정 클래스 / 역할 | 현재 허락 |
|---|---|---|---|---|
| [113220](https://freesound.org/people/NoiseCollector/sounds/113220/) | NoiseCollector | 2tone.wav | infant_vocal / train | [CC-BY-3.0](http://creativecommons.org/licenses/by/3.0/) |
| [113221](https://freesound.org/people/NoiseCollector/sounds/113221/) | NoiseCollector | ahdada.wav | infant_vocal / train | [CC-BY-3.0](http://creativecommons.org/licenses/by/3.0/) |
| [113223](https://freesound.org/people/NoiseCollector/sounds/113223/) | NoiseCollector | ahhhhahaha.wav | infant_vocal / train | [CC-BY-3.0](http://creativecommons.org/licenses/by/3.0/) |
| [46963](https://freesound.org/people/reinsamba/sounds/46963/) | reinsamba | baby_voice6.wav | infant_vocal / train | [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/) |
| [47370](https://freesound.org/people/reinsamba/sounds/47370/) | reinsamba | baby_laugh1.wav | infant_vocal / train — 취득/학습 제외 | [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/) |
| [47371](https://freesound.org/people/reinsamba/sounds/47371/) | reinsamba | baby_laugh2.wav | infant_vocal / train | [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/) |
| [377237](https://freesound.org/people/Fugazi80/sounds/377237/) | Fugazi80 | Æ.20161218.wav | infant_vocal / train | [CC0-1.0](http://creativecommons.org/publicdomain/zero/1.0/) |
| [8157](https://freesound.org/people/levinj/sounds/8157/) | levinj | Jazzgiggle-10-8-05(9mo).mp3 | infant_vocal / train | [CC-BY-3.0](http://creativecommons.org/licenses/by/3.0/) |
| [18274](https://freesound.org/people/tim.kahn/sounds/18274/) | tim.kahn | ahh.aif | infant_vocal / validation | [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/) |
| [18277](https://freesound.org/people/tim.kahn/sounds/18277/) | tim.kahn | bebehigh.aif | infant_vocal / validation | [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/) |
| [18276](https://freesound.org/people/tim.kahn/sounds/18276/) | tim.kahn | baby_whine.aif | infant_vocal / validation | [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/) |
| [18273](https://freesound.org/people/tim.kahn/sounds/18273/) | tim.kahn | aha.aif | infant_vocal / validation | [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/) |
| [184432](https://freesound.org/people/unfa/sounds/184432/) | unfa | Yo YO yo yO | adult_vocal / train | [CC0-1.0](http://creativecommons.org/publicdomain/zero/1.0/) |
| [168494](https://freesound.org/people/AlienXXX/sounds/168494/) | AlienXXX | Build_it_and_They_will_come_a.wav | adult_vocal / train | [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/) |
| [87680](https://freesound.org/people/flogman/sounds/87680/) | flogman | e.wav | adult_vocal / train | [CC0-1.0](http://creativecommons.org/publicdomain/zero/1.0/) |
| [122204](https://freesound.org/people/dobroide/sounds/122204/) | dobroide | 20110612.bye.wav | adult_vocal / train | [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/) |
| [241477](https://freesound.org/people/Reitanna/sounds/241477/) | Reitanna | allo.wav | adult_vocal / validation | [CC0-1.0](http://creativecommons.org/publicdomain/zero/1.0/) |
| [17643](https://freesound.org/people/TwistedLemon/sounds/17643/) | TwistedLemon | happy-birthday.wav | adult_vocal / validation | [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/) |
| [256684](https://freesound.org/people/xtrgamr/sounds/256684/) | xtrgamr | PIANO LOOP.wav | other_or_uncertain / train | [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/) |
| [412332](https://freesound.org/people/clemza/sounds/412332/) | clemza | prepared piano loop 2.wav | other_or_uncertain / train | [CC0-1.0](http://creativecommons.org/publicdomain/zero/1.0/) |
| [148628](https://freesound.org/people/conleec/sounds/148628/) | conleec | AMB_Ext_Auto_Engine_001.wav | other_or_uncertain / train | [CC0-1.0](http://creativecommons.org/publicdomain/zero/1.0/) |
| [410616](https://freesound.org/people/tutenchwimse/sounds/410616/) | tutenchwimse | Sewing machine | other_or_uncertain / train | [CC0-1.0](http://creativecommons.org/publicdomain/zero/1.0/) |
| [51319](https://freesound.org/people/marvman/sounds/51319/) | marvman | wah guitar 1.wav | other_or_uncertain / validation | [CC0-1.0](http://creativecommons.org/publicdomain/zero/1.0/) |
| [118659](https://freesound.org/people/Kyster/sounds/118659/) | Kyster | chainsaw wont start.wav | other_or_uncertain / validation | [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/) |

## 4. 설명·원천 연결·weak label

24개 current 설명의 명시적 Freesound /people/.../sounds/... 원천 링크는 발견하지 못했다. 재게시나 음향 중복이 없다는 입증이 아니다. 기존 negative 감사에서 kwahmah_02의 269476→luffy 3536 편집 관계를 보수적으로 묶은 계약은 유지한다. 두 업로더는 이번 고정24 목록에 없다.

원문 설명은 다음 범위로 해석한다.

- NoiseCollector 113220/113221/113223: 9개월 남아라고 설명하고 loopable/음악 작업 용도를 명시한다. 동일 아기의 짧은 segment 후보이며 독립 아기 세 명이 아니다.
- reinsamba 46963: Charlotte 9개월의 “baby language” 설명이고 raw cry 라벨이다. 실제 발성을 듣지 않아 사건 정답을 확정하지 않는다. 47370/47371은 9개월 딸 웃음 설명이다.
- Corsica_S→tim.kahn: 모두 15개월이라고 설명한다. 18276은 whining, 18273은 “aha ... happy sound”여서 raw cry/laughter를 정답으로 확정하지 않는다.
- Fugazi80 377237, levinj 8157: 각각 10개월 딸·9개월 아들이라고 설명한다. 나이는 업로더 주장이다.
- 성인 후보는 “me/my voice/recorded myself”라는 설명과 adult raw 라벨의 조합이다. 실제 나이·발성 경계·겹침은 미검증이다. Reitanna는 게임을 하며 녹음한 반응으로 게임 소리 겹침도 아직 듣지 않았다.
- 악기·기계 후보는 본인 연주·녹음 또는 장비 설명이 있다. 실제 영아 발성 부재·배경 대화·음악의 타 원천·저음질은 확인하지 않았다.

이번 목록은 **caption-weak supervised feasibility를 준비하는 공개 출처 index**다. 강한 정답 승인이나 영아 의도·얼굴 주인·발성 동기 검증이 아니다.

## 5. 기존 평가 자료와 사적 자료 계약

기존 공개33의 PCM 자체는 학습에 넣지 않는다. 이번 목록과 NoiseCollector/reinsamba의 출처가 겹치므로 공개33 결과는 회귀용이며 새로운 일반화 test로 보고할 수 없다. 정확 중복 47370은 이번 취득·학습 후보에서 제외했다. 공개33 manifest의 NoiseCollector 행에는 정확 Freesound clip ID가 없어 업로더 이름만으로 다른 clip의 동일성을 단정하지 않는다.

사용자 사적 원본·그 segment·feature/embedding은 **학습·calibration·augmentation에 포함하지 않고 평가에만 사용한다. 외부 업로드하지 않는다.** 기존 기록이나 사용자 PCM을 이 공개 메타데이터 JSON에 저장하지 않았다.

## 6. 읽기 경로와 bounded snapshot

agent-reach/insane-search의 Jina Reader 본문 읽기 뒤 공식 페이지 HTML을 별도로 읽어 og:audio:artist, og:url, 제목, soundDescriptionSection, 실제 license anchor와 data-mp3를 검사했다. 24행 모두 direct 공식 HTML로 확인됐으며 fallback이 필요하지 않았다. 스킬의 외부 설치·연락·Git 설정 등은 이번 고정24 URL 조사 범위 밖이므로 실행하지 않았다.

- 호출 timeout 20초, 응답당 최대 10MiB. 전체 읽은 텍스트는 1,032,467B.
- JSON에 유지한 텍스트는 약 0.1MB로 20MB 상한보다 작다.
- page_sha256은 읽은 공식 HTML bytes의 시점 hash다. 동적인 페이지가 나중에 같은 hash를 반환한다거나 원음/preview가 같은 hash라는 뜻이 아니다.
- 기존에 확인한 preview URL도 모두 fresh page에서 다시 확인했다. previous_preview_reused_without_new_page=false. 부모가 먼저 확인한 영아 preview 세 개는 별도 flag로 표시했고 이전 negative 감사의 세 URL도 이전 값과 비교했다.
- preview body를 GET/HEAD·다운로드·디코딩하지 않았다. 접속 성공·오디오 format·원본 PCM 동일성은 후속 담당자의 별도 검증 대상이다.
- JSON의 files 각 행에는 메인이 요청한 id, preview_url, url, sourcegroup, role, provisionalclass, current_license_url, archived_license_url, description, title, currentauthor, page_sha256 필드가 있다.
