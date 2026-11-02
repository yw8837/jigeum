# AST 공개·합성 입력의 Android 전후 비교 QA

2026-10-04 · 전후 측정·입력/격리 검증 완료. 목표는 같은 입력에서 현재 YAMNet 판정과 고정 AST/v2 추가 후 판정을 비교하는 것이다.
아기 의도 정확도·영상 공동 생성·ARM 속도·제품 채택을 이 실험으로 대체하지 않는다.

## 사전 고정

- 기존 공개·합성 corpus22개와 기존 AST-only 공개11개를 모두 사용한다. 결과를 보고 입력을 선별하지 않는다.
- 기존 f32는 저장된 SHA와 대조한다. AST-only WAV는 기존 SHA를 확인하고 PCM16/32768로 f32만 변환한다.
  구간·음량·필터·패딩을 바꾸지 않는다. 현재 앱 baseline의 AudioCleanup은 그대로 실행한다.
- 33입력 manifest SHA256:`f54b625aa24b5136eb0f419edab637aaecf8f69c43e32ecd7d344ca16dd04676`.
  `work/ast-public-paired-01/frozen-before-run.json`에 4개 실제 정책 소스 SHA도 고정했다.
- 기존 공개 자료의 holdout 표기는 과거 역할이다. 이번에는 모두 이미 알려진 개발/회귀 자료이며 독립 평가로 부르지 않는다.
- 무음·tone·noise·rumble·DC는 합성 구조에 따른 비아기 대조다. 공개 자료의 발성 주인/종류는 source-description 라벨로 남긴다.
- 새 다운로드/원본 외부 전송 없음. 정확한 별도 `.astprobe` 패키지만 설치하고 끝나면 제거한다.
- 모델 오류·부분 입력·누락 결과는 측정 실패다. 정상 보류와 구분한다. 최대1회 고정 정상 측정,
  하네스 결함이 있으면 원인을 수정한 재실행만 허용한다. 점수에 맞춘 threshold/crop 반복 없음.

## 시나리오 행렬

| ID | 의도/모델 | 준비·실행 | 기대 신호 | 실제 결과 | 수정·증거 | 정리 |
|---|---|---|---|---|---|---|
| PAIR | 같은 공개·합성 입력 | standalone, 실제 baseline→AST→v2→interpreter |33개 완전 측정, 앞뒤 입력 SHA동일|33개 측정 완료·hash/manifest 일치|run-01 exit0, 계측2통과|별도 앱 uninstall완료|
| PRESERVE | 기존 승인/차단 보존 |울음·웃음·혼합·무음 포함|기존 승인 변경/손실·blocked 신규승인0|승인12개 유지, 나머지21개 보류/차단 유지|새/손실 문장0|원본 유지|
| ADDED | 후보가 추가 승인한 사건 |기존 public AST-only11 포함|추가 승인 목록과 라벨의 강도 구분|추가 승인0|이번 actual pair의 관측치, 이전 AST-only unknown 소급변경 안함|개별결과 ignored|
| NEGATIVE | 잘못된 발성 승인 |성인 대화/웃음·음악·청소기·합성교란|전후/새오인/기존오인을 구분|합성5/설명기반공개7 모두 출력없음|공개 라벨은 weak·일반 오인율 아님|전송 사본 제거완료|
| INPUT | malformed/stale |host 결과 계약과 PCM변조|hash/count/run/model 오류 거부|신규host5통과·PCM변조CLI exit1|기기접근 전 hash mismatch 거부, 계측내 불완전AST 거부검사도 통과|아기원본미사용|
| ISOLATION | 기존 앱 보호 |exact manifest/설치SHA/제품 snapshot|별도 설치·제품불변·실패도정리|exact 별도설치·SHA일치·패키지/PID 부재|이 부팅의 제품은 전후미설치, 사용자기록보존재검증 아님|에뮬레이터 정상종료|

평가 Kotlin은 Sol/high가 담당하고 부모는 corpus/host/ADB/Gradle과 최종 해석을 담당한다.
`ultraqa` 상태는 ignored 실행 폴더의 CLI 상태로 격리하며 완료/중단 시 해당 상태만 지운다.

## 결과와 판단

**33개 모두 baseline/candidate가 같은 판정이었다.** 기존 12개 결과를 유지했고 21개는 보류/차단을 유지했다.
새로 출력한 문장·잃은 문장·승인 종류 변경은 모두0개다. 이 실험에서 추가 승인 자체가0개이므로
새 오인 추가도 관측되지 않았지만, 기존 판정의 정확도나 일반 오인율을 검증한 것은 아니다.

- 합성 비아기 대조5개: silence/tone/noise/rumble/DC 모두 결과 없음.
- 설명 기반 공개 대조7개: conversation/vacuum/piano/generic_laugh/cchenail_laugh/duducsepel_voice/ripc_laugh 모두 결과 없음.
  화자의 나이·종류는 독립 판정이 없는 source-description이다. TV의 영아 재생음은 이 묶음에 없다.
- 기존 AST-only11개 중 일부 옹알이 설명 자료는 현재 YAMNet이 이미 어린 말소리/복수 사건으로 승인한다.
  AST 단독의 옹알이 조건 미충족을 앱 전체의 누락으로 해석하면 안 된다.
- 기존 공개 `babble`, `clod111_11month` 등은 계속 미확정이다. 다른 혼합 입력의 보류도 남아 있다.
  `almost_laugh`·`cry_six_month`처럼 설명과 복수 사건 판정이 다른 기존 사례도 있어 baseline 정확도로 승격하지 않는다.

약국 사용자 개발 사례의 이전 회복을 이번0건으로 무효화하지 않는다.
고정 후보는 이33개에서 기존 동작을 악화시키지 않았으나 일반적인 새 회복 근거도 늘리지 못했다.
정책을 그대로 유지한 제한적 채택 후보이며, 실제 ARM 비용·제품 통합 검증 후 판단해야 한다.
`BabbleSupportPolicy.ENABLED=false`, 일반 패키지 AST 제외, Play beta23 상태는 바뀌지 않았다.
선택 구간의 소리·영상을 이해하여 새 아기말을 생성하는 전체 요구는 계속 미완료다.

## 실행·검토 증거

- 연구 debug/test 빌드 exit0, 1분18초. app SHA256
  `f76224c21fb2280881db0c3c6bed5e50e7151f9f50ce53ec712f0029fc064f43`,
  test SHA256 `d721dfe65fe1c363ef5f7f81fdabc263cd16ecb592ec24a63bd02a80b126b632`.
- Android API35 x86_64. JUnit2개 통과/실패0/제외0,97.43초.
  실제33입력 측정은94.222초. 이 일괄 실행 시간을 휴대폰의 단일 분석 지연으로 쓰지 않는다.
- host 신규5개 포함 관련 AST 도구 **27개 통과**. 최초 미구현 RED 이후 구현했다.
- 실제 변조 corpus CLI는 exit1, stage=corpus, hash mismatch이며 기기 명령에 도달하지 않았다.
- Sol 읽기검토: 새 P1/P2 발견0건. 이 검토를 인식 품질 통과와 구분한다.
- 로컬 증거: `work/ast-public-paired-01/run-01/{result.json,paired-results.json,instrumentation.txt}`,
  `aggregate.json`, `delta-rows.json`, `host-tests.log`, `bad-hash-run/result.json`.
  개별 점수·원본·파생 PCM·APK는 Git에 넣지 않는다.

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot'
python tools/research/ast_public_probe.py --serial emulator-5554 --adb C:/MoaSdk/platform-tools/adb.exe --apkanalyzer C:/MoaSdk/cmdline-tools/latest/bin/apkanalyzer.bat --apk <standalone.apk> --test-apk <paired-test.apk> --corpus work/ast-public-paired-01/corpus --output work/ast-public-paired-01/<new-run>
python -m unittest discover -s tools/research -p 'test_ast_*' -v
```

**ULTRAQA COMPLETE:** 같은 입력 전후 측정·입력계약·패키지격리 범위에서1회 정상 실행 완료.
**제품 인식 품질·ARM·AV 생성·배포 완료가 아니다.** APK/모델/원본 사본은 연구 패키지 uninstall로
기기에서 제거됐고, 시작한 에뮬레이터도 종료했다. 로컬 근거는 ignored work에 보존한다.
격리한 CLI 상태도 clear 후 read의 `exists=false`와 해당 상태 파일 부재를 확인했다.
첫 정리 확인 스크립트가 clear 응답의 `success` 키를 가정해 실패했으나, 읽기 재확인으로 실제 삭제 완료를 검증했다.
