# 통합 전 읽기 전용 교차 검토

2026-09-30. 실행 결과가 아닌 소스 검토. PRhead0e19c09/base5113137.

## 가져오기 — billing_recovery APPROVE

원본 timestamp+길이 구분 UTF-8 전체 필드 SHA-256, 대상 실제 내용 재확인, 별개 시각 관찰 보존. 현재DB recheck/행/출처/월령 하나의 transaction. origin/age 실패 후 reopen 시험 준비. 원시메타 entry count10,000 상한(잘못된 값 포함). 삭제 시 provenance 제거. schema1/JSONv1 유지. 남는 한계: 출처는 v1 export에 없고 독립적 외부행과 relocated행이 ID+모든 필드가 같으면 구분 불가. 차단 결함 없음. 빌드·ADB·수정 없음.

## 카메라 — import_boundary APPROVE

실제 provider 완료 listener만 지연, Main에서 실행해 actual Preview/ImageAnalysis isBound 확인. composition alive expiry/updated authorization/positive bind+dispose/late disposed callback4개. internal executor defaultMain/no-op observer, production call은 seam 미주입. provider fake/premium 우회 없음. dispose는 자기 usecase만 unbind. Play/ARM/추론 품질 검증은 별도. 차단 결함 없음. 빌드·ADB·수정 없음.

## 0.4.2 ACK — billing_recovery APPROVE

서버 eligible검사+확인된ACK 또는 성공POST 후에만 서명hash. client signedaccept 후 exactUTF8 tokenhash에서만 duplicateACK생략. caller취소/ACK실패 failclosed. 최초로 서버에 token이 오기 전 recovery/RTDN은 운영 한계. 빌드·ADB·수정 없음.

## 공유·import UI

import_boundary APPROVE. Import confirm이 root busy를 얻고 app scope finally에서 해제해 Guide dispose/페이지이동 중 유지한다. 수락되지 않은 삭제는 generation 무효화하지 않으며 실제수락삭제는 DB IO전에 동기 무효화한다. PNG는 currentDB+UI fullrow 비교. render/share generation 검사, chooserlaunch/삭제invalidate는 같은lock 및 무suspend구간으로 순서 보장. dispatched snapshot은clear에도존속,20개64MiB상한은새share거절. 시작/share/delete lazyTTL과미실행/OS회수안내반영. cancellation은미전달출력만정리. UIDreceiver는작성중으로 검토미포함. 읽기만, 빌드/ADB/수정없음.

## 별도 UID 수신 시험 — billing_recovery APPROVE

ACTION_SEND copy→testAPK explicitComponent, ClipData/READ_URI유지. receiverUID와targetUID 다름 assert, receiver originalURI reopen +bytes content compare. androidTest manifest에만 exportedActivity/:share_probe 선언, productionmainmanifest/test참조없음. 시스템chooser 사용자앱선택은이시험범위아님. 삭제후 generatedexistsfalse/retainedisFile 추가assert는 optional보강제안(현재sameURI재읽기가보존을검증). 실행미확인, 수정/빌드/ADB없음.

## UID crash 교정 재검토 — billing_recovery APPROVE

69e6706에서 standalone testAPK receiver를 Android/Java기본API만 쓰는 Java로전환. 동일FQCN/별도process와actualgrant/서로다른UID/sameURI재읽기/bytes비교유지. 발송전 host/appContext/testpackagecapture, 발송후정리는appContext. targetAPK/Kotlin새dependency또는권한우회없음. 읽기만, 실행미확인.
