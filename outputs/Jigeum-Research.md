# 아기 언어 앱: 경쟁 제품·기술 근거 조사

**현재 구현과 구분:** 아래에는 제품·연구 후보에 대한 검토도 포함된다. Android 0.2.1에 실제 연결된 것은 YAMNet과 MediaPipe이며, LiteRT 별도 SDK·librosa·voc2vec를 모두 탑재한 것이 아니다. [실제 사용 현황과 온디바이스 구조](Jigeum-OnDevice-Architecture.md)를 기준으로 구현 상태를 확인한다.

조사일: 2026-09-30. 사용자 첨부 3개 문서를 먼저 읽고, 아래 사실은 별도 공식 제품 페이지·Google Play 개발자 설명·공식 기술 문서·원 논문에서 재확인했다. 각 제품의 마케팅 문구와 독립 검증 성능은 구분한다. Exa 무료 검색은 두 번째 질의 이후 HTTP 429에 도달하여 agent-reach의 Jina 웹 읽기 및 GitHub CLI 경로로 확인했다.

## 제품 포지셔닝에 중요한 결론

- **첫 화면의 가치는 아이 시점의 짧은 말 한마디**일 수 있다. 멍냥톡은 소리 입력 뒤 ‘재미있는 한 마디+감정’을 돌려주고, 원음 듣기·결과 공유·기록으로 이어진다. 그러나 반려동물 앱 스스로 엔터테인먼트 결과라고 규정한다.[P1] 영아 앱에서 같은 형식을 가져오더라도, 추정 문장과 실제 관찰(예: 옹알이·웃음·울음)을 분리해서 보여주는 것이 신뢰 설계에 맞다. 후자는 이 조사자의 **제품 추론**이다.
- **울음의 원인 단정은 검증 범위를 넘어선다.** ChatterBaby의 공식 FAQ는 분류 범위를 fussy/hungry/pain으로 제한하고, 통증 정확도 주장은 예방접종·귀 뚫기 등 실제 급성 통증 조건에 붙인다. 배경 소음이나 사람이 노래하는 상황에서 작동이 나빠질 수 있다고 직접 경고한다.[B1] 17개 연구를 검토한 2026년 체계적 문헌고찰은 성능 범위 44.5–99.82%, 표본·라벨·동일 아기 중복·자연환경 일반화 문제를 보고한다.[E1]
- **시장에는 이미 ‘울음 번역’, ‘루틴 기록’, ‘발달 체크’가 있다.** 한국의 바뱌도 10초 울음 녹음→상위 3가지 원인 및 해결책을 내세운다.[B3] 차별점은 ‘원인 맞히기’라는 미검증 약속보다, 6–18개월 아이의 *비울음 발성까지* 놀이와 상호작용으로 이어주는 친근한 번역 경험, 엄마·아빠가 맥락을 선택하는 흐름, 기기 내 처리 및 쉬운 삭제에서 찾는 편이 타당하다. 이 문장은 **제품 추론**이다.
- **기술 출발점은 MediaPipe Audio Classifier의 권장 YAMNet 모델**이다. 공식 가이드가 Android 샘플과 Yamnet `.tflite`를 제공한다.[T2] YAMNet은 521개 일반 소리 이벤트와 1024차원 임베딩을 내며 영아의 의도를 읽는 모델은 아니다.[T1] 모델 출력으로 자유 문장을 ‘생성했다’고 주장하지 말고, 관찰 신호·보호자 맥락을 근거로 검수된 한국어 후보 문구를 선택하는 방식이 MVP에 적합하다. 마지막 문장은 **제품 추론**이다.

## 직접·인접 경쟁 제품

| 제품 / 타깃 | 공식 자료에서 확인한 입력→결과 및 핵심 기능 | 개인정보·성능·근거의 한계 | 기획상 시사점(추론) |
|---|---|---|---|
| **ChatterBaby** / 울음 이유가 궁금한 보호자 | 영아 울음의 음향 특징을 미리 라벨된 데이터와 비교해 fussy, hungry, pain 중 하나를 예측. 보호자 라벨 피드백과 음성·영상 기증으로 개인화한다.[B1] | 공식 FAQ의 약 90% 울음 검출 및 통증 예측 주장은 특정 조건의 자체 주장이다. 울음이 아닌 노래·개 짖는 소리, 높은 배경 소음은 부적합하다. 예측 시 음성과 월령 등을 UCLA로 전송하고 연구 참여에 동의한다. 의학적 치료 대체가 아니라고 고지한다.[B1] | ‘번역’이라는 말은 시장에서 익숙하지만 화면에서는 근거와 불확실성을 함께 드러내야 한다. |
| **Nanni AI (Ubenwa)** / 초보 보호자, 특히 영아 울음 | 공식 블로그는 울음으로 pain, discomfort, hunger, tired를 가늠하고 달래기 조언, 수면·기저귀·수유·이정표 추적을 설명한다.[B2] | 공개 개인정보처리방침은 아이 이름·생년월일, 울음 녹음 저장, 서비스 제공과 모델 학습 목적 사용, 제3자 서비스 제공자 공유 가능성을 명시한다. 정확도·완전성 보장 없고 의료·진단 서비스가 아니라고 명시한다. 자체 블로그도 소음 시 정확도 변동과 보호자 판단 우선을 강조한다.[B2] | 앱의 핵심 한마디와 부가 기록 기능을 분리해 첫 경험을 짧게 유지할 여지가 있다. |
| **바뱌(Babba, 한국)** / 초보 부모 | 공식 홈페이지는 10초 울음 녹음, 개인화된 상위 3가지 이유와 대처 제안, 주간 육아 가이드를 내세운다. 하루 무료 3회도 명시한다.[B3] | 정확도 수치와 독립 평가를 공식 홈페이지에서 확인하지 못했다. 사이트의 Google Play 링크는 이번 조회에서 ‘Not Found’여서 현재 배포 상태는 별도 확인이 필요하다.[B3] | 동일한 ‘울음 원인’ 포지션은 혼잡하다. 옹알이와 영상 속 맥락을 다루는 경험이 다른 진입점이다. |
| **BabyTalk by MeowTalk** / 울음·발성을 번역하려는 보호자 | App Store 설명은 아기 소리를 즉시 hungry, laughing, burping, discomfort, belly pain, coughing, sneezing, tired의 **8개 일반 의도** 중 하나로 분류한다고 주장한다. 사용자가 결과의 “No, Change it”으로 교정하고 특정 소리의 예시 5–10개를 주면 최대 24시간 후 개인화 인식이 시작된다고 설명한다.[B8] | 이 8개 의도와 5–10개 개인화 효과는 **개발자 자체 주장**이며 독립 검증을 확인하지 못했다. App Store 개발자 신고에는 식별 정보와 연결되지 않은 오디오 데이터 수집 가능성이 나온다. 같은 목록에 기침·재채기 같은 관찰 사건과 배고픔·복통 같은 해석 라벨이 섞여 있다.[B8] | 보호자 교정 루프는 재방문 이유가 될 수 있다. 그러나 ‘수유 직전=배고픔’처럼 맥락으로 만든 라벨을 참값으로 취급하면 순환 검증이 되므로 제품에서는 부모 추정으로 명시해야 한다. |
| **Huckleberry** / 루틴·수면을 관리하는 부모 | Play 설명과 요금 페이지는 수면·수유·기저귀·성장 기록, SweetSpot 수면 시점 예측, 보고서, AI 기록, 전문가 수면 계획을 제공한다. SweetSpot은 Plus/Premium에 포함.[B4] | Play 데이터 보안 표시는 개인 정보, 사진·영상 등을 수집할 수 있고 개인 정보·기기 ID를 제3자와 공유할 수 있다고 개발자가 신고한다. 울음/옹알이의 ‘뜻’ 해석은 제품 설명의 핵심이 아니다.[B4] | 루틴 맥락을 아이 발성 결과에 반영하되 거대한 기록 앱을 먼저 만들 필요는 없다. |
| **The Wonder Weeks** / 월령 발달 해석을 원하는 부모 | Play 설명은 ‘10번의 mental leap’ 일정·알림, 행동 신호 설명, 77개 놀이, 개인 일기와 공동 양육자 연결을 내세운다.[B5] | ‘도약이 울음 증가의 이유’라는 문구는 개발자 설명이다. 해당 인과·정확한 일정의 독립 재현 근거를 이번 조사에서 확보하지 못했다. Play 데이터 보안은 개인 정보·사진/영상 등의 수집 가능성을 신고한다.[B5] | 월령별 놀이 제안은 참고할 만하지만 개인의 하루 상태를 달력만으로 설명해서는 안 된다. |
| **CDC Milestone Tracker** / 발달 관찰을 하는 보호자 | Play 설명은 2개월–5세 체크리스트, 이정표 사진·영상, 조기 상담 시점, 진료 알림, 의사 공유용 요약을 제공한다.[B6] | 체크리스트 자체가 표준화·검증된 발달 선별도구의 대체가 아니라고 명시. CDC는 개인 식별 정보를 수집·공유하지 않는다고 밝히고, Play의 개발자 답변은 기록이 휴대폰에 저장된다고 한다.[B6] | ‘아기 시점 말 한마디’와 별도로, 성장 관찰 기록은 CDC처럼 진단 주장 없이 제공할 수 있다. |
| **베이비타임(BabyTime, 한국)** / 수유·육아 기록 | Google Play의 개발자 설명은 수유·수면·기저귀 기록, 월령별 발달 체크, 가족 공유, 차트·알람·성장일기를 제공한다.[B7] | Play 데이터 보안상 개인 정보·사진/영상·앱 활동 수집 가능, 위치·앱 활동 등 공유 가능성을 개발자가 신고한다. 옹알이 해석 제품은 아니다.[B7] | 한국어 사용자의 양육 기록 습관과 공유 기대를 보여주는 인접 경쟁사다. |

## 냥톡·멍톡 유사 앱에서 가져올 UX와 구분할 주장

**이름 확인:** Google Play에서 ‘냥톡’, ‘멍톡’을 검색했을 때 정확히 그 이름의 단일 공식 제품보다 **멍냥톡**, **CatTok**, **DogTok**, **멍멍톡**, **개파고** 등이 검색되었다.[P1–P4] 사용자의 ‘냥톡’ 예시에 가까운 **MeowTalk**의 공식 한국어 페이지·Android 앱도 별도로 확인했다.[P5] 아래 앱들을 특정 브랜드로 임의 동일시하지 않는다.

| 앱 | 확인된 입력 흐름·결과 문구·신뢰 표현 | 아기 앱으로 옮길 때의 해석 |
|---|---|---|
| **멍냥톡** | 강아지/고양이 선택 → 최대 10초 실시간 녹음 또는 기존 오디오 파일 → 미리 듣기 → Gemini가 ‘재미있는 한 마디’와 행복·배고픔·불안·호기심 등의 감정 제시 → 번역 TTS, 원음 재생, 기록, 공유. 하루 무료 5회+보상 광고 5회. 녹음은 Gemini API로 전송. 계정 가입 없음. ‘엔터테인먼트 앱’, ‘실제로 동물의 말을 알아듣는 것이 아님’, ‘수의학적 진단이 아님’이라고 명시.[P1] | **UX 영감:** 짧은 촬영/녹음→한 줄 문장→원본 재생→공유. **영아에 필요한 수정:** 문장은 ‘이 순간을 이렇게 상상해볼 수 있어요’처럼 가능성의 연출로 표시하고, 아래에 관찰 사실·보호자 맥락을 보여야 한다. 공유 전 얼굴·소리 노출을 명시하고 기본 저장/외부 전송은 끈다. 이는 제안이지 멍냥톡의 구현 사실이 아니다. |
| **MeowTalk** | 공식 한국어 사이트는 녹음된 고양이 발성을 AI로 11개 일반 의도/마음 상태로 해석한다고 주장한다. Android 스토어는 이 일반 의도를 사람이 읽는 말로 바꾸고, 무료판은 3개 의도만 공개하며 전체 결과·연속 번역·번역 기록·오디오 다운로드·개별 고양이 학습은 Premium에 묶는다고 설명한다. 공식 튜토리얼은 결과가 틀리면 **“No, Change it”** → 사용자 해석 입력 → 5–10회 반복 → 최대 24시간 후 반영하는 흐름과 인식 실패 시 음성 샘플 녹음·제출을 설명한다.[P5] | **UX 영감:** ‘녹음→분류→자연어 문장→기록→사용자 수정’의 일관된 루프. 11개 의도의 과학성·개별 교정 효과는 제품 자체 주장으로 취급한다. 영아 앱은 교정 피드백을 ‘보호자 생각’으로 남기고, 모델이 실제 아이 의도를 배웠다고 단정하지 않는 편이 적절하다(추론). |
| **CatTok / DogTok** | CatTok은 ‘사람의 목소리→고양이 소리’ 시뮬레이션·소리 라이브러리·훈련 기능을 전면에 두고 ‘과학적인 용도가 아닌 오락용’이라고 명시한다. DogTok도 인간→짖음 시뮬레이터와 훈련 클리커 등 놀이 중심.[P2][P3] | 이름에 ‘번역기’가 있어도 실제 사용 가치는 놀이·유대감일 수 있다. 아기 앱은 보호자와 아이의 상호작용 놀이로 연결할 수 있지만, 과학적 판독으로 오인시키는 기능 명칭은 피해야 한다. |
| **개파고** | Play 소개는 강아지 음성을 딥러닝으로 분석해 ‘어떤 말을 하는지 해석’한다고만 적는다. 상세 입력·결과·검증 근거·면책 문구는 해당 설명에서 확인되지 않았다.[P4] | ‘딥러닝’과 ‘번역’이라는 단어만으로 신뢰가 생기지 않는다. 결과의 관찰 근거를 사용자에게 보여주는 편이 제품적으로 유리하다는 추론이다. |

**결과 문구 설계 예시(조사자의 제안; 경쟁사 실제 출력 아님):**

멍냥톡의 Play ‘데이터 보안’에는 수집·제3자 공유 없음이라고 표시되는 반면 같은 개발자 설명은 Gemini API 전송을 말한다.[P1] 두 표기가 Google Play의 ‘수집’ 정의 때문에 공존하는지, 신고 누락인지는 이 조사로 판단할 수 없다. 본 앱은 서버 전송 여부를 스토어·권한 화면·개인정보처리방침에서 같은 말로 설명하고 실제 네트워크를 시험해야 한다(추론).

- 상단 연출: “나 지금 소리 내는 게 재미있어. 한 번 더 답해 줘!”
- 근거: “옹알이처럼 들리는 발성이 두 번 이어졌어요. 울음은 뚜렷하지 않았어요.”
- 맥락 질문: “방금 눈을 마주치며 놀고 있었나요?”
- 신뢰 문구: “아이 마음을 정확히 번역한 말은 아니에요. 소리·표정과 보호자가 알려준 상황을 바탕으로 상상한 한마디예요.”
- 민감 상황: 울음·불편 신호가 있으면 장난스러운 1인칭 문구보다 “평소와 다른 상태인가요? 수유·기저귀·온도와 몸 상태를 확인해 주세요” 같은 보호자 행동 안내를 우선한다. 증상 걱정 시 의료진 상담 경로를 둔다.

## 사용자 제공 5개 기술 참조의 역할과 사용권

| 참조 | 확인된 능력·라이선스 | MVP 적합성 및 남는 검증 |
|---|---|---|
| **YAMNet** | TensorFlow 공식 저장소의 YAMNet은 AudioSet 기반 **521개 일반 오디오 이벤트**와 **1024차원 임베딩**을 출력한다. 입력은 16kHz mono, 약 960ms 구간. 소스 README는 Keras 2 의존·Keras 3 비호환을 경고한다.[T1] TensorFlow Models의 `/research` 폴더에는 루트 LICENSE의 Apache-2.0 조항이 적용된다.[L1] | 소리의 종류를 관찰하는 보조 신호로 적합. ‘배고파’, ‘엄마랑 놀고 싶어’ 같은 의도는 출력하지 않는다. MediaPipe가 Android용 YAMNet `.tflite` 권장 모델을 제공하므로 원본 Keras 코드를 앱에 옮길 필요가 없다.[T2] 별도 모델 파일의 배포 조건·고지 확인은 출시 전 점검. |
| **MediaPipe** | Google 공식 문서는 Face Landmarker가 478개 3D 얼굴 랜드마크와 선택적 52개 blendshape 계수를 출력한다고 설명한다. Audio Classifier는 학습된 카테고리 점수 목록을 출력하고 Android 예제를 제공한다.[T2][T3] GitHub 저장소는 Apache-2.0.[L2] | 얼굴 움직임 및 오디오 이벤트 분류의 Android 통합에 적합. blendshape 계수 자체가 영아 감정·욕구 라벨이 아니며, 성인 중심 모델의 6–18개월 얼굴에 대한 성능 검증이 필요하다. **MVP의 영상은 촬영/재생과 얼굴 존재·기초 움직임 관찰부터 시작**하는 편이 타당하다(추론). 모델 자산 권리는 코드 라이선스와 별도 확인. |
| **LiteRT** | TensorFlow Lite 후속 온디바이스 추론 런타임으로 Android·가속·변환 경로를 공식 제공한다. 저장소 Apache-2.0.[T4][L3] | 자체 영아 분류 모델이 생긴 뒤 배포·최적화에 적합. 초기 MVP는 MediaPipe Tasks가 이미 런타임과 모델 통합을 처리하므로 LiteRT를 앱 기능처럼 추가할 이유가 약하다(추론). |
| **librosa** | Python 음향·음악 신호 처리 라이브러리, GitHub 라이선스 ISC.[T5][L4] | 모델 연구와 학습 데이터의 mel/MFCC 등 특징 실험에 적합. Android 런타임 의존성으로 바로 넣기보다 학습·평가 파이프라인에서 사용(추론). |
| **voc2vec** | 비언어적 인간 발성 표현 모델. 공개 README는 125시간·10개 데이터셋의 사전학습, FreeSound babies 23.42시간과 Donate A Cry 평가를 언급한다. 코드와 공개 모델은 Apache-2.0이라고 명시.[T6][L5] | 연구용 비교 기준·전이학습 후보. 영아 6–18개월의 개별 의도·감정 판독 성능을 검증한 것이 아니다. 아기별 분리, 연령별, 가정 환경, 한국어 보호자 피드백을 포함한 시험셋 없이는 제품 출력의 근거로 쓸 수 없다(추론). 원 데이터셋의 재사용권은 모델/코드 라이선스와 별도. |

## 평가 및 프라이버시 설계에 바로 반영할 근거

1. **라벨을 ‘말의 뜻’이 아니라 관찰 가능한 사건부터 정의한다.** YAMNet·MediaPipe 출력은 음향 이벤트/얼굴 계수다.[T1–T3] 영아 연구 리뷰에서 울음 원인 라벨과 반복 샘플·환경 누출 문제가 확인된다.[E1] 웃음·울음·옹알이·성인 음성/잡음 구분, 얼굴이 프레임에 있는지 등부터 별도 정확도 측정이 필요하다.
2. **아이 분리 평가를 한다.** 같은 아기의 여러 클립이 학습/시험에 섞이면 개별 목소리를 외울 수 있다는 체계적 검토 지적이 있다.[E1] 분할 단위는 클립이 아니라 아이, 가족, 녹음 세션이어야 한다(평가 설계 추론).
3. **6–18개월 범위에 맞춘다.** NIDCD는 언어 발달의 개인차와 영아의 발성·성인 반응 발달을 설명한다.[E2] 6개월과 18개월의 소리를 같은 기준으로 ‘번역’하면 안 된다(추론).
4. **기본은 기기 내 처리·원본 미보관·명시적 공유.** ChatterBaby는 예측 시 UCLA로 원음을 보내고 Nanni는 울음 녹음을 저장·모델 학습에 쓴다고 명시한다.[B1][B2] 이것은 경쟁사 정책의 사실이다. 본 앱이 더 간명한 신뢰 약속을 하려면 실제 APK의 네트워크 동작과 SDK 전송을 검사해야 한다(추론).

## 출처

### 경쟁 제품·스토어

- [B1] ChatterBaby 공식 FAQ: https://chatterbaby.org/pages/faq
- [B2] Nanni AI 공식 블로그: https://nanni.ai/ask-Nanni-blog/2023/10/03/how-to-understand-your-babys-cries-a-guide-for-first-time-parents/ ; Nanni 공식 개인정보처리방침: https://nanni.ai/privacy-policy-bci.html ; 제품 기능 자체 설명: https://nanni.ai/ask-Nanni-blog/2024/07/16/best-baby-monitor-tech-for-new-parents/
- [B3] 바뱌 공식 홈페이지: https://babba.deeplyinc.com/en
- [B4] Huckleberry 공식 가격·기능: https://huckleberrycare.com/pricing ; Google Play 개발자 설명: https://play.google.com/store/apps/details?id=com.huckleberry_labs.app&hl=en_US ; 개인정보처리방침: https://huckleberrycare.com/privacy-policy
- [B5] The Wonder Weeks Google Play 개발자 설명·데이터 보안: https://play.google.com/store/apps/details?id=org.twisevictory.apps.ex&hl=en_US
- [B6] CDC Milestone Tracker Google Play 개발자 설명·데이터 보안: https://play.google.com/store/apps/details?id=gov.cdc.ncbddd.actearly.milestones&hl=en_US
- [B7] 베이비타임 Google Play 개발자 설명·데이터 보안: https://play.google.com/store/apps/details?id=yducky.application.babytime&hl=ko
- [B8] BabyTalk by MeowTalk App Store 개발자 설명·프라이버시 신고: https://apps.apple.com/us/app/babytalk-translator/id1582829586
- [P1] 멍냥톡 Google Play 개발자 설명: https://play.google.com/store/apps/details?id=com.gonigon.meongnyangtalk&hl=ko
- [P2] CatTok Google Play 개발자 설명: https://play.google.com/store/apps/details?id=com.cat.translator.cattok&hl=ko
- [P3] DogTok Google Play 개발자 설명: https://play.google.com/store/apps/details?id=app.dogtok&hl=ko
- [P4] 개파고 Google Play 개발자 설명: https://play.google.com/store/apps/details?id=com.gaepago.gaepago&hl=ko
- [P5] MeowTalk 공식 한국어 페이지: https://www.meowtalk.app/?lang=ko ; Google Play 개발자 설명: https://play.google.com/store/apps/details?id=com.akvelon.meowtalk&hl=en_US ; 공식 교정 튜토리얼: https://www.meowtalk.app/tutorials

### 기술·연구·라이선스

- [E1] 영아 울음 분류 체계적 문헌고찰: https://pmc.ncbi.nlm.nih.gov/articles/PMC12804663/
- [E2] 미국 NIDCD 언어 발달 안내: https://www.nidcd.nih.gov/health/speech-and-language
- [T1] YAMNet README: https://github.com/tensorflow/models/tree/master/research/audioset/yamnet ; TensorFlow Hub 설명: https://www.tensorflow.org/hub/tutorials/yamnet
- [T2] MediaPipe Audio Classifier 공식 가이드(Android 예제·YAMNet 모델): https://ai.google.dev/edge/mediapipe/solutions/audio/audio_classifier
- [T3] MediaPipe Face Landmarker 공식 가이드: https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker
- [T4] LiteRT 저장소·공식 문서: https://github.com/google-ai-edge/LiteRT ; https://ai.google.dev/edge/litert
- [T5] librosa 저장소·문서: https://github.com/librosa/librosa ; https://librosa.org/doc/latest/
- [T6] voc2vec 원 저장소 README: https://github.com/koudounasalkis/voc2vec
- [L1] TensorFlow Models LICENSE( `/research` 적용): https://github.com/tensorflow/models/blob/master/LICENSE
- [L2] MediaPipe LICENSE: https://github.com/google-ai-edge/mediapipe/blob/master/LICENSE
- [L3] LiteRT LICENSE: https://github.com/google-ai-edge/LiteRT/blob/main/LICENSE
- [L4] librosa LICENSE: https://github.com/librosa/librosa/blob/main/LICENSE.md
- [L5] voc2vec LICENSE: https://github.com/koudounasalkis/voc2vec/blob/main/LICENSE
