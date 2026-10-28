# 영아 발성 보완: beta 적용 준비와 실폰 검증 계약

2026-10-07. 기준 `a4fcf49`. 이번 단계는 연구에서 회복한 약국 발성 보완을
실제 beta에 연결할 준비다. 연구 결과·호스트 gate 통과와 새 beta 배포를 구분한다.
현재 code32는 재업로드하지 않고, ARM 검증 뒤 새 versionCode에서만 패키징한다.

## 적용 범위

- 새 `agattokInfantPresenceBeta=true`는 `agattokAstBeta=true`가 있을 때만 허용한다.
  기존 candidate 연구 옵션과 섞지 않는다. 기본 빌드·기존 AST beta·정식 release는 그대로다.
- 새 beta 옵션은 `AST_INFANT_PRESENCE=true` 및 이미 검증한 동결 graph/notice 하나를 선택한다.
  생성 asset staging에 원 graph와 후보 graph를 함께 넣지 않는다.
- 문장/판정 코드·기존527 logits·울음/웃음/기존 옹알이 조건은 이번 단계에서 변경하지 않는다.
  존재 보완을 욕구/발성 시점/립싱크 정확도라고 표현하지 않는다.
- 최종 beta asset 병합과 공식 PowerShell builder에서 같은 ARM proof verifier를 호출한다.
  proof 없는 build, 부정한 boolean, 잘못된 variant, 오래된 code32 재사용을 거부한다.

## 실제 ARM 측정 gate

`ast_device_benchmark.py --infant-presence-candidate`로 기존 보존 연구 APK/test 한세트를 사용한다.
실폰 모델명 whitelist는 없다. 기존 아가톡 APK metadata/hash를 전후 비교하고,
연구 package만 설치/검사/제거한다. 사용자 원음·기록을 읽거나 네트워크로 전송하지 않는다.

- 동결 앱 SHA `77b20be46ecef6e302515d63bde12f4c378a8eeb50ea829fb455d0271a61624e`
  (265,353,775B), test SHA `3dea96314dbb0fe8daafecbdfed051e8a9d7b72ed774a2c02c590027bb184159`
  (2,765,526B), graph SHA `4a378476dcbe5f6b13c5f67295797d59376dab3627fe3dbd56bfeeadbd7f802a`.
- adb emulator serial·qemu property 거부, ARM ABI 및 API26 이상을 확인한다.
  Android runtime ABI/model/API가 adb metadata와 같아야 한다.
- 합성 10/20/20초를 cold→warm→repeat로 검사한다. 실제 native head/RMS의 finite/range,
  1/3/3개 분석 창은 동결 instrumentation이 확인한다. 소리 사건/의도 정답 검사는 아니다.
- AST 측정 각30,000ms 이하, sampled process PSS peak900MiB 이하, thermal<SEVERE.
  API29 이상은 실제 thermal status 필요. 이 수치는 45초 앱 분석 제한의 여유를 위한 채택 gate이며
  전체 AV 시간/모든 기기 속도/연속 사용의 발열 보장은 아니다.
- native cancel→새 실행과 analyzer cancel→owner 해제 테스트2개도 실제 실행한다.
  per-test terminal/count/skip/exit를 로그로 다시 파싱한다. `OK` 문자열만으로 승인하지 않는다.
- 검사 완료7일 이내 UTC timestamp/run ID, 설치된 exact APK SHA, 연구앱 제거와
  기존 아가톡 전후 snapshot 일치를 요구한다. duplicate/oversized/nonfinite JSON을 거부한다.
- 실행용 APK 사본은 새 실행 디렉터리 내부의 임시 하위폴더에 동결하고 종료 때 정리한다.
  보존 final APK/test는 건드리지 않으며 APK 사본 제거 실패도 채택 실패로 기록한다.

proof는 로컬 작업 증거 검사다. 서명된 기기 원격 인증이 아니며 파일을 고의로 위조하는 사람을
막는 보안장치라고 주장하지 않는다. 임시 unit fixture를 실제 ARM 성공 증거로 저장하지 않는다.

## 이번 QA matrix

| ID | 실행 경로 | 기대 |
|---|---|---|
| B1 | 신규beta/기존beta/release BuildConfig 생성 | true/true, true/false, false/false |
| B2 | 잘못된boolean·의존성누락·연구 혼합·release variant | 실제Gradle 실패 |
| B3 | 실제ARM proof 누락·기존code32·에뮬레이터 증거 | 패키징 거부 |
| B4 | candidate asset staging/hash | 동결 graph 하나·notice 보존 |
| B5 | stale·잘못된APK·누락창·속도/메모리/열·fakeOK·skip | shared verifier 거부 |
| B6 | PowerShell internal/paid/옵션누락/proof누락 | signing 접근·build 이전 거부 |
| B7 | 기존probe runner 회귀·candidate metadata | 기존경로 보존·기기 혼동 거부 |
| B8 | 더러운작업트리·timeout·state cleanup | 타작업 보존·bounded child 종료·이번 state 제거 |

각 실제 명령·exit/log/자손 종료는 별도 QA 보고서에 기록한다. 전 단계 삭제차단을 다른 도구로
우회하지 않는다. 이 준비로 APK/AAB를 대량 재생성하거나 Play를 갱신하지 않는다.
다음은 S25 ARM 실측→새 beta code→서명/update/기록 보존→Play 비공개 검토 제출이다.
