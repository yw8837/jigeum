# UltraQA Report — 다음 장면으로 이동·인식 후보 이식

2026-10-06 · 기준363ea7c · 제품 소스5311e6f · 검사 보정fce7dd5

## 최종 제품 검증 결과

**제품 Android 고유105개 최종 통과, 최종 실패/제외0.** [기계 판독 요약](evidence-summary.json).
일반 UI50 + 새 native 재생1 + 가로2 + 실제모델/미디어52이며 재실행은 중복 세지 않았다.
연구 EfficientSED Android계측1개(내부7조건)는 별도이며 새 영아 정확도 시험이 아니다.
서명29→30의 구형기록·MP4 bytes 보존, 최종 beta의 약국 원본0–10초와 실제OS picker/새촬영취소를 통과했다.
최종 서명 화면에서 중복 미리보기 없이 아기말이 첫 화면에 표시되는 것도 직접 확인했다.
beta/release 패키지·서명·payload·실제dex플래그 통과. Lint 각각 오류0/기존경고10.
QA앱 제거·에뮬레이터 종료는 완료했으나 임시 AVD 삭제가 자동 승인 검토에 차단돼 전체 정리는 미완료다.

## Goal and success criteria

[실행 계약](../../superpowers/plans/2026-10-06-result-navigation-and-sed.md).
결과에서 다음 분석으로 이동을 명확하게 하고 원본·저장·이용권 경계를 보존한다.
EfficientSED는 공식 frontend와 수치 비교 후 연구용 Android를 검사한다.
새 인식률/욕구 정확도를 QA 개수로 주장하지 않는다. 비공개 beta만 제출한다.

## Scenario matrix

| ID | 의도/사용자 모델 | 준비·실행 | 기대 | 실제·수정·증거 | 정리 |
|---|---|---|---|---|---|
| N1 | 결과→다른구간→새결과 | 실제Compose·모델대역, 다른선택 | 원본유지·새구간전달·낡은메모없음 | 통과 navigation-polished | 전용AVD |
| N2 | 다른파일선택/취소/손상파일 | 테스트registry; 최종OS picker별도 | 취소/실패시이전결과·원본유지 | 대역/최종signed OS취소통과 | 임시fixture제거 |
| N3 | 새촬영/Back/연속탭 | Compose+실제Android | 분명한전환·미저장확인·중복작업없음 | 통과 navigation-polished | 전용AVD |
| N4 | 저장·앨범중이동/복귀 | busy콜백·미디어회귀 | 파일삭제·늦은콜백오염없음 | busy/취소후reader해제/미디어회귀통과 | 앱제거 |
| N5 | 작은화면·200%글자 | 화면/글자크기변경 | 주요행동노출·스크롤접근 | 가로640×320·100/200%통과, 중복footer축소/scroll0 | 세로복구 |
| N6 | 무료/beta·이용권변경·취소 | 접근/구간기존회귀 | 무료10초/beta20초·확인시점유지 | 통과 navigation-polished | 앱제거 |
| N7 | 실제약국10/20초·취소 | 로컬원음·현재모델 | 기존회복유지·새성능주장없음 | integration5/ui-real1/media46 통과 | 복사원본제거확인 |
| N8 | 서명29→30·기록·MP4 | 실제서명설치/업데이트 | 문장/MP4bytes·전체beta유지 | signed-upgrade-final 통과211.87초 | 복사파일제거 |
| S1 | mel모델이식·경계/비정상PCM | Kotlin/공식Python·ONNX | 입력범위·수치일치·유한출력 | 합성7조건/출력28행·실패gate6검사통과 | private출력ignored |
| S2 | Android소형모델/취소 | opt-in연구실행 | 실제ONNX실행·자원회수·시간 | 7조건mel/strong/weak일치·preterminated복구통과 | 임시소스/모델제거 |
| P1 | beta/release/Lint/오해성공 | 실제패키지·서명·flags·파싱 | exit+실패/skip검사,연구모델미포함 | final-package-result 통과/SED weights·dex없음 | 캐시기존유지 |
| P2 | 기존변경보존·입력주입/긴명령 | 고정branch/자료는data·boundedrunner | 타작업보존·비밀/원본유출없음 | bounded cleanup true/잔여0, 자료는명령권한아님 | 격리state정리기록아래 |

## 경쟁 리뷰 적용

- [검증된31리뷰](../../research/20261006-competitor-review-opportunities.md)의 탐색 마찰을 사용자의 실제 결과 재진입 불편과 대조한다.
- 반복 문장/인식 실패는 새 모델 후보의 별도 비교 과제다. 새 버튼을 인식률 개선으로 보고하지 않는다.
- 실패 후 결제강제, 결과/기존저장 가림, 로그인강제는 추가하지 않는다. 무료 결과와 원음 보관 유지 여부를 회귀한다.

## Commands run / 실제 계측 범위

증거 루트는 ignored `work/result-navigation-20261006/`이다. 원본·미디어·APK·모델은 Git에 넣지 않는다.

- `build_debug.py build-ui-polish`: exit0,43.27초,600초 상한/12GiB Job, cleanup true/잔여0.
- `run_tests.py navigation-polished ...`: 제품 일반50개 통과. 함께 선택된 가로전용2개는
  세로host비율1.2857에서 실패(exit0만 믿지 않고 runner실패를 감지했다). 앱 결함이 아닌 실행설정 오류다.
- `wm user-rotation lock 1` 및 `wm fixed-to-user-rotation enabled` 후
  `run_tests.py navigation-landscape-polished ResultNavigationTest#shortLandscapeKeepsResultAndRangeActionsReachable,ResultNavigationTest#shortLandscapeAtDoubleFontKeepsResultAndRangeActionsReachable`:
  2개 통과/실패·제외0,12.40초. normal50과 합쳐 최신 UI52개다. 세로설정을 복구했다.
- `integrated.py integration-media integration,ui-real,media`: 5+1+46개 통과/실패·제외0.
  실제약국10/20초·실제native취소/복귀·미디어저장/내보내기검사. 이 실행뒤 변경은 UI compactfooter/scroll만이다.
- 제품 계측 고유ID **104개**의 최종 결과가 모두 통과다. 같은 검사의 재실행은 중복 계산하지 않았다.
  초기가로host설정 실패 이력은 유지하며 최초부터 모든실행이성공했다고 보고하지 않는다.
- `sed_run.py`: 별도연구 Android계측1개(내부7조건) 통과. 제품104개와 별도다.
  [수치와한계](../../research/20261006-efficientsed-kotlin-frontend.md)를 따른다.

## Failures found / Fixes applied

1. 결과화면에서다른장면으로가는길이불명확했다. 결과→같은원본다른구간/다른파일/새촬영을명시했다.
2. 다른파일취소·검사실패시이전결과보존, 새분석시낡은초안초기화, 편집취소시원래분석구간복원을검사했다.
3. 취소직후native/IOreader가남을수있어 실제작업완료까지임시원본삭제를미룬다. 저장중탐색도잠근다.
4. 짧은가로화면에서중복하단버튼이본문을압박했다. 높이480dp미만에서하단중복버튼만줄이고
   상단구간이동/주저장버튼을유지했다. 결과·편집전환때스크롤을맨위로초기화한다.
5. 첫AVD경로의한글문자에뮬레이터초기화실패와가로host누락은harness문제다.
   이번전용AVD만ASCII경로로옮기고방향을명시했다. 이전거부폴더를우회정리하지않았다.
6. 최초 서명본의 실제 화면에서 원본 preview와 결과 카드 사진이 중복돼 문장이 아래로 밀렸다.
   결과 정지 중 추가 preview를 접고, 재생 요청 때 영상과 소리를 함께 복원한다.
   seek와 Surface 준비 뒤 재생하며 멈춤·선택 구간 종료 때 preview를 다시 접는다.
   진행 문구의 잘못된 조사도 바로잡았다. 최종 signed 화면에서는 아기말을 첫 화면에서 확인했다.
7. 새 재생 검사의 마지막 assertTextContains가 기본적으로 완전 일치해 정상 긴 문구를 거절했다.
   전체 문구 assertTextEquals로 보정(fce7dd5) 후 해당 검사 통과9.88초. 제품 코드는 바꾸지 않았다.

## 최종 보완 후 명령·패키지

- `build_debug.py build-preview-final`: exit0/43.94초. `run_normal.py navigation-preview-final`에서
  일반50통과, 위 문구 검사1실패. 보정 후 `build-test-assert-final` exit0/32.50초 및
  `run_tests.py playback-assert-final ResultNavigationTest#resultUsesOneCardAndSourceReplayWhileEditorKeepsVideoPreview` 1개통과.
- `navigation-landscape-preview-final`: 정상·200%글자2통과/14.49초. `layout-final/` 스크린샷 확인.
- `integration-media-final`: 실제모델5/47.90초, 실제취소UI1/12.10초, 미디어46/27.52초 전부통과.
  격리앱/원본복사정리와 기존제품상태보존 확인. 최종제품소스에서 다시 실행했다.
- `build_final.py build-signed-preview-final`: exit0/366.49초,900초상한/12GiB Job,
  cleanup true/잔여0,최대Job committed4,982,169,600bytes. PC빌드자원이며 앱RAM이아니다.
- `verify_final.py`: 두배포판 서명/패키지/원본payload/hash/실제dex flags 통과.
  beta만 AST/closed-test=true, release는 둘다false, preview/debug=false다.
- `run_ui.py upgrade`: 최종signed29→30 보존 통과211.87초. 실제Play설치와 별도다.
- `known_ui.py`: 최종signed 모델실행·같은구간복귀·OSpicker취소·새촬영취소통과.
  원본불변/복사제거 확인. 알려진 개발사례이고 새 정확도 표본이 아니다.
- 최종4파일의 EfficientSED 모델명/weights와 dex 연구패키지 미포함도 확인했다.

배포용 beta AAB: `work/result-navigation-20261006/final/Agattok-0.5.16-beta-code30.aab`
(160,174,133bytes), SHA256 `363f565a5d90cdb8787faa3176f1e53d61d5da8ecffaf11f6f6c8881a1ba4d9e`.
최종APK SHA256 `7bde746993ae42369ff0599b405ebcbfa451b6ea4f4f8469bfd8f54257057499`.
`pre-visual-review/`의 이전후보는배포하지않는다. release는정식정책비교용이며Play제출금지다.
Lint경고10은 GradleDependency1/UsableSpace2/UseKtx7로 기존과같다. 0경고라고보고하지않는다.

## Standards

[독립 리뷰](CODE-REVIEW.md): 필수규칙위반0, 비차단P3 유지보수제안1(resultStart/resultEnd 묶음).

## Play 비공개 테스트 제출

**2026-10-06 10:36 KST**, 위 최종 beta30 AAB를 Alpha에 업로드하고 검토 제출 요청을 완료했다.
`검토 중인 변경사항`에 `0.5.16-beta (30) - 다음 장면 분석과 결과 화면 개선`이 표시된다.
Google 빠른 검사가 진행 중이며 검사 성공 후 검토로 전달된다. 관리형 게시off다.
이전code29는10월5일21:43 게시/선택테스터제공을이번에확인했다. 새code30의Google최종승인·게시·
테스터설치완료로확대하지않는다. 같은AAB를중복제출하지않는다.
지원제외기기0,신규설치표시112MB/업데이트2.54MB,차단오류0/권고경고2(가독화파일·native기호)다.
증거화면은 ignored `work/result-navigation-20261006/play-submitted.png`다. 정식release는제출하지않았다.

## Spec

독립요구사항리뷰의조치필요결함0. 스킬의48dp/8dp,따뜻한기존색상,저장주버튼과보조탐색위계를적용했다.
한손CTA검색에서적합한결과를얻지못해스킬내장모바일규칙을사용했다. 테스트MaterialTheme의보라색을
제품색상검증으로사용하지않으며,최종서명앱스크린샷을별도로확인한다.

## Residual risks

- 실제S25는미연결. 에뮬레이터속도·PSS를실폰성능/발열로일반화하지않는다.
- 새EfficientSED는연구이식수치만검증했다. 새영아누락/성인·TV오인/ARM/20초창계약검사후채택한다.
  이번제품엔진은기존YAMNet+AST이며,UX업데이트를새인식률상승으로보고하지않는다.
- 부모MainActivity의저장/앨범렌더링중Activity파괴와ClipRangeEditor preview reader까지통합한
  source lease는이번범위에서미검증이다. 프로세스사망·임의Activity복원도보장하지않는다.
  일반화면회전은기존manifest의configChanges경로이며프로세스복원검사가아니다.
- 실제IME열림·TalkBack·reduced-motion·한국부모대상사용성관찰은이번계측이대체하지않는다.

## Cleanup and rollback

이번전용 `AgattokNavigation30`만사용한다. 기존MoaQA35/사용자폰/이전삭제거부폴더는변경하지않는다.
세 QA 패키지 uninstall Success와 `emu kill` 성공을 확인했다. 원본 복사도 각 검사에서 제거했다.
**자동 승인 검토가 전용 AVD 폴더/등록파일 삭제 명령을 실행 전에 `blocked by policy`로 거부했다.**
구체적인 추가 사유는 반환하지 않았다. 다른 도구로 같은 삭제를 우회하지 않았다.
남은 수동 정리 대상은 `C:/MoaAVD/AgattokNavigation30.avd`와 `C:/MoaAVD/AgattokNavigation30.ini`다.
이전 거부 폴더와 기존 AVD는 변경하지 않았다. 이 제한은 제품 검증 실패나 Play 업로드 차단이 아니다.

**ULTRAQA BLOCKED: 임시 AVD 삭제가 자동 승인 검토에 차단됨.** 제품 검증은2번째 보완 사이클에서
통과했지만 전체 정리 완료 선언은 하지 않는다. 격리 QA state는 active=false/blocked 기록 후 clear한다.

실제 readback은 `state-after-clear.json`의 `exists:false`이고 QA 에뮬레이터 프로세스는0개다.
AVD 폴더/등록파일만 남았으며 `cleanup-result.json`에 분리해 기록했다.
state adapter의 read/clear 응답은 write의 success형식과 달라 처음 두 wrapper검증이 실패했다.
실제 inactive기록과 clear는 성공했고, 응답형식을 바로잡은 뒤 읽기전용으로 상태 부재를 확인했다.
