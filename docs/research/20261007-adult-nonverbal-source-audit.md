# 성인 비언어 발성 반례: 출처·허락·preview 메타데이터 감사

조회일: **2026-10-07 KST**. head가 기존 공개 회귀 표본의 성인 웃음 cchenail/ripc를 영아로 오인한 사각지대를 대상으로 조사했다. 이번 담당자는 성능 로그나 모델을 다시 실행하지 않았다. 이미 수행한 저장소·모델 권리 조사를 반복하지 않고 공개 FSD의 비언어 발성 출처만 확인했다.

## 1. 산출물과 완료 범위

[adult-nonverbal-sources.json](../../work/infant-head-20261007/adult-nonverbal-sources.json)에 **새 업로더 12개 / clip 12개**, train 6 / source validation 6을 저장했다. 출처당 하나이고 분할 사이 업로더가 겹치지 않는다. 현재 원작자 페이지의 설명·제목·허락 anchor·data-mp3·canonical URL·페이지 bytes/SHA256를 확인했다.

구성은 **웃음 6 / 울음·흐느낌 2 / 비명 4**다. 원문에 숫자 성인 나이를 명시한 것은 jacobmathiassen의 34세 한 개다. Adult Woman이라는 제목 한 개와 성숙한 작가의 스튜디오 녹음 한 개가 있고, 나머지는 man/woman이라는 성인 맥락과 본인 또는 직접 녹음 설명을 함께 갖는다. **모두 caption-weak 후보이며 검증된 성인 정답이 아니다.**

원음·preview 취득·청취·디코딩, 모델·학습·추론, 연락·로그인, Git·ADB 작업은 하지 않았다. 모든 행의 approved_training_label/audio_sha256는 null, audio_review_status는 not_downloaded_not_listened다. 이 목록은 새 source 분리 실험을 준비하기 위한 메타데이터 단계다.

## 2. 공식 metadata 고정 및 제외 계약

[FSD50K 공식 record](https://zenodo.org/records/4060432)의 metadata zip만 **1회**, 최대 8MiB 범위로 읽었다. 실제 bytes와 두 hash가 모두 이전 pin과 일치했다.

    bytes  6700838
    MD5    b9ea0c829a411c1d42adb9da539ed237
    SHA256 9a738e032546f9a2c6e3d04566928d04a65fb79b422cc9d78bb781723537bd19

원천 파일은 FSD50K.metadata/collection/collection_dev.csv 및 collection_eval.csv, dev_clips_info_FSD50K.json 및 eval_clips_info_FSD50K.json이다. fname string으로 같은 공식 split의 JSON key를 join하고 raw labels의 comma 토큰을 정확히 비교했다. 이번 최종 12개는 모두 공식 dev에 속하지만 새 train/validation은 head용 잠정 분할이다.

원음 보관 license URL은 CC0 1.0 또는 BY 3.0 정확 경로만 허용했다. current page는 CC0/BY 3.0/BY 4.0을 공식 creativecommons.org host와 정확한 경로로 검수하고 archived/current 값을 따로 보존했다.

전체 업로더·source 이름 **99개**를 제외 명단에 넣었다.

- 기존 public33의 모든 author/source_group. 정확 회귀 입력과 해당 업로더의 새 clip도 이번 후보에서 제외.
- 이전 23 실행 목록의 모든 metadata source/current author alias.
- 기존 영아 source 감사의 전체 업로더 그룹. 아이 원천·월령 설명이 약하거나 성인/가공이라고 분류된 같은 union의 원천까지 보수적으로 제외.
- 이전 negative 감사의 모든 출처와 보류 출처. Reitanna/TwistedLemon 등 이전 validation을 training으로 재배정하지 않음.
- 알려진 Corsica_S/tim.kahn alias.

제외 후 비언어 exact-label metadata index는 1,000clip/444업로더지만 이는 성인 corpus가 아니다. 개·아이·원천 미상·가공 발성도 raw 라벨에 포함될 수 있다. 최종 files 12행만 별도 선별했으며 전체 pool을 학습 대상으로 승인하지 않았다.

## 3. 고정 6+6 후보

ID 링크는 현재 공식 canonical 원작자 페이지다. 설명 전문·preview URL·raw 라벨·권리 snapshot은 JSON에 저장했다.

| 역할 | ID / 업로더(보관 → 현재 별칭) | 발성 후보 | 성인·녹음 설명 근거 | current 허락 |
|---|---|---|---|---|
| train | [92388](https://freesound.org/people/sandyrb/sounds/92388/) / sandyrb | 웃음 | 출판용 소설 audiobook 작가의 자연스러운 스튜디오 웃음, 공유 허락·익명 유지 설명. compression/editing/겹침·clipping 가능 | [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/) |
| train | [277549](https://freesound.org/people/Coral_Island_Studios/sounds/277549/) / Coral_Island_Studios | 웃음 | Adult Woman Tickled 제목, Zoom H6 studio, Authentic Acting. 실제 자연성·나이 미검증 | [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/) |
| train | [119459](https://freesound.org/people/Eneasz/sounds/119459/) / Eneasz | 웃음 | A man laughing (me, actually). 장비·숫자 나이 없음 | [CC0-1.0](http://creativecommons.org/publicdomain/zero/1.0/) |
| train | [254869](https://freesound.org/people/jacobmathiassen/sounds/254869/) / jacobmathiassen | 울음 | A 34 year old male crying authentically. 숫자 나이도 업로더 주장 | [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/) |
| train | [319537](https://freesound.org/people/FreyaMaluk/sounds/319537/) / kleine_blume → FreyaMaluk | 울음·흐느낌 | A woman crying and sobbing softly, Audacity/노이즈 감소. pitch 변화 설명 없음 | [CC-BY-3.0](http://creativecommons.org/licenses/by/3.0/) |
| train | [219663](https://freesound.org/people/saraonsins/sounds/219663/) / saraonsins | 높은 비명 | woman/high-pitched scream, iPhone5, UPF. 같은 대학/event 업로더 후보는 하나만 유지 | [CC0-1.0](http://creativecommons.org/publicdomain/zero/1.0/) |
| validation | [42841](https://freesound.org/people/FreqMan/sounds/42841/) / FreqMan | 웃음 | Woman laughs, Sony ECM-99→SonyMD 녹음 장비 | [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/) |
| validation | [134720](https://freesound.org/people/ptrmax/sounds/134720/) / ptrmax | 웃음 | evil man laughing 제목 + My laugh recorded in home. 인간 연기/가공 여부 미청취 | [CC0-1.0](http://creativecommons.org/publicdomain/zero/1.0/) |
| validation | [238819](https://freesound.org/people/marlonnnnnn/sounds/238819/) / TheAtomicBrain → marlonnnnnn | 웃음·말 | A man greetings and laughing, computer mic/Audition. event 경계·겹침 미검수 | [CC0-1.0](http://creativecommons.org/publicdomain/zero/1.0/) |
| validation | [384213](https://freesound.org/people/Vidrik/sounds/384213/) / Vidrik | 비명 | Young woman, own horror podcast, Rode NT1-A. 연기 발성 가능 | [CC0-1.0](http://creativecommons.org/publicdomain/zero/1.0/) |
| validation | [336537](https://freesound.org/people/giddster/sounds/336537/) / chrillz3r → giddster | 비명 | A scared man screaming, Zoom H1. 세금 관련 농담은 실제 동기로 해석하지 않음 | [CC0-1.0](http://creativecommons.org/publicdomain/zero/1.0/) |
| validation | [353291](https://freesound.org/people/J.Zazvurek/sounds/353291/) / J.Zazvurek | 비명 | Close mic, Studio, Young woman scream. 숫자 나이 없음 | [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/) |

Current 허락은 **CC0 6 / BY 3.0 1 / BY 4.0 5**다. kleine_blume의 current alias는 FreyaMaluk, TheAtomicBrain은 marlonnnnnn, chrillz3r는 giddster로 확인했다. 이름 변경은 새 source가 아니며 original sourcegroup을 유지했다. 정확 문자열은 JSON의 metadata_uploader/currentauthor/current_name_alias 필드가 기준이다.

sourcegroup은 FSD50K:dev:{metadata_uploader}다. **업로더는 검증된 화자·아기·가족 ID가 아니다.** 익명 성인 작가의 동일 인물 여부도 밝히지 않는다. validation을 threshold 선택이나 반복 개선에 쓰면 final 독립 test라고 보고하지 않는다.

## 4. 선별에서 제외하거나 남긴 한계

14개 공식 페이지를 읽고 성인 근거가 더 약한 두 개는 최종 목록에서 제외했다. Dinsfire 169628은 Male voice + self recorder, jorickhoofd 180300은 dry male inhale scream/Zoom H4n이지만 숫자 나이나 man/woman 단서가 없었다. 실제 어린이라고 단정한 것이 아니며 이번 좁은 성인 반례의 우선순위가 낮다고 판정했다. 두 페이지 스냅샷은 rejected_page_audits에 보존했다.

- raw Laughter/Giggle/Chuckle만으로 성인 정답을 만들지 않았다.
- 학교의 10세/13세/16~18세, generic girl 설명은 명확한 성인 후보로 쓰지 않았다.
- pitch 변경·voice changer·겹쳐 만든 laugh track·원천 미상 샘플은 자연 성인 발성 반례로 채우지 않았다.
- UPF 대학/conference 설명의 여러 업로더는 같은 actor/session 가능성이 있어 하나만 선택했다.
- 명확한 사람 whimper raw 라벨은 확보하지 못했다. raw Whimper_(dog)를 사람 흐느낌으로 바꾸지 않았다. 이 영역은 여전히 부족하다.
- crying/scream/evil/tickled 등 원문 표현이 실제 고통·두려움·발성 동기·감정을 증명하지 않는다. 인간 연기나 가벼운 노이즈 감소·compression도 허용된 후보 설명으로 보존하고, 자연성 검증 완료라고 보고하지 않는다.

## 5. 원천 링크와 취득 전 주의할 검수

최종 12개 current description의 명시적 Freesound sound 원천 링크는 0이었다. 선정 업로더의 허락 대상 비언어 metadata 설명에서도 해당 링크를 발견하지 못했다. 검사 범위는 exact 비언어 metadata와 선정 페이지이며 모든 ontology 라벨이나 음향 중복을 검수한 것이 아니다. 미기재 리믹스·alias·같은 화자·공통 녹음 원천은 남을 수 있다.

페이지마다 HTML의 실제 data-mp3를 확인했고 파일명에서 preview URL을 추측하지 않았다. page_sha256는 시점 HTML bytes pin이다. preview/original PCM hash가 아니며 동적 웹페이지의 다음 hash와 같을 필요도 없다. 호출 timeout은 20초, 개별 페이지 텍스트 상한은 10MiB였고 전체 텍스트는 약 0.7MB다.

사용자 사적 원본·그 segment/feature는 학습·calibration·augmentation에 넣지 않는다. 평가만 허용하고 외부 업로드하지 않는다. 기존 public33은 회귀용이며 이 후보들의 성능을 증명하는 새로운 독립 test로 격상하지 않는다.

## 6. 실제 모바일 개선에 남은 조건

이 12개는 말 위주의 adult 학습 후보에서 빠졌던 비언어 발성에 대한 새 공개 출처 계획이다. 실제 취득·라벨·embedding·학습·수치 parity·모바일 개선은 메인 담당자의 별도 단계다. 이 감사로 성인 웃음 오인이 해결됐다고 보고하지 않는다.

**실제 모바일 채택 전에는 성인 TV/재생 음성 반례와 희소 발성의 사건·출처·발성 동기 검사가 더 필요하다.** 자연 직접 발성만으로 TV 소리의 공간·코덱·화면 출처를 대표할 수 없다. 새 아기·가족·겹침 자료와 기존 동작 회귀도 별도로 확인해야 하며, 문장/의도 정확도나 의학적 해석으로 결과를 확장하지 않는다.
