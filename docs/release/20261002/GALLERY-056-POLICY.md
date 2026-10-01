# 갤러리 저장 정책 반영 — 0.5.6

2026-10-02. 기존 승인된 공개 Cloud Run 서비스의 정책 HTML을 갱신했다.

- 프로젝트 `jigeum-play-20261001`, 서비스 `jigeum-verifier`, 서울 리전.
- 최신 ready revision: `jigeum-verifier-00005-ph7`.
- 기존 min0/max1/concurrency24·서비스 계정·API·공개 범위를 유지했다.
- Cloud Build `f06693e2-b6f1-4f49-b132-15c203778dc4` SUCCESS, 이미지 `gallery-056-20261002`.
- 서버 Node 테스트56통과/0실패.

HTTPS 확인: `/privacy`, `/support`, `/terms`, `/jigeum-health` 모두200. privacy에는 갤러리 직접 저장·Android8/9 쓰기 권한·앱 삭제/기록 삭제와 별도 보존·사진 앱의 선택적 클라우드 백업 안내가 있다. support는0.5.6 및 앨범 저장 안내를 포함한다.

HTTP 본문 확인이며 모든 브라우저의 화면 렌더링이나 실제 Play 구매·심사 제출의 증거는 아니다. [Play 상태](PLAY-STATUS.md)를 별도로 따른다. 원본 아기자료를 전송하거나 DB를 추가하지 않았다. 변경 로그는 로컬 `work/gallery-056/policy-deploy.log`에 보존한다.
