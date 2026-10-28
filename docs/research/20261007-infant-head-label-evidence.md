# 영아 head 라벨 근거: public35의 데이터 결손과 specialist 실행 계약

확인일 **2026-10-07 KST**. 이 문서는 public35의 공개 source/feature **JSON metadata** 및 기존 실행 집계 문서, 공식 repository source/tree/license를 읽은 후속 조사다. media·embedding NPZ·weight body를 읽거나 취득하지 않았다. **원음/preview 취득·청취·추론·학습·사용자 자료 접근·Git·빌드·ADB·외부 연락은 하지 않았다.** 변경 파일은 이 문서 하나다.

## 1. 결론

public35는 영아 train **7clip/4uploader**와 validation **4clip/1uploader**다. train의 설명상 나이는9~10개월이며, held는15개월의 한아기 설명이다. 모든 실제 audio_review_completed=false이고 approved_training_label=null이다. 실제 발성 종류·speaker·event 구간이 검증되지 않은 **공개 caption weak 개발 corpus**다. 양성7clip을 이미 검수된 옹알이7종이나7독립 아기로 세지 않는다.

따라서 확인된 실패를 “YAMNet이 영아 정보를 모두 잃었다” 또는 “MIL 최적화가 잘못됐다”라고 단정할 수 없다. **직접 확인 가능한 결손은 출처·발성 종류·월령·event 정답 부족과 CV에서 남은 양성 source 오류**다. 이 조건에서 mean→MIL 변경만으로 지속 회복과 성인 오인 방지가 충족되지 않았다는 기존 실행 결과는 보존한다.

UIUC infant-vocalize의 **고정 정규화 기준 미배포**를 새 source 조사로 확인했다. VocalBaby는 input→artifact 연결이 더 명확하지만 preprocessor는 source상 median imputer이며 별도 z-score scaler가 아니다. 두 후보 모두 openSMILE 상업 조건과 exact trained artifact/원천 권리 문제가 남아 이번에 실행 가능한 상업 대체 checkpoint를 승인하지 않는다.

다음 실제 정보 이득은 **새9개 원음의 합법적 취득 후 사건 검수**다. 이 작업에서는 새9개를 모두 미청취 blocked로 유지한다. 고정된 held·eval source를 train으로 옮기지 않는다.

## 2. public35에서 실제 확인한 라벨 구성

출처: [execution-sources35.json](../../work/infant-head-20261007/execution-sources35.json), [features35-02/features.json](../../work/infant-head-20261007/features35-02/features.json). JSON에 기록된 actual_pcm_samples·frame RMS·review 상태만 계산했다. PCM 또는 feature 벡터를 읽지 않았다.

| clip | role | source | 실제 입력 초(metadata) | 전체/nonquiet975ms창 |
|---|---|---|---:|---:|
| 113220 | train | NoiseCollector | 0.911375 | 1/1 |
| 113221 | train | NoiseCollector | 1.732750 | 2/2 |
| 113223 | train | NoiseCollector | 0.854750 | 1/1 |
| 46963 | train | reinsamba | 1.575562 | 2/2 |
| 47371 | train | reinsamba | 2.344375 | 3/2 |
| 377237 | train | Fugazi80 | 13.283500 | 14/12 |
| 8157 | train | levinj | 20.000000 | 21/21 |
| 18274 | validation | Corsica_S | 0.583500 | 1/1 |
| 18277 | validation | Corsica_S | 0.814312 | 1/1 |
| 18276 | validation | Corsica_S | 1.978000 | 3/2 |
| 18273 | validation | Corsica_S | 0.562250 | 1/1 |

Train 양성 합계 **40.702312초 /44창 /nonquiet41창**이다. Fugazi80와 levinj 두 source가35창/nonquiet33창을 제공한다. **학습 loss는 class/source/clip 균등 가중**이므로 이 창수 집중을 곧바로 loss 독점이라고 해석하지 않는다. 시간·사건 다양성의 실제 분포를 보여주는 수치다.

### source별 weak 의미와 실제 결손

| source | current 원문 근거 | 설명할 수 있는 실패 위험 |
|---|---|---|
| NoiseCollector3 |9개월 남아, “various baby noises”; loopable·여러 음악장르·loop 사용 설명 |same source의짧은편집3종 총3.498875초/4창. natural infant sound/옹알이 canonical 여부·처리·시작/끝 정답미검수. edited sound와 continuous home vocal 다양성 차이 가능 |
| reinsamba2 |46963 “baby language/feelings”;47371 “laughing at my funny grimaces and sounds” |cry raw 라벨을 actualcry로 단정 불가. 보호자 sound 동반 가능성은 caption에 있지만 실제 overlapping adult event 미검수 |
| Fugazi80 |10개월딸/phone 녹음 |13.2835초14창 중quiet2. actual child event 어느창인지 모름. mixed bag에서 positive target를 만족하는 단서가 성인/환경/channel일 가능성을 event label로 검사해야 함 |
| levinj |9개월아들giggle |20초21창. laughter 양성 하나를 broad babble source diversity로 세지 않음 |
| Corsica_S→tim.kahn held4 |15개월ahh/high-pitched baby/nooo/aha;18274끝footstep 설명 |한source의짧은protospeech/whine. train9~10개월laugh/editedbabble에서 다른15개월 발성으로의 transfer 한계. face·욕구·동기 정답없음 |

Validation18274/18277/18273은 모두 **0.975초보다 짧아1창+tail padding**이며 actual-duration 비율은 약59.8%/83.5%/57.7%다. Padding/RMS/actual-sample 처리는 기존 runner 계약을 따른다. **padding이 누락을 일으킨 것으로 입증된 것은 아니다**. 비교할 source/발성 종류·길이 분포가 좁고 다르다는 진단이다. 새 private crop이나 threshold 변경으로 이 표를 맞추지 않는다.

Train source는 설명상9/9/10/9개월이고 **6~8개월 및11~18개월의새 train source는0**이다. validation4를같은15개월source로보았다는 사실은 family/age 일반화를 입증하지 않는다.

### 선행 실패와 양성 CV 오류

[기존 binary35 결과](20261007-infant-presence-results.md)와 [MIL35 결과](20261007-infant-mil-results.md)에 따라 지속 회복은 충족되지 않았고 성인비명384213 오인이 남았다. 이 문서에서는 private 실행 파일·원음·feature를 열지 않았다. 다음은 **public train selection JSON**을 다시 집계한 새로운 진단이다.

| head | 사전선택L2 | 전체18source macroNLL | 영아4source 평균NLL | negative14source 평균NLL | NoiseCollector held foldNLL |
|---|---:|---:|---:|---:|---:|
| binary35 | 0.001 | 0.265849590 | 0.507152146 | 0.196906003 | 1.602437801 |
| MIL35 | 0.001 | 0.263161695 | 0.489523833 | 0.198486798 | 1.667376279 |

18source 단순macro에는negative14/infant4가들어간다. 전체 값이낮아도특정infant source의오류가작다는뜻은아니다. MIL .001에서NoiseCollectorheldNLL1.667은binary1.602보다낮아지지않았다. **pooling변경만으로이source일반화가해결되지않았다는train-only근거**다. retrospective하게다른L2/threshold를선택하지않는다. 앞으로새사전계약에서class별/source별진단을같이보고해야할이유다.

모든label은weakcaption이다. acoustic feature 한계, 실제speaker identity 학습부족, 최적화, annotation noise의 인과적 몫을 현재 결과만으로 분해할 수 없다. 새 source가 늘면 반드시 성공한다고 보고하지 않는다.

## 3. UIUC infant-vocalize: exact frontend/normalizer/weight 추가 감사

고정 revision **ed1115642231b334c09c345d98b1b3d59d94fe29**. head artifact·GPL3·openSMILE 제한의 기본 사실은 [이전 감사](20261007-next-infant-representation-evidence.md)에 있으므로 이 절은 새 실행 정보만 정리한다.

### 새로 확인한 normalization blocker

[고정 util.py163~176](https://github.com/uiuc-sst/infant-vocalize/blob/ed1115642231b334c09c345d98b1b3d59d94fe29/util.py#L163)의 normalize_features는:

1. 주어진H5의모든row를모은다.
2. column별mean을그H5에서계산한다.
3. std=max(std,1e-6)로계산해(feature-mean)/std를쓴다.
4. normalizedrow를새H5에쓴다. **mean/std를별도재사용artifact로저장하지않는다.**

**한clip/한row만이helper에넣으면모든column의normalized값은0**이다. 이는 source 수식의 결론이며 실제음성실행결과는아니다. 공개head의학습 reference 통계를 읽은 공개 자료에서 확보하지 못한 상태에서 새입력을임의z-score하거나새9clip의통계로정규화하면원래checkpoint의inputcontract를복원한것이아니다.

[make_os_data.py395~396](https://github.com/uiuc-sst/infant-vocalize/blob/ed1115642231b334c09c345d98b1b3d59d94fe29/make_os_data.py#L395)에는train/test H5를각각normalize하는 **주석 예시**가있다. 580~581에도별도호출주석이있다. 실제namedcheckpoint가그경로로훈련됐다는증거는없다. 이 예시를우리app의데이터누출결함으로단정하지않는다.

[normalize_features_segment](https://github.com/uiuc-sst/infant-vocalize/blob/ed1115642231b334c09c345d98b1b3d59d94fe29/util.py#L178)는means를계산하지만write식에서mean이란다른이름을참조한다. 공개helper를그대로완성품runner로볼수없는추가source문제이며실제artifact훈련과의관련성은미확정이다.

### 정확일치 확인상태

| 경로 | 확인 | 미확정·필요정보 |
|---|---|---|
| [opensmile_feature_extraction.py](https://github.com/uiuc-sst/infant-vocalize/blob/ed1115642231b334c09c345d98b1b3d59d94fe29/opensmile_feature_extraction.py) |openSMILE2.3.0/IS09_emotion.conf 호출 |README의emobase/combined/Fisher1000과어느weight가대응하는지불명 |
| [NN/ser.py133~177](https://github.com/uiuc-sst/infant-vocalize/blob/ed1115642231b334c09c345d98b1b3d59d94fe29/NN/ser.py#L133) |input size는H5에서읽고hidden128. defaultload는idp_lena_5way_combined_fisher_1000_fold.pt |실제공개파일lena_idp_4way.pt/5way.pt와defaultload이름이다름. 공개tensor를안읽었으므로실제input dimension추정금지 |
| [README_JL](https://github.com/uiuc-sst/infant-vocalize/blob/ed1115642231b334c09c345d98b1b3d59d94fe29/README_JL.md) |feature naming/fold/normalized/privatepath 설명 |column order/selected feature indices/train statistics/namedcheckpoint의연결manifest없음 |
| [tree API](https://api.github.com/repos/uiuc-sst/infant-vocalize/git/trees/ed1115642231b334c09c345d98b1b3d59d94fe29?recursive=1) |공개4way813,388B/5way813,904B존재 |이tree에H5/NPY/NPZ/ARFF고정normalizerdata파일없음.부재는조회한tree범위의결론 |

root params.py의 16k/25ms/10ms/64band/0.5초 설정은FBANK/CNN경로자료다. 이를openSMILEFChead의해결된frontend로대체하지않는다. 실행하려면원저자의exactartifact→column/order/config→frozennormalizer→labelorder연결근거가필요하다.이번에는문의하지않았다.

### 상업조건

Repository **GPL3는 상업 이용 금지가 아니다**. 결합/배포의source제공범위와별도weightgrant·IDP/LENA training권리를확인해야한다. [openSMILE 공식 licensing](https://github.com/audeering/opensmile#licensing)은open-source판을commercialproduct에사용할수없으며제품개발에는별도commercialdevelopmentlicense를요구한다. 무료beta/로컬학습/작은head만으로조건이해소되지않는다. 제한된teacher/weight를distillation하는우회도제안하지않는다.

## 4. VocalBaby: 더구체적artifact 연결, 계속남는권리조건

고정 revision **8d214be476e9819322622cb08966faf00fdaeb3b**. [tree](https://github.com/arunps12/VocalBaby/tree/8d214be476e9819322622cb08966faf00fdaeb3b/final_model)의공개파일은model5,432,713B/preprocessing749B/labelencoder500B다. picklebody를취득/실행하지않았다.

[PredictionPipeline constructor31~50](https://github.com/arunps12/VocalBaby/blob/8d214be476e9819322622cb08966faf00fdaeb3b/src/vocalbaby/pipeline/prediction_pipeline.py#L31)는model_trainer_dir아래의세파일을직접연결한다:

- xgb_egemaps_smote_optuna.pkl
- preprocessing.pkl
- label_encoder.pkl

[extract_egemaps72~82](https://github.com/arunps12/VocalBaby/blob/8d214be476e9819322622cb08966faf00fdaeb3b/src/vocalbaby/components/data_transformation.py#L72):openSMILEeGeMAPSv02/Functionals의88columns를flatten해FP32로반환한다. 실패시0벡터반환경로가있다. 입력오류를무음/정상feature로취급하면안되며exactrunner에서는별도거부가필요하다.

[model_trainer254~259](https://github.com/arunps12/VocalBaby/blob/8d214be476e9819322622cb08966faf00fdaeb3b/src/vocalbaby/components/model_trainer.py#L254), [imputation.py](https://github.com/arunps12/VocalBaby/blob/8d214be476e9819322622cb08966faf00fdaeb3b/src/vocalbaby/utils/ml_utils/preprocessing/imputation.py#L10):train에SimpleImputer(strategy=median)를fit하고valid/test에transform한다. [save319~345](https://github.com/arunps12/VocalBaby/blob/8d214be476e9819322622cb08966faf00fdaeb3b/src/vocalbaby/components/model_trainer.py#L319)는이imputer와LabelEncoder/model을저장한다. **source의preprocessing은median imputer며StandardScaler가아니다.**749B파일의실제내용·학습 통계는이번에읽지않았으므로source와artifact의실행동일성은미검증이다.

[chunked inference114~169](https://github.com/arunps12/VocalBaby/blob/8d214be476e9819322622cb08966faf00fdaeb3b/src/vocalbaby/pipeline/prediction_pipeline.py#L114)는16kmono로load후400ms비중첩,tail200ms미만skip이다.400ms이하파일은원path의whole-filefeature를사용하여해당분기와chunk의실제sample-rate/encoding계약은추가고정이필요하다. [predict225~239](https://github.com/arunps12/VocalBaby/blob/8d214be476e9819322622cb08966faf00fdaeb3b/src/vocalbaby/pipeline/prediction_pipeline.py#L225)는chunk probability평균을내어파일당라벨하나를낸다.400ms시간축발성검출값이바로배포되는것은아니다.

[requirements](https://github.com/arunps12/VocalBaby/blob/8d214be476e9819322622cb08966faf00fdaeb3b/requirements.txt)의opensmile/scikit-learn/xgboost는version이pin되지않았다. 실제column순서·전처리statistics·labelencoderclasses·runtime호환성·artifact훈련manifest가필요하다. rootMIT는openSMILE상업제한을해제하지않으며별도weight/data권리근거는여전히미확보다.

**VocalBaby의성능수치·코드구조를우리head실패의해결근거로쓰지않는다.** 새MFCC40/logmel등을88dim으로맞춰이weight에대입해도동일eGeMAPS계약이아니다. 별도상업허락확보또는조건에맞는자체feature+자체head가필요하다. 권리구매·연락·모델실행을시작하지않았다.

## 5. 고정source분할을유지하는실제다음데이터경로

새9개는[별도current감사](20261007-extra-babble-source-audit.md)와 [extra-babble-sources.json](../../work/infant-head-20261007/extra-babble-sources.json)에고정돼있다. 아래는**권리와경로확인된메타데이터개발후보**이며실제옹알이양성확정은아니다.

| source | 옹알이/발성다양성취득우선ID | 실제current권리·review상태 |
|---|---|---|
| FunWithSound |416677 Ah-Bah-Bah,416681 Gee-Gee; raregrunt416635; laugh416702 |CC0/공식preview확인/미청취blocked.4개를전체48행과같은source로묶음 |
| Stevious42 |259609 Baby Babble and crinkle |BY3/공식preview확인/미청취blocked.소품음혼합·같은source monitor가검수조건 |
| wjoojoo |254454,254455 babbling;254465,254471 sayingdada |currentBY4/archiveBY3/공식preview확인/미청취blocked.전체20행을같은source로묶음 |

실제 청취·라벨 검수 전에는 이 9개의 approved_training_label을 채우지 않는다. **월령미상3source**이며6~18개월새검증가족0이다. 새고정sourcegroup은 개발 train 후보에만 배정돼 있고 Corsica/tim.kahn·BoilingSand/eval·negativeheld·public33held는이동하지않는다. 기존source-audit의remainingNoiseCollector17clip을늘려도새독립source가생기지않는점은유지한다.

### 다음실행에필요한최소label근거

합법취득후localreview가허용된담당자가public9개마다다음을기록해야한다:

1. 원path/currentgrant/archivedgrant/attribution, 실제취득filekind(preview/original), bytes/hash/nativeformat.
2. infant/youngchild발성존재여부와확실성, event시작/끝, babble·laugh·grunt·whine및uncertain;출처설명·raw라벨과별도저장.
3. adultvoice/성인흉내·TV/playback/monitor·pitchchange·crinkle·mixedsound flag와각event근거.소리를못들었으면unknown.
4. targetage는정확월령증거가있을때만채움. youngbaby/toddler/dada를6~18개월verified로강제하지않음.
5. 우선review완료인trainbag만새head실험에허용.고정held/privatefeature를normalizerfit·threshold·augmentation에사용하지않음.

불확실sound를infantlabel로채우는대신uncertain/block를유지한다. source가3개늘어도같은uploaderclip수를독립화자로세지않는다. 추가자료전후결과는source/event구성표와adultnonverbal/TV/악기반례를같이보고한다.

## 6. 실행선택과완료범위

**우선순위는 source의 실제 발성 사건 검수다.** 이를충족한publictrainingbag에서만기존fixedrepresentation의head비교를후속사전계약으로진행한다. 기존35 mean/MIL실패를그대로반복하거나원음을맞추는사후threshold변경은이문서의제안이아니다.

상업source조건이명확한별도자체MFCC/logmel+작은head는만들수있지만현재prototypehead의eventlabel결손을우회하지못한다. LEAF학습가능source·모바일비용한계는[기존representation감사](20261007-next-infant-representation-evidence.md)에있으며이번에재실험하지않았다. UIUC/VocalBaby는조건확정전다음weight취득목록에넣지않는다.

완료: public35의source/label/시간창분포및train-onlypositiveCV결손계산,UIUCfrozennormalizer미배포의source근거,VocalBabyimputer와artifact연결·chunk계약추가확인,새9개의권리/blocked/split조건정리. 미완료: 원음취득·실제label검수·새학습/품질회복·mobile/product승인·권리문의발송.
