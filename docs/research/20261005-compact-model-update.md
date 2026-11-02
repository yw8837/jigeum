# 작은 로컬 모델·양자화 추가 조사

조회: 2026-10-05. 질문: Gemma가 아가톡에 최선인가, 더 작은 양자화 모델은 없는가?

## 판단

**Gemma가 최선이라고 입증하지 못했다. 로컬 LLM을 넣는 것 자체도 옹알이 검출 개선을 보장하지 않는다.**
현재 Gemma 파일은 이미 혼합 2/4/8bit 모바일 QAT를 사용한다. 양자화는 용량·연산 비용을 줄이는 수단이며,
아기 소리 지식을 추가하는 학습이나 인식 정확도 개선과는 별개다. 더 작은 파일이 음성·영상 encoder를
포함하지 않으면 같은 기능의 대안으로 비교할 수 없다.

이번에 추가 확인한 **MiniCPM-V 4.6**은 실제 Android 코드와 약 **1.638GB** Q4_K_M+시각 projector를
갖춘 유효한 *영상 관찰* 연구 후보다. 다만 원음 입력을 받는 omni 모델은 아니다. 별도의 발성 검출·시간
정렬과 결합해야 하고, 아기 표정·발성 주체 연결·한국어 표현 품질은 이 앱에서 미검증이다.
이 발견을 Gemma보다 아기 소리 인식이 좋다는 증거로 쓰지 않는다.

## 확인한 후보

크기는 실제 Hub 파일 메타데이터의 **decimal GB**다. 실행 RAM, 설치 전체 크기, 모든 변형을 더한
저장소 크기와 구분했다. 서로 대체되는 checkpoint 포맷은 중복 합산하지 않았다.

| 후보 | 입력·용량 근거 | 모바일·우리 목적 판단 |
|---|---|---|
| Gemma 4 E2B 현재 LiteRT 파일 | `gemma-4-E2B-it.litertlm` 2,588,147,712B. 카드에 혼합2/4/8bit. 시각·오디오 encoder는 필요할 때 로딩 | Android 경로가 있으나 기존 S25 합성 AV 의미 검사가 미통과. 최선/완성품으로 확정 불가 |
| Gemma E2B GPU 파일 | 같은 고정 tree에 `gemma-4-E2B-it-gpu.litertlm` 2,008,432,640B | 더 작다는 사실만 확인. 카드에 이 파일별 AV 동시 지원이 명확하지 않아 동일 기능 대체안으로 확정하지 않음 |
| Gemma E2B Web 파일 | 2,008,432,640B. 카드가 **text-only**라고 명시 | 아기 원음+영상 분석 대체안에서 제외 |
| 공식 Gemma E2B QAT Q4_0 GGUF | LLM 3,349,516,256B + mmproj 986,833,664B = 4,336,349,920B | 현재 파일보다 큼. 다른 runtime 검토 경로이며 더 작은 해결책이 아님 |
| **MiniCPM-V 4.6 1.3B** | Q4_K_M LLM 529,101,504B + f16 mmproj 1,108,746,944B = **1,637,848,448B**. image/video/text | 공식 Kotlin/Gradle Android demo와 build 안내 확인. 저자 권장 RAM ≥6GB. 음성 입력 없음. **영상 보조 연구 후보**이며 단독 AV 대체품 아님 |
| Megrez-3B-Omni | image/text/audio. 공개 safetensors 2조각 합계 8,018,463,368B. 저자는 중국어·영어 speech 입력을 설명 | “on-device”라는 이름만으로 Android 20초 AV 품질/속도를 입증하지 않음. 공식 읽은 경로에서 Android 통합·한국어 아기 발성 검증 미확인 |
| Phi-4-multimodal-instruct | 이미지+음성 공동 입력 지원. 본체 safetensors 3조각 합계 11,149,227,208B, speech/vision adapter가 별도 공개됨 | 멀티모달 기능은 실제이나 이번 자료에서 작은 Android 완성 경로·영아 검증을 확보하지 못함. 다른 모델보다 더 작다고 소개할 근거 없음 |
| Voxtral-Mini-3B-2507 | 오디오 이해 모델. HF safetensors 2조각 합계 9,356,474,312B. 별도의 consolidated 파일은 대체 포맷이므로 더하지 않음 | 영상 encoder가 없는 음성 연구 경로. 현재 요구의 AV 일체형 대안 아님 |
| Omni-Embed-Mini-0.9B | 검색에서 발견했으나 지정 공식 Hub endpoint가 HTTP401 | 검색 요약을 가중치 접근/기능 확인으로 취급하지 않음. embedding 모델을 문장 생성 모델로 간주하지 않음 |

기존 [compact AV 조사](20261004-compact-av-candidates.md)의 Mini-Omni2, MiniCPM-o2.6/4.5,
Qwen2.5-Omni3B/Qwen3-Omni, SmolVLM2와 함께 본다. 기존 문서의 “확인한 후보 없음”은 당시의
**오디오+영상 공동 입력·한국어·Android 등 전체 조건**에 대한 판단이다. 이번 시각 전용 후보 발견과
모순되지 않는다. MiniCPM-o4.5의 데스크톱 llama.cpp-omni 경로와 V4.6의 Android 경로도 섞지 않는다.

## Google의 ‘1GB’ 설명에서 빠뜨리면 안 되는 조건

[Google QAT 원문](https://blog.google/innovation-and-ai/technology/developers-tools/quantization-aware-training-gemma-4/)은
2026-06-05 모바일 양자화, static activation, channel-wise/targeted2bit, embedding/KV cache 최적화를
설명한다. **text-only·Per-Layer Embeddings 제외 시 1GB 미만**이라는 예시도 포함한다.
이를 원음과 시각 encoder를 모두 쓰는 아가톡의 전체 다운로드/실행 메모리가 1GB라는 뜻으로 해석하지 않는다.

LiteRT 카드의 성능 표도 1024 prefill/256 decode/2048 context, cache 준비 상태의 text benchmark다.
모델 로드 시간은 TTFT에서 제외한다. 따라서 표의 S26 처리량을 S25의 아기 20초 분석 시간으로 환산하지 않는다.
기존 발열 시험의 순서·냉각 미통제 한계는 [S25 QA](../qa/ondevice-av-generation/S25-GEMMA-20261004.md)와
[지원 인계](20261004-gemma-support-handoff.md)를 따른다.

## 앱에 연결할 우선순위

1. **들리는 소리가 모델 전에 차단되는 결함부터 수정.** 전체 평균 RMS의 무음 희석을 재현하고,
   같은 소리의 앞/중간/끝 위치·무음 패딩·저주파 잡음 대조로 확인한다.
2. **아기 발성에 맞는 작은 판별 모델을 우선 비교.** 어린 발성/성인 말/배경음의 구분이 첫 문제다.
   작은 모델의 존재와 실제 배포 가능한 head·권리·분리 평가를 확인한다.
3. **영상은 시간순 관찰로 검증.** MiniCPM-V4.6은 별도 연구 후보로 보관하되, 프레임만 넣으면 소리
   주체·욕구가 증명된다고 하지 않는다. 원음 시간표와 맞춘 얼굴/입 움직임 검증이 필요하다.
4. **새 문장 생성은 관찰 개선 이후에 판단.** 텍스트 LLM에 잘못된 소리 라벨을 주면 유창한 오해가
   생길 수 있다. 자유 문장이 검출 개선을 대신하지 않는다.

## 조사 경로·한계

- Agent Reach Exa 검색으로 후보 발견 → GitHub 공식 REST/README/tree → Hugging Face 공식
  API의 고정 revision/파일 크기/카드 → Google 공식 QAT 원문을 Insane Search로 읽었다.
- 공개 메타데이터/문서만 조회했다. 새 가중치 다운로드·모델 추론·아기 자료 외부 전송은 없다.
- 여기서 완전 탐색이나 승자 모델을 선언하지 않는다. 작은 음성·영상 모델의 결합도 Android
  속도·발열·오인·취소·배포 권리를 검증한 뒤에만 제품 후보가 된다.

## 고정 출처

1. [Gemma LiteRT tree](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/tree/b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1), 같은 revision README.
2. [Gemma Q4_0 tree](https://huggingface.co/google/gemma-4-E2B-it-qat-q4_0-gguf/tree/675cff42a74c774d6cb76f76d8eacb49b48c9b93).
3. [MiniCPM-V4.6 GGUF](https://huggingface.co/openbmb/MiniCPM-V-4_6-gguf/tree/afe9accb78d2995d214cd912920c9c92f4015faa), [기본 모델](https://huggingface.co/openbmb/MiniCPM-V-4_6/tree/36f34a661a4bd35d0dc2294cb044d2584646c7d3).
4. [공식 Android demo·RAM·파일 표](https://github.com/OpenBMB/MiniCPM-V-edge-demo/tree/d1aac05eecaac64b7fc35f1cc93a06b726ce10e3).
5. [Megrez 모델](https://huggingface.co/infinigence/Megrez-3B-Omni/tree/2a15ac85df31645708eb0bdf769aa1f92eb44a31), [공식 코드](https://github.com/infinigence/Infini-Megrez-Omni/tree/031e8160e3846f81245495980c9ff53f08ed55c7).
6. [Phi4 모델 카드·files](https://huggingface.co/microsoft/Phi-4-multimodal-instruct/tree/93f923e1a7727d1c4f446756212d9d3e8fcc5d81).
7. [Voxtral Mini 모델 카드·files](https://huggingface.co/mistralai/Voxtral-Mini-3B-2507/tree/3060fe34b35ba5d44202ce9ff3c097642914f8f3).
