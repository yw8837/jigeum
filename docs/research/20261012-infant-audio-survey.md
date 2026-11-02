# 영아 발성 검출·시간 구간·공개 모델 조사

확인일: **2026-10-12 KST**. 아가톡의 대상 6–18개월, 온디바이스 처리, 사용자 원본 외부 전송 금지를 기준으로 조사했다. Agent Reach의 Exa 검색, Jina 원문, GitHub CLI/API, Hugging Face 모델 카드 텍스트, Zenodo 메타데이터를 실제 조회했다. 모델·원음·데이터셋을 받거나 학습·추론·Android/ADB·Play 작업을 수행하지 않았다. 이 문서만 소유하며 다른 작업자의 변경을 되돌리지 않는다.

## 먼저 실행할 일

**(추천) 새 영아의 짧고 조용한 발성에 시간 구간 정답을 붙인 독립 평가를 먼저 만들고, 기존 시간축 후보를 비교한다.** 영아별 검증과 상업 권리를 갖춘 데이터에서 presence/종류/시간 경계를 학습하는 방향이 현재 문제에 가장 직접적이다. 조용한 옹알이를 일반 AudioSet 모델 교체만으로 회복하거나, 울음 검출을 배고픔·졸림·놀이 요청 정확도로 환산할 근거는 확보하지 못했다.

이번에 재확인한 중요한 공개 변화는 **ChildVox의 실제 가중치 배포**다. 다만 최신 공식 release README와 모델 카드 모두 **`No commercial use`**를 명시한다. 연구 관찰 후보이며 유료 앱 탑재·상업 fine-tuning/distillation 승인 근거로 삼지 않는다. 공개 모델 접근과 앱 배포 권리를 따로 확인해야 한다.

## 기존 결과와 겹치지 않는 범위

현재 HANDOFF의 code39는 저장·결과 UI 수정이며 일반 인식률 개선을 입증하지 않는다. 아래 연구 결과는 과거 문서에서 읽은 사실이며 이번에 재실행하지 않았다.

| 기존 비교 | 이미 남아 있는 결과 | 이번 조사에 주는 제약 |
|---|---|---|
| AST 2초/4초, PretrainedSED `frame_mn10_strong_1.pt` | 알려진 누락 두 구간에 회복 근거 없음. 두 구간은 한 원본에서 겹침 | 같은 원본에 창·문턱을 다시 맞춘 결과를 새로운 인식률로 세지 않는다 |
| PANNs Cnn10 | 같은 보존 누락에 새 회복 근거 없음 | 더 작은 일반 CNN이라는 이유만으로 다시 채택하지 않는다 |
| VTC1 | 누락에 KCHI 구간은 남았으나 피아노 오인, TV/화자 평가 부족, 가중치 권리 미확정 | 유망한 child-presence 연구 신호이며 앱 승인·옹알이·욕구 정답이 아니다 |
| EfficientSED 연구 export/frontend | Kotlin·Python 호스트 일치에 이어 Android 에뮬레이터 합성 **7조건 수치 일치 완료** | 남은 것은 새 영아 누락·성인/TV/겹침 평가, ARM 실행·자원·시간 격자 계약이다. 완료한 frontend 단계를 다시 우선과제로 제시하지 않는다 |

근거: [누락 비교](20261006-short-context-and-pretrainedsed-results.md), [PANNs](20261006-panns-cnn10-contract.md), [VTC1](20261007-vtc1-first-screen-results.md), [EfficientSED frontend의 후속 Android 기록](20261006-efficientsed-kotlin-frontend.md). **EfficientSED 기존 export와 이번에 확인한 PretrainedSED 최신 MobileNet 목록의 정확한 파일·hash는 서로 대조하기 전 같은 artifact로 취급하지 않는다.**

## 후보 비교

아래 크기·mAP는 해당 원천의 공개값이다. Android 지연·한국 가정 영아 정확도·욕구 번역 정확도가 아니다. `미확정`은 허락이 없다는 단정도, 허락이 있다는 뜻도 아니다.

| 후보 | 실제 출력·새 근거 | Android 가능성과 한계 | 코드 / 가중치 / 데이터 권리 | 판단 |
|---|---|---|---|---|
| **기존 시간축 CNN + 권리가 확인된 영아 라벨 (추천)** | 존재·발성 종류·onset/offset을 각각 평가 | 이미 있는 연구 frontend/ORT 경로 활용 가능. ARM 성능·새 영아 일반화는 미검증 | 기존 exact artifact와 새 학습 원음·라벨·동의 범위를 별도 확인 | 새로운 엔진 목록보다 독립 시간축 평가를 먼저 진행 |
| ChildVox Whisper-base BabbleCor/SpeechMaturity | canonical/non-canonical/cry/laugh/junk 5종. HF 카드 최대1초·16k mono, fold1–5. 최신 GitHub는 실제 배포/EMNLP2026 Main accepted 표시 | 짧은 입력 comparator 가능. encoder와 LoRA/head 변환·padding·층 결합 검증 필요. 정리된 clip 종류 분류이며 연속 영상의 detector가 아님 | GitHub licenseInfo null. HF `openrail`에 더해 **No commercial use**. 데이터 접근 가능 여부와 학습/재배포 조건도 별개 | 상업 탑재·상업 distillation 후보에서 제외. 허용 범위의 연구 비교만 검토 |
| ChildVox BabyHuBERT | 동일 발성 5종; repo는 모든 hidden layer의 학습 가중 결합, LoRA 미사용 설명 | 긴 backbone/여러 층 feature가 필요. small head만 복사하면 같은 모델이 아님. ARM 실측 없음 | 동일 상업 금지. BabyHuBERT upstream 조건도 별도 | 논문의 표현 방식 참고 |
| LittleBeats wav2vec2 | 집 안 가족 오디오 pretraining, child cry/fuss/babble와 가족 화자 분류. 가족 분리 평가 확인 | base 12 transformer layers; 기존 조사 encoder 약377.6MB. 2초 창/0.2초 hop 연구이므로 비용 큼. 14–18개월·폰 거리·TV 도메인 부족 | SpeechBrain 코드 조건과 weights를 구분. HF generic `openrail`, 특정 전문·상업 재배포 grant·exact recipe 연결 미확정. 가족 원음은 별도 | representation의 도메인 학습 필요성을 지지; 당장 배포용 아님 |
| VTC1 | KCHI/CHI/MAL/FEM/SPEECH 다중 라벨; 기존 연구에서 누락 child 구간 존재 | 작은 checkpoint여도 촘촘한 BiLSTM frame 연산·폰 domain shift 존재. wearer key-child가 영상 얼굴 주인이 아님 | dependency MIT를 task weights 허락으로 확대하지 않음. 배포 권리 미확정 | 이미 확보한 결과·오인을 보존하고 권리 해결 전 제품 미채택 |
| EfficientAT MN | 공개 `mn04_as`:0.983M params/0.11B MACs/mAP43.2%; release4,066,513B. `mn10_as`:4.88M/0.54B/mAP47.1% | MobileNetV3는 비교적 변환하기 쉬운 구조. 기본32k/128mel/10ms를 기존16k frontend와 혼동 금지. clip pooling은 짧은 발성 희석 가능 | repo MIT 확인; release와 exact weight 적용 범위·notice 보존 확인 필요. AudioSet 원음은 YouTube별 권리 | 기존 계열을 반복 실행할 근거 부족. 영아 head의 작은 backbone 후보 |
| PretrainedSED frame-MN | 공식 현재 README `frame_mn06`1.62M, `frame_mn10`3.83M; AudioSet strong 기반 frame 표현. ensemble pseudo-label447×250/10초(40ms) | clip tag보다 시간축 학습 목적이 맞음. 출력격자40ms가 실제 사건 onset 정확도40ms라는 뜻은 아님 | repo MIT 확인. 정확 weights·teacher·pseudo-label/원음 권리는 연결 확인 | 기존 Android 일치 완료 경로와 새로운 실영아 평가를 연결. 알려진 누락 회복0 제한 유지 |
| PANNs | 일반 AudioSet CNN. tagging과 DecisionLevel SED checkpoint는 별도 | Cnn10과 Cnn14 SED를 혼동 금지. 32k default와 16k 변형의 별도 config 유지 | 코드 MIT. **Zenodo 공식 pretrained record는 CC BY4.0 확인**. AudioSet 원음 권리는 별도 | 기존 Cnn10 실패 보존. 일반 baseline 교체 우선도 낮음 |
| PaSST / BEATs | PaSST는 patchout audio transformer, 기본32k; BEATs는16k self-supervised audio tokenizer 기반, 공개 AudioSet fine-tuned weights | transformer reference/teacher 후보. Android export·memory·CPU/NNAPI 비용 검증 없음. patchout 학습 효율을 폰 추론 효율로 환산 금지 | PaSST repo Apache2; BEATs README는 root MIT 연결. exact weights 고지 및 AudioSet 원음은 각각 확인 | 큰 일반 모델의 mAP만으로 영아 개선을 주장하지 않음 |
| ICSD CRNN+BEATs | 실제/합성 strong·weak 라벨, cry/snoring 시간 검출. non-target 음악/잡음/말소리 false positive 별도 평가 | 아기 울음 detector 설계로 유용. noncry babble label 없음. BEATs 임베딩 사전 계산 경로는 앱 실행 증거가 아님 | repository license null, gated HF 데이터·checkpoint. README는 **원음 저작권을 ICSD가 소유하지 않음** 명시. ESC-50 등 원 출처 조건 유지 | 시간 구간·오인 평가의 설계 참고. 상업 detector artifact 미확정 |

## 영아 자료의 적합성·분할·라이선스

**BabbleCor / ComParE BabySounds.** canonical/noncanonical/cry/laugh/junk는 욕구가 아닌 발성·발달 범주다. 기존 공식논문 감사에서 BabySounds는 2–36개월46명, baby-disjoint/age-balanced partition으로 확인됐다. 짧게 잘린 발성 분류는 긴 폰 영상의 침묵·어른 말·TV·겹침 속 검출과 다른 문제다. ChildVox 카드의 5fold 공개만으로 원 corpus의 아이 분리 정책을 그대로 지켰다고 증명할 수 없다. 이번에 ChildVox release tree/README를 읽었으나 실제 infant IDs/fold mapping을 확보하지 못했다. [기존 공식논문 감사](20261006-specialist-actionable-next.md), [ComParE2019 원천](https://www.isca-archive.org/interspeech_2019/schuller19_interspeech.html).

**LittleBeats.** 원문 Methods는 라벨 있는70개10분 녹음을 **train52/dev6/test12 recordings**, 가족 **15/3/4**로 나누고 가족이 겹치지 않게 했다고 명시한다. 연령 그룹은1.1–4/4–9/9–13/13–14개월이다. 두 화자 이상 구간을 버렸고, 외부 라벨 자료는 낮은 energy 자료를 제거했다. 따라서 가족 독립 검증이라는 장점이 있지만, **조용한 신호·겹침·14–18개월**의 제품 문제에 바로 전이된다는 근거는 약하다. pretraining의 unlabeled 가족과 downstream test 가족도 분리됐는지는 이 Methods 문단만으로 확정하지 않았다. card의 generic OpenRAIL을 commercial grant로 읽지 않는다. [원문](https://pmc.ncbi.nlm.nih.gov/articles/PMC12875392/), [현재 모델 카드](https://huggingface.co/lijialudew/wav2vec_LittleBeats_LENA/raw/main/README.md), [기존 exact artifact 권리/입력 감사](20261007-littlebeats-input-license-followup.md).

**Donate-a-Cry.** 원천 README는 이름의36자 UUID를 앱 설치 instance로 설명한다. 아이·가족·device의 확정 식별자가 아니다. 이유는 부모의 suspected reason이며 정확 생리 측정이 아니다. **ODbL1.0 + 개별 contents DbCL1.0**를 명시하므로 무조건 NC 자료라고 부르는 것도 틀리다. 재사용에는 고지·database adaptation/share-alike 범위를 확인해야 한다. 원음과 gain/crop/noise 파생본을 먼저 묶은 뒤 group split해야 하고, 설치 UUID 분리가 infant-disjoint를 완전히 보장하지 않는다. 기존 원음 감사의 hunger 비율 약83.59%와 7개월–2세 구간81개는 이번에 다시 세지 않았다. 7개월–2세를 정확6–18개월 표본이라고 쓰지 않는다. [README/권리](https://github.com/gveres/donateacry-corpus), [기존 집계](20261005-infant-needs-expression-evidence.md).

**ICSD와 ESC-50.** ICSD는 AudioSet/Donate-a-Cry/ESC-50/BCD 등에서 모은 울음·코골이 검출 corpus다. ICSD 공개 repository가 라이선스 grant를 제공하지 않으며 원음 copyright는 원 소유자에게 있다고 명시한다. ESC-50 전체는 **CC BY-NC**이고, 원 source fragment를 같은 fold에 묶는다. 이를 섞은 데이터·checkpoint를 하나의 permissive commercial dataset으로 설명하지 않는다. AudioSet annotated metadata의 허락도 YouTube 원음/초상·동의 허락을 대체하지 않는다. [ICSD 원문](https://arxiv.org/html/2408.10561), [ICSD README](https://github.com/QingyuLiu0521/ICSD), [ESC-50 README](https://github.com/karolpiczak/ESC-50).

## 시간 구간 검출에서 받아올 설계

1. **Clip score와 사건 구간을 분리한다.** AST의10초 관찰 점수나1초 종류 점수가 높아도 전체 창에 발성이 이어졌거나 onset이 창 시작이라고 표시하지 않는다. 시간 라벨이 있는 SED representation으로 presence/kind별 frame 출력을 학습하고, 같은 원음의 짧은 사건 recall을 별도 측정한다.
2. **다중 사건과 reject를 유지한다.** 울음·옹알이·성인·TV는 긴 클립에서 공존한다. 동일 선택 전체의 softmax로 하나만 고르면 짧은 발성이나 겹침이 사라질 수 있다. presence/kind/overlap/불확실성을 분리하고 일반 울음·비명을 영아 단서로 무조건 합치지 않는다.
3. **시간 경계·오인을 함께 평가한다.** frame-F1, event-F1, false-positive duration/events per negative minute, 짧은 사건 duration strata별 recall을 함께 쓴다. 단순 clip accuracy나 음성창 누적 수를 실제 사건 수로 바꾸지 않는다. onset/offset collar와 event matching 규칙은 실험 전에 고정한다.
4. **도메인이 맞는 학습·distillation을 검토한다.** LittleBeats는 큰 일반 pretraining보다 가족 오디오 representation의 이득을 보고한다. EfficientAT/PretrainedSED는 큰 teacher를 CNN으로 옮기는 일반 소리 설계를 제공한다. 이는 작은 영아 detector의 연구 가설을 지지하지만, 상업 금지 teacher를 상업 student로 옮겨도 된다는 권리 근거가 아니다.

**2025 신규 참고 논문:** [Infant Cry Detection Using Causal Temporal Representation](https://arxiv.org/html/2503.06247v1), ICASSP2025/2025-03-08 공개. 3명 annotation을 각 표본에 적용해 cry/no-cry 시간 구간을 만들고8초를50ms 간격으로 나눈다. Methods의80/20 random split은 아이 독립 검증을 보장하지 않는다. CRSTC/ST-VAE는 sparse transition·LSTM temporal clustering이며 욕구 인과를 알아낸다는 뜻이 아니다. 정확한 제품용 checkpoint·상업 권리를 이번 범위에서 확보하지 못했으므로 구조·평가 참고로 둔다.

[Infant Cry Detection In Noisy Environment Using Blueprint Separable Convolutions and Time-Frequency Recurrent Neural Network](https://arxiv.org/abs/2508.19308), 2025-08-26 공개. separable conv·multi-scale CRNN·time-frequency RNN denoising은 경량 잡음 검출 설계다. 논문은 [공식 ICD_MMSP](https://github.com/fhfjsd1/ICD_MMSP)를 연결한다. 오늘 Git tree의 파일명 조사에서 pth/pt/ckpt와 LICENSE를 확인하지 못했고 GitHub license null이다. 정확 weight·분할·상업 권리를 확보한 엔진으로 올리지 않는다. 울음의 noisy classification 이득을 조용한 noncry babble 회복으로 바꾸어 말하지 않는다.

## 욕구·감정 해석의 경계

[Lockhart-Bouron 등의 Communications Psychology2023 원문](https://pmc.ncbi.nlm.nih.gov/articles/PMC11332224/)을 오늘 다시 읽었다. 24아기의 생후15일–3.5개월 가정녹음에서39,201 cry units를 분석했으며 울음을 멈추게 한 부모 행동으로 hunger/discomfort/isolation을 라벨했다. 부모 추정과75% 일치하지만 생리적 욕구의 직접 참값은 아니다. 논문의 결론은 나이·개인 음향 정보는 존재하나 원인 분류는 신뢰롭게 되지 않았다는 것이다. **39,201개를 독립 아기 수로 세거나 이 초기 월령 연구를6–18개월 모든 영상의 불가능성 증명으로 확장하지 않는다.**

ChildVox 공식 페이지의 공개 표도 cry-cause(CryBank)에서 일반 encoder 수치0.30–0.333, Donate-a-Cry0.467–0.528을 보고한다. 이 페이지의 metrics·분할·label skew·실제 model revision을 다시 고정하기 전 앱 정확도로 사용하지 않는다. ReCANVo의 `request`나`frustrated`는 최소 발화 이용자 corpus의 분류이며 6–18개월 아기의 놀이·배고픔·졸림 참값과 같은 라벨이 아니다. 이번 조사에서 **독립6–18개월 아이의 같은 영상·소리·상황 정답으로 hunger/sleep/play를 검증하고 상업 배포 권리를 확인한 완성 모델**을 확보하지 못했다. 전 세계 부재의 결론은 아니다.

반복 문구를 개선할 때는 관찰 가능한 차이를 문장에 반영한다. 예: 짧은 발성이 쉬었다 반복되는지, 후반에 커지는지, 울음과 비울음이 번갈아 나타나는지, 성인과 겹쳐 판단을 보류한 구간이 있는지. 검출된 소리의 시간 흐름이 다양하면 근거 있는 설명도 달라질 수 있다. 랜덤 욕구 문장을 늘리는 것은 정확도 개선이 아니다.

## 다음 실험 우선순위

1. **새 영아 시간축 평가를 먼저 고정 (추천).** 기존 겹치는 개발 원본을 그대로 보존하고, 모델 선택/문턱 조정에 쓰지 않은 새6–18개월 아이·가족·원음·source를 holdout에 묶는다. 짧은 조용한 noncry/cry/laugh, 성인 고음·웃음·TV baby replay·음악·겹침·무음 정답을 포함한다. 기존 uploader 제목·caption만으로 영아/종류/의도를 확정하지 않는다. 두 사람이 onset/offset/kind/unknown을 독립 표시하고 disagreement를 보존한다.
2. **기존 실제 시간축 후보 비교.** 새 고정 holdout에서 현재 YAMNet+AST/head와 기존 EfficientSED 연구 모델의 구간별 recall/오승인/보류를 비교한다. 이미 완료한 에뮬레이터 synthetic parity는 재실행 우선과제가 아니다. 앞서 AST 짧은창·PANNs Cnn10·별도 PretrainedSED artifact에서 얻지 못했던 알려진 누락 회복을 EfficientSED의 결과로 혼동하지 않는다. 이들의 기존 실패와 EfficientSED의 기존 국소 단서를 각각 보존하고 새 자료의 이득을 비교한다. 같은 clip의 overlap 창을 독립 samples로 세지 않는다.
3. **권리 있는 영아 temporal head/작은 CNN 학습.** generic AudioSet `Babbling` 단독보다 infant/noninfant presence와 cry/noncry/laugh/unknown 구간 정답을 목표로 한다. 먼저 학습 데이터·teacher·weights의 상업 grant를 확인하고, source/child-disjoint train/dev/test를 유지한다. 모델이 조용한 발성을 버리지 않도록 음량 strata를 평가하며 synthetic noise/gain 파생본은 원본과 같은 split에 묶는다.
4. **ARM 비용과 품질을 같이 검증.** 선택한 전체10/20초 coverage를 유지하고 cold/warm 초기화·decode/frontend/model/postprocess 시간, peak memory·발열, 취소, 원래 성공 보존을 측정한다. 정확도 우선 사용자 조건에 따라 속도만을 위해 관찰 범위나 임계값을 바꾸지 않는다. 실제 권리·새 영아 개선·Android 실행 gate를 통과한 artifact만 제품 채택으로 넘긴다.
5. **욕구 표현은 별도 근거 확보 후.** 소리 검출 개선에서 hunger/sleep/play를 자동 생성하지 않는다. 행동·상황 라벨과 대상 월령 독립 검증이 없으면 중립적인 발성·시간 흐름 설명을 유지한다.

## 실제 조회 원천·제약

외부 조회는 공개 검색·논문/README/LICENSE/card·metadata만 수행했다. Exa 최초 infant query/LittleBeats 검색 성공 후 free MCP rate limit을 받았고 추가 검색에는 반복 호출하지 않았다. 연결 web_search fallback도 error/content0였다. Jina의 Hugging Face 접근은403이어서 같은 모델의 **공개 raw README 직접 GET**으로 확인했다. source를 읽을 수 없는 부분을 검색 snippet으로 검증했다고 쓰지 않는다.

| 원천 | 오늘 확인한 내용 |
|---|---|
| [ChildVox 공식 release](https://github.com/tiantiaf0627/childvox-release) | commit **2af22d97d312913fe92d935bffa824d628661deb**, 실제 모델 목록/LoRA·layer 결합/No commercial use |
| [BabbleCor Whisper-base 카드](https://huggingface.co/tiantiaf/childvox-babblecor-whisper-base/raw/main/README.md) | model revision metadata **2c741cf4e6af097cef4846c61019e494f42459b6**, 1초16k/5종/folds/No commercial use |
| [ChildVox landing](https://tiantiaf0627.github.io/childvox/) | benchmark 표·상업 금지. landing은under-review/release-soon인 반면 최신 repo/card는accepted/released이므로 현재 artifact 상태는repo/card를 우선 |
| [LittleBeats 원문](https://pmc.ncbi.nlm.nih.gov/articles/PMC12875392/) / [card](https://huggingface.co/lijialudew/wav2vec_LittleBeats_LENA) | 가족 분리15/3/4,2초/.2초,겹침 제외·외부 low-energy 선별,base12층;generic openrail |
| [EfficientAT](https://github.com/fschmid56/EfficientAT) / [release](https://github.com/fschmid56/EfficientAT/releases/tag/v0.0.1) | MIT code, 모델 표·FP16 caveat, release MN04/MN10 actual size |
| [PretrainedSED](https://github.com/fschmid56/PretrainedSED) | MIT, 신규 frame-MN1.62M/3.83M,447×250 pseudo-label 시간축 |
| [PANNs repo](https://github.com/qiuqiangkong/audioset_tagging_cnn) / [Zenodo metadata](https://zenodo.org/api/records/3987831) | MIT code, tagging/SED separate checkpoints; weight record license **cc-by-4.0**, DOI10.5281/zenodo.3987831 |
| [PaSST](https://github.com/kkoutini/PaSST) / [BEATs README](https://github.com/microsoft/unilm/tree/master/beats) | Apache2/MIT project code 조건,32k/16k 입력·pretrained/fine-tuned 분리 |
| [ICSD](https://github.com/QingyuLiu0521/ICSD) / [paper](https://arxiv.org/html/2408.10561) | gated HF checkpoint,original owner copyright,license null,cry/snoring·non-target false positive 평가 |
| [Donate-a-Cry](https://github.com/gveres/donateacry-corpus) / [ESC-50](https://github.com/karolpiczak/ESC-50) | instance UUID,ODbL/DbCL;ESC-50 NC/source-file folds |
| [2023 cry 원인 연구](https://pmc.ncbi.nlm.nih.gov/articles/PMC11332224/) |24아기/39,201개/초기월령,원인 음향 분류의 한계 |
| [CRSTC2025](https://arxiv.org/html/2503.06247v1) / [noisy2025](https://arxiv.org/abs/2508.19308) |시간 구간 annotation·random split 한계,경량CRNN 연구;제품용권리/weight 미확정 |

Agent Reach `check-update`는 **v1.5.0 최신**으로 응답했다. 설치·업데이트·유료키 설정을 수행하지 않았다. 배포 후보마다 exact source/weight hash·notice·license 전문을 고정하는 일은 실제 채택 직전의 별도 gate이며, 이 문서의 공개 metadata 확인을 법률 검토 완료로 보고하지 않는다.
