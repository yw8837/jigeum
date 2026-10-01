# 아가톡 — 울음·발성 분석 근거와 엔진 선택

조사·원문 확인일: **2026-10-01 (KST)**. 대상: **아가톡 - 지금 내 기분이 그래**, 6~18개월 아기의 부모. 사용자는 월령 선택을 원하지 않으며, 원음·영상은 온디바이스에서 처리한다. 이 문서는 연구·설계 판단이다. 새 모델의 학습·앱 탑재·실기기 성능·아기 의도 정확도를 검증했다는 보고서가 아니다.

`docs/HANDOFF.md`의 0.4.5 설명과 기존 참고문서는 조사 출발점으로만 사용했다. 아래 기술·연구·권리 판단은 공식 소스, 원 논문, 저자 저장소, 데이터 배포자의 조건을 다시 읽어 작성했다. 인터넷 검색은 agent-reach의 Exa, 본문은 Jina Reader, GitHub는 `gh api`로 확인했다. 공개 문서·연구 코드·저장소 메타데이터를 읽었으며 아기 음성, 모델 가중치, 사용자 원음은 다운로드하거나 전송하지 않았다. 빌드·ADB·앱 소스 변경은 수행하지 않았다.

## 1. 이번 제품 결정

**0.5에서는 녹음마다 실제로 달라지는 소리의 종류·시간·변화를 설명하는 엔진을 먼저 완성한다. 공개 모델을 교체하는 것만으로 배고픔·졸림·놀고 싶은 마음을 자동으로 알아낸다고 표현할 근거는 확보하지 못했다.** 아래 결론은 연구에서 모든 의도 추론이 영원히 불가능하다는 뜻이 아니다. 현재 앱이 사용할 수 있는 모델·권리·대상 연령·외부 검증이 함께 충족되지 않았다는 판단이다.

1. YAMNet을 소리 사건의 기준 모델로 유지한다. **`Babbling`이라는 실제 클래스가 있다.** `Hubbub, speech noise, speech babble`는 별도 클래스다. `babbl` 부분 문자열만으로 둘을 묶으면 주변 말소리를 옹알이로 잘못 설명할 수 있다.[T1]
2. 60초를 한 점수로 평균내기 전에 구간별 관찰을 보존한다. 녹음 재생·구간 확인·불확실 상태를 제공하고, 소리가 약하거나 섞였을 때 특정 아기 말로 채우지 않는다. 이 항목은 제품 설계 제안이며 검증된 분류 성능 주장이 아니다.
3. 결과의 핵심 문장은 **실제 관찰 사실**에 연결한다. 보호자 상황은 선택 사항이며, 사용자가 고른 상황으로 음성 모델이 그 뜻을 알아맞힌 것처럼 보이게 하지 않는다.
4. 다음 모델 비교는 **YAMNet 임베딩+작은 분류기 → PANNs/SSAST-Tiny → voc2vec 교사 모델** 순서로 진행한다. 비교의 정답은 울음·웃음·옹알이 등 관찰 사건이다. 한국 가정의 6~18개월 소리에서 개선이 없으면 모델을 바꾸지 않는다.
5. **ICSD와 CryCeleb를 현재 상용 앱의 학습·파생 모델에 사용하지 않는다.** 공식 배포 조건이 비상업용이다. Donate-a-Cry는 상업 사용을 명시적으로 배제하지 않는 ODbL이지만, 강한 불균형·보호자 추정 라벨·불명확한 아기 식별 때문에 의미 분류기의 주 학습·검증 자료로 부족하다.[D1–D5]

## 2. 제공된 다섯 GitHub 자료의 실제 역할

| 자료 | 원본에서 확인한 내용 | 앱에서 가능한 역할 | 이 자료만으로 할 수 없는 주장 |
|---|---|---|---|
| **YAMNet** | AudioSet의 521개 사건, MobileNet v1, 3.7M weights, 1024차원 임베딩. 16kHz mono, log-mel 64 bins, 0.96초 패치/50% 겹침. 첫 출력을 얻는 실제 파형은 최소 0.975초. 공식 MediaPipe 모델 입력은 `1 x 15600`.[T1,T2] | 온디바이스 소리 구분·구간 후보·전이학습 기준. 짧은 신호와 잡음의 상태를 분리하는 입력 분석. | 521개가 아기 의도 521개라는 표현. 점수 0.8을 “이 아기가 배고플 확률 80%”로 표시. 일반 AudioSet 전체 지표를 아기 정확도로 표시. |
| **MediaPipe Face Landmarker** | 478개 3D landmark, 52개 blendshape와 변환 행렬. 모델 카드는 주 용도를 AR entertainment로 적고, life-critical decisions 용도가 아니라고 명시. 얼굴 위치·크기·방향·극단적 조명에 민감.[T3,T4] | 얼굴이 안정적으로 잡혔는지, 입 열림 등 형상 변화가 있었는지 보조 관찰. 안정적인 추적·얼굴 크기·시야 확보 여부를 우선. | `mouthSmile`만으로 즐거움, `jawOpen`만으로 배고픔·울음, 눈 감김만으로 졸림 확정. 6~18개월 감정 정확도가 공식 검증됐다는 주장. 읽은 모델 카드에 영아 연령별 성능표는 없다. |
| **LiteRT** | TensorFlow Lite 후속 추론 런타임. TensorFlow/PyTorch 변환과 양자화·Android 실행 경로를 공식 제공. 저장소 Apache-2.0.[T5] | 자체 분류기가 검증된 뒤 변환·양자화·CPU/GPU 등의 기기 성능 측정. | 런타임을 추가하면 분류 지능이나 의미 해석 정확도가 생긴다는 주장. PyTorch 체크포인트가 변환·연산자 검증 없이 즉시 동작한다는 가정. |
| **librosa** | Python audio/music signal processing 라이브러리, ISC.[T6] | 학습·평가 단계의 재표본화, mel/MFCC·스펙트럼 특징, 전처리 검증. Android 구현과 수치 일치 시험. | 미리 학습된 아기 의미 모델로 소개. 특정 MFCC·피치 수치를 즉시 배고픔으로 대응. |
| **voc2vec** | ICASSP 2025 비언어 발성 표현 연구. Voc125는 10개 자료, 125.19h/68,067 samples. 아기 관련 FreeSound 23.42h/1,450 clips, TUT 13.17h/1,540 clips 포함. 코드·모델 Apache-2.0이라고 저자가 명시.[T7] | 오프라인 비교 연구, 비언어 발성 임베딩, 작은 분류기에 대한 교사 모델 후보. | 영아 전용 의미 번역 모델로 소개. 125h를 전부 6~18개월 영아 데이터로 소개. 공개 encoder에 임의의 hunger/sleep/play 이름을 붙여 배포. |

YAMNet의 정확한 소리 라벨은 원본 `yamnet_class_map.csv`에서 다음과 같이 확인했다.[T1]

| index | AudioSet MID | 정확한 이름 |
|---:|---|---|
| 1 | `/m/0ytgt` | `Child speech, kid speaking` |
| 4 | `/m/0261r1` | `Babbling` |
| 14 | `/t/dd00001` | `Baby laughter` |
| 20 | `/t/dd00002` | `Baby cry, infant cry` |
| 21 | `/m/07qz6j3` | `Whimper` |
| 65 | `/m/07qfr4h` | `Hubbub, speech noise, speech babble` |

**조사 중 정정:** 첫 부분 검색에서 `Babbling` 검색어를 누락한 뒤 “옹알이 클래스가 없다”고 전달한 것은 잘못이었다. 원본 CSV를 재조회해 index 4를 확인했고 이 문서에는 정정된 결과만 적용한다. 앱에 들어 있는 파일의 metadata 순서·라벨은 별도로 검증해야 하며, 위 번호를 확인 없이 다른 모델에 하드코딩하면 안 된다.

YAMNet README의 mAP 0.306은 **AudioSet eval 20,366 segments, 521 classes 전체**의 지표다. 앱의 울음 검출·옹알이 분류·의도 정확도가 아니다. 출력층은 클래스마다 logistic score를 내므로 서로 배타적인 의도 분포로 다루면 안 된다.[T1]

### voc2vec의 공개 파일과 평가 코드를 읽고 확인한 제한

공식 `voc2vec-hubert-ls-pt` model config의 architecture는 **`HubertModel`**, hidden size 768, transformer 12 layers이다. 파일 목록의 `pytorch_model.bin`은 **378 MB**다. 이는 공개 파일 크기이며 Android RAM·지연시간 측정값이 아니다. 확인한 config에는 hunger/sleep/play 분류 라벨이 없다.[T8]

저장소 README 예시의 `AutoModelForAudioClassification`만 실행한다고 학습된 아기 원인 head가 생기지 않는다. 배포된 encoder와 해당 과제로 fine-tuning한 classifier를 구분하고, head weights·라벨 mapping·전처리·학습 자료·평가를 함께 확보해야 한다. 파일 suffix도 README 예시와 공개 모델 링크가 다르므로 실제 ID/config를 기준으로 고정해야 한다.[T7,T8]

원 논문 Table IV의 **Donate-a-Cry 5분류, 457 clips, 10-fold CV**에서 `voc2vec-ls`는 다음을 보고한다.[R1]

| 지표 | 논문 수치 | 읽는 방법 |
|---|---:|---|
| Accuracy | 0.793 ± 0.095 | 다수 라벨이 지배할 수 있는 전체 적중률 |
| UAR | 0.378 ± 0.173 | 각 클래스 재현율의 평균 |
| Macro-F1 | 0.311 ± 0.092 | 각 클래스 F1의 평균 |

이 세 지표를 함께 봐야 한다. 아래 직접 집계한 동일한 이름의 공개 정리본에서는 전부 hungry라고 답할 때 accuracy가 382/457=**83.59%**다. 전처리·fold 구성이 완전히 동일하다고 재현한 것은 아니므로 모델이 무조건 baseline보다 나쁘다고 단정하지 않는다. 그러나 79.3%만으로 “울음 이유를 잘 맞힌다”고 홍보할 수 없음은 명확하다. README의 6개 벤치마크 평균과 2025-04-14 추가 HuBERT 모델 결과를 이 특정 5분류 성능으로 바꿔 인용해서도 안 된다.[R1,T7,D1]

공개 학습 소스 `src/ssl_model_finetuning.py`는 dataframe row 인덱스를 `random.shuffle`하고 10개 fold로 나눈다. **아기/설치/가정/녹음 세션 group으로 나누지 않는다.** 같은 `val_dataset`을 early stopping 평가와 최종 `trainer.predict(val_dataset)`에 사용한다. 따라서 공개 파이프라인 결과를 아기 독립·외부 검증으로 설명하면 안 된다. `dataset.py`는 파일 로딩 실패 시 다른 임의 sample로 대체하는 경로도 있어, 재현 연구에서는 실패 샘플을 명시적으로 세고 실행을 중단하도록 바꿀 필요가 있다.[T9]

## 3. 원 연구가 실제로 답한 질문

| 원 연구·연도 | 표본·연령·정답 | 분할·검증과 결과 | 아가톡에 적용할 범위 |
|---|---|---|---|
| **Lockhart et al., “Infant cries convey both stable and dynamic information about age and identity”**, Communications Psychology, 2023-10-02 | 24 babies, 15일/1.5/2.5/3.5개월의 가정 기록. 최종 39,201개의 짧은 울음, 평균 약 0.86초. hunger/isolation/discomfort. 주 라벨은 울음을 멈춘 보호자 행동이며 보호자 원인 추정과 75% 일치. pain·unknown 등 제외. | Random Forest, session이 겹치지 않는 2/3 train·1/3 test를 100회. 원인 3분류 median accuracy **36%, 95% coverage interval 33~38%**; 3분류 chance 약 33.3%. 아기별 fold의 외부 가정 평가라고 재해석하지 않는다. 원음/코드는 EnesBabyCries1 OSF에 공개.[R2] | 신생아의 흔한 울음 원인을 음향 하나로 확정하는 데 직접적인 반례. 생리학적 참값은 아니며 6~18개월·60초 맥락 분석의 실패를 입증한 실험도 아니다. 이 한계를 그대로 적는다. |
| **Yao et al., “Infant crying detection in real-world environments”**, ICASSP 2022 | 울음+칭얼거림 episode vs 나머지. 훈련된 annotator, kappa 0.8469. real-world 필터링 자료와 독립 **17 infants × 24h RW-24h** 시험. 실험실 비교 CRIED는 20 infants/140 recordings/5,587 vocalizations. 본문/표 확인 범위에서 전체 연령별 분포는 확보하지 못함. | RW-Filt에서 leave-one-participant-out. 독립 RW-24h에서 DSF+AF **F1 .613, precision .672, recall .552**. 같은 구조를 실험실 자료로 학습하면 in-lab LOPO F1 .656 → RW-24h .236. 본문은 원시 real-world 742h, 초록은 annotated aggregate 780h라고 적으므로 이를 동일 분모의 780h 울음으로 부르지 않는다.[R3] | ‘울음 검출’도 실제 가정의 겹치는 말·옷 마찰·TV에서 어렵다. 정돈된 수초 클립의 높은 accuracy를 실제 사용으로 옮기지 않는다. 이 연구는 배고픔/졸림 분류가 아니다. |
| **Koudounas et al., “voc2vec: A Foundation Model for Non-Verbal Vocalization”**, ICASSP 2025, arXiv 2025-02-22 | 혼합 비언어 발성 사전학습. Donate-a-Cry 457 samples, ASVP-ESD babies 표 기준 1,339 samples/7 classes. 아기 수·6~18개월 대표성은 해당 표에서 확인되지 않음. | 위 10-fold row 분할·한 val fold 재사용. Donate-a-Cry macro-F1 .311. 논문 본문 ASVP babies 1,204와 Table II 1,339가 달라 실제 재현 manifest가 필요.[R1,T9] | 유용한 표현 후보이며 검증된 의도 엔진이 아니다. 큰 모델과 최신 이름만으로 앱의 반복·오분류 문제를 해결했다고 볼 수 없다. |
| **Hong et al., “InfantCryNet: A Data-driven Framework for Intelligent Analysis of Infant Cries”**, ACML proceedings/PMLR 260, 2025-01-14 | 검출 6,600 clips(6,000 train/600 validation), 15초/16kHz. 원인 분류 835 clips(750/85), awake/hug/sleepy/uncomfortable/diaper/hungry. 읽은 원문에 아기 수·연령·라벨러·아기 독립 분할·상용 데이터 허가를 충분히 설명하지 않음. | CNN14 원인 분류 Table 5 accuracy **73.63%**, 표 Model Size 81M; quantization 75M. 검출 99.8%는 별도 600 validation clips 과제. 외부 corpus·모바일 기기별 실제 속도 검증은 읽은 원문에서 확인 못함. 공개 아기 원인 체크포인트·사용권을 연결하는 공식 배포 링크도 확보하지 못함.[R4] | PANNs 전이학습·pooling·distillation은 비교 실험 아이디어. 검출 99.8%를 여섯 원인이나 이 앱의 정확도로 쓰지 않는다. 81M 표 단위가 앱 파일 bytes라고 추정하지 않는다. |
| **“Social and endogenous infant vocalizations”**, PLOS ONE, 2020 | 6 parent-infant dyads, nominal 3/6/10개월, 36 sessions, 평균19분. 영상·음향을 사람이 보고 social/endogenous 발성으로 코딩. | 부모가 상호작용하려는 때에도 많은 protophone이 상대에게 향하지 않고 스스로 발성을 탐색하는 형태. 작은 표본·실험실 행동 연구; 자동 classifier의 검증 아님.[R5] | 옹알이 검출을 곧바로 “엄마를 불러요/같이 놀아줘요”라는 의도로 바꾸지 않는다. 관찰형 문장·함께 반응해 보는 제안을 허용하되 속마음 정답으로 표시하지 않는다. |
| **“Babbling development as seen in canonical babbling ratios: A naturalistic evaluation of all-day recordings”**, Infant Behavior and Development (PMC 원문) | 21 infants(English 13, Chinese 8), **6·11개월** 42 daylong recordings. 총1,260개 5분 구간 human-coded, 상황 분석에는823구간. | canonical babbling ratio를 연령·언어·상호작용 상황으로 분석한 발달 연구. 학습/시험 classifier·욕구 라벨·한국어 앱 외부 검증이 아니다.[R6] | 대상 연령과 겹치는 연구도 ‘옹알이의 의미를 번역’한 것은 아니다. 월령을 UI에서 묻지 않아도 독립 연구 평가는 연령별로 점검해야 한다. |

### 2026년 자료의 추가 점검

**Wu Hangyu, “How Far Do Foundation Models Transfer to Infant Signals? A Cross-Dataset Transfer Audit with a Unified Need Ontology”**, arXiv:2608.08989, 2026-08-10은 네 공개 corpus의 중복·라벨 충돌·cross-corpus transfer를 검사한 **심사 중 preprint**다. 초록과 읽힌 PDF에 일부 전체 지표 값의 불일치가 있어, 이 문서는 개선폭을 확정 수치로 채택하지 않는다. 저자는 349개 content-identical group의 metadata label 충돌을 보고하지만, 이를 349명의 보호자 또는 독립 재라벨링 실험으로 해석하지 말라고 원문에서 제한한다.[R7]

특히 원문에는 별도 검증 없이 작성한 ontology mapping, infant identity를 확보하지 못하는 자료의 그룹 한계, hash가 다른 동일 waveform·동일 장기 녹음에서 잘린 클립의 train/test 잔존을 적는다. 따라서 “중복 제거를 했으니 아기 독립 평가”라는 표현 역시 틀릴 수 있다. 이 연구는 중복·라벨 관행을 점검할 근거로 사용하며, 공개된 5개 need label이 생리학적 정답이라는 근거로 사용하지 않는다. 코드·manifest 공개 주장은 있지만 이 조사에서 저자 배포 파일을 실행·재현하지 않았다.

## 4. 데이터셋의 사용 가능성·권리·표본 문제

### Donate-a-Cry: 원본에서 직접 확인한 규모

원 저장소 commit **`6baf5aca0460964ad072653585837f968a87c2af`**(2020-09-01)을 `gh api .../git/trees/master?recursive=1`로 읽고 `donateacry_corpus_cleaned_and_updated_data/**/*.wav` 경로를 집계했다. 음성을 다운로드하지 않은 **파일 메타데이터 집계**다.[D1]

| 정리본 폴더 | wav 파일 수 | 파일명 앞36자로 식별한 서로 다른 설치 UUID |
|---|---:|---:|
| hungry | 382 | 205 |
| discomfort | 27 | 16 |
| tired | 24 | 16 |
| belly_pain | 16 | 7 |
| burping | 8 | 7 |
| 전체 | **457** | **221** (클래스 간 중복 UUID 제거) |

연령 tag는 0~4주149개, 4~8주55개, 2~6개월162개, **7개월~2세81개**, 2세 초과10개였다. 즉 6~18개월만의 대표 자료가 아니다. 7개월~2세 tag도 19~24개월을 분리하지 못한다. 다섯 클래스에 걸친 고유 Git blob은457개지만, 이는 재인코딩·부분 중복 음성이 없다는 검사가 아니다.

README의 핵심 원문:

> “original, unmodified, unchecked form”
>
> “The samples were tagged by the contributors themselves.”
>
> “These ids … identify an installed instance, not a device or a user”

따라서 **221 infants**라고 쓰면 안 된다. 설치 UUID로 나누는 것은 clip random split보다 낫지만, 재설치·공유기기·한 가정 여러 아기를 해결하지 못한다. belly_pain의 일부 파일은 같은 UUID·가까운 timestamp의 반복이라 세션 분리도 필요하다. 보호자가 “배고픈 것 같다”고 붙인 이유는 독립 생리 정답이 아니다.

라이선스는 database **ODbL 1.0**, individual contents **Database Contents License 1.0**이라고 명시한다. ODbL §3.1은 commercial use를 명시적으로 허용하지만, 공개 재배포·derivative database·Produced Work의 고지와 share-alike 조건은 별도로 적용된다. “인터넷에 있으니 무조건 상용 자유”나 “ODbL이라 상업 금지” 모두 틀리다. 실제 모델·manifest 배포 형태에 적용할 조건과 원음의 동의 범위를 확인해야 한다.[D1,D2] 저장소의 연락 요청을 읽었으나 외부 연락은 하지 않았다.

### 나머지 후보

| 자료 | 확보된 내용·접근 상태 | 사용권·용도 판단 |
|---|---|---|
| **ICSD** — Liu et al., arXiv:2408.10561 / 공식 GitHub·HF | 울음/코골이 **event detection**. official card의 real-strong 울음 train338/val43/test43; synthetic 울음4,000/500/500; weak 울음1,699/189, weak test 없음. 아기 수·연령·가정 독립성이 확보된 코호트가 아니다. `synth_only.ckpt`, `hybrid.ckpt`, `BEATS.ckpt`를 공식 README가 안내하나 HF 승인 gate가 있다. 실제 checkpoint 다운로드·실행하지 않음.[D3,D4] | 공식 HF 약관: **“ONLY for non-commercial research and educational purposes.”** 배포 금지, 파생물도 허용된 목적만·배포 금지. 상용 앱 학습/검증 자료로 자동 채택 금지. 원 오디오 저작권도 원 소유자에게 남는다고 명시. 논문 제목의 open-source를 상용 허가로 바꾸지 않는다. |
| **CryCeleb2023** — Ubenwa | 786 infants, 약26k expirations/6.5h, 병원 birth/discharge 녹음. infant split train586/dev40/test160. speaker verification 자료이고 hunger/sleep/play 정답이 없다. 출생·퇴원 신생아 자료로 6~18개월과 다르다.[D5] | **CC BY-NC-ND 4.0**와 접근 gate. 현재 상용 제품 학습·파생 가중치 후보에서 제외. 아기별 분할의 좋은 예시이며 의도 모델로 소개하지 않는다. |
| **EnesBabyCries1** — R2의 OSF | 세션·아기 metadata, 울음을 멈춘 행동·보호자 원인 추정·연령. 24명의 생후4개월 미만 아기. 원문에 부모 공개 동의를 설명.[R2] | 논문은 CC BY4.0이나 **논문 라이선스만으로 OSF의 모든 원음·파생 모델 권리를 확정하지 않는다.** 실제 연구 자료의 라이선스와 동의를 확인한 뒤 접근. 의미 분류의 긍정적 근거가 아니라 한계 분석 자료. |
| **ACLEW/HomeBank** | ACLEW는 여러 corpus의 ELAN annotation 모음. 각 corpus 음원 접근은 secure/password 등으로 나뉨. social addressee·vocalization 코딩 연구에 유용.[D6] | 원음·eaf 공개 재배포는 참가자별 명시 허용 없으면 금지. corpus별 접근·사용 제한 유지. annotation이 공개된다고 상업용 아기 음성 학습 corpus로 간주하지 않는다. |
| **AudioSet·FreeSound 기반 자료** | YAMNet/PANNs/voc2vec의 출발 자료. 일반 소리 사건 또는 다양하게 수집된 비언어 발성.[T1,T7,T10] | 모델 코드 라이선스, 공개 checkpoint 조건, 원본 오디오 권리는 각각 확인한다. AudioSet의 사건 라벨 공개가 YouTube 원음 재배포 허가가 되는 것은 아니다. FreeSound도 파일별 조건을 manifest에 보존한다. |

## 5. 엔진 선택표

이 표의 우선순위와 합격 조건은 **조사자의 제안**이다. 실행·학습·비교 결과가 아니다.

| 후보 | 아기 과제로 준비된 부분 | 온디바이스/권리 상황 | 결정 |
|---|---|---|---|
| **현재 YAMNet + 구간 분석 + 보류 판단** | Babbling/Baby cry/Baby laughter 등 실제 labels. 영아 의도 head 없음. | 공식 `.tflite`·MediaPipe 지원. research 소스 Apache-2.0. 앱의 기존 모델 자산 고지·해시는 별도 보존. | **지금 적용할 기준선.** PCM 품질·시계열·정확한 라벨 대응을 고치고 가정 평가 수치를 만든다. |
| **YAMNet embeddings + 작은 linear/MLP classifier** | 기존1024d feature에 영아 관찰 라벨을 실제로 학습해야 함. | 변환 가능한 작은 head. full encoder 포함 APK/latency는 실제 측정. 상용 권리가 확보된 새 학습 자료 필요. | **첫 학습 후보.** 작은 모델로 가능한 개선을 먼저 측정. |
| **PANNs Cnn14/Cnn10·작은 CNN** | AudioSet527 일반 사건. 공식 Cnn14·16k·event-detection checkpoints. 아기 의미 전용 아님. | 코드 MIT. checkpoint는 공식 Zenodo 배포를 고정하고 해당 배포 조건 확인. PyTorch 코드가 기본이며 Android 변환·수치 parity·실기기 측정 필요.[T10] | **두 번째 기준선/교사 후보.** CNN14 전체를 무조건 탑재하지 않는다. |
| **SSAST-Tiny / Small / Base** | 일반 audio SSL. 아기 과제 head를 새로 학습해야 함. | 공식 pretrained Tiny6M, Small23M, Base89M. repo BSD-3-Clause. 다운로드 링크 존재를 확인했고 실다운로드·변환은 미실행.[T11] | **Tiny를 연구 비교.** Base의 이름·일반 benchmark만으로 채택하지 않는다. |
| **voc2vec-HuBERT** | 아기 음성 일부 포함 비언어 표현. 공개378MB encoder; 학습된 need head 확인 못함. | 코드·모델 Apache-2.0; 데이터 출처별 조건은 별도. Android conversion·memory 미검증. | **오프라인 교사/표현 후보.** infant-disjoint 검증에서 작은 모델보다 좋아야 distillation 검토. |
| **InfantCryNet** | 논문에 6 need labels 학습 실험 존재. | 사용 가능한 task checkpoint·dataset commercial rights·아기 독립 검증 확보 못함. | **아이디어 참고. 바로 탑재 불가.** |
| **ICSD pretrained / CryCeleb 적응학습** | cry event 또는 identity 과제. 필요 라벨 없음. | 공식 비상업용 조건. | **현 상용 경로에서 제외.** 별도 허가를 얻은 새 계획 전에는 학습하지 않는다. |
| **일반 음성 LLM·Whisper로 의미 문장 만들기** | 전사·audio reasoning 능력이 영아의 비언어 의도 정답을 제공하지 않음. | 큰 모델 비용 외에 원음 업로드는 앱 정책과 충돌. 로컬 실행도 의미 정확도 근거가 별도로 필요. | **현재 문제 해결책으로 채택하지 않음.** 그럴듯한 문장만 늘리는 실험은 평가 기준에서 탈락. |

Face Landmarker는 위 audio 후보와 경쟁하는 원인 분류기가 아니다. 정면 얼굴·입 움직임·추적 품질을 더할 수 있으나, 얼굴의 기하 계수와 감정·생리적 요구 사이에 검증되지 않은 규칙을 추가하면 오류가 더 그럴듯해질 수 있다.

## 6. 관찰과 문장의 연결

### 측정할 입력

- **품질:** 실제 샘플 수/길이, RMS·peak, clipping 비율, 무음 비율, dropout. 기기 자동 gain·거리 때문에 raw amplitude를 절대 울음 강도·고통으로 해석하지 않는다. R2도 녹음 거리 비표준화 때문에 loudness를 계산할 수 없었다고 적는다.[R2]
- **사건:** 모델 라벨과 timestamp, 선택한 클래스의 score, 경쟁하는 말·음악·잡음 score, 지속 길이. 내부 진단용 점수와 사용자에게 제시할 관찰 문장은 구분한다.
- **시간 변화:** 검출된 구간의 시작/끝·반복·끝부분의 약해짐 등. `n회`는 detector가 나눈 구간 수이며 발성의 진짜 음절 수라고 부르지 않는다. 녹음 전체의 1개 평균 점수로 짧은 웃음이나 앞부분 울음을 지우지 않는다.
- **맥락:** 사용자가 덧붙인 수유·놀이 등은 보호자 기록이다. 소리 분석에서 그 사건을 알아낸 것처럼 표시하지 않는다.

YAMNet score threshold, 최소 지속시간, smoothing·hysteresis 값은 문헌에서 그대로 복사할 정답이 없다. validation에서 선택하고 test를 고정한다. 당장 규칙을 도입한다면 **heuristic이며 calibration 전**이라는 상태를 엔진 버전 기록에 남긴다. 낮은 점수에서도 다섯 소리 중 하나를 반드시 고르는 규칙은 제거한다.

### 조건별 문구 예시

아래 숫자는 **설계 예시 값**이다. 측정하지 않은 횟수·초·음높이를 템플릿에서 생성하면 안 된다.

| 확보된 관찰 | 첫 줄 예시 | 다음 행동/기록 |
|---|---|---|
| 18초 중 두 구간에서 Babbling 우세, 음질 통과 | “짧은 옹알이 같은 소리가 두 차례 이어졌어요.” | 해당 구간을 눌러 다시 듣기. 한마디 기록은 “이 소리도 내 목소리야!”처럼 관찰에서 벗어나지 않는 선택적 표현. |
| 앞부분 Baby cry 우세, 마지막 구간은 음성 활동 감소 | “앞부분에는 울음 같은 소리가 이어졌고, 끝부분은 잠잠했어요.” | “수유해서 괜찮아졌어요”는 보호자가 기록한 경우에만 보호자 메모로 제시. |
| Baby laughter가 확인된 짧은 구간 | “중간에 웃음 같은 소리가 짧게 들렸어요.” | 웃음 구간 재생. “행복지수98%” 같은 값은 만들지 않는다. |
| 성인 말소리/음악과 겹치거나 근거 약함 | “말소리와 함께 녹음돼 아기 소리를 나누기 어려웠어요.” | 그대로 다시 듣기·원하면 더 가까이서 녹음. 임의의 배고픔·졸림 후보를 세 개 나열하지 않는다. |
| 입력이 거의 무음·깨짐 | “분석할 소리가 충분히 담기지 않았어요.” | 마이크 상태·거리 확인 후 다시 녹음. 녹음 실패를 아기의 조용한 기분으로 해석하지 않는다. |

“수유·기저귀·온도를 확인해 주세요” 같은 일반 조언은 상황에 맞는 도움말에 둘 수 있지만, 매번 소리 분석 결과를 대신하면 사용자의 현재 불만이 계속된다. 한마디의 다양성보다 **다른 입력에서 근거가 달라지고, 같은 입력에서는 근거가 재현되는 것**이 먼저다. 문장 템플릿·local language model 여부는 그 이후의 표현 선택이다.

## 7. 실제 배포로 가는 평가·자료 계획

### 단계 A — 0.5 입력과 관찰의 신뢰성

**완료 기준 제안:** 녹음0/1/60초, 조기 중지, 재생·선택구간, 무음·clipping·성인 음성·TV·음악·겹침에 대해 엔진이 어떤 경로로 출력했는지 재현할 수 있다. 실패·음성 없음·근거 부족 상태에서 아기 의도를 만들지 않는다. 같은 PCM·모델·규칙 버전의 **관찰 결과는 동일**해야 한다. 사용자 문구 후보만 바뀔 수 있다.

기능 회귀와 분류 성능 평가를 구분한다. 합성 사인파·디지털 무음은 입출력·무음 처리 검사에는 좋지만, 영아 발성 분류의 정확도를 입증하지 않는다. 손으로 만든 고정 score 배열 역시 시간 집계 규칙의 단위시험일 뿐이다. 실제 아기/성인/혼합 오디오가 필요하다.

### 단계 B — 데이터 정의와 최소 파일럿

자료 수집은 **별도 동의와 권한이 있는 연구**로 진행한다. 현재 앱의 원음·영상·임베딩을 자동 수집하거나 업로드하지 않는다. 기존 기록 저장 동의를 모델 학습 동의로 바꾸지 않는다. 온디바이스 제품과 별도의 관리된 연구 자료 경로를 먼저 정한다.

파일럿 시작점으로 30가정, 가정당10~20개의 자연스러운 짧은 녹음(300~600 clips)을 제안한다. 이는 **출시 정확도를 증명하는 최소 표본 수가 아니고**, 라벨링 가능성·기기 편차·실패 유형을 찾기 위한 규모다. 실제 필요한 독립 가정·사건 수는 목표 CI와 관찰 빈도로 다시 계산한다.

연구 metadata에는 infant/family/session/device/source/license/consent ID를 두고, **평가용**으로 6~8/9~12/13~18개월 층을 확보한다. 제품 UI에 월령 선택을 되살리지 않는다. 같은 집에서 여러 아기·부모가 등장한 녹음, 같은 원본을 자른 모든 구간, augmentation·재인코딩 결과는 같은 group에 넣는다.

정답 라벨은 우선 `cry/fuss`, `laugh`, `babbling or infant speech-like`, `adult speech`, `music/TV`, `other`, `uncertain`, `overlap`을 구간별로 정의한다. 실제로 사람이 일관되게 나누기 어려운 라벨은 합친다. 두 명이 모델 출력을 보지 않고 일부를 독립 코딩하고, 일치도·불일치 사례·조정 규칙을 공개한다. 보호자의 “졸린 것 같다”는 별도 필드에 보존한다.

### 단계 C — 고정 외부 시험과 모델 비교

1. train/validation/test를 **infant·family·source recording**으로 먼저 나누고 이후 clip을 만든다. 병원·공개 corpus·한국 가정을 섞을 때 source-domain도 따로 평가한다. 모델 선택·threshold 조정에 test를 쓰지 않는다.
2. SHA뿐 아니라 resample된 waveform hash, fingerprint·시간 이동 상관·임베딩 후보 검색으로 재인코딩/잘라내기 중복을 검사한다. 자료의 label provenance를 확인하고 같은 소리가 서로 다른 이름으로 재배포된 경우 독립 샘플로 세지 않는다.
3. 기존 YAMNet·항상 majority·간단 음향 특징 분류기를 baseline으로 포함한다. 모든 후보에 같은 고정 manifest·segment 정의·split을 적용한다. 큰 모델의 training 자료에 test 원본이 섞일 수 있는지도 기록한다.
4. **class별 precision/recall/F1, macro-F1/UAR, confusion matrix**, 낮은 점수에서 보류한 비율, 보류하지 않은 결과의 오류율을 함께 보고한다. 검출은 event/segment 정의와 오차 허용 폭을 명시하고, 시간당 false positives·누락된 울음 길이를 포함한다. 클래스마다 독립 infant 수와 event 수를 함께 적는다.
5. confidence interval은 인접 프레임을 독립 sample로 간주하지 않고 **infant/family 단위 bootstrap** 등으로 계산한다. 새로운 기기·방·집·소음에서 precision/coverage가 유지되는지 평가한다.
6. 유망 모델만 float→quantized parity, 저가/중급 ARM 단말에서 warm/cold latency p50/p95·peak RAM·APK 크기·60초 연속 분석 발열/전력·취소 후 해제를 시험한다. 에뮬레이터 통과를 실기기 성능으로 대신하지 않는다.

첫 관찰 문구 release gate의 예로 “표시된 cry/laugh/babble 관찰의 class별 precision lower 95% CI ≥0.90, 동시에 사전 합의한 최소 coverage 확보”를 제안할 수 있다. **0.90은 문헌이 보증한 수치나 현재 성능이 아니라 제품 목표 예시**다. 자료가 적으면 CI가 넓어 합격 여부를 정할 수 없고, 거의 모든 입력을 보류하는 시스템도 좋은 제품이 아니므로 coverage·사용성 조건을 함께 정해야 한다.

### 단계 D — 욕구 연구는 별도 가설 검증

배고픔/졸림/놀이를 자동으로 다루려면 음성만, 보호자 맥락만, 음성+맥락을 각각 비교해 **음성이 추가로 제공하는 정보**를 검증해야 한다. 수유 직전이라는 metadata만 보고 배고픔을 맞힌 뒤 음성 정확도라고 부르면 안 된다. 보호자가 고른 이유를 그대로 결과로 돌려주는 순환 검증도 제외한다.

일상 돌봄에서 이미 일어난 행동·변화를 동의 아래 기록할 수는 있으나 아기를 일부러 굶기거나 울게 하거나 통증을 유발해 학습 자료를 만들지 않는다. 달래기·수유 후 울음이 줄어도 가능한 설명이 여러 개이므로 원인 참값으로 확정하지 않는다. 이 단계가 아기 독립·다른 가정·다른 시기의 검증을 통과하기 전에는 **관찰형 기능의 정확도와 욕구 추론의 정확도를 합치지 않는다.**

## 8. 원문 출처

아래 링크는 모두 2026-10-01 확인했다. 과거 문헌고찰의 정확도 범위는 이번 앱의 수치로 재인용하지 않았다. API의 latest/main/master는 변할 수 있어 실제 실험 시 checkpoint SHA·source commit·전처리·label map을 함께 고정한다.

- **[T1]** TensorFlow YAMNet [README](https://github.com/tensorflow/models/blob/master/research/audioset/yamnet/README.md), [class map](https://github.com/tensorflow/models/blob/master/research/audioset/yamnet/yamnet_class_map.csv), [LICENSE — research 적용](https://github.com/tensorflow/models/blob/master/LICENSE).
- **[T2]** Google [MediaPipe Audio Classifier](https://ai.google.dev/edge/mediapipe/solutions/audio/audio_classifier). 공식 모델 링크·입력 크기·score/allowlist 계약.
- **[T3]** Google [Face Landmarker](https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker), [MediaPipe LICENSE](https://github.com/google-ai-edge/mediapipe/blob/master/LICENSE).
- **[T4]** Google [Face Mesh V2 Model Card](https://storage.googleapis.com/mediapipe-assets/Model%20Card%20MediaPipe%20Face%20Mesh%20V2.pdf), [Blendshape V2 Model Card](https://storage.googleapis.com/mediapipe-assets/Model%20Card%20Blendshape%20V2.pdf). 일부 PDF 텍스트의 ligature가 NUL로 추출됐으나 intended uses·outputs·evaluation 항목은 확인했다.
- **[T5]** [LiteRT 공식 문서](https://ai.google.dev/edge/litert), [저장소·라이선스](https://github.com/google-ai-edge/LiteRT).
- **[T6]** [librosa 원 저장소](https://github.com/librosa/librosa), [ISC LICENSE](https://github.com/librosa/librosa/blob/main/LICENSE.md).
- **[T7]** [voc2vec 원 저장소](https://github.com/koudounasalkis/voc2vec), 조회한 commit `ef960582d728b71988649320ba144db6bf1ecfe2` (2025-04-14). README와 Apache-2.0 명시.
- **[T8]** 저자 [voc2vec-hubert-ls-pt config](https://huggingface.co/alkiskoudounas/voc2vec-hubert-ls-pt/blob/main/config.json), [파일 목록](https://huggingface.co/alkiskoudounas/voc2vec-hubert-ls-pt/tree/main).
- **[T9]** voc2vec [학습 소스](https://github.com/koudounasalkis/voc2vec/blob/ef960582d728b71988649320ba144db6bf1ecfe2/src/ssl_model_finetuning.py), [dataset.py](https://github.com/koudounasalkis/voc2vec/blob/ef960582d728b71988649320ba144db6bf1ecfe2/src/dataset.py).
- **[T10]** Kong et al. [PANNs 공식 저장소·checkpoint 안내](https://github.com/qiuqiangkong/audioset_tagging_cnn), [MIT LICENSE](https://github.com/qiuqiangkong/audioset_tagging_cnn/blob/master/LICENSE.MIT), [공식 Zenodo 모델 배포](https://zenodo.org/records/3987831). 이 조사에서 Zenodo 개별 파일의 bytes·다운로드·변환은 검증하지 않았다.
- **[T11]** Gong et al. [SSAST 공식 저장소](https://github.com/YuanGongND/ssast), [BSD-3-Clause LICENSE](https://github.com/YuanGongND/ssast/blob/main/LICENSE). AAAI 2022, 일반 오디오 사전학습·공개 모델 규모.
- **[R1]** Koudounas et al. [voc2vec 논문 PDF](https://arxiv.org/pdf/2502.16298), [원 DOI](https://doi.org/10.1109/ICASSP49660.2025.10890672). Table II/IV와 fine-tuning protocol.
- **[R2]** Lockhart et al. [Infant cries convey both stable and dynamic information about age and identity](https://www.nature.com/articles/s44271-023-00022-z), [EnesBabyCries1 OSF](https://osf.io/ru7na/).
- **[R3]** Yao et al. [Infant crying detection in real-world environments](https://pmc.ncbi.nlm.nih.gov/articles/PMC9609294/), [DOI](https://doi.org/10.1109/ICASSP43922.2022.9746096).
- **[R4]** Hong et al. [InfantCryNet 공식 proceedings](https://proceedings.mlr.press/v260/hong25a.html), [publisher가 제공한 PDF](https://raw.githubusercontent.com/mlresearch/v260/main/assets/hong25a/hong25a.pdf). 자료·분할 Table1, 검출 Table2, 분류·압축 Table3/5.
- **[R5]** [Social and endogenous infant vocalizations](https://pmc.ncbi.nlm.nih.gov/articles/PMC7406057/). 6 dyads·36 sessions의 방법·결과.
- **[R6]** [Babbling development as seen in canonical babbling ratios: A naturalistic evaluation of all-day recordings](https://pmc.ncbi.nlm.nih.gov/articles/PMC5869132/). 21 infants·6/11개월·human coding.
- **[R7]** Wu Hangyu [2026 preprint 초록·상태](https://arxiv.org/abs/2608.08989), [PDF](https://arxiv.org/pdf/2608.08989). under review 표기. 수치 버전 불일치로 개선폭 확정 인용에서 제외.
- **[D1]** [Donate-a-Cry 원 저장소](https://github.com/gveres/donateacry-corpus), [고정 commit 정리본](https://github.com/gveres/donateacry-corpus/tree/6baf5aca0460964ad072653585837f968a87c2af/donateacry_corpus_cleaned_and_updated_data). 이유/연령/UUID 계약과 라이선스는 README.
- **[D2]** Open Data Commons [ODbL 1.0 전문](https://opendatacommons.org/licenses/odbl/1-0/), [Database Contents License](https://opendatacommons.org/licenses/dbcl/1-0/).
- **[D3]** [ICSD 공식 GitHub](https://github.com/QingyuLiu0521/ICSD), 조회한 commit `ff44a26e30c9a4de74d816472efacbe722c7dd54` (2025-06-11). [논문](https://arxiv.org/abs/2408.10561). 저장소 README와 논문 버전의 상태·규모 서술이 일부 다르므로 실험 시 version 고정 필요.
- **[D4]** [ICSD 공식 HF gate·약관·통계](https://huggingface.co/datasets/QingyuLiu1/ICSD). **비상업 연구/교육 한정·무단 배포 금지** 원문을 직접 확인. 동의 제출·접근 신청은 하지 않았다.
- **[D5]** Ubenwa [CryCeleb2023 dataset card](https://huggingface.co/datasets/Ubenwa/CryCeleb2023). infant split·birth/discharge·26k/6.5h·CC BY-NC-ND 4.0.
- **[D6]** TalkBank [ACLEW HomeBank 공식 소개·Usage Restrictions](https://talkbank.org/homebank/access/Public/ACLEW.html).

확인 한계: 이 문서는 특정 사용자의 실제 녹음으로 모델을 비교한 결과가 아니다. 접근 제한 데이터에 대한 승인을 받거나 상업 이용 허가를 새로 얻지 않았다. 모델 파일 실행·Android 변환·실기기 latency 측정은 하지 않았다. agent-reach 업데이트 조회 결과는 설치 v1.5.0이 최신이었으며 업데이트는 수행하지 않았다.
