# AST 영아 존재 head: Android 출력 범위와 상업 배포 근거

조사일 **2026-10-07 KST**. 최신 [HANDOFF](../HANDOFF.md), [extra9 첫 평가](20261007-extra-babble-evaluation-results.md), 실제 학습 metadata·연구 코드 및 아래 공식 원문을 읽었다. 담당 파일은 이 문서 하나다. 앱 구현·빌드·ADB·Git·사용자 원본 접근·청취·추론·학습·calibration·원음/weight 다운로드·외부 연락은 수행하지 않았다. 웹에서는 모델 카드/코드/권리 문구와 **6,984B짜리 FSD 문서 ZIP**만 읽었다. 이는 음원·모델 취득이 아니다.

## 1. Android 보완에 사용할 수 있는 근거

동결 head는 **넓은 입력 구간에서 영아 발성과 비슷한 음향 단서가 있는지**에 대한 연구 후보다. 기존 약국 두10초 구간의 support는 회복했지만 같은 원본의 겹치는 개발 구간이며, 독립 영아 두 명의 회복이 아니다. 추가 공개9 중 support4는 **웃음1 / babble+crinkle 혼합1 / dada2**다. gibberish/babbling/grunt caption의 나머지5는 보류했다. 따라서 일반 옹알이 detector·욕구 번역·정확한 발성 시점이나 화자 귀속을 승인하는 결과는 아니다.[L1,L2]

| 근거 | 실제로 확인한 범위 | Android에서 남겨야 할 제한 |
|---|---|---|
| public35 학습21 / 18 uploader source | 영아 weak caption7 / 4source, 나머지14는 성인/기타. loss는 infant_vocal 여부의 이진값 | positive 내부의 cry/laugh/babble/grunt를 구별하는 출력 없음. uploader는 검증된 가족/화자 ID가 아님 |
| 약국 .8198 / .9400 | 한 사적 원본의 겹치는10초 broad support | 975ms 사건·새 source 일반화·실제 onset/offset·욕구 정확도 아님 |
| public35 held infant2/4 / negative10/10 abstain | caption과 고정 score 관계 | 대상 월령·자연 발성·성인 TV에 대한 population accuracy 아님 |
| public9 / 3 uploader | 4 support, 5 abstain, 모든 월령·자연성 미확인. wjoojoo infant는 tags-only | 4/9 정확도나 세 독립 아기 가족 검증으로 쓰지 않음 |
| gipsyGA128479 .073074 | 본인 cartoon-grandpa gibberish caption 한 파일 보류 | 실제 성인 나이·baby imitation·TV/news·infant absence 정답 미검수 |
| 기존 public33 성인/음악 대조 새 support0 | 기존 개발·합성·중복 source 회귀 범위 | 새 성인/재생/혼합 소리를 모두 차단했다는 결론 없음 |

이 문서는 원 사적 결과를 열지 않고 공개된 연구 보고를 인용했다. 새 head가 놓친 기존 울음·짧은 옹알이가 있으므로 기존 YAMNet/AST의 승인 경로와 분류를 보존하는 **보완 후보**로 검증해야 한다. 기존 subtype·문장·저장을 generic 존재 결과로 덮어쓰면 안 된다.[L1,L2]

## 2. 존재 점수에서 subtype·욕구가 나오지 않는 이유

### 2.1 실제 학습 목표

현재 [ast_infant_head_probe.py](../../tools/research/ast_infant_head_probe.py)의159행은 `labels = ... float(c['weak_class']=='infant_vocal')`로 target을 만든다. `presence_weights`는 class/source/clip 균등가중을 만들며, cry/laugh/babble/의도 label을 별도 출력으로 학습하지 않는다. 입력768 → weight768+bias1 → sigmoid1이다. fit metadata에는 `weak_labels_only=true`, `calibrated_probability=false`가 명시되어 있다.[L3]

양성에 웃음·울음 계열·gibberish가 함께 있으므로 높은 score는 그 종류를 식별하지 못한다. `.8`은 결정 기준이며 “아기일 확률80%”, “정확도80%”로 표시할 근거가 없다. positive caption9/9가 실제 양성 정답으로 승인된 것도 아니다.

### 2.2 원 ontology도 다른 사건을 구분한다

AudioSet 공식 [ontology.json](https://github.com/audioset/ontology/blob/master/ontology.json)은 다음을 별개로 정의한다.[P3]

| 사건 | 공식 MID / 핵심 정의 | 존재 head로 대신 확정할 수 없는 부분 |
|---|---|---|
| Babbling | `/m/0261r1`, “Speech-like sounds ... lack the deeper structure and meaning of conventional speech” | 존재 여부만으로 speech-like structure·옹알이 사건을 입증하지 못함 |
| Child speech, kid speaking | `/m/0ytgt`, 성인 목소리와 아직 닮지 않은 human child의 speech | 정확6~18개월이라는 정의가 아님 |
| Baby laughter | `/t/dd00001`, baby/infant가 내는 laughter | 웃음이라는 subtype과 baby 화자 둘 다 별도 근거 필요 |
| Baby cry, infant cry | `/t/dd00002`, young child crying/bawling | crying 사건·원인/욕구는 서로 다른 주장 |

최신 extra9에서 웃음과 dada가 통과한 사실은 이진 target의 넓은 범위와 일치한다. 존재 score를 `BABBLE`, `CRY`, `LAUGH` 중 하나로 임의 매핑하면 학습 목표와 다른 label을 만들어내는 것이다. 기존 모델이 독립적으로 승인한 subtype은 기존 출처와 함께 유지할 수 있지만 새 score로 승인되지 않은 subtype을 승격하면 안 된다.

학습자료에는 배고픔·졸림·놀아 달라·고통·불안·부모 호명·욕구 정답이 없다. title/description의 “happily”, “dada”, “trying to express ... feelings”를 이런 target으로 소급 바꾸지 않는다. 일반 관찰에 맞는 요청형 표현을 제공하더라도 실제 아기 의도 번역 정확도나 의료 판단이라고 보고하지 않는다.[L4,L5]

## 3. 10초 support가 정확한 onset을 만들지 못하는 이유

### 3.1 원 AST는 전체 입력의 classification 표현이다

공식 AST [forward](https://github.com/YuanGongND/ast/blob/31088be8a3f6ef96416145c4b8d43c81f99eba7a/src/models/ast_models.py#L165)는 transformer 이후 cls/dist token 두 개를 `(x[:,0]+x[:,1])/2`로 평균하고 classification head에 넣는다(184–186행). 출력은 sample별527 class이며 frame별 event start/end 출력이 아니다.[P1]

AudioSet 공식 설명은 YouTube의 **human-labeled10초 clips**이며, download CSV의 `start_seconds/end_seconds`는 annotation 대상 segment를 지정한다. 예제420–430초는 해당10초에 labels가 존재한다는 의미다. 그 사건이420초에 시작해430초에 끝났다는 뜻이 아니다.[P2]

FSD50K 공식 README도 “Ground truth labels are provided at the clip-level (i.e., weak labels)”라고 명시한다. uploader 단위 dev/eval 분리는 화자·월령·구간 경계를 입증하지 않는다. 현재 head는 original44.1kHz WAV 전체를 받은 방식도 아니며 공식 preview MP3의 첫20초를 실제 연구 전처리로 쓴다.[P4,L4]

### 3.2 현재 head에도 시간 정답과 출력이 없다

연구 코드 `spans`는 비중첩10초/최대20초를 자르고 실제 sample 수를 기록한다. 학습은 nonquiet span 표현의 실제 sample 수 가중평균 → clip L2이며 event timestamps를 supervision하지 않았다. `score_spans`도 “Full-context support is never converted to actual infant vocalization duration”라고 명시한다.[L3]

Android의0–10 /5–15 /10–20초 중첩3창은 서로 겹치는 **분석 맥락**이다. 같은0–20초에서 세 support를 얻어도 독립3회 사건·세 번 발성·연속20초·onset5초라는 증거가 생기지 않는다. 가장 높은 창·창 경계·RMS peak·기존 후보 중 하나를 골라 정확 onset으로 꾸미는 것도 이 head가 검증한 기능이 아니다.

| Android 데이터/화면 사용 | 허용되는 기록 | 부적절한 확장 |
|---|---|---|
| support 범위 | source=head, 실제 input span, score, broad scope, sample/padding/quiet 상태 | input start/end를 실제 vocal event start/end로 저장 |
| 발성 시간 | exact onset/offset·duration은 없음/미확인 | 10초 support를10초 아기발성, 중첩합계를30초로 표시 |
| 결과 분류 | generic infant-like vocal clue의 별도 kind/evidence | BABBLE/cry/laugh event·장면 단계로 치환 |
| 시각 결합 | 기존 승인 YAMNet975ms 사건의 독립 시간 근거 유지 | broad10초 score를 얼굴/입/표정과 시간상 귀속하거나 뒤 표정을 앞 소리 동기로 연결 |
| phase/문장 | broad 관찰의 출처와 불확실 범위를 보존 | “그때 웃으며 불렀어요”, “점점 요청이 강해졌어요” 등 순서/동기 생성 |
| 저장/내보내기 | 문장·선택 구간과 실제 source/scope 보존 | legacy BABBLE 출처로 위장하거나 exact event로 내보내기 |

이는 Android 구현 인터페이스 이름을 강제하는 제안은 아니다. 핵심은 **kind·source·temporal scope를 subtype/정확 사건과 분리하고 exact time이 없는 사실을 데이터에서 보존**하는 것이다. 현재0.5.18의 시각 연결은 YAMNet975ms 안의 연속 유효 단일 얼굴 관찰을 쓰며, AST broad는 시간 근거가 없어 제외한 상태다. head를 추가해도 그 제외 이유가 사라지지 않는다.[L6]

## 4. 현장 아기·성인 흉내·TV 재생 귀속 제한

현재 head의 입력은 mono PCM에서 나온768 음향 표현뿐이다. live/TV/playback label, source microphone distance, speaker identity, 얼굴/입 움직임을 학습/판정하는 경로는 없다.[L3] 동일 PCM을 직접 입력하거나 TV에서 재생한 녹음을 같은 PCM으로 입력하면 함수는 같은 값을 낸다. **소리의 infant-like 단서와 그 소리를 현재 화면의 실제 아기가 냈는지는 다른 검증 항목**이다.

- gipsyGA 반례는 “cartoon grandpa” 목적의 자기 gibberish다. baby imitation이라고 명시하지 않았으며 이 한 파일을 보류해도 성인이 아기 발성을 흉내 내는 전체 경우를 검증하지 못한다.[L5]
- 기존 후보 index의 성인/자기 목소리 pitch change, Google Translate babytalk는 raw Babbling metadata가 infant identity 정답이 아님을 보여 준다. 이들은 head 학습21에 포함되지 않았다. 원본·제3자 권리와 실제내용 검수 없이 새로운 합법 반례로 승인하지 않는다.[L4,L5]
- 기존 Reitanna는 본인 목소리를 게임 중 녹음했다는 caption이다. 게임 소리의 존재·TV/news·성인 정확 나이까지 확정되지 않았다. 실제 TV/news 원천 권리와 재생 경로가 확인된 평가 gate는 남아 있다.[L5]
- 성인 웃음/비명·악기·TV에서 재생된 아기 목소리·성인과 아기 겹침에서 존재 score만으로 현장 아기를 선택하거나 기존 negative/conflict 근거를 무시하면 안 된다. 얼굴이 하나 보인다는 사실만으로 broad sound의 화자를 그 얼굴에 귀속하지 않는다.

화면 문구도 관찰 범위를 지켜야 한다. 예: **“아기 목소리로 들리는 소리 단서가 있어요.”**는 모델 관찰 표현이다. “아기가 옹알이를 시작했어요”, “아기가 엄마를 부르며 놀아 달래요”, “화면 속 아기가 계속 말하고 있어요”는 현재 head가 검증하지 않은 종류·시점·화자·욕구를 추가한다. 기술 caveat를 모든 사용 흐름에 길게 넣을 필요는 없지만 상세 근거와 저장 출처에서는 broad 범위를 유지해야 한다.

## 5. 상업 배포: 원 AST와 새 tiny head의 권리 경로

### 5.1 원 AST 코드·배포 weight

현재 앱 notice가 가리키는 고정 배포 모델은 `MIT/ast-finetuned-audioset-10-10-0.4593`, revision `f826b80d28226b62986cc218e5cec390b1096902`다. 이번에 해당 revision의 공식 HF API와 README를 읽었으며 둘 다 **bsd-3-clause**를 표시했다. README는 HF team이 model card를 작성했다고 명시하며 original AST 저장소를 원천으로 연결한다.[P5]

공식 AST [LICENSE](https://github.com/YuanGongND/ast/blob/31088be8a3f6ef96416145c4b8d43c81f99eba7a/LICENSE)는 Copyright2021 Yuan Gong, BSD3이며 source/binary를 **with or without modification** 재배포·사용할 수 있다고 적는다. NC나 연구전용 조건은 없다. binary distribution에는 copyright·조건·disclaimer를 documentation/other materials에 재현해야 하며 저자/기여자 이름을 제품 추천·보증으로 사용할 수 없다.[P1]

[기존 AST_MODEL_NOTICE.txt](../../outputs/MoaAndroid/app/src/astProbe/assets/AST_MODEL_NOTICE.txt)는 revision, original repo/card, opset17·dynamic QInt8 파생, 기존 graph SHA, BSD3 전문과 일반 AudioSet/영아·욕구 미검증을 이미 담고 있다. **기존 notice가 새 head 학습자료 attribution까지 담은 것은 아니다.** 앱에 실제 새 graph를 넣을 때는 원 BSD3 전문을 유지하고 새 derivative SHA/작은 presence head 추가·공개 학습 출처를 정확히 추가해야 한다.[L7]

현재 연구 graph는 기존 AST91,134,123B에4,474B를 더한91,138,597B이며 SHA `4a378476dcbe5f6b13c5f67295797d59376dab3627fe3dbd56bfeeadbd7f802a`다. 작은 head769FP32 parameters는3,076B지만 전체 모델의 크기·메모리 비용을3KB라고 표현하면 안 된다. 원527 logits/기존 node 유지·NumPy score parity는 연구 검증이며 영아 품질·ARM 성능·권리 검수의 대체물이 아니다.[L2]

AudioSet의 공개 segment annotations와 YouTube 원본 저작권은 별개다. 이번 권리 근거는 실제 배포 모델의 BSD 표기와 원 code LICENSE다. 이 조건이 AudioSet의 모든 원영상·방송을 재배포할 권리를 제공한다고 확장하지 않는다.[P1,P2,P5]

### 5.2 FSD50K 주석과 개별 음원

공식 FSD 문서의 License 절은 **각 clip에 uploader가 정한 CC0/BY/BY-NC/Sampling+ 권리**가 있으며, 그와 별도로 curated dataset 전체에 **CC BY4**가 적용된다고 명시한다. dataset BY4로 모든 개별 NC 음원을 상업 이용할 수 있는 것은 아니다.[P4]

Freesound 공식 AI FAQ의 현재 원문은 “yes as long as individual sounds' Creative Commons licenses are respected”, CC-BY attribution 제공, commercial training에서 BY-NC 제외를 요구한다. 이 head는 discriminative score를 계산하는 모델이며 소리를 생성·복제·voice clone하는 GenAI 모델을 학습하지 않았다. 같은 FAQ의 GenAI preferences·voice cloning 추가 권리 설명을 판별 head에 대한 일괄금지나 일괄면책으로 바꾸지 않는다.[P6]

실제 학습 input manifest `execution-sources35.json`의 `role=train` **21개 /18source**를 확인했다. current grant는 **CC0 8 /BY3 5 /BY4 8**, positive7/negative14다. 전처리·features·fit metadata는 이 manifest SHA `27966aa9265da239489e7351959c01399ed3a0aed6428149babc2cb127fad4b9`에 연결된다. current Freesound preview에 대한 이전 공식 페이지 snapshot을 재사용했고 이21페이지를 이번에 다시 GET한 것은 아니다.[L8]

| 실제 train ID / 공식 page | current author | title | current grant |
|---|---|---|---|
| [113220](https://freesound.org/people/NoiseCollector/sounds/113220/) | NoiseCollector | 2tone.wav | [BY3](http://creativecommons.org/licenses/by/3.0/) |
| [113221](https://freesound.org/people/NoiseCollector/sounds/113221/) | NoiseCollector | ahdada.wav | [BY3](http://creativecommons.org/licenses/by/3.0/) |
| [113223](https://freesound.org/people/NoiseCollector/sounds/113223/) | NoiseCollector | ahhhhahaha.wav | [BY3](http://creativecommons.org/licenses/by/3.0/) |
| [46963](https://freesound.org/people/reinsamba/sounds/46963/) | reinsamba | baby_voice6.wav | [BY4](https://creativecommons.org/licenses/by/4.0/) |
| [47371](https://freesound.org/people/reinsamba/sounds/47371/) | reinsamba | baby_laugh2.wav | [BY4](https://creativecommons.org/licenses/by/4.0/) |
| [377237](https://freesound.org/people/Fugazi80/sounds/377237/) | Fugazi80 | Æ.20161218.wav | [CC0](http://creativecommons.org/publicdomain/zero/1.0/) |
| [8157](https://freesound.org/people/levinj/sounds/8157/) | levinj | Jazzgiggle-10-8-05(9mo).mp3 | [BY3](http://creativecommons.org/licenses/by/3.0/) |
| [184432](https://freesound.org/people/unfa/sounds/184432/) | unfa | Yo YO yo yO | [CC0](http://creativecommons.org/publicdomain/zero/1.0/) |
| [168494](https://freesound.org/people/AlienXXX/sounds/168494/) | AlienXXX | Build_it_and_They_will_come_a.wav | [BY4](https://creativecommons.org/licenses/by/4.0/) |
| [87680](https://freesound.org/people/flogman/sounds/87680/) | flogman | e.wav | [CC0](http://creativecommons.org/publicdomain/zero/1.0/) |
| [122204](https://freesound.org/people/dobroide/sounds/122204/) | dobroide | 20110612.bye.wav | [BY4](https://creativecommons.org/licenses/by/4.0/) |
| [256684](https://freesound.org/people/xtrgamr/sounds/256684/) | xtrgamr | PIANO LOOP.wav | [BY4](https://creativecommons.org/licenses/by/4.0/) |
| [412332](https://freesound.org/people/clemza/sounds/412332/) | clemza | prepared piano loop 2.wav | [CC0](http://creativecommons.org/publicdomain/zero/1.0/) |
| [148628](https://freesound.org/people/conleec/sounds/148628/) | conleec | AMB_Ext_Auto_Engine_001.wav | [CC0](http://creativecommons.org/publicdomain/zero/1.0/) |
| [410616](https://freesound.org/people/tutenchwimse/sounds/410616/) | tutenchwimse | Sewing machine | [CC0](http://creativecommons.org/publicdomain/zero/1.0/) |
| [92388](https://freesound.org/people/sandyrb/sounds/92388/) | sandyrb | CLOSE FEMALE LAUGH 029.wav | [BY4](https://creativecommons.org/licenses/by/4.0/) |
| [277549](https://freesound.org/people/Coral_Island_Studios/sounds/277549/) | Coral_Island_Studios | Adult Woman Tickled 'Hold on'! hahahaha Authentic Acting.mp3 | [BY4](https://creativecommons.org/licenses/by/4.0/) |
| [119459](https://freesound.org/people/Eneasz/sounds/119459/) | Eneasz | Male Laughter.wav | [CC0](http://creativecommons.org/publicdomain/zero/1.0/) |
| [254869](https://freesound.org/people/jacobmathiassen/sounds/254869/) | jacobmathiassen | Male crying and weeping | [BY4](https://creativecommons.org/licenses/by/4.0/) |
| [319537](https://freesound.org/people/FreyaMaluk/sounds/319537/) | FreyaMaluk (metadata kleine_blume) | woman crying | [BY3](http://creativecommons.org/licenses/by/3.0/) |
| [219663](https://freesound.org/people/saraonsins/sounds/219663/) | saraonsins | high-pitched scream.wav | [CC0](http://creativecommons.org/publicdomain/zero/1.0/) |

47370은 기존 public33과 중복되어 train에서 제외됐다. extra9와 grandpa128479, held14, 사용자 자료는 이 train 목록에 들어 있지 않다. 평가자료를 학습 실적으로 notice에 적지 않는다. 평가보고에서 자료를 공유할 때는 그 자료의 개별 grant/attribution을 별도로 유지한다.[L1,L5,L8]

### 5.3 notice에 필요한 구체 사항과 권리의 한계

1. 원 AST의 copyright·BSD3 전문·비보증·비추천 조건, 고정 revision·원 URL·기존 graph와 실제 배포할 새 graph의 SHA·변경 내용을 유지한다.
2. 새 head의 실제 학습21clip은 위 표의 author/title/page/license, archived/current의 차이, 취득한 preview SHA까지 추적할 수 있어야 한다. 배포 notice에는 출처를 읽을 수 있는 형태로 넣고 FSD50K 저자/curators·dataset BY4·논문 citation도 유지한다. 변경 설명 예시는 “공식 preview를16kHz mono로 변환하고 최대20초의10초span에서 기존AST 특징을 추출하여 caption-weak presence 판별 head를 학습”이다. 생성 음성이나 원음을 동봉했다고 적지 않는다.
3. CC BY4 §3(a)는 creator·notice·license URI·변경 표시를 요구하고, §2(a)(5)는 Licensed Material의 하위 수신자 권리를 제한하는 추가 조건/기술 조치를 금지한다. BY3 §4(b)도 author/title/URI/변경credit 등을 요구한다. 이는 BY 대상 자료의 조건이며 앱 전체를 CC BY로 변경하거나 source를 공개해야 한다는 뜻은 아니다. 학습한 weight가 법적 Adapted Material인지 이 문서에서 판정하지 않는다. Freesound FAQ의 학습 attribution 지시도 고려해 실제 train 출처를 보수적으로 배포 notice에 넣는다.[P7]
4. CC0는 상업 이용을 포함하는 waiver/fallback을 제공하지만 §4(c)는 제3자 권리/동의가 해결됐음을 보증하지 않는다. BY4 §2(b)(1)도 privacy/publicity 등을 일괄 허락하지 않는다. 공개 caption의 “my daughter/son”은 가족 관계에 대한 업로더 주장이며 법적 음성 동의나 verified family/age/event의 완료 증명은 아니다. 원음 재배포나 voice cloning으로 범위를 확장하지 않고, 명시적인 제3자 원음/변조/재게시가 발견되면 해당 자료를 재검토한다.[P7,P8]
5. Freesound 공식 FAQ는 radio/TV를 녹음해도 broadcast copyright가 있다고 설명한다. uploader CC만으로 TV/news 프로그램 원천 권리를 확보한 것은 아니다. TV gate를 위해 임의의 뉴스 음성을 취득하거나 삭제된393504를 mirror로 대체할 근거가 없다.[P6,L5]

**현재 CC0/BY·BSD3는 상업적인 판별 head 개발/변경 배포를 진행할 구체적인 허락 경로가 있다.** 월령과 자연성이 미검증이라는 사실을 이 license 자체가 NC라는 설명으로 바꾸지 않는다. 이 조사로 모든 개인 권리·모든 원음 권리의 보증, 최종 APK에 notice가 올바르게 들어간 사실, 제품 품질/공개 출시의 승인을 입증하지 않는다.

## 6. 메인 구현의 확인 항목

- **타입/근거:** generic presence의 독립 source/kind·broad scope를 보존하고 subtype/욕구/인물을 추가하지 않는다. 기존 approve/conflict/quiet/missing/오류 보류를 유지한다.
- **시간:** 고정 input span을 분석 범위로 보존하고 actual samples/padding을 추적한다. 중첩3창을 독립 사건이나 실제 발성 시간으로 합산하지 않는다. AV alignment/phase/continuity에 exact onset이 없는 새 score를 사용하지 않는다.
- **수치:** frozen head SHA `81d7357c4c59f57222952a1542716f7d89e423d45f2987dd3917a3522bdfd510`, L2 .0001·support .8/margin .4·normalization을 유지한다. 새 자료로 calibration/threshold fitting을 하지 않는다. 기존527logits와 새score parity는 실제 Android에서도 확인한다.[L2,L3]
- **평가:** 기존 수용 보존, 같은 약국 원본의 전체 경로, 실제20초 입력, adult nonverbal/음악/quiet/혼합·cancel/메모리/ARM을 확인하고 event/새가족/TV의 미검증을 결과에 남긴다. PC 한session 또는 두session의 peak/속도를 실기기 수치로 환산하지 않는다.
- **배포:** 실제 graph의 notice/21clip attribution과 FSD의 별도 license를 검토 가능하게 만든다. beta 업데이트의 기존 승인은 유지되지만 이 문서만으로 실행QA/새beta제공/공개프로덕션 완료라고 보고하지 않는다.

## 7. 참조 원문과 취득 기록

### 공식 일차 자료

- **[P1]** AST repo commit `31088be8a3f6ef96416145c4b8d43c81f99eba7a`. [LICENSE](https://github.com/YuanGongND/ast/blob/31088be8a3f6ef96416145c4b8d43c81f99eba7a/LICENSE), [model source](https://github.com/YuanGongND/ast/blob/31088be8a3f6ef96416145c4b8d43c81f99eba7a/src/models/ast_models.py), [README](https://github.com/YuanGongND/ast/blob/31088be8a3f6ef96416145c4b8d43c81f99eba7a/README.md). 공식 GitHub API read-only, LICENSE blob `826e1706f3841a940dc1c7a1ab03e345d6692010`, model blob `be5e0fbfb9880833502c21dd50fd2b5354fda32f`. Git 작업/clone 없음.
- **[P2]** Google AudioSet [dataset 설명](https://research.google.com/audioset/dataset/index.html), [download CSV 의미](https://research.google.com/audioset/download.html). Jina Reader를 통해 공식 원문을 읽었다. 음원URL/video body 취득 없음.
- **[P3]** AudioSet [공식 ontology](https://github.com/audioset/ontology/blob/master/ontology.json). GitHub API blob `cee2c5055a4778833dd165cae4010086e6d4bdc5`. 위4종의 정의를 추출했고 linked 영상은 읽지 않았다.
- **[P4]** FSD50K [공식 Zenodo4060432](https://zenodo.org/records/4060432) / [FSD50K.doc.zip](https://zenodo.org/api/records/4060432/files/FSD50K.doc.zip/content?download=1). HTTP200,6,984B, SHA256 `a5c314ad2bf93d01b5921aa4270c70c90e28ac9a283ba9f08167490e728deb85`. memory에서 README/LICENSE-DATASET을 읽었다. audio/metadata/ground-truth archive 재취득 없음.
- **[P5]** 고정HF [API](https://huggingface.co/api/models/MIT/ast-finetuned-audioset-10-10-0.4593/revision/f826b80d28226b62986cc218e5cec390b1096902), [README](https://huggingface.co/MIT/ast-finetuned-audioset-10-10-0.4593/blob/f826b80d28226b62986cc218e5cec390b1096902/README.md). API200/4,756B, README200/1,165B, README SHA256 `dbc8ce1fc5abd1635d073640d8cb032901bfe264a5e06507f363f10a6112cfc8`. card license만 확인, weight body/HEAD 없음.
- **[P6]** Freesound [AI training FAQ](https://freesound.org/help/faq/#i-want-to-download-large-amounts-of-sounds-from-freesound-to-train-an-artificial-intelligence-ai-model-can-i-do-that), [합법 upload FAQ](https://freesound.org/help/faq/#what-sounds-are-legal-to-put-on-freesound). Jina Reader200,51,073B, 읽은text SHA256 `7c473014a3da11f536154fec5a472c91c25aadc573fad02609ad629acc7adc5d`. 2026년7월 도입된 GenAI preferences 설명도 구분해서 읽었다. 게시/연락 없음.
- **[P7]** Creative Commons [BY4 legalcode §2/3](https://creativecommons.org/licenses/by/4.0/legalcode.en), [BY3 legalcode §3/4](https://creativecommons.org/licenses/by/3.0/legalcode). Jina Reader에서 공식 조문을 읽었다. BY3/4는NC/SA와 별개이며 모델에 대한 법 적용을 판정하지 않았다.
- **[P8]** Creative Commons [CC0 legalcode §2/3/4](https://creativecommons.org/publicdomain/zero/1.0/legalcode). Jina Reader200,9,390B, 읽은text SHA256 `ec15e854a2ca88b3c0739869c9c69e2ec7a82a311d430099d005b1d11619c788`.

### 실제 연구·로컬 원기록

- **[L1]** [extra9 평가](20261007-extra-babble-evaluation-results.md), [AST head 실측](20261007-ast-infant-head-results.md). 이번 추론 없음.
- **[L2]** [head graph 실측](20261007-ast-infant-head-graph-results.md), [graph contract](20261007-ast-infant-head-graph-contract.md). 이번 weight 읽기/변환 없음.
- **[L3]** [ast_infant_head_probe.py](../../tools/research/ast_infant_head_probe.py):29/58/78/159/175, [infant_presence_probe.py](../../tools/research/infant_presence_probe.py):22/87, [fit metadata](../../work/infant-head-20261007/ast-head35-01/fit.json), [selection metadata](../../work/infant-head-20261007/ast-head35-01/selection.json). metadata와 source만 읽었고 NPZ 특징/weight body는 읽지 않았다.
- **[L4]** [permissive 자료 원문 감사](20261007-permissive-infant-voice-next.md), [label evidence](20261007-infant-head-label-evidence.md), [source metadata](../../work/infant-head-20261007/source-audit.json). current clip/page 취득snapshot과 원문 구분 유지.
- **[L5]** [extra9 평가 계약](20261007-extra-babble-evaluation-contract.md), [extra9 출처 감사](20261007-extra-babble-source-audit.md), [adult nonverbal 감사](20261007-adult-nonverbal-source-audit.md). 새 평가 전 미취득 상태는 이력이고 최신 취득/scoring10/10은L1.
- **[L6]** [0.5.18 시각 시간 연결 계약](../UX-VISUAL-ALIGNMENT-0.5.18.md), 최신HANDOFF. 현재 구현/배포 단계와 연구 후보 구분.
- **[L7]** [기존 AST_MODEL_NOTICE](../../outputs/MoaAndroid/app/src/astProbe/assets/AST_MODEL_NOTICE.txt). 새head attribution이 없는 기존 파일을 읽었으며 수정 없음.
- **[L8]** [실제 public35 input](../../work/infant-head-20261007/execution-sources35.json), [첫24page 감사](20261007-infant-head-subset-sources.md), [adult12page 감사](20261007-adult-nonverbal-source-audit.md). 실제train IDs21와 currentgrant를 집계했다. execution manifest129,144B/SHA는 본문의source pin과 일치한다.

**완료 범위:** Android 보완의 semantic/time/provenance 제약과 원AST·실제학습21clip·FSD annotation의 상업 허락/notice 경로 조사. **미실행:** 앱 변경, 최종package notice 검증, 추가모델/원음 취득, 실행QA, 독립자연영아/TV 평가, 실기기성능, 배포. 메인 작업의 후속 범위이며 문서 조사 완료를 제품 개선 완료로 보고하지 않는다.
