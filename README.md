# 지금 내 기분이 그래

6~18개월 아기의 보호자가 소리와 장면을 살펴보고, 직접 확인한 맥락을 아이 시점의 문장으로 남기는 Android 앱입니다.

**협업 시작: [전체 맥락·결정·레퍼런스·다음 작업](docs/HANDOFF.md).** 현재 소스는 **0.4.5/code12**입니다. 무료 음성·기록·백업과 카메라·영상·PDF 전체 이용권 정책, [PR #2 통합](docs/qa/PR2-INTEGRATION.md)을 유지합니다. 최신 기준은 [운영 연결 후 QA](docs/qa/0.4.5/connected/README.md)와 [연결 진행](docs/release/20261001/CONNECTION-PROGRESS.md)입니다.

- Android 소스: `outputs/MoaAndroid`
- 로그인 없는 Google Play 이용권 검증 서비스 소스: `outputs/PlayVerifier`
- 기획·시장·BM 문서: `outputs/Jigeum-*.md`
- 확정된 영상·홍보물: `outputs/Marketing`
- QA 재현 도구: `outputs/QA`
- 통합 검증 보고서: [0.4.5 연결 QA](docs/qa/0.4.5/connected/README.md), [0.4.5 초기 로컬 QA](outputs/Jigeum-QA-0.4.5.md), [0.4.4 QA](outputs/Jigeum-QA-0.4.4.md), [0.4.3 QA](outputs/Jigeum-QA-0.4.3.md), [이전 0.3.0 QA](outputs/Jigeum-QA-0.3.0.md)
- 로컬 빌드 APK와 QA 증거 압축본은 생성물이며 Git에서 제외합니다.

## 현재 범위

0.4.5 소스와 실제 서버 연결 후보의 QA 자료를 제공합니다. 서명 기반 심사 접근·HTTP 전체 기한·workload 인증·설치별/전체 요청 제한을 통합했습니다. 최종 Android는 **142통과·미설정 전용2skip·실패0**, Node **56/56**, 실제 release 앱 **10/10**, 실제 HTTPS **9/9**입니다. Lint 오류0·개별 검토 경고15이며 경고 억제나 baseline을 추가하지 않았습니다. 이전 초안 timeout의 원인은 미확정으로 유지합니다.

전용 Cloud Run 검증 서비스·정책 페이지와 이 앱 한정 Play runtime 권한을 연결했습니다. 최종 `connected` APK/AAB에는 실제 HTTPS 주소·공개 키가 있고 심사 이용권 왕복을 검증했습니다. 초기 `internal`·중간 `configured` 파일은 별도로 보존합니다. 실제 Play 상품·구매/ACK/환불·Play 설치·ARM 실기기·개인 계정12명14일·공개 제출은 남았습니다. 심사 이용권 수락과 로컬 대역 통과를 실제 구매 완료로 보고하지 않습니다.

아기 기록은 기기의 SQLite에 저장하고 외부 아기 기록 DB·앱 회원가입을 사용하지 않습니다. 구매 확인은 구매 토큰·설치 식별자를, 선택한 심사 접근은 심사 코드·설치 식별자를 HTTPS 본문으로 전송합니다. 원본 미디어·아기 기록은 보내지 않습니다. 모델은 일반 소리·얼굴 단서를 찾으며, 의도 판독 모델이나 의료/발달 진단으로 소개하지 않습니다.

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
