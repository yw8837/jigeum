# 지금 내 기분이 그래 — Play 구독 확인 서비스

사용자의 최신 연결 요청 후 전용 서울 Cloud Run에 실제 배포했습니다. 구성·공개 정책·최소 IAM·Play 앱 한정 runtime 접근은 [운영 문서](../../docs/release/20261001/OPERATIONS.md)를 따릅니다. Node56/56·실제HTTPS9/9·warm 오류 코드 제한4/4와 실제 release 앱의 심사 이용권 수락을 확인했습니다. **실제 Google Play 구매/ACK/환불은 미검증**이며 [최종 연결 QA](../../docs/qa/0.4.5/connected/README.md)를 먼저 읽습니다. 도구의 기본 dry-run은 외부 변경을 하지 않습니다.
Google Play 상품, API 권한, 운영 HTTPS 주소가 없는 빌드는 유료 구매를 열지 않습니다.
무료 소리 관찰·아기말·기록·기본 공유·백업 기능은 그대로 사용할 수 있습니다. 0.4의 카메라·저장 영상·PDF는 하나의 전체 이용권으로 제공합니다.

## 데이터와 구매 흐름

1. Android가 Google Play Billing 9.1.0에서 `jigeum_album` 구매 내역을 조회합니다.
2. 구매 완료 상태만 `/verify`로 전송합니다. 전송 항목은 패키지명, 상품 ID, 구매 토큰, 기기 설치 UUID입니다.
3. 서버가 Android Publisher `purchases.subscriptionsv2.get`을 호출해 상품·플랜·유효기간을 확인합니다.
4. 허용 가능한 구매의 acknowledgementState를 확인합니다. ACK PENDING이면 서버에서 `purchases.subscriptions.acknowledge`를 호출하고 성공 후에만 이용권을 서명합니다. ACKNOWLEDGED이면 추가 쓰기를 하지 않습니다. 누락·알 수 없는 상태·acknowledge 오류는 HTTP503이며 다음 검증에서 Play를 새로 조회합니다.
5. EC P-256 서명과 최대24시간 이용권, acknowledge된 구매 토큰의 SHA-256 해시를 반환합니다. Android가 서명·앱·설치 UUID·만료와 로컬 실제 경과시간을 확인한 뒤 새 카메라·영상·PDF 작업을 엽니다. 서명 수락 후 정확한 토큰 해시가 일치하면 중복 client acknowledge를 생략합니다. 이전 서버 응답에 해시가 없거나 다르면 기존 Android acknowledge 재시도를 유지합니다. 앨범 미리보기는 무료입니다.

아기 소리·영상·표정·문장·기록은 이 서버에 보내지 않습니다. 사용자 계정이나 외부 아기 기록 DB는 없습니다.
원본 구매 토큰은 서버의 진행 중 요청 메모리에만 있으며 로그·파일·DB에 저장하지 않습니다. SHA-256 확인값은 서명 이용권에 포함돼 앱의 기존 private 권한 캐시에 저장됩니다. 원본 토큰이나 아기 기록을 해시에서 복원하는 기능은 없습니다.
설치 UUID는 앱 내부에만 저장됩니다. 앱 재설치 후에는 Google Play 계정 구매 조회를 통해 새 설치에 복원합니다.

## 구독 판정

| 상태 | 처리 |
|---|---|
| ACTIVE / IN_GRACE_PERIOD | 현재 기간이 남아 있으면 허용 |
| CANCELED | 자동갱신 취소 후 남은 이용기간만 허용 |
| PENDING / ON_HOLD / PAUSED / EXPIRED | 유료 기능 잠금 |
| Play 400·404·410 또는 유효 이용권 없음 | HTTP 403, 앱의 이전 권한도 제거 |
| Google 인증·권한 오류, 429, 장애, 시간초과 | HTTP 503, 기존에 서명 검증한 권한의 남은 기간만 허용 |

플랜은 `monthly`(매월 자동갱신)와 `three-month-prepaid`(3개월 선불)입니다.
앱은 Play가 반환한 실제 가격을 표시하며 월간 P1M 자동갱신/선불 P3M 단건 조건이 다른 상품을 숨깁니다.
유료 이용권을 위조하는 테스트 우회 스위치는 없습니다.

## 로컬 검증

Node.js 22 LTS 이상을 권장합니다. 외부 npm 의존성은 없습니다.

```powershell
npm test
```

테스트는 임시 루프백 HTTP 포트와 메모리 키를 사용하고 종료 시 소켓을 닫습니다.
Google 응답은 대역입니다. **실제 Play 구매·취소·환불을 검증했다는 뜻이 아닙니다.**

## 향후 운영 설정

출시/배포를 재개할 때 다음을 설정해야 합니다. 이번 작업에서는 운영 키를 생성하거나 업로드하지 않았습니다.

| 서버 환경 변수 | 용도 |
|---|---|
| `PLAY_CREDENTIALS_MODE` | 로컬 기존 `service-account` 또는 Cloud Run용 `workload`. workload에서는 서비스 계정 JSON을 거부 |
| `PLAY_RUNTIME_SERVICE_ACCOUNT_EMAIL` | Cloud Run 실행 서비스 계정. metadata ADC→IAMCredentials의 androidpublisher scoped token, 원문키 내보내기 없음 |
| `PLAY_SERVICE_ACCOUNT_JSON_PATH` | 이전 로컬 service-account mode 전용. Android Publisher 조회·acknowledge 권한을 별도 검증. Cloud Run 배포에 사용하지 않음 |
| `PLAY_ENTITLEMENT_PRIVATE_KEY_PATH` | P-256 PKCS#8 PEM 서명 개인키, 저장소 밖 비밀 파일 |
| `PLAY_REVIEW_CODE_SHA256` / `PLAY_REVIEW_ACCESS_ID` | 별도 심사자 코드의 SHA256 및 credential ID. 둘 다 없으면 /review403, 부분·잘못된 설정이면 시작 실패 |
| `HOST` | 기본 `127.0.0.1`. 직접 공개하지 말고 통제된 HTTPS ingress 사용 |
| `PORT` | 기본 8080 |
| `RATE_LIMIT_MODE` | 기존 로컬 `socket`, 배포 `installation`. 임의 forwarding header를 신뢰하지 않음 |
| `ORIGIN_REQUESTS_PER_MINUTE` | 선택한 소켓 또는 salted 설치 UUID별1분 한도. 기본30 |
| `GLOBAL_REQUESTS_PER_MINUTE` | 프로세스 전체한도. 기본1000, 배포계획300 |
| `MAX_CONCURRENT_VERIFICATIONS` / `MAX_RATE_KEYS` | HTTP진행/한도테이블 최대. 배포24/10000 |
| `BODY_TIMEOUT_MS` / `VERIFICATION_TIMEOUT_MS` / `UPSTREAM_TIMEOUT_MS` | 기본5000/16000/7000ms, workload배포3000/23000/5000ms |

앱 빌드의 공개 설정은 `JIGEUM_PLAY_VERIFY_URL=https://…/verify`,
`JIGEUM_PLAY_ENTITLEMENT_PUBLIC_KEY=…`(SPKI DER을 Base64로 인코딩한 공개키)입니다.
서비스 계정 JSON이나 개인키를 앱·Git·소스 ZIP에 넣으면 안 됩니다.

## 몰림·장애 대응과 한계

- 본문 최대16KiB, 수신 대기5초, HTTP 검증 대기16초, Google 호출마다7초 상한입니다. 최초 OAuth+구매 조회+acknowledge의 공유 작업은 최대3호출입니다. HTTP deadline이 먼저 지나면 해당 요청에 이용권을 반환하지 않으며, 공유 작업은 각 호출의 제한 시간 안에 끝나고 다음 요청이 최신 상태를 다시 조회합니다.
- 진행 중 HTTP 요청 최대 32개, 전체 연결 256개입니다. 초과분은 빠르게 503과 Retry-After로 응답합니다.
- OAuth 재발급은 동시에 하나만 합니다. 같은 구매 토큰의 진행 중 Play 조회와 acknowledge를 함께 합치며 공유 작업은32개를 넘지 않습니다. 완료 결과는 캐시하지 않고 다음 조회는 환불/만료/acknowledge 상태를 다시 확인합니다. 설치마다 이용권은 따로 서명합니다.
- 원본 IP 테이블도 10,000개로 제한합니다. `X-Forwarded-For`는 신뢰하지 않습니다.
- HTTPS 프록시 뒤에서는 모든 요청이 같은 프록시 주소로 보일 수 있습니다. Cloud Run 계획은 `RATE_LIMIT_MODE=installation`으로 설치 UUID별30/min·프로세스전체300/min·동시24개를 사용하므로 공유 proxy주소 때문에 사용자전체가30/min에묶이지 않습니다. UUID회전공격·전역한도독점방어를완료했다는뜻은아닙니다. 강한client별ingress보호는추가운영gate이며 소켓기본값30을Cloud Run에그대로사용하지않습니다.
- 다중 인스턴스의 공통 제한, 전역 Google API 할당량, 외부 DDoS 차단, 용량 자동확장, 가용성 모니터링은 운영 인프라에서 설정해야 합니다. 로컬 테스트로 운영 수용 인원을 주장하지 않습니다.
- 이용권은 최대 24시간 오프라인 허용입니다. 앱 실행 중 서명 만료 시 잠기고, 온라인에서 명시적 권한 거절을 받으면 즉시 잠깁니다. 환불 직후 오프라인 기기의 권한은 이 잔여기간만큼 남을 수 있습니다.
- 앱은 수신 당시의 단조시간과 부팅 횟수를 저장해 기기 시간 되돌림으로 오프라인 기한이 늘어나지 않게 합니다. 재부팅·누락되거나 손상된 검증 근거는 온라인 재확인이 필요합니다. 루팅한 기기나 변조 앱까지 보호한다는 뜻은 아닙니다.
- 서버 무상태 설계는 구매 토큰을 가진 요청자의 실제 기기 소유권까지 증명하지 않습니다. 토큰 탈취 방지와 루팅/클라이언트 변조 방어를 강화하려면 출시 단계에서 Play Integrity와 검증 정책을 추가로 검토해야 합니다.
- 로컬 health는 프로세스 응답만 확인합니다. 외부 Google 장애까지 정상이라는 뜻이 아닙니다.
- 서버가 구매를 처음 수신하기 전에 앱이 완전히 종료되고 다시 실행되지 않으면 이 경로는 동작하지 않습니다. RTDN·누락 구매 복구·프로세스 장애 뒤 재처리와 실제 acknowledge 기한은 운영 검증 항목입니다. DB나 원본 아기 업로드를 추가하지 않았습니다.

## 0.4.2 acknowledge 계약

서명 payload의 선택 필드 `acknowledgedTokenHash`는 서버가 해당 구매를 acknowledge 완료하거나 Play의 ACKNOWLEDGED 상태를 읽은 후에만 포함하는 lowercase SHA-256 확인값입니다. 앱은 `accept()` 성공 후 현재 purchaseToken의 UTF-8 SHA-256과 비교합니다. 서명 실패·다른 토큰·해시 누락은 client acknowledge 생략 근거가 아닙니다.

공식 API: [acknowledge](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptions/acknowledge), [SubscriptionPurchaseV2](https://developers.google.com/android-publisher/api-ref/rest/v3/purchases.subscriptionsv2). 현재 단일 SKU이므로 경로에 `jigeum_album`을 지정하고 body는 `{}`입니다. 계정 UUID·사용자 기록·developerPayload는 붙이지 않습니다.

`node tools/create-ack-fixture.mjs`는 Android androidTest에 사용할 공개 합성 fixture를 재생성합니다. 임시 개인키는 메모리에서만 생성·폐기하며 운영 키/거래를 사용하지 않습니다. 새 서버와0.4.2앱의 계약을 먼저 시험한 뒤 함께 운영에 적용해야 합니다. 구버전 앱은 이미 서버에서 끝난 acknowledge를 중복 시도해 지연 안내를 표시할 수 있습니다.

## 실제 Play에서 남은 검증

라이선스 테스트 계정과 실제 Android 기기로 구매 성공, 결제 취소, 보류→완료/취소, 3개월 선불,
구매 acknowledge 재시도, 앱 삭제 후 복원, 계정 변경, 취소 후 만료, 환불/회수, 24시간 오프라인 경계를 확인해야 합니다.
현재 로컬 테스트 결과와 이 외부 미검증 항목을 구분해 QA 보고서에 기록합니다.

## 배포 패키지와 심사 계약 후보 — 2026-10-01

배포 계획·비용 가정·정확한 IAM 범위와 순서는 `tools/deploy/README.md` / `plan.json`을 따릅니다. 이 패키지는 Cloud Run 단일 공급자와 최소0·최대1·서울·request billing을 사용합니다. Docker와Cloud Build업로드allowlist는source/public/package만포함하고테스트키·fixtures·env·로컬JSON은제외합니다. Docker daemon이현재실행되지않아컨테이너실행은아직확인하지않았습니다.

`POST /review`의정확한3필드 `{packageName, installationId, reviewCode}`는고엔트로피코드SHA256과일치할때만최대24시간·설치bound·별도`grantSource=play-review`/`basePlanId=play-review`이용권을발급합니다. 이는구매SKU플랜·acknowledge와별도계약입니다. 서버에는코드원문을환경변수나로그로저장하지않습니다. 심사자코드의실제생성·운영주입·Play설명제출은미완료입니다. 정책GET/HEAD정적페이지는서버factory의`publicRouteHandler`로연결되며현재파일은게시승인전후보입니다.

로컬시험에는기존구매43개와workload·심사·설치/global제한·정책계약시험이포함됩니다. 테스트의Google메타데이터/Play응답은합성대역이며실제IAM·Google Play거래·Cloud부하능력검증을대체하지않습니다.
