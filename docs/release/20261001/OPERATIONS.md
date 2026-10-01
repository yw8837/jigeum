# 결제 검증 서비스 운영 — 2026-10-01

## 실제 구성

전용 프로젝트 `jigeum-play-20261001` / 번호 `795224750373` / ACTIVE / billingEnabled=true.

- Cloud Run: 서울 `asia-northeast3`, `jigeum-verifier-00002-hvb`, traffic 100%.
- 공개 주소: https://jigeum-verifier-th7ce7roma-du.a.run.app
- 상태 확인 `/jigeum-health`. 로컬 호환 `/healthz`의 외부 동작은 첫 실패 기록을 따른다.
- 요청 기반 과금, min 0 / max 1, concurrency 24, 1CPU / 512MiB, timeout 30초.
- image digest: `sha256:8b11b64a20b4eba7231991a3ef4a34fd845c384b2c869f7488e21cb8de10e95e`.
- runtime `jigeum-play-runtime@jigeum-play-20261001.iam.gserviceaccount.com`; build `jigeum-play-build@jigeum-play-20261001.iam.gserviceaccount.com`.
- service account JSON 키 미생성. metadata → IAM Credentials `androidpublisher` scope 토큰을 사용한다. runtime의 자체 SA 리소스 `getAccessToken` 한 권한과 secret 2개 accessor를 확인했다.
- build는 전용 source bucket 읽기·image repository 쓰기·build 로그 쓰기 범위다. source bucket 서울/UBLA/public 방지/Delete age7/soft-delete0, signing/hash secret 서울 version1이다.
- Play 권한은 **이 앱 하나만** 읽기·구매 조회·주문/구독 관리다. 계정 전체나 다른 앱의 권한을 추가하지 않았다.
- 자동 기본 compute Editor·legacy builder 및 임시 billing 연결/Service Usage 사용자 역할을 제거하고 inventory를 재조회했다.
- 접속 로그 30일. 구매 본문·토큰·UUID·심사 코드는 로그/DB/파일에 쓰지 않는다. 원본 아기 데이터는 서버로 보내지 않는다.
- 이 프로젝트만 월 **10,000 KRW** actual 50/80/100%와 forecast 100% 알림. **알림과 최대 인스턴스 수는 비용 상한이 아니다.**

실제 [IAM 19/19](cloud-iam-audit.json), [Run 11/11](run-service-verdict.json), [bootstrap](bootstrap-live.txt)을 확인했다. 기본 SDK 계정은 유지하고 명령마다 studio 계정을 지정했다.

## 요청 제한과 장애 처리

설치 UUID별 30/min, 인스턴스 전체 300/min, HTTP 진행 24개. 프록시 뒤 사용자를 하나의 소켓 IP 30/min으로 묶지 않는다. `X-Forwarded-For`를 신뢰하지 않는다. body 3초 / 각 upstream 5초 / 전체 검증 23초 / Run 30초로 제한한다.

metadata/scoped 토큰 재발급과 같은 구매 토큰의 진행 작업을 공유한다. 완료된 구매 결과를 서버에 저장하지 않아 다음 검증은 Play에서 현재 상태를 읽는다. 인정할 구매는 acknowledge가 완료되거나 ACKNOWLEDGED일 때만 서명하며 0.4.2 ACK 계약을 유지한다.

앱은 새 유료 실행 직전 현재 서명 권한을 확인한다. 명시적 거절 시 재잠금, 일시적 장애에서는 남아 있는 최대 24시간의 유효 캐시만 허용한다. 재부팅 후 온라인 재확인이 필요하고 이미 생성된 문장·무료 기록·내보낸 파일은 보존한다.

UUID는 인증된 사용자 ID가 아니며 회전 공격을 막지 못한다. rate 상태는 메모리에 있고 재시작/여러 revision 간에 공유되지 않는다. 정책/health 요청도 비용을 유발할 수 있다. 별도 edge/LB/DDoS 방어 비용은 추가하지 않았다. 대규모 공격·Google quota·정확한 지출 차단을 검증했다고 하지 않는다.

## 실제 시험과 남은 gate

| 검증 | 실제 결과 |
|---|---|
| Node | 56/56, Google 응답 대역 |
| 공개 HTTPS | 9/9, HTML/서명·잘못된 본문/코드/토큰 상태 |
| 몰림·격리 | warm 잘못된 코드 48요청, 4/4. 설치 16개 동시, 한 설치 30회/31번째429, 다른 설치·health 응답 |
| 지연 | 오류 코드 요청 median 30ms / p95 374ms / max 422ms. 구매 지연·수용량 아님 |
| 실제 release 앱 | 서버 서명 심사 이용권 수락, 카메라·영상 화면·PDF·해제·무료 보존 포함 10/10 |
| 미완료 | 상품/실제 구매·ACK/보류/복원/환불·계정 변경, 콜드 시작·구매 부하·장시간 운영·RTDN 복구 필요성 평가 |

근거: [HTTPS](https-smoke.json), [제한 시험](live-load-smoke.json), [연결 QA](../../qa/0.4.5/connected/README.md). health 200은 Google 구매 검증 정상의 증거가 아니다.

## 후속 실행

1. [등록 문서](../../PLAY-REGISTRATION.md)의 심사 안내·Console 선언·상품·내부 테스트를 실제 저장/업로드 상태로 확인한다.
2. Play 설치로 라이선스 구매·보류·취소·복원·환불·ACK·계정 변경을 검증한다. 토큰·테스터 개인정보를 캡처하지 않는다.
3. 실제 콜드 시작/구매 부하에서 latency·429/503·upstream timeout·quota·비용을 관측한다. 오류 코드 수치를 재사용하지 않는다.
4. ARM/OEM/16KB·TalkBack·보호자 사용성과 12명·14일 테스트 후 사용자 출시 지시에 맞춰 제출한다.

재배포는 [tools/deploy](../../../tools/deploy/README.md)의 read-only preflight와 숫자 secret 버전·고정 image digest를 따른다. 현재 기본 런타임/권한/비밀값을 임의 재생성하지 않는다. 비용·권한·공개 범위 변경은 구체적으로 확인하며 다른 앱 프로젝트를 재사용하지 않는다.
