# 2026-10-23 공개 출시 준비

## 실제 Play 확인

- 사용자 `출시하자` 및 `출시가능이야 메일왔어` 요청으로 공개 출시 진행 권한을 확인했다.
- Console 대시보드: **앱에 Google Play 프로덕션 액세스 권한이 부여되었습니다.**
  [화면](production-access-approved.png), [접근성 원문](production-access-approved.txt).
- 기존 Alpha 출시20: **0.5.26-beta (40) - community and expressions**가
  **선택한 테스터에게 제공됩니다**, 게시일 원문 `10월 22일 오후 9:31`.
  [화면](alpha40-available.png), [원문](alpha40-available.txt).
- 프로덕션은 **비활성**, 국가/지역 미지정. 새 정식 AAB/출시 초안/검토 제출은 아직 없다.
  [화면](production-inactive.png), [원문](production-inactive.txt).
- Play 정기 결제 목록에는 등록된 상품이 없고 `구독 만들기`가 표시된다.
  [원문](subscriptions-empty.txt). 가격 제안은 실제 상품/가격 승인이 아니다.

## 공개 이용 범위 결정 대기

기존 정책은 무료60초 촬영/10초 소리 분석/100MB 가져오기, 전체 이용권120초/20초/200MB 및
시각 관찰/PDF다. 상품 등록·가격·실제 거래는 보류되어 있었다.
무료 기본판 선공개(추천), 구독까지 준비 후 공개, 전체 기능 무료 공개 중 사용자 선택을 요청했다.
**답변 없이 새 가격을 정하거나 전체 기능을 무료로 공개하지 않는다.**

## 발견·준비

기존 beta40은 테스트 전체 기능 개방이다. 기존 release40은 정책 비교용으로 AST/영아 모델이 없다.
어느 파일도 정식 출시 파일로 승급/재사용하지 않는다.

- 새 소스 버전0.5.27/code41.
- `agattokAstProduction=true`를 추가하여 release에서 동일한 고정 AST·영아 존재·caption을 포함한다.
  베타/연구 플래그 혼합 및 다른 variant 실행을 차단한다.
- FULL_ACCESS_PREVIEW/FULL_ACCESS_CLOSED_TEST는 모두false이며, 모델/임계값은 변경하지 않았다.
- 모델·caption SHA 고정 검사와 실제 ARM 측정(7일 이내) 확인을 release에도 연결했다.
- 빌더에 `production-basic` 경로와 기존 `paid-candidate`의 명시적인 전체 모델 구성을 추가했다.
  실제 상품이나 과금 설정을 생성한 것은 아니다.
- 첫 정식 lint는 ResultMomentCard의 Media3 UnstableApi opt-in 누락11건으로 실패했다.
  해당 함수에 AndroidX OptIn을 명시했다. 렌더링 로직 변경은 없다.
- 수정 후 정식 Kotlin 컴파일·lint, 고정 모델/caption 검사,10/22 S25 ARM증거 확인 통과.
  [실행 로그](production-compile-lint-fixed.log), [첫 실패](production-compile-lint.log).
- Python 배포검사15개 통과(옵션 거부/ARM증거/고정모델 payload 검사).
- 실제 Gradle에서 beta혼합, debug variant, ARM증거누락3경로 거부를 확인했다.
  `guard-mixed-beta.log`, `guard-wrong-variant.log`, `guard-missing-proof.log`.

## 한계·다음 단계

공개 이용 범위 답변 후 해당 UX/설정·스토어 설명·IARC를 맞추고 최종 서명 패키지를 생성한다.
최종APK/AAB의 모델/flags/서명, beta40→정식41 기록/미디어 보존과 실제 분석/저장 검증 후
대한민국 프로덕션 출시를 제출하고 Google의 실제 상태를 확인한다.
사용자 폰은 이미 반환되어 이번 작업에서 연결·사용하지 않았다. 새 예약이나 자동화 재개도 없다.
최신 인식 모델을 유지하는 작업이며 일반 인식률·감정/욕구 정확도 개선을 입증하지 않는다.
Google/Kakao 로그인·마이페이지와 사용자 도메인의 운영자 callback404는 기존 미완료 사항이다.

Console의 비차단 후속 안내: DEX 난독화1%, 해결 기한2027년2월 및 일부 edge-to-edge 화면 안내.
검증된 MediaPipe/JNI 경로를 유지하려고 현재 비축소 설정을 유지했다. 이를 해결 완료로 기록하지 않는다.
