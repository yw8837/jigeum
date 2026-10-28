# Gemma 4 E2B 구성 분리·특화 가능성

확인일 **2026-10-05**. Google 공식 문서·Gemma/LiteRT 소스·Google HF config/card를 읽은 조사다. 모델 다운로드·변환·학습·추론·빌드·ADB·사용자 자료 업로드를 수행하지 않았다. 이 문서 하나만 작성했다.

## 판단

**특화 경량화는 연구할 수 있지만 ‘코딩·수학 지식만 삭제’하는 공식 기능은 확인되지 않았다.** E2B는 dense decoder이며 `enable_moe_block=false`다. encoder·adapter·embedding·PLE 같은 실행 구성은 분리되지만, 범용 지식별 독립 module을 골라 지우는 구조는 아니다. AV 입력을 유지하면 audio/vision encoder도 필요하다.

| 조치 | 확인한 사실 | 아가톡 적용 판단 |
|---|---|---|
| 사용하지 않는 modality 제외 | audio/vision encoder·adapter·종료 embedding이 별도 export/bundle section. 공식 Gemma `text_only=True`는 media encoder 제외 | 한 modality 또는 text-only 연구 경로는 있다. 두 encoder를 모두 빼면 소리+영상 공동 입력 기능도 사라진다. |
| PLE memory mapping | 고정 LiteRT card는 약0.8GB text weight footprint와 **1.12GB embedding parameters의 mmap**을 설명 | 상주 메모리 최적화다. PLE 물리 삭제나 전체 다운로드0.8GB의 근거가 아니다. |
| PLE 제거 | 내부 token lookup skip과 PLE 없는 config 분기는 존재하지만, E2B 공개 설정/export는 PLE를 사용 | **AV E2B의 PLE 물리 삭제·품질 유지·재export를 입증한 공식 recipe/checkpoint는 확보하지 못했다.** |
| 시각 token budget 축소 | Android0.17.1 experimental flag가 Kotlin/JNI→Gemma4 processor에 전달 | 기존 artifact 최대280에서70/140 비교를 설계할 근거. 실제 실행/품질/속도는 미검증. |
| 특화 fine-tuning | Google vision QLoRA 안내에 Gemma4 E2B 이미지+텍스트 학습과 adapter merge/save가 있음 | 특화 지원은 확인. LoRA는 base layer/shape를 줄이는 방법이 아니며, 아기 raw audio+시간순 AV 학습·mobile QAT 재export 성공은 미확인. |
| layer/head/channel pruning | config에 구조 차원이 있으나 읽은 공식 경로에 E2B AV pruning recipe 없음 | source model·weights/config·graph를 함께 바꾸고 재export하는 새 모델 연구. 품질 회복 학습 필요 여부도 실제 평가해야 한다. |

## PLE를 엄격히 구분

- [Google 기본 E2B config](https://huggingface.co/google/gemma-4-E2B-it/blob/3e22461f65e89153144f8adb70e3b8c2cc9845a7/config.json):35 decoder layers,hidden1536,PLE dimension256,vocabulary262144,KV 공유20층. PLE lookup table은 config 계산상 **262144×35×256=2,348,810,240 parameters**다. 이를 파일 bytes/RAM으로 환산하지 않았다.
- [고정 LiteRT card](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/blob/b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1/README.md)는 embedding mmap과 audio/vision on-demand loading을 명시한다. 가중치를 보존하며 메모리를 관리하는 방식이다.
- [Google QAT blog](https://blog.google/innovation-and-ai/technology/developers-tools/quantization-aware-training-gemma-4/)의 “text-only model (without Per-Layer Embeddings) requires less than1GB of memory”는 memory 예시다. 해당 문구에 AV PLE 제거 명령·checkpoint·품질 검증은 없다. 고정 LiteRT card의 mmap 설명과 합쳐 ‘PLE를 삭제해도 AV 품질 유지’로 해석하지 않는다.
- 공식 JAX [Embedder](https://github.com/google-deepmind/gemma/blob/c490becf42d18a3337a6ef351c24dfc77896e6d4/gemma/gm/nn/gemma4/_modules.py#L169)는 context projection `x`와 token lookup `y`를 합친다. `ignore_ple_tokens=True`는 `x`만 반환하며 projection/gate 전체 제거가 아니다. table도 setup에서 parameter로 정의한다.
- 공식 [Transformer 호출](https://github.com/google-deepmind/gemma/blob/c490becf42d18a3337a6ef351c24dfc77896e6d4/gemma/gm/nn/gemma4/_transformer.py#L235)은 해당 flag를 공개 인자로 받지 않고 내부 encoder를 기본값으로 호출한다. 내부 분기를 일반적인 PLE 삭제 버튼으로 소개하지 않는다.
- [LiteRT E2B export](https://github.com/google-ai-edge/litert-torch/blob/5a31633a9118b122c2ada01b136d470a714b3550/litert_torch/generative/export_hf/model_ext/gemma4/exportable_module.py)는 `[batch,sequence,layers,PLE_dimension]` 입력과 별도 per_layer_embedder를 사용한다. section 삭제만으로 graph 입력 계약이 바뀌지 않는다.

## public export: release와 main 차이

| 기준 | 고정 revision | 실제 확인 |
|---|---|---|
| 최신 released LiteRT Torch | [v0.9.4](https://github.com/google-ai-edge/litert-torch/releases/tag/v0.9.4),`ad05a3fa6426ffc2b809caf574a26c08b6c99a15` | release note는 Gemma4 export 지원을 적지만, tag에는 Gemma4 audio_exportable.py가 없다. 이를 현재 AV 재export 완료 지원으로 소개하지 않는다. |
| 조사 시점 main | `5a31633a9118b122c2ada01b136d470a714b3550` | [audio encoder/adapter/EOA](https://github.com/google-ai-edge/litert-torch/blob/5a31633a9118b122c2ada01b136d470a714b3550/litert_torch/generative/export_hf/model_ext/gemma4/audio_exportable.py),[vision encoder/adapter/EOI](https://github.com/google-ai-edge/litert-torch/blob/5a31633a9118b122c2ada01b136d470a714b3550/litert_torch/generative/export_hf/model_ext/gemma4/vision_exportable.py),[dispatcher](https://github.com/google-ai-edge/litert-torch/blob/5a31633a9118b122c2ada01b136d470a714b3550/litert_torch/generative/export_hf/model_ext/exportables.py)가 실제 존재한다. 과거 ‘오디오 export 없음’ 이슈를 현재 main 사실로 반복하지 않는다. |

- [Export config](https://github.com/google-ai-edge/litert-torch/blob/5a31633a9118b122c2ada01b136d470a714b3550/litert_torch/generative/export_hf/core/exportable_module_config.py):`image_text_to_text`는 `export_audio_encoder=False`로 강제한다. 양쪽 경로가 있는 task는 **`multimodal_lm`**이다. VLM export 성공을 AV 성공으로 세지 않는다.
- [check_supported_model](https://github.com/google-ai-edge/litert-torch/blob/5a31633a9118b122c2ada01b136d470a714b3550/litert_torch/generative/export_hf/core/export_lib.py#L126):truthy `quantization_config`를 만나면 **“Quantized checkpoint is not supported yet”**를 발생시킨다. [Google mobile QAT config](https://huggingface.co/google/gemma-4-E2B-it-qat-mobile-transformers/blob/dd693ff40353f057ca5f07e945ad867f4afbf2ec/config.json)에 해당 필드가 있다. packed QAT를 바로 재export하는 경로로 확정할 수 없다.
- main vision exporter는 `gemma4_vision_max_soft_tokens` 기본140으로 고정 sample/signature를 만들고 [metadata](https://github.com/google-ai-edge/litert-torch/blob/5a31633a9118b122c2ada01b136d470a714b3550/litert_torch/generative/export_hf/model_ext/gemma4/metadata_builder.py#L58)에9×budget을 쓴다. audio exporter의 sample은 mel `[1,816,128]`,adapter `[1,204,1536]`다. 기존10/20초 입력과 numerical/runtime parity는 실행하지 않았다.
- [공식 builder](https://github.com/google-ai-edge/LiteRT-LM/blob/d17a52fd5c2b4ce1280959309af175caebaac2c3/docs/litert_lm_builder.md)는 section unpack/rebundle을 지원한다. decoder/PLE/AV의 computation이나 품질을 자동으로 고쳐주는 도구가 아니다. 구조를 바꾸면 config·weights·graph·metadata·tokenizer 계약과 재export를 같이 다뤄야 한다.

## Android0.17.1 visualTokenBudget: 실제 고정 소스 경로

아래는 모두 앱 SDK tag **v0.17.1**,commit `5e58e9a0aef7abf7091207a8b1d1063a1c800f08`에 고정했다.

1. [ExperimentalFlags.kt](https://github.com/google-ai-edge/LiteRT-LM/blob/5e58e9a0aef7abf7091207a8b1d1063a1c800f08/kotlin/java/com/google/ai/edge/litertlm/ExperimentalFlags.kt#L94):`visualTokenBudget:Int?`,Gemma4만 지원,70/140/280/560/1120. Engine 생성 전 설정하면 Engine 최대값을 결정한다. 이후 값은 그 최대값을 넘기면 안 된다. experimental/temporary API다.
2. [Conversation.kt](https://github.com/google-ai-edge/LiteRT-LM/blob/5e58e9a0aef7abf7091207a8b1d1063a1c800f08/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt#L135):동기/비동기 JNI에 budget 전달.
3. [Gemma4 processor](https://github.com/google-ai-edge/LiteRT-LM/blob/5e58e9a0aef7abf7091207a8b1d1063a1c800f08/runtime/conversation/model_data_processor/gemma4_data_processor.cc#L149):`args.visual_token_budget`를 prompt processing으로 전달.
4. [Multimodal helper](https://github.com/google-ai-edge/LiteRT-LM/blob/5e58e9a0aef7abf7091207a8b1d1063a1c800f08/runtime/conversation/model_data_processor/multimodal_processor_helper.cc#L90):patchify 최대값을 budget×pooling_kernel_size²로 변경한다. [config 계약](https://github.com/google-ai-edge/LiteRT-LM/blob/5e58e9a0aef7abf7091207a8b1d1063a1c800f08/runtime/conversation/model_data_processor/gemma4_data_processor_config.h#L84)은 사용 가능한 budget이 model의 max_num_patches에 종속한다고 명시한다.

부모 작업자가 기존 로컬 합성 실행의 capability만 확인한 결과,우리 artifact의 **max_vision_token_budget=280**,runner는 budget flag 미설정이다. 작성자는 private 결과를 열거나 전송하지 않았다. **70/140 실제 실행·shape 호환·아기 관찰 품질·속도/RAM 개선은 미검증**이다. 작은 얼굴·입 단서가 손실될 수 있어 감소를 품질 보존으로 가정하지 않는다.

## 특화와 남은 판단

Google [fine-tuning 안내](https://ai.google.dev/gemma/docs/tune)와 [Gemma4 vision QLoRA 예제](https://ai.google.dev/gemma/docs/core/huggingface_vision_finetune_qlora)는 이미지+텍스트 task 튜닝·adapter merge/save를 직접 지원한다. 예제의 PEFT 기본 대상은 LM layers다. 학습 parameter 감소와 배포 base 모델 축소는 다르다. **raw audio+시간순video 특화 학습,PLE/decoder pruning 후 회복,동일 mobile QAT `.litertlm` 재배포는 미확인**이다.

현재 가장 구체적인 경로는 AV를 유지한 **280/140/70 제한 비교의 설계**다. 실제 품질·지연·메모리 gate를 통과해야 하며 이번 조사에서는 실행하지 않았다. 이후 PLE/decoder 축소는 새 모델 개발로 산정한다. 작은 파일이 아기 소리 인식 능력을 자동으로 추가하지 않는다. 기존 S25의 소리 누락·시각 혼입 실패는 남아 있다.

조사는 agent-reach Exa 발견→Jina·GitHub 공개 API/raw·HF config/card 원문 읽기로 수행했다. 비공식 PLE ablation의 소수 text 질문을 AV 품질 근거로 채택하지 않았다. source main의 구현 존재와 released converter/runtime의 실제 성공을 구분했다. 인터넷 전체의 기술 부재를 주장하지 않는다.
