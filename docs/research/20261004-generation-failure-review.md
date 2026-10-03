# 생성 실패 원인 검토와 제한된 호출 형식 대조

2026-10-04. Astra 읽기 전용 검토. 변경한 산출물은 이 문서뿐이다. 검토자는 모델 추론·Gradle·ADB·다운로드·환경 변경을 하지 않았다. 설치 SDK 소스, 기존 로그, 로컬 artifact의 첫1MiB에 포함된 템플릿을 읽었다. 아래 추가 smoke는 부모 실행자가 별도로 실행했다.

**판정:** 현재 Gemma/Qwen 실행 결과를 앱 채택 실패로 취급하는 것은 타당하다. 다만 모든 실패를 모델 자체의 불가능성으로 일반화하지 않는다. Qwen의 content 배열/문자열 처리와 강제 JSON 의심은 한 번의 제한 smoke에서 지지되지 않았다. 한국어 통제문과 no-think 경로가 동작하는 상태에서도 중립 옹알이에 관찰 없는 정서·행동을 추가했다. 다음 모델을 무한히 찾거나 같은 실패 prompt를 계속 미세 조정하는 근거로 삼지 않는다.

## 실제 실행 코드와 SDK에서 확인한 사실

검토 입력은 `work/grounded-text-probe/run.py`, `run-01`, `run-02-ko`, `run-03-qwen17-ko`다. SDK는 설치된 `work/ondevice-av/runtime/Lib/site-packages/litert_lm`이며 dist-info가 `litert-lm-api 0.17.1`을 명시한다.

| 점검 항목 | 확인된 소스/로그 | 판단 |
|---|---|---|
| 강제 JSON grammar | `run.py`는 `constrained_decoding_config`나 `response_format`을 전달하지 않는다. SDK `engine.py:335`는 명시 config가 있을 때만 constraint 설정을 호출한다. `conversation.py:136`, `:167`, `:254`의 response-format 경로는 이 호출에서 None이다. | Python 호출 경로에서 JSON grammar를 강제했다는 근거 없음. SDK가 response 객체를 JSON transport로 반환하는 것과 모델이 JSON 텍스트를 출력하는 것은 다름 |
| 예전 JSON 요청의 대화 잔류 | 각 사례마다 `with engine.create_conversation(...)`를 새로 만들고 닫는다. `engine.py:363`은 native conversation을 새로 생성하고 `conversation.py:81` 이후 close는 native pointer를 삭제한다. Gemma/Qwen은 다른 worker process와 model별 cache 경로다. | 대화 이력 재사용을 뒷받침하는 호출 코드는 없음. native 내부 전체를 독립 감사한 것은 아님 |
| cache 오류 | Qwen stderr에는 cache directory가 없어 XNNPACK weight cache를 열거나 저장하지 못했다는 메시지가 있다. SDK `interfaces.py:388`은 cache를 compiled model artifacts 용도로 설명한다. 추론은 완료됐다. | 반복 로드 비용/환경 잡음의 문제. JSON 요청/답변을 이 cache에서 복원했다는 증거로 사용하지 않음 |
| role 규약 | 실제 입력은 `role='user'`; 내장 Qwen Jinja도 user를 사용한다. SDK Message의 model role과 Qwen의 assistant 표기 차이는 과거 assistant 메시지를 넘기지 않는 이 단일 입력 경로의 원인으로 볼 근거가 없다. | user role 오용을 찾지 못함 |
| no-think | `extra_context={'enable_thinking':False}`를 create_conversation/send에 전달한다. Qwen 실제 내장 Jinja는 `not enable_thinking\|default(true)`에서 닫힌 `<think>…</think>` 접두어를 추가한다. | 이름과 템플릿은 일치. 실제 렌더 대조로 적용 여부 확인 가능 |
| content 형태 | `run.py`는 `content=[{type:'text',text:prompt}]`. Python `_messages.py`의 normalize_message는 Mapping을 그대로 반환한다. 실제 Qwen Jinja는 `message.content is string`을 검사하며 user의 content를 직접 연결한다. | native에서 배열을 flatten하는지 Python 소스만으로 판정할 수 없어 우선 대조 가설로 선정. 배열 입력이 잘못됐다고 단정하지 않음 |
| sampler/context | top_k=1, top_p=1, temperature=0, seed=17; max context2048, output220. native 로그는 KV graph를2048로 조정한 기록을 남긴다. | greedy는 일반 권장 sampling과 다르지만 문법/role 버그의 증거는 아님. 임의 sampler 교체로 실패 원인을 덮지 않음. 전체 한국어 token 수·truncation 여부는 처음6사례에 별도 기록되지 않았음 |
| missing modality 경고 | Qwen stderr의 audio/vision encoder section 미존재 경고 | text-only artifact에서 예상 가능한 경고. 이 Qwen이 원본 소리/영상을 읽었다는 증거가 아님 |

Qwen artifact 경로는 `C:/AgattokProbe-20261003/qwen3-1.7b.litertlm`, 실행 코드의 크기/hash 검증은977,184,032bytes / `2eeffef7b51bc3e1225ea69fe7aa5f417397934b56a5b6c20cc068d6fd2c918b`다. 검토자는 artifact의 제한된 byte read로 Jinja 본문을 직접 확인했다. 여기에는 tools가 있을 때만 tool JSON 설명을 넣는 분기, user/assistant ChatML role, enable_thinking 분기가 있었으며 일반 출력에 JSON을 요구하는 무조건 지시는 발견하지 못했다. 부분 byte scan이 전체 binary 의미를 감사한 것은 아니다.

## 딱 한 번의 제한 smoke: 가설과 결과

content형식 가설을 부모에게 전달한 뒤 부모가 `work/grounded-text-probe/template_smoke.py`를 실행했다. 새 모델·미디어·환경 설치는 없고 동일 Qwen artifact를 사용했다. 결과는 `work/grounded-text-probe/run-04-template-smoke/results.json`이다.

- 한국어 단순 통제문(대한민국 수도 이름만 plain 출력)과 중립 옹알이 한 문장 요청 두 개를 사용했다. 원래6사례를 다시 최적화하는 실험이 아니다.
- 각 prompt를 기존 content 배열과 SDK가 지원하는 문자열로 각각 전달했다. 최대4 send, output100tokens, process240초 한도. sampler/no-think/모델 hash는 동일하다.
- render 전용 conversation과 실제 send conversation도 각각 새로 만들었다. render의 부작용 가능성이 send 결과에 섞이지 않게 했다.
- 코드상 Python socket 연결 차단과 별도 cache 경로를 사용했다. native 전체 egress를 독립 입증한 실험으로 표현하지 않는다.

| 질문 | 배열 입력 | 문자열 입력 | 해석 |
|---|---|---|---|
| 수도 이름만 | `서울` | `서울` | plain Korean 응답과 기본 지시 준수 가능. 강제 JSON이 모든 응답을 지배한다는 가설과 맞지 않음 |
| 중립 옹알이 | 두 형태 모두 동일한 3인칭 문장. `그의 마음은 어둠이었지만`과 관찰에 없는 `속삭이는 바람` 추가 | 배열과 동일 | JSON 형식 문제는 나타나지 않았으나 사실 충실도·1인칭·자연스러움 실패 |

각 prompt의 **렌더된 문자열은 배열/문자열 간 동일**했다. 한 번의 user ChatML 구간, 정상 assistant 접두어, 닫힌 빈 think 구간이 있었다. prompt가 누락되거나 content dictionary/JSON wrapper 그대로 들어가거나 이중 ChatML로 감싼 흔적은 없었다. 따라서 이번 artifact/runtime 경로는 content 배열을 적절하게 템플릿에 맞춰 처리한다는 실행 증거가 생겼다. Python 코드만 보고 세웠던 content형식 의심은 이 결과로 해소한다.

기존 Qwen6사례의 JSON 출력은 모델이 복잡한 부정 지시를 무시하거나 요청에 등장한 금지 단어/형식을 따라간 결과일 수 있다. 그것이 유일한 원인이라고 증명한 것은 아니다. 짧은 수도 대조의 성공은 긴 생성의 사실성을 보장하지 않으며, 같은 중립 옹알이 prompt에서 동일한 부당 정서 추가가 남았으므로 현재 구성의 채택 실패 판단은 유지한다. **추가 smoke나 새 모델 다운로드를 권고하지 않는다.**

## 소리 보조 정책에서 과한 승인 조건을 분리

옹알이 보조 관찰이 실제 현장 아기와 TV 속 동일한 아기 소리를 반드시 구분해야 한다는 조건은 적절하지 않다. 마이크에 들어온 음향만으로 동일한 파형의 출처를 항상 식별할 수 없다. 해당 출처 확인 불가능성은 명시할 한계이며, 모든 보조 검출을 영구 비활성화할 이유가 아니다.

- 필요한 negative 검증은 **영아 발성 없이** 성인 말·웃음·음악·TV만 있는 자료가 새 baby 표현을 여는지 확인하는 것이다.
- 실제 영아 발성이 재생된 TV 자료는 옹알이/울음이라는 음향 사건을 찾는 것 자체가 곧 오류는 아니다. 현장 아기 신원·현재 얼굴과의 대응·실제 의도를 확인했다고 표시하는 것이 오류다. 이 범주는 출처 미확인 challenge로 따로 기록한다.
- v1의 `Television >= .30` 보류는 보수적인 제품 규칙이다. TV 식별의 완전성이나 현장 신원 보증으로 해석하지 않는다. 이번 결과를 보고 v1을 조용히 바꾸지 않는다.
- 모든 옹알이를100% 회복해야만 어느 개선도 채택할 수 있다는 기준도 정하지 않는다. 미리 정한 독립 source에서 실제 회복이 있고, 기존 수락 울음/웃음이 보존되며, 중요한 negative 오승인과 실행 비용이 확인되어야 제한적인 개선으로 판단할 수 있다.

현재 비활성 판단의 직접 이유는 TV의 불가능한 완전 식별을 요구해서가 아니다. 기존 독립 대상 연령 Sage 자료에서 후보가 없었고, 부모가 사전 고정해 추가 실행한 clod11111개월/NoiseCollector9개월에서도 후보0을 보고했다. 새 자료는 `work/public-perception/int8-target-age-01/scores.json`에 있다. 출처 설명 기반 후보 라벨을 독립 청취 정답으로 바꾸지 않으며, 이 작은 표본으로 일반 정확도를 계산하지 않는다. **현재 자료가 독립 대상 연령 옹알이 개선을 입증하지 못한다**는 좁은 판단을 유지한다. 사후 임계값 낮추기나 회복된 개발 사례를 holdout 성공으로 재분류하지 않는다.

## 사용자 목표와 완료 판단

이 검토와4회 smoke는 synthetic text-only 검사다. 사용자의 최대20초 원본 소리·시간순 영상 해석, 온디바이스 실제 입력, 새 아기말 생성의 전체 요구를 대신하지 않는다. AST 점수/얼굴 관찰을 읽어 기존 문장을 고르는 구현을 새 생성 완료로 보고해서도 안 된다.

현재 단계에서 가능한 정확한 결론은 런타임 content형식 가설 하나를 분리했고, 현재 생성 후보의 사실/언어 실패와 AST의 독립 회복 증거 부족을 확인했다는 것이다. 이 결과만으로 새 기능 베타를 승격하거나 기존0.5.9보다 일반 인식이 좋아졌다고 홍보하지 않는다. 실제 입력 품질과 생성 충실도가 미통과인 상태는 미완료로 남긴다.
