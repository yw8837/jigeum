# 결제 확인 서버 배포 준비

이 디렉터리는 **실행 전 계획·검증 도구**다. 준비 파일 생성과 로컬 시험은 Cloud 배포·Play 권한 설정 완료를 의미하지 않는다. `deploy-cloud-run.ps1`의 기본 모드는 dry-run이며 외부 변경을 하지 않는다.

**실제 2026-10-01 진행:** 사용자 승인 후 bootstrap·서울 Cloud Run 배포·Play 앱 한정 권한·정책 게시를 완료했다. [실제 운영](../../docs/release/20261001/OPERATIONS.md), [IAM19/19](../../docs/release/20261001/cloud-iam-audit.json), [Run11/11](../../docs/release/20261001/run-service-verdict.json), [HTTPS9/9](../../docs/release/20261001/https-smoke.json)을 따른다. 문서의 실행 예시와 fake 시험은 그 실제 결과와 별개다. 실제 구매·콜드 시작·수용량·장시간 운영은 미검증이다.

## 구성과 비용

`plan.json`은 새 전용 `jigeum-play-20261001`만 대상으로 한다. 서울 `asia-northeast3`, 요청 기반 과금, 최소0·최대1 인스턴스, 1vCPU·512MiB·동시24개·요청30초다. 공개 HTTPS 서비스 하나에서 `/verify`, `/review`, 정책 페이지를 제공하며 외부 사용자 DB나 미디어 업로드가 없다.

예시로 하루1,000건·건당 청구2초·30일이면 compute+request의 무료할당 전 추정은 **US$1.527/월**이다. 해당 월의 계정별 무료할당이 남으면 이 부분은0일 수 있다. 이는 서울 가격 확인 전 가정이며 Secret Manager·빌드·이미지 저장·로그·네트워크·세금은 별도다. 한 인스턴스가 한 달 내내 청구되고 분당300건이면 같은 가정으로 compute+request 약US$70.632다. 콜드스타트와 배포 중 인스턴스 겹침은 추가될 수 있다. 최대1과 예산 알림은 결제 차단 기능이 아니다. [공식 가격](https://cloud.google.com/run/pricing)을 실제 연결 직전에 확인한다.

## 키를 내보내지 않는 인증

1. Cloud Run의 전용 실행 서비스 계정으로 metadata ADC 토큰을 받는다. metadata의 고정 내부 URL만 사용하며 Google 응답 헤더와 응답 크기를 검사한다.
2. IAM Credentials `generateAccessToken`으로 `androidpublisher` 범위의1시간 토큰을 받는다. metadata의 일반 `cloud-platform` 토큰이 Publisher에도 동작한다고 가정하지 않는다.
3. 이 계정 자체에 `iam.serviceAccounts.getAccessToken` 한 권한만 갖는 custom role을 **서비스 계정 리소스**에 부여한다. 프로젝트 전체 Token Creator·Owner·Editor가 필요하다는 뜻이 아니다.
4. 같은 실행 계정을 Play Console의 `com.moa.babylanguage` 앱에만 초대하고 주문·구독 조회/관리 권한을 부여한다. Cloud IAM만으로 Play 권한이 생기지 않는다. 실제 조회와 acknowledge 시험이 남는다.
5. EC P-256 서명 개인키는 저장소 밖에서 생성·보존하고 지정 Secret Manager 버전만 파일로 마운트한다. 원문 서비스 계정 JSON이나 서비스 계정 키 생성은 하지 않는다.

metadata와 scoped OAuth 토큰은 프로세스 메모리에만 있고1회 재발급을 공유한다. 구매 토큰과 UUID는 요청 메모리에만 있다. UUID별 제한은 프로세스마다 무작위salt로 HMAC한 키를 최대10,000개/분 보관한다. 개인키·토큰·코드·UUID·본문을 로그·파일·DB에 저장하지 않는다.

## 실행 순서와 권한

실행 순서는 `plan.json.executionOrder`를 따른다. API6개/Artifact Registry/런타임 계정/Secret Manager/최소 IAM/Play 권한/전용 build identity가 먼저 필요하다. 준비 스크립트가 이들을 몰래 생성하지 않는다. signing secret과 review hash secret 각각에만 runtime `secretAccessor`를 부여한다. build identity는 소스 버킷 read·대상 image repository write·build log write만 부여하고 기본 compute 계정에 편집자 권한을 재사용하지 않는다. 배포 계정의 runtime `actAs` 역시 필요하다.

### 운영 준비 bootstrap

사용자의 최신 비용 연결 승인 후에 `bootstrap-cloud.ps1`을 준비했다. 기본은 dry-run이다. 실행 모드는 대상 project의 기존 결제 연결을 읽어 확인하고, 자원·최소 권한·예산 알림을 구성한다. 결제 연결 자체·Cloud Run 배포·Play 권한 초대·공개 출시를 수행하는 스크립트는 아니다.

```powershell
# private 파일을 열거나 실제 Cloud를 변경하지 않는다.
./tools/deploy/bootstrap-cloud.ps1

# 운영 담당자가 승인·통화·private 파일 경로를 확인한 뒤 실행한다.
$jigeumPrivateDir = 'C:/Users/최용우/.jigeum/verifier-20261001'
./tools/deploy/bootstrap-cloud.ps1 `
  -GcloudPath 'C:/Users/최용우/AppData/Local/Google/Cloud SDK/google-cloud-sdk/bin/gcloud.ps1' `
  -SigningKeyPath (Join-Path $jigeumPrivateDir 'entitlement-signing.pem') `
  -ReviewHashPath (Join-Path $jigeumPrivateDir 'review-code-sha256.txt') `
  -BillingAccountIdFile (Join-Path $jigeumPrivateDir 'billing-account.id') `
  -BudgetAccount 'bsfire1237@gmail.com' `
  -Execute -OperationalPreparationConfirmed -BillingCurrencyKRWConfirmed
```

세 private 입력은 저장소 밖의 기존 파일이어야 한다. script는 운영 서명키나 서비스 계정 키 JSON을 생성하지 않고, P-256인지 검증한 뒤 signing/hash 파일을 Secret Manager 정확 버전1에 전달한다. review hash 파일은 줄바꿈 없는 lowercase hex64다. account ID·key·hash 원문이나 원문 CLI 응답을 출력하지 않으며 HTTP 본문 디버그 로그도 명시적으로 끈다.

project 준비는 `studioyeolmae.com@gmail.com`, 예산 작업만 기존 billing 관리자인 `bsfire1237@gmail.com`을 명시한다. budget 명령은 이 프로젝트를 `--billing-project`로 지정하며 그 계정의 `serviceusage.services.use`가 필요하다. 실제 최초 실행에서는 `serviceUsageConsumer`만 임시 부여하고 finally에서 제거했다. 재실행 시 같은 최소 범위와 제거를 확인하며 Owner·Editor를 부여하지 않는다. 월1만원은 billing 계정 통화가 KRW임을 직접 확인한 뒤 적용한다. `projects/795224750373` 하나만 필터하고 50/80/100% actual 및100% forecast 알림을 기본 billing 관리자/사용자에게 유지한다. 알림은 지출 차단·정확히1만원에서 정지하는 기능이 아니다.

core6개 API 외에 준비용 IAM·Storage·Logging·Billing Budgets API4개도 활성화 목록으로 명시한다. 전용 source bucket은 서울/UBLA/public 방지/정확한 Delete age7 lifecycle/soft-delete0로 생성하고 기존 bucket도 실제 raw 속성으로 검증한다. 다른 설정이면 임의 변경하지 않고 중단한다. 배포 도구는 `--gcs-source-staging-dir=gs://jigeum-play-20261001-build-source/source`를 항상 지정한다. 기본 Cloud Build bucket을 사용하는데 권한은 다른 bucket에 주는 불일치를 피한다.

재실행 시 inventory를 읽어 기존 동일 자원을 재사용하고, 저장된 secret 버전1을 자동 교체하거나 새 버전을 누적하지 않는다. 기존 secret을 그대로 사용하는 경우 운영자가 입력 파일과 저장된 버전의 일치 여부를 확인한 뒤 `-ReuseExistingSecretVersionsConfirmed`를 별도로 지정해야 한다. 기존 role에 다른 권한이 있거나 budget의 금액/범위/알림이 다르면 중단한다.

중간 단계나 budget에서 실패해도 앞에서 만든 API·계정·저장소·secret·IAM은 남는다. 자동 rollback으로 키나 자원을 지우지 않는다. 출력된 `PASS:` 단계는 해당 단계의 실제 재조회 결과이며 전체 완료 표시는 마지막 `bootstrap-verified`에서만 나온다. 실패 원인을 해결한 뒤 같은 파일로 재실행하고, secret 재사용 확인이 필요한 경우 위 조건을 따른다. 프로젝트 IAM과 상위 리소스에서 상속된 더 넓은 권한이 없는지는 운영 담당자가 실제 inventory로 별도 확인해야 한다. script가 특정 리소스 binding을 확인했다고 전체 권한의 최소성까지 증명한 것은 아니다.

```powershell
# 운영 파일을 읽지 않고 ephemeral 합성 파일과 fake gcloud만 사용하는 시험
node ./tools/deploy/test-bootstrap.mjs
# PowerShell5.1/7 모두14사례: 28/28. 외부 Cloud 작업0.
```

```powershell
# 외부 변경 없이 실제 대상·환경·gate를 출력
./tools/deploy/deploy-cloud-run.ps1 -GcloudPath 'C:/Users/최용우/AppData/Local/Google/Cloud SDK/google-cloud-sdk/bin/gcloud.ps1'

# 실제 수행은 별도 운영 승인 후. build identity는 실제 생성/승인한 주소를 사용한다.
# -Execute -OperationalGatesConfirmed: preflight 성공 후 image build/push와 Cloud Run배포.
# -PublishPublicService: 별도로 public invoker변경.
# -ReuseExistingPublicService: 실제 확인한 기존 공개service를 공개 상태로 재사용하는 명시적 선택.
```

배포는 image digest로 고정하며 Secret Manager 숫자 버전만 허용한다. `latest` 버전·mutable image tag만으로 실행하지 않는다. `.gcloudignore`/`.dockerignore`가 모두 source/public/package/Dockerfile만 허용하고 fixture·env·로컬키를 업로드하지 않는다. 기존 Android 산출물을 덮어쓰지 않고 반환한 HTTPS `/verify` URL과 대응 공개키를 새 빌드에 연결해야 한다.

### 실행 전 확인

dry-run은 확인할 gate를 출력할 뿐 실제 상태 확인 완료가 아니다. `-Execute`는 `-OperationalGatesConfirmed`를 함께 지정해야 한다. 이 선택은 운영자가 수동으로 IAM의 최소 범위·Play 앱 권한·공개 정책·30일 요청 로그 보존·예산 알림·이미지/Android QA를 확인했다는 표시이며 스크립트가 이 수동 항목까지 자동 검증했다는 뜻은 아니다.

이미지를 push하기 전에 스크립트는 선택한 project의 billingEnabled, API6개 활성 상태, 지정한 양의 숫자 secret 버전2개의 ENABLED 상태, DOCKER 저장소 존재, runtime/build 계정의 실제 존재·활성을 읽는다. 이어서 실제 Cloud Run service와 invoker IAM을 읽는다. 기존 service가 allUsers invoker 또는 invoker IAM검사 비활성으로 공개돼 있으면 `-PublishPublicService` 또는 `-ReuseExistingPublicService`가 없을 때 중단한다. 기존 공개 상태를 몰래 재사용하거나 임의로 회수하지 않는다. 정확한 대상 service의 not-found 진단만 신규 private 생성 경로로 허용하며 권한/네트워크/API 오류는 중단한다.

이 확인과 현재 source의 `node --test`가 모두 통과해야 build/push를 시작한다. build 실패는 digest조회·deploy를 막고, 잘못된 digest는 deploy를 막으며, deploy실패는 최종URL조회를 막는다. 기존 상태는 build 중 바뀔 수 있으므로 이검사는 인프라 변경을 원자적으로 잠그는 보장이 아니다.

```powershell
# fake gcloud만 사용하는 로컬 fail-closed harness. 실제 Cloud 변경/비용 없음.
./tools/deploy/test-deployment-preflight.ps1
```

이 하네스는 gate 누락, billing/API/secret/계정/저장소 오류, 권한 오류와 정확한 not-found 구분, 기존 공개·비활성 invoker 검사의 동의, Node 실패, build/digest/deploy 중간 실패를 시험한다. fake 호출 순서와 실제 실패 이유까지 확인해 이른 단계의 다른 오류를 성공한 gate 시험으로 오보고하지 않는다.

## 병목·몰림과 잔여 위험

- Cloud Run 앞단 소켓 하나에 사용자 모두를30/min으로 묶지 않는다. 운영은 검증한 설치 UUID별30/min + 프로세스 전체300/min + 진행24개다. 소켓 방식은 로컬 호환용 기본값으로 유지한다.
- 설치 UUID는 인증된 사용자 식별자가 아니다. 공격자가 UUID를 계속 바꿔도 전역300/min와 동시24개를 넘기지 못하지만 그 범위 안에서 다른 사용자를 막을 수 있다. health/정책 경로에 대한 인입도 Cloud Run compute비용을 유발할 수 있다. 직접 공개만으로 spoof-resistant IP 제한·대규모 DDoS 방어가 완료됐다고 말하지 않는다.
- `X-Forwarded-For`를 신뢰하지 않는다. 강한 클라이언트별 인입 제한이 필요하면 공식 ingress 보장 검증 후 구성하거나 검증 가능한 edge를 따로 승인·추가한다. 이 패키지는 별도 edge/LB비용을 만들지 않는다.
- 초기 인스턴스1의 메모리 제한은 서로 다른 진행 Play조회32개/HTTP24개를 제한하고 동일토큰은 공유한다. 완료된 구매 결과는 캐시하지 않아 다음 요청은 Play에서 최신 상태를 다시 읽는다. 재시작하면 rate state는 초기화된다. 여러 revision/instance에서 공유 quota나 정확한 총량 제한은 아니다.
- body3초/각 upstream5초/verify23초/Run30초다. metadata+scope+Play+ack 최초4호출의20초 상한을23초 HTTP deadline에 맞춘다. Run콜드스타트·네트워크 시간은 따로 실제 측정해야 한다.
- 로그를 전혀 저장하지 않는다고 가정하면 안 된다. 애플리케이션이 본문을 로그하지 않아도 Cloud Run의 기본 요청 로그는IP/URL·상태·처리시간 등을 포함할 수 있다. 이번 정책 후보는 Google Cloud Logging 기본 요청 메타데이터의30일 보존/글로벌처리를 공개한다. 운영 배포 전에 실제 보존 설정을 이 정책과 맞춘다. 앱은 token·UUID·review code를query나URL에 넣지 않고body로만 보낸다. body·query·토큰·UUID·review code를 별도observability에 추가하지 않는다.
- 플랫폼이 즉시 중단하거나 구매 후 앱이 돌아오지 않으면 acknowledge복구는 보장되지 않는다. RTDN·프로세스 종료 복구·실제환불·재설치·오프라인24시간·실제ARM검증은 추가 운영 gate다.

## 심사 접근

고엔트로피 재사용 코드의 SHA256 hash를 지정 secret버전으로 주입하고 공개된 credential ID를 `PLAY_REVIEW_ACCESS_ID`에 넣는다. `POST /review`는 정확히 packageName·installationId·reviewCode만 받으며 유효 hash일 때만별도 `grantSource=play-review`24시간 이용권을 서명한다. 잘못된/미설정 코드는403, 잘못된 스키마400, quota429, 장애503이다. 이는 구매 이용권·acknowledge와 섞이지 않는다. 코드 원문은 심사자 안내와 사용자 지정 비밀보관 경로 외에 저장하지 않는다. 심사 기간에는 상시 유효·반복 사용 가능하도록 버전운영하고 노출 시 교체해야 한다.
