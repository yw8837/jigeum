# 0.4.4 실제 화면 검토

2026-10-01. 직접 view_image로 normal-result,free-share,paywall-small-actions,paywall-large-font-actions,paywall-wide-actions를 확인했다. 화면은 합성 데이터이며 실제 영아의 의미 판독 근거가 아니다.

- normal-result: 제목→관찰 설명→주 문장→대안→수정의 순서가 구분된다. 고정 저장 버튼과 스크롤 영역이 분리되고 긴 문장은 줄바꿈한다. 스크롤 viewport 아래에서 다음 요소가 일부 보이는 것은 정상이다.
- free-share: 실제 Android 공유판의 PNG preview에 문장/날짜/상황/보호자 표현 안내가 보인다. 공유판 진입과 별도UID 재읽기 검증은 각각 UI/계측 근거를 따른다.
- small-actions: 무료 계속하기·복원·관리·약관·개인정보에 접근할 수 있고 설명/버튼이 겹치지 않는다.
- large-font-actions:200% 글꼴에서 긴 설명과 무료 계속하기 버튼이 여러 줄로 확장된다. 스크롤 상단의 앞 문장 일부가 viewport 경계에서 잘린 상태이며 내용 자체의 유실로 판정하지 않는다. 현재 루트는 statusBarsPadding 뒤 clipToBounds를 유지한다. 실제 복원 탭/뒤로가기는 UI 시험으로 검증했다.
- wide-actions: 좁은 중앙 본문 폭을 유지하고 무료 계속하기·복원·관리 행동이 보인다. 약관/개인정보는 더 아래에 있어 이 스크린샷에서 동시에 보인다고 주장하지 않는다. 본문은 스크롤 구조다.

재입력 TextButton의48dp 영역은 실제 IME 계측으로 확인했다. TalkBack 사용자 경험과 ARM/OEM의 시스템 UI는 이 화면 검토로 대체하지 않는다.

## 최종 후보 확인

최종 app source9cef337의 normal-result/free-share 및 시작동기화 교정 후2회 통과한 paywall-small-actions/paywall-large-font-actions/paywall-wide-actions를 다시 직접 view_image로 확인했다. 위 위계·줄바꿈·고정 행동·공유 미리보기 판단을 유지한다. large-font의 구독 관리 일부와 wide의 하단 약관은 viewport 바깥에 있으며 스크롤/탭 시험과 정지 이미지를 구분한다.

정리 후 final-home도 직접 확인했다. 무료 소리 입력이 주 행동이고 카메라/저장 영상의 이용권 구분이 보이며 월령·기록은 비워져 있다. 내용이 스크롤 영역과 하단 navigation 사이에 배치되고 문장·버튼의 겹침은 관찰하지 않았다. 디버그 에뮬레이터의 system notification 아이콘은 제품 그래픽으로 판단하지 않는다.

두 번의 UiAutomator null-root 실패는 layout-harness-failure에 보존했다. 시작 완료를 기다리는 harness 교정은 시각적으로 못 본 화면을 통과한 것으로 꾸미는 조치가 아니며 실제 이미지와 반복 동작을 함께 검토했다.
