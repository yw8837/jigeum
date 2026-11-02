# 다음 영아 head 평가: 6–18개월 공개 후보와 검수 조건

확인일 **2026-10-07 KST**. `agent-reach`의 공식 웹/Hugging Face 경로로 페이지·검색 metadata만 확인했다. 이 문서만 작성했다. 음원·preview body·특징·가중치 취득, 청취, 추론, 학습, calibration, 빌드, ADB, Git, 사용자 원본 접근, 외부 연락은 모두 **0**이다.

## 1. 판단

**현재 읽은 근거만으로 월령·자연 발성·실제 사건·원천 권리까지 모두 검증된 새 평가 자료는 0개다.** 후속 검수 후보를 **5개 파일/5업로더 그룹**으로 고정한다. 앞의 4개는 기존 FSD50K eval 색인의 재확인이고, 마지막 1개는 이번에 exact ID를 확인한 17개월 caption 파일이다. 마지막 업로더도 기존 색인에 있었으므로 새 영아 source를 처음 발견했다고 표현하지 않는다.

5개 후보는 아래의 기존 train21/18source·held14/11source·extra9/3업로더와 **현재 metadata 이름 및 기록된 preview 업로더 ID 기준 교집합이 없다**. 이는 가족·아동·녹음 세션의 독립성을 입증하지 않는다. 원작자/재게시 관계와 실제 발성은 미확인이다. **모든 파일 `training_blocked=true`, `approved_training_label=null`을 유지한다.** 기존 eval 파일은 eval 보류를 유지하며 다른 split으로 옮기지 않는다.

현재 frozen AST head는 일반 영아 발성 존재 보완 후보다. 동일 약국 원본의 겹치는 두 개발 구간 회복, 기존 승인 13개 보존, public33의 새 generic support 0, extra9의 caption support4/miss5는 자연 영아 발성의 일반 정확도·의도 번역·가족 독립 검증이 아니다. 이 문서는 부모 작업자의 진행 보고를 범위 제약으로 사용하며 실행 산출물을 재감사하지 않았다. 새 모델/학습 확장을 승인하지 않는다.

## 2. 중복 제외 근거

먼저 [positive46 계약](20261007-permissive-infant-voice-next.md), [기존 specialist/표현 조사](20261007-next-infant-representation-evidence.md), [head 라벨 근거](20261007-infant-head-label-evidence.md)를 읽었다. **positive46은 46개 파일/5업로더**이며 46개 독립 source가 아니다.

- train 쪽 NoiseCollector20·reinsamba8·Fugazi80 1·levinj1은 이미 현재 train source에 포함된다.
- held 쪽 Corsica_S→tim.kahn16은 이미 held source다. 보지 않은 같은 업로더의 파일을 blind/source 독립 test로 추가할 수 없다.
- FunWithSound/Stevious42/wjoojoo의 extra9는 이미 첫 평가에 쓰였다. 다음 새 평가 후보로 다시 세지 않는다. 해당 월령·자연성 미검증 상태도 유지한다.
- public33과 NoiseCollector/reinsamba는 겹친다. 기존 실제 회귀 파일47370 및 기타 평가 자료를 새 train으로 편입하지 않는다.

이번 비교의 고정 로컬 metadata:

| 파일 | bytes | SHA256 |
|---|---:|---|
| [execution-sources35.json](../../work/infant-head-20261007/execution-sources35.json) |129,144|27966aa9265da239489e7351959c01399ed3a0aed6428149babc2cb127fad4b9|
| [extra-babble-sources.json](../../work/infant-head-20261007/extra-babble-sources.json) |54,093|a7658a7a85688ed360377d5607101b31d1d1a115be96e7c58abc9aebbd3d045b|
| [source-audit.json](../../work/infant-head-20261007/source-audit.json) |445,428|592099e29c0f5e15289f8d31df938ca696a06c0031cd12713fd4edbd8078c9c6|

현재 후보 업로더 BoilingSand/amccoy711/carameans/soundscalpel.com/klankbeeld를 실행35와 extra9의 sourcegroup·currentauthor·metadata_uploader에 비교했다. public33의 기록된23 source 이름에도 교집합0이다. 현재 공식 preview URL의 업로더 숫자571325/7293523/2456349/1930766/1648170도 실행35/extra9의 기록된 preview URL과 교집합0이다. source-audit의 명시적 파생 링크5개는 이 후보를 참조하지 않는다. 전체 인터넷의 재게시/별칭/같은 가족까지 조사한 결과가 아니며, URL의 숫자를 보호자 신원으로 해석하지 않는다.

## 3. 다음 검수 후보 — exact 파일 5개

표의 월령은 모두 **caption claim**, label strength는 **metadata 근거**다. actual review/event ground truth는 전부 없다. 원음 저장·다운로드 성공은 아직 확인하지 않았으며, acquisition availability는 공식 HTML이 preview URL을 제공한다는 뜻이다.

| 후보·source identity | 월령 근거·발성 근거 | 자연성·처리·권리 | 현재 경로·역할 |
|---|---|---|---|
| **[50665](https://freesound.org/people/BoilingSand/sounds/50665/)** / `FSD50K:eval:BoilingSand`, preview owner571325, `toddler cry 1 0803xx.wav` | 설명에16개월, 낮잠 직후 짧은 cry. raw FSD `Baby_cry_and_infant_cry`. **FUS 정답은 없음** | 일상 중 자발적으로 발생했다고 명시해 자연성 caption은 가장 강하다. Zoom H2/약8ft·작성자의 `low pass at 80` 처리 문구를 보존한다. 현재/archived **CC BY3.0** | HTML200/preview 제공. 기존 eval 재확인; 새 발견 아님. 취득 후 cry/fuss/uncertain를 귀로 구분해야 함. 학습 차단 |
| **[388407](https://freesound.org/people/amccoy711/sounds/388407/)** / `FSD50K:eval:amccoy711`, owner7293523, `Baby Giggles.wav` | 자신의15개월 아들의 giggling 설명. raw `Baby_laughter` | 자기 아들의 녹음/iPhone caption은 있으나 자발성·비연기 명시는 없다. joy 설명은 감정 정답으로 사용하지 않는다. 현재/archived **CC0** | HTML200/preview 제공. 기존 eval 재확인. 실제 laugh 존재·구간·성인 겹침 미검수. 학습 차단 |
| **[135185](https://freesound.org/people/carameans/sounds/135185/)** / `FSD50K:eval:carameans`, owner2456349, `Twins Bathtime, Audio.wav` | one-year-old twin daughters→12개월 claim. 설명은 목욕; laughter는 tags/raw `Baby_laughter` 근거 | 생활 장면 caption이며 발성 종류·자발성은 설명에서 확정할 수 없다. **복수 아동/물소리 혼합** 가능. 쌍둥이를 독립 가족2개로 세지 않는다. 현재/archived **CC0** | HTML200/preview 제공. 기존 eval 재확인. speaker 지정·개별 아기 ground truth 없음. 학습 차단 |
| **[110392](https://freesound.org/people/soundscalpel.com/sounds/110392/)** / `FSD50K:eval:soundscalpel.com`, owner1930766, `human_baby_toddler_male_15_months_laugh.wav` | 제목/설명에15개월 boy laughing, raw `Baby_laughter` | stock 게시자이며 가족·원녹음자·연기/편집/재게시 lineage 미확인. 현재/archived **CC BY3.0**. 현재 description의 외부 원저작 링크는0개 | HTML200/preview 제공. 기존 eval 재확인. 현재 CC 표시만으로 stock 원작자의 모든 권리를 입증하지 않는다. 학습 차단 |
| **[578238](https://freesound.org/people/klankbeeld/sounds/578238/)** / `Freesound:klankbeeld`, owner1648170, `baby talk 17 MO 005 181204_1298.wav` | 현재 제목/설명에17개월 baby girl talk. babble/infant tags; **FSD raw annotation 없음** | 녹음 장비/decoded Mid-side stereo caption. 자연·비연기 명시는 없다. 현재 **CC BY4.0**와 설명의 raw/stock 재배포 금지 문구가 공존하므로 사용 조건을 해결하기 전 상업 학습·앱 배포 허락으로 보고하지 않는다 | HTML200/preview 제공. exact ID는 기존233행에 없음. 그러나 klankbeeld는 기존 색인의16개 파일 업로더여서 **새 업로더 발견은 아님**. 새 source 후보의 event·권리 검수 대상일 뿐. 학습 차단 |

BY 파일의 후속 취득자는 title·작성자·원페이지·license·편집 여부를 attribution에 남긴다. CC0도 provenance를 기록한다. FSD annotation의 CC BY4.0는 원음 license를 바꾸지 않는다. klankbeeld의 BY4 표시와 추가 문구 사이의 범위를 이 조사에서 법률 판단으로 해결하지 않았으며, 문의·연락은 하지 않았다.

### 동일 source 대안과 새 발견의 범위

[50666](https://freesound.org/people/BoilingSand/sounds/50666/)도 같은 조사에서 재확인했다.16개월·낮잠 직후 자발적 little cry, 같은 장비/거리/처리 caption, BY3/owner571325다. **위5개 파일 목록에는 포함하지 않는 같은 source 대안**이며 두 파일을 독립 source로 세지 않는다. little cry를 FUS label로 자동 전환하지 않는다.

klankbeeld 기존 색인에는4개월 caption의405443/405596/406729/406955/407606/411662/412202/412242/416876/416936 등과 월령 미상 파일이 있다. 새578238를 기존4개월 파일의 월령 증거로 전파하지 않는다. 같은 업로더의 다른 월령·세션은 source 전체를 함께 묶고, 가족/아기가 같은지 모르는 상태를 보존한다.

## 4. 공식 HTML snapshot과 경로

조회 시작 시각 **2026-10-06 19:33:18 UTC**(10-07 04:33:18 KST), 추가 조회19:34:03 UTC. snapshot hash는 **HTML bytes**이며 음원 hash가 아니다. 동적 HTML hash 변경은 음원이 바뀌었다는 근거가 아니다. 모든 GET은 공식 설명 페이지이며 preview/원음에 GET·HEAD를 보내지 않았다.

| ID | HTML bytes·SHA256 | 공식 페이지의 preview URL |
|---|---|---|
|50665|41,851 /7272542b13faceb5932714a0717d09d39050356c958c403ba16ee72f81053042|https://cdn.freesound.org/previews/50/50665_571325-lq.mp3|
|50666 — 동일 source 대안|40,460 /62e60e534b06320da4c7535bc827d5d9a23144ea310d03d8aec25cec13349fea|https://cdn.freesound.org/previews/50/50666_571325-lq.mp3|
|388407|45,028 /e30578d8b395ce046ae74ad94ca1c520bea4522c8d623e7726268287a9a9c040|https://cdn.freesound.org/previews/388/388407_7293523-lq.mp3|
|135185|37,492 /305220395de168d243be3938a954069f021cfcd030697a727a557d2bdf115d7c|https://cdn.freesound.org/previews/135/135185_2456349-lq.mp3|
|110392|44,060 /9a8db0f32c5dbfcb0d3537ca87a300409d62a38c5b9daf731accb0636efb0b51|https://cdn.freesound.org/previews/110/110392_1930766-lq.mp3|
|578238|46,821 /efa4300cf65802bb015aa688ea254516b12c35d81cfffb0d51657f981beeff73|https://cdn.freesound.org/previews/578/578238_1648170-lq.mp3|

## 5. 다른 공식 경로의 좁은 확인

새 파일을 무제한 추가하지 않았다. 다음은5개 평가 후보를 늘릴 수 있는 근거로 승인되지 않은 경로다.

- [Providence 공식 corpus](https://talkbank.org/phon/access/Eng-NA/Providence.html)는6아동, 약11개월 이후부터 집의 자발적 부모 상호작용 녹음을 설명한다. linked audio/video와 전사는 있으나 babble/laugh/fuss event 정답은 페이지에서 입증되지 않는다. [기존10-04 조사](20261004-labeled-av-data.md)에 이미 있으므로 새 발견이 아니다. 공식 media folder 링크는 이번 도구에서 본문0줄로 반환되어 실제 파일 접근 성공을 확인하지 못했다. 개별 transcript·media를 취득하지 않았다.
- [HomeBank San Joaquin Public](https://talkbank.org/homebank/access/Public/SanJoaquin-Public.html)은11참여자/11녹음의 naturalistic public subset이다. 페이지가 ASR 전사가 인간 검수되지 않았다고 명시한다. 정확한6–18개월 sample/file과 babble 사건은 이번에 고정하지 못했다. media folder 역시 본문0줄이다. [공식 Ground Rules](https://talkbank.org/homebank/rules.html)의 **CC BY-NC-SA3.0**이 있어 상업 앱 학습·배포의 permissive source로 올리지 않는다. NC를 제거하거나 제한된 자료에서 증류하는 우회도 제안하지 않는다.
- HF 공식 API의 `babbling` 검색은 `Manisha12Researcher/Babies-weeping-and-happy-babbling-sounds`(revision40d5b41d376e74f029d2a8602261f0ff94584186), 로봇/action 이름의 결과2개를 반환했다. 첫 dataset의 고정 README 요청은404다. 검색 metadata에 음원 원저작·월령·license가 없고 card 본문도 확인되지 않아 후보에 추가하지 않았다. 공개/ungated 표시는 사용 허락이 아니다. `infant vocal` 검색은0결과다. shard·audiofolder를 읽지 않았다.

## 6. 다음 단계: 모델 점수를 가린 인간 검수

완전 충족 후보가 없으므로 기존 보류 후보의 검수부터 수행한다. 이 문서는 향후 실행 계약이며 이번 실행 완료 보고가 아니다.

1. 취득 담당자가 페이지 권리·원작자·파생 관계를 다시 고정하고 합법 취득 가능한 파일만 로컬에 받는다. exact bytes/SHA256·format·duration·source URL·원음/preview 구분을 기록한다. 실패/삭제/접근 불가도 목록에 남긴다. klankbeeld의 별도 문구와 soundscalpel의 원저작 lineage가 해결되지 않으면 미취득/권리 보류로 보고한다.
2. 리뷰어에게 head/기존 모델의 score·승인 여부를 숨긴다. 가능하면 caption의 발성 label도 가리고, 각 파일 전체에서 들리는 사건을 **babble / laugh / cry / fuss / other / uncertain / mix**로 독립 기록한다. playback/성인 연기/합성·편집 의심, 성인 겹침·생활잡음·녹음 처리도 flag한다. 동기·기분·요구·의료 상태는 정답으로 만들지 않는다.
3. 실제 들리는 사건의 onset/offset만 표시한다. 파일 전체나975ms window 전체에 양성 label을 복제하지 않는다. 검수자가2명 가능하면 독립 판독 뒤 불일치를 uncertain로 남기고 합의 절차를 기록한다. 단일 리뷰면 그 한계를 명시한다. 청취는 정확한 월령이나 가족 신원을 검증할 수 없으므로 caption claim을 유지한다.
4. BoilingSand의 두 clip은 동일 source로 묶는다. carameans의 쌍둥이·stock 게시자는 개별 가족/아동 수로 세지 않는다. 다른 업로더라도 같은 원본/파생 링크가 확인되면 보수적으로 한 그룹으로 합친다. source 관계가 해결되기 전 독립 일반화 test라는 이름을 붙이지 않는다.
5. 모델·학습 데이터·head·기존0.8/0.4 gate를 검수/평가 결과 이전에 동결한다. 이 자료로 재학습·threshold 변경·calibration·augmentation·좋은 clip 선택을 하지 않는다. 권리 보류/미취득/uncertain를 포함한 모든 사전 고정 파일의 결과와 source별 support/miss/false support를 보고한다. 기존 train/held/extra 역할을 유지한다.
6. cry/laugh metadata를 BAB/FUS 정답으로 바꾸지 않는다. infant generic support를 발성 세부 분류 성공으로 보고하지 않는다. event recall 및 성인/playback/잡음의 반례를 함께 평가하되, 소수 clip과 업로더 그룹은 일반6–18개월 정확도를 입증하지 못한다. 제품 채택은 별도 Android 전체 경로·실기기·회귀 검증 계약을 따른다. 사용자 사적 녹음은 온디바이스/평가 전용이며 외부 업로드·학습·calibration에 쓰지 않는다.

## 7. 이번 실제 조회·취득 횟수

| 항목 | 정확한 횟수·결과 |
|---|---|
| 웹 검색 query |3: `site.talkbank.org babbling Providence corpus infants audio license`, `site.huggingface.co/datasets babbling infant months`, `site.homebank.talkbank.org infant babble license audio`|
| 공식 Freesound HTML GET |6요청/6성공/삭제0/실패0. 후보5개+동일 source 대안50666 |
| HF 공식 API 검색 |2요청/2성공. `babbling`3결과, `infant vocal`0결과 |
| HF 고정 README GET |1요청/0성공/404 1 |
| 공식 academic 페이지 open |4요청: Ground Rules/Providence/SanJoaquin-Public 본문3성공, `talkbank.org/0share/cc.html` inaccessible1. 마지막 실패는 권리 근거로 사용하지 않음 |
| 공식 academic media-folder link click |2요청/본문0줄2. media/acquisition 성공으로 세지 않음 |
| GitHub 검색·repo/file 조회 |0 |
| 전체 외부 조회 operation |**18**=검색3+Freesound6+HF API2+HF README1+academic open4+folder click2. 이는 backend 내부 HTTP/redirect 수가 아닌 발행한 query/GET/open/click 수 |
| 음원/preview/feature/weight body 취득 |**0/0/0/0** |
| 청취·decode·추론·학습·calibration·빌드·ADB·Git·외부 연락 |모두 **0** |

**완료:** 기존 자료의 중복 제외, 기존 eval5개 파일의 현재 권리/경로 재확인, exact17개월 파일의 권리 blocker 확인, 최종5파일 후보와 blind human review 조건 고정. **미완료:** 실제 월령/자연성/사건/가족 독립 검증, 음원 취득·인간 검수·모델 평가·제품 적용. 이 결손을 새 정확도나 신규 독립 가족 확보로 표현하지 않는다.
