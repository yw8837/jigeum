# 0.4.5 소스·배포 도구 독립 검토

2026-10-01 · Android **0.4.5 / code12** · 검토자: `quality_review`

## 판정과 근거의 범위

**현재 검토한 Android·Node 소스와 배포 도구는 승인한다. 추가 P1/P2 결함은 확인하지 못했다.** 최초 배포 도구 검토에서 지적한 P2 두 건은 수정본의 실제 분기와 실패 시 중단 경로를 다시 읽어 해결을 확인했다.

검토 시 Git 기준 커밋은 `01b4b58fdcae496e5bacb4bb2f5749e75666a7d7`이며 변경사항은 아직 작업 트리에 있었다. 이 커밋만을 수정본의 식별자로 쓰지 않는다. 함께 작성한 [review-verdicts.json](review-verdicts.json)의 16개 소스 SHA256이 이번 읽기 검토의 범위를 식별한다. 최종 패키지·커밋·원격 일치 판정은 root의 통합 기록을 따른다.

검토자는 Android/ADB/Gradle·실제 Cloud 작업을 실행하지 않았다. 독립 실행은 배포 PowerShell AST, 기본 dry-run, 기존 21개 하네스 보고서의 구조·개수 일치 확인이다. 전체 21개 시험을 검토자가 다시 실행했다는 뜻이 아니다.

## 유지한 구현 경계

- 무료/전체 이용권의 기존 0.4 계약과 PR #2 구현을 유지한다. 카메라·영상·PDF의 실제 작업 시작에는 현재 권한을 확인하며 이미 생성된 무료 초안과 저장 기록은 유지한다.
- 구매와 심사 접근은 서로 다른 서명 출처다. `verifySignedEntitlement`는 `play-review`를 수락하지 않고, 심사 verifier도 구매 grant를 수락하지 않는다. 코드·콜백·화면 Boolean만으로 전체 기능을 열지 않는다.
- 심사 코드 확인은 정확한 HTTPS `/verify` 설정에서 만든 동일 origin의 `/review`에만 보낸다. 사용자 정보·query·fragment와 redirect를 거부한다. 아기 미디어·표현·기록을 이 요청에 포함하지 않는다.
- 심사 코드는 설치 식별자를 AAD에 포함한 기기 Keystore AES256-GCM으로 보관한다. 복사한 preference, 암호문 손상, 키 유실은 접근을 허용하지 않는다. UI 입력은 `rememberSaveable`에 넣지 않는다.
- 네트워크 작업은 저장소를 변경하지 않는다. 완료 결과는 manager의 현재 generation·닫힘 상태를 검사한 뒤에만 수락한다. 해제·닫힘은 기존 요청을 취소하며 늦은 결과가 이전 권한을 복원하지 못한다.
- 구매·심사 캐시는 최대24시간, 같은 boot, 단조 경과시간과 서명 기한을 함께 검사한다. 재부팅·근거 손상은 온라인 재확인이 필요하다. 장애에서는 유효한 기존 서명 시간만 유지한다.
- `HttpRequestDeadline`는 공용 daemon scheduler의35초 elapsed watchdog과 취소 hook을 사용한다. `finally`에서 timer·hook·연결을 정리하고 수락 직전에도 기한을 확인한다. 느린 drip 응답의 idle timeout 갱신만으로 전체 기한을 늘리지 않는다. 플랫폼 socket 종료·스케줄링 지연 때문에35초를 정확한 밀리초 UI 보장으로 표현하지 않는다.
- TLS fixture와 신뢰 설정은 `androidTest`에만 있다. 제품의 endpoint·HTTP·인증서 검증 우회는 추가하지 않았다.
- workload 인증은 고정 metadata URL·Google 응답 헤더·redirect 차단·응답 크기를 확인한 뒤 IAM Credentials로 Publisher 범위 토큰을 받는다. 서비스 계정 키를 APK나 저장소에 넣는 방식으로 바꾸지 않았다.
- Node의 body·verification deadline, 동시 요청·전역/설치별 제한, 공유 lookup·토큰 발급을 확인했다. UUID는 인증된 사용자 식별자가 아니며 회전 공격이나 여러 revision의 전역 한도를 해결했다고 주장하지 않는다.
- 정책 route는 정확한 allowlist와 CSP를 사용하고 사용자 경로로 임의 파일을 선택하지 않는다. 서울 실행·글로벌 로그·30일 보존은 운영 후보이며 실제 제공 상태와 맞춘 공개 확인이 필요하다.

## 배포 도구 P2 해결

| 최초 지적 | 수정 후 확인 |
|---|---|
| dry-run의 gate 문자열만 출력하고 `-Execute`는 바로 build/push 진행 | `-OperationalGatesConfirmed`를 필수로 요구한다. 첫 source upload/build/push 전에 billingEnabled, API6개, 지정 secret 버전2개의 ENABLED 상태, DOCKER 저장소, 분리된 runtime/build 계정의 존재·활성, 기존 service/IAM을 읽고 현재 source Node 시험을 실행한다. 어떤 선행 단계가 실패해도 후속 변경을 중단한다. |
| switch 없이 기존 공개/IAM 검사 비활성 상태 재사용 가능 | `allUsers` invoker 및 invoker IAM 검사 비활성을 탐지한다. `-PublishPublicService` 또는 `-ReuseExistingPublicService` 없이는 공개 서비스를 재사용하지 않는다. 정확한 대상 service의 not-found만 신규 경로로 인정하며 권한/네트워크 오류는 중단한다. 신규 비공개에는 `--no-allow-unauthenticated`를 명시한다. |

추가된 dedicated build-source bucket과 `--gcs-source-staging-dir`를 재검토했다. 계획의 bucket 이름이 전용 project에서 파생된 값과 다르면 중단하며, Cloud Build는 이 bucket의 `source` 경로를 명시한다. 하네스는 build 호출의 해당 인수도 검사한다.

기본 dry-run 독립 실행에서 `gatesActuallyChecked=false`, 수동 확인·공개 변경·기존 공개 재사용 동의·결제/리소스 준비 변경 모두 `false`를 확인했다. 최신 source-bucket 변경 뒤에도 AST/dry-run을 재확인했다. PowerShell AST 오류는0개였다. [배포 하네스 보고서](../../../tools/deploy/QA-preflight-20261001.json)의21개 결과·21개 PASS·실제 Cloud 작업0을 읽어 구조 일치를 확인했다.

`-OperationalGatesConfirmed`는 운영자가 수동 IAM 최소 범위·Play 앱 권한·정책·보존 설정·예산·이미지/Android QA를 확인했다는 표시다. 해당 항목을 스크립트가 자동 인증한 결과로 보고하지 않는다. preflight 이후 외부 관리자가 상태를 바꿀 수 있으며 원자적 인프라 잠금은 제공하지 않는다.

## 통합 실행 결과 — root 담당

| 실행 | 최신 결과와 출처 구분 |
|---|---|
| 전체 Android 계측 | root가144개 **PASS**,88.77초, 실패0·ignored0를 보고했다. 검토자는 `work/release-completion-20261001/cycle2-instrumentation/instrumentation-result.json`의144/88.77/exit0을 읽었다. |
| Focused UI | root가3개 **PASS**를 실행했다. 검토자는 `work/release-completion-20261001/focused-ui/result.json`의3/exit0을 읽었다. |
| 첫 Android 실행 |144개 중 실패1·ignored0. 비활성 TextField는 `hasSetTextAction`을 제공하지 않아 선택자가 노드를 찾지 못했다. 실제 입력의 `testTag`로 선택하면서 비활성 검사·제품 동작을 유지한 뒤 재시험했다. 실패를 숨기거나 제품 장애로 단정하지 않는다. |
| Node | root 실행 **55/55 PASS**. Google metadata/IAM/Play 응답은 대역이며 실제 거래 결과가 아니다. |
| 배포 gate 하네스 | 배포 담당 실행 **21/21 PASS**, 실제 Cloud 작업0. 검토자의 독립 실행은 AST/dry-run/보고서 구조 일치이며 전체21 재실행이 아니다. |
| Lint | 실제 release XML/txt에서 **0오류·15경고·0정보 항목**을 읽었다. 아래15개를 개별 검토했다. |

## Lint15개 판정

경고 숫자를 줄이기 위한 baseline·suppress·검사 비활성화는 넣지 않았다. 기존 SDK 버전은 변경하지 않았다. 원본은 `outputs/MoaAndroid/app/build/reports/lint-results-release.xml` / `lint-results-release.txt`, 요약은 `work/release-completion-20261001/lint-summary.json`이다. 같은 `clearGrant` 위치의 ApplySharedPref·UseKtx는 별개의 경고2개다.

| 번호 | ID·위치 | 유지 이유와 판단 |
|---:|---|---|
|1|ApplySharedPref · ReviewAccessVerifier.kt:102|심사 grant/anchor/선택한 credential 삭제를 동기 transaction으로 처리한다. `apply()`로 바꾸면 디스크 완료를 기다리지 않는 의미로 달라진다. commit 반환을 검사하는 위치는 아니며 저장 성공을 확인했다고 주장하지 않는다. 아래 삭제 내구성 한계를 함께 기록한다.|
|2|AndroidGradlePluginVersion · gradle-wrapper.properties:3|Gradle8.14.3→8.14.5 안내다. ID 명칭과 별개로 실제 메시지는 wrapper 버전 안내다. 검증한 빌드 조합을 유지하며 업그레이드는 별도 빌드·패키지 회귀를 요구한다.|
|3|GradleDependency · app/build.gradle.kts:78|Activity Compose1.10.1→1.13.0 안내. 카메라·권한·Activity/Compose 수명주기 변경을 이번 마감 범위에 추가하지 않았다. 최신 버전 존재만으로 현재 결함이나 취약점을 단정하지 않는다.|
|4|GradleDependency · app/build.gradle.kts:83|Lifecycle runtime2.9.2→2.11.0 안내. 현재 취소·재진입·상태 흐름 회귀를 검증한 조합을 유지한다. 업그레이드는 해당 수명주기 회귀를 함께 수행한다.|
|5|GradleDependency · app/build.gradle.kts:98|Android test runner1.6.2→1.7.0 안내. 계측 전용 의존성이다. 현재144개 실행의 runner를 유지하며 제품 권한이나 결제 검증 우회와 무관하다.|
|6|GradleDependency · app/build.gradle.kts:99|Android test JUnit1.2.1→1.3.0 안내. 계측 전용 의존성이다. runner와 함께 별도 시험 도구 업그레이드로 관리한다.|
|7|NewerVersionAvailable · app/build.gradle.kts:91|protobuf-javalite4.27.5→4.36.2 안내. MediaPipe 모델·protobuf/JNI 계약과 현재 비축소 release 구성을 유지한다. 치환은 실제 release 모델/프로토콜 회귀 후 수행한다.|
|8|UseKtx · ObservationStore.kt:195|기존5개 중1. legacy events/age_band 삭제의 commit Boolean을 확인해 실패를 처리한다. Unit 반환 KTX edit로 단순 치환해 실패 검사를 버리지 않는다.|
|9|UseKtx · PhraseEngine.kt:80|기존5개 중2. 문장 이력 삭제의 commit 실패를 `check`로 처리한다. 반환 검사 유지가 필요하다.|
|10|UseKtx · PhraseEngine.kt:130|기존5개 중3. 문장 이력 저장의 commit 실패를 `check`로 처리한다. 저장했다고 오보고하지 않는다.|
|11|UseKtx · PurchaseVerifier.kt:101|기존5개 중4. 설치ID commit 성공 시에만 ID를 사용하고 실패하면 null로 닫는다. 반환 검사 유지가 필요하다.|
|12|UseKtx · PurchaseVerifier.kt:127|기존5개 중5. 서명 envelope와 clock anchor를 한 transaction으로 저장하며 commit Boolean을 수락 결과로 사용한다. Unit 치환으로 권한 수락 조건을 없애지 않는다.|
|13|UseKtx · ReviewAccessVerifier.kt:87|신규3개 중1. 심사 설치ID 저장 성공 시에만 ID를 사용한다. 구매 경로와 동일하게 실패 시 null로 닫는다.|
|14|UseKtx · ReviewAccessVerifier.kt:100|신규3개 중2. `clearGrant`는 commit 반환을 검사하지 않는다. 따라서 Boolean 보존이 유지 이유는 아니다. 동기 삭제 transaction을 유지했으며 `edit(commit=true)`로 동일 의미 치환은 가능하다. 경고를 남겨 미처리 저장 실패·성능 검토를 추적한다.|
|15|UseKtx · ReviewAccessVerifier.kt:133|신규3개 중3. 검증한 envelope·암호화 코드·clock anchor를 한 transaction으로 저장하고 commit 성공만 수락한다. Boolean 검사 없는 KTX 치환은 하지 않는다.|

합계는 **버전 안내6 + UseKtx8 + ApplySharedPref1 =15**다. UseKtx8은 **기존 Boolean 검사5 + 신규 Boolean 검사2 + 신규 삭제 transaction1**로 구분한다.

### 동기 삭제의 한계

`clearGrant`는 commit 반환을 사용하지 않아 디스크 삭제 성공을 보증하지 않는다. 명시적 해제는 preference 삭제 후 Keystore 키 삭제도 수행하므로 이전 암호문을 사용할 추가 경로를 막는다. 만료·손상된 grant는 삭제 자체와 별개로 서명·시간·boot/elapsed 검사에서 거부한다. 그러나 디스크 쓰기 실패와 키 삭제 실패가 함께 발생하는 경우의 재시작 내구성을 시험했다고 주장하지 않는다. 동기 preference I/O의 실제 OEM 지연도 별도 확인 대상이다. KTX 스타일 치환만으로 이 한계가 해결되지는 않는다.

## 외부 상태와 완료하지 않은 항목

최신 사용자 승인 뒤 root가 전용 프로젝트의 **billingEnabled=true**와 임시 `projectBillingManager` 제거 재조회를 보고했다. 이는 root의 외부 실행 결과이며 검토자가 Cloud를 재조회한 결과가 아니다. 결제 계정 ID·비밀값은 이 문서와 JSON에 넣지 않는다.

결제 연결만으로 실행 계정·Play 권한·비밀 주입·검증 서버 배포·정책 공개가 완료된 것은 아니다. 다음 항목은 이번 코드 승인으로 완료 처리하지 않는다.

- 컨테이너 image build/run·실제 운영 HTTPS smoke·부하/콜드스타트 실측.
- 운영 verify URL·공개키 연결, 심사 코드의 실제 발급/주입·상시 재사용 운영.
- 실제 Play 상품·구매·복원·보류·해지·환불·프로세스 종료 뒤 ACK 회복.
- ARM/16KB/OEM 실기기·실제 사용자 접근성 검증.
- 정책 HTTPS 게시와 실제 로그 제공자/보관/국외 처리의 일치 확인.
- Play 전체 접근 안내·내부/비공개 테스트 배포,12명14일 요건과 공개 제출.

서명 없는 권한 열림·취소 뒤 늦은 저장·조용한 배포 실패를 승인한 것이 아니다. 현재 소스의 해당 방어 경로와 로컬 실행 근거를 검토했고, 실제 운영 검증이 남아 있는 상태의 **코드·패키징 후보**를 승인했다.

## bootstrap 추가 초기 읽기 검토

root 실행 전 `tools/deploy/bootstrap-cloud.ps1`를 추가로 읽었다. 정확한 project/number/서울 대상, version1 고정, 운영 준비·KRW 확인 switch, 저장소 밖 입력 파일, P-256/hash 형식, 기존 결제 연결 일치, resource 쓰기 뒤 읽기 확인, secret/repository/source bucket별 권한과 getAccessToken 하나의 self 역할, 월1만원의 project-only 알림을 확인했다. 초기 읽기에서 추가 P1/P2를 확인하지 못했으며 외부 실행은 하지 않았다. 검토한 초기 파일 hash는 JSON의 `initialReadOnlyBootstrapReview`에 별도로 기록했다.

실제 실행 전 다음 한계를 root에 전달했다. 이후 수정본은 이 초기 승인에 자동 포함하지 않는다.

- 기존 source bucket 재사용의 초기 검사는 owner/서울/UBLA/PAP를 확인하며 lifecycle7일·soft-delete0 재검사는 없다. 생성 경로는 해당 값을 설정한다. 기존 bucket의 보존 설정은 추가 확인 대상이다.
- 기존 runtime/build 계정 재사용은 이메일·활성 상태와 추가한 resource binding을 확인한다. 상위/기존 광역 IAM 권한이 없다는 전체 감사를 수행하지 않는다.
- 기존 secret version1의 재사용은 운영자가 로컬 키/hash와의 대응을 알고 명시적으로 확인해야 한다. payload를 읽지 않는 구조 자체가 두 값의 일치를 검증해 주지는 않는다.
- 마지막 budget 단계 실패 시 앞서 생성한 API/secret/IAM이 남는 부분 실행이다. 자동 rollback은 없으며 단계별 결과를 실제 운영 기록에 보존해야 한다.
- 실제 결제 계정 통화와 기본 알림 수신 권한을 확인해야 한다. 월1만원은 알림 기준이며 지출 차단 기능이 아니다.
