# Play 등록 준비 — 2026-10-01

## 최신 연결 상태 — 0.4.5/code12 connected

전용 Cloud billing·서버/공개 정책·Play runtime 앱 한정 권한·최종 연결 APK/AAB를 실제 확인했다. [연결 진행](release/20261001/CONNECTION-PROGRESS.md), [최종 QA](qa/0.4.5/connected/README.md), [운영](release/20261001/OPERATIONS.md)을 먼저 따른다. 실제 Play 상품·거래·ARM·테스트 배포·공개 제출은 미완료다.

## 실제 Console 상태

- 계정 **Yeolmae**, 개발자 ID `8736564730162379651`.
- 앱 **지금 내 기분이 그래**, ID `4975993958261665020`, 패키지 `com.moa.babylanguage`, 한국어·무료 설치 + 전체 이용권 인앱결제. 별도 부제 없음.
- [대시보드](https://play.google.com/console/u/0/developers/8736564730162379651/app/4975993958261665020/app-dashboard) **9/11**. 정책 URL·심사 접근·성인18세 이상 저장 성공. 카테고리/지원 이메일·광고/광고ID 없음·정부/금융/건강 아님·승인한 IARC 설문 완료.
- 남은 2항목: 데이터 보안·스토어 등록정보. 심사 코드/영문 안내는 승인 후 저장했고 선택 파트너 기기 사용은 껐다. 내부 테스트 초안 AAB 업로드는 사용자 응답 대기.
- IARC 대한민국3세 이상/북미전체이용가/PEGI3/브라질14세 이상. 사용자는 성인 보호자18세 이상이며 콘텐츠 등급과 구분한다.
- Play runtime 계정은 이 앱 하나의 읽기·구매 조회·주문/구독 관리만 승인/저장 후 활성·4권한을 확인했다. 계정 전체 권한은 추가하지 않았다.
- 판매자 프로필·NH농협 지급 계좌 저장 완료, 소액 입금 확인 대기. 금융/신원 원본과 등록 캡처·계정ID는 Git에 넣지 않는다.
- 전용 `jigeum-play-20261001`은 ACTIVE/billing=true. 원계정의 생성 quota 실패/한도 요청·프로젝트 삭제 예약은 이전 이력이며 사용자 지정 studio계정으로 생성/배포했다. 다른 제품 자원을 재사용하지 않았다.
- 새 개인 계정은 **12명 이상의 테스터가 14일 연속 비공개 테스트 참여** 요건을 표시한다. 확인 시 opt-in0명, 생산 신청 비활성. 내부 테스트는 이 요건을 대신하지 않는다.

## 최종 연결 산출물

- `outputs/Jigeum-Android-connected-0.4.5-code12-upload-signed.apk`
- `outputs/Jigeum-Play-connected-0.4.5-code12-signed.aab`
- [정확한 SHA/크기](qa/0.4.5/connected/artifacts.json). 실제 서버 URL/공개 키가 들어 있고 심사 이용권 왕복을 검증했다. Play가 재서명한 배포 APK/실제 구매 검증본은 아니다.

## 이전 등록용 로컬 파일

`outputs/StoreListing/registration-20261001/`에 제목·짧은 설명·전체 설명·내부 릴리스 노트·검토자 안내, 실제 서명 release 화면, 아이콘·피처 그래픽을 정리한다. 전체 설명은 **운영 결제를 연결한 최종 서비스용 초안**이다. 결제가 꺼진 내부 빌드의 혜택과 혼동해 게시하지 않는다.

서명 AAB: `outputs/Jigeum-Play-internal-0.4.4-code11-signed.aab`. 내부 부트스트랩용이며 **실제 구매 비활성**이다. 공개 유료 서비스 제출본이 아니다. 로컬 시험용 업로드키 서명 APK: `outputs/Jigeum-Android-release-0.4.4-code11-upload-signed.apk`. Play가 재서명한 실제 배포 APK와는 다르다. 기존 unsigned AAB/debug APK/QA ZIP은 보존한다.

실행과 해시는 [등록 준비 근거](release/20261001/README.md)에 기록한다. 서명만 바꾸고 Android 서비스 코드·모델·SDK를 바꾸지 않았다. 실제 Play 설치·구매·ARM·16KB 실기기 시험은 미완료다.

## Console 입력 기준

| 항목 | 입력/처리 |
|---|---|
| 이름/언어/설치 | 지금 내 기분이 그래 / 한국어 / 무료 |
| 앱 유형·카테고리 | 앱 / 출산·육아 |
| 지원 | 최용우 · bsfire1237@gmail.com. 공개 전화번호·웹사이트는 임의 작성하지 않음 |
| 대상 연령 | **성인 보호자: 18세 이상**. 촬영 대상이 아기라는 이유로 아동 사용자 연령을 선택하지 않음 |
| 광고 | 앱 내 광고 SDK·광고 게재 없음. Play의 외부 마케팅 기본 설정과 구분 |
| 정부/금융 | 정부 앱·금융 상품·대출·결제 지갑 기능 없음. Play로 앱 기능을 사는 것은 금융 상품 제공과 구분 |
| 건강 | 건강·발달·울음 원인 진단 또는 모니터링 앱으로 소개하지 않음. 최종 양식을 실제 기능과 대조 |
| 콘텐츠 등급 | 실제 IARC 설문으로 결정. 등급을 임의 확정하지 않음. IARC 약관 동의 시에는 사용자 확인 필요 |
| 앱 접근 | 앱 로그인 없음. 무료 기능은 즉시 사용. 유료 기능은 검증된 Play 구매 필요. ‘모든 기능 제한 없음’이라고 답하지 않음 |
| 정책 URL | 공개 HTTPS 페이지의 실제 URL 필요. 로컬 파일·미배포 주소를 입력하지 않음 |
| 데이터 보안 | 로컬 기록·사용자 공유 예외·SDK·구매 검증을 구분. 운영 결제 활성화 시 구매 데이터/설치 식별자 외부 처리를 선언 |
| 유료 설명 | 카메라·저장 영상·PDF 전체 이용권. 무료 기록·백업·PNG·기존 초안을 잠그지 않음 |

### 심사자 전체 접근 — 실제 왕복·Console 저장 완료

무료 기능은 로그인 없이 사용하고 카메라·영상·PDF는 전체 이용권이 필요하다. 별도 코드로 서버가 서명한 `play-review` 이용권만 수락한다. 실제 release 앱의 카메라 시작/종료·영상 화면·PDF 생성/공유·해제/재잠금·무료 기록 보존을 확인했다. 구매 대역이나 화면 Boolean으로 열지 않는다.

재사용 코드 자체는 만료 없이 검증 서버의 지정 secret 버전으로 확인하고 설치별 서명 grant는24시간이다. 만료/재부팅 후 같은 코드로 재확인한다. 사용자 승인 후 영문 접근 안내와 실제 코드를 이 앱의 Console에 저장했고 저장 성공을 확인했다. 선택 파트너 기기 테스트 공유는 껐다. 서버/코드 운영 중단 시 심사 접근도 중단될 수 있으므로 심사 기간에 유지해야 한다.

### 상품 설정 준비 상세

- 구독ID `jigeum_album`, 표시명 **전체 이용권**.
- 혜택: 카메라로 보이는 움직임 살펴보기 / 선택한 영상의 소리·장면 관찰 / 월별 PDF 아기말북.
- `monthly`: P1M 자동 갱신. 기존 제안 **₩4,900/월**.
- `three-month-prepaid`: P3M 선불, 자동 갱신 없음. 기존 제안 **₩12,900/3개월**.
- 두 플랜은 동일한 권한. 출시 국가·세금·판매자 프로필·실제 Play 표시 가격을 확인해야 한다. 제안값을 실제 등록 가격이라고 쓰지 않는다.
- 할인·무료 체험·가격으로 구독 이름/혜택을 채우지 않는다. 앱은 실제 Play 가격만 표시한다.

## 업로드 키 보관과 빌드

업로드 RSA3072 키를 저장소 밖 `%USERPROFILE%/.jigeum/signing`에 생성했다. 비밀번호는 현재 Windows 사용자의 DPAPI로 보호한다. 폴더 권한은 현재 사용자와 SYSTEM이다. **개인키와 비밀번호를 Git·공유 ZIP·APK/AAB에 넣지 않는다.** 공개 인증서만 등록 근거에 보관한다.

DPAPI 파일은 다른 컴퓨터에서 복호화할 수 있는 휴대용 백업이 아니다. 공개 출시 전에 키스토어와 비밀번호를 모두 별도의 암호화된 오프라인 보관소/비밀번호 관리 경로에 백업해야 한다. 업로드 인증서와 Play 앱 서명 인증서는 구분한다. 인증서의 Play 실제 연결 여부는 Console 업로드 뒤 확인한다.

```powershell
# 기존 키가 있으면 재생성하지 않는다.
./tools/release/prepare-upload-key.ps1 -JavaHome '<JDK 경로>'
# 비밀값은 저장소 밖 DPAPI 파일에서 프로세스 환경으로만 읽는다.
./tools/release/build-play-bundle.ps1 -Purpose internal `
  -JavaHome '<JDK 경로>' -AndroidSdk '<SDK 경로>' `
  -Destination 'outputs/<새 내부 AAB 이름>.aab'
```

`paid-candidate`는 실제 HTTPS 검증 URL·공개 키가 있어야 빌드한다. 이것만으로 서버/실제 거래 검증 통과를 의미하지 않는다. 운영 설정 활성화 후 code11보다 높은 versionCode의 새 후보를 만들고 이전 기준본을 덮어쓰지 않는다.

## 비공개 테스트 실행 순서

1. 서명 부트스트랩 AAB를 내부 테스트 **초안**에 올리고 패키지·서명·API36·번들 경고를 확인한다. 사용자에게 구체적인 파일 업로드 승인을 받은 뒤 진행한다.
2. 전용 Cloud 자원·HTTPS·서명키·서비스 계정과 Play 접근·상품을 연결한다. 프록시별 원본 총량과 실제 사용자별 제한을 함께 검증한다. [운영 계획](release/20261001/OPERATIONS.md).
3. 운영 설정을 넣은 새 후보로 실제 Play 구매·보류→완료/취소·복원·ACK·환불/회수·만료·재부팅·계정 변경을 시험한다. 캡처에는 구매 토큰·테스터 개인정보를 남기지 않는다.
4. 실제 ARM 기기에서 녹음·전후면 카메라·선택 영상·PDF·발열/메모리·권한 거부·오프라인을 확인한다. 가능하면16KB ARM도 시험한다.
5. 실제 배포 제공자·국가·접근 로그·보존기간을 확정해 정책·약관을 HTTPS로 게시하고 Data safety와 앱 내 고지를 맞춘다. 현재 초안의 ‘게시 전 확정’을 그대로 공개하지 않는다.
6. 성인 보호자 테스터12명 이상을 모집한다. 최소14일 연속 opt-in과 실제 사용·피드백을 확인한다. 초대는 사용자 승인 없이 보내지 않는다.
7. 테스트 결과·발견한 문제·실제 수정사항으로 생산 접근 신청 답변을 작성한다. 가상의 피드백/날짜/기기/거래로 완료 표시하지 않는다.
8. 공개 출시 심사 제출과 출시 단계는 사용자 요청에 맞춰 별도로 진행한다.

## 공식 요구사항 확인 — 2026-10-01

- [새 개인 계정 테스트 요건](https://support.google.com/googleplay/android-developer/answer/14151465): 12명·14일 연속, 이후 신청 및 검토. 날짜는 참여 시작과 Console 기준에 따라 결정.
- [현재 타깃 API 요건](https://support.google.com/googleplay/android-developer/answer/11926878): 2026-08-31부터 신규 앱/업데이트 API36 이상. 현재 target36.
- [앱 서명](https://developer.android.com/studio/publish/app-signing): AAB 업로드 키와 Google의 실제 앱 서명 키 구분.
- [스토어 그래픽](https://support.google.com/googleplay/android-developer/answer/9866151): 512px 아이콘,1024×500 피처 그래픽,실제 화면2장 이상. JPEG/24bit PNG 스크린샷,최소320px/최대3840px 및 장변은 단변2배 이하. 초기1080×2400 촬영본은 별도 보존하고 제출용은 실제 기기 표시를1080×2160으로 바꿔 재촬영했다. RGB PNG 화면4장과32bit 아이콘을 픽셀 변경 없이 검증했다.
- [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469): SDK 전송도 포함. 일시 처리도 설문에 기입. 온디바이스 처리 제외·사용자 주도 공유·서비스 제공자 예외는 정의에 맞춰 적용.
- [구독/플랜](https://support.google.com/googleplay/android-developer/answer/140504): 월 자동 갱신과3개월 선불 별도 base plan,동일 구독.
- [심사자 접근 요구사항](https://support.google.com/googleplay/android-developer/answer/15748846): 구독 paywall 뒤까지 무료로 접근할 실제 방법과 영문 안내, 재사용 가능·상시유효·지역독립. [외부 공유와 IARC](https://support.google.com/googleplay/android-developer/answer/11070862): 다른 앱을 통한 공유와 앱 고유 사용자 콘텐츠 교환을 구분.
- [프로젝트 생성 한도](https://cloud.google.com/resource-manager/docs/creating-managing-projects#managing_project_quotas_2): API 호출 한도가 아닌 생성 개수다. 삭제 예정 프로젝트도30일 완전 삭제까지 한도에 포함되므로 삭제가 즉시 새 생성 슬롯을 보장하지 않는다.
