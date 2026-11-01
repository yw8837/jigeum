# 아가톡 공용 할인 관리

사용자의 현재 PC에서만 동작한다. `Start-Dashboard.ps1 -OpenBrowser`로 시작한다.
서버는 `127.0.0.1:8765`에만 바인딩하고 기존 gcloud 운영자 계정 인증을 사용한다.
자동 실행·예약 작업·공개 관리자 URL·추가 IAM 권한을 만들지 않는다.

- 공용 할인: 최대 100 **앱 설치 등록**. 실제 구매 수나 검증된 고유 사람 수가 아니다.
- `campaigns/launch-20261027/slots/001.json`부터 100개의 immutable object에 generation-zero 조건으로 기록한다.
- 같은 설치의 동시 요청/재시도는 같은 슬롯을 재사용한다. 다른 서버 인스턴스에도 동일하다.
- 마감은 신규 등록만 차단한다. 기존 등록자 갱신, 기존 유료 구독, 초대 이용권은 유지한다.
- 마감 직전에 이미 진행하던 요청은 완료될 수 있다. 마감 뒤 현황 새로고침으로 확인한다.
- 다시 열기는 카운터를 초기화하지 않는다. 삭제/초기화 API는 없다.
- 등록 수는 GCS 슬롯을 직접 읽어 표시한다. 오류를 0건으로 표시하지 않는다. 실제 결제 수는 미집계로 표시한다.
- 런타임은 기존 get/create 권한을 유지한다. 운영자만 control object를 generation CAS로 수정한다.
- Host/Origin 검사, CSRF, no-store, CSP, 요청 크기 제한을 적용한다. OAuth 토큰은 서버 메모리에만 있다.
- 코드 원문은 D의 private 디렉터리에만 보존한다. 앱/소스/웹/로그에 하드코딩하지 않는다.

## 배포와 이관

1. `prepare-private-codes.mjs`에 기존 private manifest와 **새** D private 디렉터리를 준다. 기존 파일을 덮어쓰지 않는다.
2. `migrate.mjs <private-manifest> inspect`로 실제 발급 코드에 속한 기존 등록만 조회한다.
3. `seed-closed`로 control을 닫힌 상태로 만들고 기존 실제 등록을 공용 슬롯에 보존한다.
4. 기존 Cloud Run에 v2 manifest를 배포하고 100% 새 revision을 확인한다. 이전 요청의 최대 30초가 지난 뒤 `seed-closed`를 다시 수행해 전환 중 기존 등록도 포함한다.
5. 실제 HTTPS의 초대 서명/잘못된 코드/마감 거절을 검사한다. QA를 위해 실제 할인 슬롯을 소비하지 않는다.
6. 대시보드에서 등록을 연다. 실제 할인 결제 확인은 별도이며, 코드 등록을 구매 확인으로 보고하지 않는다.

## 검증

저장소의 `outputs/PlayVerifier`에서 `node --test`. 실제 125개 동시 등록, 4개 검증 인스턴스,
재시작/중복/마감/재개/기존 코드 공용 한도/외부 Origin와 Host 거절/CSRF/CAS 충돌을 검사한다.
메모리 저장소 테스트는 실제 Play 결제 검증이 아니다.
