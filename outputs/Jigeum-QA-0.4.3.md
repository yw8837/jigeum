# UltraQA — 지금 내 기분이 그래 0.4.3

2026-09-30 · code10 · Android API35 x86_64 전용 에뮬레이터.

**PR #2의 기록·공유·CameraX 수정과 보강을 통합한 소스에서 로컬 QA를 통과했다.** 기존0.4 무료/전체 이용권,0.4.2 구매 복구·서버ACK·서명tokenhash·protobuf4.27.5를 유지한다. 운영 설정·실제 Play·ARM·출시·제출은 미완료다.

## 소스와 범위

- base main: `51131377d8d4a6f5b6aed1cd5e3ce76860780cb2`
- 최초 준비 완료 PRhead: `0e19c09577352bd2c1c0da69dff4de58ed4d2844`
- 최신 PRhead: `8475658f171b68e6d909f2798bd680fdeeaffd9d`. 최신main forward merge와 인수인계2문서 변경이며, 통합 중 원본 PR의 Android8개 수정 파일은 변경되지 않았음을 비교했다.
- 첫 후보: `789719c`; test-only URIreceiver 교정`69e6706`, 최신PR문서통합`f3736a4`, test-only 실패진단`c4edfed8910c1d2cf01160deb0fd2d87f811c407`.
- 최종 빌드·전체 계측 소스는 `c4edfed`다. 첫 후보 이후 production Android/Gradle/서버 소스 diff는0이며 테스트와 협업문서만 바뀌었다. [산출물 SHA](../docs/qa/0.4.3/artifacts.json).

## 결과

| 항목 | 실제 결과 |
|---|---|
| Android | **119/119**, fail/ignored0,95.94초. 기존85 +PR8 +SQLite/import7 +실제CameraX4 +공유/가져오기UI/별도UID15 |
| Node | **43/43**, fail/cancel/skip0. localhost HTTP와Google대역, 실제Play 아님 |
| 빌드 | debug/test/release/lint 성공. 최초2분49초, 수신시험교정후17초, 최종4초 incremental |
| Lint | 오류0·경고23·힌트2. warning-free로 보고하지 않음 |
| 업데이트 | 데이터 삭제 없이0.4.2/code9→0.4.3/code10. 실제합성record1건의ID/내용 및meta 보존, schema1 |
| 실제UI | normal/access/album/paywall-layout/transfer-e2e **5종 모두pass** |
| 패키지 | APK개발서명, unsignedAAB, ZIP/ELF16KB, FileProvider,백업제외 통과 |
| 모델/native |0.4.2 APK의18파일과바이트동일. ARM실물검증은별도 |
| 정리 |record/월령/문장이력/출처메타,시험fixture/testAPK/시험snapshot/실패시험DB제거;권한·화면원복;최종crash0;Gradledaemon/전용emulator종료 |

## 실제 실행한 경계

| ID | 실패 조건·실행 | 기대와 실제 신호 |
|---|---|---|
| P1 |timestamp충돌/반복/재개방/삭제/오래된preview/잘못된입력 | 실제SQLite 및JSONparser;별개기록보존,같은설치의충돌재시도안정,메타10,000원시항목상한 |
| P2 |origin/월령메타INSERT에실제SQLite trigger실패 | 기록/출처/월령한transaction rollback,reopen후원본유지 |
| P3 |이전APK에서합성기록생성후install-r |ID/모든필드/meta/schema유지. 빈DB를업데이트시험으로계산하지않음 |
| P4 |실제Compose PNG/PDF생성전/후삭제,오래된클릭callback |현재DB+UI목록/generation검사로renderer/chooser차단;수락하지않은삭제는세대변경안함 |
| P5 |JSON병합직전suspend,페이지이동후전체/단일삭제,앱dispose |root예약으로먼저삭제되지않음;병합완료후명시적삭제;취소된늦은병합으로재생성없음 |
| P6 |chooser전달후기록삭제/clear,24h·20개·64MiB |실제providerURI재읽기;별도testAPK UID가Android읽기권한으로동일URI재open+바이트일치;TTL지연정리/상한시기존파일유지 |
| P7 |실제CameraX provider완료listener를Main실행전보류 |살아있는composition에서만료/authorization교체→Preview/ImageAnalysis bind0;정상grant실제bind,dispose해제;disposed늦은listener bind0 |
| P8 |전체회귀/ACK/패키지 |최종119/서버43/build/lint/package pass,0.4접근·0.4.2ACK보존 |
| P9 |실제UI편집/프로세스죽음/중복탭/무료share,무료잠금/복귀,앨범복원,작은/200%/넓은화면 |5종pass. DocumentsUI백업중processdeath완료/중복import/손상거절/미래시각이후현재저장pass |
| P10 |실패를숨기지않음/dirtyworktree/hang/정리 |두실패cycle원본보존·600s상한·사용자자료보존·scoped상태정리. 공개/실거래조작없음 |

실제 유료 UI·만료는 androidTest의 검증된 권한 대역이며 실제 Play 거래를 대체하지 않는다. CameraX는실제provider/usecase이나권한clock은시험대역이다. URI수신시험은explicittestreceiver이며 시스템chooser의사용자선택전체를자동화한것은아니다. 실제chooser진입·PNGpreview는normalUI에서별도확인했다.

## 실패·교정·간헐적 시험 한계

1. cycle1: 119개 중 별도 UID 수신 시험 1개가 실패했다. standalone testAPK process에 Kotlin 런타임이 없어 receiver가 `NoClassDefFoundError`로 종료했고, finally의 `compose.activity` 재조회가 원래 오류를 가렸다. receiver를 Android/Java 기본 API로 전환하고 발송 전에 context를 확보했다. production 우회나 의존성 추가는 없다. 교정 후 focused 시험과 최종 전체에서 통과했다.
2. cycle2: 119개 중 기존 초안 보존 시험에서 한 차례 8초 timeout이 발생했다. 문장 편집 후 재시작 탭→paywall 대기 단계다. **원인은 미확정**이며 IME/클릭 문제로 단정하거나 앱 버그를 고쳤다고 보고하지 않는다. 입력 동작·timeout·검증 조건을 그대로 둔 focused 실행 두 번(9.544초/5.485초)과 최종 전체 cycle3에서 통과했다. 실패 전에 의미 트리·IME·focus·실제 화면을 저장하고 원래 예외를 던지는 시험 진단을 추가했다. 재발하면 이 자료로 원인을 좁혀야 한다.

이전후보95/main85결과를PR2통과로재사용하지않았다. [cycle1](../docs/qa/0.4.3/instrumentation-cycle1-failure.log),[cycle2](../docs/qa/0.4.3/instrumentation-cycle2-failure.log),[최종전체](../docs/qa/0.4.3/instrumentation.log),[focused1](../docs/qa/0.4.3/access-original1.log),[focused2](../docs/qa/0.4.3/access-original2.log).

## 계약상 남는 한계

- JSONv1/schema1을 유지한다. 출처는 로컬 meta이며 v1 backup에 없다. 새 DB 복원 후 원래 충돌 백업을 가져오면 별도 보존 사본이 추가될 수 있고, 외부 독립 행의 ID·모든 필드가 재배정 행과 완전히 같으면 구분할 수 없다.
- 전달한 snapshot은 기록 삭제와 별도로 남는다. 24시간 후 다음 시작·삭제·공유 때 정리하고, OS 캐시 회수 시 더 일찍 사라질 수 있다. 앱 미실행·시계 변경에도 정확한 실시간 보관 기한을 보장하지 않는다. 20개·64MiB 상한은 새 공유를 거절하며 기존 URI를 임의로 삭제하지 않는다. 앱 데이터 삭제·제거와 외부 공유본 삭제는 구분한다.
- 일반 소리·얼굴 단서와 보호자 맥락으로 유한 문장 후보를 만든다. 영아 의도 정확도·의료 진단·실제 개인화 학습을 검증한 것이 아니다.
- MediaPipe/Billing SDK의 자체 진단 시도와 현재 권한 구성의 전송 차단 경로를 [SDK 검토](../docs/qa/0.4.3/sdk-transport-review.md)에 기록한다. 빈 SDK DB만으로 미전송을 추측하지 않고 실제 권한 오류와 현재 SDK 전송 순서를 함께 확인했다. 모든 SDK 네트워크 경로를 패킷 수준으로 검증했다는 뜻은 아니다.

## 남은 운영 조건

검증 HTTPS URL·공개키·업로드 서명은 미설정이며 현재 APK에서 실제 구매는 비활성화다. 새 개인 Play 계정 승인·앱/상품·운영 서비스 계정/HTTPS/키/프록시 한도/관측, 실제 구매·보류·복원·환불·ACK·계정 변경, ARM/OEM/16KB 물리 기기, 공개 정책 HTTPS·시행일·서명은 별도 gate다. 서버가 처음 구매 token을 받기 전에 영구 종료된 구매의 3일 ACK 기한은 RTDN/운영 복구가 필요하다. 공개 출시·Play 제출은 보류를 유지했다.

## 산출물과 근거

- [debug APK](Jigeum-Android-debug-0.4.3.apk) · SHA-256 `137bc3154f9ebf947a5da078e6450bbd56f512f8b6be22e73537889a89d8ff35`
- [unsigned AAB](Jigeum-Android-unsigned-0.4.3.aab) · [해시·크기](../docs/qa/0.4.3/artifacts.json)
- [QA 증거 ZIP](Jigeum-QA-evidence-0.4.3.zip) · 원본stdout/합성DB/화면/행렬/정리스크립트. Git에는정규화한receipt만보존하며키/실거래token/사용자기록/빌드캐시를포함하지않는다.
- [전체계측](../docs/qa/0.4.3/instrumentation-result.json),[서버](../docs/qa/0.4.3/server-tests.log),[UI](../docs/qa/0.4.3/ui-summary.json),[정리](../docs/qa/0.4.3/cleanup-result.json),[업데이트](../docs/qa/0.4.3/upgrade-result.json),[교차검토](../docs/qa/0.4.3/cross-review.md).
