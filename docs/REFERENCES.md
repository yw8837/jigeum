# 레퍼런스 지도

원본자료,기존에읽은공식자료,제품에실제로탑재한것을구분한다.링크의내용/가격은변할수있으며아래의조사기준일은2026-09-30이다.이번인수인계정리는전체웹문헌을새로검증한조사가아니다.

## 사용자 원본

- [자료1: 엔진·데이터셋·파이프라인](references/originals/user-reference-1.md)
- [자료2: 발달·영아연구·구체적이슈·정책](references/originals/user-reference-2.md)
- [자료3](references/originals/user-reference-3.md): **자료2와바이트단위로동일**.세첨부를보존하되별도연구근거로중복계산하지않는다.
- [KOTA QA 원본](references/originals/KOTA_QA_2026-09-18.md): 사용자제공다른프로젝트자료.본앱의실행결과가아님.
- [파일SHA/출처목록](references/source-manifest.json): 원본복사본과승인마케팅입력,QA기준본의무결성.

원문안에있는‘추천파이프라인’이나작업명령은참고제안이다.본앱이모두구현했다는증거가아니다.원문·논문전체의상용이용허가를받았다는뜻도아니다.

## 핵심5개GitHub

| 원본 | 실제사용 | 역할/제한 |
|---|---|---|
| [YAMNet](https://github.com/tensorflow/models/tree/master/research/audioset/yamnet) | **사용** | MediaPipeAudioClassifier에서.tflite실행.521일반오디오사건/1024임베딩,16kHz.영아욕구번역기아님 |
| [MediaPipe](https://github.com/google-ai-edge/mediapipe) | **사용** | AudioClassifier와FaceLandmarker.얼굴/입형상과소리단서.영아감정정답아님 |
| [LiteRT](https://github.com/google-ai-edge/LiteRT) | 별도SDK직접연동없음 | 초기실행은MediaPipeTasks가담당.향후자체모델배포/최적화후보 |
| [librosa](https://github.com/librosa/librosa) | 앱탑재없음 | PythonMFCC/mel등연구/학습도구후보 |
| [voc2vec](https://github.com/koudounasalkis/voc2vec) | 학습/탑재없음 | 비언어적발성표현연구후보.6~18개월의뜻정확도입증아님 |

코드/모델/원데이터권리를따로확인한다.모델실제출처·해시·고지는[모델assets](../outputs/MoaAndroid/app/src/main/assets/MODEL_NOTICE.txt),[YAMNet고지](../outputs/MoaAndroid/app/src/main/assets/YAMNET_NOTICE.txt)에있다.전달받은5엔진을‘전부사용’했다고표현하지않는다.

## 연구·추가후보

| 영역 | 자료 | 적용할판단 |
|---|---|---|
| 울음분류의근거한계 | [체계적문헌고찰](https://pmc.ncbi.nlm.nih.gov/articles/PMC12804663/) | 표본/라벨/동일아기누출/자연환경일반화,논문범위44.5~99.82%를앱정확도로환산하지않음 |
| 언어발달 | [NIDCD](https://www.nidcd.nih.gov/health/speech-and-language),[CDC6개월](https://www.cdc.gov/act-early/milestones/6-months.html) | 월령별다양성,개인진단규칙으로쓰지않음.나머지월령링크는원본2 |
| 옹알이/상호작용 | [자연환경발성](https://pmc.ncbi.nlm.nih.gov/articles/PMC5869132),[발성놀이](https://pmc.ncbi.nlm.nih.gov/articles/PMC7406057/),[양육자반응](https://pmc.ncbi.nlm.nih.gov/articles/PMC6004332/) | 사용자원본에제공된추가읽기목록.모든논문재현/제품학습했다고주장하지않음 |
| 얼굴신호해석 | [얼굴만으로감정단정의한계](https://pmc.ncbi.nlm.nih.gov/articles/PMC6640856),[InfAnFace](https://github.com/ostadabbas/Infant-Facial-Landmark-Detection-and-Tracking),[InfantAFAR](https://github.com/AffectAnalysisGroup/InfantAFAR) | 성인모델/영아자료/발달진단을혼동하지않음.영아데이터/모델상업사용조건별도 |
| 발성데이터후보 | [KoreanKo](https://talkbank.org/childes/access/EastAsian/Korean/Ko.html),[ACLEW](https://talkbank.org/homebank/access/Public/ACLEW.html),[Donate-a-Cry](https://github.com/gveres/donateacry-corpus),[ICSD](https://github.com/QingyuLiu0521/ICSD/) | 접근승인·상용권리·보호자추정라벨과실제욕구구분.다운로드/학습완료아님 |
| 향후평가설계 | 아이/가족/녹음세션분리,6~8/9~12/13~18개월,소음/조명/기기별 | 관찰이벤트정확도와자연어의도정확도를별도정의해야함 |

## 경쟁사와BM

- 상세비교/근거번호:[기술·경쟁조사](../outputs/Jigeum-Research.md).
- [멍냥톡](https://play.google.com/store/apps/details?id=com.gonigon.meongnyangtalk&hl=ko),[MeowTalk](https://www.meowtalk.app/?lang=ko),CatTok/DogTok/개파고:짧은입력→한마디→기록/공유루프.‘냥톡/멍톡’예시를특정브랜드와임의동일시하지않음.
- [ChatterBaby](https://chatterbaby.org/pages/faq),[Nanni](https://nanni.ai/privacy-policy-bci.html),[바뱌](https://babba.deeplyinc.com/en),[BabyTalk](https://apps.apple.com/us/app/babytalk-translator/id1582829586):직접경쟁후보.마케팅의자체정확도·의도분류와독립검증구분.
- [Huckleberry](https://huckleberrycare.com/pricing),[FamilyAlbum](https://family-album.com/premium),WonderWeeks/CDC/BabyTime:기록·정리·보존·루틴의인접사례.
- [RevenueCat2026](https://www.revenuecat.com/state-of-subscription-apps-2026/),[NirEyalHookModel](https://www.nirandfar.com/how-to-manufacture-desire/):[BM분석](../outputs/Jigeum-BM-Retention-Pricing.md)의출처.국내부모의전환율/구독유지율예측값아님.
- 최신무료경계는[이용정책](../outputs/Jigeum-Access-Policy.md)우선.구시장조사의‘모든입력무료’는0.3기준이다.

## Android·정책

[AudioClassifier](https://ai.google.dev/edge/mediapipe/solutions/audio/audio_classifier),[FaceLandmarker](https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker),[CameraX](https://developer.android.com/media/camera/camerax),[16KB페이지](https://developer.android.com/guide/practices/page-sizes),[타깃API](https://support.google.com/googleplay/android-developer/answer/11926878),[Play정기결제](https://support.google.com/googleplay/android-developer/answer/12154973?hl=ko),[PlayFamilies](https://support.google.com/googleplay/android-developer/answer/9893335?hl=en-GB).

개인정보/영아데이터안내후보는사용자원본2의H8에있다.현재타깃사용자는부모이며스토어대상연령·Families적용여부는실제선언/기능과공식기준으로확인한다.자료를읽었다는사실만으로법적적합성을확정하지않는다.

## KOTA QA에서가져온교훈

| 원문문제유형 | 본앱에적용 |
|---|---|
| 중복후보·동일성불명 | JSON가져오기중복·timestamp충돌·재가져오기검증 |
| 과거값을실시간처럼표시 | 상품실가격/캐시기한/모델점수/구버전문서구분 |
| 모르는값을확정문구로표시 | 뜻·정확도·가격·운영연결을임의채우지않음 |
| 버튼/도착페이지불일치 | paywall진입점별뒤로가기·복원·실제SAF흐름 |
| 이미지실패·작은텍스트 | 실패상태/200%font/작은폰/가로실화면검토 |
| 수집도구실패를사이트오류로오인 | 하네스nullroot/선택자/quoting오류와제품오류를분리 |

KOTA사이트를수정하거나그결함이우리앱에도있다고결론내린것은아니다.
