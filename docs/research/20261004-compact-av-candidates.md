# 온디바이스 audio+vision 후보 원문 조사

조사일: 2026-10-04 (KST)  
질문: Gemma 4 E2B가 S25 실측에서 실패한 뒤, 선택한 최대 20초의 연속 원음과 시간순 이미지를 같은 문맥에서 읽고 한국어 문장을 생성할 다른 compact 공개 모델이 있는가? 상용 라이선스, Android 또는 LiteRT/llama.cpp 실행 근거, 한국어, 체크포인트와 encoder 크기를 공식 원문으로 대조했다.

## 결론

**현재 확인 범위에서 요구를 모두 충족하며 다음 단계에서 Android 실기기 검증 후보로 넘길 모델은 확인되지 않았다.** Mini-Omni2는 가장 작은 후보군이지만 카드가 영어 출력만을 명시하고, Android/LiteRT/llama.cpp용 통합 audio+video 실행 경로가 확인되지 않는다. MiniCPM-o는 audio/video 기능을 제공하지만 2.6이 8B, 4.5가 9B이며 문서에 나온 llama.cpp 경로는 2.6의 vision-only 모드다. **Qwen2.5-Omni-3B는 존재하고 audio/video를 지원한다. 다만 공식 3B Hub 자료 총량은 약11.99GB, 카드가 Korean을 명시하지 않으며 Android/GGUF 실행 경로가 확인되지 않았다. 라이선스 metadata와 포함 LICENSE도 일치하지 않아 확인이 더 필요하다.** Qwen3-Omni는 한국어 audio 입력·출력과 통합 AV를 명시하지만 30B-A3B 체크포인트이며 공개 파일 총량 약70.5GB이고 공식 Android 경로가 없다. SmolVLM2는 더 작고 on-device video 설명이 있지만 입력은 image/video/text뿐이고 audio가 없다.

따라서 이것은 “더 작은 다음 모델” 선정 보고서가 아니다. 추가 모델 무한 탐색 없이, **현 원문 증거로는 다음 검증 후보 없음**으로 판단한다. 이는 다른 후보가 세상에 없다는 보편적 주장이나 제품 설계 요구 완화가 아니다. Korean baby-vocal quality, 20초 성능, S25 외 보급형 Android 호환성, 네트워크 차단 동작은 어느 후보에서도 검증되지 않았다.

## 비교

| 후보 | 기능·언어 원문 | 상용/배포 및 크기 | Android 실행 증거와 판단 |
|---|---|---|---|
| **MiniCPM-o 2.6 / 4.5** | 공식 모델 카드는 o 2.6을 SigLIP-400M + Whisper-medium-300M + ChatTTS-200M + Qwen2.5-7B 기반 총 **8B**로 명시한다. 실시간 AV 스트림 기능을 설명하나, 음성 대화 언어는 영어·중국어로 명시되어 한국어 생성 근거가 부족하다. o 4.5 README는 end-to-end 실시간 AV 입력을 말하고 총 **9B**라 한다. 이 설명만으로 한국어 baby-talk 품질은 알 수 없다. | 모델 카드/저장소 LICENSE가 Apache-2.0이다. 저장소 README의 사용자 설문은 **선택 참여(optional)**로 적혀 있고 추가 이용 조건으로 서술되지 않는다. Hub의 o 2.6 원본 파일 총량은 **17.43GB**(모델 파트만 합계 약17.35GB); o 4.5 파일 총량은 **20.03GB**다. 이는 다운로드 원본 크기이며 RAM 사용량이 아니다. | 저장소의 llama.cpp 지침은 명시적으로 **“vision-only mode”**라고 한정한다. 공식 OpenBMB 앱의 Android 시연은 별도 MiniCPM-V 비디오/시각 경로이며 o의 오디오+비디오 Android 경로를 입증하지 않는다. **AV Android 후보로는 미충족.** |
| **Mini-Omni2** | 공식 카드가 이미지·음성·텍스트 입력 통합 및 speech 출력 구조를 설명한다. FAQ는 학습 언어가 영어뿐이며 Whisper encoder 덕분에 다른 언어를 이해할 수 있어도 **출력은 영어만**이라고 명시한다. | Hub 카드 MIT. 언어 모델은 0.5B 기반으로 소개되지만, 실제 공개 체크포인트에는 `lit_model.pth` **2.815GB**, `ViT-B-32.pt` **354MB**, `small.pt` **484MB**가 따로 있다. 따라서 “0.5B”를 전체 다운로드 크기로 간주할 수 없다. encoder/보조 weight 합산도 카드의 단일 모델 파라미터 수와 같은 기준이 아니다. | 확인한 공식 README와 저장소에서 Android, LiteRT 또는 llama.cpp AV 통합 실행 사례가 확인되지 않았다. vision/audio 기능 설명은 요구 입력이 한 문맥에서 시간순으로 들어가는 휴대폰 경로의 증거가 아니다. **작지만 한국어 출력·Android 근거에서 탈락.** |
| **Qwen2.5-Omni-3B / Qwen3-Omni-30B-A3B-Instruct** | Qwen2.5-Omni-3B 카드가 text/image/audio/video 통합과 audio·video timestamp 동기화 아키텍처를 설명한다. 모델 카드 metadata 언어는 `en`만 표기하며 본문에서도 Korean 지원을 확인하지 못해 한국어는 **미확인**이다. Qwen3-Omni는 Korean speech input/output을 카드에 직접 적는다. | Qwen2.5-Omni-3B Hub 카드 metadata는 `license: other`, `license_name: qwen-research`이나 해당 고정 revision에 포함된 `LICENSE` 본문은 Apache-2.0 표준문이다. 메타데이터와 파일의 불일치를 확인했으므로 상용 허용 여부는 **라이선스 재확인 전 미확정**으로 둔다. 공개 파일 합계는 약 **11.99GB**이며 safetensors shard 세 개만 약11.97GB다. 3B 파라미터 표기는 전체 배포 파일 크기와 같지 않다. Thinker 출력만 필요하면 `disable_talker()`가 Talker를 제외해 GPU 메모리를 약2GB 절약한다는 모델카드 안내가 있으나 이는 동일 체크포인트에서 Talker 초기화를 끄는 옵션이지 별도 소형 Android/GGUF 배포판이 아니다. Qwen3 저장소는 Apache-2.0이며 공개 파일 합계 약 **70.53GB**(15개 safetensors 포함). `30B-A3B`는 활성 파라미터가 3B라는 뜻이지 체크포인트/전체 메모리가 3B 모델 크기가 아님을 구분한다. encoder 별도 용량은 미확인. | Qwen2.5 공식 repo의 확인된 소스/카드에는 Android 또는 GGUF 실행 경로가 없다. Thinker-only 옵션은 Transformers Python API의 Talker 비활성화이며 Android 경로를 입증하지 않는다. Qwen3 QuickStart도 Transformers 기반이고 Android/LiteRT/llama.cpp 증거를 찾지 못했다. **3B 변형이 존재하고 오디오+영상은 지원하지만 크기·라이선스 명확성·한국어·Android 증거 모두에서 현재 다음 후보는 아니다.** |
| **SmolVLM2-500M-Video-Instruct** | 공식 카드의 모델 입력은 image/multi-image/video/text이고 언어는 English다. audio 입력 모달리티가 없다. | Apache-2.0. “500M” 모델의 HF 저장소 파일 총량은 **7.92GB**(여러 ONNX 변형 포함); safetensors 원본은 약2.03GB다. 카드의 video 추론 1.8GB GPU RAM 표기는 모델 저장소 용량 또는 Android 메모리 보증이 아니다. | 카드에 제한된 자원에서 on-device 적용 가능성을 말하지만 공식 카드에 Android 앱/LiteRT 실행 근거는 없다. 무엇보다 audio 입력이 빠져 요구를 충족하지 않는다. **AV 대체 후보 아님.** |

## 근거와 한계

- 모델 카드의 benchmark 또는 저자 설명은 일반 공개 평가 주장이다. 실제 아기 발성·tone 누락·시간 순서·장면 근거·한국어 아기말 정확도를 입증하지 않는다.
- “멀티모달”이나 같은 체크포인트에 audio와 vision encoder가 포함된 사실만으로 하나의 Android 호출에서 시간 순서가 보존된 AV를 처리한다는 뜻은 아니다.
- 공개된 전체 체크포인트 byte 합계, 파라미터 수, encoder 표기는 서로 다른 양이다. 위 크기는 Hugging Face 공식 API의 고정 revision 파일 메타데이터를 합산했으며 런타임 RAM/압축 후 크기로 해석하지 않는다.
- 이 조사에서는 모델을 받거나 설치·실행하지 않았고 Android, ADB, Gradle 추론 실험을 하지 않았다. 개인 미디어/결과 파일은 열지 않았다.
- 비교 출발점인 앱 설계 및 Gemma S25 실패의 수치는 [온디바이스 AV 설계](../superpowers/specs/2026-10-03-ondevice-av-generation-design.md), [S25 연구 QA](../qa/ondevice-av-generation/S25-GEMMA-20261004.md), [HANDOFF](../HANDOFF.md)를 따른다. 본 문서는 그 실패를 다른 후보의 성공 근거로 돌려 쓰지 않는다.

## 고정 revision의 1차 출처

1. MiniCPM 공식 저장소 README와 LICENSE, commit [`6ada8e8ef5e2979670fc94406f02b87c3c7e7ee0`](https://github.com/OpenBMB/MiniCPM-o/tree/6ada8e8ef5e2979670fc94406f02b87c3c7e7ee0); [o 2.6 카드 고정 revision `06849bfd36da94da1f5a88fa17e8ba63f08a4c4d`](https://huggingface.co/openbmb/MiniCPM-o-2_6/tree/06849bfd36da94da1f5a88fa17e8ba63f08a4c4d); [o 4.5 카드 고정 revision `503e754207c94da6bb26850b4469f367c9ea3582`](https://huggingface.co/openbmb/MiniCPM-o-4_5/tree/503e754207c94da6bb26850b4469f367c9ea3582). README의 llama.cpp 절은 2.6 **vision-only** 실행으로 명시한다.
2. Mini-Omni2 공식 [README 고정 revision `49f474f4c38f80cf716859bb1b1442df2a8dea46`](https://huggingface.co/gpt-omni/mini-omni2/tree/49f474f4c38f80cf716859bb1b1442df2a8dea46), MIT 메타데이터 및 파일 트리. 언어 한계는 README FAQ의 “other languages” 문답.
3. Qwen2.5-Omni 공식 [3B 모델 카드 고정 revision `f75b40e3da2003cdd6e1829b1f420ca70797c34e`](https://huggingface.co/Qwen/Qwen2.5-Omni-3B/tree/f75b40e3da2003cdd6e1829b1f420ca70797c34e) 및 [공식 저장소 commit `d8a31ca56c0456b6edfcbcbf4bdbb6ae2200ef42`](https://github.com/QwenLM/Qwen2.5-Omni/tree/d8a31ca56c0456b6edfcbcbf4bdbb6ae2200ef42)의 README, LICENSE, 경로 목록. 카드의 `disable_talker()`는 약2GB GPU 메모리 절약 안내다. 동일 모델 카드에는 `qwen-research` metadata와 Apache-2.0 LICENSE 본문이 함께 있어 라이선스 충돌을 표기했다.
4. Qwen3 공식 [모델 카드 고정 revision `26291f793822fb6be9555850f06dfe95f2d7e695`](https://huggingface.co/Qwen/Qwen3-Omni-30B-A3B-Instruct/tree/26291f793822fb6be9555850f06dfe95f2d7e695), [공식 소스 저장소 commit `e4235853125589c789f06a2dd83e9f4126df5e9d`](https://github.com/QwenLM/Qwen3-Omni/tree/e4235853125589c789f06a2dd83e9f4126df5e9d) 및 저장소 LICENSE. 한국어 speech input/output 문구는 모델 카드에 직접 표기.
5. Hugging Face 공식 [SmolVLM2 카드 고정 revision `7b375e1b73b11138ff12fe22c8f2822d8fe03467`](https://huggingface.co/HuggingFaceTB/SmolVLM2-500M-Video-Instruct/tree/7b375e1b73b11138ff12fe22c8f2822d8fe03467). 모델 요약의 모달리티 목록과 Apache-2.0 표기.

크기 계산은 각 카드 revision의 공식 Hub `/api/models/{id}/tree/{revision}?recursive=true&expand=true` 파일 `size` 메타데이터 합산이다. 레포의 최신 가변 브랜치가 아니라 위에 고정한 revision을 기준으로 한다.
