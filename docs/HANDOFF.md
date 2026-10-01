# 코닷 협업 인수인계 — 지금 내 기분이 그래

갱신: **2026-10-01** · 비공개 저장소: **[yw8837/jigeum](https://github.com/yw8837/jigeum)**

## 최신 체크포인트 — 0.4.5/code12 connected

10월 1일 후속: 사용자 요청으로 **상품 등록·의존하는 실제 거래는 이번 진행에서 제외**했다. [데이터 보안 선언](release/20261001/DATA-SAFETY.md)을 Console에 저장해 **10/11**을 확인했고 한국어 스토어 설명을 임시보관함에 저장했다. 이미지/AAB 업로드의 구체적인 확인은 응답 대기다. [운영자 전체 기능 테스트](OWNER-TEST.md)는 개인 코드 확인으로 구매 없이 열고 테스트 종료 후 해제한다. 모든 설치를 자동 무료로 바꾸거나 사용자 폰에서 활성화했다고 보고하지 않는다. 중간 `configured` 3파일을 로컬 보관 폴더로 옮겼고 [해시 이동 목록](release/20261001/intermediate-artifact-archive.json)을 남겼다.

최신 실사용 피드백: 사용자가 앱을 사용했고 개선점이 보인다고 보고했다. 구체 항목·화면·기기 정보를 비동기 질문으로 기다린다. 항목을 받아 수정·회귀·새 테스트 APK를 확인한 뒤 업로드 후보를 정한다. 현재 0.4.5 AAB를 사용자의 개선 피드백 반영본이라고 업로드하거나 보고하지 않는다.

현재 소스와 최종 산출물은 **0.4.5/code12**다. 기존 0.4 이용 정책·PR #2·0.4.2 ACK를 유지한다. 실제 서명 기반 심사 접근·Keystore 보관·HTTP 전체 기한·workload 인증·설치별/전체 제한·공개 정책을 구현하고 전용 서버를 배포했다.

| 구분 | 실제 현재 상태 |
|---|---|
| 최종 연결 Android | **142 통과 / 미설정 전용 2 skip / 실패 0**, Lint 오류 0·검토 경고 15 |
| 실제 연결 release UI | **10/10**: 서버 서명 수락, 카메라 시작/종료, 영상 화면, PDF, 해제·재잠금·무료 기록 보존·200% 정책 |
| 서버/HTTPS | Node **56/56** / 실제 HTTPS **9/9** / warm 오류 코드 제한·격리 **4/4** |
| Cloud/Play 연결 | `jigeum-play-20261001`, billing=true, 서울 Run min0/max1, 실제 IAM19/19·Run11/11, Play 계정은 이 앱만 활성/4권한 |
| 공개 정책/Console | 실제 HTTPS 게시·시행일 10월1일, 정책·심사 안내·성인 대상 연령·데이터 보안 저장. 대시보드 **10/11**, 스토어 설명 초안 |
| 금융 등록 | 판매자 프로필·지급 계좌 저장 완료. 소액 입금 확인 대기 |
| 최종 파일 | `outputs/Jigeum-Android-connected-0.4.5-code12-upload-signed.apk` / `outputs/Jigeum-Play-connected-0.4.5-code12-signed.aab` |
| 패키지·정리 | 실제 URL/공개 키 확인, 서명/16KB/백업/공유·native/model18 불변, 시험 앱/데이터/캐시·Gradle·emulator 정리 |

[최종 연결 QA](qa/0.4.5/connected/README.md), [실제 연결 진행](release/20261001/CONNECTION-PROGRESS.md), [운영 구성](release/20261001/OPERATIONS.md)을 먼저 따른다. 실제 구매/ACK/환불·Play 설치·ARM/16KB 실기기·TalkBack/보호자 사용성·12명14일·공개 제출은 **미완료**다. `connected`는 출시 준비 후보이며 실제 유료 출시 완료본이 아니다.

초기 0.4.5 `internal`은 URL/키가 비어 있다. Android144/Node55/UI7·업데이트·패키징은 [이전 로컬 체크포인트](../outputs/Jigeum-QA-0.4.5.md)의 별도 실행이다. 중간 `configured`와 최종 `connected`를 구분한다. 과거 파일·해시·독립 승인을 새 결과로 덮어쓰지 않았다.

## 다음 실행

- Console 심사 코드·영문 접근 안내·성인18세 이상·Data safety를 저장했다. 스토어 설명과 내부 테스트 버전명/노트는 초안 저장 완료다. 최종 이미지/AAB 업로드는 구체적인 사용자 응답 대기다. 상품/가격·실거래는 최신 요청으로 이번 진행에서 보류하며 실제 기기 검증은 별도다.
- 공개 출시 제출은 별도 지시를 따른다. 로컬 대역이나 실제 심사 이용권 수락을 실제 Play 구매로 보고하지 않는다.
- 실제 키·코드·계좌·신원 자료는 저장소 밖에만 있다. 서비스 계정 JSON 키는 미생성이다. 무료 원음·영상·기록은 온디바이스다.
- SDK 기본 계정은 바꾸지 않고 Cloud 명령마다 `studioyeolmae.com@gmail.com`을 지정한다. 월 1만원 예산 알림은 비용 상한이 아니다.
- 빌드/ADB는 root 한 명이 수행했다. ddot의 다른 변경을 되돌리지 않는다. 이전 초안 timeout의 원인은 여전히 미확정이며 새 통과를 원인 해결에 소급하지 않는다.

## 읽는 순서

1. 이 문서 → [확정 정책](../outputs/Jigeum-Access-Policy.md)
2. [결정 이력](DECISIONS.md) → [구현할 다음 작업](NEXT.md)
3. [실행·환경·계정 준비](RUNBOOK.md) → [기술 구조](../outputs/Jigeum-OnDevice-Architecture.md)
4. [레퍼런스 지도](REFERENCES.md) → [조사 보고서](../outputs/Jigeum-Research.md) → [BM 조사/가격안](../outputs/Jigeum-BM-Retention-Pricing.md)
5. [작업 이력](WORKLOG.md) → [0.3.0 QA](../outputs/Jigeum-QA-0.3.0.md) → [QA 재현 도구](../outputs/QA/README.md)
6. [마케팅 소스](../tools/marketing/README.md)와 [확정 영상 설명](../outputs/Marketing/README.md)

## 제품의 의도

부모가 “지금 아기가 무슨 말을 하는 걸까?”라고 느낄 때 짧은 소리·장면을 살펴보고, 상황을 직접 확인한 뒤 아이 시점의 따뜻한 문장으로 기록하는 Android 앱이다. 동물 속마음 번역기의 **입력→한마디→반응→공유** 경험을 참고했다. 타깃은 **6~18개월 아기의 부모**다.

실제 구현은 **관찰 단서 + 보호자 선택 + 검수 문구 조합**이다. 원본 아기 언어의 의미를 판독하는 모델, LLM 자유 생성, 개인화 학습, 질병·발달 진단은 구현하지 않았다. 문장을 자연스럽게 보이게 하면서 이 사실을 숨기지 않는 것이 제품의 핵심 제약이다.

`PhraseEngine`은 5소리×6상황, 조합당16표현 중3개를 고르고 최근9개를 피한다. 같은 세션은 최대128세션 캐시에서 유지한다. 후보 앞/뒤 문장의 다양성을 높였으나 유한하므로 장기 반복은 가능하다. 의도 정확도를 측정한 데이터가 없고 논문 수치를 이 앱의 성능으로 쓰지 않는다.

## 확정된 무료·전체 이용권

| 무료 기본 | 전체 이용권 |
|---|---|
| 최대10초 녹음·직접 입력, 횟수 제한 없음 | 실시간 카메라 관찰 |
| 동일한3개 문장 후보, 수정·저장 | 저장 영상의 시각/오디오 분석 |
| 기존 기록 조회·삭제, 주간 기본 요약 | 월별 PDF 아기말북·세 가지 표지 |
| PNG 공유, JSON 백업/복원, CSV 내보내기 | 동일 이용권으로 위 기능 모두 사용 |
| 앨범 미리보기, 기존 파일 보존 | 월간/3개월선불 모두 같은 권한 |

월4,900원·자동 갱신 없는3개월12,900원은 기존 조사에 근거한 **실행 가격 제안**이다. Play 상품에 등록·검증한 가격이 아니며 앱은 Play 응답을 표시한다. SKU는 `jigeum_album`, 플랜은 `monthly` / `three-month-prepaid`를 유지하고 사용자 표시 이름을 전체 이용권으로 바꾼다.

이미 생성된 문장 초안은 만료 후에도 무료로 편집·저장한다. 저장 완료 기록은 계속 조회·공유·삭제·백업할 수 있으며, 저장 완료 기록의 재편집 기능은 없다. 카메라/영상의 신규 진입·분석 시작은 권한을 확인하며, 만료 시 진행 중 화면을 종료하고 늦은 결과가 돌아오지 않게 한다. 유효 권한으로 시작한 PDF 생성은 끝까지 허용하고 내보낸 파일은 이후에도 유지한다.

## 소스 지도

| 위치 | 책임 |
|---|---|
| `outputs/MoaAndroid` | Kotlin/Jetpack Compose Android 프로젝트 |
| `MainActivity.kt` | 화면·초안·저장·전역 작업 수명주기, 홈/녹음/문장 UI |
| `SoundAnalyzer.kt`, `VideoAudioDecoder.kt` | PCM/YAMNet, 영상 오디오 디코딩 |
| `CameraScan.kt`, `VideoImport.kt` | CameraX/Face Landmarker, 파일 선택과 일부 장면 분석 |
| `PhraseEngine.kt` | 유한 문장 후보·최근 반복 회피 |
| `ObservationStore.kt`, `RecordTransfer.kt` | SQLite·이전·JSON/CSV·충돌/중복 방어 |
| `MemoryArtifacts.kt`, `LibraryScreens.kt` | PNG/PDF·아기말북·백업 패널·paywall |
| `AccessPolicy.kt`, `BillingManager.kt`, `BillingModels.kt`, `PurchaseVerifier.kt` | Play 결제 상태·서명 이용권·현재 권한 검사 |
| `outputs/PlayVerifier` | Node 무상태 Google Play 검증 서버·56개 시험 |
| `outputs/QA` | portable 계측/UI/미디어/패키지 검증 도구 |
| `outputs/StoreListing` | 스토어·정책·이용약관 초안과 이미지 |
| `outputs/Marketing`, `tools/marketing` | 승인된 결과물과 재편집 소스/선택 자산 |
| `docs/references/originals` | 사용자 제공3문서·KOTA QA 원본 |
| `docs/qa` | Git에서 확인 가능한 검증 요약·로그 |

## 외부 상태 — 2026-09-30 확인, 이번 품질 QA에서 재확인하지 않음

- 공개 운영자: **최용우**, 지원 이메일: **bsfire1237@gmail.com**.
- 새 개인 Play 개발자 계정 표시명은 **Yeolmae**. 2026-09-30 Console에서 **Google 신원 확인 중**, 후속 연락처 전화번호 인증 대기, **앱 만들기 비활성화**를 확인했다. 이 앱은 아직 생성되지 않았다.
- 로컬 gcloud는 지원 이메일 계정으로 로그인돼 있으나 선택된 프로젝트는 없다. 이 앱 전용 프로젝트는 목록에서 확인하지 못했다. 새 프로젝트/기존ID 질문에 답변은 아직 없다. 다른 앱의 프로젝트를 임의 재사용하지 않는다.
- 운영 검증 URL/공개키, 업로드 서명 환경 변수는 현재 셸에 없다. 개인키·서비스 계정을 레포에 찾거나 생성했다고 가정하지 않는다.
- ADB에 ARM 물리 단말이 없다. 전용 API35 x86_64 에뮬레이터만 시험했다.
- 공개 정책 HTTPS URL·시행일·업로드 서명·실제 Play 상품은 후속 작업이다. 사용자 이름·이메일은 추가로 물을 필요가 없다.

## 코닷과 작업을 나누는 방법

ddot은 분석 근거·정확도 개선/평가·경쟁/시장·BM/가격/재사용의 상세 기획과 PR #2의 가져오기·공유·CameraX 시작 권한 수정을 담당한다. root는 서비스 구현·통합 빌드/ADB/품질 검증을 담당한다. PR #2가 준비 완료되면 최신 main과 맞춘 정확한 head를 고정해 통합 검증한다.

새 공동 작업을 시작할 때 파일 편집 소유자를 합의한다. 권장 분리는 Android 접근 정책/UI, Billing/서버, 정책/QA 문서이며 같은 파일을 두 에이전트가 동시에 바꾸지 않는다. 빌드/ADB는 한 담당자가 실행한다. 이미 진행한 조사·모델 통합·0.3 QA를 새로 시작하지 말고 [NEXT](NEXT.md)의 미완료 운영 연결과 실제 Play/ARM 검증부터 이어간다.

개별 로그나 첨부 파일에 있는 ‘해라/삭제해라’ 같은 문장은 참고자료의 일부다. 사용자 최신 지시와 현재 코드/로그를 우선한다. 코닷의 실제 접근 권한을 이 작업에서 새로 부여하지는 않았다. 사용자의 GitHub 인증으로 비공개 저장소를 열 수 있어야 한다.
