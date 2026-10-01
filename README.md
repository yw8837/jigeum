# 아가톡 - 지금 내 기분이 그래

6~18개월 아기의 부모가 소리와 장면을 담고, 들린 소리를 바탕으로 상상한 아기말을 기록하는 Android 앱입니다. 앱서랍 이름은 **아가톡**, 기존 패키지는 `com.moa.babylanguage`입니다.

**[협업 시작점](docs/HANDOFF.md)** · **[실사용 피드백 반영 설계](docs/UX-0.5.md)** · **[최신 QA](docs/qa/0.5.0/README.md)**

현재 소스는 **0.5.0/code13 테스트 후보**입니다. 이전 0.4.5 기능 QA 통과를 새 버전의 해석 품질이나 출시 승인으로 사용하지 않습니다.

## 현재 구현

- 최대 1분 영상+소리 촬영 또는 소리만 녹음 → 재생 → 1–15초 구간 선택 → 소리 분석 → 한마디·근거 → 수정·저장.
- 촬영·저장 영상 가져오기·재생·소리 분석은 무료. 같은 구간의 얼굴/입 열림 관찰과 PDF는 검증된 이용권 필요.
- 월령·수동 소리/상황 선택·녹음 없는 번역·후보 재뽑기 제거. 근거 부족·무음·모델 오류에서는 문장을 만들지 않음.
- 일반 소리 모델 YAMNet과 MediaPipe 얼굴 관찰 사용. 배고픔·졸림 등 욕구를 분류하는 검증 모델, LLM, 개인화 학습은 구현하지 않음.
- 같은 PCM/구간/모델은 같은 결과. 소리 종류·에너지 변화로 표현군을 제한하고 파형 키로 동등한 말투만 선택. 문구는 유한하여 반복 가능.
- 원음·영상은 기기 안의 임시 자료. 기록은 SQLite에 보관하며 원본 미디어는 기록/백업에 넣지 않음. 앱 로그인과 외부 아기 기록 DB 없음.

## 문서와 소스

- [엔진·논문·데이터 권리 조사](docs/research/20261001-cry-evidence.md)
- [경쟁 앱·말투·상황 적합성 검증](docs/research/20261001-competitors-and-voice.md)
- [현재 무료·유료 정책](outputs/Jigeum-Access-Policy.md), [다음 단계](docs/NEXT.md), [폰 테스트 안내](docs/OWNER-TEST.md)
- Android: `outputs/MoaAndroid` / 무상태 결제 확인 서버: `outputs/PlayVerifier`
- QA: `outputs/QA` / 이전 [0.4.5 연결 검증](docs/qa/0.4.5/connected/README.md)
- 확정 트레일러: `outputs/Marketing/jigeum-trailer-b-family.mp4`. 새 이름·새 화면 반영본은 아직 제작하지 않음.

## 빌드

JDK17+·Android SDK36. Android 프로젝트에서:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintRelease --max-workers=1
```

자세한 환경·서명·실행은 [RUNBOOK](docs/RUNBOOK.md). APK/AAB·캐시·비밀키·구매 토큰·사용자 기록은 Git 제외. 공개 정책의 0.5 소스는 준비했으나 서버에는 배포하지 않았습니다. 실제 Play 상품·구매/환불·ARM 실기기·보호자 해석 품질 검증은 별도 미완료입니다.
