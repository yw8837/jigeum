# Play 비공개 테스트 업데이트 — 0.5.9-beta/code23

확인일: 2026-10-03 · Yeolmae / **아가톡 - 지금 내 기분이 그래** / `com.moa.babylanguage`.

## 현재 상태

**beta23 AAB 업로드·출시 저장·검토 전송 확인 버튼 처리를 완료했다.** Console의 **‘검토 중인 변경사항’** 아래 **‘비공개 테스트 - Alpha / 0.5.9-beta (23) - 관찰 안내와 저장 개선’**을 확인했다. 이 시점에는 **Google 빠른 검사 실행 중**이며, 화면에 ‘검사가 성공적으로 완료되는 즉시 변경사항이 검토를 위해 전송됩니다’가 표시된다. 검토 요청은 접수됐지만 자동 검사 완료·심사 승인·새버전 게시 완료로 보고하지 않는다. [확인 화면](play-beta23-review-submitted.jpg).

기존 beta22의 Alpha 게시 완료(2026-10-02 16:08)를 이번 작업에서 확인했다. 관리형 게시는 사용 중지 상태다. Alpha의 ‘전체 출시’는 비공개 트랙의100%이며 공개 프로덕션이 아니다. 공개 프로덕션·상품 등록·실제 거래는 수행하지 않았다.

Play 미리보기: 제출 차단 오류0, 경고2(가독화 파일/네이티브 디버그 기호 미제공), 지원 기기 감소0. 현재 minifyfalse이므로 난독화 mapping파일은 생성하지 않는다. 네이티브 기호 미제공에 따른 충돌 분석 한계는 남아 있다. [경고 화면](play-beta23-warnings.jpg). Play 표시 신규 설치30.1MB·업데이트2.44MB는 해당 Console의 추정치이며 모든 기기의 동일 크기를 보장하지 않는다.

## 업데이트 패키지

- 제출 대상: `outputs/Agattok-0.5.9-beta-code23.aab`
- AAB SHA256: `4ec607c43b978642bc58bd6736d751a1fe74c7dbce185a88f383e4ccc6cccfc6`
- APK SHA256: `b938c70436815e84bc81cfc07e9e578b270b965091eff02f2f0d3c56736b5dfc`
- versionCode23 / versionName0.5.9-beta. package/signature/모델·라이선스/backup 제외/beta 설정 검사를 통과했다.
- 비교용 `release23`과 sideload preview는 제출하지 않는다.
- [출시 노트](../20261002/RELEASE-NOTES-059.md) / [QA와 한계](../../qa/sound-visual-0.5.9/README.md).

## 테스터 안내

1. [아가톡 테스트 그룹 가입](https://groups.google.com/g/agattok-test/about)
2. 같은 Google 계정으로 [Play 테스터 참여·설치](https://play.google.com/apps/testing/com.moa.babylanguage)
3. 이미 Play로 설치했다면 새 버전 게시 후 앱 페이지에서 업데이트한다. 자동 업데이트 반영 시점은 Play/기기 설정에 따른다.

그룹과 참여 링크는 유지한다. 테스트 beta에서는 구매 없이120초/20초·200MB·시각 관찰·PDF를 제공한다. 자체 자동 만료는 없으며 정식 전환은code24 이상과 실제 Play 서명/업데이트 경로 확인이 필요하다. 기존 기록·무료 내보내기를 보존한다.

## 별도 확인이 필요한 것

- 사용자 제공 두 영상의 발성 누락은 미해결이다. 이번 버전은 보류 안내·구간 제안·영상 관찰·근거 출처를 개선했다.
- ARM/OEM 실제 기기·Play split/자동 업데이트·실제 거래는 로컬 QA로 입증하지 않는다.
- 그룹 회원 수·링크·업데이트 제출을12명/연속14일 참여 충족으로 계산하지 않는다. 실제 참여 및 프로덕션 액세스 신청은 별도다.
- 과거 계정/그룹 구성과 승인 과정은 [2026-10-02 기록](../20261002/PLAY-STATUS.md)을 참조한다.
