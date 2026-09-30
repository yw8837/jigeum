# 레퍼런스 지도

사용자 원본 자료, 기존에 읽은 공식 자료, 제품에 실제로 탑재한 것을 구분한다. 링크의 내용과 가격은 바뀔 수 있으며 조사 기준일은 2026-09-30이다. 이번 인수인계 문서 정리는 전체 웹 문헌을 새로 검증한 조사가 아니다.

## 사용자 원본

- [자료 1: 엔진·데이터셋·파이프라인](references/originals/user-reference-1.md)
- [자료 2: 발달·영아 연구·구체적 이슈·정책](references/originals/user-reference-2.md)
- [자료 3](references/originals/user-reference-3.md): **자료 2와 바이트 단위로 동일하다.** 세 첨부를 모두 보존하되 별도 연구 근거로 중복 계산하지 않는다.
- [KOTA QA 원본](references/originals/KOTA_QA_2026-09-18.md): 사용자가 제공한 다른 프로젝트의 자료다. 본 앱의 실행 결과가 아니다.
- [파일 SHA·출처 목록](references/source-manifest.json): 원본 복사본, 승인된 마케팅 입력 자료, QA 기준본의 무결성을 확인하는 목록이다.

원문에 있는 ‘추천 파이프라인’이나 작업 명령은 참고 제안이다. 본 앱이 모두 구현했다는 증거가 아니며, 원문과 논문 전체에 대한 상용 이용 허가를 받았다는 뜻도 아니다.

## 핵심 GitHub 자료 5개

| 원본 | 실제 사용 | 역할과 제한 |
|---|---|---|
| [YAMNet](https://github.com/tensorflow/models/tree/master/research/audioset/yamnet) | **사용** | MediaPipe AudioClassifier에서 .tflite 실행. 일반 오디오 사건 521종, 1024차원 임베딩, 16kHz 입력. 영아 욕구 번역기가 아니다. |
| [MediaPipe](https://github.com/google-ai-edge/mediapipe) | **사용** | AudioClassifier와 FaceLandmarker로 소리와 얼굴·입 형상의 단서를 살펴본다. 영아 감정의 정답을 제공하지 않는다. |
| [LiteRT](https://github.com/google-ai-edge/LiteRT) | 별도 SDK 직접 연동 없음 | 현재 실행은 MediaPipe Tasks가 담당한다. 향후 자체 모델 배포·최적화 후보다. |
| [librosa](https://github.com/librosa/librosa) | 앱 탑재 없음 | Python의 MFCC/mel 등 연구·학습 도구 후보 |
| [voc2vec](https://github.com/koudounasalkis/voc2vec) | 학습·탑재 없음 | 비언어적 발성 표현 연구 후보. 6~18개월 아기의 뜻을 알아내는 정확도가 입증된 것은 아니다. |

코드·모델·원데이터의 권리를 각각 확인한다. 실제 모델 출처·해시·고지는 [모델 assets](../outputs/MoaAndroid/app/src/main/assets/MODEL_NOTICE.txt)와 [YAMNet 고지](../outputs/MoaAndroid/app/src/main/assets/YAMNET_NOTICE.txt)에 있다. 전달받은 엔진 5개를 ‘전부 사용’했다고 표현하지 않는다.

## 연구와 추가 후보

| 영역 | 자료 | 적용할 판단 |
|---|---|---|
| 울음 분류의 근거 한계 | [체계적 문헌고찰](https://pmc.ncbi.nlm.nih.gov/articles/PMC12804663/) | 표본·라벨·동일 아기 데이터 누출·자연환경 일반화의 한계를 확인한다. 논문에서 보고한 44.5~99.82% 범위를 앱 정확도로 환산하지 않는다. |
| 언어 발달 | [NIDCD](https://www.nidcd.nih.gov/health/speech-and-language), [CDC 6개월](https://www.cdc.gov/act-early/milestones/6-months.html) | 월령별 다양성을 참고하며 개인 진단 규칙으로 사용하지 않는다. 나머지 월령 링크는 원본 자료 2에 있다. |
| 옹알이·상호작용 | [자연환경 발성](https://pmc.ncbi.nlm.nih.gov/articles/PMC5869132), [발성 놀이](https://pmc.ncbi.nlm.nih.gov/articles/PMC7406057/), [양육자 반응](https://pmc.ncbi.nlm.nih.gov/articles/PMC6004332/) | 사용자 원본에 제공된 추가 읽기 목록이다. 모든 논문을 재현하거나 제품 학습에 사용했다고 주장하지 않는다. |
| 얼굴 신호 해석 | [얼굴만으로 감정을 단정하는 데 따르는 한계](https://pmc.ncbi.nlm.nih.gov/articles/PMC6640856), [InfAnFace](https://github.com/ostadabbas/Infant-Facial-Landmark-Detection-and-Tracking), [InfantAFAR](https://github.com/AffectAnalysisGroup/InfantAFAR) | 성인 모델·영아 자료·발달 진단을 혼동하지 않는다. 영아 데이터와 모델의 상업 사용 조건은 별도로 확인한다. |
| 발성 데이터 후보 | [Korean Ko](https://talkbank.org/childes/access/EastAsian/Korean/Ko.html), [ACLEW](https://talkbank.org/homebank/access/Public/ACLEW.html), [Donate-a-Cry](https://github.com/gveres/donateacry-corpus), [ICSD](https://github.com/QingyuLiu0521/ICSD/) | 접근 승인·상용 권리를 확인하고 보호자의 추정 라벨과 실제 욕구를 구분한다. 다운로드나 학습을 완료한 상태가 아니다. |
| 향후 평가 설계 | 아이·가족·녹음 세션 분리, 6~8/9~12/13~18개월 구분, 소음·조명·기기별 평가 | 관찰 사건의 정확도와 자연어로 표현한 의도의 정확도를 별도로 정의해야 한다. |

## 경쟁사와 BM

- 상세 비교와 근거 번호는 [기술·경쟁 조사](../outputs/Jigeum-Research.md)를 참고한다.
- [멍냥톡](https://play.google.com/store/apps/details?id=com.gonigon.meongnyangtalk&hl=ko), [MeowTalk](https://www.meowtalk.app/?lang=ko), CatTok/DogTok/개파고: 짧은 입력 → 한마디 → 기록·공유의 흐름을 참고한다. 사용자가 든 ‘냥톡/멍톡’ 예시를 특정 브랜드와 임의로 동일시하지 않는다.
- [ChatterBaby](https://chatterbaby.org/pages/faq), [Nanni](https://nanni.ai/privacy-policy-bci.html), [바뱌](https://babba.deeplyinc.com/en), [BabyTalk](https://apps.apple.com/us/app/babytalk-translator/id1582829586): 직접 경쟁 후보다. 마케팅에서 주장한 자체 정확도·의도 분류와 독립 검증을 구분한다.
- [Huckleberry](https://huckleberrycare.com/pricing), [FamilyAlbum](https://family-album.com/premium), WonderWeeks/CDC/BabyTime: 기록·정리·보존·루틴의 인접 사례다.
- [RevenueCat 2026](https://www.revenuecat.com/state-of-subscription-apps-2026/), [Nir Eyal Hook Model](https://www.nirandfar.com/how-to-manufacture-desire/): [BM 분석](../outputs/Jigeum-BM-Retention-Pricing.md)의 출처다. 국내 부모의 전환율·구독 유지율 예측값으로 사용하지 않는다.
- 최신 무료·유료 목표 범위는 [이용 정책](../outputs/Jigeum-Access-Policy.md)을 우선한다. 과거 시장조사의 ‘모든 입력 무료’는 0.3 기준이며, 0.4 목표의 구현·QA가 끝났다는 뜻은 아니다.

## Android와 정책

[AudioClassifier](https://ai.google.dev/edge/mediapipe/solutions/audio/audio_classifier), [FaceLandmarker](https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker), [CameraX](https://developer.android.com/media/camera/camerax), [16KB 페이지](https://developer.android.com/guide/practices/page-sizes), [타깃 API](https://support.google.com/googleplay/android-developer/answer/11926878), [Play 정기결제](https://support.google.com/googleplay/android-developer/answer/12154973?hl=ko), [Play Families](https://support.google.com/googleplay/android-developer/answer/9893335?hl=en-GB).

개인정보·영아 데이터 안내 후보는 사용자 원본 자료 2의 H8에 있다. 현재 대상 사용자는 부모다. 스토어 대상 연령과 Families 적용 여부는 실제 선언·기능과 공식 기준을 대조해 확인한다. 자료를 읽었다는 사실만으로 법적 적합성을 확정하지 않는다.

## KOTA QA에서 가져온 교훈

| 원문의 문제 유형 | 본 앱에 적용할 점 |
|---|---|
| 중복 후보·불명확한 동일성 | JSON 가져오기의 중복, timestamp 충돌, 재가져오기를 검증한다. |
| 과거 값을 실시간 정보처럼 표시 | 상품의 실제 가격·캐시 기한·모델 점수·구버전 문서를 구분한다. |
| 모르는 값을 확정 문구로 표시 | 뜻·정확도·가격·운영 연결 상태를 임의로 채우지 않는다. |
| 버튼과 도착 페이지의 불일치 | 이용권 화면(paywall)의 진입점별 뒤로 가기·복원과 실제 SAF 흐름을 확인한다. |
| 이미지 실패·작은 텍스트 | 실패 상태, 200% 글꼴, 작은 폰과 가로 화면을 실제 화면에서 검토한다. |
| 수집 도구 실패를 사이트 오류로 오인 | 하네스의 null root·선택자·quoting 오류와 제품 오류를 분리한다. |

KOTA 사이트를 수정하거나 그 결함이 우리 앱에도 있다고 결론 내린 것은 아니다.
