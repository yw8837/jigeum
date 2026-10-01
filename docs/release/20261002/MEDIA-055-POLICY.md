# 0.5.5 저장 미디어 정책 반영

2026-10-02: 새 로컬 MP4 보관·내려받기·직접 공유, JSON/CSV/PDF 미디어 제외, 기록 삭제 시 영상 삭제를 앱 내부 및 공개 정책에 반영했다. 기존 전용 프로젝트/서비스/계정/숫자 secret 버전/실행 한도와 공개 범위를 사용했다. 새 아기 기록 DB·미디어 업로드 API는 없다.

- Cloud Run ready revision: `jigeum-verifier-00004-qnr`
- Immutable image: `sha256:210b3d19a68df580c13e56202e72aa99750dfd476202e7ee334dbfb456f805e9`
- Build: `1bf9801d-f01a-4a4c-b90d-86306ddd25f1`, status SUCCESS.
- 배포 전 기존 자원/결제/API/계정/secret 버전/공개 상태 검사 및 현재 소스 Node56/56 통과.
- [`/privacy`](https://jigeum-verifier-th7ce7roma-du.a.run.app/privacy): HTTP200, 새 MP4 안내 확인.
- [`/terms`](https://jigeum-verifier-th7ce7roma-du.a.run.app/terms): HTTP200, 무료 MP4 안내 확인.
- [`/support`](https://jigeum-verifier-th7ce7roma-du.a.run.app/support): HTTP200,0.5.5 안내와 MP4 설명 확인.
- `/jigeum-health`: HTTP200.

HTTP 본문 확인이며 웹 화면 시각 검증의 증거는 아니다. 실제 Play 구매·심사 제출·12명14일 진행을 확인한 작업도 아니다. Play Alpha에 저장된 code18과 공개 전화번호 보완 대기는 [PLAY-STATUS](PLAY-STATUS.md)를 따른다.0.5.5/code19 AAB를 Play에 업로드했다는 뜻이 아니다.
