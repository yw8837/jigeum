# Gemma 4 E2B 배포 파일·런타임 결함 조사

2026-10-04. Astra/high 핵심 검토의 후속이다. 공식 모델 카드·모델 저장소 토론·LiteRT-LM/Gemma 공식 저장소의 이슈·릴리스·커밋만 읽었다. 공식 저장소에 올라온 사용자 제보와 관리자가 재현한 결함을 구분했다. `agent-reach`의 GitHub CLI/Jina Reader 경로를 사용했고, Jina가 본문을 생략한 모델 토론은 Hugging Face 공개 API로 확인했다. 모델/SDK 다운로드·설치·ADB·Gradle·추론은 하지 않았다. 개인 원본·개별 관찰 결과를 읽거나 전송하지 않았다. 다른 작업자의 파일을 수정하지 않고 이 문서만 작성했다.

## 결론

**알려진 Gemma/LiteRT-LM 결함은 존재한다. 그러나 고정 E2B 파일과 SDK0.17.1에서 관찰한 S25의 음성 누락·색상 혼입을 설명하고 해결하는 재현 조건·수정 커밋은 찾지 못했다. 현재 후보는 채택 실패 상태다.** 이를 근거 없이 “LLM이라 원래 그렇다”로 확정하지도, 알려진 다른 결함이 있다는 이유만으로 이 실행이 수리 가능하다고 주장하지도 않는다.

새로운 모델·데이터·요구 절충 또는 **이 현상에 적용되는 검증된 upstream 수정 근거** 없이, 같은 후보의 prompt·sampler·버전을 반복해서 바꾸며 실행할 다음 합리적 단계는 현재 확보하지 못했다. 사용자의 온디바이스·장면에 연결된 감정/욕구 추측·기종별 제한 없는 사용 목표는 유지한다. 템플릿 확대나 특정 기기 전용 지원으로 요구를 축소해 완료 처리하지 않는다.

## 조사 기준과 실제 실패

[S25 QA](../qa/ondevice-av-generation/S25-GEMMA-20261004.md)의 합성 결과를 기준으로 삼았다. LLM CPU/GPU 모두 AV10초에서 tone을 놓치고 색상 내용을 음성 요약에 섞었다. GPU20초는 tone을 언급했으나 색상 순서를 반복된 전환으로 바꾸고 구조 출력에도 실패했다. 이 때문에 **음성 입력이 항상 완전히 누락됐다**고 단정할 수도 없다.

CPU/GPU 비교는 LLM backend 비교다. 두 경우 모두 vision GPU/audio CPU였으므로 이 결과만으로 모든 GPU 경로나 공유 audio 전처리의 silent corruption을 배제하지 않는다. 이전 AudioFile→AAR JSON→JNI→native decoder 규약 검토는 재수행하지 않았다. native 실제 tensor 값을 계측하지 않은 한계도 유지한다.

고정 모델은 `gemma-4-E2B-it.litertlm`, 2,588,147,712bytes, SHA256 `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`다. 공개 Hub tree API에서 고정 revision `b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1`와 조사 시점 `main`의 같은 파일이 **동일 size/LFS SHA**임을 확인했다. `main` 조회는 이날의 관찰이며 영구 고정 참조가 아니다. 잘못된 web text-only 파일을 받았거나 이후 수정 파일을 놓쳤다는 증거는 없다.

SDK는 Android0.17.1, tag commit `5e58e9a0aef7abf7091207a8b1d1063a1c800f08`다. 조사 시점 공식 최신 릴리스도0.17.1이었다. [릴리스의 유일한 명시 수정](https://github.com/google-ai-edge/LiteRT-LM/releases/tag/v0.17.1)은 tool-call 정수를 실수로 반환하는 문제이며, [커밋 f300c4f](https://github.com/google-ai-edge/LiteRT-LM/commit/f300c4fdc28b4770c61ae93d24f3f601cfcd19f6)의 변경 파일도 tool parser/constraint 경로다. 이번 일반 AV 요약의 소리 누락 수정은 아니다.

## 근접한 공식 이슈와 적용 여부

| 출처·확인 수준 | 구체적 조건·해결 상태 | S25 실행과의 관계 |
|---|---|---|
| [#1848](https://github.com/google-ai-edge/LiteRT-LM/issues/1848), 신고자가 설정 오류로 종결 | 초기 E2B Python Linux CPU에서 audio/vision backend를 주지 않아 SIGSEGV. 명시 backend 후 성공했다고 신고자가 정정했다. | 현재 audio CPU/vision GPU를 명시했고 생성까지 완료했다. 이 설정 누락이 아니다. |
| [#1874](https://github.com/google-ai-edge/LiteRT-LM/issues/1874), contributor가 설정 수정 안내 | Kotlin0.10.1의 multimodal placeholder 오류. contributor가 새 `maxNumImages` 설정을 안내하며 종결했다. | 현재10/20장 수를 설정했고 실제 색상 입력도 반영한다. custom template 부재를 일반 원인으로 제시할 근거가 없다. |
| [#3260](https://github.com/google-ai-edge/LiteRT-LM/issues/3260), contributor 설명 | JVM0.16.0 CPU crash. `Missing 10 bands` 경고는 Gemma4에서 예상되며 실제 원인은 XNNPACK이라고 설명했다. 다음 릴리스 수정을 예고했으나 해당 댓글에 수정 SHA는 없다. | 초기화/실행 중 crash와 정상 종료한 잘못된 AV 관찰은 다르다. 이 경고만으로 audio 손상 원인을 확정할 수 없다. |
| [#3721](https://github.com/google-ai-edge/LiteRT-LM/issues/3721), **collaborator가 재현 확인** | 같은0.17.1/E2B의 llguidance JSON 제약에서 특수/종료 토큰이 문자열 안에 허용되어 미완성 JSON으로 끝나는 문제. 조사 시 open이며 연결된 배포 수정은 확인하지 못했다. | 구체적인 런타임 결함이지만 S25 runner는 conversation constrained decoding을 끄고 response schema를 전달하지 않았다. 이번 trailing comma·소리 오인의 원인으로 적용할 수 없다. 과거 PC의 LL_GUIDANCE 형식 검증을 과신하지 말아야 할 근거다. |
| [#1850](https://github.com/google-ai-edge/LiteRT-LM/issues/1850), 사용자 patch 및 collaborator 후속 | Pixel8/0.10.x GPU decode의 invalid command queue. 사용자는 `external_tensor_mode=false`, `hint_waiting_for_completion=true`로 crash 해소 보고. collaborator는 내부 Pixel8 GPU 정상이라고 후속 확인했다. | 현재 S25는 해당 abort 없이 응답한다. 구버전 GPU crash patch를 음성 의미 오류의 해결책으로 이식할 근거가 없다. |
| [#2611](https://github.com/google-ai-edge/LiteRT-LM/issues/2611), 사용자 제보 | A55/Xclipse530/0.13.x에서 GPU만 무의미한 다국어 반복, CPU 정상. SOCL memory mapping 오류 동반. 조사 시 open. | 기기·GPU·버전·오류 양상이 다르다. S25의 LLM CPU에서도 남는 음성 관찰 실패 전체를 설명하지 않는다. |
| [#2152](https://github.com/google-ai-edge/LiteRT-LM/issues/2152), C API cache 전달 제보 | C API cache setter가 audio/vision executor에 cache를 전달하지 않아 반복 준비 비용. 보고 자체가 Kotlin/JVM 경로와 구분한다. | 현재 Java→Kotlin API 경로의 의미 오류 원인으로 연결되지 않는다. cache 수정만으로 관찰 품질 회복을 주장할 수 없다. |

## 양자화·MTP·한국어에 관한 근거

[고정 배포 카드](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/blob/b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1/README.md)는 이미 혼합2/4/8bit 모바일 양자화를 사용한다고 명시한다. [모델 토론 #30](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/discussions/30)에서 조직 구성원도 `.litertlm` 파일에 QAT가 적용됐다고 답했다. “QAT를 적용하지 않은 잘못된 파일이므로 새 QAT 파일로 교체하면 해결된다”는 주장은 맞지 않는다. 양자화 손실이 전혀 없다는 증명으로도 읽지 않는다.

[Google E2B 토론 #10](https://huggingface.co/google/gemma-4-E2B-it/discussions/10)은 tool_response 숫자 오류에서 출발했다. 조직 구성원은 재현 자료를 요청했고, 사용자는 주로 GPU/thinking에서 숫자가 깨지고 CPU에서는 정상이라고 보고했다. [LiteRT #2202](https://github.com/google-ai-edge/LiteRT-LM/issues/2202)와 후속 댓글에는0.10–0.13 계열/Mali의 숫자·반복 오류가 추가됐지만, 이 제보를 S25/0.17.1/audio 합성 실패에 대한 관리자의 확정 진단으로 바꾸지 않는다.

[LiteRT 모델 토론 #35](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/discussions/35)는 S23 Ultra/0.13.1 generic 모델의 GPU precision 저하를 보고한다. 조직 구성원은6월·7월 두 차례 구체적 재현 자료를 요청했다. 공개 스레드에서 원인 확정이나 적용할 수정 커밋을 확인하지 못했다. 가까운 모바일 사례이지만 같은 결함의 증명은 아니다.

[모델 토론 #20](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/discussions/20)은 pre-MTP 파일로의 롤백을 권하는 사용자 제보다. 그러나 위 Google #10/#2202의 다른 사용자가 수행한 동일 장치 대조에서는 MTP/pre-MTP 모두 비슷한 tool/JSON 실패가 남았다. 이 수치를 우리 품질 점수로 옮기지 않으며, 과거 파일로 되돌리면 의미 문제가 해결된다는 증거로 사용하지 않는다. [모델 토론 #16](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/discussions/16)의5월 파일 변경 이슈는 구형 Gallery의 engine 생성 실패 및 MTP 켜기 안내였다. 현재 응답까지 완료하는 경로와 다르다.

공식 LiteRT-LM 저장소의 `Korean` 검색은0건이었다. Gemma 공식 저장소에서는 한국어 토큰화 제안과 Gemma3 로마자 출력 문제가 검색됐지만 현재 E2B 배포 파일의 한국어 지시 불이행에 해당하는 수정 근거는 없었다. 검색 누락 가능성이 있으므로 “한국어 버그가 없다”는 증명은 아니다. 또한 이번 S25 소리·색상 검사는 영어 prompt/응답이므로 한국어만의 문제로 설명할 수 없다.

## 공식 권장 설정과 실제 설정의 차이

[공식 Gemma4 카드](https://ai.google.dev/gemma/docs/core/model_card_4)는 temperature1.0/top_p0.95/top_k64와 이미지→텍스트→음성 순서를 권장한다. 이번 S25는 greedy(top_k1/temperature0)와 legacy 텍스트→음성→이미지 순서를 사용했다. 따라서 공식 최적 권장 설정 그대로의 시험이라고 소개하지 않는다.

그러나 이것은 허용되지 않는 API 형식이나 확인된 잘못된 artifact를 썼다는 뜻이 아니다. 기존 PC 권장순서 대조에서도 tone 누락·시각 혼입이 남았고, S25에서 sampler/순서를 바꾼 대조는 없다. **설정의 기여는 미확인**이며, 같은 prompt의 무작위 출력이 우연히 나아질 때까지 sampler를 바꾸는 것이 해결 검증은 아니다. 이 차이만으로 추가 반복 실행이나 채택을 권하지 않는다.

공식 카드의 audio 핵심 평가·예시는 ASR/음성 번역 중심이다. 30초 audio/60초1fps 영상 입력 한도 안에 있다는 사실은 비언어 tone, 아기 발성, 시간에 따른 감정·욕구 맥락의 정확성 보장이 아니다. 카드의 text-only 성능 수치도 이번 AV 비용을 대체하지 않는다.

## 수정 커밋 확인 범위와 종료 판단

0.17.1 이후 Gemma4 data processor 커밋 이력에는 공통 렌더링/응답 변환 정리가 있었으나 이 음성 누락의 수정이라는 설명은 없었다. 같은 기간 native multimodal helper와 MiniAudio 전처리 파일 이력에서 적용할 후속 수정은 확인하지 못했다. [#3688](https://github.com/google-ai-edge/LiteRT-LM/issues/3688)의0.18 nightly string-content 템플릿 문제는 Qwen3/새 content 정규화 조건이며 현재0.17.1 Gemma 배열 템플릿과 다르다. 무조건 nightly로 올리는 근거가 아니다.

Gemma 공식 저장소의 [PR #732](https://github.com/google-deepmind/gemma/pull/732)는 “지시 불이행 수정”이라는 제목과 달리12B/다른 runtime/긴 system prompt·다중 턴 문제를 다룬 미병합 PR이다. 제안자가 일부 가짜 수정을 철회하고 닫았다. 이를 검증된 Gemma E2B 해결책으로 사용하지 않는다.

따라서 **현재 알려진 수정으로 기존 실패를 바로잡는 실행 계획은 성립하지 않는다.** native 수치 계측은 이론상 추가 진단 수단이지만, 무엇을 고치면 사용자 요구가 충족되는지 근거를 이번 조사에서 얻지 못했다. 계측 가능성을 끝없는 다음 실험의 명분으로 제시하지 않는다. 현재 후보의 채택·배포를 보류하고 실패 근거를 보존한다. 이 판단은 모든 온디바이스 모델이나 사용자의 기능 목표가 불가능하다는 결론이 아니다.

외부 조회는 위 공개 페이지·공식 API에 한정했다. `agent-reach check-update`는 v1.5.0 최신을 반환했으며 업데이트·설치는 하지 않았다.
