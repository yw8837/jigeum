# On-device text-only candidates for Korean sentence generation

확인일: 2026-10-04. 목적은 이미 생성된 관찰 사실을 바탕으로 한국어 한 문장만 표현하는 후보를 찾는 것이다. 부모 조사에서 확인한 영어 JSON 및 한국어 plain prompt의 synthetic 6-case 오류(아기 울음을 babbling으로 바꿈, cry를 hunger로 바꿈, neutral babble에 재미/욕구를 붙임)를 모델 카드의 성능 근거로 대체하지 않는다. 원본 미디어·개인정보를 검색하거나 전송하지 않았고, 모델 파일 다운로드·추론·앱 수정·Gradle·ADB는 수행하지 않았다.

## 후보 비교

| 후보 | 고정된 HF artifact | 파일 크기 / LFS SHA-256 | 카드가 선언한 형식과 런타임 | Korean 근거와 한계 | 권장 용도 |
|---|---|---|---|---|---|
| **Qwen3-0.6B INT4 no-think** (`litert-community/Qwen3-0.6B-int4`) | `qwen3_0.6b_nothink_q4_block32_ekv1280.litertlm`; repo revision [`6aa2daf8aba4aa456797fb8040b36a3948bcfda7`](https://huggingface.co/litert-community/Qwen3-0.6B-int4/tree/6aa2daf8aba4aa456797fb8040b36a3948bcfda7) | **347,251,840 B** (~331.1 MiB); LFS SHA-256 `2df6821ec12702dafd33915e7a1a1adc7c4b053f3672fd9555dfaf3a114c4139` | Community card는 `.litertlm`, Android/iOS/desktop/embedded LiteRT-LM이라고 선언하고 Android/Kotlin `EngineConfig` 사용 예를 제공한다. INT4 block-32, FP32 activations, 1280-token KV cache. No-think 파일은 ChatML assistant prefix에 빈 `<think></think>` 구간을 미리 닫는 형식이라 카드 설명상 추론 mode가 파일에 고정된다. | Qwen 원 model card는 Qwen3 전체에 100+ 언어 지원을 말한다. Qwen 공식 launch blog는 119개 언어·방언 표에 **Korean**을 명시한다. 이는 0.6B INT4 변환본의 한국어 문장 품질 검증이나 욕구/사건 의미 검증이 아니다. | 우선 작은 artifact로 동일 오류 6-case를 다시 시도할 비교 후보. 텍스트 형식·사실 충실도·한국어 자연스러움은 별도 실행 검증이 필요하다. |
| **Qwen3-1.7B dynamic INT4** (`litert-community/Qwen3-1.7B`) | `Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm`; repo revision [`73fbc3fe8271c162a603ee66f6e7ed25b6211195`](https://huggingface.co/litert-community/Qwen3-1.7B/tree/73fbc3fe8271c162a603ee66f6e7ed25b6211195) | **977,184,032 B** (~931.7 MiB); LFS SHA-256 `2eeffef7b51bc3e1225ea69fe7aa5f417397934b56a5b6c20cc068d6fd2c918b` | Community card는 LiteRT-Torch 변환, dynamic INT4 weight/block-32, FP32 activation, context 4096을 명시한다. 같은 카드에는 LiteRT-LM CLI 사용법, Android Gallery import 경로, community 측 v0.16.0 Android 기기 측정이 있다. 해당 repo가 요구하는 최소 runtime version이나 정확한 앱 내 Kotlin 0.17.1 실행 여부는 명시적으로 고정돼 있지 않다. 카드의 기기 측정은 Galaxy S26의 CPU에서 2.5 GB peak RSS, GPU에서 약 1.0 GB였으며 대상 단말 자원 보증이 아니다. | Qwen3 원 model card/공식 blog의 100+/119 언어 설명에서 Korean을 확인했다. 작은 base와 동일하게 1.7B 변환 파일에서의 한국어 문장 품질·환각률은 별도 검증되지 않았다. | 더 큰 모델을 비교할 가치가 있을 때 후보. 저장 공간과 메모리 부담이 크고, 부모의 동일 사례에서 개선을 보장하지 않는다. |

## 고정 메타데이터와 라이선스

Hugging Face `api/models/{repo}/tree/{revision}?recursive=true&expand=true` 응답의 각 파일 `size`와 LFS `oid`를 읽었다. 이는 원격 저장소 메타데이터 확인이다. 바이너리 내용을 받거나 해시를 로컬에서 재계산하지 않았다. API metadata에서 두 community repo와 두 Qwen base repo의 `license`는 `apache-2.0`이다. Community model card도 `license: apache-2.0`를 표시한다. 재배포/제품 사용 전에는 두 고정 revision의 LICENSE 파일과 upstream 조건을 실제 배포 형태에 맞춰 다시 확인해야 한다.

- [Qwen3-0.6B LiteRT-LM community card @ pinned revision](https://huggingface.co/litert-community/Qwen3-0.6B-int4/blob/6aa2daf8aba4aa456797fb8040b36a3948bcfda7/README.md)
- [Qwen3-0.6B official base model card @ pinned revision](https://huggingface.co/Qwen/Qwen3-0.6B/blob/c1899de289a04d12100db370d81485cdf75e47ca/README.md)
- [Qwen3-1.7B LiteRT-LM community card @ pinned revision](https://huggingface.co/litert-community/Qwen3-1.7B/blob/73fbc3fe8271c162a603ee66f6e7ed25b6211195/README.md)
- [Qwen3-1.7B official base model card @ pinned revision](https://huggingface.co/Qwen/Qwen3-1.7B/blob/70d244cc86ccca08cf5af4e1e306ecf908b1ad5e/README.md)
- [Qwen official Qwen3 language list](https://qwenlm.github.io/blog/qwen3/): 119 languages/dialects; Korean is listed under “Other”.

## LiteRT-LM 0.17.1 API and prompt mode

LiteRT-LM Kotlin source pinned at [`v0.17.1 Config.kt`](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.17.1/kotlin/java/com/google/ai/edge/litertlm/Config.kt) defines `ThinkingConfig(enableThinking, thinkingTokenBudget)` and exposes `thinkingConfig` on `ConversationConfig`. This establishes that the pinned Kotlin API has a supported way to request disabled thinking for templates that honor that setting. It does **not** establish that every community-converted model artifact honors it.

- For the **0.6B no-think artifact**, use its file-baked prompt format; its card says no additional runtime prompt-format configuration is needed. The artifact’s prompt template/stop tokens are embedded. Do not prepend a second ChatML wrapper without inspecting the actual artifact integration.
- For the **1.7B INT4 artifact**, Qwen’s pinned upstream model card specifies `enable_thinking=False` in `apply_chat_template`; Kotlin v0.17.1 offers `ThinkingConfig(enableThinking = false)` and template context. The community card alone does not specify the artifact’s exact no-think prompt behavior. The community repo’s discussion notes that an earlier prompt-template issue in the regular artifact was fixed in the INT4 file, but this remains maintainer/community evidence, not verification on this app. Pin the file hash and test the exact Kotlin path before relying on the switch.
- Qwen upstream’s non-thinking sampling recommendation is temperature 0.7, top-p 0.8, top-k 20, min-p 0. It is an upstream general recommendation, not a proven best setting for short fact-bound baby sentences. Do not infer that greedy decoding or any one sampler will be reliable for this task.

A candidate prompt should state that source facts are fixed, require exactly one natural Korean sentence, prohibit adding causes/needs/emotions/intents not present in supplied facts, and permit an explicit cautious phrasing when evidence is ambiguous. These are evaluation conditions to try, not a claim that prompt wording prevents unsupported claims.

## Compatibility and quality status

- **Format evidence:** both exact artifacts are `.litertlm`; the 0.6 card documents Android/Kotlin; the 1.7 card documents Android Gallery import and LiteRT-LM CLI usage. LiteRT-LM v0.17.1 Kotlin has the relevant text generation/conversation and thinking configuration surface.
- **Not established:** direct loading of either pinned binary with the app’s exact Maven AAR/runtime v0.17.1, required operators, memory peak on target devices, latency, offline cold start, tokenizer behavior for Korean, exact one-sentence compliance, or reduction in any of the three observed errors. No model file was downloaded or executed here.
- **Text-only scope:** these Qwen3 checkpoints generate text. They do not consume the original audio/video or validate the observations supplied to them. An upstream model’s multilingual support does not make it an audio/video perception model or a baby-intent classifier.
- Keep the same six factual cases and prompt languages from the failed Gemma trial for an apples-to-apples next evaluation. Report per-case verbatim output, unsupported fact additions, language/format violations, refusals, and runtime failure separately. Do not call a model an improvement until those outputs and the installed Kotlin path are actually checked.

## Primary source retrieval

Agent-Reach Exa identified the candidate repositories; Jina Reader read pinned Hugging Face README files; Hugging Face model/tree APIs returned repository revisions, license fields, file sizes, and LFS SHA-256 metadata; official Qwen source supplied multilingual and non-thinking claims; LiteRT-LM `v0.17.1` source supplied the Kotlin API evidence. No model binary was downloaded, no inference was run, and no user media or private data entered any query.
