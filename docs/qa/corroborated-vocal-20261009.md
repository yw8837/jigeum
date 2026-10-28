# UltraQA Report — 같은 구간의 아기 발성 보완 / 0.5.21-beta(code35)

## 목표와 승인 기준

code34 기준으로 종류 미확정/혼합 발성의 불필요한 보류를 줄인다.
같은 시간창의 실제 음량·영아 소리 단서·두 보조 head를 함께 요구한다.
모델, 필터, 음량 gain, 수치 임계값은 유지한다. 배고픔·졸림·감정 판정은 추가하지 않는다.
기존 출력/관찰 exact 보존, 성인·잡음 신규 출력 0, 기록·정식 접근 보존이 조건이다.
최대 5cycle, 동일 실패 3회 중단. 아기 자료 업로드·상품·프로덕션 변경은 범위 밖이다.

기준 커밋 `a06417037856e75a4297fb21e2e7b24c1bd4c53f`, 구현 `340558f`, 최종 앱 소스 `ed52fae`.
사전 계약은 [발성 보완](../research/20261009-corroborated-vocal-contract.md),
미채택 실험은 [작은 음량 gain](../research/20261009-quiet-gain-contract.md)을 따른다.
원시 로그/스크립트는 `work/corroborated-vocal-20261009/`에 있다.

## 시나리오와 실제 결과

|ID|목적·입력|명령/설정|기대|실제 결과|근거·정리|
|---|---|---|---|---|---|
|P1|작은 웃음·혼합 단서|`host.py red01/green01`, `baseline.py candidate01`|기존 보류 재현/회복|host 2실패→63통과, Android 28조건에서 2추가 출력|입력/모델 관측 동일, 이전5출력 exact|
|P2|성인·일반웃음·잡음·무음/기존 아기 입력|`device.py paired final`, `compare.py`|오인 증가0/기존 승인 exact|53조건 중 이전20출력 exact, 6추가 출력; 두 묶음의16/20대조 출력0|원본 SHA/production 설치 보존, runtime 입력 삭제|
|P3|NaN·시간 순서·coverage·다른창 음량·감정/TV|`native.py`, `host.py green02`|불완전 자료로 우회 불가|Android108통과/0skip, 최종host27통과|실제 설치 APK SHA 확인|
|P4|새 경로 모델실패·취소·다음 분석|`new_branch.py`, `integration.py ui final`|실패 숨김 금지/취소 전파/복구|새 경로1통과·실제 추론4,386ms, CaptureFlow1통과|에뮬레이터 측정, 후속 추론 성공·원본 보존|
|P5|구간·미디어·앨범·백업/복원|`integration.py regression final`|기존 기능 보존|Android80통과/0skip|실제 설치code35/hash 검증|
|P6|서명34와35 같은 AAC 입력|`signed_recovery.py 01`|실제 앱에서 회복 및 불확실성 표시|34보류→35아기 발성 후보/종류 미확정|공개 원본hash 보존, 생성 MP4/기기 복사 제거|
|P7|서명 업데이트/기록·MP4|`run_ui.py upgrade retry02`|34→35에서 데이터 동일|exit0/204.73초, 문장·LEGACY 출처·10초 audio+video MP4 SHA 동일|초기 downgrade 설정 오류 보존, 소유 외부파일 제거|
|P8|beta/release 패키지와 접근|`build_signed.py beta/release 01`, `verify_final.py`, `run_ui.py closed final`|beta전체/정식 재잠금/서명|빌드·패키지·실제7시나리오 통과(exit0/565.42초)|DEX·APK/AAB·서명·백업 제외·beta→release 미디어hash 보존|
|P9|불완전 실행·skip·stale·dirty·저장공간|종료상태/count/hash와 lifecycle 점검|실패를 성공으로 집계하지 않음|2cycle complete/clear/부재, 소유 QA 정리 완료|공유 AVD 보존, 중간파일3,598,502,380B 제거|
|D1|기존 Alpha 업데이트|검증 beta35 AAB만 제출|정확한 버전 검토/게시 확인|10월9일17:20 KST 검토 요청, 정확35 사전검사 중/제공 미확인|[제출 증거](corroborated-vocal-20261009/play-review.md), 다른 트랙·테스터 설정 보존|

## 변경과 검토

`InfantVoiceSupportPolicy`는 기존 YAMNet 영아 클래스 .35, 같은 창 RMS .003,
AST infantPresence/captionPresence 각각 .80, 전체 coverage와 감정/TV veto .30을 요구한다.
두 head는 같은 encoder를 공유한다. 통계적으로 독립적인 판정 두 개로 해석하지 않는다.
시간 범위 정합성을 만족해야 하며, 출력은 종류 미확정 아기 발성에 한정한다.
`SoundTimeline`은 기존 AST 실행을 재사용하고 새 대상도 한 번만 실행한다.
`ClipEvidence`는 종류 미확정 근거를 표시하고 종류/리듬/순서/입-소리 동시성을 만들어내지 않는다.

### Spec

초기 P2: 새 경로에서 AST 실패가 일반 아기 미인식으로 숨겨질 수 있었다.
`MODEL_FAILURE` 보존과 취소 전파를 수정했다. 최종 필수 지적 0.

### Standards

동일 P2 및 중복975 상수 지적을 수정했다. 기존 `SoundPhaseRecovery.FRAME_MS`를 사용한다.
최종 필수 지적 0, 신규 문제 없음. 리뷰는 읽기 전용이며 동적 검사를 대신하지 않는다.

## 실패·보류 이력

1. **gain 미채택:** 고정28조건 회복0. laugh_rms6 미확정→혼합. 제품에 추가하지 않았다.
2. **연구 빌드 wrapper:** build01/02 Gradle 자체 exit0이나 `orphan_cleanup`으로 wrapper exit1.
   descendant0/cleanup 완료. 이를 clean wrapper 통과로 세지 않는다. 최종 beta/release 서명 빌드는
   각각 exit0/termination null/cleanup 완료다.
3. **new-branch01:** rms6 PCM16 WAV는 새 분기의 eligibility 사전조건에서 실패했다.
   세부 원인은 계측하지 않았다. 이미 고정된 다른 양성 rms9로 new-branch02가 통과했다.
   코드·임계값을 변경하지 않았다. 첫 실패 로그는 보존하며 running 보고서를 pass로 바꾸지 않는다.
4. **private AV 재실행 불가:** 이전 KakaoTalk_20261002_213909230.mp4 원본이 지정 경로에 없고,
   Downloads/Desktop/Documents의 집중 검색에서도 찾지 못했다. AV 모델 실행 전 실패했다.
   retained PCM3의 결과는 exact 보존됐고, 공개 AAC 서명 비교·시각 시간축 회귀로 대체 검증한다.
   새 private10/20초 AV 실측을 주장하지 않는다.
5. **signed-upgrade-final:** 앞선 signed recovery가35를 설치한 상태에서34를 설치해
   INSTALL_FAILED_VERSION_DOWNGRADE. 소유 QA 에뮬레이터의 시험 설치만 제거 후 재시도했다.
   사용자 휴대폰·실제 기록을 지운 것이 아니다.

## 성능과 남은 한계

새 경로 정상 AST의 4,386ms는 에뮬레이터 값이다. 서명 회복 UI31.65초는 전체 화면 흐름이며
모델 단독 속도가 아니다. 기존45초 제한을 유지했다. 동일 encoder의 기존 S25 ARM proof를
재검증했지만 이번 새 조건의 휴대폰 속도·발열을 새로 측정한 것은 아니다.
추가 모델 다운로드/graph/외부 처리 비용은 없다.

두 시험 묶음의 2+6조건은 서로 독립적인 영아8명이 아니다. 같은 소스 변형·약한 caption과
목표 연령보다 어린 자료가 포함된다. 일반 인식률, 아기 화자 식별, 욕구 정확도를 입증하지 않는다.
나머지 작은 울음·일부 옹알이/혼합 소리 보류는 그대로 남았다.

## 패키지와 증거

beta35 AAB SHA256: `6608e17f827c193df3a82a6b3eaa595467ba880037d40fb343877444fc36cd13`.
beta35 APK SHA256: `2dbd5c1f3f20c5437859ad9e9eca4a382174e37f6453f790321792247a55b145`.
최종 파일은 `work/corroborated-vocal-20261009/final/`의4개다.
release 비교용 AAB는 Play에 제출하지 않는다.
검증 요약과 정리 결과는 이 문서와 같은 이름의 디렉터리에 저장한다.

## 현재 상태

로컬 UltraQA는 2cycle 완료다. 실제120초 촬영(클립1:59.7), 20초 무음 보류,
200MiB/+1byte 경계, 공개 실제 AV·영상 저장·PDF 공유·재시작,
동일 키 정식 업데이트의60/10/100·시각/PDF 재잠금과 기록/MP4 SHA 보존까지 통과했다.
원시 접근 검사565.42초, 서명 업데이트204.73초 모두exit0이다.

시험 앱3개 제거, 기기 임시 입력/내려받기 제거, 화면·글꼴 복원, 에뮬레이터 종료,
소유 worktree의 재생성 가능한 build 중간파일5,282개(3,598,502,380B) 삭제를 확인했다.
공유 MoaQA35 디렉터리, 사용자 원본, 최종35/직전34 패키지와 QA 근거는 보존했다.
정리 후 C: 여유 공간83,420,282,880B. 이는 전체 free 변화이며 삭제파일 합계와 구분한다.
스코프 내 UltraQA state complete→clear→absent를 확인했다.
[검증 요약](corroborated-vocal-20261009/summary.json)에 실패와 한계까지 보존한다.

**ULTRAQA COMPLETE: Goal met after 2 cycles (local QA).**
기존 Alpha 검토 요청은10월9일17:20 KST 전송했다.
**18:13 KST에 정확35가 선택한 테스터에게 제공됨을 확인했다. Console 게시일은17:56 KST다.**
게시 개요의 검토 항목도 해소됐다. [게시 증거](corroborated-vocal-20261009/play-review.md).
이번 code35의 로컬 QA와 Alpha 제공 확인은 완료됐으며, 전체 장기 개선 목표는 남아 있다.
후속200ms 영상 관찰 후보는 이 배포에 포함되지 않는다.35는500ms 관찰을 사용한다.
