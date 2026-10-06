# 추가 옹알이 출처 9개: 현재 권리와 공식 preview 경로 감사

확인일 **2026-10-07 KST**. 기존 FSD50K 공개233행에서 지정된9개만 조사했다. 공식 원작자 HTML의 본문·권리·Open Graph 원작자·preview 속성을 읽고 공개 metadata만 저장했다. **원음/preview body·HEAD·weights 취득, 청취·디코딩·추론·학습·사적 사용자 자료 접근·Git·빌드·ADB·외부 연락은 하지 않았다.** 작성한 파일은 이 문서와 ignored extra-babble-sources.json 둘이다.

## 1. 결과와 제한

이번 독립 재조회에서 **9개 페이지 모두 HTTP200, sound ID 일치, exact CC0/BY grant와 공식 data-mp3 경로를 확인**했다. 현재 이름은 FunWithSound/Stevious42/wjoojoo이며9개 모두 metadata uploader와 같아 새로운 alias는0이다. **공식 주소의 존재 확인**이다. 해당 MP3 body의 접속 성공·동일 원음·PCM/파일 크기·내용·학습 정답은 확인하지 않았다.

현재 권리는 **CC0 4 /CC-BY3 1 /CC-BY4 4**다. wjoojoo4개는 archived BY3→current BY4로 바뀌어 둘을 보존한다. 이 snapshot은 dataset 주석 CC-BY4와 원음 개별 권리를 구분한다. preview MP3 자체를 받아 SHA로 고정한 것은 아니다.

세 uploader 그룹은 기존 public33/positive46의 train 또는 held 그룹과 metadata 이름·currentauthor 기준으로 겹치지 않는다. **검증된 가족·화자·월령 독립성을 뜻하지 않는다.**9개 모두 정확 월령이 없고 **6~18개월 verified positive는0**이다. 새 source를 개발용 train candidate로만 지정했으며 기존 held/eval을 재배정하지 않았다.

[공개 manifest](../../work/infant-head-20261007/extra-babble-sources.json)는 schema jigeum.extra-babble-source-pages.v1이다.

- bytes: 54,093B
- SHA256: a7658a7a85688ed360377d5607101b31d1d1a115be96e7c58abc9aebbd3d045b
- role: train_development_candidate, subset_role:train. 아직 approved_training_label=null, training_blocked=true.
- 현재 취득 상태: not_attempted_current_preview_url_presence_only.
- raw current HTML hash와 license/description/author/preview HTML snapshot, exact current URL 및 archived 문구·grant, per-page attempts를 보존했다.
- 원음 hash·event 시작/끝·검증된family ID는 모두 null이다. metadata_eligible_for_planned_subset=true를 청취 정답 승인으로 바꾸지 않는다.

## 2. 고정한 9개 공식 경로

Preview 주소는 아래 source page HTML의 data-mp3 값을 그대로 읽었다. 패턴을 추측하거나 과거 URL을 재사용하지 않았다.

| clip·공식 page | currentauthor | current title | current grant | 공식 preview URL |
|---|---|---|---|---|
| [416677](https://freesound.org/people/FunWithSound/sounds/416677/) | FunWithSound | Baby Gibberish - Ah-Bah-Bah.wav | CC0-1.0 | https://cdn.freesound.org/previews/416/416677_6456158-lq.mp3 |
| [416681](https://freesound.org/people/FunWithSound/sounds/416681/) | FunWithSound | Baby Gibberish - Gee-Gee.wav | CC0-1.0 | https://cdn.freesound.org/previews/416/416681_6456158-lq.mp3 |
| [416635](https://freesound.org/people/FunWithSound/sounds/416635/) | FunWithSound | Baby Gibberish - Grunt 2.wav | CC0-1.0 | https://cdn.freesound.org/previews/416/416635_6456158-lq.mp3 |
| [416702](https://freesound.org/people/FunWithSound/sounds/416702/) | FunWithSound | Baby Laugh.wav | CC0-1.0 | https://cdn.freesound.org/previews/416/416702_6456158-lq.mp3 |
| [259609](https://freesound.org/people/Stevious42/sounds/259609/) | Stevious42 | Baby Babble and crinkle.wav | CC-BY-3.0 | https://cdn.freesound.org/previews/259/259609_4841074-lq.mp3 |
| [254454](https://freesound.org/people/wjoojoo/sounds/254454/) | wjoojoo | marvie_babble1.flac | CC-BY-4.0 | https://cdn.freesound.org/previews/254/254454_93137-lq.mp3 |
| [254455](https://freesound.org/people/wjoojoo/sounds/254455/) | wjoojoo | marvie_babble09.flac | CC-BY-4.0 | https://cdn.freesound.org/previews/254/254455_93137-lq.mp3 |
| [254465](https://freesound.org/people/wjoojoo/sounds/254465/) | wjoojoo | marvie_dada01.flac | CC-BY-4.0 | https://cdn.freesound.org/previews/254/254465_93137-lq.mp3 |
| [254471](https://freesound.org/people/wjoojoo/sounds/254471/) | wjoojoo | marvie_dada02.flac | CC-BY-4.0 | https://cdn.freesound.org/previews/254/254471_93137-lq.mp3 |

CC-BY source는 원작자·title·page URL·license URL·변경 사항의 attribution을 유지한다. archived grant는 source-audit.json의 개별 원음 license를 보존한다. FSD 주석은 annotation_license_url CC-BY4로 별도 유지한다. [기존 허락 감사](20261007-permissive-infant-voice-next.md)의 Freesound AI FAQ 계약을 따른다.

## 3. 원문 설명과 weak label 범위

| sourcegroup | 지정ID | current 설명 원문·해석 한계 |
|---|---|---|
| FSD50K:dev:FunWithSound |416677,416681|“A .WAV audio clip of an adorable small boy toddler/baby speaking meaningless words. The 'Baby Gibberish Misc.' pack is a collection of many miscellaneous baby sounds and nonsensical speech for movies and other projects. Royalty-free. :-)” 단어가 무의미하다는 caption이며 실제 옹알이·정확 월령·speaker identity·촬영 동기는 미검수 |
| 같은 FunWithSound |416635|“A .WAV audio clip of a small boy toddler/baby grunting happily. The 'Baby Gibberish' pack is a collection of many baby sounds and nonsensical speech for movies and other projects. Royalty-free. :-)” grunt형 rare vocal 후보. happily를 emotion/motivation 정답으로 쓰지 않음 |
| 같은 FunWithSound |416702|“A .WAV audio clip of a small male toddler/baby laughing.” laughter caption, 원음 웃음 여부 미검수 |
| FSD50K:dev:Stevious42 |259609|“babbling noises of a young baby”. title은 Baby Babble and crinkle.wav, archived raw는 Babbling+Baby_cry_and_infant_cry. 둘을 모두 실제 사건으로 승인하지 않음 |
| FSD50K:dev:wjoojoo |254454,254455|“marvie babbling”. caption의 marvie 이름/별칭·정확 나이·가족 관계 미확인 |
| 같은 wjoojoo |254465,254471|“marvie saying dada”. dada 소리/단어·의미·부모 호명·의도·발달 수준을 음성과 문구만으로 확정하지 않음 |

JSON에는 원문 full description와 raw tags/title을 저장했다. 표의 줄바꿈·공백 표기는 가독성을 위한 것이며 source HTML snapshot이 실제 원문 기준이다.

- FunWithSound는 movie/sound-effect 목적 pack이라고 설명하지만 **성인 연기라고 명시된 것은 아니다**. 반대로 실제 자연 아기라고 음성 검증된 것도 아니다. adult_acting_excluded_by_review=false, natural_child_vocal_verified=false를 유지한다.
- 지정9개의 current description에 pitch modification/synthesis/remix/adult acting 표현을 확인하지 못했다. 이는 해당 가공·성인 흉내의 **부재 입증이 아니다**. pitch_modification_excluded_by_review=false다.
- source 페이지 description 안에 다른 sound 원천을 인용하는 링크는9개 모두0이었다. 링크 부재로 독립 원저작권·가족·재게시 부재를 입증하지 않는다.
- Stevious42의 같은 uploader source에는259610 baby-monitor cry가 있다.259609의 crinkle/monitor/playback 여부·성인 겹침은 별도 검수할 channel/mix flag다.
- wjoojoo 같은 uploader의194535/194536 “My son laughing”을 새4개에 자동 전파하지 않는다. 동일 아기·session·나이 증거가 아니다.
- CC0/BY metadata grant는 명확하나 adult imitation/processing/ageunknown을 자동 배제한 clean infant target corpus는 아니다.

## 4. source 분리와 alias 확인

다음 manifest metadata만 비교했다. 음성 경로의 PCM이나 파일 body는 읽지 않았다.

1. 기존 [public33 manifest](../../work/ast-public-paired-01/corpus/manifest.json)의 author/source_group와 URL에 실제 기록된 exact sound IDs. 숫자가 기록되지 않은 NoiseCollector source clip ID는 추론하지 않았다.
2. [source-audit.json](../../work/infant-head-20261007/source-audit.json)의 original train NoiseCollector/reinsamba/Fugazi80/levinj와 held Corsica_S.
3. [subset-source-pages.json](../../work/infant-head-20261007/subset-source-pages.json)의 current author·old uploader·sourcegroup 및 validation source.
4. 기존 negative-source-audit/adult-nonverbal-sources의 metadata held/validation groups와 알려진 tim.kahn alias.

새 source3 모두 public33 author/source와 clip ID, 기존 positive train/held, 다른 기존 held alias 겹침0이다. 해당 checks는 각9개 JSON row에 저장했다. source-audit233에 세source가 이미 indexed인 점은 새로운데이터 source 발견으로 세지 않는다. **처음 실제 current page/preview 계약을 고정한 개발 후보 확장**이다.

| source | 개발 후보 수 | group 유지 계약 |
|---|---:|---|
| FunWithSound |4|전체 uploader48행(47 raw Babbling/1laughter)을 한source로 유지. 나머지44를 validation/test로 나누지 않음 |
| Stevious42 |1|같은 uploader의 다른4행도 sourcegroup 공유.259610 monitor 등을 다른split 독립 반례로 세지 않음 |
| wjoojoo |4|전체 uploader20행(12 raw Babbling/6cry/2laughter)을 한source로 유지. 다른16을 독립validation에 보내지 않음 |

기존 Corsica_S/tim.kahn·public33 held·negative held·eval source들의 역할은 그대로다. 새로운 source는 train 개발 후보만이며 새blind test는0이다. 향후 alias/파생 관계가 발견되면 전체 연결 그룹을 합쳐 재검토하고 음성취득/학습 전에 block한다.

## 5. HTTP와 권리 snapshot

이전 [representation 조사](20261007-next-infant-representation-evidence.md)의416677/416681/416702 병렬 HTML 조회는 TimeoutError로 batch가 종료됐고 개별 정상 결과를 확보하지 못했다. 그 시점의 timeout 기록은 유효한 이력이다. **이번9개는 모두 새 GET의 정상 결과**이며 각 attempt1에서 성공했다. 과거 timeout을 거짓 실패라고 지우거나 당시 취득가능성 입증으로 바꾸지 않았다.

| clip | 이번 HTML bytes | 정확 snapshot SHA256 | HTTP / 이번 attempt |
|---|---:|---|---|
| 416677 | 43,327 | 8c46eb61467c8babf7516a58fafb33a9f2045a3cff0d58377c9c05d3f23b2ce9 | 200 / 1회 |
| 416681 | 43,220 | c56e8ef77d3ab5272f4c31e1aec0893d08af03f0c4745b452807f4dff2132286 | 200 / 1회 |
| 416635 | 43,284 | a77d2e8af87b675c8989710c2e59d298919322a52a08a0e63a79d7a76ec752b9 | 200 / 1회 |
| 416702 | 43,463 | ab87fec847dce81de6adf0aed794de14f18a98802db016df7775f8a8b5a6ad68 | 200 / 1회 |
| 259609 | 38,847 | 16eb555ddc465403c05800e93000994bf1b0ccb761ae41769b714ae2c1a25efa | 200 / 1회 |
| 254454 | 37,647 | ebbd84d36ac9bc747e04681ae185595b1b8237eb2d977768534453fc01a4e22f | 200 / 1회 |
| 254455 | 37,660 | db984a0fcf0332bd16cc0c2b9d45821ce0b2e364ab38027dbc1e0535ecd49b55 | 200 / 1회 |
| 254465 | 38,333 | a4ae6c613a13e49c556cd0e465330e17c4f3513832cf4dfccde8f10499d2757e | 200 / 1회 |
| 254471 | 38,395 | fd547ac82b62a80676bc97d3d0d29c7264fe4e99a48de427da194cf1b27402d6 | 200 / 1회 |

HTML에는 동적 토큰 등이 있어 같은 페이지의 hash가 과거 snapshot과 달라질 수 있다. hash는 이번 읽은 bytes를 식별하며 지속 불변 page content를 보장하지 않는다. 라이선스/원작자/title/description/preview 속성의 실제 문자열은 JSON snapshot으로 따로 보존했다. **HTML bytes를 preview/audio bytes로 해석하지 않는다.**

## 6. 다음 개발 취득·검수 계약

1. 지정9개 중 합법적으로 취득 성공한 file만 raw bytes/hash/format/sample-rate와 preview/original 종류를 고정한다. 취득실패는 missing으로 기록하고9개 성공으로 보충하지 않는다.
2. local review에서 실제 child-vocal event 존재, 시작/끝, grunt/babble/laugh, 혼합·성인·TV/monitor·소품음·변조·불확실성을 기록한다. metadata가 옹알이라고 해도 모든 window를 양성으로 복제하지 않는다.
3. source3의 용도는 월령미상 공개 발성 개발 augmentation 후보로 제한한다. 정확6~18개월 영아/가족 독립성과 일반성능은 이 자료로 충족하지 않는다.
4. parent의 weakMIL 비교는 approved public bags만 사용하고 source별 기여를 제한한다. crinkle 한창이나 음질 cue가 infant positive를 대신 만족시키는 shortcut과 성인 비명 반례를 확인한다. 이 문서는 모델·threshold·학습의 승인이 아니다.
5. 기존 held source와 사용자 사적 원본·구간·feature/embedding을 train/calibration/augmentation에 넣지 않는다. 사적자료 외부업로드 금지와 기존 beta/product 상태를 유지한다.

**완료:**9개 current metadata·grant·author·official preview 경로·출처 분리 감사. **미완료:**음성취득 성공·청취/사건 정답·6~18개월 검증·학습·추론·mobile/product 채택.
