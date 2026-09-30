# H1. 먼저 읽을 발달·해석 기준
1. CDC 6개월 이정표 | 소리 주고받기 등
   https://www.cdc.gov/act-early/milestones/6-months.html
2. CDC 9개월 이정표 | 다양한 표정, 반복 발성
   https://www.cdc.gov/act-early/milestones/9-months.html
3. CDC 12개월 이정표 | 몸짓, 부모 호칭 등
   https://www.cdc.gov/act-early/milestones/1-year.html
4. CDC 15개월 이정표 | 단어 시도, 가리키기 등
   https://www.cdc.gov/act-early/milestones/15-months.html
5. CDC 18개월 이정표 | 단어와 간단한 지시 이해
   https://www.cdc.gov/act-early/milestones/18-months.html
6. NIDCD 언어·청각 발달 안내 | 월령별 발성과 개인차
   https://www.nidcd.nih.gov/health/speech-and-language
7. 옹알이 발달의 자연환경 녹음 연구
   https://pmc.ncbi.nlm.nih.gov/articles/PMC5869132
8. 사회적 발성과 혼자 하는 발성 놀이를 구분한 연구
   https://pmc.ncbi.nlm.nih.gov/articles/PMC7406057/
9. 영아 울음 분류 연구의 성능·라벨 한계를 검토한 체계적 문헌고찰
   https://pmc.ncbi.nlm.nih.gov/articles/PMC12804663/

# H2. 사용자가 준 5개 저장소: 앱에서 맡길 역할
1. YAMNet | 521종 일반 소리 이벤트 분류·임베딩. 옹알이의 '뜻' 해석기는 아님.
   https://github.com/tensorflow/models/tree/master/research/audioset/yamnet
   주의: 현재 README는 Keras 2 의존성과 Keras 3 비호환성을 명시.
2. MediaPipe | 얼굴 랜드마크·오디오 처리용 크로스플랫폼 도구
   https://github.com/google-ai-edge/mediapipe
3. LiteRT | 학습한 모델을 휴대전화 등 기기에서 실행하는 런타임
   https://github.com/google-ai-edge/LiteRT
4. librosa | mel 스펙트로그램·MFCC 등 음향 특징 추출·실험 도구
   https://github.com/librosa/librosa
5. voc2vec | 비언어적 발성 표현 모델. 학습 자료에 아기 소리가 일부 있지만
   6~18개월의 욕구·심리 판독 성능을 입증한 모델은 아님.
   https://github.com/koudounasalkis/voc2vec

# H3. 음성 파이프라인과 실행 예제
1. MediaPipe Audio Classifier 공식 안내
   https://developers.google.com/edge/mediapipe/solutions/audio/audio_classifier
2. MediaPipe 오디오 분류 Python 예제
   https://github.com/google-ai-edge/mediapipe-samples/blob/main/examples/audio_classifier/python/audio_classification.ipynb
3. MediaPipe 오디오 분류 Android 예제
   https://github.com/google-ai-edge/mediapipe-samples/tree/main/examples/audio_classifier/android
4. YAMNet 임베딩을 활용한 전이학습 튜토리얼
   https://www.tensorflow.org/tutorials/audio/transfer_learning_audio
5. Silero VAD | 일반 음성 활동 감지. 영아 울음·옹알이 검출기로 가정하지 말 것.
   https://github.com/snakers4/silero-vad
6. VTC | 장시간 녹음에서 아동·성인 발성 구간 구분. 대상 연령과 사용권 확인 필요.
   https://github.com/LAAC-LSCP/VTC
7. openSMILE | 음성 특징 추출. 공개판의 상용 제품 사용 조건 별도 확인 필요.
   https://github.com/audeering/opensmile
8. LiteRT 공식 안내
   https://developers.google.com/edge/litert
9. LiteRT 샘플 앱 모음
   https://github.com/google-ai-edge/litert-samples

# H4. 영아 발성 데이터·연구 프로젝트
1. CHILDES Korean Ko Corpus | 한국어 모자 상호작용; 말 이전 집단 평균 8개월,
   초기 발화 집단 평균 13개월. 접근·재사용 조건 확인.
   https://talkbank.org/childes/access/EastAsian/Korean/Ko.html
2. HomeBank ACLEW 주석 | 영아 자연환경 녹음의 발성·상호작용 주석;
   일부 원음은 제한 접근.
   https://talkbank.org/homebank/access/Public/ACLEW.html
3. SEEDLingS | 6~17개월 종단 녹음. 원음은 회원 접근 대상.
   https://talkbank.org/homebank/access/Password/Bergelson.html
4. HomeBank 접근 정책
   https://homebank.talkbank.org/access/
5. Donate-a-Cry | 보호자 제공 울음 녹음. 울음 원인 라벨은 보호자 추정이며
   월령 구간이 넓어 6~18개월만 깔끔하게 분리하기 어려움.
   https://github.com/gveres/donateacry-corpus
6. ICSD | 영아 울음 이벤트 탐지 코드·데이터 안내. 데이터 접근 승인 확인.
   https://github.com/QingyuLiu0521/ICSD/
   https://huggingface.co/datasets/QingyuLiu1/ICSD
7. DeepInfant | iOS/CoreML 울음 분류 구현 참고. 자체 정확도 주장과
   독립적으로 검증된 성능을 구분할 것.
   https://github.com/skytells-research/DeepInfant
8. 영아·어머니 발성을 cry/fuss/laugh/babble/screech 등으로 분류한 연구
   https://doi.org/10.1016/j.specom.2021.07.010
9. ComParE 2019 Baby Sounds | 발성 유형 벤치마크 참고;
   데이터 다운로드·상용 재사용 권한은 별도 확인.
   http://www.compare.openaudio.eu/wp-content/uploads/2019/03/INTERSPEECH_2019_ComParE.pdf

# H5. 얼굴·표정 파이프라인과 영아 전용 검증
1. MediaPipe Face Landmarker | 랜드마크·blendshape 출력.
   출력 점수 자체가 영아의 감정이나 의도를 뜻하지는 않음.
   https://developers.google.com/edge/mediapipe/solutions/vision/face_landmarker
2. Face Landmarker Android 예제
   https://github.com/google-ai-edge/mediapipe-samples/tree/main/examples/face_landmarker/android
3. InfAnFace | 영아 얼굴 랜드마크 검출·추적 연구.
   감정 라벨 데이터가 아니며 데이터의 제품 편입·상용 이용 제한 확인 필수.
   https://github.com/ostadabbas/Infant-Facial-Landmark-Detection-and-Tracking
   https://arxiv.org/html/2110.08935v3
4. Infant AFAR | 영아 얼굴 행동단위(AU) 연구. 13개월 자료가 일부 해당;
   저장소는 완성된 실행 엔진으로 가정하지 말 것.
   https://github.com/AffectAnalysisGroup/InfantAFAR
   https://link.springer.com/article/10.3758/s13428-022-01863-y
5. PyAFAR | 영아 AU 검출 후보. 공개 라이선스가 비상업적 연구용이므로
   상용 앱 적용 전 별도 허가 필요.
   https://github.com/AffectAnalysisGroup/PyAFAR
   https://pyafar.org/
6. Baby FaceReader 9의 4·8개월 영아 검증 연구 및 분석 저장소
   https://pubmed.ncbi.nlm.nih.gov/38273072/
   https://github.com/MZaharieva/Baby_FaceReader9_Validation
7. Tromsø Infant Faces | 4~12개월 표정 사진 연구; 연령·대표성 제한
   https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2017.00409/full
8. City Infant Faces | 0~12개월 사진 자료; 제공·이용 조건 확인
   https://link.springer.com/article/10.3758/s13428-017-0859-9
9. 신생아 얼굴 기반 통증 평가의 기술적 과제 | 신생아 통증 연구이지
   6~18개월의 일상 감정 판독 근거는 아님.
   https://pmc.ncbi.nlm.nih.gov/articles/PMC10594024/

# H6. 음성+영상 결합과 응답 문구 설계에 참고할 연구
1. 영아가 다른 영아의 얼굴·목소리 정서 단서를 연결하는 연구
   https://pmc.ncbi.nlm.nih.gov/articles/PMC3843965
2. 영아 발성에 대한 양육자 반응과 상호작용 연구
   https://pmc.ncbi.nlm.nih.gov/articles/PMC6004332/
3. 얼굴 움직임만으로 감정을 단정하기 어려운 이유를 검토한 연구
   (영아 전용 연구는 아님)
   https://pmc.ncbi.nlm.nih.gov/articles/PMC6640856
4. 초기 발성의 턴테이킹과 양육자 반응 연구
   https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2018.01510/full

# H7. 구현 전에 볼 구체적 GitHub 이슈
1. MediaPipe #4130 | 얼굴 랜드마크의 시간적 흔들림 사례.
   과거 보고이며 현재 모든 버전의 결함이라는 뜻은 아님.
   https://github.com/google-ai-edge/mediapipe/issues/4130
2. MediaPipe #6291 | 온디바이스 입력 처리와 별개인 사용 지표 전송 논의.
   종료된 이슈지만 앱의 실제 네트워크 동작을 확인할 때 유용.
   https://github.com/google-ai-edge/mediapipe/issues/6291
3. MediaPipe #6355 | 웹 번들 모델 가중치의 상업적 재배포 권한 질의.
   코드 라이선스와 모델·데이터 권한을 따로 확인해야 함.
   https://github.com/google-ai-edge/mediapipe/issues/6355
4. Silero VAD #765 | 고음 음성 누락 보고. 영아 적용 전 별도 시험 필요.
   https://github.com/snakers4/silero-vad/issues/765
5. VTC #18 | 장시간 파일에서 필요한 구간만 추론하는 기능 요청
   https://github.com/LAAC-LSCP/VTC/issues/18
6. ICSD #4 | 공개 설정으로 논문 성능 재현이 어렵다는 사용자 보고
   https://github.com/QingyuLiu0521/ICSD/issues/4

# H8. 아기 영상·음성의 동의, 보관, 출시 정책
1. 개인정보보호위원회 아동·청소년 개인정보 보호 안내서
   https://pipc.go.kr/np/cop/bbs/selectBoardArticle.do?bbsId=BS217&mCode=G010030000&nttId=10896
2. 개인정보 보호법 제22조의2 | 만 14세 미만 아동의 동의 관련 조항
   https://www.law.go.kr/LSW//lsLinkCommonInfo.do?ancYnChk&chrClsCd=010202&lsJoLnkSeq=1029334873
3. FTC COPPA 사업자 안내 | 아동의 얼굴·목소리가 담긴 파일을 포함한
   개인정보 수집, 부모 동의, 보관·삭제 검토
   https://www.ftc.gov/business-guidance/resources/childrens-online-privacy-protection-rule-six-step-compliance-plan-your-business
4. Google Play Families 정책 | 카메라·마이크 데이터와 외부 SDK 검토
   https://support.google.com/googleplay/android-developer/answer/9893335?hl=en-GB
5. UNICEF 아동을 위한 AI 지침 | 프라이버시·안전·설명 책임
   https://www.unicef.org/innocenti/reports/policy-guidance-ai-children

# H9. 이 자료로 잡을 수 있는 첫 MVP 범위
1. 기기에서 짧은 영상·음성 분석 → 아기/보호자 소리 분리 →
   웃음·울음·옹알이 같은 '관찰 가능한 발성 유형'과 얼굴 움직임 표시.
2. 보호자가 수면·수유·놀이 직후인지 선택 → 앱은 가능한 해석을
   질문형 대화로 제시: "졸린 건지, 놀이를 더 원하는 건지 살펴볼까요?"
3. 원본 자동 저장·업로드는 기본값으로 켜지 말고, 보호자 동의와 삭제
   경로를 설계. 월령별·기기별·조명별로 별도 시험셋을 만들어 검증.
4. "배고파요", "아파요", "자폐 징후입니다"처럼 영상·소리만으로
   확정하거나 진단하는 표현은 초기 제품에서 제외.
