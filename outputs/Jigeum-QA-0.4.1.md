# UltraQA Report — 지금 내 기분이 그래 0.4.1

2026-09-30 · code 8 · 소스 `690df50df85b6479271de65ca4ba9e91fff94b34` · API 35 x86_64 전용 에뮬레이터.

**겹치지 않는 결제 복구·protobuf 수정의 로컬 QA를 완료했다. PR #2는 통합하지 않았다.** 기존 0.4 무료/전체 이용권과 온디바이스 정책을 유지한다. 공개 출시·제출은 보류하며 실제 Play 거래·ARM·운영 연결은 미완료다.

## 목표·범위·종료 조건

결제 콜백 유실 후 무한 대기와 재결제 위험을 줄이고, 현재 구매 조회 확인 전 새 결제를 막는다. acknowledge 제한 재시도와 이전 응답 무효화, patched protobuf 파서·번들 모델 호환을 검증한다. 정상/오류/시간초과/늦은 응답 시험, 전체 회귀·관련 UI·패키지·정리가 통과하면 이 범위의 QA를 종료한다. 합성 자료와 전용 에뮬레이터만 사용하고 실제 구매·배포·PR #2 머지는 수행하지 않는다.

## 검증 결과

| 항목 | 결과와 한계 |
|---|---|
| 빌드 | debug APK·계측 APK·release AAB·release lint 성공, 2분 14초 |
| Android | **81/81**, 실패·ignored 0. 하네스 57.9초. 기존72 + 결제복구7 + protobuf/Face2 |
| 서버 | **34/34**, 실패·취소·skip 0. localhost HTTP, Google 응답은 대역. 원격 리뷰 추가18개 포함 |
| 실제 무료 UI | normal/access/transfer-e2e **3유형 통과**, 최종 main APK에서 실행 |
| Lint | 오류0, 경고23, 힌트2. 추가 경고는 protobuf 최신 버전 안내 |
| 패키지 | debug 서명, ZIP/ELF16KB, FileProvider, 백업 제외 통과. AAB **unsigned** |
| 모델/native | 보존된0.4.0 APK의18개 파일과 바이트 동일 |
| 정리 | 합성 기록·문구 이력·공유 캐시·fixtures·계측 패키지 제거, 화면/권한 복원·cold launch 확인 |

## 시나리오 행렬

| ID | 실패 모델·설정 | 실행·기대 신호 | 실제 결과·근거 | 수정·정리 |
|---|---|---|---|---|
| B1 | 결제 launch 뒤 callback 없음, 정상 복귀와 경합 | androidTest timer/foreground/restore:120초 유예 뒤 조회, 조회 실패시 재결제 금지 | 통과, BillingManagerLifecycleTest | monotonic deadline, 관리자 close |
| B2 | 구매 조회 무응답·시간초과·이전 응답 | 30초 상한, request/generation으로 새 상태 보호, 성공한 빈 조회 전 새 결제 금지 | 통과, 전체81 로그 | query timer·request ID, close시 취소 |
| B3 | acknowledge 오류·무응답·늦은 결과 | 3회 상한,5/10초 간격, 시도당30초 상한, 성공시 지연 안내만 해제 | 통과, 테스트 대역으로 지연 재현 | cancellable continuation, 재검증시 이전 세대 취소 |
| P1 | 깊은 protobuf unknown groups | 512바이트 합성 입력, 재귀한도32에서 protobuf 예외. StackOverflow 허용 안함 | 통과, ProtobufRuntimeTest |4.26.1→4.27.5, fixture 메모리 해제 |
| P2 | 런타임 교체 후 실제 번들 모델 | FaceLandmarker 생성·IMAGE 빈 프레임 추론, YAMNet 합성 오디오 | 통과, 실제 모델 실행. 얼굴/의도 정확도나 camera VIDEO 시험 아님 | 모델·bitmap close |
| U1 | 수정 초안·프로세스 종료·저장 연타 | normal: 초안 보존, 한 건 저장, 무료 PNG chooser | 통과,ui-summary/스크린샷 | 생성 기록·export 삭제 |
| U2 | 무료 미디어 우회·화면 복원 | access: 센서/파일 선택 전 안내, 복원 후 무료 저장 | 통과,ui-summary | 실제 결제 대역으로 오인하지 않음 |
| U3 | SAF 중 프로세스 종료·중복/손상/미래시각 JSON | transfer-e2e: 백업 복귀·중복 방지·잘못된 파일 거절·현재 저장 | 통과,ui-summary | 합성 JSON/DB/Downloads 정리 |
| C1 | dirty tree·명령 중단·과거 로그 혼합 | 원래0.4/로컬후보 보존, 제한시간/exit/실제 count 확인 | 통과, main81과 후보95 분리 | 전용 scope·emulator·daemon 정리 |

수정 대상의 시간초과·취소·늦은 결과는 계측에서 실행했다. 새 결제는 현재 성공한 빈 구매 내역 조회 후에만 허용한다. 운영 MainActivity에는 테스트 유료 우회가 없다. 실제 상업 거래를 실행한 시험은 아니다.

## 명령·근거

- Gradle assembleDebug/assembleDebugAndroidTest/bundleRelease/lintRelease, worker1: exit0. [build.log](../docs/qa/0.4.1/build.log)
- `instrument.py --install-app`, timeout600초: exit0, OK(81 tests), ignored0. [계측](../docs/qa/0.4.1/instrumentation.log)
- `node --test`: exit0,34/34,skip0,724.9025ms. [서버](../docs/qa/0.4.1/billing-node-tests.log)
- `run_qa.py normal`, `run_qa.py access`, `transfer_e2e.py`: 각각 exit0, 내부 각 단계 bounded timeout. [UI](../docs/qa/0.4.1/ui-summary.json)
- `check_package.py --expect-unsigned-aab --baseline outputs/Jigeum-Android-debug-0.4.0.apk`: exit0. [패키지](../docs/qa/0.4.1/package-verdict.json)
- protobuf dependencyInsight:4.27.5 선택. [의존성](../docs/qa/0.4.1/protobuf-dependency.log), [공식 GHSA](https://github.com/protocolbuffers/protobuf/security/advisories/GHSA-735f-pc8j-v9w8). 이 버전은 해당 advisory의 patch 목록에 있다. 모든 의존성 취약점의 부재를 보증하지 않는다.

## PR #2와 남은 코드 경계

[PR #2](https://github.com/yw8837/jigeum/pull/2)는 Draft이며 준비 완료 안내 전에는 머지하지 않는다. 별도 로컬 대안 `codex/service-041-candidate` / `99ca1e1`의95계측·빌드 성공은 PR 실행 근거가 아니다. 그 대안은 원격에 푸시하지 않았다.

main에는 PR #2의 timestamp 충돌 import 누락·삭제 중 공유·CameraX bind 직전 권한 수정이 아직 포함되지 않았다. 삭제한 기록을 가리키는 오래된 공유 콜백과 전체 삭제 뒤 늦은 import의 기록 재생성도 준비 완료 후 통합 검토 대상이다. **이 경계까지 서비스 QA가 완료됐다고 보고하지 않는다.** 비교·승인 기준은 [통합 대기 문서](../docs/qa/PR2-INTEGRATION.md)에 있다. 로컬 대안의JSON v2 정책은 main에 반영하지 않았으며 main 백업은 기존v1이다.

## 정리·산출물

이 실행에서 발견된 실패0, main QA cycle1. 합성 데이터·공유 캐시·Downloads fixture·테스트 패키지·화면/권한을 정리했고 cold launch에서 앱 crash buffer0을 확인했다. Gradle daemon과 소유 에뮬레이터를 종료한다. 다른 작업자의 소스와 기존0.2/0.3/0.4 산출물을 보존한다. [정리 결과](../docs/qa/0.4.1/cleanup-result.json).

- [debug APK](Jigeum-Android-debug-0.4.1.apk), SHA-256 `47cdd62f43e6e62ff5d320c5285f3e43b3f2b5be089a6a6453a69b68100d5536`
- [unsigned AAB](Jigeum-Android-unsigned-0.4.1.aab), SHA-256 `aba319bf009d28a26e3ea120e39d0f7bdef8091b36e2fcb51ef8bc282fe2b173`
- [QA receipts](../docs/qa/0.4.1/README.md), [크기·SHA](../docs/qa/0.4.1/artifacts.json)
- 로컬 증거ZIP: `outputs/Jigeum-QA-evidence-0.4.1.zip`, 52개 항목,CRC 전체 확인. 합성 DB·화면이 포함돼 Git에서 제외한다.

QA 상태 정리 하네스가 처음에 read/clear 응답에 공통 success 필드가 있다고 잘못 가정했다. 실제 read 상태와 clear의 cleared:true, 최종 exists:false를 확인해 정리했다. 앱 시험 실패가 아니며 scoped 상태 부재는 [별도 영수증](../docs/qa/0.4.1/state-cleanup.json)에 있다. Git 계측 로그는 줄바꿈을 정규화했고 원본 stdout은 로컬 evidence/ZIP에 보존했다.

## 운영 미완료

검증URL·공개키·업로드 서명 미설정으로 실제 구매는 비활성화돼 있다. 무료 기능은 사용 가능하다. Play 개인 계정 인증·상품·HTTPS verifier·정책 URL·실제 구매/환불/복원·ARM/OEM 기기는 별도다. 앱이 실행되지 않으면 client acknowledge 재시도는 3일 기한을 보장할 수 없으므로 실제 운영에서 acknowledge 처리와 관측을 추가 검증해야 한다. 서버34시험은 실트래픽 capacity나 proxy/Google quota 결과가 아니다.

**ULTRAQA COMPLETE: main 결제·protobuf 범위는 cycle1에서 통과. PR #2 통합과 전체 서비스 완료는 대기.**
