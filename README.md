# 아가톡 - 지금 내 기분이 그래

6~18개월 아기의 부모가 소리와 장면을 담고, 들린 소리를 바탕으로 상상한 아기말을 기록하는 Android 앱입니다. 앱서랍 이름은 **아가톡**, 기존 패키지는 `com.moa.babylanguage`입니다.

**[협업 시작점](docs/HANDOFF.md)** · **[UI 개선](docs/UX-0.5.7.md)** · **[베타 전체 기능 계약](docs/UX-CLOSED-TEST-0.5.8.md)** · **[최신 QA](docs/qa/closed-test-0.5.8/README.md)**

현재 테스트 배포본은 **0.5.8-beta/code22**입니다. 비공개 테스트 기간에는 구매 없이 유료판 전체 기능을 제공합니다. 전용 beta AAB를 Play Alpha에 업로드·검토 전송했고 자동 검사 완료 후 **Google 검토 중**입니다. beta22 게시·실제 Play 설치는 아직 확인하지 않았습니다. [Play 상태](docs/release/20261002/PLAY-STATUS.md).

Android 전체 계측 **253통과/0실패/3명시제외**, 호스트 게이트 **30통과**, 서명 UI **7시나리오 증거 결합**, beta/release lint **오류0/기존 경고20**입니다. 접근·동작 결과를 아기 의도 정확도·실기기·Play 구매 검증으로 사용하지 않습니다.

## 현재 구현

- 영상+소리 촬영 또는 소리만 녹음 → 재생 → 양끝 손잡이/구간 통째 이동 → 분석 → 한마디·근거 → 수정·저장. 하단 촬영/분석 버튼, 큰 손잡이·선택 밖 탭 이동·0.5초 미세 조절·2×2표지를 지원합니다.
- **테스트 beta:** 최대2분 촬영/녹음·20초 분석·200MB 가져오기·시각 관찰·PDF를 구매 없이 개방합니다. **정식 release:** 무료1분/10초/100MB, 전체 이용권2분/20초/200MB·시각/PDF. 상용20/30초 최종 선택·상품/실거래는 별도입니다.
- 월령·수동 소리/상황 선택·녹음 없는 번역·후보 재뽑기 제거. 근거 부족·무음·모델 오류에서는 문장을 만들지 않음.
- 일반 소리 모델 YAMNet과 MediaPipe 얼굴 관찰 사용. 배고픔·졸림 등 욕구를 분류하는 검증 모델, LLM, 개인화 학습은 구현하지 않음.
- 같은 PCM/구간/모델은 같은 결과. 소리 종류·에너지 변화로 표현군을 제한하고 파형 키로 동등한 말투만 선택. 문구는 유한하여 반복 가능.
- 결과 보기만으로 자동 기록을 만들지 않습니다. 저장 버튼으로 문장과 선택 구간 MP4를 기기에 보관하거나, 카드 PNG/원음 포함 MP4를 ‘아가톡’ 앨범에 저장합니다. JSON·CSV·PDF에는 미디어가 없고 MP4는 따로 내려받습니다. 앱 기록을 지워도 갤러리 사본은 남습니다. 앱 로그인·외부 아기 기록 DB·무단 업로드는 없습니다.
- beta 접근을 영구 구매 권한으로 저장하지 않습니다. 정식 업데이트부터 이용권 제한을 적용하고 기존 기록·영상·무료 내보내기는 유지합니다. 자동 만료/강제 업데이트는 없으며 후속 Play 정식 전환은code23 이상과 실제 서명/업데이트 경로 검증이 필요합니다.

## 문서와 소스

- [엔진·논문·데이터 권리 조사](docs/research/20261001-cry-evidence.md)
- [경쟁 앱·말투·상황 적합성 검증](docs/research/20261001-competitors-and-voice.md)
- [현재 무료·유료 정책](outputs/Jigeum-Access-Policy.md), [다음 단계](docs/NEXT.md), [폰 테스트 안내](docs/OWNER-TEST.md)
- [테스터 초대 안내](docs/release/20261002/TESTER-INVITE.md): Google 그룹 가입 → 같은 계정 Play 참여 → Play 설치/업데이트 →0.5.8-beta 확인. 매번 이메일을 수동 등록하는 방식은 아닙니다.
- Android: `outputs/MoaAndroid` / 무상태 결제 확인 서버: `outputs/PlayVerifier`
- QA: `outputs/QA` / 이전 [0.4.5 연결 검증](docs/qa/0.4.5/connected/README.md)
- 확정 트레일러: `outputs/Marketing/jigeum-trailer-b-family.mp4`. 새 이름·새 화면 반영본은 아직 제작하지 않음.

## 빌드

JDK17+·Android SDK36. Android 프로젝트에서:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintRelease --max-workers=1
```

Play beta는 기존 업로드키로 `tools/release/build-play-bundle.ps1 -Purpose closed-test`를 사용합니다. sideload 전용 preview AAB는 차단하며 비교용 release22를 제출하지 않습니다. 자세한 환경·서명·실행은 [RUNBOOK](docs/RUNBOOK.md)을 따릅니다.

APK/AAB·캐시·비밀키·구매 토큰·원본 기록은 Git 제외입니다. 기존 결제 확인 서버·공개 갤러리 정책 revision `jigeum-verifier-00005-ph7`은 유지하며 이번 beta로 서버를 재배포하지 않았습니다. 실제 그룹 경로의 Play 설치·최소12명/연속14일·프로덕션 액세스 신청·Play 거래·ARM 실기기·해석 품질 평가는 별도입니다.
