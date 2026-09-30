# 아기(6~18개월) 표정·옹알이 분석 앱 개발 참고 링크

## 1. 핵심 오픈소스 저장소

### YAMNet — 오디오 이벤트 분류
- 저장소: https://github.com/tensorflow/models/tree/master/research/audioset/yamnet
- 관련 목적: 울음, 웃음, 옹알이, 비언어적 발성 등 오디오 이벤트 분류
- 참고 문서: https://www.tensorflow.org/hub/tutorials/yamnet
- AudioSet 데이터셋: https://research.google.com/audioset/
- AudioSet 클래스 목록: https://github.com/tensorflow/models/blob/master/research/audioset/yamnet/yamnet_class_map.csv
- TensorFlow Models 이슈: https://github.com/tensorflow/models/issues
- TensorFlow Models 프로젝트: https://github.com/tensorflow/models/projects

### MediaPipe — 얼굴·표정·랜드마크 분석
- 저장소: https://github.com/google-ai-edge/mediapipe
- 공식 문서: https://ai.google.dev/edge/mediapipe/solutions/guide
- Face Landmarker: https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker
- Face Detection: https://ai.google.dev/edge/mediapipe/solutions/vision/face_detector
- 얼굴 표정 Blendshape 안내: https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker#blendshapes
- Python 예제: https://github.com/google-ai-edge/mediapipe/tree/master/mediapipe/tasks/python
- Android 예제: https://github.com/google-ai-edge/mediapipe/tree/master/examples
- MediaPipe 오픈 이슈: https://github.com/google-ai-edge/mediapipe/issues?state=open
- MediaPipe 프로젝트: https://github.com/google-ai-edge/mediapipe/projects

### LiteRT — 모바일·엣지 디바이스 추론
- 저장소: https://github.com/google-ai-edge/LiteRT
- 공식 문서: https://ai.google.dev/edge/litert
- 개요: https://ai.google.dev/edge/litert/next/overview
- 모델 변환: https://ai.google.dev/edge/litert/models/convert
- Android 배포: https://ai.google.dev/edge/litert/android
- iOS 배포: https://ai.google.dev/edge/litert/ios
- GPU·NNAPI 등 가속: https://ai.google.dev/edge/litert/performance
- LiteRT 오픈 이슈: https://github.com/google-ai-edge/LiteRT/issues?state=open
- LiteRT 프로젝트: https://github.com/google-ai-edge/LiteRT/projects
- LiteRT Pull Requests: https://github.com/google-ai-edge/LiteRT/pulls

### librosa — 오디오 전처리·특징 추출
- 저장소: https://github.com/librosa/librosa
- 공식 문서: https://librosa.org/doc/latest/
- 오디오 로딩: https://librosa.org/doc/latest/generated/librosa.load.html
- MFCC: https://librosa.org/doc/latest/generated/librosa.feature.mfcc.html
- Mel Spectrogram: https://librosa.org/doc/latest/generated/librosa.feature.melspectrogram.html
- RMS 에너지: https://librosa.org/doc/latest/generated/librosa.feature.rms.html
- Zero Crossing Rate: https://librosa.org/doc/latest/generated/librosa.feature.zero_crossing_rate.html
- 음성 구간 분리: https://librosa.org/doc/latest/generated/librosa.effects.split.html
- librosa 오픈 이슈: https://github.com/librosa/librosa/issues?state=open
- librosa Pull Requests: https://github.com/librosa/librosa/pulls

### voc2vec — 비언어적 발성 표현 학습
- 저장소: https://github.com/koudounasalkis/voc2vec
- 논문 검색: https://scholar.google.com/scholar?q=voc2vec+A+Foundation+Model+for+Non-Verbal+Vocalization
- 관련 목적: 옹알이, 웃음, 울음, 비언어적 발성 등의 표현 학습
- voc2vec 오픈 이슈: https://github.com/koudounasalkis/voc2vec/issues
- voc2vec Pull Requests: https://github.com/koudounasalkis/voc2vec/pulls

## 2. 아기 울음·옹알이·발성 데이터셋 및 연구 자료

### Donate-a-Cry Corpus
- 정보 페이지: https://github.com/gveres/donateacry-corpus
- 목적: 아기 울음소리 분류 및 분석 연구

### Baby Chillanto Database
- 검색 링크: https://www.google.com/search?q=Baby+Chillanto+Database+infant+cry+dataset
- 목적: 영아 울음의 상태·생리적 특성 분석 연구

### CryCeleb
- 검색 링크: https://www.google.com/search?q=CryCeleb+baby+cry+dataset
- 목적: 아기 울음 음성 표현 및 화자·상태 분석 연구

### AudioSet
- 홈페이지: https://research.google.com/audioset/
- GitHub 메타데이터: https://github.com/audioset/ontology
- 목적: Crying baby, laughter, speech, vocalization 등 오디오 클래스 참고

### CHILDES
- 홈페이지: https://childes.talkbank.org/
- 데이터베이스: https://talkbank.org/
- 목적: 아동 언어 발달 및 발성 연구용 데이터 참고
- 주의: 데이터 사용 조건과 개인정보·연구윤리를 반드시 확인

### Wordbank
- 홈페이지: https://wordbank.stanford.edu/
- GitHub: https://github.com/childlanguage/wordbank
- 목적: 아동 언어 발달 및 초기 언어 습득 데이터 참고

### Databrary
- 홈페이지: https://databrary.org/
- 목적: 영유아 행동·시청각 발달 연구 자료
- 주의: 대부분 연구자 승인 및 이용 조건이 필요함

## 3. 얼굴 표정·행동 분석 관련 저장소

### OpenFace
- 저장소: https://github.com/TadasBaltrusaitis/OpenFace
- 목적: 얼굴 랜드마크, Action Unit, 시선 및 머리 자세 분석

### OpenSeeFace
- 저장소: https://github.com/emilianavt/OpenSeeFace
- 목적: 실시간 얼굴 추적 및 랜드마크 분석

### facial-expression-recognition.PyTorch
- 저장소: https://github.com/sbitigham/facial-expression-recognition.PyTorch
- 목적: 얼굴 표정 분류 모델 참고

### DeepFace
- 저장소: https://github.com/serengil/deepface
- 목적: 얼굴 분석 파이프라인 참고
- 주의: 일반 성인 얼굴 데이터 기반 모델을 영아에게 적용할 때 성능 검증 필요

### FER
- 저장소: https://github.com/justinshenk/fer
- 목적: 얼굴 표정 분류 및 감정 인식 참고

## 4. 오디오·음성 처리 및 모델 학습 저장소

### PyTorch Audio
- 저장소: https://github.com/pytorch/audio
- 문서: https://pytorch.org/audio/stable/
- 목적: 오디오 전처리, 특징 추출, 음성 모델 학습

### Hugging Face Audio Classification
- 문서: https://huggingface.co/docs/transformers/tasks/audio_classification
- 모델 검색: https://huggingface.co/models?pipeline_tag=audio-classification
- 데이터셋 검색: https://huggingface.co/datasets?task_categories=task_categories:audio-classification

### SpeechBrain
- 저장소: https://github.com/speechbrain/speechbrain
- 목적: 음성·오디오 분류, 임베딩, 전이학습

### Asteroid
- 저장소: https://github.com/asteroid-team/asteroid
- 목적: 음원 분리 및 오디오 신호 처리

### Essentia
- 저장소: https://github.com/MTG/essentia
- 목적: 오디오 신호 분석 및 음악·음성 특징 추출

### Torchaudio Tutorials
- 문서: https://pytorch.org/audio/stable/tutorials.html
- 목적: 오디오 분류 및 스펙트로그램 처리 예제

## 5. 모바일·웹 실시간 카메라 및 오디오 처리

### MediaPipe Tasks
- 문서: https://ai.google.dev/edge/mediapipe/solutions/guide
- Vision Tasks: https://ai.google.dev/edge/mediapipe/solutions/vision
- Audio Tasks: https://ai.google.dev/edge/mediapipe/solutions/audio

### Android CameraX
- 문서: https://developer.android.com/media/camera/camerax
- 저장소: https://github.com/androidx/androidx
- 목적: 실시간 카메라 프레임 처리

### AVFoundation
- 문서: https://developer.apple.com/av-foundation/
- 목적: iOS 카메라·마이크 입력 처리

### Web Audio API
- 문서: https://developer.mozilla.org/en-US/docs/Web/API/Web_Audio_API
- 목적: 브라우저에서 마이크 오디오 처리

### WebRTC
- 저장소: https://github.com/webrtc
- 목적: 실시간 오디오·비디오 스트리밍

## 6. 추천 앱 처리 파이프라인

```text
카메라·마이크 입력
        │
        ├── 얼굴 검출
        │     └── MediaPipe Face Detection
        │
        ├── 얼굴 랜드마크·Blendshape 추출
        │     └── MediaPipe Face Landmarker
        │
        ├── 오디오 전처리
        │     ├── librosa
        │     ├── Mel Spectrogram
        │     ├── MFCC
        │     └── 음량·피치·발성 길이
        │
        ├── 오디오 분석
        │     ├── YAMNet
        │     ├── voc2vec
        │     └── 자체 학습 분류 모델
        │
        ├── 시계열 정보 결합
        │     ├── 표정 변화
        │     ├── 발성 종류
        │     ├── 발성 지속 시간
        │     └── 움직임·상황 정보
        │
        ├── LiteRT 모델 변환·최적화
        │
        └── 보호자에게 자연어로 안내
              ├── “기분이 좋아 보일 가능성이 있어요”
              ├── “졸리거나 피곤한 상태일 수 있어요”
              └── “배고픔이나 불편함 여부를 확인해 보세요”