# 배고픔·졸림·놀이 요청 표현의 근거와 배포 조건

2026-10-05 · 원문 논문·공식 안내·공개 소스/파일 metadata 조사 · 모델 실행 없음

**현재 확인한 자료로는 6~18개월의 소리·영상에서 `배고파요`, `졸려요`, `놀아줘요`를 정확하게 구별하는 완성 온디바이스 모델을 채택할 수 없다.** 원인 분류 연구는 존재하지만 라벨의 신뢰도, 아이/원음 분리, 대상 월령, 배포 가중치와 권리 중 하나 이상이 부족했다. 일반 울음·옹알이·웃음 검출 개선만으로 이 세 욕구의 정확도가 좋아졌다고 보고할 수 없다. 세 문구를 언제나 틀리거나 영원히 구현 불가능하다고 단정하는 결론도 아니다.

이 문서는 현재 제품 문구·임계값·모델·APK·배포를 변경하지 않는다. 기존 [영아 발성 전문 모델 조사](20261005-infant-specialist-followup.md)와 [시간축 SED 조사](20261005-temporal-model-followup.md)의 발성 검출 결과를 욕구 추론 근거로 옮기지 않는다.

## 원인을 실제로 구별하는가

### 가정에서 같은 아기를 반복 녹음한 연구

[Lockhart-Bouron et al., Communications Psychology 2023, DOI 10.1038/s44271-023-00022-z 원문](https://pmc.ncbi.nlm.nih.gov/articles/PMC11332224/)은 **24가족/24아기, 생후15일·1.5·2.5·3.5개월**, 집에서 각48시간 녹음을 수집했다. 최종 분석은 **39,201개 울음 조각**이다. 이는39,201명의 독립 아기가 아니다. 원인 라벨은 주로 **울음을 멈추게 한 부모 행동**에서 배고픔·불편·분리를 정했으며, 부모가 미리 추정한 원인과75% 일치했다. 생리적 욕구의 직접 측정은 아니다.

논문의 Methods는 원인별 stratification을 사용하고, 학습2/3·평가1/3을 **다른 녹음 session**에서 뽑아100회 반복했다고 명시한다. 결과는 다음과 같다.

| 검증된 원문 결과 | 해석 |
|---|---|
| 10개 음향 특징 Random Forest의 원인 분류 **36% [33,38]**, 3종 우연 수준33.3% | 세 일반 원인에 안정적인 공통 음향 코드가 있다는 근거가 약함 |
| 부모의 추정 원인 라벨로 바꾸어도 **35% [33,36]** | 종료 행동 라벨만의 문제로 결과를 설명하기 어려움 |
| 아기 ID를 예측 변수로 넣으면 **43% [36,50]** | 원인별 분포의 개인차 영향을 포함하므로 새 아기의 정확도 근거로 사용하지 않음 |
| 한 아기 학습→같은 아기 다른 session **38% [17,65]**, 다른 아기 **34% [30,54]** | 개인별 소리 코드도 안정적으로 회복되지 않았고 다른 아기에 전달되지 않음 |

저자의 요약: **“neither machine learning algorithms nor trained adult listeners can reliably recognize the causes of crying.”** 전체 모델은 아이 완전 분리만으로 구성된 단일 holdout이 아니다. session 분리 및 위의 다른 아기 전이 분석을 정확히 구분한다. 전문의 범위는 초기 영아·깨끗하게 편집한 울음·특정 음향 특징이며, **6~18개월·영상 공동 모델·모든 학습 방법의 불가능성을 증명하지 않는다.** 강한 통증/불편의 차이와 같은 distress 강도 구별은 별개의 문제다.

원문은 공개 [EnesBabyCries1 OSF](https://osf.io/ru7na/) 자료와 부모의 공개 동의를 명시한다. 공개 API의 node license는 null이었다. 이번에 자료·가중치를 받지 않았고 특정 상업 학습/재배포 권리와 Android 모델은 확보하지 않았다. 초기 영아 자료만 추가해 아가톡 대상 월령을 채우는 것도 불충분하다.

### 높은 정확도를 보고한 공개 연구의 분할 문제

[Machine learning-based infant crying interpretation, Frontiers in AI 2024 원문](https://pmc.ncbi.nlm.nih.gov/articles/PMC10882089/)은 Donate-a-cry **457개 원음→1,939개 증강**,5개 원인,80/20 분할·10-fold CV를 사용하고 MFCC+RF의 **96.39%**를 보고한다. 이 숫자는 논문에 있는 결과이며 이번 재실행 결과가 아니다.

연결된 [공식 소스 고정 commit 4f305abe2afdf54530afb73a3a213acfa0d39643](https://github.com/Hammoudmsh/ML_based_infant_Crying_classification/tree/4f305abe2afdf54530afb73a3a213acfa0d39643)에서:

- [README](https://github.com/Hammoudmsh/ML_based_infant_Crying_classification/blob/4f305abe2afdf54530afb73a3a213acfa0d39643/README.md)는 augmentation→features→train 순서를 제시한다.
- [data_aug.py](https://github.com/Hammoudmsh/ML_based_infant_Crying_classification/blob/4f305abe2afdf54530afb73a3a213acfa0d39643/data_aug.py)는 원음과 여러 noisy 파생본을 같은 출력 corpus에 쓴다.
- [train.py](https://github.com/Hammoudmsh/ML_based_infant_Crying_classification/blob/4f305abe2afdf54530afb73a3a213acfa0d39643/train.py)의 `load_np_files`는 파일명을 그룹 ID로 반환하지 않고 특징과 클래스만 수집한다. 이후 **행 index**에 `train_test_split(...test_size=0.2,random_state=42,shuffle=True,stratify=...)`을 사용한다. images 경로도 같은 행 분할이다.

따라서 **이 공개 recipe는 원음과 파생본을 한 그룹으로 묶거나 아기별로 분리하지 않는다. 같은 원음의 파생본이 양쪽으로 들어갈 위험이 실제 코드에 있다.** 논문 실행 당시 모든 학습 파일/분할 manifest가 없으므로96.39% 전체를 누수라고 정량 판정하지 않는다. 그러나 독립 아기 또는 독립 원음의 욕구 정확도를 입증하는 수치로 채택할 수 없다. 공개 master tree에 고정 trained classifier/label/preprocessing release는 없고, main branch의 MIT LICENSE만으로 master recipe·데이터·모델 권리를 일괄 확정하지 않는다.

## Donate-a-cry의 원본 라벨·표본·권리

[원 제공 repo 고정6baf5aca0460964ad072653585837f968a87c2af README](https://github.com/gveres/donateacry-corpus/blob/6baf5aca0460964ad072653585837f968a87c2af/README.md)는 다음을 직접 명시한다.

> “original, unmodified, unchecked form”
>
> “The samples were tagged by the contributors themselves.”
>
> “they identify an installed instance, not a device or a user”

이름의 원인은 **“suspected reason”**이다. 고유36자 ID는 앱 설치 instance여서 아이/가족 신원의 확정 ID가 아니다. 동일 ID의 연속 녹음도 있고 서로 다른 성별/월령 태그가 섞인 ID도 원 tree에서 확인된다. 최소한 같은 ID와 같은 원음의 파생본을 묶어야 하지만, 그렇게 해도 독립 아기 분리가 증명되지는 않는다.

이번 GitHub tree metadata에서 `donateacry_corpus_cleaned_and_updated_data`의 WAV를 직접 집계했다. 미디어 내용은 다운로드·청취하지 않았다.

| 클래스 | 파일 수 |
|---|---:|
| hungry |382|
| tired |24|
| discomfort |27|
| belly_pain |16|
| burping |8|
| 합계 |457|

배고픔만 항상 출력해도 이 원본의 단순 accuracy는 **83.59%**다. 증강으로 숫자를 맞춘 것이 실제 독립 원인/아이 수를 늘린 것은 아니다. WAV의 설치 ID는221개이며 아기221명이라는 뜻이 아니다.

월령 태그별 파일은0–4주149,4–8주55,2–6개월162,**7개월–2세81**,2세 초과10개다. 7개월–2세81개를6~18개월81개로 보고할 수 없다. 실제6~18개월 정확 월령별 아이수는 확인할 수 없다. 원 README에 lonely 등9개 원인 태그가 있으나 위 clean5종에는 **play/attention/놀아줘요 라벨이 없다.**

README는 database **ODbL1.0**, 개별 contents **DbCL1.0**을 명시한다. 이는 임의 MIT corpus가 아니다. 학습자료·파생 database·재배포의 의무와 원 기여자 권리는 상업 사용 전 별도 확인해야 한다. 이번엔 학습·저자 연락·원음 수집을 하지 않았다.

## Dunstan과 공개 소형 classifier

| 후보 | 실제 확인 | 결정 |
|---|---|---|
| [Dunstan 공식 사이트](https://www.dunstanbaby.com/), [FAQ](https://www.dunstanbaby.com/faq/) | 스스로 **newborn0–5개월**과 reflex **pre-cry**5음을 설명. 강한 울음 전체·6~18개월 옹알이·놀이 요청의 같은 문제라고 볼 수 없음 | 앱 대상 월령에 직접 적용 보류 |
| [Dunstan 공식 research 페이지](https://www.dunstanbaby.com/the-latest-research-into-the-baby-language/) |89%·94.7%논문 주장 링크가 있으나 첫 PDF host는 DNS 실패로 전문 미확보. 링크된 [PMC5593797 원문](https://pmc.ncbi.nlm.nih.gov/articles/PMC5593797/)은 **호기/흡기 울음 segmentation**이며 hunger/sleep/play 분류 연구가 아님 | 판매 페이지 숫자를 검증된 욕구 정확도로 인용하지 않음. 2023논문의 Dunstan corpus 비판은 해당 저자들의 문헌 분석임을 구분 |
| [foduucom/baby-cry-classification 고정 card705bbf8439606e49343f837b7d35cfe3f7ee99d6](https://huggingface.co/foduucom/baby-cry-classification/blob/705bbf8439606e49343f837b7d35cfe3f7ee99d6/README.md) | card 자체의 accuracy **0.384615**, macro F1**0.38**. class0–4의 label mapping과 support 없음. corpus·월령·아이/원음 split·외부 test 미기재 | 작은 다운로드를 즉시 제품 후보로 삼지 않음 |

[foduucom 고정 HF API](https://huggingface.co/api/models/foduucom/baby-cry-classification/revision/705bbf8439606e49343f837b7d35cfe3f7ee99d6?blobs=true)의 공개 파일은 `model.joblib` **113,695bytes**, `label.joblib` **527bytes**다. LFS SHA256는 각각`7c640cd82970f11fdd3db4147251b8fe7e28112968c1eca06532e1fca3c0aab2`, `a82740f73e162c78aa43903814af40bae174b3c9a84c1b823f282089e0ff9791`. metadata이고 다운로드 후 계산한 해시가 아니다. 본문은 MIT/LICENSE.md를 주장하지만 tree에 해당 LICENSE.md가 없고 metadata는 **Apache2.0**이다. 권리가 일관되게 확인되지 않는다.

card의 Librosa 특징 함수에는 미정의 `n_fft/hop_length/win_length/window/n_mels/n_bands/fmin`이 있어 고정 추론 계약이 완성되지 않는다. `joblib`은 Android 실행 artifact가 아니며, 이번에 역직렬화·ONNX변환·ARM 실행을 하지 않았다. 정확한 estimator/label/전처리를 검증하고 export하는 비용보다 **독립 욕구 성능과 권리 부족**이 먼저 해결돼야 한다.

## 영상·행동에서 추가할 수 있는 정보

공식 행동 안내는 욕구 추론의 **관련 단서**를 제공하지만 자동 classifier 성능표는 아니다.

| 문구 | 대상 월령과 관련된 원문 | 현재 설계에 필요한 구분 |
|---|---|---|
| 배고파요 | [CDC 공식6–23개월 hunger/fullness](https://www.cdc.gov/infant-toddler-nutrition/mealtime/signs-your-child-is-hungry-or-full.html): **“Reach for or point to food”**, 음식/숟가락을 제시하면 입 벌리기, 음식 보고 들뜨기. 울음은 **“often a late sign of hunger”** | 음식·제시 행동·손·입과 반응의 맥락이 필요. 얼굴 한 장의 슬픔/입 모양이나 높은 울음 점수만으로 배고픔을 고르지 않음 |
| 졸려요 | [Stanford Medicine Children's Health, Infant Sleep](https://www.stanfordchildrens.org/en/topic/default?id=infant-sleep-90-P02237): sleep readiness에 **“Rubbing eyes”, “Yawning”, “Looking away”, “Fussing”** 제시 | 실제 시간축에서 하품·눈 비비기를 검출한 뒤에도 개별 단서의 비특이성과 여러 원인/가림/프레임 밖을 평가해야 함. 현재 표정점수를 이 행동검출 정확도로 보고하지 않음 |
| 놀아줘요 | [CDC9개월 공식 milestones](https://www.cdc.gov/act-early/milestones/9-months.html): 까꿍하면 미소/웃음, 보호자 떠날 때 보기/손 뻗기/울음, 안아달라는 팔 들기 | 웃음/옹알이·놀이에 대한 반응과 **놀이를 요구하는 의도**는 다른 라벨. 분리불안·안아달라기·단순 탐색과 놀이 요청을 구별할 실제 맥락 자료 필요 |

CDC9개월 페이지는 Jina가 HTTP404 경고를 포함하면서 본문을 반환했다. 본문 항목은 확인했으나 직접 HTTP 정상 제공을 추가 검증한 것은 아니다. CDN/이전 경로를 제품에서 의존하지 않는다. [AAP 공식 Responding to Your Baby's Cries](https://www.healthychildren.org/English/ages-stages/baby/crying-colic/Pages/Responding-to-Your-Babys-Cries.aspx)도 **“Sometimes different types of cries overlap”**, 아기마다 목소리를 다르게 사용한다고 설명한다. 이 안내를 특정 AI의 정확도 보증으로 쓰지 않는다.

이번 조사에서 **6~18개월 같은 아이의 영상+소리+행동 맥락을 갖춘, 독립 아이 holdout의 hunger/sleep/play3종 공개 모델**을 확보하지 못했다. 실제 시각 행동 추론 가능성까지 부정하는 전수조사는 아니다.

## 세 문구를 정확도 기능으로 배포하려면

다음은 원문의 자동 요구가 아니라 이번 조사에 따른 구현·평가 제안이다.

1. **정답을 먼저 정의한다.** 음식 제시/섭취·수면 직전과 실제 잠듦·상호 놀이/손짓에 대한 반응을 함께 기록하되 결과 행동을 욕구의 절대 정답으로 삼지 않는다. 보호자 추정, 관찰 가능한 행동, 후속 반응을 별도 항목으로 두고 복수 라벨·모름을 허용한다. 실제 학습 이용 동의와 로컬 운영 계약을 먼저 확보한다.
2. **6~18개월 아이/가족을 분리한다.** 같은 원음·영상·촬영 session·잘라낸 창·증강본·embedding은 전부 같은 split이다. 아이 완전 분리 test와 새 session의 개인화 test를 별도로 평가한다. 훈련 때 평가 가족이 있었는지도 pretrained/teacher provenance에서 확인한다.
3. 세 욕구의 macroF1/balanced accuracy/클래스별 precision·recall·혼동행렬, **보류율과 보류 후 남은 표본의 정확도**, calibration을 함께 보고한다. 정확도 기준은 현재 증거로 임의 정하지 않는다. 월령구간·아기별 분포와 신뢰구간을 함께 보고 독립 아이 수를 창 수로 대체하지 않는다.
4. hungry/tired/play 외에 평온·통증/불편·분리·혼합·TV/성인·음식 없는 손입·졸리지 않은 하품 등을 대조한다. `babble→play`, `cry→hunger`, `eyes closed→sleep` 같은 대응이 우연한 배경/촬영 관행을 학습하지 않았는지 반증한다. 소리만/영상만/맥락추가의 ablation으로 실제 이득을 확인한다.
5. 권리가 일관된 exact weights·label order·전처리·시간범위·export parity를 고정한다. S25와 중급 ARM에서10/20초 cold/warm 지연·RAM·취소·발열을 측정한다. PC 성공이나100KB joblib 존재는 Android 완성으로 보고하지 않는다.

지금의 연구 결정은 **검증되지 않은3종 원인 head를 바로 추가하는 것이 아니라, 행동·맥락 라벨과 독립 아이 평가가 확보되는지 확인하는 것**이다. 일반 발성 detector는 관찰 근거로 계속 개선할 수 있다. 세 문구를 화면에서 사용한다면 현재 모델이 이를 정확히 판별했다는 표시와 분리해야 하며, 확률·정확도 주장을 붙일 근거는 아직 없다.

## 조사 범위·경로

- agent-reach 검색 skill 적용. Exa free MCP quota 종료 후 유료 key/호출을 추가하지 않았다. GitHub CLI/API·Jina 원문·Crossref/EuropePMC discovery로 공식 원천을 확인했다. 검색 snippet은 논문 결과로 쓰지 않았다.
- 공개 파일 tree/모델 metadata와 웹 원문만 조회했다. 사용자 또는 실제 아기 미디어·파생 특징·점수를 외부에 보내지 않았다. 원음·모델 다운로드/학습/실행·계정등록·저자 메시지·Elice 호출 없음.
- Agent Reach check-update:1.5.0 최신. 조사 기간 내 새로운 배포 가능한 모델이 없다는 결과이며 전체 공개/비공개 모델에 대한 부존재 증명은 아니다.
