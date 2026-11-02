# 다음 자연 영아 발성 학습 source: 새 caption 후보 2그룹

확인일 **2026-10-07 KST**. `agent-reach`의 Exa 검색과 공식 Freesound HTML 경로로 metadata만 확인했다. 소유 파일은 이 문서 하나다. 음원·preview body·특징·가중치 취득, 청취, decode, 추론, 학습, calibration, 사용자 원본 접근, 빌드, ADB, Git, 삭제, 외부 연락은 모두 **0**이다.

## 1. 현재 결론

기존 학습 양성은 **7 clip / 4 source / 40.7초**다. 이를 늘릴 수 있는 후속 검수 후보를 **새 exact 파일 2개 / 업로더 그룹 2개**로 고정했다. 기존 train·held·eval·실제 추가 평가에 쓰인 source와 기록된 이름/별칭 및 preview owner ID가 겹치지 않는다. 현재 두 공식 페이지는 **CC0 1.0**과 preview 경로를 제공한다.

그러나 **지금 학습에 승인할 수 있는 새 자료는 0개**다. 월령은 작성자의 caption이고, 실제 자연 영아 발성·사건 경계·원작자 관계는 아직 검수하지 않았다. 두 후보 모두 `training_blocked=true`, `approved_training_label=null`, `audio_review_status=unreviewed`를 유지한다. 기존 7 clip을 교체하거나 train/held/eval의 역할을 바꾸지 않는다.

이번 후보는 일반 영아 발성 존재를 위한 검수 대상이다. 첫 파일은 babbling caption이지만 둘째는 coo/gurgle caption이므로, 둘을 자동으로 canonical babble 정답으로 묶지 않는다. 의도·욕구·감정·의료 정답을 만들지 않는다.

## 2. 기존 자료를 먼저 제외한 근거

먼저 [head 라벨 근거](20261007-infant-head-label-evidence.md), [positive46 계약](20261007-permissive-infant-voice-next.md), [extra9 계약](20261007-extra-babble-evaluation-contract.md), [다음 평가 후보](20261007-next-infant-head-validation.md), [자연 옹알이 조사](20261007-new-natural-babble-sources.md), [공개 후속 실제 결과](20261007-public-infant-followup-results.md), [사건 정답 자료 조사](20261007-babble-event-label-next.md)를 대조했다. 이미 조사한 encoder/모델/포털 목록은 다시 제안하지 않는다.

- 실제 train21/18source와 held14/11source 전체를 제외했다. 양성 train의 NoiseCollector·reinsamba·Fugazi80·levinj, 양성 held의 Corsica_S→tim.kahn을 포함한다. 같은 업로더의 남은 positive46 파일은 새 독립 source로 세지 않는다.
- 현재 이름 별칭 kleine_blume→FreyaMaluk, TheAtomicBrain→marlonnnnnn, chrillz3r→giddster와 명시적 linked source도 제외 비교에 포함했다.
- 이미 평가한 extra9의 FunWithSound/Stevious42/wjoojoo, public33의 기록된 23개 source 이름, 기존 FSD eval 업로더 전체를 제외했다. 후속 실제 평가의 amccoy711/carameans/bvaudio도 train 후보로 재사용하지 않는다.
- 앞선 5개 평가 후보 BoilingSand/amccoy711/carameans/soundscalpel.com/klankbeeld는 이번 train 제안에서 모두 제외했다. BoilingSand50666 같은 source 대안도 제외했다.
- 남은 기존 metadata의 boldcity394772는 설명이 단순히 “baby sounds”여서 월령·자연성 근거가 없다. gipsyGA는 성인 cartoon-grandpa 연기이고 batchku는 영아 출처 없는 리듬 nonsense speech다. 이들을 새 자연 6–18개월 양성으로 채우지 않는다.

### 고정 metadata와 실제 중복 검사

| 로컬 파일 | bytes | SHA256 |
|---|---:|---|
| [source-audit.json](../../work/infant-head-20261007/source-audit.json) |445,428|592099e29c0f5e15289f8d31df938ca696a06c0031cd12713fd4edbd8078c9c6|
| [execution-sources35.json](../../work/infant-head-20261007/execution-sources35.json) |129,144|27966aa9265da239489e7351959c01399ed3a0aed6428149babc2cb127fad4b9|
| [extra-babble-sources.json](../../work/infant-head-20261007/extra-babble-sources.json) |54,093|a7658a7a85688ed360377d5607101b31d1d1a115be96e7c58abc9aebbd3d045b|

세 JSON의 source/uploader/currentauthor/metadata_uploader 및 기록된 preview 경로를 재귀 검사했다. 후보 이름 Rodcencko/Sauron974와 preview owner **383/152878**의 교집합은 0이다. `docs/research`에서도 두 이름 및 exact ID를 검색해 기존 제안·평가 언급이 없음을 확인했다. 이는 현재 저장소 metadata에 대한 검사이며 전체 인터넷의 별칭·재게시·동일 가족 여부를 검증한 결과는 아니다. 업로더와 preview owner 숫자는 가족·아동 신원이 아니다.

## 3. 사전 고정한 새 후보 — 최대 3그룹 중 2그룹

| exact 파일·새 source 그룹 | 현재 공식 설명·월령 | 자연성·발성 한계 | 현재 권리·역할 |
|---|---|---|---|
| **[9437](https://freesound.org/people/Rodcencko/sounds/9437/)** / `Freesound:Rodcencko` / owner383 / `childVoice001.aiff` | 원문: “18 month female child babbling recorded with sony dat and mono microphone”. **18개월 caption**, target 상한에 해당. 공식 duration **1.340초** | 실제 아동·babbling이라고 설명하지만 일상 장면/비연기 여부·화자 단독 여부·사건 경계는 미확인. 설명은 mono microphone이나 페이지 channels는 stereo이므로 파일 형식/처리를 추측하지 않는다. 매우 짧아 source 다양성 또는 긴 문맥 확보 효과가 제한적 | 현재 **CC0 1.0**. description의 외부/파생 링크0. 기록된 train/held/eval/extra/public source와 이름·owner 겹침0. **향후 train 검수 후보**, 지금 학습 차단/approved null |
| **[244237](https://freesound.org/people/Sauron974/sounds/244237/)** / `Freesound:Sauron974` / owner152878 / `Baby Girl 1 year old - Coos, Gurgles, Drops Toys` | 원문: “A one-year-old American baby girl coos and gurgles while playing with a toy. Recorded in 2011 with an ElectroVoice CL-42 shotgun mic into a Zoom H4n portable recorder.” **한살→12개월 caption**, 공식 duration **97.518초** | 놀이 중 생활 녹음 caption으로 자연성 근거가 있으나 실제 비연기/아동·화자·발성 구간은 미검수. toy/drop/background 및 성인 겹침 가능성을 확인해야 한다. babble tags는 canonical babble 사건 정답이 아니고, `happy` tag도 감정 정답이 아님 | 현재 **CC0 1.0**. description의 외부/파생 링크0. 기록된 train/held/eval/extra/public source와 이름·owner 겹침0. **향후 train 검수 후보**, 지금 학습 차단/approved null |

공식 page duration 합계는 **98.858초**지만 실제 양성 발성 합계가 아니다. 청취·다운로드를 하지 않았으므로 새 98.858초 학습자료 확보로 보고하지 않는다. CC0 표시와 commercial 허용 문구는 현재 게시자의 권리 표시이며, 원작자 lineage/아동의 월령·관계까지 입증하지 않는다. 현재 두 description에는 추가 stock/raw 제한 문구나 파생 링크가 없었으나 전체 권리 검증을 대체하지 않는다.

## 4. 현재 공식 license·preview snapshot

두 페이지는 **2026-10-06 21:09:04 UTC / 2026-10-07 06:09:04 KST**에 HTTP200으로 확인했다. license anchor는 둘 다 `http://creativecommons.org/publicdomain/zero/1.0/`이고 표시 이름은 `Creative Commons 0`이다. 페이지에 commercial use를 포함해 copy/modify/distribute/perform 허용 문구가 있다. 아래 hash는 **HTML bytes**이며 원음 hash가 아니다. preview 경로에 GET/HEAD를 보내지 않았다.

| ID | 공식 HTML bytes·SHA256 | 공식 HTML의 exact preview 경로 |
|---|---|---|
|9437|38,206 /4412221a2a7d6b46c3823155116547560253d86e9a83f950cb044b7b1c2248bd|https://cdn.freesound.org/previews/9/9437_383-lq.mp3|
|244237|45,807 /c72e8f98cbf47cb5162ff5899864a69a1f6ac0eda55a60742544f0aaea6b4736|https://cdn.freesound.org/previews/244/244237_152878-lq.mp3|

HTML이 preview URL을 제공하는 것을 확인했으며 실제 audio 응답·bytes·duration·음질은 확인하지 않았다. 기존 FSD annotation/모델 weight의 license와 이 두 원음의 현재 license를 서로 전파하지 않는다.

## 5. 후속 학습 승인 전 필요한 검수

1. 향후 담당자가 두 페이지의 권리/원작자/동일 source·파생 관계를 다시 확인하고, 적법한 취득 뒤 exact audio bytes/SHA/format/실제 duration을 기록한다. 이번에는 취득하지 않았다.
2. 모델 점수를 보지 않고 전체 음원의 실제 사건 onset/offset, infant vocal/nonvocal, babble/coo/gurgle/laugh/cry/fuss/uncertain/mix, 성인·toy·background·playback·연기 의심을 표시한다. 97.518초 전체를 양성으로 복제하지 않는다. 단일 검수의 한계나 독립 판독 불일치를 기록한다.
3. 청취만으로 정확한 월령이나 가족 독립성을 입증할 수 없으므로 caption claim을 유지한다. 일상 녹음 caption을 verified natural 표시로 자동 승격하지 않는다. 같은 원본/가족/업로더 관계가 드러나면 source를 보수적으로 합친다.
4. 이 두 그룹은 향후 train 측 검수 후보로 지정한다. 후속 평가에 먼저 소비하거나 threshold/calibration/좋은 구간 선택에 사용했다면 평가 노출 사실을 기록하고 독립 검증용으로 다시 세지 않는다. 기존 held/eval source를 train으로 옮기지 않는다. 실제 training 승인 전까지 blocked/null을 유지한다.
5. 검수 후에도 긍정 학습자료 4 source의 부족이 해결됐다고 즉시 결론 내리지 않는다. source 전체를 묶은 split, 성인 흉내/TV/생활잡음 반례, 별도 held source와 실행 경로 검증이 필요하다. 모델 출력은 정답 라벨로 쓰지 않는다.

부모 작업자의 이번 frozen score replay **51조건 = 승인 출력 보존18 / 높은 presence와 감정 맥락 보류8 / presence 없음25**는 별도 논리 회귀다. 이 문서는 그 실행 산출물을 재감사하지 않았으며 새 acoustic inference·앱 누락 회복·새 학습 증거로 세지 않는다.

## 6. 실제 조회 예산과 실패

외부 작업 시도는 실패도 보수적으로 포함해 **7/8회**이며 종료했다. 성공한 metadata 조회는 **검색3 + 공식 HTML GET2 = 5회**다. timeout은 각20초로 설정했고 실제 timeout0이다. 내부 HTTP/redirect 횟수가 아닌 발행한 query/GET 시도 기준이다.

| 시도 | 종류·정확한 query 또는 URL | 실제 결과 |
|---|---|---|
|1–2|아래 3–4 query와 같은 Exa 호출을 PowerShell wrapper로 발행|CLI 인자 hydration 단계 `Too many positional arguments (3)` 실패2. 검색 결과0. 실패를 예산에 포함했고 쿼리를 직접 Node argv/JSON으로 전달하도록 수정 |
|3|`site:freesound.org/people/ baby babbling "8 months" OR "10 months" OR "12 months" -NoiseCollector -reinsamba -bvaudio -clod111 -klankbeeld -Islabonita`|성공. Rodcencko9437/Sauron974244237 발견. FunWithSound·기존 held·target 밖 신생아 등 제외 |
|4|`site:freesound.org/people/ baby babble "14 months" OR "18 months" OR "one year" -carameans -amccoy711 -BoilingSand`|성공. 위 두 후보 재반환. Robinhood76 / ID82710은15개월 `Mama` word caption이므로 옹알이 후보로 추가하지 않음. 기존 source 결과 제외 |
|5|`https://freesound.org/people/Rodcencko/sounds/9437/`|HTML200·현재 CC0·exact preview 경로 확인 |
|6|`https://freesound.org/people/Sauron974/sounds/244237/`|HTML200·현재 CC0·exact preview 경로 확인 |
|7|`site:freesound.org/people/ baby "babbling" "months old" "8" -NoiseCollector -reinsamba -Islabonita -bvaudio -clod111 -klankbeeld -FunWithSound`|성공. 반환6개 모두 bvaudio/Islabonita/NoiseCollector/clod111의 기존 제외 그룹. 추가 후보0 |

검색의 exclusion 문법만 믿지 않고 모든 반환 업로더를 로컬 제외 목록과 다시 대조했다. 공식 GET은 `urllib` timeout20초, 성공 검색은 CLI timeout20,000ms 및 process timeout20초를 함께 적용했고 각 실제 호출은20초 안에 완료했다. 음원/feature/weight body 취득 **0**, 청취/추론/학습/제품변경 **0**이다.

**완료:** 기존 평가 source의 train 재활용 제외, 새2그룹의 exact ID/현재 CC0/preview·caption·중복 한계 고정. **미완료:** 실제 사건 검수·원음 취득·월령/자연성/가족 관계 확인·학습 승인. 현재 승인된 새 train 자료 **0**이며 기존 양성 7 clip/4 source/40.7초의 부족은 남아 있다.
