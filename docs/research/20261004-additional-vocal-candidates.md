# 후속 음성 후보와 독립 라벨 자료 접근 확인

2026-10-04 · 공개 원문/메타데이터 읽기 · 신규 가중치·음원 다운로드/추론 없음

## 현재 판단

새로 확인한 ChildVox와 VTC는 기존 SpeechMaturity 분류기와 다른 후속 후보다.
**ChildVox의 상업 사용 금지 고지, VTC의 녹음 도메인·배포권리·실행 환경 차이를 먼저 확인해야 한다.**
작은 다운로드 숫자나 논문 F1을 아가톡 정확도·모바일 모델 용량으로 옮기지 않는다.
기존 고정 AST의 제한적 회복을 검토하는 작업은 이 조사와 독립적으로 진행한다.

## 1. ChildVox: 실제 공개된 작은 분류 head

원천: [저자 저장소](https://github.com/tiantiaf0627/childvox-release/tree/2af22d97d312913fe92d935bffa824d628661deb),
[Whisper-Base 모델 카드](https://huggingface.co/tiantiaf/childvox-speechmaturity-whisper-base/blob/8df4d5ab7e899dc485488f0ad203afd853fd90c1/README.md),
[모델 API](https://huggingface.co/api/models/tiantiaf/childvox-speechmaturity-whisper-base/revision/8df4d5ab7e899dc485488f0ad203afd853fd90c1?blobs=true).
프로젝트 홈페이지의 “Release Soon” 문구와 달리, 실제 GitHub/HF에는 task head가 공개돼 있다.

- SpeechMaturity와 BabbleCor용 Whisper-Base/Whisper-Large/BabyHuBERT 모델이 있다.
  출력은 `Canonical`, `Non-Canonical`, `Crying`, `Laughing`, `Junk`다.
  카드에서 Junk는 잡음·성인 말 등 비아동 발성을 포함한다고 설명한다. 실제 거절 성능을 확인한 것은 아니다.
- SpeechMaturity Whisper-Base `fold_1/model.safetensors`는 **4,045,728 bytes**,
  공개 LFS SHA256 `5a444daaeff44922537e2f88f10c81beca236548ade1cbecc6d9f7677e7b030f`다.
  파일을 내려받아 해시한 결과가 아닌 공개 메타데이터다. 이것은 **head/LoRA delta**이며 전체 모델이 아니다.
  loader는 별도로 `openai/whisper-base`를 가져온다. 4MB 온디바이스 전체 모델이라고 홍보할 수 없다.
- 저자 예제의 입력은 16kHz mono **최대 1초**다. 10/20초 전체 맥락을 한 번에 이해하거나
  영상·욕구·새 한국어 문장을 생성하는 모델은 아니다. 창 결합과 무발성/성인/혼합 거절 검증이 별도로 필요하다.
- 고정 `whisper_audio.py`에는 특징을 `.cuda()`로 강제 이동하는 코드가 있고,
  wrapper 생성 시 외부 backbone/feature extractor 로더를 호출한다. 현재 PC/Android에 그대로 이식할 경로가 아니다.
  `_from_pretrained`의 `strict=False`도 누락된 키가 의도한 backbone인지 구분한 후 검증해야 한다.
- **HF metadata는 `openrail`이지만 같은 카드와 저장소 README의 Out-of-Scope에 `No commercial use`가 명시돼 있다.**
  더 느슨한 metadata만 선택해 상업 포함 권리가 있다고 판단하지 않는다. 현 유료 앱에 포함하거나 상업 목적 학습에 쓰기 전
  저자의 조건 확인이 필요하다. 이번에는 공개 문서/구조 확인만 했으며 다운로드·설치·학습·추론·저자 연락은 하지 않았다.

## 2. VTC/BabyHuBERT: 발성 주체의 시간 구간 후보

원천: [VTC 소스](https://github.com/LAAC-LSCP/VTC/tree/a66d76548e2ec9cb042b28b8b3106e77ca0fbc13),
[고정 모델 카드](https://huggingface.co/coml/VTC-2/blob/6b1a95508302edc14c50f670cd9a30d66fa4f88a/README.md),
[고정 설정](https://huggingface.co/coml/VTC-2/blob/6b1a95508302edc14c50f670cd9a30d66fa4f88a/model/config.toml),
[모델 API](https://huggingface.co/api/models/coml/VTC-2/revision/6b1a95508302edc14c50f670cd9a30d66fa4f88a?blobs=true).

- VTC는 `KCHI`(마이크를 착용한 아이), `OCH`(다른 아이), `MAL`, `FEM`의 시간 구간을 출력한다.
  보통 0~5세 아이 옷에 단 마이크의 장시간 녹음을 위한 학습이다. 손에 든 휴대폰 영상에서 `KCHI`가
  화면 속 아기라는 의미는 아니다. 얼굴 연결/옹알이 종류/감정·욕구 생성도 별도다.
- 상위 README는 2.2이고 현재 submodule이 가리키는 카드/설정에는 2.1 표기가 남아 있다.
  비교 시 버전 이름보다 정확한 submodule SHA를 사용한다.
- 고정 checkpoint는 **1,099,159,455 bytes**, 공개 LFS SHA256
  `5c3446d037f9c6746cbe19bb77a77973354d3443556f9e2278e83cd4fe7db5a2`다.
  optimizer 포함 여부·실제 추론 가중치 크기·quantization 결과는 이번에 검사하지 않았다.
- 설정은 16kHz/10초 chunk이고 공식 실행기는 overlapping stride와 frame probability를 지원한다.
  단일 장면/clip label보다 시간 구간 근거를 얻을 수 있는 후보지만, 실제 앱 입력에서 실행한 결과는 없다.
- 공식 설치 대상은 Linux/macOS, Python3.13 이상이며 Windows 미지원이라고 명시한다.
  PyTorch/segma 기반이다. 원문의 서버 CPU/GPU 처리량을 휴대폰 속도로 해석하지 않는다.
- GitHub license metadata와 HF card에 명시적인 라이선스가 확인되지 않았다. 이는 전체 연구 금지라는 결론이 아니라
  상업 포함/재배포 권리가 아직 입증되지 않았다는 뜻이다. 이번에는 가중치를 받거나 환경을 설치하지 않았다.

다음 연구 대상으로 삼으려면 고정 모델의 실제 구성·전처리·frame 좌표·CPU 로컬 실행 경로와
목소리 종류/무음 거절을 먼저 검토한다. 동일 약국 자료만으로 임계값을 맞추거나 가상 라벨을 만들지 않는다.

## 3. SMD/BabbleCor: 라벨 의미는 확인, 자료 접근은 미확보

원천: [SMD](https://osf.io/tf3hq/), [공개 README](https://osf.io/download/epz7q/),
[공개 submodule 목록](https://osf.io/download/d2v63/), [BabbleCor](https://osf.io/rz4tx/),
[SMD 논문](https://william-n-havard.github.io/assets/pdf/articles/hitczenko-etal-2024-speechmaturity.pdf).

- SMD README에서 citizen scientist 여러 명의 다수결 라벨, child/corpus grouping, 개별 annotator 라벨,
  source selection, 나이/성별 메타데이터의 실제 schema를 확인했다.
- `child_age`는 **녹음기를 착용한 아이의 나이**로, 해당 순간 발성 주인의 나이와 다를 수 있다고 명시한다.
  `majority_label_age`, `selection` 등의 근거 없이 6~18개월 영아 정답으로 간주하지 않는다.
- `Maturity 2`는 먼저 화자 나이/성별, 아동인 경우 발성 종류를 묻는다. 앞선 캠페인은 성숙도 5-way 중심이다.
  짧은 약 0.5초 clip 라벨이며 20초 자연 장면·face-level active speaker 정답은 아니다.
- 상위 README는 공개됐지만 `Maturity 2`의 명시된 node `pczvt` API는
  **“Authentication credentials were not provided”**를 반환했다. 이 접근 경계에서 중단했다.
  BabbleCor 캠페인은 README에 직접 접근 문의가 필요하다고 명시한다.
  제한된 파일을 다른 경로나 mirror로 우회하지 않았고, 가입·로그인·약관 수락·자료 요청 메시지도 보내지 않았다.

따라서 **전문 라벨 자료의 schema를 확인했지만 음원/독립 평가 묶음은 확보하지 못했다.**
모델 점수를 보고 정답을 만든 결과와 corpus의 원래 라벨을 구별할 수 있는 후보라는 데 의미가 있다.
접근 가능한 새 자료가 없다는 이유로 이미 측정한 고정 AST의 알려진 회복을 무효화하지 않는다.

## 조사 방식과 남은 범위

agent-reach Exa 검색 → Jina/공식 GitHub API/HF API/OSF API의 공개 문서를 읽었다.
Jina의 HF 공유 서비스 차단은 공식 public model API/card 읽기로 전환했고, OSF의 인증 요구는 우회하지 않았다.
비공개 아기 자료·파생 frame/audio/embedding을 검색어나 외부 API에 전송하지 않았다.
Agent Reach v1.5.0 최신 확인. 앱/모델 교체·새 독립 정확도·ARM 성능·배포가 완료됐다는 보고가 아니다.
