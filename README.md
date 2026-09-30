# 지금 내 기분이 그래

6~18개월 아기의 보호자가 소리와 장면을 살펴보고, 직접 확인한 맥락을 아이 시점의 문장으로 남기는 Android 앱입니다.

**협업 시작: [전체 맥락·결정·레퍼런스·다음 작업](docs/HANDOFF.md).** 현재 소스는 0.4.1/code 8입니다. 무료 음성·기록·백업을 유지하며 카메라·영상·PDF를 하나의 전체 이용권으로 묶었습니다. [0.4.1 검증 상태](outputs/Jigeum-QA-0.4.1.md)를 먼저 확인하세요. [PR #2](docs/qa/PR2-INTEGRATION.md)는 준비 완료 안내 후 통합할 예정입니다.

- Android 소스: `outputs/MoaAndroid`
- 로그인 없는 Google Play 이용권 검증 서비스 소스: `outputs/PlayVerifier`
- 기획·시장·BM 문서: `outputs/Jigeum-*.md`
- 확정된 영상·홍보물: `outputs/Marketing`
- QA 재현 도구: `outputs/QA`
- 통합 검증 보고서: [0.4.1 QA](outputs/Jigeum-QA-0.4.1.md), [0.4.0 QA](outputs/Jigeum-QA-0.4.0.md), [이전 0.3.0 QA](outputs/Jigeum-QA-0.3.0.md)
- 로컬 빌드 APK와 QA 증거 압축본은 생성물이며 Git에서 제외합니다.

## 현재 범위

0.4.1 소스와 로컬 QA 자료를 제공합니다. 결제 응답 유실 복구와 protobuf patch를 반영했으며, ddot의 가져오기·공유·카메라 PR #2는 통합 대기 중입니다. 스토어 출시·제출 및 운영 서버 배포는 보류합니다. 실계정 구매와 실제 ARM 단말 검증을 로컬 시험 결과로 대체하지 않습니다.

검증한 테스트 수와 환경은 위 버전별 QA 보고서에 기록합니다. 현재 APK는 운영 결제 검증 URL·공개 키가 비어 있어 실제 구매가 비활성화되어 있습니다. 무료 문장·기록·공유·백업을 사용할 수 있으며, 유료 활성화는 운영 설정과 실제 Play 검증을 마친 뒤 가능합니다.

아기 기록은 기기의 SQLite에 저장하고 외부 사용자 DB와 앱 회원가입은 사용하지 않습니다. 결제 검증 통신에는 구매 토큰과 임의 설치 식별자만 사용합니다. 모델은 일반 소리·얼굴 단서를 찾으며, 아기의 욕구나 감정을 판정하거나 언어의 뜻을 번역하지 않습니다.

## 빌드

JDK 17 이상과 Android SDK 36을 준비한 뒤 Android 소스 디렉터리에서 실행합니다.

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:bundleRelease :app:lintRelease --max-workers=1
```

결제 검증 서비스의 로컬 시험:

```powershell
cd outputs/PlayVerifier
npm test
```

키·서비스 계정·결제 토큰·사용자 데이터는 저장소에 커밋하지 않습니다. 자세한 설정과 실제 검증 범위는 각 모듈 문서 및 최신 QA 보고서를 참고합니다.
