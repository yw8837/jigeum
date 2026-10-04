# SpeechMaturity 분류 연구의 공개 artifact·권리·실행 경로

조사일: 2026-10-04 (KST)  
범위: arXiv `2506.08999v1`과 논문에 연결된 저자 GitHub → Hugging Face 모델 경로만 확인. 모델 다운로드·설치·추론은 하지 않았다.

## 판단

**저자 경로에 task-trained 분류기 checkpoint와 라벨 맵은 존재한다.** 이전 조사에서 미확인으로 남겼던 해당 연구의 학습된 분류 head를 이번에 확인했다. voc2vec는 별도 연구이므로 그 encoder의 공개 범위와 혼동하지 않는다. 다만 이 artifact를 상용 앱에 채택하거나 Android에서 실행할 수 있다고 확인한 것은 아니다. 모델/HF 저장소에 명확한 weight license가 없고, 구성요소인 upstream 모델은 `openrail`로 표시되어 있으며, 데이터셋 공개 문구와 별도로 적용 가능한 데이터 사용 license가 추적 경로에서 확인되지 않았다. 공식 artifact는 SpeechBrain/PyTorch 추론 recipe다. 공식 Android나 ONNX 배포는 없다.

**다음 단계:** 고정 recipe의 실제 구성과 입력 단위를 읽어 제한된 로컬 비교의 타당성을 먼저 검토한다. 이번 조사에서는 모델을 다운로드하거나 실행하지 않았다. 명시 조건을 확인하는 내부 연구 평가와 상용 앱의 모델 포함·재배포 결정을 구분하며, 라이선스 미확인을 모든 로컬 연구에 대한 금지나 신규 사용자 승인 의무로 바꾸지 않는다. 앱 채택에는 적용 가능한 weight 이용 조건을 확인해야 한다. 원 논문의 5-way audio 분류를 재현하더라도 그 결과를 아기 의도/감정, 한국어 성능, 영상 결합, Android 호환성으로 해석하지 않는다.

## 논문이 다룬 task와 공개 분류 head

논문은 발화 단위의 cry, laughter, mature/canonical, immature/non-canonical 분류를 다룬다. 아기 욕구·감정 분류는 아니다. 논문은 SpeechMaturity 242,004개 주석 발성을 설명하지만 학습/평가 사용량은 별도 부분집합이며, 데이터 건수/성능을 이 앱의 대상군 정확도로 옮길 수 없다.

논문 HTML은 저자 repo 경로 `spoglab-stanford/w2v2-pro-sm/.../W2V2-LL4300-Pro-SM`로 연결한다. 해당 recipe README는 `spoglab/w2v2-pro-sm` 모델 저장소에서 여러 checkpoint를 내려받도록 안내하고, 라벨을 직접 고정한다.

| label ID | recipe 표시명 | 의미 |
|---:|---|---|
| 0 | Junk / NONE | 기타·제외 |
| 1 | Non-Canonical | 비정형/미성숙 발성 |
| 2 | Canonical | 정형/성숙 발성 |
| 3 | Laughing | 웃음 |
| 4 | Crying | 울음 |

이는 논문의 4개 발성 label에 Junk/none을 더한 **5-way recipe map**이다. 공개 config는 5 출력 head, hidden layer 512, combined encoder dimension 1536을 선언한다. 고정 HF revision `9aac676c2b29c42d580b862a0294bc3807788847`의 파일 트리에 두 checkpoint 폴더의 `model.ckpt`(~3.17MB)와 `wav2vec2.ckpt`(~377.57MB), 별도 `checkpoint_best.pt`(~1.141GB), `wav2vec_asr.ckpt`(~377.57MB)가 있다. 두 폴더가 다른 학습 seed라는 근거는 확인하지 못했다. 파일 metadata상 학습 state 및 inference에 필요한 구성요소는 있으나, 한 파일짜리 포터블 inference bundle은 확인되지 않았다. `checkpoint_best.pt`/`wav2vec_asr.ckpt`는 repo README가 별도 upstream 모델에서 가져왔다고 명시한다.

**분류기와 encoder를 구분해야 한다.** 여기에는 head가 실제 있다. 동시에 recipe는 일반 wav2vec encoder 하나만 호출하는 단순 분류기가 아니라 LittleBeats encoder, ASR wav2vec 경로, temporal/weighted feature combination 및 MLP head를 조합한다. `wav2vec2` 표현만 가져오면 논문 분류기의 재현이 아니다.

## 권리·데이터 접근 확인

- 저자 GitHub 저장소 root의 GitHub license field는 비어 있고 root LICENSE가 없다. 하위 `speechbrain/`의 LICENSE는 SpeechBrain 프로젝트 파일이지 저자 task checkpoint의 weight license 증거로 쓰지 않는다.
- 저자 HF `spoglab/w2v2-pro-sm` 모델 카드에는 license field/tag가 없다. 고정 revision README는 SpeechMaturity corpus fine-tuning을 설명하지만 별도의 사용 허가 문구를 싣지 않는다.
- 같은 README가 encoder 구성요소를 다음 두 HF 모델에서 온 것으로 명시한다. 두 upstream 모델 카드 모두 `license: openrail`을 표시한다. 이 표기만으로 각 사용 제한 및 재배포 조건을 해소했다고 보지 않는다.
  - `lijialudew/wav2vec_LittleBeats_LENA`, revision `b3250219066482f571ef3868413b0b0f1ad92502`
  - `lijialudew/wav2vec_children_ASR`, revision `c0e5ca013970bd9332449c5219f0ad150c7753b9`
- 논문은 SpeechMaturity를 연구자에게 공개한다고 서술하지만, 연결된 저자 GitHub README와 저자 모델 카드에서는 dataset artifact URL 또는 dataset-specific license를 찾지 못했다. 논문이 인용하는 Speech Maturity Dataset 설명은 “under review”로 적혀 있다. 그러므로 “openly available” 문장을 상업 제품 학습·평가의 dataset permission으로 등치하지 않는다.
- 결과적으로 이 조사에서 **task weights·기초 weights의 상용 사용·재배포 조건을 명확하게 완결하지 못했다.** 공개 다운로드 가능성과 상업 이용 허가는 별개다. 별도 SpeechMaturity 데이터셋을 내려받아 재학습하는 작업은 이번 비교 범위에 없으며, 그 데이터 접근 조건을 이미 학습된 모델의 명시 조건과 동일하게 취급하지 않는다.

## Android·ONNX·비교 경로

- 저자 README가 명시하는 경로는 SpeechBrain 설치, Python environment, 여러 checkpoint 경로 설정 및 recipe 실행이다. Android, LiteRT, ONNX 파일/실행 지침은 저자 연결 저장소와 HF model tree에서 확인되지 않았다.
- ONNX 변환은 별도 기술 작업으로 시도할 수 있을 뿐, 현 source로는 성공 근거가 없다. 두 encoder, sequence aggregation 및 분류 head의 연산/가중치 변환과 Android delegate 실행을 각각 확인해야 한다. ONNX 경로가 이미 검증되어 있다고 쓰지 않는다.
- 저자 recipe에 따른 host Python 재현은 task 비교를 위한 원래 실행 경로로 가능해 보인다. 다만 실제 실행 의존성·입력 계약을 검증하지 않았고 환경 준비나 모델 실행도 하지 않았다. 고정 소스의 runtime 검토가 다음 단계다.
- 이 연구의 task는 오디오 발성 분류다. 영상은 입력에 없고, `canonical`은 발성 성숙도 범주이지 아기가 특정 말을 했다는 보장이나 욕구 추론이 아니다.

## 고정 1차 출처

1. [arXiv HTML v1](https://arxiv.org/html/2506.08999v1), *Employing self-supervised learning models for cross-linguistic child speech maturity classification*. 논문 본문과 References의 dataset 관련 항목, 그리고 저자 repo 링크 확인.
2. 저자 GitHub [repo commit `68850104737ab271cf25f47908070b6b73f06b89`](https://github.com/spoglab-stanford/w2v2-pro-sm/tree/68850104737ab271cf25f47908070b6b73f06b89), 특히 [recipe README](https://github.com/spoglab-stanford/w2v2-pro-sm/blob/68850104737ab271cf25f47908070b6b73f06b89/speechbrain/recipes/W2V2-LL4300-Pro-SM/Readme.md), [config](https://github.com/spoglab-stanford/w2v2-pro-sm/blob/68850104737ab271cf25f47908070b6b73f06b89/speechbrain/recipes/W2V2-LL4300-Pro-SM/hparams/train_1_w2v2_2dnn_WA_LL4300_asr_bbcor_concat.yaml), [training/inference script](https://github.com/spoglab-stanford/w2v2-pro-sm/blob/68850104737ab271cf25f47908070b6b73f06b89/speechbrain/recipes/W2V2-LL4300-Pro-SM/train_1_w2v2_WA_2dnn_combine_asr_features_bbcor.py). Recipe declares label mapping and upstream checkpoint provenance.
3. 저자 [Hugging Face model tree](https://huggingface.co/spoglab/w2v2-pro-sm/tree/9aac676c2b29c42d580b862a0294bc3807788847). Artifact path/file sizes fixed at revision `9aac676c2b29c42d580b862a0294bc3807788847`; README says folder is finetuned on SpeechMaturity and names the two upstream models. README states no task-weight license.
4. Upstream [LittleBeats-LENA model card](https://huggingface.co/lijialudew/wav2vec_LittleBeats_LENA/tree/b3250219066482f571ef3868413b0b0f1ad92502) and [children-ASR model card](https://huggingface.co/lijialudew/wav2vec_children_ASR/tree/c0e5ca013970bd9332449c5219f0ad150c7753b9). Both declare `openrail`; fixed revisions recorded above.

메타데이터 확인은 GitHub API와 Hugging Face 공식 Hub API의 고정 revision tree/file `size` 필드를 읽은 것이다. 실제 checkpoint를 내려받아 해시 검증하거나 로드한 것은 아니다. 파일 크기는 Hub에 공개된 개별 artifact byte 값이며 peak RAM이나 APK 추가 크기가 아니다.
