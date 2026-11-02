# 영아 비울음·시각 행동의 시간축 후보 추가 확인

2026-10-06 · 공개 원문·고정 소스·공식 모델 metadata 조사 · 새 모델 실행 없음

**이번에 확인한 후보에도 6~18개월의 배고픔·졸림·놀이 요청을 그대로 출력하는 배포 가능한 완성 모델은 없었다.** 다만 새로 확인한 실용적 경로는 있다. VocalBaby에는 실제 5.43MB 영아 발성 분류 파일이 있고, MediaPipe는 얼굴과 손을 Android에서 시간에 따라 관찰할 수 있으며, MoViNet-A0에는 실제 TFLite 행동 모델이 공개되어 있다. 이들은 서로 다른 문제를 해결한다. 발성 종류, 보이는 행동, 보호자에게 요청하는 맥락, 내부 욕구를 같은 정확도 지표로 합치지 않는다.

제품 기준은 [HANDOFF](../HANDOFF.md)의 0.5.15-beta/code29와 YAMNet+AST다. [이전 specialist 조사](20261005-infant-specialist-followup.md), [욕구 표현 근거](20261005-infant-needs-expression-evidence.md), [시간축 SED](20261005-temporal-model-followup.md)를 먼저 읽었다. **EfficientSED 20.3MB의 합성 ONNX export 성공은 기존 확인 사실**이며 Android mel·실폰·새 누락 회복은 별도 미검증이다. 이번 문서는 기존 LittleBeats/BabyHuBERT/HindiBabyNet와 EfficientSED를 새 발견으로 다시 세지 않는다.

## 이번에 새로 확인한 다섯 경로

| 경로 | 실제 공개 배포물 / 출력 | 영아·시간축 근거 | 권리와 Android 판단 |
|---|---|---|---|
| **voc2vec** | 저자 공식 4종 weight 존재. config상 Wav2Vec2ForPreTraining 또는 HubertModel이며 task label/head 없음. 377.6~380.3MB | Voc125의 아기 소리를 포함한 비언어 발성 사전학습. 영아 6~18개월 전용 detector 검증 없음 | 코드와 모델 Apache2.0을 저자가 명시. 공개 task head가 없고 큰 encoder이므로 즉시 보완 detector로 보류 |
| **VocalBaby** | 공개 `xgb_egemaps_smote_optuna.pkl` 5,432,713bytes, 전처리와 label encoder도 존재. Canonical/Non-canonical/Crying/Laughing/Junk | 코드에 child-ID 분리 절차. 400ms 비중첩창을 처리하나 기본 반환은 평균을 낸 **파일당 단일 라벨** | repo MIT. 실제 eGeMAPS는 openSMILE 상업 제품 금지 조건에 걸림. 공개 train manifest·월령·저장 pkl 대응·Android 추론 미검증. 서버 구조를 앱에 도입하지 않음 |
| **영아 행동 specialist: BabyNet / InfAct / NNS** | BabyNet 공개 실행 weight 미확보. InfAct는 Drive 모델 링크, NNS는 저자 pretrained zip 링크 존재 | 손과 물체의 시간 관계, 자세 전환, 입의 반복 운동을 실제 연구. 각기 reach / 자세 / 비영양성 빨기이며 욕구가 아님 | InfAct·NNS는 명시적 NC. BabyNet exact source/weights/상업 허락 미확보. 제품 탑재 후보로 보류하되 행동 라벨·시간축 설계의 원문 근거 |
| **MediaPipe Face+Hand Landmarker** | 공식 Android API와 face/hand `.task` 존재. 478 face points·52 blendshapes / 21 hand points. 욕구·눈비비기·하품 head 없음 | VIDEO 모드 tracking으로 위치·변화를 관찰할 기반. 영아 전용 평가가 아니며 손과 얼굴의 주인 연결도 추가 검증 필요 | face mesh·blendshape 모델카드 Apache2.0, 코드 Apache2.0. 얼굴+손 time-series 관찰을 먼저 시험할 수 있음. 전체 bundle 구성요소 권리·infant-domain·실폰 비용 확인 필요 |
| **MoViNet-A0-Stream** | 공식 TFLite, 600-way Kinetics head와 state 입출력. HTTP metadata상 **27,141,832bytes** | causal (2+1)D conv의 시간축. `yawning`, `crawling baby`, 음식 먹기·손뼉 놀이 등의 일반 행동 라벨 존재 | 공식 project Apache2.0. 실제 모바일 배포 경로 있으나 6~18개월 행동과 욕구 성능은 없음. MediaPipe 관찰 부족 시 별도 작은 비교 후보 |

공개 파일 **존재**와 정확한 weight tensor·전처리·head를 **실행해 확인**한 것을 구분한다. 위 크기는 API/HTTP metadata다. 내려받아 해시한 모델 크기, 전체 peak RAM, APK 증가량이나 속도가 아니다.

## voc2vec: 이름과 quickstart보다 config가 중요하다

고정 공식 소스는 [koudounasalkis/voc2vec, ef960582d728b71988649320ba144db6bf1ecfe2](https://github.com/koudounasalkis/voc2vec/tree/ef960582d728b71988649320ba144db6bf1ecfe2)다. [README](https://github.com/koudounasalkis/voc2vec/blob/ef960582d728b71988649320ba144db6bf1ecfe2/README.md)는 코드와 모델 모두 Apache2.0으로 공개했다고 명시한다. Voc125 125.19h 중 FreeSound babies23.42h와 TUT babies13.17h가 있으나, 공개 본문만으로 아기 월령·가족 분리·각 음원의 상업 권리 provenance를 확정할 수 없다. 논문의 ASVP-ESD babies/Donate-a-cry 평가를 새 아기 detector 배포물과 구분한다.

| 공식 HF 배포 / 고정 revision | `pytorch_model.bin` bytes | 실제 config architecture |
|---|---:|---|
| [voc2vec / ce6d664f363af5ae15d4376717c0e26b5490f8dd](https://huggingface.co/alkiskoudounas/voc2vec/blob/ce6d664f363af5ae15d4376717c0e26b5490f8dd/config.json) | 380,250,930 | Wav2Vec2ForPreTraining |
| [voc2vec-as-pt / 3df34d8f964f1514740b19901419e460396c0be8](https://huggingface.co/alkiskoudounas/voc2vec-as-pt/blob/3df34d8f964f1514740b19901419e460396c0be8/config.json) | 380,250,930 | Wav2Vec2ForPreTraining |
| [voc2vec-ls-pt / 21c5653e5b5b2691e42cb5462830f8f55005216d](https://huggingface.co/alkiskoudounas/voc2vec-ls-pt/blob/21c5653e5b5b2691e42cb5462830f8f55005216d/config.json) | 380,250,930 | Wav2Vec2ForPreTraining |
| [voc2vec-hubert-ls-pt / 7c0c7c26ab9b3eb1482fef276f4a77f3ea0acd92](https://huggingface.co/alkiskoudounas/voc2vec-hubert-ls-pt/blob/7c0c7c26ab9b3eb1482fef276f4a77f3ea0acd92/config.json) | 377,555,286 | HubertModel |

bytes와 LFS hash는 `/api/models/{id}/revision/{sha}?blobs=true`로 조회했다. 예: [HuBERT API](https://huggingface.co/api/models/alkiskoudounas/voc2vec-hubert-ls-pt/revision/7c0c7c26ab9b3eb1482fef276f4a77f3ea0acd92?blobs=true). 네 config 모두 12층·768dim, 영아 detector의 의미 있는 `id2label`이 없다. HF `pipeline_tag:audio-classification`만으로 task head가 있다는 결론은 성립하지 않는다.

README quickstart의 `voc2vec-hubert-ls`는 공개 목록의 `voc2vec-hubert-ls-pt`와 이름도 다르다. AutoModelForAudioClassification을 임의 생성해 누락 head를 초기화한 결과를 영아 판정으로 사용하면 안 된다. [fine-tuning 코드](https://github.com/koudounasalkis/voc2vec/blob/ef960582d728b71988649320ba144db6bf1ecfe2/src/ssl_model_finetuning.py)는 새 label mapping/head와 `ignore_mismatched_sizes=True`를 사용하는 학습 절차다. row index를 shuffle하여 10fold로 나누므로 코드만으로 아이/원음 분리 benchmark라고 주장하지 않는다. 저자의 [공개 HF 목록](https://huggingface.co/api/models?author=alkiskoudounas&limit=100)에서도 별도 voc2vec 영아 task checkpoint는 확인되지 않았다. 모든 비공개 배포물의 부존재를 증명한 것은 아니다.

**판단:** 공개·상업 가능한 비언어 representation이라는 차이는 있지만 현재 전문 모델 보류를 뒤집을 완성 detector가 아니다. 이 큰 encoder를 우선 다운로드하거나 증류하는 대신 권리가 확인된 task head와 대상 월령 독립 평가가 나오는지 재검토한다.

## VocalBaby: 실제 작은 head가 있으나 제품 경로는 막혀 있다

[공식 고정 소스 8d214be476e9819322622cb08966faf00fdaeb3b](https://github.com/arunps12/VocalBaby/tree/8d214be476e9819322622cb08966faf00fdaeb3b)와 [recursive tree API](https://api.github.com/repos/arunps12/VocalBaby/git/trees/8d214be476e9819322622cb08966faf00fdaeb3b?recursive=1)를 확인했다. `VisionInfantNet`를 조회한 [GitHub API](https://api.github.com/repos/arunps12/VisionInfantNet)의 `full_name`과 `html_url`은 **arunps12/VocalBaby**다. 검색 서비스에 남은 VisionInfantNet URL을 별도 아기 영상 모델로 세면 중복이며 이름 오해다.

| 파일 | Git blob SHA / bytes |
|---|---|
| `final_model/xgb_egemaps_smote_optuna.pkl` | `4e84c2f26094f113c66341e8a8599ecbeb484c0b` / 5,432,713 |
| `final_model/preprocessing.pkl` | `f282f2bc1ae047c44bdb7854c4d054d662fed35a` / 749 |
| `final_model/label_encoder.pkl` | `636b337f712094f0f445b42e791e06e79e4d856e` / 500 |

위는 **Git blob ID**이며 SHA256이 아니다. pkl를 다운로드·역직렬화·실행하지 않았다. 실제 persisted label order, model parameters, training run과 현재 source 대응을 확정한 결과도 아니다.

- [prediction_pipeline.py](https://github.com/arunps12/VocalBaby/blob/8d214be476e9819322622cb08966faf00fdaeb3b/src/vocalbaby/pipeline/prediction_pipeline.py)는 16kHz·0.4초 비중첩창·마지막0.2초 미만 생략을 사용한다. `predict_proba`를 창별로 얻은 후 **mean→argmax→파일당 라벨**로 반환한다. 자체 native temporal head나 event boundary 출력이 아니다. 10/20초 전체 평균은 짧은 옹알이 희석을 다시 일으킬 수 있다.
- [feature extraction](https://github.com/arunps12/VocalBaby/blob/8d214be476e9819322622cb08966faf00fdaeb3b/src/vocalbaby/components/data_transformation.py)는 openSMILE eGeMAPSv02 Functionals88dim이다. 추출 예외를 **0벡터**로 반환하는 경로가 있어 오류를 실제 무음/정상 특징과 구분하는 추가 계약이 필요하다.
- [ingestion](https://github.com/arunps12/VocalBaby/blob/8d214be476e9819322622cb08966faf00fdaeb3b/src/vocalbaby/components/data_ingestion.py)은 unique child IDs를 먼저 분리하므로 행 랜덤 분할보다 적합하다. 그러나 [params](https://github.com/arunps12/VocalBaby/blob/8d214be476e9819322622cb08966faf00fdaeb3b/params.yaml)의 입력은 `private_metadata.csv`다. 공개 train/test 아이 목록·월령·실제 persisted model의 split provenance는 확보하지 못했다.
- 공식 README의 test UAR=.460, weightedF1=.600은 저자 보고다. laughingF1<.11, cryingF1≈.32의 flat classifier 문제를 저자가 명시하며 hierarchy는 개선을 목표로 하는 후속 코드다. hierarchy의 완성 공개 weight·개선 수치가 있다는 뜻이 아니다.
- repo MIT가 특징 추출기의 권리를 덮지 않는다. [openSMILE 공식 License](https://audeering.github.io/opensmile-python/#license)는 **“It is not allowed to use the open-source version of openSMILE for any sort of commercial product.”** 및 연구 결과가 제품이면 commercial development license 필요를 명시한다. 무료 베타·로컬 실행·작은 XGBoost로 권리 문제를 해소했다고 볼 수 없다. 상업권리 구매·저자 연락·외부 서버 호출은 이번에 하지 않았다.

**판단:** 신규 task weight의 존재는 확인했으나 eGeMAPS 재현·권리·월령·head parity가 선행한다. 기존 MFCC 코드를 eGeMAPS 대신 연결하면 같은 weight 추론이 아니다. 큰 HindiBabyNet encoder 없이 작은 특징+head의 가능성을 보여주는 연구 참고로 남긴다.

## 실제 영아 행동 연구가 다루는 것

### BabyNet: 손 뻗기와 물체 관계를 시간에 따라 본다

[저자 arXiv2208.04950v2 원문](https://arxiv.org/pdf/2208.04950v2)은 **6~12개월**,21subjects·20공개 YouTube videos·193reaches를 다룬다. 일부 나이는 제목·관찰로 추정됐다. 손과 물체가 완전히 보이는 앉은 자세·고정 카메라가 선택 조건이고, 가림·중단 reach·손 사이 전달은 제외됐다. RN은 물체를 향해 손이 움직이기 시작한 frame, RF는 닿는 frame이다. 음식 여부·배고픔·놀이 요청 라벨이 아니다.

BabyNet의 LSTM head는 **1,204parameters**, bbox 중심 거리/IoU의 두 timestep 입력과 RN/RF/Reach/NoReach 출력이다. 작은 head 앞에는 별도로 fine-tune한 YOLOv3 infant/left-hand/right-hand/object detector가 필요하다. 1,204params를 전체 모델이나 모바일 비용으로 쓰면 안 된다. BabyNet/MLP는63reaches, 큰 비교모델은193reaches로 실험했으며60/15/25split을 설명한다. 아이 완전 분리 holdout은 확인되지 않았다. 논문 test66.27%는 reach frame classification 결과이며 원하는 음식·의도 정확도가 아니다.

공식 논문에서 배포 checkpoint·Android source·상업 grant를 확보하지 못했고 GitHub BabyNet/E-BabyNet/reaching 검색에서도 대응 release를 찾지 못했다. [Ibrassow/BabyNet](https://github.com/Ibrassow/BabyNet)는 README가 iPlanner 로봇 경로 계획으로, 같은 영아 모델이 아니다. 후속 **E-BabyNet**, [DOI10.1109/TNSRE.2024.3392161](https://doi.org/10.1109/TNSRE.2024.3392161), [공식 EuropePMC metadata](https://www.ebi.ac.uk/europepmc/webservices/rest/search?query=EXT_ID:38648156&format=json)는 확인했지만 open full text/weight는 확보하지 못했다. 375reaches·BiLSTM이라는 discovery abstract만으로 대상 월령·분할·가중치를 확정하지 않는다.

### InfAct와 NNS: 시간축 가능성의 근거와 사용권을 함께 본다

[InfAct 고정 source a68ba1287f5e84606fbb68453f6e6acb06a4be98](https://github.com/ostadabbas/Infant-Posture-based-Action-Recognition/tree/a68ba1287f5e84606fbb68453f6e6acb06a4be98), [CVF 원문](https://openaccess.thecvf.com/content/CVPR2023W/L3D-IVU/papers/Huang_Posture-Based_Infant_Action_Recognition_in_the_Wild_With_Very_Limited_CVPRW_2023_paper.pdf)은3~18개월 추정200videos를5자세와 그 전환으로 표현한다. FiDIP2D/HW-HuP3D pose→posture→transition segmentation→start/end posture pair라는 실제 시간축이다. 테스트는50clips이며 아이/가족 완전 분리라고 확인되지 않았다. 저자가 연결한 [posture weights](https://drive.google.com/drive/folders/1X_d_Rle9aDeyCNECaH8Wu1N2IHdF9gAJ?usp=share_link)와 [segmentor weights](https://drive.google.com/drive/folders/1ZuwZ8sqY5w-27N3EcWqtPamztP00diCz?usp=share_link)는 링크 존재만 확인했으며 파일별 크기·내용을 검증하지 않았다. 라벨은 눈비비기/하품/음식손뻗기/놀이반응이 아니다. [2024 InfActPrimitive source 505107a8004a4487eee62d289e9a470327a9ebcf](https://github.com/ostadabbas/Video-Based-Infant-Action-Recognition/tree/505107a8004a4487eee62d289e9a470327a9ebcf) 역시 자세 primitives다. 두 README 모두 code/data **non-commercial** 조건과 제품 편입 제한을 명시한다.

[NNS 고정 source a29fe78223b4b6c77388474fd1e267a389fe3f5e](https://github.com/ostadabbas/NNS-Detection-and-Segmentation/tree/a29fe78223b4b6c77388474fd1e267a389fe3f5e)는 실제 [pretrained zip](https://coe.northeastern.edu/Research/AClab/NNS/pretrained_model.zip)을 연결하고 CPU inference entrypoint를 제공한다. optical flow+CNN/LSTM·짧은창·시간구간 aggregation으로 비영양성 빨기를 검출한다. **Non-nutritive는 영양 공급이 없는 빨기**다. 이를 hungry label로 바꾸면 라벨 의미를 바꾸는 것이다. 공개 README의19infants/183h clinical와30개의60초clip 결과는6~18개월 앱·10/20초 일반 가정영상 평가가 아니다. NC 조건과 invention disclosure도 명시되어 제품 후보로 중단한다. 실제 공개 weight가 있어도 상업 탑재 권리가 없다는 사례다.

**새 연구지만 실행 경로 미확보:** [Non-Contact Infant Sleep Monitoring via Deep Learning-Based Facial Behavior Analysis, IEEE11536128](https://ieeexplore.ieee.org/document/11536128), DOI10.1109/ARSO68304.2026.11536128의 공식 abstract는 MobileNet eye/mouth binary state와 temporal logic, blink/yawn을 설명한다. 이번 읽기에서는 abstract+introduction만 확보했고 full PDF 접근 불가였다. 코드·task weights·월령·수면 정답·독립 아이 분할·Android 비용 미확인으로 **적용 가능 모델 목록에는 올리지 않는다**. 눈/입 상태에서 행동을 시간에 따라 결합하는 관련 연구가 있다는 사실만 남긴다.

## 작은 시각 실험의 현실적인 출발점

### MediaPipe: 먼저 관찰 시계열의 품질을 측정한다

[Face 공식 guide](https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker), [Hand guide](https://ai.google.dev/edge/mediapipe/solutions/vision/hand_landmarker), [고정 source0e9cdac7342775f9d6bb5f36276d4c0b1c5cb716](https://github.com/google-ai-edge/mediapipe/tree/0e9cdac7342775f9d6bb5f36276d4c0b1c5cb716)를 확인했다. IMAGE/VIDEO/LIVE_STREAM이 있고 tracking은 추론 비용을 줄이는 수단이다. tracking이 아기 신원 또는 소리 화자를 입증하지 않는다. Face smoothing은 num_faces=1에서만 적용된다.

| 정확한 버전1 bundle | HTTP bytes / generation / ETag |
|---|---|
| [face_landmarker float16/1](https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task) | 3,758,596 / `1683136941916318` / `b0e7274907a1644404fef66b28dd6d85` |
| [hand_landmarker float16/1](https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task) | 7,819,105 / `1682480004222387` / `15318430ea3851670fe9914116a9cfad` |

ETag를 SHA256이라고 쓰지 않는다. [FaceMeshV2 card](https://storage.googleapis.com/mediapipe-assets/Model%20Card%20MediaPipe%20Face%20Mesh%20V2.pdf)와 [BlendshapeV2 card](https://storage.googleapis.com/mediapipe-assets/Model%20Card%20Blendshape%20V2.pdf)는 Apache2.0이며 얼굴 크기·orientation·조명·motion과 inter-frame jitter 한계를 명시한다. 영아 월령별 성능은 확인하지 못했다. Blendshape는 표현계수이며 특정 욕구/감정의 확률이 아니다. Hand guide의Pixel6 CPU17.12ms/GPU12.27ms는 저자 pipeline benchmark이며 아가톡 실측이 아니다.

제안하는 **첫 실험은 새 욕구 head 학습보다 얼굴·손 time-series가 실제로 남는지 확인**하는 것이다. 앱 변경 없이, 기존 허용 로컬영상10/20초를 timestamp와 함께10fps 등 고정 방식으로 읽어 mouth opening, eye closure, hand-to-eye distance와 손 이동의 지속/반복/가림을 기록한다. 10fps는 이번 제안 값이며 빠른 행동을 충분히 샘플링한다는 보장이 없다. 음성 event time과 겹치는 구간을 맞추되 “같은 시각에 보임”을 화자 일치로 승격하지 않는다. 실제 관찰 label로 event precision/recall·경계오차·보류율을 확인하고, 샘플링 누락이 있으면 필요한 구간에서만 fps를 높여 비용을 비교한다.

눈 비비기에는 손의 주인·눈 주변 접촉·반복/왕복 이동이, 하품에는 입 벌림의 시간 변화와 발성/먹기/웃음 대조가 필요하다. 단순히 손이 눈 근처에 있거나 입이 열린 한 프레임은 해당 행동의 정답이 아니다. 음식 손 뻗기에는 별도 음식/그릇/숟가락·제시 방향·손 접근이라는 object context가 필요하다. Face+Hand만으로 음식 여부를 알 수 없다. 놀이 반응에는 보호자의 행동과 아기의 응답·미소/웃음/소리의 선후관계가 필요하며 이것도 놀이를 요구하는 의도와 구분한다.

규칙/소형TCN/GRU는 위 관찰 특징을 묶는 **새 학습 설계**일 수 있지만 이번에 공개된 영아 눈비비기/하품/요청 taskweights가 확인된 것은 아니다. 정답·아이 분리 data가 없는 상태에서 작은 head를 무작위 초기화해 적용가능 모델로 보고하지 않는다.

### MoViNet-A0: 모바일 실행물은 있지만 라벨의 의미를 제한한다

[TensorFlow Models 고정 source930a6f98f7debcc32ca7afbca4a176dbf9211e03](https://github.com/tensorflow/models/tree/930a6f98f7debcc32ca7afbca4a176dbf9211e03), [공식 README](https://github.com/tensorflow/models/blob/930a6f98f7debcc32ca7afbca4a176dbf9211e03/official/projects/movinet/README.md), [600labels](https://github.com/tensorflow/models/blob/930a6f98f7debcc32ca7afbca4a176dbf9211e03/official/projects/movinet/files/kinetics_600_labels.txt)를 확인했다. [공식 A0-stream TFLite](https://storage.googleapis.com/tf_model_garden/vision/movinet/movinet_a0_stream.tflite)는200OK,27,141,832bytes,generation`1626903706896942`,ETag`7ccd041e8adcc8ba0e1473ddefeebc20`였다. README 표의“13MB”와 현재 HTTP 크기가 달라 그대로13MB라고 보고하지 않는다. 바이너리 다운로드·tensor/signature 검사·현재 artifact와 과거 latency 측정의 대응은 미검증이다.

A0는172×172 입력으로 상태를 누적하는 causal2+1D 모델이며, 마지막 출력은 누적 clip classification이다. streaming score를 event onset/off 확률로 바꾸려면 별도 평가가 필요하다. 매 영상 시작 시 state를 초기화하고10→20초의 frame 순서·시각간격을 보존해야 한다. 공식Pixel4 단일CPU float32 **22ms/frame**은 과거 저자 측정이다. 이를 영상decode·화면·다른 모델을 포함한20초 앱 전체시간으로 쓰지 않는다.

600labels에는 `yawning`, `scrubbing face`, `crawling baby`, `playing hand clapping games`, `tasting food` 등이 있다. `scrubbing face`는 눈비비기 head가 아니며 `yawning`에6~18개월 domain 보장이 없다. 음식 먹는 행동도 배고픔 요청과 다르다. 얼굴/손 관찰이 부족할 때 하품 등 **관찰 행동 한 종류**에만 고정 비교하고, 성인하품·옹알이·웃음·먹기·표정·가림·TV를 함께 대조한다. 현재600class를3욕구로 임의 매핑하지 않는다. project Apache2.0과 실제 TFLite 공개는 긍정적이나 artifact별 권리·학습data provenance와 infant-domain은 별도 확인한다.

## 다음 단계와 보류 조건

1. **음성 누락은 기존 EfficientSED exact checkpoint의 Android parity·새 실패/성인·TV 대조를 우선**한다. 바로 다음 P0는 PCM→mel의 수치 일치다. voc2vec는 공개 task head와 합법 학습·평가 자료가 없는 동안 대체하지 않는다. VocalBaby는 openSMILE 제품 권리와 정확한 checkpoint 계약이 확보되기 전 실행/탑재 후보로 올리지 않는다.
2. **시각은 Face+Hand 시계열 관찰의 가능 여부부터** 확인한다. 사용자는 가입·수동상황탭을 추가할 필요가 없다. 연구자가 로컬에서 행동 정답과 모름을 annotation하는 것과 사용자 입력 UX를 추가하는 것은 별개다. 관찰이 거의 남지 않거나 다른 사람의 손/얼굴을 연결하면 행동 판정을 보류한다.
3. 하품 같은 단일 행동에서 landmark 기반 관찰과 MoViNet을 비교할 자료가 확보될 때만 다음 작은 실험을 한다. 원음/원본/창/증강/embedding은 같은 split, 아이와 가족의 독립 test를 유지한다. 이번 기존 자료를 독립 6~18개월 benchmark로 보고하지 않는다.
4. 잠시 하품·눈비비기·음식 접근·놀이 반응을 검출해도 **배고파요/졸려요/놀아줘요의 정확도가 개선됐다는 뜻은 아니다**. 행동별 event 지표와 요청 맥락 적합성 평가를 나누고 다중 원인·반례·모름을 허용한다. 문장만 다양해진 결과도 검출 정확도 개선과 나눈다.
5. 실폰 10/20초 cold/warm·RAM·취소·연속 분석 발열을 확인할 때까지 Android 완성으로 보고하지 않는다. 얼굴 몇 장→욕구 확정, 거대 LLM 재설계, 원본·embedding 업로드, 원격 추론·로그인·유료 API·Elice 경로는 이번 조사로 승인되지 않았다.

조사 경로는 research+agent-reach의 GitHub CLI/API·HF 공식 API·Jina를 통한 저자 arXiv/CVF/IEEE/Google 원문이다. Exa는 발견용 검색에만 썼으며 snippet 수치를 원문 검증 결과로 쓰지 않았다. 임시 공개 문서/metadata는 `~/.agent-reach/agattok-context-20261006/`에 두었다. 새 모델/사용자 미디어 다운로드·실행·가입·약관 수락·저자 연락·빌드/ADB·commit/push는 없다. Agent Reach 1.5.0 최신을 확인했다. 이 문서는 5개 경로의 실무 판단을 추가한 것이며 배포·제품 인식 회복을 완료한 보고가 아니다.
