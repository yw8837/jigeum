# 아가톡 0.5.26-beta (40) 게시판 연결·최종 검증

**2026-10-23 후속:** Alpha40이 선택한 테스터에게 제공 중임을 확인했다(게시일10/22 21:31).
프로덕션 액세스도 승인됐으며 정식 공개판은 이용 범위 선택·최종 패키지/검증/제출이 남아 있다.
[최신 근거](../production-launch-20261023/README.md). 아래 검토대기는 제출 당시 이력이다.

## 범위와 배포 상태

- 사용자 요청: 최종 점검, 우리 아이 자랑 게시판 연결, 기존 Alpha 새 배포. 새 예약 금지.
- 공개 웹: <https://baby.yeolmaeapps.kr/>. Sites **v5**, source `bc8ee9ccfa28cc2f88c8f64057b325fd6c3d53e8`, deployment `appgdep_6ad9f54c42688191b19a75db64c880f7`, 2026-10-22 20:37 KST 성공.
- 앱: **0.5.26-beta / versionCode40**, 기존 Alpha 출시20에 정확 beta AAB 업로드. 최종 검토 제출·실제 제공 상태는 `play-review.md`를 따른다.
- 일반 방문자는 GPT 로그인 없이 닉네임·관리 비밀번호로 글/댓글 작성. Google/Kakao 로그인·마이페이지는 미구현이다.
- 공개 프로덕션 액세스 신청은 별도 검토 중이다. Alpha 배포와 공개 출시를 혼동하지 않는다.

## 반영 내용

1. Android 홈/결과의 게시판 진입, 기기에서 만든 영상카드 선택·공개 전 미리보기·명시적 공개 버튼. 개인 기록 자동 업로드 없음.
2. 페이지2 글/댓글 수정 초안 보존, 이전 응답의 최신 화면 덮어쓰기 방지, 닉네임/본문/비밀번호/미디어 SHA가 다른 재시도의 충돌409, 수정·삭제 경합 검사.
3. JPEG/MP4 구조·크기·시간 검사. 최종 앱에서 만든 MP4의 **64비트 크기 mdat**를 기존 검사기가 거부하는 문제를 실제 재현 후 수정. 64비트는 mdat에만 허용하고 안전 정수/경계/샘플 오프셋/메타데이터 제거 검사를 유지한다.
4. 앱·게시판 통합 개인정보처리방침 <https://baby.yeolmaeapps.kr/privacy.html>, Play 개인정보 URL/데이터 보안/UGC 등급 갱신. IARC 약관은 사용자 동의 후 적용.
5. 10월19일 표현 확장 포함. 기본3,584조합, 고정 출력 집계 음성30,976 / 허용 시각 문구 포함90,624. 이는 의미 종류·인식률 수치가 아니다. 모델·음성 임계값은39와 동일하다.
6. 최종 lint가 검출한 CameraX/AppCompat 경유 Fragment1.1.0 문제는 `androidx.fragment:fragment:1.8.9`로 수정. lint 억제 없음.

## 검증 증거

| 대상 | 결과/근거 |
|---|---|
| 웹 | Node34/34, TypeScript noEmit, production build 통과. `node-final-tests.txt` |
| 운영 도메인 | 합성 JPEG + Android형 extended-mdat MP4 게시/Range206/댓글/좋아요/비밀번호 수정/삭제. `hosted-v5.json`. 소유 합성 글 삭제 후 미디어404 |
| 운영자 신고 처리 | 실제 운영자 계정으로 글/댓글 숨김→복원, 신고 처리 완료, 합성 글 삭제, 신고함·미디어 삭제대기0. `moderation-*.png/json` |
| S25 ARM | 최신 모델10초 3238ms,20초 5651/5602ms. native7창 모두 finite. sampled PSS515026/521468/520695KiB, thermal0. benchmark1+취소/회복2, 실패0/skip0. `arm03/result.json` |
| S25 보호 | 기존 Play39 설치/서명 불변, 소유 releaseqa앱 제거. 사용자 데이터·기존 연구앱 보존. 폰 사용/분리 가능 안내 완료 |
| 서명 패키지 | beta/release 빌드·ARM gate·lint·서명·manifest·ELF/zipalign·payload검사 통과. `package/`, `package-release/`, `payload-*.json` |
| 동일서명39→40 | 기록 ID/LEGACY 문구와10초 MP4 SHA 보존. `upgrade/pass.json`. Play스토어 자동업데이트 설치 검사가 아니라 업로드키로 서명한 가상폰 검사 |
| signed40 20초 | 실제20초 선택/분석/장면저장 후 결과 유지. `final-ui20/result.json`. 7.7초는 가상폰 UI 관측 지연 포함값 |
| signed40 게시판 | 결과/홈에서 실제 운영도메인 진입, 준비한 카드의 영상 미리보기, 취소→결과 복귀. 공개 게시 버튼은 누르지 않음. 실제 아기 자료 외부전송 없음 |
| signed40 내보내기/재잠금 | `board-relock/` 최종 결과 참조. 테스트 beta와 정책 비교용 release를 구분 |

## 실패 기록과 수정 범위

- ARM01은 시험용 새 package 허용목록 누락, ARM02는 USB 연결 끊김. ARM03에서 통과. 전용 proof mode는 정확 패키지/revision/pin/7일 기한/정리/설치불변 조건을 유지한다.
- signed 업데이트 시험의 저장 후 이동/화면 밖 저장영수증 가정이 현재 UX와 달라 실패. 실제 파일 SHA 검사는 유지하고 화면 이동·제한된 스크롤만 수정. 실패 근거는 `upgrade/`에 보존.
- 최종20초 시험 첫 실행은10초 fixture 선택 오류. 20.064초 로컬 CC0 fixture로 재실행해 통과.
- 최초 게시판 첨부 실패는 앱 생성 MP4의 extended mdat 미지원. 실제 카드1,475,046bytes/20.064초/AAC+H264에서 수정 전 거부, 수정 후 통과. 합성 자료로 운영 서버까지 재검증.
- PDF 시스템 공유창을 닫지 않고 정책 비교앱 설치 뒤 홈을 찾던 시험은 실패했다. 공유창을 닫은 후 중단 지점부터 재개하고 원래 실패 기록을 보존한다.
- 운영자 로그인은 기본 Sites 도메인에서 검증. 사용자 도메인의 운영자 로그인 callback404는 플랫폼 경로 제한으로 남아 있다. 일반 방문자 흐름은 영향을 받지 않는다.
- 운영자 테스트용 합성 글 최초 요청500 뒤 재시도 성공 이력이1건 있으며 원인은 미확정이다.
- 게시용 체크아웃에서 npm shim/의존성 경로 문제로 helper build가 실패했다. 정상 빌드한 canonical 소스와 게시용 추적128파일의 SHA 일치를 확인하고 같은 dist를 사용했다. 이전 generated dist 정리 시도는 자동 승인 정책이 차단해, 삭제 없이 최신 빌드로 복사했다. 소스·원격 SHA와 배포 아카이브는 일치한다.

## 산출물과 한계

문서에 복사한 텍스트 로그는 줄바꿈/줄끝 공백을 정규화했다. 원 실행 로그는 work 폴더에 보존하며 판정/수치/패키지 SHA는 변경하지 않았다.

최종 배포물은 `work/community-release-20261022/final/`에 보존한다.

- beta AAB `aa12d9cd8699815b0ccf518c76f3d27012de8aae249548cbd5fc70b21986aa61`, 160,293,923bytes.
- beta APK `3a7d37f31d40f58a31dcd5463351b43536d70c07bc8177a107dc8f6c29ceb809`.
- 정책 비교용 release AAB `6df3ccba410f59b061a99cdbe3acc7fad8042ab6f43b7a30d428a56edf6cfe05`, **Play 업로드 금지**.
- 정책 비교용 release APK `b6193ed5a166ae251add91d5784adf71eaf50193eab61565ff45fe35de20fad9`.
- 이전39 원본 AAB `b77b5d3cf31164031ed3a90a9a73b66e90fbd80954f482925e8faec8ee360fb0` 보존.

S25 수치는 제한된 고정 입력 모델 실행 측정이다. 일반 인식률·아기 의도/감정/욕구 정확도, 모든 기기 발열, signed40 실폰 UI 전체속도를 입증하지 않는다. 소유 가상폰·합성 글 정리는 `cleanup.json`을 따르며 이전 정책 차단 삭제를 우회하지 않는다. 자동화33 PAUSED와 새 예약 금지를 유지한다.
