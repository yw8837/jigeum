# 영아 발성 전문 모델 후속 조사

2026-10-05 · 공개 원문·소스·모델 메타데이터 확인 · 모델/사용자 미디어 다운로드·실행 없음

**이번에 확인한 전문 후보 중 아가톡의 유료 Android 앱에 즉시 넣을 수 있는 새 모델은 확정하지 못했다.**
새 Whisper 연구와 BS-SSAMBA는 짧은 발성의 시간 구간을 모델링하는 관련 연구지만, 정확한 배포용
체크포인트·이용권리·모바일 경로가 부족하다. 아기 웃음 라벨도 없다. LittleBeats의 기존 공개
가중치는 실제 분류 head가 있으나 완전한 추론 계약과 권리 확인이 먼저다. 이는 전문 모델이
영원히 불가능하다는 뜻이나 현재 앱의 누락이 해결됐다는 뜻이 아니다.

기존 [공급 저장소 감사](20261005-infant-repos-audit.md), [논문 감사](20261005-infant-papers-audit.md),
[ChildVox/VTC 감사](20261004-additional-vocal-candidates.md)를 반복 실험으로 세지 않는다.
EfficientAT/GTCRN 실행 결과와 부모 작업자의 작은 일반 SED 모델 조사는 별도다.

## 새로 확인한 후보

| 후보 / 고정 원천 | 실제 출력·표본·분할 | 배포·권리·Android 판단 |
|---|---|---|
| **Whisper Structured Speaker Conditioning**, [arXiv2608.11587v1 전문](https://arxiv.org/html/2608.11587v1), 2026-08-12 | LittleBeats 52가족, **4–15개월**, 약17h; 가족 분리 train37/val5/test10. 30초 입력, Whisper-large-v2 encoder+LoRA, 5프레임을 합치는 projector와 tier별 시간축 분류. CHN=`INACTIVE/BAB/CRY/FUS`; FAN의 `LAU`는 **성인 웃음**. 여러 tier 동시 활동 허용 | 전문에 usable code/weight 링크 없음. 저자 공개 GitHub 저장소 목록·이름 검색에서도 이 모델의 정확한 checkpoint 미확보. 논문 CC BY4.0은 미공개 weight/가족 음원 사용권이 아니다. LoRA는 큰 backbone을 없애지 않으며 전체 bytes/ARM latency 미측정. 16–18개월·아기 웃음·휴대폰 거리 도메인 부족 |
| **Band-Split SSAMBA**, [ISCA 저자 PDF](https://www.isca-archive.org/interspeech_2025/fan25_interspeech.pdf), DOI10.21437/Interspeech.2025-504 | 61가족, **1–18개월** 평균9.43; 가족 분리51/5/5, 라벨 약14h. 10초 입력의 **중앙0.1초**를 분류. 10라벨 `SIL/CXN/F-ADS/F-CDS/M-ADS/M-CDS/FM-SNG/C-BAB/C-CRY/C-FUS`. 아기 웃음 없음. 24층 bidirectional Mamba,768dim, **102M params** | 논문 전문에 이 specialist의 배포 checkpoint/코드 링크 없음. `BS-SSAMBA`, `Band-Split SSAMBA` GitHub repo 검색은 비어 있었음. 일반 SSAMBA의 공개 weight를 이 fine-tuned 영아 모델로 대체할 수 없다. 상업 weight grant, exact bytes/전처리 export/Android 미확인. 작은 상태공간 연산이라는 설명만으로 작은 앱 모델이라고 부르지 않음 |
| **W2V2 LittleBeats Long Transformer**, [저자 repo](https://github.com/xulinfan/w2v_LB_Long_Transformer/tree/a36fe68541db995c244a8133f9187e8ee7875b2e) | 새 `train_tf_long.py`는 모든층 특징을 시간축과 합친 뒤 CLS token의 **샘플당 단일 5-way** 결과. `SIL+CXN=0`, female adult1,male adult2,babble3,**cry+fuss4**; 아기 웃음 없음. 소스 자체가 frame probability sequence를 반환하지 않음 | [recipe](https://github.com/xulinfan/w2v_LB_Long_Transformer/blob/a36fe68541db995c244a8133f9187e8ee7875b2e/speechbrain/recipes/wav2vec_LittleBeats/Readme.md)는 실제 [공개 Drive 폴더](https://drive.google.com/drive/folders/1jlOOMWC_XFh5P3-mEIgTVDLgyAtKsdee?usp=sharing)를 연결. 공개 페이지에서 `model.ckpt` **28.2MB**, `wav2vec2.ckpt` **360.1MB** 확인; UI 반올림 값이며 exact bytes/hash 미확인. repo 최상위 license 없음, 내장 SpeechBrain은 Apache2.0; **이 checkpoint 별도 허락 없음**. 논문의 0.1초 central-label sliding 평가와 native frame head를 구분해야 함. Python/Fairseq/SpeechBrain, Android 없음 |
| **HindiBabyNet HuBERT**, [card](https://huggingface.co/arunps/hubert-home-hindibabynet-ssl/blob/1d827722cc95f059cef06bfc7a949ecfcf2f60a4/README.md), [API](https://huggingface.co/api/models/arunps/hubert-home-hindibabynet-ssl/revision/1d827722cc95f059cef06bfc7a949ecfcf2f60a4?blobs=true) | `HubertModel` 특징 encoder,12층768dim, 약94.7M; **발성 분류 head/id2label 없음**. card는 Hindi 가정 녹음308.45h train99files/dev37.25h10files, participant ID 분리라고 설명. 월령 분포·가족 manifest·독립 test 없고 표준 benchmark 미평가를 명시 | MIT metadata는 확인, 데이터는 GDPR/윤리로 비공개. `model.safetensors` **377,510,584 bytes** 전체 encoder이며 작은 delta 아님. 공개 card의 데이터 권리 주장과 실제 consent/재배포 provenance를 구분. 새 head를 학습할 독립 합법 자료·대조 시험이 필요하므로 즉시 detector 후보 아님. ONNX/export/Android 결과 없음 |
| **HindiBabyNet Wav2Vec2**, [card](https://huggingface.co/arunps/wav2vec2-home-hindibabynet-ssl/blob/979186836e9c6ff2b2b69dda4849ddbb3ad68bb2/README.md), [API](https://huggingface.co/api/models/arunps/wav2vec2-home-hindibabynet-ssl/revision/979186836e9c6ff2b2b69dda4849ddbb3ad68bb2?blobs=true) | `Wav2Vec2ForPreTraining`, 12층768dim, 약95M. 346h111files, train99/dev12,participant 분리라는 card 주장. 8초 SSL crop·RMS.001 이하 crop 재선택은 사전학습 절차이며 앱 무음 기준으로 이식할 근거 아님 | MIT metadata, **380,204,696 bytes**, 발성 head 없음. 비공개 corpus, 월령·독립 검출 성능·ARM 미확인. HuBERT와 같은 자료 계열이며 두 개의 독립 영아 검증으로 세지 않음 |

## 새 논문의 수치를 적용할 때 필요한 구분

- Whisper paper Table2의 **74.88 Macro-F1**은 CHN/FAN/MAN/CXN의 평균이며 각 tier의
  `INACTIVE`도 포함한다. 아기 CHN은 **69.13**, W2V-LB 비교값 **68.72**로 차이0.41%p다.
  큰 개선은 adult tier에 있다. CHN macro-F1을 babble recall이나 6–18개월 앱 정확도로 바꾸지 않는다.
  val kappa로 checkpoint를 선택했고, 미지 test family offset은0이다. TTA는 test 음성으로
  offset만5epochs 조정한 별도 결과이며 신규 원음 고정 평가와 구분한다. SSL/Whisper 사전학습에
  어떤 test 가족이 있었는지는 실제 manifest가 없어 미확인이다.
- BS-SSAMBA Table2는 accuracy.825/macro-F1.609/kappa.663 대 W2V-LB.814/.599/.655다.
  Fig2의 babble 행은66.24%가 babble로,17.02%가 SIL로,12.48%가 fuss로 분류됐다.
  논문도 모든 발성을 완전히 회복하지 못했다. 가족 단위 supervised split은 명시돼 있지만
  SSL1100h에서 test 가족/녹음이 배제됐는지 전문에 확인되지 않는다. best kappa checkpoint의
  선택 대상 split도 해당 문장만으로 단정하지 않는다. 독립 test 가족은 **5**이며 0.1초 프레임을
  독립 아기 수로 세지 않는다.
- 두 연구는 infant-worn 녹음 도메인이다. 화자 거리가 adult 분류의 단서가 되며, 손에 든
  휴대폰·TV·화면 밖 아기에서 같은 분포를 보장하지 않는다. `CHN`은 화면 얼굴과 연결된 신원
  또는 욕구의 정답이 아니다. 욕구·의도 생성 모델로 해석하지 않는다.

## LittleBeats 기존 release의 추론 계약을 더 확인한 결과

HF card가 직접 연결하는 현재 원천은 [jialuli3/wav2vec_LittleBeats_LENA,
commit688114b8cd774b8a2937f07b4e0114d9f7c9c8ce](https://github.com/jialuli3/wav2vec_LittleBeats_LENA/tree/688114b8cd774b8a2937f07b4e0114d9f7c9c8ce)다.
이전 감사의 `jialuli3/speechbrain` branch와 구분해 정확한 recipe를 고정할 수 있다.

1. [고정 recipe](https://github.com/jialuli3/wav2vec_LittleBeats_LENA/blob/688114b8cd774b8a2937f07b4e0114d9f7c9c8ce/speechbrain/recipes/wav2vec_LittleBeats/Readme.md),
   [weighted-average 설정](https://github.com/jialuli3/wav2vec_LittleBeats_LENA/blob/688114b8cd774b8a2937f07b4e0114d9f7c9c8ce/speechbrain/recipes/wav2vec_LittleBeats/hparams/hparams_LL_4300_WA.yaml),
   [label source](https://github.com/jialuli3/wav2vec_LittleBeats_LENA/blob/688114b8cd774b8a2937f07b4e0114d9f7c9c8ce/speechbrain/recipes/wav2vec_LittleBeats/scripts/train_WA_3dnn.py)를 확인했다.
   화자5-way/영아3-way/성인4-way DNN,768→384,mean pooling이다. 소스 영아 index는
   **CRY0/FUS1/BAB2**, 성인 **CDS0/ADS1/LAU2/SNG3**다. 따라서 `LAU`를 아기 웃음으로 연결하면 오류다.
2. [HF 고정 tree/API](https://huggingface.co/api/models/lijialudew/wav2vec_LittleBeats_LENA/revision/b3250219066482f571ef3868413b0b0f1ad92502?blobs=true)의
   `LL_4300_fine_tuned/save/CKPT+2022-11-26+14-06-17+00/model.ckpt`는 **3,588,884 bytes**,
   `wav2vec2.ckpt`는 **377,568,539 bytes**다. head만3.6MB이지 전체 모바일 모델이 아니다.
   LFS SHA256는 각각 `244494e389ff1d311bf6a48f747dd0051f4b3b6463d3ac117778a0e95d5cef20`,
   `e6bd8393cc7ba323b94213caacea702db6e7638f09cfa7fc54d7e2396d03f018`.
   공개 메타데이터이며 내려받아 해시한 결과는 아니다.
3. 같은 tree에는 release별 **hyperparams.yaml·label encoder·inference entrypoint가 없다**.
   [Fairseq wrapper](https://github.com/jialuli3/wav2vec_LittleBeats_LENA/blob/688114b8cd774b8a2937f07b4e0114d9f7c9c8ce/speechbrain/speechbrain/lobes/models/fairseq_wav2vec.py)는
   먼저 Fairseq pretrained architecture를 만들고 SB parameters를 `strict=False`로 로드한다.
   HF quickstart도 encoder 특징 추출만 보여준다. 1.14GB pretrained 파일은 head를 포함한
   최종 추론 크기와 다르지만 현재 로더 초기화에는 pretrained config 복원이 필요하다.
   어떤4개 augmentation/embedding recipe가 공개 `model.ckpt`와 정확히 일치하는지 tensor 검사 전
   확정하지 않는다. 맞지 않는 키를 조용히 건너뛴 상태로 성능 비교하면 안 된다.
4. [HF 고정 card](https://huggingface.co/lijialudew/wav2vec_LittleBeats_LENA/blob/b3250219066482f571ef3868413b0b0f1ad92502/README.md)는 generic `openrail` metadata만 있고
   특정 OpenRAIL license 전문/버전 파일은 tree에서 확인되지 않았다. 내장 SpeechBrain 코드의
   Apache2.0이 별도 학습 weight나 가족 음원의 허락을 자동 증명하지 않는다. 기존 논문의 가족
   자료는 연구팀 밖 공유 동의가 없다고 서술한다. **상업 포함·distillation 권리도 미확정**이다.

같은 원천의 [BabbleCor recipe](https://github.com/jialuli3/wav2vec_LittleBeats_LENA/blob/688114b8cd774b8a2937f07b4e0114d9f7c9c8ce/speechbrain/recipes/BabbleCor/Readme.md)는
2–36개월52아이/약11k clips의 canonical/noncanonical/laugh/cry/junk를 다룬다. 연결 HF
`lijialudew/wav2vec_children_ASR`는 **phoneme recognition** 모델이며 그 자체를 공개 BabbleCor
5-way classifier로 보고하지 않는다. 이 저자의 공개 HF 목록에서 별도 BabbleCor classifier release는
찾지 못했다. 이는 모든 계정/비공개 모델에 존재하지 않는다는 선언이 아니다.

## BabyHuBERT의 권리 판단을 갱신할 새 증거

기존 조사에서 `coml/BabyHuBERT`의 권리가 미확정이었다. 이번 모델 API 검색은 현재 다음
배포들을 반환했다. gated 파일을 우회하거나 동의·등록하지 않았다.

| 공개 metadata 원천 | 이번에 확인한 것 | 처리 |
|---|---|---|
| [MarvinLvn/BabyHuBERT, revision3736078db7fc4703222e4bb52da9f59871425243 API](https://huggingface.co/api/models/MarvinLvn/BabyHuBERT/revision/3736078db7fc4703222e4bb52da9f59871425243?blobs=true) | `license:other`,BabyHuBERT-license-v1. 공개 `extra_gated_prompt`가 **commercial use 금지**, surveillance 금지,파생 모델에 동일 조건 승계를 명시. `.pt`377,568,018bytes/`.ckpt`1,136,670,109bytes. card/raw 본문은 인증 요구 | 다운로드·추론·학습·상업 탑재 대상에서 보류. full license를 읽었다고 주장하지 않으며 공개 금지 고지만으로 현재 제품 사용권이 확보되지 않음을 판단 |
| [labicquette/BabyHuBERT, revisiona0378eb341532ac169262a7d90f68ee1c77b80f7 API](https://huggingface.co/api/models/labicquette/BabyHuBERT/revision/a0378eb341532ac169262a7d90f68ee1c77b80f7?blobs=true) | 다른 bespoke BabyHuBERT-license-v1.0,gated. 결과물의 기여자2명 이상 mailing list 가입·결과 통지 조건. 현재 공개 siblings는README/LICENSE뿐 | 조건 수락·메일링리스트 가입·연락 없이 중단. 이 배포의 조건을 Marvin 배포와 임의로 합치거나 더 느슨한 버전으로 선택하지 않음 |
| [Abdoul27/babyhubert-base-clean, revision39fcfdced1508bc828060d64fb056241a4a86235](https://huggingface.co/Abdoul27/babyhubert-base-clean/blob/39fcfdced1508bc828060d64fb056241a4a86235/README.md) | 공개 full encoder377,507,424bytes지만 card는 자동 template이며 license/source/evaluation 모두 미기입 | gated 원본의 상업 금지를 해소하는 권리 근거로 사용하지 않음. mirror에서 weights를 받지 않음 |

VTC-2 고정 task checkpoint의 provenance/권리에는 별도 추적이 필요하다. 위 새 고지 하나로
모든 과거 VTC weight가 동일 허락이라고 단정하지 않지만, BabyHuBERT 파생 경로를 상업 적합으로
전제하는 근거는 더 약해졌다. ChildVox의 명시적 `No commercial use`는 기존 보류를 유지한다.

## 다음 단계

**즉시 실행할 영아 전문 후보로 권리가 확인된 완성 detector는 확보하지 못했다.** LittleBeats를
후속 비교 대상으로 살리려면 먼저 공개된 특정 weight의 이용 허락과 정확한 recipe 대응이
확인돼야 한다. 새 source와 Drive file 존재만으로 다운로드·앱 탑재를 진행할 수 없다.

권리 확인 뒤의 비교 계약은 원음의 모든10/20초 시간축에서 babble/cry/laugh별 event recall,
성인/TV 오인,overlap,보류율,경계 오차를 평가하고 기존 승인 회귀를 보존하는 것이다.
head/encoder/export parity와 중급·저가 ARM 지연/RAM/발열 실측이 필요하다. 휴대폰 사용자의
원본·파생 미디어·embedding을 외부 API로 보내는 경로는 추가하지 않는다.

이 문서는 전문 후보 조사만 완료했다. 권리가 확인된 작은 **일반 시간축 SED** 후보의 실험은
별도 조사 결과를 따른다. 새 모델이 영아 의도 번역을 수행하거나 알려진 누락을 회복했다는
성능 주장은 하지 않는다.

조사 경로: agent-reach Exa 발견 → 공식 GitHub API·고정 소스,HF 공개 API/card/config,
arXiv HTML·ISCA PDF 원문,저자 연결 Drive 공개 file list. 외부 API 검색어에는 사적 미디어나
파생 정보가 없었다. Agent Reach1.5.0 최신 확인; 업데이트·저자 연락·약관 수락 없음.
