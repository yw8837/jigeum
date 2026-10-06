# 다음 영아 발성 표현: 새 checkpoint의 계약과 합법적 양성 확장 경로

확인일 **2026-10-07 KST**. 공개 GitHub/Hugging Face metadata, README, config, license, source와 기존 공개 manifest만 읽었다. 원음·preview body·가중치 취득·디코딩·모델 실행·학습·Git·빌드·ADB·사용자 사적 자료 접근·외부 연락을 하지 않았다. 이 문서만 작성했다. VTC 권리 문의는 **미발송·승인 대기**다.

## 1. 결론

**옹알이까지 명시한 새 소형 specialist artifact는 찾았지만, 상업 제품에 넣을 수 있는 실행 계약은 완성되지 않았다.** UIUC infant-vocalize의 4way/5way head는 각각 813,388B/813,904B다. raw waveform encoder가 아니라 openSMILE 특징에 붙는 FC classifier이며, 정확한 feature/normalizer/weight 대응 및 원천 데이터 허락이 부족하다. repository GPL3와 openSMILE의 상업 제품 제한도 분리해 해결해야 한다.

BYOL-A의 512d 파일은 2,410,576B로 작지만 공식 계약이 내부 평가 전용이며 배포·수정을 제한한다. 이를 상업 학습 초기값이나 앱 weight로 권하지 않는다. LEAF는 Apache 2.0의 별도 학습 가능한 frontend 소스이며 영아 checkpoint가 아니다. 기존 작은 head의 영아 validation 실패를 해결할 근거로 보고하지 않는다.

현재 가장 짧은 다음 경로는 **새 checkpoint 실행을 시작하기 전에 공개 양성 발성의 출처·사건 검수를 늘리는 것**이다. 정확한 6~18개월 설명이 있는 기존 dev46은 사실상 5업로더뿐이고, public33 겹침을 제거하면 train 쪽에 남는 것은 Fugazi80/levinj의 2clip뿐이다. 이미 validation에 쓰인 Corsica_S/tim.kahn을 train으로 옮기면 해결되지 않는다. 새 source 두 곳의 검수 후보와 기존 eval 유지 후보를 5절에 고정한다. 새로운 6~18개월 옹알이 family가 확보됐다고 주장하지 않는다.

## 2. 새 specialist: UIUC infant-vocalize

공식 [저장소](https://github.com/uiuc-sst/infant-vocalize), source/tree revision **ed1115642231b334c09c345d98b1b3d59d94fe29**. [고정 tree API](https://api.github.com/repos/uiuc-sst/infant-vocalize/git/trees/ed1115642231b334c09c345d98b1b3d59d94fe29?recursive=1)의 blob size다. 파일 body나 tensor를 읽지 않았다.

| 실제 공개 파일 | bytes | 공식 task |
|---|---:|---|
| NN/model/lena_idp_4way.pt | 813,388 | CRY / FUS / LAU / BAB |
| NN/model/lena_idp_5way.pt | 813,904 | 위 4종 + SCR |
| NN/model/idp_mom.pt | 814,420 | mother vocalization; infant head 아님 |

[NN README](https://github.com/uiuc-sst/infant-vocalize/blob/ed1115642231b334c09c345d98b1b3d59d94fe29/NN/README.txt)가 위 파일과 task를 직접 연결한다. [README_JL](https://github.com/uiuc-sst/infant-vocalize/blob/ed1115642231b334c09c345d98b1b3d59d94fe29/README_JL.md)의 5way 순서는 CRY0/FUS1/LAU2/BAB3/SCR4다. 현 artifact의 실제 tensor label order를 실행으로 입증한 것은 아니다. root README의 hic 라벨과 이 5way의 SCR은 구분한다.

### 실제 입력과 모바일 blocker

- [NN/ser.py](https://github.com/uiuc-sst/infant-vocalize/blob/ed1115642231b334c09c345d98b1b3d59d94fe29/NN/ser.py)는 waveform을 받지 않고 H5 feature/label을 읽는다. input_size는 H5 첫 행에서 계산하며 hidden_size128의 FC를 만든다.
- [NN/fc.py](https://github.com/uiuc-sst/infant-vocalize/blob/ed1115642231b334c09c345d98b1b3d59d94fe29/NN/fc.py)는 Linear→LeakyReLU→Linear→LeakyReLU다. constructor 기본201→5와 runner의 hidden128을 checkpoint의 실제 shape로 단정하지 않는다.
- [특징 추출 source](https://github.com/uiuc-sst/infant-vocalize/blob/ed1115642231b334c09c345d98b1b3d59d94fe29/opensmile_feature_extraction.py)는 openSMILE2.3.0의 IS09_emotion.conf를 부른다. 다른 README/runner는 emobase/combined/Fisher1000 normalized feature를 설명한다. **공개된 lena_idp_4way/5way에 정확히 어느 feature 순서·통계·선택 index가 대응하는지는 미확정**이다.
- 학습 폴더는 private IDP/LENA 경로다. exact infant age, source/family split, training 원음의 commercial/weight redistribution grant를 확인하지 못했다.
- FC constructor가 stack=false에도 cuda device의 empty tensor를 만들므로 공식 source 그대로 CPU/Android 가능하다고 보고하지 않는다. ONNX/TFLite export, frontend parity, Android RAM/latency/thermal 수치는 없다.
- 별도 raw vocal detector나 adult/other reject head가 없다. BAB 분류를 곧바로 영아 존재·욕구·동기로 바꾸지 않는다.

### 권리 판단

[고정 repository LICENSE](https://github.com/uiuc-sst/infant-vocalize/blob/ed1115642231b334c09c345d98b1b3d59d94fe29/LICENSE)는 **GPL3**다. GPL을 비상업 금지로 오해하지 않는다. 다만 proprietary Android 앱과 결합·배포할 때 source 제공 및 결합 범위 조건을 판단해야 하며 별도 weight grant는 읽은 자료에 없다. [openSMILE 공식 licensing](https://github.com/audeering/opensmile#licensing)은 open-source판을 어떤 commercial product에도 사용할 수 없고 제품 개발에는 commercial development license가 필요하다고 명시한다. repository GPL3가 이 의존성 제한을 해제하지 않는다.

**현 판정: 작고 task는 적합하지만 runnable/commercial 후보 승인 보류.** 원 weight에 MFCC나 새로운 frontend를 임의 대입하면 계약이 다른 모델이 된다. 허락 불명 자료나 제한된 weight를 distillation해서 제한을 우회하는 경로도 제안하지 않는다.

## 3. 새로 좁혀 읽은 다른 공개 artifact

| 후보·고정 revision | 실제 공개 크기·입력 | 결정 근거 |
|---|---|---|
| [BYOL-A](https://github.com/nttcslab/byol-a/tree/3e65c9283498d06915e6dbcff3445307d8d3e930) | 512d encoder2,410,576B; 1024d6,608,976B; 2048d21,297,232B; v2 2048d25,340,689B. 16k mono→log mel64, FFT/window1024, hop160, dataset mean/std normalization. unit0.95s 설정 | generic audio SSL. [LICENSE](https://github.com/nttcslab/byol-a/blob/3e65c9283498d06915e6dbcff3445307d8d3e930/LICENSE) 1조 내부 testing/analyzing/evaluating만 허용,4조 배포·수정 제한. 작은 상업 영아 representation으로 제외 |
| [owlgebra-ai/babycry](https://huggingface.co/owlgebra-ai/babycry/tree/9ef32cc80b70053831ac024dbb3b674942a6c469) | model.safetensors2,219,772B; default head single_logreg6,428B. native mono float→2s/1s hop pooled AST16k/w2v16k/CLAP48k embeddings; lazy encoder 세 개 별도 다운로드 | positive/distress task; actual shipped train-on-all에 ReCANVo+DonateACry+ESC50. [모델 card](https://huggingface.co/owlgebra-ai/babycry/blob/9ef32cc80b70053831ac024dbb3b674942a6c469/README.md)는 대부분6~23세와 영아 transfer 한계를 직접 명시. mixed-per-source와 ESC50 NC로 전체 weight의 상업 허락 미확보. 작은 head 크기를 총 모델 크기로 세지 않음 |
| [uild42/infant_mamba](https://huggingface.co/uild42/infant_mamba/tree/35fc1fbc5bf1eb2b6adf8abec596f866db331cc3) | safetensors214,814,120B, bin138,870,101B; config d_model768/n_layer10/vocab50280, tokenizer 존재 | audio input/head/infant task/card/license 없음. 이름만으로 영아 음성 모델이라고 분류하지 않고 제외 |

owlgebra [dataset card 고정 revision](https://huggingface.co/datasets/owlgebra-ai/babycry/blob/7fdf2f0efbb0af1113cf220378ccb2763bdac562/README.md)은 ReCANVo7,077/DonateACry457/ESC5040, source 별 CC-BY4/ODbL1/CC-BY-NC3라고 설명한다. 해당 card의 NC40 제거 제안은 **이미 섞여 학습된 shipped weight의 허락을 소급 변경하지 않는다**. ReCANVo를 영아 옹알이 source로 확장하지 않는다. 이 표는 metadata/card의 근거이며 데이터 shards는 읽지 않았다.

좁은 official HF 검색 babbling/cooing 및 infant/baby audio task, GitHub infant vocalization/BabbleCor를 조회했다. CountInfantSyllables와 Interspeech2022 vocalization-space는 source 프로젝트였으며 읽은 tree에 대응 ready infant representation weight를 확인하지 못했다. 기존 EfficientAT를 다시 조사하다 같은 고정 SHA와 이미 실행한 계열임을 확인했으므로 새 대안/재실험으로 올리지 않았다. 기존 AST/EfficientSED/PANNs/YAMNet head/VTC 실험을 반복하지 않았다. **확인 범위에서 suitable ready artifact 부재**이며 전 세계 부재 결론은 아니다.

## 4. 다른 representation의 합법적 source 경로: LEAF

공식 [google-research/leaf-audio](https://github.com/google-research/leaf-audio/tree/b130481b7ddeffceaa265eba4e49a49ed7352d7e), revision **b130481b7ddeffceaa265eba4e49a49ed7352d7e**. [LICENSE](https://github.com/google-research/leaf-audio/blob/b130481b7ddeffceaa265eba4e49a49ed7352d7e/LICENSE)와 각 source header Apache2다. 소스를 조건에 맞게 수정·배포하는 경로가 있으며 원음 권리는 별개다. 읽은 공식 tree/README에 **exact pretrained infant checkpoint는 없다**.

[frontend.py](https://github.com/google-research/leaf-audio/blob/b130481b7ddeffceaa265eba4e49a49ed7352d7e/leaf_audio/frontend.py)의 default contract:

- 16kHz mono float [-1,1], shape batch×samples 또는 batch×samples×1.
- 40 Gabor filter,25ms kernel 실제401 samples,10ms stride160; squared modulus→Gaussian lowpass→learnable per-channel PCEN. 출력 batch×time_frames×40.
- default preemp/instance normalization/specaugment는 false. mel 근사 초기화는 infant supervision이 아니다.
- source상 학습 scalars: Gabor80+Gaussian40+PCEN alpha/delta/root120+EMA40=**280개, FP32 scalar만1,120B**. 실제 serialized model/expanded convolution constants/classifier/state/runtime 크기를 뜻하지 않는다.
- stride1의 80 real/imag convolution과401 tap 때문에 단순 dense 산술은 **16,000×80×401≈0.513B MAC/s**다. 이는 source 구조의 예상 비용이며 기기 측정이 아니다. PCEN은 tf.scan recurrence를 사용한다. TFLite builtins/streaming state/parity의 추가 검증이 필요하다.

**우선 실행 권고는 보류**다. 35 weak clips와 소수 source로 frontend+encoder를 처음부터 학습해 유의미한 일반화가 나온다고 보장할 수 없다. source 조건이 명확하다는 점과 영아 품질/모바일 준비 완료는 별개다. 자료가 늘어난 뒤 기존 고정 embedding head와 비교하는 task learned frontend 실험으로 남긴다. 기존 제품 YAMNet의 pretrained input에 LEAF output을 대체하는 호환 변경은 아니다.

## 5. 지금 확보 경로를 고정할 수 있는 양성 source

기준은 [기존 source-audit.json](../../work/infant-head-20261007/source-audit.json) 233행과 [positive46 분할 계약](20261007-permissive-infant-voice-next.md)이다. description/raw 라벨은 청취 정답이 아니다. 6~18개월은 업로더의 설명상 월령이며 독립 검증된 나이가 아니다.

### 5.1 정확 월령의 dev46을 늘리는 것만으로 source 독립성이 생기지 않는다

| source | ID·역할 | 확장 판단 |
|---|---|---|
| NoiseCollector 9개월 | train 후보 Babbling20:113220,113221,113223,113224,113225,113226,113229,113236,113237,113238,113239,113240,113241,113242,113243,113249,113253,113255,113256,113257 | public33 source 겹침. 새17clip은 사건 다양성 검수용으로는 가능하지만 새 독립source/일반화test로 세지 않음 |
| reinsamba 9개월 | cry46963,46964; laughter47370,47371,47372,47373,46966,46962 | public33 source 겹침.47370은 exact regression clip으로 계속 제외. 기존 held/test에서 train 재할당 금지 |
| Fugazi80 10개월 / levinj 9개월 | 377237 CC0 /8157 archived BY3, current audited BY4 | public33 겹침을 제거한 기존 train 쪽 양성은 두 source/두clip. 이미 작은 subset에 포함됨. 새source 확보로 보고하지 않음 |
| Corsica_S→tim.kahn 15개월 | held16: Babbling18274,18277,18278,18279,18280,18285,18286,18288,18289,18292; cry18276,18284,18291; laughter18273,18275,18281 | 전 source held 유지. 이미 validation4를 본 상태이므로 나머지12도 blind test로 부르지 않음. calibration에 쓰면 validation만 주장 |

**dev46에서 public33 겹침28clip을 제외하면 18clip/3source다. 이 중16clip/1source는 이미 held이므로 새 train6~18개월 양성을 늘릴 여지가 없다.** 현재 나머지 dev48/unknown-month를 target-age ground truth로 채우는 것도 허용하지 않는다.

### 5.2 기존 233행의 새 dev source: 월령 미상으로 명시하고 검수부터

| 우선순위·source | 실제 후보 ID | raw metadata·권리 | 역할 제안 |
|---|---|---|---|
| 1 / FunWithSound | babble형416677 Ah-Bah-Bah,416681 Gee-Gee; grunt416635; laughter416702 | 전체 source48행=Babbling47/laughter1. small boy toddler/baby 설명, 월령 미상. archived CC0; current CC0 확인은416635/416636뿐, 나머지3 current 재확인 필요 | 한 새 source로 묶고 최대4clip 청취 검수. clean natural child인지·성인 연기/편집 여부·event 구간 확인 전 승인 금지. 나머지44를 다른 split으로 나누지 않음 |
| 2 / Stevious42 | 259609 Baby Babble and crinkle | Babbling+cry raw 동시 라벨, young baby 설명, 월령 미상. archived/current BY3 | 한 새source의 mixed bag 후보. crinkle을 infant event로 라벨링하지 않음. 같은source259610은 baby-monitor라 channel confound 별도 flag |
| 이후 / wjoojoo | 254454,254455,254465,254471 | marvie babbling/saying dada, 월령·동일 아기 관계 미상; archived BY3, current 미확인 | 원문/현재 권리/preview 경로 재검수 대기. 이미 검증된6~18개월 신규source라고 세지 않음 |

FunWithSound와 Stevious42는 기존 positive46/public33/binary23 양성 source와 이름 기준으로 다르다. 이는 metadata uploader 독립일 뿐 speaker/family 독립 입증이 아니다. 현재 페이지 확인은 5.4절이며 음성 body를 취득하지 않았다.

### 5.3 정확 월령의 eval source는 기존 보류를 유지

| source·월령 | 정확 ID·archived 원음 권리 | 유지 역할 |
|---|---|---|
| BoilingSand16개월 |50665,50666 /BY3 |eval 보류;50665만 current BY3 확인. 두clip은 한source |
| amccoy71115개월 |388407 /CC0 |eval 보류/current 미확인 |
| carameans12개월 |135185 /CC0 |eval 보류/current 미확인; 쌍둥이 목욕, 복수아기/배경 혼합 |
| soundscalpel.com15개월 |110392 /BY3 |eval 보류/current 미확인; stock 출처·원저작 권리 검수 필요 |

이 다섯clip은 **옹알이 source 확장이 아니다**. 울음2/웃음3이며 parent가 보류해 온 source를 학습 쪽으로 이동시키지 않는다. 해당 original/current license, raw bytes/hash, 실제 사건, 원작자·title·URL·license·편집 사항 attribution을 취득 담당자가 고정해야 한다. Freesound CC FAQ와 FSD annotation CC-BY4는 [기존 허락 감사](20261007-permissive-infant-voice-next.md)의 계약을 유지한다.

### 5.4 이번 current page 확인

공식 Freesound HTML의 license href와 data-mp3만 읽었다. 추가416677/416681/416702 current HTML 병렬 조회는 timeout으로 정상 결과를 확보하지 못해 current 검증으로 세지 않았다. 아래 preview URL은 공식 페이지 제공값이며 원음·preview body/HEAD/PCM 검사는 하지 않았다. current/archived 권리를 섞지 않는다.

| ID·페이지 | current 권리 | HTML bytes·SHA256 | 공식 preview |
|---|---|---|---|
| [416635](https://freesound.org/people/FunWithSound/sounds/416635/) |CC0|43,284 /97935d03739bd3d976c5b5486071b03eab0d95946503589de4ad61a604f33088|https://cdn.freesound.org/previews/416/416635_6456158-lq.mp3|
| [416636](https://freesound.org/people/FunWithSound/sounds/416636/) |CC0|43,260 /c5eea78749feb0b892b18cbb044ef9c323ceb161b77ff52f6a00512b474559cb|https://cdn.freesound.org/previews/416/416636_6456158-lq.mp3|
| [259609](https://freesound.org/people/Stevious42/sounds/259609/) |BY3|38,847 /4908bec98756b49010e8aed734bad0ff46e2947f48159028030113df2adbcfed|https://cdn.freesound.org/previews/259/259609_4841074-lq.mp3|
| [50665](https://freesound.org/people/BoilingSand/sounds/50665/) |BY3|41,851 /05a8231c515a96cb23c7a59b5f98584f3faa8619d03a570541e92d3a18e32b44|https://cdn.freesound.org/previews/50/50665_571325-lq.mp3|

## 6. 내부 weakMIL 가능성과 다음 조건

부모 작업자의 binary35 후속 보고: 이전 성인웃음2 오인은 줄었으나 약국첫누락은 회복되지 않고 후속은0.975초 한창만 남았으며 validation infant4 전부 support0, held adult scream384213은3.9초 연속 오인이다. **이 문서는 해당 실행 산출물을 재감사하지 않았다. 부모 실행 보고를 다음 데이터 선택의 제약으로만 반영하며 품질 승인을 하지 않는다.**

약한 clip labels를 모든975ms window에 복제하는 대신 bag 단위 infant-presence loss를 쓰는 내부 weakMIL은 기술적으로 가능하다. 기존 binary head 위에서 bounded top-k/log-sum-exp aggregation 등 **한 가지 train-only 규칙을 사전 고정**하고 source 하나당 loss contribution을 제한하는 개발 실험이다. 양성파일의 성인/잡음/악기를 infant 정답으로 학습할 위험을 줄이는 목적이다. max만으로 bag을 만족시키면 crinkle/recording channel shortcut 한창을 택할 수 있어 원음 사건 검수와 negative 대조가 필수다.

다음 실행의 순서:

1. 새 devsource FunWithSound/Stevious42의 소량취득 계약을 만들고 기존 held/public33 source와 전체 파생관계를 확인한다. raw original과 preview를 별도로 hash/format 기록한다.
2. local human review에서 child event 존재/시작·끝, 월령 미확인, 성인겹침, 재생·합성·편집·잡음과 uncertainty를 기록한다. source 설명의 행복/요구 표현을 동기 정답으로 가져오지 않는다.
3. target6~18개월의 독립 옹알이source가 여전히 없으면 데이터 결손을 그대로 유지한다. 이를 해결하려면 commercial permission과 보호자 동의를 갖춘 별도 수집 경로가 필요하며 이번에 연락/수집을 시작하지 않았다.
4. approved public training bags에만 weakMIL을 적용하고 기존 Corsica/tim.kahn 및 모든 held adult/eval source는 학습/augmentation에서 제외한다. 이미 본 validation을 최종test로 다시 보고하지 않는다.
5. adult nonverbal 및 TV/playback/악기 반례와 infant event recall/false approval per minute를 함께 고정한다. 새 미노출source 없이는 일반화·채택을 확정하지 않는다. 사용자 사적 원본·구간·features는 계속 평가 전용이며 학습/calibration/augmentation 금지다.
6. 자료 조건 충족 뒤에도 측정 이득이 없는 모델/weakMIL은 적용하지 않는다. LEAF 등 새 representation은 이 데이터 gate를 지난 후 별도 source/input/export 계약과 Android 비용을 검증한다.

**완료한 것: 새 작은 specialist의 primary metadata 계약·실행/권리 blocker, BYOL-A 제한 확인, LEAF 소스 대안의 비용 한계, 기존 source의 분리 상태와 다음 취득 가능한 ID 고정. 미완료: 새 양성 음성 검수·학습·모바일 검증·제품 채택·권리 문의 발송.**
