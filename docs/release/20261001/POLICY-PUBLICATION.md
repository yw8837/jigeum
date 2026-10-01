# 공개 정책 게시·Console 저장 — 2026-10-01

최신 연결 요청 후 전용 Cloud Run에 정책·약관·지원 페이지를 실제 게시했다. 앞선 비용 미연결·미게시 기록은 당시 준비 상태이며 현재는 아래 결과를 따른다.

| 항목 | 실제 주소/상태 |
|---|---|
| 개인정보처리방침 | https://jigeum-verifier-th7ce7roma-du.a.run.app/privacy |
| 이용약관 | https://jigeum-verifier-th7ce7roma-du.a.run.app/terms |
| 지원 | https://jigeum-verifier-th7ce7roma-du.a.run.app/support |
| 시행일 | 2026년 10월 1일 |
| 공개 정보 | 최용우 / bsfire1237@gmail.com |
| Console | 개인정보처리방침 URL 저장 성공. 후속 심사 안내·성인 연령도 저장해 대시보드 9/11. 심사 제출은 별도 |

실제 HTTPS smoke에서 세 HTML·보안 헤더·허용 경로를 확인했고 브라우저 렌더도 확인했다. [실행 결과](https-smoke.json). 앱 내 개인정보·약관도 최종 connected 빌드에 같은 시행일과 서버 설명을 반영했다. [앱 연결 QA](../../qa/0.4.5/connected/README.md).

아기 기록 DB·로그인·자동 원본 업로드를 추가하지 않았다. 구매/심사 확인은 POST 본문으로 구매 토큰·임의 설치 식별자·심사 코드를 보내며 원음·영상·얼굴·아기 기록은 보내지 않는다. 일시 상태는 메모리에만 있고 본문·토큰·UUID·코드를 DB/파일/요청 본문 로그에 쓰지 않는다.

실행은 서울, Google Cloud Logging의 기본 접속 메타데이터는 30일 보관이다. IP·요청 경로·상태·처리 시간, Google LLC(미국)·글로벌 로그/국외 처리와 문의 경로를 고지했다. ‘서버 로그가 전혀 없다’고 선언하지 않는다.

심사 credential은 저장소 밖 비밀보관 경로와 승인한 Play Console 전달 경로만 사용한다. 실제 코드·개인키·계좌·신원 자료·등록 캡처는 Git/QA 패키지에서 제외한다. [등록 상태](../../PLAY-REGISTRATION.md)와 [연결 진행](CONNECTION-PROGRESS.md)을 따른다.
