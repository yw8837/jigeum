# 결제 연결 진행 — 2026-10-01

최신 사용자 ‘그럼 연결하자, 내가 채워놓은 거 이어서’ 요청으로 비용 연결 작업을 재개했다. 기본 gcloud 계정은 바꾸지 않고 각 명령에 계정을 명시한다.

## 실제 확인 완료

- 전용 `jigeum-play-20261001` / 번호 `795224750373` / ACTIVE.
- 기존 Cloud 결제 계정과 연결한 뒤 studio 계정에서 **billingEnabled=true** 재조회.
- 원계정의 프로젝트 `roles/billing.projectManager`를 연결 중에만 추가하고 finally에서 제거. IAM 재조회로 제거 확인.
- 기존 Cloud Billing 상세의 비용·예산 입력이 KRW 표시임을 확인.
- 사용자가 Play 판매자 프로필 최종 제출을 완료한 뒤 실제 지급/설정 화면 진입을 확인.
- 첨부 통장 기준 지급 계좌가 저장되고 **확인 대기중** 상태임을 확인. Google 소액 입금 확인과 지급 가능 검증은 미완료.
- 실제 P-256 서명 키, 공개 SPKI, 고엔트로피 심사 코드와 hash를 저장소 밖 ACL 제한 경로에 생성. Google 서비스 계정의 개인키 JSON은 생성하지 않음.
- 전용 runtime/build·API10개·서울 repository/source bucket·서울 secret2개 version1·self getAccessToken 권한·기본 접속 로그30일·이 프로젝트만 월10,000KRW 예산 알림을 실제 bootstrap으로 준비·재조회했다. [실행 로그](bootstrap-live.txt). 알림은 비용 상한이 아니다.
- Play Console에서 사용자 승인 후 runtime 계정을 이 앱에만 등록했고 **활성 / 앱 권한4개**를 저장 후 다시 확인했다. Console 필수 앱/품질 읽기와 재무/구매 조회·주문/구독 관리이며 계정 전체 권한은 선택하지 않았다. 실제 구매·ACK는 별도 검증이다.
- API 활성화가 자동으로 부여한 기본 compute Editor와 legacy Cloud Build builder 역할을 발견했다. 전용 프로젝트의 기존 build/Run 목록이 비어 있음을 확인하고 두 역할을 제거했다. 전용 계정과 Google 서비스 에이전트는 유지했다.
- 예산 작업의 quota project만 지정하도록 수정하고 billing 관리자에게 `serviceUsageConsumer`를 임시 부여했다. finally에서 제거한 뒤 별도 IAM inventory로 재확인한다. 기본 SDK 계정과 다른 앱 프로젝트는 바꾸지 않았다.

## 실행 중 발견·수정

초기 준비가 custom role 검사에서 중단됐다. 실제 permission1개는 일치했으나 PowerShell7의 `Write-Output -NoEnumerate`가 단일 JSON 객체를 List로 감싸 permission 배열을 문자열로 평탄화했다. index0이 권한 이름 대신 첫 글자가 되는 재현을 확인하고 정상 enumeration으로 고쳤다. PowerShell5.1과7 각각14사례, **28/28**을 다시 통과했다. 첫 실패와 수정 후 로그를 [QA 경로](../../qa/0.4.5/README.md)에 보존했다.

다음 예산 조회는 billing quota project 미지정으로 실패했다. 이 프로젝트의 Billing Budgets API와 `--billing-project`를 명시하고 임시 최소 consumer 권한으로 재시도해 bootstrap 최종 성공을 확인했다. 생성했던 secret version1을 재사용했고 새 키·버전을 만들지 않았다.

## 실제 서버·앱 연결 완료

- 서울 Cloud Run `jigeum-verifier`를 고정 image digest로 배포했다. revision `jigeum-verifier-00002-hvb`, traffic 100%, 최소 0·최대 1, 1CPU/512MiB, concurrency 24, 요청 기반 과금, timeout 30초다. [설정 11/11](run-service-verdict.json).
- 공개 주소: **https://jigeum-verifier-th7ce7roma-du.a.run.app**. POST `/verify`·`/review`, 공개 `/privacy`·`/terms`·`/support`, 외부 상태 확인 `/jigeum-health`.
- 실제 IAM inventory [19/19](cloud-iam-audit.json): runtime/build 키 내보내기 0, 기본 계정 광역 역할과 임시 사용자 역할 제거, 대상 secret/bucket/repository/self-token 최소 범위 확인.
- Node 56/56, 실제 HTTPS [9/9](https-smoke.json), warm 오류 코드 요청 제한·설치 격리 [4/4](live-load-smoke.json)를 확인했다. 실제 심사 코드의 서명 왕복도 통과했고 비밀값은 출력·문서·Git에 남기지 않았다.
- 최종 `connected` 0.4.5/code12 APK/AAB에 실제 주소와 공개 키를 넣고 서명했다. 최종 Android는 **142 통과·미설정 전용 2 skip·실패 0**, 실제 릴리스 앱은 **10/10**이다. 실제 서버 서명 수락, 카메라 시작/종료, 영상 화면, PDF 생성/공유, 해제/재잠금과 무료 기록 보존을 확인했다. [최종 연결 QA](../../qa/0.4.5/connected/README.md).
- 실제 공개 정책과 앱 내 로그·국외 처리 고지/시행일을 맞췄다. Play Console에 `/privacy` URL을 저장하고 저장 성공을 확인했다. 이어 사용자 승인 후 심사 코드/영문 안내와 성인18세 이상을 저장해 대시보드 **9/11**을 확인했다. 파트너 기기 선택 사용은 껐고 공개 심사 제출은 하지 않았다.

초기 `internal` 파일은 URL/공개 키가 비어 있다. 중간 `configured`와 최종 `connected` 파일은 별개이며 이전 산출물을 덮어쓰지 않았다. 최종 배포 준비에는 `connected` 파일을 사용한다.

## 남은 실제 운영 검증

Data safety·스토어 정보·테스트 업로드/배포, 실제 상품/가격·Play 구매/ACK/보류/복원/환불·계정 변경, ARM/16KB 실기기, 은행 소액 확인, 비공개 12명·14일과 공개 제출은 미완료다. 심사 이용권의 실제 앱 수락은 Play 구매 검증 통과를 뜻하지 않는다.

콜드 시작·실제 구매 부하·장시간 운영은 미검증이다. 오류 코드 요청 p95 374ms를 구매 지연으로 쓰지 않는다. 월 1만원 알림과 최대 1인스턴스는 비용 차단선이 아니다.

첨부 사업자/영문 증명과 통장, 결제 계정 ID, 법적 주소·계좌번호·심사 코드·개인키는 Git/QA 공유 패키지에 넣지 않는다. 사용자 선택한 개인 결제 프로필을 임의로 사업자용으로 변경하지 않았다. 15% 수수료 프로그램의 계정 그룹/약관은 별도 항목이며 완료 표시하지 않는다.
