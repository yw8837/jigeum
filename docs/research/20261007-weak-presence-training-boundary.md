# caption weak 영아 존재 학습과 사건 정답의 경계

확인일 **2026-10-07 KST**. `research` background 조사로 현재 계약·실제 Python 구현과 AudioSet 공식 원문을 읽었다. 작성 파일은 이 문서 하나다. 코드 수정·원음/feature/weight body 접근·추론·학습·사용자 기록 접근·Git 명령·빌드·ADB·삭제·외부 연락은 **0**이다. 외부 조회는 `agent-reach` Jina Reader로 공식 HTML/text 2페이지만 읽었으며 음원·모델·자료 corpus는 취득하지 않았다.

## 1. 현재 판단

**새 9437/244237의 미검수 학습을 현재 계약으로 승인할 근거는 없다.** 과거 public35의 caption weak 학습은 실행 전 정한 exact corpus의 연구 비교였고, 새 blocked 자료에 계속 적용되는 포괄적 승인 규칙이 아니다. 최신 [라벨 근거](20261007-infant-head-label-evidence.md) 136·142행은 review 완료 train bag만 후속 비교에 허용한다. [새 source 조사](20261007-next-infant-training-source.md) 9·56·58행은 두 파일의 `training_blocked=true`, `approved_training_label=null`, 미검수를 명시하며 실제 사건 검수와 학습 승인을 분리한다. 이 조사에서는 그 상태를 바꾸지 않는다.

**weak bag 학습 자체와 사건 정답 학습은 서로 다른 연구 목표다.** 모델 점수로 찾은 창을 정답으로 만들지 않고, 게시자 caption의 파일 수준 존재 주장을 noisy target으로 기록하는 연구는 정의할 수 있다. 그러나 현재 두 자료를 그런 실험에 사용하려면 미검수 연구 사용을 별도로 다루는 새 계약과 권리·입력·분할·평가 조건이 필요하다. 기존 사건 라벨의 null/blocked를 지우거나 `audio_review_completed=true`로 바꾸는 것은 허용 근거가 아니다. 이 문서는 새 실험을 실행하거나 승인하지 않는다.

## 2. 과거 방법에서 실제로 학습한 것

| 직접 확인한 근거 | 실제 의미와 한계 |
|---|---|
| [AST 계약](20261007-ast-infant-head-contract.md) 11–27행 | 고정 public35, 첫20초, 비중첩10초 최대2span, train21/18source, 영아7/4source. caption weak이며 사건·욕구 정답이 아니라고 사전 명시 |
| [ast_infant_head_probe.py](../../tools/research/ast_infant_head_probe.py) 56–62·155–161행 | RMS≥.003 span의768 표현을 실제 sample 가중평균한 뒤 L2 정규화한다. clip당 한 target `weak_class=='infant_vocal'`로 binary BCE head를 fit한다. 각10초 창을 개별 양성 training row로 복제하지 않음 |
| 같은 코드 126–128·173–179행 | feature metadata에 `audio_review_completed=False`; 결과는 `weak_labels_only=True`, `calibrated_probability=False`, `quality_validated=False`, `product_changed=False`. 이는 검수 완료의 증거가 아님 |
| [infant_head_screen.py](../../tools/research/infant_head_screen.py) 69–100행; AST runner 96–101행 | source loader는 exact ID/role/license/source 분할과 `training_blocked`를 검사한다. AST runner는 exact public35와 SOURCE_SHA만 받음. 두 새 ID나97초 전체를 넣는 일반 학습기가 아님 |
| [라벨 근거](20261007-infant-head-label-evidence.md) 7·33·47행 | 과거 양성은 40.702312초의 clip 입력이지 검증된 영아 발성 시간이나 독립 아기7명 자료가 아님. 영아 validation4개도 한15개월 source이고 모두 검수 미완료 |
| [AST 결과](20261007-ast-infant-head-results.md) 32–56행 | 약국 두 구간은 동일 원본의 겹친 개발 사례. held·public 결과도 좁은 caption 자료이며 일부 infant miss가 남았다. score 통과를 일반 영아 정확도나 안전한 한마디 승인으로 확장하지 않음 |

과거 mean pooling은 window별 정답 복제를 하지 않았지만 모든 nonquiet span이 clip 평균에 기여한다. toy/adult/music/호흡·방음향·마이크 channel이 clip label과 함께 나타나면 그 표현을 학습할 여지가 있다. RMS는 음량이고 infant identity 검사가 아니다. class/source/clip 균등 가중은 한 source의 clip 수에 따른 loss 독점을 줄이지만 source 안의 상관 단서를 제거하지 않는다. 따라서 **과거 방법의 실행 가능성과 제한된 회복 증거는 확인되나 실제 화자·음향 shortcut에 대한 안전성은 입증되지 않았다.**

원문의 요청 경로 `infant_head_mil_probe.py`는 없으며 실제 구현은 [infant_mil_probe.py](../../tools/research/infant_mil_probe.py)다. 파일 조회 오류1은 이 경로로 해결했다. 이는 제품 실행 실패가 아니다.

## 3. 공식 weak 라벨의 의미와 우리 caption의 차이

[AudioSet 공식 dataset](https://research.google.com/audioset/dataset/index.html)은 “human-labeled 10-second sound clips”, annotator가 segment에서 들린 소리의 존재를 확인했다고 설명한다. nomination에 YouTube metadata와 content search를 사용했다. [공식 CSV 설명](https://research.google.com/audioset/download.html)은 같은10초에 Keys jangling과 Car가 함께 존재하는 예와 label co-occurrence를 명시한다. 따라서 파일/segment의 존재 라벨은 모든 시각에 그 소리가 있거나 같은 화자가 냈다는 사건 정답을 제공하지 않는다.

같은 공식 페이지의 **Quality Assessment and rerating**은 misinterpretation/confusability/difficulty 때문에 일부 class의 정확도가 낮았고, expert가 class별 무작위10segment를 확인한 뒤 instructions와 cluster labeling을 개선했다고 설명한다. class마다 최대1,000segment를 재평가하므로 unbalanced train의 품질이 balanced split과 상당히 다를 수 있다고 경고한다. 여기의 class quality estimate는 우리 head score의 calibration도, 새 source의 정확도도 아니다.

우리 Freesound caption 두 개에는 이러한 사람의 **실제10초 소리 존재 확인**이 없다. 공식 weak supervision 사례가 있다는 이유로 미청취 caption을 AudioSet과 같은 검수 수준이라고 쓰면 안 된다. 사람 주석도 오류가 있을 수 있지만, 그 사실은 사람 검수 부재를 검수 완료로 대체할 근거가 아니다. caption에 맞는 사건을 찾거나 모델 score를 먼저 본 검수자는 확인 편향을 가질 수 있으므로 후속 event 검수는 score·예측 결과를 가리고, 가능하면 caption/태그도 가린 첫 판독과 출처 주장 확인을 분리해야 한다. 이는 후속 검수 제안이며 지금 수행한 검수가 아니다.

## 4. 이전 허용 조건을 새 자료에 적용하려면

| 조건 | 새 자료의 현재 상태·필요한 조치 |
|---|---|
| 허용 범위 | 과거 exact public35 연구 계약을 새 ID로 자동 확대하지 않는다. 현재는 두 자료 모두 비승인. 현재 계약대로 진행하면 실제 local review 완료가 먼저임 |
| 라벨 의미 | event 정답·canonical babble·감정·욕구·현장 화자를 승인하지 않는다. 별도 미검수 실험을 제안할 경우 `weak_target_origin=publisher_caption`, `weak_target_scope=whole_recording`, `event_truth_verified=false`와 미검수 상태를 별도 manifest로 명시해야 함 |
| 권리·동일성 | 현재 CC0 표시만으로 actual audio lineage·권리·동일성·자연성이 확인된 것은 아님. 적법한 취득 경로, 실제 bytes/SHA/format/duration, current/archived 조건과 파생 관계가 필요함. 이번에는 원음 미취득 |
| source 분할 | 새 후보는 향후 train 쪽에만 지정. 이름/owner 중복0은 실제 가족/재게시 독립성 증명이 아니므로 확인된 관계는 보수적으로 합침. existing held/eval·사용자 자료를 train/normalizer/calibration/augmentation으로 이동하지 않음 |
| 입력과 bag 범위 | caption이 설명하는 원파일 전체와 loss에 넣는 범위를 연결해야 함. 첫20초 crop의 infant 존재를 원파일 caption만으로 확정하지 않음 |
| 동결·선택 | encoder/frontend·음량 기준·support .8/margin .4·제품 guard를 유지. 모델 점수를 보고 좋은 crop/source/L2/threshold를 고르지 않음. 새 가설·한도·비교 기준은 실행 전에 고정 |
| 승인 상태 | canonical `approved_training_label`을 weak 추정으로 채우지 않음. 별도 연구 사용이 명시적으로 허용되더라도 검수 완료나 제품 승인으로 보고하지 않음. 현재 loader는 blocked row를 거부하므로 field 삭제·false 변경으로 gate를 우회하지 않음 |

권리 또는 분할·bag 존재 가정이 해소되지 않거나 기존 review 요구를 유지한 상태라면 **새 미검수 weak fit을 거절하고 blocked/null을 유지하는 판단이 타당하다.** 단순히 양성 source 수를 늘리기 위해 승인 기준을 낮추지 않는다.

## 5. 97초 bag의 모든10초 창을 양성으로 복제하지 않는 방법

새244237의97.518초는 공식 페이지 metadata이며 실제 decode duration이 아니다. caption은 toy와 함께 coo/gurgle이 있다는 파일 설명이다. 전체 기록 어딘가의 존재 주장은 첫20초나 무작위 일부 crop마다 존재한다는 주장이 아니다. 현재 AST의 `ffmpeg -t 20`(119행), `spans`의≤20초(29–34행), 최대2span validation(46–52행)으로 전체97초 bag 연구를 했다고 보고할 수 없다.

1. **현재 계약의 권장 경로: 먼저 score를 가린 사람 검수.** source 자체의 infant 여부·성인/장난감/재생·uncertain를 확인하고 검수한 시간범위와 라벨 수준을 기록한다. event GT가 없으면 unknown으로 남긴다. 이 경로는 최신 review 조건을 충족할 수 있지만 지금 수행되지 않았다.
2. **별도 허용을 검토할 연구 가설: whole-recording bag loss 한 번.** 전체 실제 음원을 deterministic nonoverlap10초 context와 실제 tail로 나누되 모든 window의 instance label은 unknown이다. bag당 하나의 caption weak BCE를 적용하고, source/class/clip weight 합을 유지한다. 97초 파일이10개 독립 positive clip으로 loss에 들어가지 않게 한다. 첫20초만 읽거나 score로 몇 창을 뽑으면 whole-recording presence 가정을 충족했다고 할 수 없다. quiet 제외도 위치를 검증하지 않으므로 실제 sample/누락/범위를 기록해야 한다.
3. **집계 대안은 새 가설이다.** 현재 AST sample-weighted mean에 bag BCE를 적용하면 모든 창에 라벨을 복제하지는 않지만 배경 평균·희석 위험이 있다. 기존 YAMNet MIL 구현은 실제 duration 정규화된 log-mean-exp logit에 bag BCE를 적용한다([infant_mil_probe.py](../../tools/research/infant_mil_probe.py) 19–61행). 이는 모든 instance에 positive BCE를 거는 것과 다르다. 그러나 toy/adult의 큰 logit을 positive witness로 택할 수 있으며 attention을 infant event/정답 onset으로 해석할 수 없다. 이 구현은1024차원/≤21개의975ms창용이므로 AST768/97초에 그대로 적용했다고 말할 수 없다. AST용으로 정의·검사할 별도 연구 계약이 필요하다.
4. **금지할 대체 방식.** 원caption을 각10초 양성 label로 복사, 높은 score 창을 pseudo GT로 재훈련, 일부 crop이 caption을 대표한다고 확정, toy/quiet/background-only 구간을 검수 없이 true negative로 생성, 같은 원파일 창을 train/held로 나누기는 모두 사건·source 경계를 훼손한다.

여기서 전체bag 집계는 ground truth를 복원하지 않는다. weak 존재 가정이 틀리거나 background shortcut이면 bag loss만 개선될 수 있다. long bag과 short1.340초 파일은 context 수·padding·평균 희석이 달라 bag score와 single-window .8 판정의 관계도 별도 진단해야 한다. 어느 방식도 새 안전성이나 정확도 향상을 보장하지 않는다.

## 6. 사람 검수 없는 weak 실험과 실제 개선을 나눌 평가 조건

후속 계약을 작성한다면 다음 두 평가 수준을 분리하는 것이 필요하다.

**weak 실험에서 주장 가능한 범위:** 코드/수치/완료 QA, source별 caption weak NLL·support 변화, frozen 기존 개발 자료의 판정 보존/변화, 동일 조건에 대한 모델 비교다. train-only source CV는 source 누출을 줄이지만 caption 오류를 교정하지 않는다. 현재 [presence_weights](../../tools/research/infant_presence_probe.py) 22–36행과 [source folds](../../tools/research/infant_head_source_cv.py) 17–31행을 참고해 class/source/clip 균형, source별·양성/음성별 결과를 함께 보고한다. 기존 validation과 이미 반복 확인한 public/약국은 회귀 자료로 표시하며 신규 독립 test나 정확도 분모로 다시 세지 않는다.

**실제 인식 개선을 주장할 추가 조건:** 모델 선택 전에 분리한 새 held source·현장/재생 구분·score를 가린 실제 사건 검수가 필요하다. adult cry/laugh/scream/아기흉내, 음악/TV/폰 재생, toy/drop/crinkle, 물·호흡·마찰·방음향, 영아와 성인/장난감이 겹치거나 분리된 조건을 포함한다. 같은 원본의 quiet prefix/tail과 background-only가 필요하면 먼저 검수해야 한다. 반례 제목이나 synthetic score fixture만으로 음향 오인 검증을 했다고 쓰지 않는다. 오인·보류·miss를 숨기지 않고 실제 source/사건/노출 길이와 불확실성·판독 불일치를 보고한다. uploader를 가족/아동 수로 세지 않는다.

특히244237의 toy와 infant가 함께 있고9437이 짧은 studio/편집 음향일 수 있으므로 channel·toy·caption association만 학습할 위험을 반례에서 검사해야 한다. 이 위험은 지금 관측한 오인이라는 주장이 아니다. 비영아라고 caption에 적히지 않았다는 이유로 absence 정답을 만들지 않는다. 평가자의 caption·월령·모델 score 노출, 좋은 구간만 선택하는 편향과 동일 evaluator의 반복 사후판독도 기록해야 한다.

weak 학습자료만 추가하고 human-verified test가 없다면 결과가 좋아져도 **“caption weak 개발 비교 변화”**까지만 보고한다. `quality_validated=false`, 사건 timing/화자/감정/욕구 검증 false, 새 가족 정확도 미확정이다. frozen threshold .8을 바꾸지 않고 AST 원527 출력 동등성·기존 울음/웃음/보류 보존을 확인한 뒤에도 실제 온디바이스 ARM 성능·열·취소·서명 업데이트·beta QA는 별도 gate다. 연구 fit이나 PC의 positive support 증가는 앱 한마디 회복·배포 완료가 아니다. 사용자 자료훈련0·외부 원본 업로드0을 유지한다.

## 7. 실제 조사와 미완료

외부 조회 **2/최대4**, 각 `curl --max-time 20`, exit0/timeout0이다. `https://r.jina.ai/https://research.google.com/audioset/dataset/index.html`와 `https://r.jina.ai/https://research.google.com/audioset/download.html`의 공식 원문 text를 읽었다. 첫 페이지 class 목록의 출력은 절단됐지만 인용한 dataset 소개 문단은 보존됐고, 두 번째 quality/CSV 본문은 전체 출력됐다. feature 다운로드 링크는 읽은 문서 안의 자료이며 호출하지 않았다. 웹 corpus/HTML을 파일로 저장하지 않았고 추가 update 조회나 재시도도 하지 않았다.

완료: 기존 mean/BCE와 YAMNet MIL source 구현의 실제 라벨 단위, 현재 blocked/review 계약, 긴bag의 crop 상속 문제, weak 연구와 사건·품질 평가 경계를 조사했다. **미완료/비승인:** 새자료 실제권리·취득·검수·학습·새held 음향평가·모바일/배포. 부모 작업자가 새 실험 계약을 검토할 수 있는 근거 문서이며, 현재 새2source의 학습 차단을 해제하는 승인 문서는 아니다.
