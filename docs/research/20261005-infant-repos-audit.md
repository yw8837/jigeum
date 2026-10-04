# 공개 아기 소리·표정 저장소 및 잡음 처리 후보 감사

조회일: **2026-10-05**. 대상: 아가톡의 **6~18개월 아기 발성 누락·잡음 처리·선택한 10/20초 전체 온디바이스 분석**.

## 1. 결론과 적용 순서

공급 링크를 GitHub 공식 API의 저장소·파일 트리·README·선별 소스, Hugging Face 공식 모델 카드·config·LFS 메타데이터로 확인했다. 일반 웹 링크도 직접 확인했다. **이 문서는 저장소 조사 결과이며 새 모델의 다운로드·추론·Android 빌드·기기 QA를 수행한 결과가 아니다.** 바이트 수는 공개 파일 메타데이터다. RAM, APK 증가량, Android 처리 시간으로 바꾸어 해석하지 않는다.

현재 기준본은 [HANDOFF](../HANDOFF.md)의 AST beta25 검증이다. 알려진 한 원본의 회복과 기존 회귀 검사를 보존하면서, 작은 소리를 없애는 전처리 또는 전체 구간 누락부터 확인해야 한다. 이미 비교한 [SpeechMaturity](20261004-speech-maturity-artifacts.md)와 기존 voc2vec HuBERT 조사를 새 결과로 세지 않는다. 이 문서는 다른 작업자가 조사하는 AV 모델·논문 목록과 별개다.

| 순서 | 후보·작업 | 우리 조건과 맞는 점 | 아직 적용할 수 없는 이유·다음 확인 |
|---|---|---|---|
| 1 | 기존 PCM 전처리·전체 10/20초 구간 검사 | 기존 모델과 기준본을 유지하면서 작은 발성 삭제·누락 여부를 직접 분리할 수 있음 | 저장소 이름이나 논문 정확도로 해결 여부를 판단할 수 없음. 실제 원음/처리음·시간 커버리지·약한 발성·비아기 대조를 비교 |
| 2 | **EfficientAT `mn04_as_mAP_432.pt`** | 공식 공개 학습 head, AudioSet 527라벨에 `Babbling`, `Baby cry, infant cry`, `Baby laughter`; 파일 **4,066,513 bytes** | PyTorch 모델이며 공식 Android 실행 근거 미확인. 전처리·변환·라벨 매핑 및 기존 승인/보류의 paired 비교 필요. 일반 소리 분류이며 욕구·의도 번역 모델이 아님 |
| 3 | **GTCRN + sherpa-onnx**를 제한 비교 | 실제 ONNX **352,084 bytes**; sherpa 공식 Kotlin denoiser API·Android AAR 있음 | 성인 음성 강화 모델의 아기 발성 보존이 검증되지 않음. 처리음을 판정에 바로 대체하지 말고 원음과 비교해 발성 손실·오인·시간 비용을 먼저 확인 |
| 4 | **LittleBeats의 공개 fine-tuned head** | 가족·아기 음성, cry/fuss/babble와 화자 라벨. pretrained encoder 외 fine-tuned `model.ckpt`도 실제 공개 | encoder 약377.6MB+head 약3.6MB, Python/Fairseq/SpeechBrain 의존. 선택 checkpoint의 head·recipe·라벨 복원 일치, 변환, 6~18개월 일반화, 모델/데이터 이용 조건 미확인. 기존 SpeechMaturity와 동일 classifier가 아님 |
| 5 | **VocalBaby** | 실제 약5.4MB XGBoost head; canonical/non-canonical/cry/laugh/junk가 문제에 직접 관련 | eGeMAPS/openSMILE 상업 이용 제약, Android 파이프라인 미확인, infant-disjoint split·월령 대표성 미확인. README test UAR0.460이며 제품 정확도로 인용 불가 |
| 6 | **DTLN**, **RNNoise**, **DeepFilterNet** | 공개 구현·일부 소형 가중치; 잡음 처리 비교 후보 | 성인 speech 강화 목표이며 아기 소리 보존 증거 없음. DTLN TFLite 파일·RNNoise C·DFN ARM 실행은 우리 앱의 Android QA가 아님. 모델 권리·버전·연산량을 개별 확인 |
| 7 | **EmotiEffLib** | 실제 공개 소형 표정 모델과 Android 예제 코드 | 영아 월령별 타당성·학습 데이터 이용 조건 미확인. 얼굴 표현 관찰에만 제한하고 배고픔/통증/화자/발성 동기로 확대하지 않음 |
| 보류 | voc2vec pretrained, BEATs, DeepFace, baby-emotion | 표현 학습 또는 일반 표정/소리 연구 참고 | voc2vec는 즉시 사용할 아기 classifier head가 없음. BEATs는 checkpoint별 head 유무가 다르고 큰 연구 후보. 일반 표정은 영아 검증이 없음 |
| 제외 | 다섯 가지 울음 원인 프로젝트, VocalMat, 중복·인덱스 링크 | 라벨·실험 설계 참고 가능 | 울음 원인 라벨은 옹알이 검출과 다름. 대부분 가중치 또는 이용허락 부재. VocalMat은 쥐 초음파, VisionInfantNet은 audio repo 중복, topic/문서 링크는 모델이 아님 |

**즉시 교체 승인 근거가 되는 새 후보는 없다.** 파일·head·Android 경로가 확보된 후보부터 제한적으로 비교할 수 있다. 성공 기준은 소리의 유형 관찰과 발성 누락 감소이며 아기 의도 번역 정확도나 의료 진단이 아니다.

## 2. 확인 기준

- **가중치 있음**: GitHub blob/release asset 또는 HF LFS 파일이 존재한다는 뜻이다. 파일을 로드하여 tensor·estimator를 검증한 것은 아니다. notebook의 경로나 README 다운로드 언급만 있으면 공개 가중치가 확인됐다고 하지 않았다.
- **Android 있음**: 실제 공식 Android 소스/패키지 근거가 있는 경우만 표시했다. ONNX/TFLite 형식, Raspberry Pi/ARM 실행, MATLAB/iOS 모델만으로 Android 실행을 확정하지 않았다.
- **라이선스 분리**: 저장소 코드 LICENSE, 가중치에 명시된 허락, 학습/평가 데이터 권리를 각각 기록했다. 코드 MIT/Apache만으로 모든 pretrained weight·얼굴 데이터·아기 녹음의 상업 이용을 확정하지 않는다. LICENSE 부재는 금지 여부를 단정하는 대신 허락 미확인으로 처리한다.
- GitHub는 표에 명시된 조회 당시 branch를 읽었다. branch 링크는 이후 바뀔 수 있다. HF는 아래 revision을 고정했다. Git LFS pointer 크기를 실제 모델 크기로 세지 않았다.
- 사용자 원음·모델 binary·논문 첨부를 외부로 전송하지 않았다. 공개 문서와 공개 API만 조회했다. 개인 녹음 DB·로그인·업로드 기능을 앱에 추가하지 않았다.

## 3. 공급된 울음 분류 프로젝트

공통으로 다섯 원인 라벨은 대체로 `belly_pain / burping / discomfort / hungry / tired`다. 이 라벨은 데이터셋의 주석이며 아기의 실제 욕구·의도나 원인에 대한 정답을 보장하지 않는다. 옹알이 head와 분리해야 한다.

| 공급 링크·조회 branch | 정체·라벨·입력 | 실제 공개 가중치·파일 크기 | 코드 / 모델 / 데이터 라이선스 | Android·우리 조건 부적합 이유 |
|---|---|---|---|---|
| [Aniruth2003/Infant-cry-classification](https://github.com/Aniruth2003/Infant-cry-classification), `main` | notebook·Raspberry Pi 센서·Telegram 알림. detector `laugh/Silence/crying`, classifier 다섯 원인. 5초 녹음 | `ModelExecution.ipynb`가 Kaggle 경로의 `detection.h5`, `classification.h5`를 참조. 조회 tree에는 weights 없음 | 코드 LICENSE 없음 / weights 허락 미확인 / Kaggle 원본별 허락 미확인 | Android 없음. Telegram 전달 경로는 온디바이스 계약과 맞지 않음. 옹알이 분류 아님 |
| [gaganchapa/Baby_Cry_Detection](https://github.com/gaganchapa/Baby_Cry_Detection), `main` | Donate-a-Cry 다섯 원인; MFCC40+DecisionTree notebook | weights 없음 | 코드 LICENSE 없음 / 모델 허락 미확인 / Donate-a-Cry 원자료 권리 별도 | Android 없음. 전체 데이터 oversampling 후 split하는 코드가 있어 README95%는 독립 일반화 근거로 쓸 수 없음 |
| [ThinkerYard/DeepInfant](https://github.com/ThinkerYard/DeepInfant) | 공식 API **404** | 공급 주소에서 미확인 | 모두 미확인 | 아래 `skytells-research`는 검색으로 발견한 별도 대안이며 같은 URL로 간주하지 않음 |
| [echoCodeScript/Infant-Cry-Classification-ML-Model](https://github.com/echoCodeScript/Infant-Cry-Classification-ML-Model), `main` | Donate-a-Cry 다섯 원인; MFCC40+chroma12+mel128+contrast7+tonnetz6=193 features, RF/XGBoost | 코드·notebooks만; weights 없음 | 코드 LICENSE 없음 / 모델 허락 미확인 / 데이터 원본 권리 별도 | Android 없음. README99%는 우리 아기·잡음·월령·10/20초 성능 근거가 아님 |
| [Omshinde01/InfantCry_Classification](https://github.com/Omshinde01/InfantCry_Classification), `main` | ResNet34, 16kHz/5초/64mel, 다섯 원인. API에서 YAMNet cry 관련 keyword 합계 threshold0.10 gate | 실제 ResNet34 파일 **85,270,891 bytes**. `api/`와 `models/` 사본은 동일 blob이며 독립 모델2개가 아님 | README MIT badge와 LICENSE 링크가 있으나 실제 tree에 LICENSE 없음 / 별도 weight 허락 미확인 / Donate-a-Cry 등 데이터 조건 별도 | Android 없음. `api/app.py`가 TFHub YAMNet을 online load하고 들어온 원음 bytes를 S3로 background upload. 그대로 통합 불가. 옹알이 head 없음 |
| [MiladSoleymani/infant_cry_classification](https://github.com/MiladSoleymani/infant_cry_classification), `main` | PyTorch CNN/ResNet, 3초128mel, Donate-a-Cry 다섯 원인 | tree weights 없음 | 표준 LICENSE 없음; README research/educational 문구 / 모델 허락 미확인 / 데이터 조건 별도 | Android 없음. 가중치·배포 허락·우리 발성 과제 적합성 부족 |
| 복구 후보 [skytells-research/DeepInfant](https://github.com/skytells-research/DeepInfant), `main` | iOS/CoreML, 다섯 cry 원인; README2~7초, VGGish975ms, private+published data | `DeepInfant_AFP.mlmodel` **17,384**; `DeepInfant_VGGish.mlmodel` **4,909,457**; `DeepInfant_V2.mlmodel` **5,302,732 bytes** | 코드 Apache-2.0 / 가중치별 추가 명시 여부 미확인 / private data 및 공개 데이터 권리 별도 | Android 없음. CoreML 변환·월령·원인 라벨 타당성 미검증. README89%를 앱의 정확도로 옮기지 않음 |

핵심 소스: [Aniruth notebook](https://github.com/Aniruth2003/Infant-cry-classification/blob/main/ModelExecution.ipynb), [gagan split 코드](https://github.com/gaganchapa/Baby_Cry_Detection/blob/main/code.ipynb), [Omshinde API](https://github.com/Omshinde01/InfantCry_Classification/blob/main/api/app.py), [DeepInfant 모델 경로](https://github.com/skytells-research/DeepInfant/tree/main/Models), [DeepInfant LICENSE](https://github.com/skytells-research/DeepInfant/blob/main/LICENSE).

## 4. 발성 분류·표현 학습 후보

| 공급 링크·버전 | 정체·라벨·공개 파일 | 코드 / 모델 / 데이터 라이선스 | Android·평가·우리 조건 |
|---|---|---|---|
| [arunps12/VocalBaby](https://github.com/arunps12/VocalBaby), `main` | eGeMAPS88+XGBoost; `Non-canonical / Junk / Canonical / Crying / Laughing`. `final_model/xgb_egemaps_smote_optuna.pkl` **5,432,713 bytes**, label encoder500, preprocessing749 bytes | 코드 MIT / 별도 weight 명시·권리 범위는 추가 확인 / naturalistic infant dataset 출처·월령·독립 infant split·배포허락 미확인. **openSMILE open-source판 commercial product 금지**, 별도 상업 license 필요 | Android 없음. FastAPI/AWS/DVC 운영 template. 10,770 segments, README test UAR0.460·weightedF1 0.600; rare laugh/cry 성능 낮음을 스스로 명시. 우리 아기 원본 성능 미측정 |
| [arunps12/VisionInfantNet](https://github.com/arunps12/VisionInfantNet), `main` | 이름과 달리 VocalBaby README·audio pipeline. 위 세 `final_model` 파일의 blob SHA가 VocalBaby와 동일 | 코드 MIT / 모델·데이터·openSMILE 제한 동일 | 독립 영아 영상 모델로 세지 않음. Android 없음 |
| [jialuli3/speechbrain](https://github.com/jialuli3/speechbrain/tree/infant-voc-classification/recipes/wav2vec_LittleBeats), `infant-voc-classification` | LittleBeats family audio training recipe. 화자 `CHN/FAN/MAN/CXN` + `SIL`; 아기 `CRY/FUS/BAB`; 성인 `CDS/MAN 또는 FAN/LAU/SNG`. hparams의 output head5/3/4 확인 | SpeechBrain 코드 Apache-2.0 / weights는 아래 HF 카드 / 가족 녹음·noise dataset 조건 별도 | Python/Fairseq/SpeechBrain, 공식 Android 없음. README 예시는2초. **`Readme.md`**가 실제 경로이며 대문자 `README.md`404는 저장소 부재가 아님 |
| [lijialudew/wav2vec_LittleBeats_LENA](https://huggingface.co/lijialudew/wav2vec_LittleBeats_LENA), revision `b3250219066482f571ef3868413b0b0f1ad92502` | LB1100 pretrained **1,140,641,433**, LL4300 pretrained **1,140,642,009 bytes**. **fine-tuned `model.ckpt` 3,588,884 + `wav2vec2.ckpt` 377,568,539 bytes 존재**. ECAPA embedding 각83,310,835 bytes | recipe 코드 Apache-2.0 / HF metadata는 generic `openrail`; 정확한 적용 license 문구·모델 배포 조건 미확인 / 가족 녹음 데이터 권리 별도 | pretrained encoder만 공개된 모델로 단정하면 오류. 분류 recipe와 fine-tuned release는 확인했지만 tensor/head/라벨 복원은 실행 미검증. pretrain110가족 under5 +275LENA; labeled LB22/LENA30가족은 <14개월. 14~18개월 대표성·Android 미확인 |
| [alkiskoudounas/voc2vec](https://huggingface.co/alkiskoudounas/voc2vec), revision `ce6d664f363af5ae15d4376717c0e26b5490f8dd` | 실제 `pytorch_model.bin` **380,250,930 bytes**. config **`Wav2Vec2ForPreTraining`**, 12layers/hidden768. 표현 학습이며 ready-made infant class head 없음 | 카드 Apache-2.0 / 카드 기준 허락, 추가 weight 조건 미확인 / nonverbal multi-source data 권리 별도 | HF audio-classification tag만으로 classifier라고 부르면 안 됨. Android 없음. head 학습·변환·latency 검증이 필요해 즉시 적용 대상 아님 |
| 기존 비교 [voc2vec-hubert-ls-pt](https://huggingface.co/alkiskoudounas/voc2vec-hubert-ls-pt), revision `7c0c7c26ab9b3eb1482fef276f4a77f3ea0acd92` | `HubertModel`, **377,555,286 bytes**, infant head 없음 | 카드 Apache-2.0 / 데이터 권리 별도 | 위 공급 Wav2Vec2 pretraining 모델과 다른 artifact. 기존 [cry evidence](20261001-cry-evidence.md)를 보존; 새로 실행한 비교로 쓰지 않음 |
| [foduucom/baby-cry-classification](https://huggingface.co/foduucom/baby-cry-classification), revision `705bbf8439606e49343f837b7d35cfe3f7ee99d6` | 다섯 울음 원인, librosa193 features + saved sklearn classifier. `model.joblib` **113,695**, `label.joblib`527 bytes; binary 미로드로 estimator 종류·class ID 복원 미확인 | **metadata Apache-2.0와 README MIT 충돌**, 카드가 참조한 `LICENSE.md`는 siblings에 없음 / 별도 모델 허락 불명확 / 데이터 권리 별도 | Android 없음, 옹알이 과제 아님. 카드 자체 accuracy0.3846·macroF1 약0.38; sample의 FFT변수 미정의. 작은 파일만으로 사용 가능하다고 판단하지 않음 |

핵심 근거:

- [VocalBaby 공개 head 경로](https://github.com/arunps12/VocalBaby/tree/main/final_model), [VocalBaby params](https://github.com/arunps12/VocalBaby/blob/main/params.yaml), [VisionInfantNet 동일 경로](https://github.com/arunps12/VisionInfantNet/tree/main/final_model).
- [openSMILE 공식 이용 조건](https://github.com/audeering/opensmile#licensing): desktop/mobile/Android 지원을 언급하지만 상업 제품은 별도 허락이 필요하다. 이 엔진의 Android 지원은 VocalBaby 전체 classifier의 Android 검증이 아니다.
- [LittleBeats 정확한 Readme](https://github.com/jialuli3/speechbrain/blob/infant-voc-classification/recipes/wav2vec_LittleBeats/Readme.md), [head 구성](https://github.com/jialuli3/speechbrain/blob/infant-voc-classification/recipes/wav2vec_LittleBeats/hparams/hparams_LL_4300_WA.yaml), [고정 HF 파일 트리](https://huggingface.co/lijialudew/wav2vec_LittleBeats_LENA/tree/b3250219066482f571ef3868413b0b0f1ad92502).
- [voc2vec 고정 config](https://huggingface.co/alkiskoudounas/voc2vec/blob/ce6d664f363af5ae15d4376717c0e26b5490f8dd/config.json), [voc2vec 고정 카드](https://huggingface.co/alkiskoudounas/voc2vec/blob/ce6d664f363af5ae15d4376717c0e26b5490f8dd/README.md), [foduucom 고정 카드](https://huggingface.co/foduucom/baby-cry-classification/blob/705bbf8439606e49343f837b7d35cfe3f7ee99d6/README.md).

**SpeechMaturity 구분:** 기존 조사의 `Junk/Non-canonical/Canonical/Laughing/Crying` trained head와 LittleBeats upstream의 화자·cry/fuss/babble head는 서로 다르다. upstream을 공유한다는 이유로 같은 모델·같은 성능·같은 가중치라고 합치지 않는다.

## 5. 표정·다른 생물 발성·도구 링크

| 공급 링크·branch | 정체·라벨·공개 파일 | 코드 / 모델 / 데이터 라이선스 | Android·우리 조건 |
|---|---|---|---|
| [Aheli001/baby-emotion-recognition](https://github.com/Aheli001/baby-emotion-recognition), `main` | grayscale48x48 CNN; angry/disgust/fear/happy/neutral/sad/surprise. 실제 `BabyEmotion.onnx` **25,321,398**, `.pth` **25,323,124 bytes** | 코드 LICENSE 없음 / weights 허락 미확인 / train data·월령 대표성 미확인 | Flask/OpenCV/ONNX Python. 공식 Android 없음. `app.py`에 Gemini online 추천 API 경로. 이름만으로 영아 정서 검증 또는 AV 이해 모델이 되지 않음 |
| [serengil/deepface](https://github.com/serengil/deepface), `master` | 일반 얼굴 인식·인구통계·7표정 Python toolkit. 실제 v1.0 facial-expression weights: H5 **5,977,392**, ONNX **5,947,004**, PTH **5,949,382 bytes** | 코드 MIT / 각 upstream weight 이용 조건을 별도로 확인해야 함 / 얼굴 학습 데이터 권리 별도 | TF/PyTorch/ONNX backend 존재. 확인한 공식 구현은 Python이며 공식 Android 미확인. 영아 검증·의도 번역 근거 없음 |
| [sb-ai-lab/EmotiEffLib](https://github.com/sb-ai-lab/EmotiEffLib), `main`; [공식 웹](https://sb-ai-lab.github.io/EmotiEffLib/) | Python/C++ ONNX, 7/8표정. 8라벨 Anger/Contempt/Disgust/Fear/Happiness/Neutral/Sadness/Surprise. `enet_b0_8_best_vgaf.onnx` **16,039,595**, b2_7 **30,774,088**, b2_8 **30,779,724**, mbf PT **8,519,573 bytes** | 코드 Apache-2.0 / 선택 weight의 별도 조건 확인 필요 / VGGFace2 pretrain·AffectNet/AFEW/VGAF 데이터 권리 별도 | **실제 Android 예제 소스 있음**. `EmotionPyTorchClassifier.java`의 LiteModuleLoader/load/forward, TFLite classifier 모델 파일 명시를 확인. 우리 앱 통합·속도·영아 표정 신뢰도는 미측정 |
| [DietrichLab/VocalMat](https://github.com/DietrichLab/VocalMat) | 공식 API **404**. [Dietrich lab 페이지](https://www.dietrich-lab.org/vocalmat)는 살아 있고 **쥐 초음파 발성** 도구로 안내 | 공급 repo에서 미확인 | 사람 아기 후보 제외. 페이지가 안내하는 아래 대안은 별도 저장소 |
| 대안 [ahof1704/VocalMat](https://github.com/ahof1704/VocalMat), `master` | MATLAB, 11종 call shape+noise=12라벨. 모델 Git LFS/OSF 링크; tree134bytes는 pointer이며 **실제 모델 크기 미확인** | 코드 Apache-2.0 / OSF weights 별도 허락 미확인 / OSF 동물 데이터 권리 별도 | Android 없음, 사람 infant task 아님. [OSF 모델 링크](https://osf.io/3yc79/download)는 공개 안내 위치만 확인했고 binary 다운로드하지 않음 |
| [speechbrain/speechbrain](https://github.com/speechbrain/speechbrain), `develop` | Apache-2.0 Python toolkit·recipes·HF ecosystem. 단일 infant model 아님 | 코드 Apache-2.0 / 모델별 카드 / 데이터별 권리 | toolkit 자체를 공급 classifier로 세지 않음. 개별 recipe·checkpoint·모바일 경로 필요 |
| [github/topics/facial-expression-recognition](https://github.com/topics/facial-expression-recognition) | 실제 열리는 topic 검색 인덱스 | 연결된 각 프로젝트별로 다름 | 모델·weights·영아 평가·Android를 제공하는 단일 artifact가 아님 |
| [speechbrain.readthedocs.io](https://speechbrain.readthedocs.io/) | `/en/latest/`로 열리는 공식 도구 문서 | 문서/코드와 개별 모델 허락 구분 | 자체 infant checkpoint가 아님 |

핵심 소스: [Aheli app.py](https://github.com/Aheli001/baby-emotion-recognition/blob/main/app.py), [DeepFace ONNX Emotion](https://github.com/serengil/deepface/blob/master/deepface/models/demography/onnx/Emotion.py), [DeepFace TF Emotion](https://github.com/serengil/deepface/blob/master/deepface/models/demography/tf/Emotion.py), [DeepFace 실제 weight release](https://github.com/serengil/deepface_models/releases/tag/v1.0), [EmotiEffLib 모델](https://github.com/sb-ai-lab/EmotiEffLib/tree/main/models/affectnet_emotions), [Android PyTorch source](https://github.com/sb-ai-lab/EmotiEffLib/blob/main/training_and_examples/mobile_apps/demo/app/src/main/java/com/asav/facialprocessing/EmotionPyTorchClassifier.java), [Android TFLite source](https://github.com/sb-ai-lab/EmotiEffLib/blob/main/training_and_examples/mobile_apps/demo/app/src/main/java/com/asav/facialprocessing/EmotionTfLiteClassifier.java).

EmotiEffLib README의 예제 링크에 `mobile_app`가 보이더라도 실제 tree 경로는 **`mobile_apps`**다. 한 경로404만으로 Android 예제가 없다고 보고하지 않았다. 얼굴 표정은 얼굴 주인과 소리를 낸 사람의 동일성, 발성 동기, 배고픔/통증의 원인을 입증하지 않는다.

## 6. 추가 조사: 잡음 처리·소형 소리 분류

GitHub/HF 검색으로 후보를 발견한 뒤 공식 작성자 저장소·release/source를 확인했다. 검색 결과의 비공식 HF mirror를 공식 모델로 간주하지 않았다.

| 후보·공식 링크 | 실제 구현·공개 가중치 | 코드 / 모델 / 데이터 라이선스 | Android 근거와 제품 적용 조건 |
|---|---|---|---|
| [xiph/rnnoise](https://github.com/xiph/rnnoise), `main` | 48kHz C RNN denoiser. `download_model.sh`가 `model_version`으로 지정한 Xiph model 다운로드. **실제 model bytes 미확인** | 코드 BSD-3-Clause / 해당 model archive의 허락 범위 추가 확인 / speech·noise 데이터 권리 별도 | C/NDK 이식 가능성은 있으나 확인한 core repo에서 Android 앱 실증 미확인. 작은 아기 소리를 보존하는지 미검증 |
| [Rikorose/DeepFilterNet](https://github.com/Rikorose/DeepFilterNet), `main` | 48kHz Rust/PyTorch/ONNX. DFN2 ONNX tar **8,628,375**, DFN3 ONNX tar **7,983,136**, DFN3 ll tar **36,359,660 bytes** | README는 **all code** MIT 또는 Apache-2.0 dual license / 별도 weight 조건 미확인 / speech dataset별 권리 별도 | 확인한 공식 repo의 Android 경로 미확인. desktop/embedded ARM을 Android QA로 바꾸지 않음. infant preservation 미검증 |
| [Xiaobin-Rong/gtcrn](https://github.com/Xiaobin-Rong/gtcrn), `main` | 48.2K params·33MMAC/s README, DNS3/VCTK-DEMAND speech enhancement. 실제 `stream/onnx_models/gtcrn.onnx` **352,084**, simple **535,190 bytes** | 코드 MIT / 별도 model 허락의 범위 추가 확인 / DNS3·VCTK-DEMAND 각각 데이터 조건 | 작성자가 sherpa-onnx 지원 안내. Android는 아래 runtime source로 연결됨. infant preservation·our10/20초 latency 미검증 |
| [breizhn/DTLN](https://github.com/breizhn/DTLN), `master` | 16kHz dualLSTM denoise, DNS500h. ONNX1 **1,458,237**+2 **2,510,010**; floatTFLite1 **1,459,944**+2 **2,515,804**; quantTFLite1 **377,776**+2 **649,456 bytes** | 코드 MIT / pretrained weight grant 범위 추가 확인 / DNS 데이터별 license | TFLite artifact와 RPi timing 있음. 실제 공식 Android QA 미확인. 두 stage·상태·전처리를 함께 이식해야 함. 아기 소리 보존 미검증 |
| [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), `master` | offline audio runtime이며 babbling classifier 자체가 아님. `v1.13.8` Android AAR **50,129,134 bytes**는 runtime 크기. GTCRN denoise·tagging·VAD 등 지원 | 코드 Apache-2.0 / 선택 upstream weights별로 다름 / 각 데이터 권리 별도 | 공식 Android Kotlin **OfflineSpeechDenoiser/OnlineSpeechDenoiser** source 존재. Android 실행 경로 근거는 강하지만 선택 모델의 우리 소리 성능·AAR최적화·APK/RAM 비용은 별도 |
| [fschmid56/EfficientAT](https://github.com/fschmid56/EfficientAT), `main` | AudioSet527 **trained class head**를 포함한 MobileNet/DyMN. release `v0.0.1`: mn04_as **4,066,513**, dymn04_as **8,086,973**, mn10_as_fc **14,131,205 bytes** | 코드 MIT / 별도 weight grant·제3자 pretraining 조건 추가 확인 / AudioSet의 YouTube 원본 권리 별도 | 공식 Android 변환/실행 미확인. `mn04`약0.983M, `dymn04`약1.97M params README. 소형 일반 audio tagger 비교 우선순위 높음; cry 원인·의도 head 없음 |
| [microsoft/unilm/beats](https://github.com/microsoft/unilm/tree/master/beats), `master` | BEATs source와 공식 OneDrive pretrained 및 AudioSet-finetuned checkpoint 링크. **선택 파일 bytes 미확인** | repository 코드 MIT / 개별 checkpoint 이용허락 추가 확인 / AudioSet 데이터 권리 별도 | Python transformer, 공식 Android 미확인. pretrained는 representation, AudioSet-finetuned는 class head가 있으므로 전체를 encoder-only로 단정하지 않음. 무거운 연구/teacher 후보 |

공식 근거:

- [RNNoise model downloader](https://github.com/xiph/rnnoise/blob/main/download_model.sh).
- [DeepFilterNet 모델 경로](https://github.com/Rikorose/DeepFilterNet/tree/main/models), [코드 dual license 설명](https://github.com/Rikorose/DeepFilterNet#license).
- [GTCRN 실제 ONNX 경로](https://github.com/Xiaobin-Rong/gtcrn/tree/main/stream/onnx_models), [DTLN pretrained models](https://github.com/breizhn/DTLN/tree/master/pretrained_models).
- [sherpa-onnx Kotlin 소스 경로](https://github.com/k2-fsa/sherpa-onnx/tree/master/sherpa-onnx/kotlin-api), [v1.13.8 release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.8). AAR는 denoiser weight와 별도다.
- [EfficientAT release asset](https://github.com/fschmid56/EfficientAT/releases/tag/v0.0.1), [527개 라벨 CSV](https://github.com/fschmid56/EfficientAT/blob/main/metadata/class_labels_indices.csv), [MobileNet model source](https://github.com/fschmid56/EfficientAT/blob/main/models/mn/model.py). 라벨 CSV의 index6 `Babbling`, 17 `Baby laughter`, 23 `Baby cry, infant cry`를 확인했다. index70 `Hubbub, speech noise, speech babble`와 `Babbling`을 혼동하지 않는다.
- [BEATs 공식 README](https://github.com/microsoft/unilm/blob/master/beats/README.md), [BEATs source](https://github.com/microsoft/unilm/blob/master/beats/BEATs.py).

## 7. 적용·QA에서 지켜야 할 구분

### 잡음 제거는 먼저 발성 보존을 검증한다

성인 speech 강화 모델은 아기 squeal/growl/cry/non-canonical 소리를 잡음으로 줄일 수 있다. 모델이 가볍거나 성인 speech 지표가 좋아도 작은 아기 소리의 보존 근거가 되지 않는다. 원음의 immutable 보관과 원음 경로를 유지하고, 처리음은 비교용으로 검사해야 한다. 소리를 판정한 뒤 녹음이 원치 않게 변형되는 경로도 따로 확인한다.

후속 비교는 원음과 처리음의 같은 구간·같은 판정 정책으로 해야 한다. 최소한 약한 옹알이, cry/laugh/non-canonical, 성인 말, TV/음악, 마찰·생활 잡음, 무음, 원본의10초·20초를 포함한다. **임계값을 낮추는 것으로 통과를 만들지 않는다.** 발성 회복·승인 손실·비아기 추가 승인·처리시간·취소 후 다음 분석을 함께 기록한다.

### 10/20초 입력은 전체 구간을 덮는다

저장소의2/3/5초 학습 입력 또는 AudioSet10초 라벨은 우리 앱의20초 지원 증거가 아니다. model-specific window/hop/edge 처리·중첩·무음·꼬리 padding과 출력 집계를 명시하고, 선택한 구간의 모든 시간 범위가 분석됐는지 검사한다. 한 창의 점수·최대 점수만으로 특정 발성이 언제 있었는지, 전체 소리를 이해했는지 확정하지 않는다.

### 일반화·라벨·권리 확인을 실행 결과와 분리한다

1. **데이터 독립성:** 같은 아이·같은 원본·oversampling 복제본이 train/test에 걸치면 높은 점수가 일반화 근거가 되지 않는다. gagan notebook은 oversampling-before-split을 직접 확인했다. 다른 프로젝트에 같은 결함이 있다고 단정하지 않는다.
2. **head:** HF task tag·encoder 이름·config만으로 ready classifier를 선언하지 않는다. voc2vec 공급 모델은 pretraining architecture다. LittleBeats는 fine-tuned weight도 공개됐지만 선택 recipe·head·라벨 복원을 실제로 확인해야 한다. BEATs는 checkpoint 유형을 먼저 선택한다.
3. **라벨:** 다섯 cry 원인, canonical/non-canonical, broad AudioSet event, 표정7/8개는 서로 다른 과제다. 성능을 하나의 ‘아기 정확도’로 합산하지 않는다.
4. **권리:** VocalBaby의 MIT는 openSMILE 제한을 해제하지 않는다. foduucom의 Apache/MIT 충돌과 Omshinde의 badge/실제 LICENSE 부재를 해결하기 전 배포허락을 확정하지 않는다. 얼굴·가족 녹음·YouTube·private data 권리도 별도다.
5. **실행:** source 존재, ONNX/TFLite 존재, Android example 존재, 앱에 통합됨, S25실측 통과를 각각 나누어 기록한다. 이 문서는 앞의 artifact 확인까지만 수행했다.
6. **제품 계약:** 원음 upload·cloud API·external DB가 있는 sample을 그대로 넣지 않는다. 관찰된 소리·얼굴 단서만 표현하고 의도·진단·얼굴 주인·발성 동기 확정 문장을 만들지 않는다.

## 8. 남은 확인과 이 조사에서 수행하지 않은 작업

- EfficientAT/GTCRN의 고정 weight 권리 범위·전처리 계약·Android 변환 성공·실기기 비용은 다음 단계다. 현재 후보 간 우선순위는 artifact와 과제 적합성 기반이며 우리 앱의 성능 순위가 아니다.
- LittleBeats fine-tuned head의 binary tensor와 라벨 encoder 복원, generic OpenRAIL의 실제 상세 조건을 확인해야 한다. 큰 pretrained 파일을 받기 전에 필요한 inference files를 구분해야 한다.
- VocalBaby는 feature extractor의 상업 license 및 데이터 provenance 문제가 먼저다. `final_model/model_info.yaml`은 조회404였으며 실제 tree의 세 공개 pkl 파일을 근거로 삼았다.
- RNNoise 실제 model archive 크기, VocalMat 실제 LFS/OSF model 크기, BEATs 선택 OneDrive checkpoint 크기는 **미확인**이다. pointer나 README 파라미터 수로 임의 크기를 채우지 않았다.
- 공급404주소2개, case/path404와 별도 복구 후보를 구분했다. 추가 대안 발견은 원래 저장소의 소유·권리·신뢰성 회복을 뜻하지 않는다.
- 앱 코드·Gradle·모델 binary·기록·추론 임계값을 변경하지 않았다. 모델 다운로드·변환·실행·ADB·Play 제출·푸시는 수행하지 않았다. 적용과 QA 완료는 별도 실행 증거가 필요하다.

공식 조회 경로: [GitHub repository API 예시](https://api.github.com/repos/arunps12/VocalBaby), [GitHub release API 예시](https://api.github.com/repos/fschmid56/EfficientAT/releases/tags/v0.0.1), [HF voc2vec API](https://huggingface.co/api/models/alkiskoudounas/voc2vec), [HF LittleBeats API](https://huggingface.co/api/models/lijialudew/wav2vec_LittleBeats_LENA). 문서에 포함된 크기는 이 API의 blob/release/LFS 메타데이터와 선별 source를 대조한 조회일 기준이다.

### 재확인할 artifact 식별자

아래는 조회 API의 **Git blob SHA**이며 파일 SHA-256이 아니다. 후속 다운로드 시 별도 파일 SHA-256 및 고정 revision을 기록해야 한다.

| artifact | 조회 당시 Git blob SHA |
|---|---|
| GTCRN `stream/onnx_models/gtcrn.onnx` | `613bd97557556a296f578af0a0993da191bbf8d4` |
| GTCRN `stream/onnx_models/gtcrn_simple.onnx` | `015454eef64bd4328e6ff4946c231d4a4a632e93` |
| VocalBaby와 VisionInfantNet `xgb_egemaps_smote_optuna.pkl` | 양쪽 모두 `4e84c2f26094f113c66341e8a8599ecbeb484c0b` |
| 양쪽 `label_encoder.pkl` | `636b337f712094f0f445b42e791e06e79e4d856e` |
| 양쪽 `preprocessing.pkl` | `f282f2bc1ae047c44bdb7854c4d054d662fed35a` |
