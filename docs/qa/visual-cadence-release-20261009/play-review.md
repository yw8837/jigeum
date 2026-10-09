# Alpha beta36 — 영상 관찰 간격 개선

현재: **2026-10-09 19:27 KST beta36이 선택한 테스터에게 제공됨을 확인**.
기존 Alpha 출시 ID16이며 Console 게시일은 **10월9일19:24 KST**다.

## 승인·게시 확인

- 실제 Alpha의 최신 출시 `0.5.22-beta (36) - visual cadence`에 **선택한 테스터에게 제공됩니다** 표시.
- 게시 개요의 검토 항목 해소·관리형 게시 사용 중지, 앱 업데이트 게시 알림 확인.
- [제공 원문](play-available.txt), [제공 화면](play-available.png),
  [게시 개요](play-publishing-complete.txt), [테스트 및 출시](play-release-complete.txt).
- 업체 계정별 설치·업데이트 적용·12명 연속14일을 확인한 것은 아니다.
- 이번 확인에서 신규 업로드·빌드·ADB·테스터 설정 변경·업체 메시지는 없었다.
- 제공 증거/문서를 커밋·푸시·SHA/clean 확인하고 승인 확인 자동화33을 일시중지한다.
- 전체 장기 개선 목표는 미완료다. 새 실폰성능·음성보류·전체AV맥락/욕구정확도,
  정책 차단된 build 정리 잔여는 그대로 보존한다.

## 18:56 제출 당시 확인한 상태

- 게시 개요의 **검토 중인 변경사항** 아래 `0.5.22-beta (36) - visual cadence`가 표시됐다.
- 관리형 게시 사용 중지. 사전 검사 완료 후 검토로 전송된다는 안내와 최대14분 표시를 확인했다.
- API26이상 / targetSDK36. 지원 기기 감소0, 신규 설치112MB(-901B), 업데이트2.55MB.
- 제출 차단 오류0. code36의 경고2개는 가독화 파일 없음과 네이티브 디버그 기호 미업로드 권고다.
- [사전 검사 원문](play-preflight.txt), [검토 요청 원문](play-submitted.txt), [화면](play-submitted.png).
- 자동화33을36 승인 확인 ACTIVE/15분으로 갱신했다. 실제 제공 확인 뒤 증거·문서·SHA를 기록하고 일시중지한다.

- 제출파일: `work/visual-cadence-release-20261009/final/Agattok-0.5.22-beta-code36.aab`.
- SHA256: `f160674fa45947adaa8adceefefb4777d8dca0264e21fcc645a5d463a0a00ab3`.
- 코드·QA `8436f55f03c7b14dae6ef469fc5f9b317b048fcd` 푸시/SHA일치/clean 확인 뒤 업로드했다.
- 출시명: `0.5.22-beta (36) - visual cadence`.
- 새모델·권한·요금제/테스터목록 변경은없다. 비교용release/연구APK는업로드하지않는다.
- Console직접AlphaURL에서로딩이지연됐다. 같은세션앱목록→대시보드→테스트및출시→Alpha의
  화면메뉴로접근해복구했다. 기존제출을취소하거나트랙을다시만들지않았다.

## 범위

선택구간전체순차decode에서0.2초간격의관찰과시간순연결을사용한다.
공동AV맥락모델·발성보류회복·감정/욕구정확도입증은아니다.
실폰성능미측정은[QA](../visual-cadence-release-20261009.md)에명시했다.

## 관찰된 별도 후속사항

새초안진입전에code35의테스트및출시페이지에서다음안내를보았다.
이번200ms변경으로해결했다고표시하지않으며별도검토한다.

- DEX난독화1%/2027년2월기한안내.
- androidx.fragment:fragment1.1.0오래된SDK안내.
- Android15 edge-to-edge권고.

19:27의36 테스트및출시페이지에서도 위세항목이 같은내용으로표시됐다.
새배포가위권고를해소했다고보고하지않는다.후속성능/호환성개선과제로유지한다.

앱대시보드에는12명참여선택·현재1일이표시됐다. 정확한전체참여인원이나실제앱실행수와
동일하지않고,14일완료나프로덕션승인을확인한것은아니다.

## 링크

- [설치·업데이트](https://play.google.com/store/apps/details?id=com.moa.babylanguage)
- [웹 테스터 참여](https://play.google.com/apps/testing/com.moa.babylanguage)
