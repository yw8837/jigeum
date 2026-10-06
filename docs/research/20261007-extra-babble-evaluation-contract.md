# 추가 옹알이 9개 / 3 업로더 — 평가 전용 계약

작성일: **2026-10-07 KST**. 소유 범위는 이 문서 하나다. [기존 출처 감사](20261007-extra-babble-source-audit.md)의 metadata를 재사용하고 추가 반례 공식 HTML 두 페이지만 읽었다. 원음·preview body/HEAD·weight 취득, 청취·디코딩·추론·학습·사용자 미디어 접근·Git·빌드·ADB·외부 연락은 수행하지 않았다.

## 1. 현재 상태와 이번 용도

**상업 사용을 허용하는 개별 CC0/BY grant와 공식 preview 경로가 있는 공개 후보 9개, 서로 다른 metadata 업로더 그룹 3개를 확보했다.** 기존 public33·positive train/held·다른 기존 held 그룹과 알려진 업로더/alias/clip ID 겹침은 0이다. 확인 범위와 exact ID 누락 한계는 기존 감사에 남아 있다. 실제 자연 아기 발성·화자·가족·6~18개월을 검증한 source는 **0**이다. 따라서 독립 영아 세 가족의 검증 요건은 아직 충족하지 않는다.

현재 9개 모두 `not_downloaded_not_listened`, `training_blocked=true`, `approved_training_label=null`이며 event start/end·audio bytes/SHA·verified family/age가 없다. 이번 계약은 **동결한 head의 향후 weak caption 평가 후보**로 한정한다. 현재 실행 결과는 0개다. 원음 취득·청취 검수·평가 실행은 별도 후속 범위에서 진행하고 실제 로그를 남겨야 한다.

[기존 manifest](../../work/infant-head-20261007/extra-babble-sources.json)의 `role=train_development_candidate`, `subset_role=train`은 미승인 개발 후보를 기록한 이전 designation이다. 해당 필드를 학습 허락으로 해석하지 않는다. 이번 문서는 JSON을 변경하거나 기존 held/eval을 재배정하지 않았다. 후속 평가 도구는 이 9개를 평가 전용으로 명시한 별도 입력 계약과 `training_blocked`를 검사해야 하며, 현재 `subset_role=train`을 보고 자동 학습에 편입하면 계약 위반이다.

- manifest: **54,093B**, SHA256 `a7658a7a85688ed360377d5607101b31d1d1a115be96e7c58abc9aebbd3d045b`.
- 현재 grant: **CC0 4 / CC BY3 1 / CC BY4 4**. wjoojoo 4개는 archived BY3 → current BY4.
- current author 변경: 0. 기존 알려진 source/clip 겹침: 0. 전체 페이지의 fresh GET/ID/preview 속성 검사: 9.
- 페이지 검사 시각: 2026-10-06 17:41:07–17:41:10 UTC, 즉 2026-10-07 02:41 KST. 이 문서에서 새로 9개를 재조회한 것은 아니다.
- 이 grant는 저작권 허락의 확인이다. 음성 화자의 나이·자연성·제3자 권리·사건 정답까지 입증하지 않는다.

## 2. 파일별 원문 주장과 미확인 항목

아래의 “미기재”는 문구에 해당 주장이 없다는 뜻이다. 청취로 성인 연기·loop·변조가 없음을 확인한 뜻이 아니다. **자연성/연기 배제/loop 배제/정확 월령/혼합 여부는 9개 모두 미검증**이다. title·tags·raw FSD label은 서로 구분한다.

| ID / source | 설명이 명시하는 발성·화자 | 자연/연기·편집·loop 주장 | 나이 주장 | 혼합·출처 단서와 weak 평가 한계 |
|---|---|---|---|---|
| 416677 / FunWithSound | “small boy toddler/baby speaking meaningless words” | 자연 녹음·연기·loop·pitch는 미기재. 영화 및 다른 프로젝트용 pack이라는 목적 명시 | toddler/baby만 명시, 개월 수 없음 | baby gibberish caption. 효과음 목적을 성인 연기로 확정하지 않음. 실제 옹알이 경계·배경음 미확인 |
| 416681 / FunWithSound | 같은 “small boy toddler/baby speaking meaningless words” | 같은 영화/프로젝트 pack 문구, 자연/연기/loop/pitch 미기재 | toddler/baby만 명시 | 다른 파일이라고 다른 화자/가족/세션으로 세지 않음 |
| 416635 / FunWithSound | “small boy toddler/baby grunting happily” | 영화/프로젝트용 Baby Gibberish pack. 자연/연기/loop/pitch 미기재 | toddler/baby만 명시 | grunt caption으로 분리. happily를 감정·욕구 정답으로 쓰지 않음. Babbling raw label을 실제 옹알이로 확정하지 않음 |
| 416702 / FunWithSound | “small male toddler/baby laughing” | 선택 설명에 자연/연기/loop/pitch·영화 pack 문구 미기재 | toddler/baby만 명시 | laughter caption. 같은 uploader의 pack 맥락은 가능성만 남김. 옹알이 평가 성공 수에 웃음을 혼합하지 않음 |
| 259609 / Stevious42 | “babbling noises of a young baby” | 자연/연기/loop/pitch 미기재 | young baby만 명시 | title “Baby Babble and crinkle.wav”는 crinkle 혼합을 명시. raw Babbling+Baby_cry_and_infant_cry는 사건 정답이 아님 |
| 254454 / wjoojoo | “marvie babbling” | 자연/연기/loop/pitch 미기재 | 선택 설명에 baby·숫자 나이 없음; archived tags는 infant/baby/child | 이름/별칭과 옹알이 caption만 확인. tags는 weak infant 단서. 선택 화자·아기 관계 미확인 |
| 254455 / wjoojoo | “marvie babbling” | 자연/연기/loop/pitch 미기재 | 선택 설명에 baby·숫자 나이 없음; archived tags는 infant/baby/child | 같은 이름·업로더를 동일 화자/세션의 입증으로 쓰지 않음 |
| 254465 / wjoojoo | “marvie saying dada” | 자연/연기/loop/pitch 미기재 | 선택 설명에 baby·숫자 나이 없음; archived tags는 infant/baby/child | dada caption/raw Babbling. 의미 있는 단어·부모 호명·요청·발달 상태 정답이 아님 |
| 254471 / wjoojoo | “marvie saying dada” | 자연/연기/loop/pitch 미기재 | 선택 설명에 baby·숫자 나이 없음; archived tags는 infant/baby/child | 같은 dada caption의 별도 파일이며 독립 아기로 세지 않음 |

FunWithSound 416677/416681의 full 설명은 “A .WAV audio clip of an adorable small boy toddler/baby speaking meaningless words. The 'Baby Gibberish Misc.' pack is a collection of many miscellaneous baby sounds and nonsensical speech for movies and other projects. Royalty-free. :-)”다. 416635도 같은 목적의 Baby Gibberish pack이라고 적는다. 이 목적은 자연 발성이나 성인 흉내 중 어느 쪽의 입증도 아니다.

Stevious42의 다른 파일 259610은 baby monitor 맥락을 설명한다. 이를 259609의 녹음 장치·TV/monitor 재생 증거로 전파하지 않는다. wjoojoo의 다른 파일 194535/194536에 “My son laughing”이 있어도 선택 4개의 화자·나이·가족 관계를 입증하지 않는다. 선택 문구에 가공이나 성인 연기가 없다는 이유로 clean infant label을 승인하지 않는다.

## 3. 정확한 공개 취득 경로와 권리

다음 preview는 기존 공식 페이지 HTML의 `data-mp3`를 읽은 값이다. body의 접속 성공·MP3 동일성·PCM·duration은 미확인이다. original WAV/FLAC을 받았다고 표현하지 않는다. 이번 후속에 쓸 경우 preview/original 종류와 실제 bytes/hash를 구분해야 한다.

| 공식 원작자 페이지 / title | 현재 개별 grant | 공식 preview 경로 |
|---|---|---|
| [FunWithSound / 416677 — Baby Gibberish - Ah-Bah-Bah.wav](https://freesound.org/people/FunWithSound/sounds/416677/) | [CC0 1.0](http://creativecommons.org/publicdomain/zero/1.0/) | https://cdn.freesound.org/previews/416/416677_6456158-lq.mp3 |
| [FunWithSound / 416681 — Baby Gibberish - Gee-Gee.wav](https://freesound.org/people/FunWithSound/sounds/416681/) | [CC0 1.0](http://creativecommons.org/publicdomain/zero/1.0/) | https://cdn.freesound.org/previews/416/416681_6456158-lq.mp3 |
| [FunWithSound / 416635 — Baby Gibberish - Grunt 2.wav](https://freesound.org/people/FunWithSound/sounds/416635/) | [CC0 1.0](http://creativecommons.org/publicdomain/zero/1.0/) | https://cdn.freesound.org/previews/416/416635_6456158-lq.mp3 |
| [FunWithSound / 416702 — Baby Laugh.wav](https://freesound.org/people/FunWithSound/sounds/416702/) | [CC0 1.0](http://creativecommons.org/publicdomain/zero/1.0/) | https://cdn.freesound.org/previews/416/416702_6456158-lq.mp3 |
| [Stevious42 / 259609 — Baby Babble and crinkle.wav](https://freesound.org/people/Stevious42/sounds/259609/) | [CC BY3](http://creativecommons.org/licenses/by/3.0/) | https://cdn.freesound.org/previews/259/259609_4841074-lq.mp3 |
| [wjoojoo / 254454 — marvie_babble1.flac](https://freesound.org/people/wjoojoo/sounds/254454/) | [CC BY4](https://creativecommons.org/licenses/by/4.0/) | https://cdn.freesound.org/previews/254/254454_93137-lq.mp3 |
| [wjoojoo / 254455 — marvie_babble09.flac](https://freesound.org/people/wjoojoo/sounds/254455/) | [CC BY4](https://creativecommons.org/licenses/by/4.0/) | https://cdn.freesound.org/previews/254/254455_93137-lq.mp3 |
| [wjoojoo / 254465 — marvie_dada01.flac](https://freesound.org/people/wjoojoo/sounds/254465/) | [CC BY4](https://creativecommons.org/licenses/by/4.0/) | https://cdn.freesound.org/previews/254/254465_93137-lq.mp3 |
| [wjoojoo / 254471 — marvie_dada02.flac](https://freesound.org/people/wjoojoo/sounds/254471/) | [CC BY4](https://creativecommons.org/licenses/by/4.0/) | https://cdn.freesound.org/previews/254/254471_93137-lq.mp3 |

BY attribution에는 uploader·title·page·license URL·변경 사항을 보존한다. FSD50K 주석 BY4와 개별 원음 grant는 별도다. archived/current grant와 HTML bytes/SHA/원문 snapshot은 기존 JSON 및 [감사 §5](20261007-extra-babble-source-audit.md)에 있다. 권리·author·preview가 바뀌거나 사라지면 해당 file을 missing/blocked로 남기고 숫자를 채우기 위해 다른 파일을 임의 대체하지 않는다.

## 4. source 분리와 결과 노출 계약

| 보수적 source ID | 지정 파일 수 | source 단위 유지 |
|---|---:|---|
| FSD50K:dev:FunWithSound | 4 | 전체 uploader 48행(47 raw Babbling / 1 laughter)을 연결 그룹으로 유지. 나머지 44개를 별도 독립 test로 세지 않음 |
| FSD50K:dev:Stevious42 | 1 | 전체 5행을 같은 source로 유지. monitor 259610 등 다른 4개를 독립 source로 세지 않음 |
| FSD50K:dev:wjoojoo | 4 | 전체 20행(12 raw Babbling / 6 cry / 2 laughter)을 같은 source로 유지. 다른 16개를 독립 source로 세지 않음 |

세 그룹의 caption과 경로는 이미 조사자가 보았다. 아직 모델 score/원음은 노출되지 않았지만 이 구성을 blind family test라고 부르지 않는다. alias·재게시·동일 화자·파생 관계가 추가로 발견되면 연결 그룹 전체를 합치고 기존 train/held overlap을 재검사한다. source가 train과 연결되면 독립 평가 분모에서 빼고 그 이유를 보존한다.

이 9개 및 같은 연결 source의 다른 파일을 이번 평가의 training·feature normalization fitting·calibration·L2 선택·threshold 선택·early stopping·augmentation에 사용하지 않는다. 결과를 본 뒤 수정한 head는 이 자료를 개발 자료로 소비한 것이므로 이후 새 독립 평가 source가 필요하다. 기존 Corsica_S·Reitanna·다른 held/eval 역할과 사용자 사적 원본/구간/features의 평가 전용 계약을 유지한다.

## 5. 평가 전에 고정할 AST768 실행 상태

[AST head 계약](20261007-ast-infant-head-contract.md)과 실제 `ast-head35-01/fit.json`, `selection.json`, `completion.json`을 읽어 아래 동결값을 확인했다. weight body는 읽지 않았다.

- encoder SHA256: `3dff0cc2b374977ae1460258261608e33428f4767a6959cb8bc910b09433f71e`.
- head SHA256: `81d7357c4c59f57222952a1542716f7d89e423d45f2987dd3917a3522bdfd510`.
- public35 source SHA256: `27966aa9265da239489e7351959c01399ed3a0aed6428149babc2cb127fad4b9`.
- existing features SHA256: `359083b64f4db2ac29059be5430e773ea99e8fc423952bbb0e5ecf1940e0c330`.
- 실제 선택 L2 **0.0001**, steps 300 / lr .03 / 768→1 / 769 parameters. 다른 YAMNet/MIL 실험의 .001을 전파하지 않는다.
- score support **p≥.8**, binary margin≥.4. calibrated 정답확률 아님.
- 기존 PCM16 mono16k / 첫20초 / 비중첩10초 최대2 spans / 1초 미만 tail은 끝에만 waveform zero padding. 실제 sample 수·padding·RMS·SHA를 기록한다.
- nonquiet RMS≥.003 span의 actual-sample 가중평균 → clip L2. quiet-only는 score를 만들지 않고 별도 abstain으로 보고한다. quiet-only를 성능 분모에서 조용히 없애지 않는다.
- 기존 frontend/runtime와 527 logits 동일성·FP64/저장FP32 차이≤5e-5를 유지한다. gain·DC·pitch·loop·추가 crop 선택을 새 자료에만 적용하지 않는다.
- fit code SHA: `abc1d046ab8379c3706c28870478e27012e5aa0c03672f2e8fb56137867ff349` (`ast_infant_head_probe.py`, 당시 completion 기록).

첫 결과에서 부모 작업자가 보고한 약국 두 10초 bag score .8198/.9400은 **같은 사적 원본의 겹치는 개발 구간**이다. public35 held infant 2/4 support, negative 10/10 abstain, 기존 public33 음악/성인 새 false approval 0도 현재 개발 관측이며, 이번 문서는 사적 결과를 다시 읽거나 재추론하지 않았다. 짧은 옹알이는 아직 회복되지 않았다. 이 결과를 이유로 extra9에 맞춰 임계값·학습을 변경하거나 제품 품질을 승인하지 않는다.

## 6. 향후 weak caption 평가 출력 계약

후속에서 취득·실행하더라도 우선 **caption과 broad clip score의 일치 정도**만 보고한다. 9개의 metadata가 실제 infant event ground truth가 된 것으로 처리하지 않는다.

1. 요청 9 / 실제 취득 / 실제 decode / scoring / quiet-only / missing / blocked 수와 각 이유를 모두 기록한다. 요청 수를 성공 수로 대체하지 않는다. manifest ID·source·page·grant·preview/original·raw bytes/SHA·decoded 실제 samples·padding·head/config SHA를 연결한다.
2. 파일별 `caption_category`(babble/gibberish, grunt, laugh, dada)와 `speaker_claim_strength`(explicit toddler/baby, tags only), `age_verified=false`, `naturalness=unknown`, `mixed=explicit_crinkle_or_unknown`을 score와 함께 보존한다. wjoojoo를 verified infant positive로 바꾸지 않는다.
3. 고정 threshold의 clip support/abstain와 실제 span별 score를 공개한다. 파일 9개 분모와 source 3개 묶음별 결과를 모두 보이며, 웃음/신음/dada를 옹알이 회복 건수로 합치지 않는다. 같은 uploader 파일이 많아도 독립 화자 수는 증가하지 않는다.
4. 자연 아기 recall·6~18개월 sensitivity·event duration recall·false approvals/minute는 **미산출**이다. 사건 경계·실제 화자 정답이 없고 broad10초 점수는975ms 사건이 아니다. 높은 score의 span 전체를 실제 아기 발성 시간으로 표시하지 않는다.
5. 별도 승인된 local review를 진행하면 모델 score를 보기 전에 실제 발성 구간과 uncertain/overlap/adult/prop/playback/processed flags를 기록한다. 청취만으로 정확 월령·가족·발성 동기를 확정하지 않는다. 불일치/불확실을 infant label로 채우지 않는다. 이 계약 자체는 청취나 학습 실행 승인이 아니다.
6. weak caption 실패·미취득·불확실·반례 오인을 그대로 보고한다. 좋은 세 파일만 골라 성공 표를 만들지 않는다. 성인 nonverbal·음악·TV/재생 및 독립 영아 사건의 별도 gate는 계속 남는다.

## 7. 기존 반례 재사용과 공식 권리 경로 두 건

기존 [negative-source-audit](../../work/infant-head-20261007/negative-source-audit.json)의 Reitanna 242661 “really.wav”, 242688 “laugh that was funny.wav”는 **archived CC0**와 “I record my voice while playing video games” 설명이 있다. 같은 Reitanna 241477은 이미 validation이다. 추가 두 파일도 기존 held source 맥락이며 새 source나 train으로 재배정하지 않는다. 두 페이지의 current grant/preview는 이번에 조회하지 않았고 기존 JSON 값도 null이므로 취득은 blocked다. 자기 목소리+게임 맥락은 실제 게임 소리 혼합·성인 정확 나이·TV/news 출처의 입증이 아니다.

추가로 기존 index에서 두 공식 페이지를 새 GET했다. **HTTP200만으로 유효한 sound/허락/취득 성공을 인정하지 않는다.** 아래 snapshot은 HTML이며 audio bytes/hash가 아니다. HTML 전체를 파일로 저장하지 않았고 이 문서에 추출값과 hash를 남긴다.

| 후보 / 실제 페이지 결과 | archived → current grant / 공식 경로 | 사용할 수 있는 주장 / blocked 범위 |
|---|---|---|
| [gipsyGA 128479](https://freesound.org/people/gipsyGA/sounds/128479/), title “GrandPa004(2.5s).mp3”, currentauthor gipsyGA, og:url/ID 일치 | archived BY3 → current [BY4](https://creativecommons.org/licenses/by/4.0/); `data-mp3=https://cdn.freesound.org/previews/128/128479_2331961-lq.mp3` | “Some gibberish I recorded thinking of making it the voice of a cartoon grandpa.” 본인 gibberish와 cartoon-grandpa 목적의 weak confuser. 정확 성인 나이·자연성·아기 흉내·TV/news는 미확인. preview body·청취·infant absence 미검수. 평가 후보만, 학습 불허 |
| [rubekolo 393504](https://freesound.org/people/rubekolo/sounds/393504/), HTTP200지만 h1 “Deleted sound” | archived BY3. current author/description/license anchors/og:url/preview는 모두 없음 | archived “30 y.o. female says WELLWELLL”은 명시적인 성인 나이 caption이지만 current 권리·취득 경로를 확인하지 못함. **blocked**, 임의 mirror/추측 preview로 우회하지 않음. 기존 FSD eval 역할 유지 |

gipsyGA의 exact current license anchor는 `href="https://creativecommons.org/licenses/by/4.0/"`, `title="Go to the full license text"`, 표시 “Attribution 4.0”이었다. 본문은 위 인용과 동일했다. 128484도 같은 업로더이므로 128479를 사용한 뒤 독립 반례로 세지 않는다. 이는 성인 baby imitation을 검증한 자료의 발견으로 보고하지 않는다.

| GET snapshot | UTC / KST | HTML bytes | SHA256 |
|---|---|---:|---|
| gipsyGA 128479, HTTP200 | 2026-10-06 18:17:33 UTC / 2026-10-07 03:17:33 KST | 36,279 | `3090e6d2badbc43b7034a2358929f54047159ed8b8b66741dba6a10ec37c07a8` |
| rubekolo 393504, HTTP200 / Deleted sound | 2026-10-06 18:17:35 UTC / 2026-10-07 03:17:35 KST | 15,825 | `7b56a52473ce5f967b88b5e9b1082078a57d3f90b89c14a32b28b5751838515c` |

기존 index의 Puniho 115378은 Google Translate babytalk를 Wavelab에서 가공했다는 설명이다. uploader BY만으로 제3자 음성 출력 권리를 해결하지 못하므로 합법적인 baby imitation 반례의 대체물로 승인하지 않는다. Creeper_Ciller78 317359의 self voice/pitch change도 자연 발성 정답으로 대체하지 않는다.

**상업 이용 가능한 방송 원천까지 확인된 실제 TV/news 반례는 이번에 확보하지 못했다.** 공개 업로드·uploader CC만으로 TV 방송/뉴스 원본 권리를 가정하지 않는다. gipsyGA 직접 목소리나 Reitanna 게임 녹음으로 TV/news gate를 충족했다고 보고하지 않는다. 별도 권리와 재생 경로가 확인된 반례·독립 자연 영아 사건·ARM/Android 검증 전에는 head 채택/제품 정확도 개선을 승인하지 않는다.

## 8. 완료와 남은 작업

**완료:** extra9의 평가 전용 계약, 3개 metadata source 분리, 파일별 명시/미기재/미검수 구분, exact 공개 권리·preview 경로 연결, 현 AST head 동결값 확인, 기존 negative 권리 경로와 추가2페이지 확인.

**남음:** 9개 실제 취득/내용/PCM 무결성·자연/연기/변조/혼합 검수·정확 월령/가족·사건 정답·평가 실행·상업권 확인된 TV/news/baby imitation 반례·새 독립 영아 일반화·Android·제품 채택. **학습 승인과 성능 승인 모두 없음.** 제품과 배포 상태는 기존 HANDOFF를 따른다.
